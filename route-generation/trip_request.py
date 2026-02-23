"""TripRequest — rota üretim isteği ve iç puanlama mantığı."""

from __future__ import annotations

import math
import random
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Set

from place import Place
from poi_controller import POIController
from route import Route, RoutePoint
from utils import haversine_km, safe_float, safe_int


@dataclass
class TripRequest:
    requestId: str = ""
    maxBudgetMin: int = 480
    maxStops: int = 6
    mandatoryTypes: Set[str] = field(default_factory=set)
    overrideWeights: Dict[str, float] = field(default_factory=dict)

    # runtime:
    _repo: Optional[POIController] = field(default=None, repr=False)

    # opsiyonel filtreler (userVector'dan dolduruluyor)
    _center_lat: Optional[float] = field(default=None, repr=False)
    _center_lng: Optional[float] = field(default=None, repr=False)
    _radius_km: Optional[float] = field(default=None, repr=False)
    _travel_mode: str = field(default="driving", repr=False)
    _min_rating: float = field(default=0.0, repr=False)
    _max_price_level: str = field(default="", repr=False)

    # -----------------------------------------------------------------
    # Public API
    # -----------------------------------------------------------------

    def generateRoutes(self, userVector: Dict[str, str], k: int) -> List[Route]:
        self.parseUserVector(userVector)
        if not self.validate():
            return []

        if self._repo is None:
            raise RuntimeError("TripRequest._repo (POIController) set edilmemiş.")

        routes: List[Route] = []
        k = max(1, int(k))

        for i in range(k):
            rnd = random.Random(hash((self.requestId, i, "routegen")))

            selected = self._select_places(rnd)
            ordered = self._order_nearest_neighbor(selected, rnd)

            route = Route(
                routeId=f"route-{self.requestId}-{i}",
                travelMode=self._travel_mode,
            )
            route._repo = self._repo  # type: ignore[attr-defined]
            route._req = self  # type: ignore[attr-defined]

            route.points = [
                RoutePoint(
                    index=j,
                    poi=p,
                    plannedVisitMin=self._estimate_visit_minutes(p),
                )
                for j, p in enumerate(ordered)
            ]
            route.recomputeLocalSegments(0)
            route.recomputeTotals()
            route.feasible = self._is_feasible(route)
            routes.append(route)

        return routes

    def parseUserVector(self, userVector: Dict[str, str]) -> None:
        uv = userVector or {}
        self.requestId = (
            uv.get("requestId") or uv.get("tripId") or "req"
        ).strip()

        self.maxStops = max(
            1, min(safe_int(uv.get("maxStops"), self.maxStops), 30)
        )
        self.maxBudgetMin = max(
            1,
            safe_int(
                uv.get("maxBudgetMin") or uv.get("maxDurationMin"),
                self.maxBudgetMin,
            ),
        )

        mand = (uv.get("mandatoryTypes") or "").strip()
        if mand:
            self.mandatoryTypes = {
                x.strip().lower()
                for x in mand.replace(";", ",").split(",")
                if x.strip()
            }
        else:
            self.mandatoryTypes = set()

        self.overrideWeights = {}
        for key, val in uv.items():
            if key.startswith("weight_"):
                t = key[len("weight_") :].strip().lower()
                w = safe_float(val, 0.0)
                if t and w > 0.0:
                    self.overrideWeights[t] = w

        self._center_lat = (
            safe_float(uv.get("centerLat"), None) if uv.get("centerLat") else None
        )
        self._center_lng = (
            safe_float(uv.get("centerLng"), None) if uv.get("centerLng") else None
        )
        self._radius_km = (
            safe_float(uv.get("radiusKm"), None) if uv.get("radiusKm") else None
        )

        self._travel_mode = (
            uv.get("mode") or uv.get("travelMode") or "driving"
        ).strip().lower()
        self._min_rating = safe_float(uv.get("minRating"), 0.0)
        self._max_price_level = (uv.get("maxPriceLevel") or "").strip()

    def validate(self) -> bool:
        if self.maxStops <= 0:
            return False
        if self.maxBudgetMin <= 0:
            return False
        return True

    def rerollRoutePoint(
        self, route: Route, index: int, indexParams: Dict[str, str]
    ) -> Route:
        locked = self.lockUnchangedPOIs(route, index)
        route.rerollPoint(index, indexParams, self, locked)
        return route

    def lockUnchangedPOIs(self, route: Route, index: int) -> Set[str]:
        locked: Set[str] = set()
        for i, rp in enumerate(route.points or []):
            if i == index:
                continue
            if rp.poi and rp.poi.id:
                locked.add(rp.poi.id)
        return locked

    # -----------------------------------------------------------------
    # Internal selection / scoring
    # -----------------------------------------------------------------

    def _build_candidate_pool(self) -> List[Place]:
        assert self._repo is not None
        pool = self._repo.getAllPlaces()

        pool = [
            p
            for p in pool
            if not p.businessStatus
            or p.businessStatus.strip().upper() == "OPERATIONAL"
        ]

        if self._min_rating > 0:
            pool = [p for p in pool if p.ratingScore >= self._min_rating]

        if (
            self._center_lat is not None
            and self._center_lng is not None
            and self._radius_km is not None
        ):
            clat, clng, rkm = self._center_lat, self._center_lng, self._radius_km
            pool = [
                p
                for p in pool
                if haversine_km(clat, clng, p.latitude, p.longtitude) <= rkm
            ]

        return pool

    def _score_place(self, p: Place) -> float:
        rating = p.ratingScore
        pop = math.log(max(p.ratingCount, 0) + 1.0)

        type_bonus = 0.0
        if self.overrideWeights:
            for t in p.types:
                tt = (t or "").strip().lower()
                type_bonus += self.overrideWeights.get(tt, 0.0)

        dist_penalty = 0.0
        if self._center_lat is not None and self._center_lng is not None:
            d = haversine_km(
                self._center_lat, self._center_lng, p.latitude, p.longtitude
            )
            dist_penalty = 0.05 * d

        return (1.0 * rating) + (0.4 * pop) + (1.0 * type_bonus) - dist_penalty

    def _pick_one_place(
        self,
        desired_type: str,
        excluded_ids: Set[str],
        min_rating: float,
        max_price_level: str,
    ) -> Optional[Place]:
        pool = self._build_candidate_pool()

        dt = (desired_type or "").strip().lower()
        candidates = []
        for p in pool:
            if p.id in (excluded_ids or set()):
                continue
            if min_rating and p.ratingScore < min_rating:
                continue
            if dt and not p.has_type(dt):
                continue
            if max_price_level:
                try:
                    if p.priceLevel:
                        if int(p.priceLevel) > int(max_price_level):
                            continue
                except Exception:
                    pass
            candidates.append(p)

        if not candidates:
            return None

        candidates.sort(key=self._score_place, reverse=True)
        top_n = min(25, len(candidates))
        return random.choice(candidates[:top_n])

    def _select_places(self, rnd: random.Random) -> List[Place]:
        pool = self._build_candidate_pool()
        if not pool:
            return []

        used: Set[str] = set()
        selected: List[Place] = []

        # 1) mandatoryTypes: her birinden 1 tane
        mand = list(self.mandatoryTypes)
        rnd.shuffle(mand)
        for t in mand[: self.maxStops]:
            candidates = [p for p in pool if p.id not in used and p.has_type(t)]
            if not candidates:
                continue
            candidates.sort(key=self._score_place, reverse=True)
            chosen = candidates[0]
            selected.append(chosen)
            used.add(chosen.id)
            if len(selected) >= self.maxStops:
                break

        # 2) kalan slotlar
        if len(selected) < self.maxStops:
            remaining = [p for p in pool if p.id not in used]
            remaining.sort(key=self._score_place, reverse=True)

            top_m = min(max(self.maxStops * 10, 40), len(remaining))
            head = remaining[:top_m]
            rnd.shuffle(head)

            for p in head:
                if len(selected) >= self.maxStops:
                    break
                selected.append(p)
                used.add(p.id)

        while len(selected) < self.maxStops and len(used) < len(pool):
            p = rnd.choice(pool)
            if p.id in used:
                continue
            selected.append(p)
            used.add(p.id)

        return selected[: self.maxStops]

    def _order_nearest_neighbor(
        self, places: List[Place], rnd: random.Random
    ) -> List[Place]:
        if len(places) <= 2:
            return places

        if self._center_lat is not None and self._center_lng is not None:
            start = min(
                places,
                key=lambda p: haversine_km(
                    self._center_lat, self._center_lng, p.latitude, p.longtitude
                ),
            )
        else:
            start = max(places, key=self._score_place)

        remaining = places[:]
        remaining.remove(start)

        ordered = [start]
        cur = start
        while remaining:
            nxt = min(
                remaining,
                key=lambda p: haversine_km(
                    cur.latitude, cur.longtitude, p.latitude, p.longtitude
                ),
            )
            ordered.append(nxt)
            remaining.remove(nxt)
            cur = nxt

        return ordered

    def _estimate_visit_minutes(self, p: Place) -> int:
        ts = {t.lower() for t in (p.types or [])}
        if "hotel" in ts or "lodging" in ts or "guest_house" in ts:
            return 20
        if "museum" in ts:
            return 90
        if "restaurant" in ts:
            return 70
        if "cafe" in ts:
            return 50
        if "park" in ts or "nature" in ts:
            return 60
        if "historical" in ts or "landmark" in ts or "tourist_attraction" in ts:
            return 60
        return 45

    def _is_feasible(self, route: Route) -> bool:
        if self.mandatoryTypes:
            present = set()
            for rp in route.points:
                for t in rp.poi.types or []:
                    present.add((t or "").strip().lower())
            for m in self.mandatoryTypes:
                if m not in present:
                    return False

        return route.totalDurationSec <= self.maxBudgetMin * 60
