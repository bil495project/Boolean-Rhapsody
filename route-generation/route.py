"""Route, RoutePoint, RouteSegment — rota veri modelleri."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Dict, List, Set

from place import Place
from utils import haversine_km, km_to_meters, safe_float, travel_seconds

if TYPE_CHECKING:
    from trip_request import TripRequest


@dataclass
class RoutePoint:
    index: int
    poi: Place
    plannedVisitMin: int = 45

    def assignPOI(self, poi: Place) -> None:
        self.poi = poi


@dataclass
class RouteSegment:
    fromIndex: int
    toIndex: int
    durationSec: int
    distanceM: float

    def hasGeometry(self) -> bool:
        return False


@dataclass
class Route:
    routeId: str
    points: List[RoutePoint] = field(default_factory=list)
    segments: List[RouteSegment] = field(default_factory=list)
    totalDurationSec: int = 0
    totalDistanceM: float = 0.0
    feasible: bool = False

    # runtime config
    travelMode: str = "driving"

    def rerollPoint(
        self,
        index: int,
        indexParams: Dict[str, str],
        req: "TripRequest",
        lockedPOIIds: Set[str],
    ) -> None:
        if index < 0 or index >= len(self.points):
            return

        desired_type = (indexParams or {}).get("type", "") or ""
        min_rating = safe_float(
            (indexParams or {}).get("minRating"), req._min_rating
        )
        max_price = (
            (indexParams or {}).get("maxPriceLevel", req._max_price_level) or ""
        )

        if not desired_type:
            cur_types = self.points[index].poi.types
            if cur_types:
                desired_type = cur_types[0]

        replacement = req._pick_one_place(
            desired_type=desired_type,
            excluded_ids=lockedPOIIds,
            min_rating=min_rating,
            max_price_level=max_price,
        )
        if replacement is None:
            return

        self.points[index].assignPOI(replacement)
        self.points[index].plannedVisitMin = req._estimate_visit_minutes(replacement)

        self.recomputeLocalSegments(index)
        self.recomputeTotals()
        self.feasible = req._is_feasible(self)

    def insertManualPOI(
        self, route: "Route", index: int, poiId: str
    ) -> "Route":
        p = (
            route._repo.getPlaceById(poiId)
            if hasattr(route, "_repo")
            else None
        )
        if p is None:
            return route

        idx = max(0, min(index, len(self.points)))
        self.points.insert(idx, RoutePoint(idx, p, plannedVisitMin=45))

        for i, rp in enumerate(self.points):
            rp.index = i

        self.recomputeLocalSegments(idx)
        self.recomputeTotals()
        self.feasible = (
            route._req._is_feasible(self)
            if hasattr(route, "_req")
            else self.feasible
        )
        return self

    def removePoint(self, route: "Route", index: int) -> "Route":
        if index < 0 or index >= len(self.points):
            return route

        self.points.pop(index)
        for i, rp in enumerate(self.points):
            rp.index = i

        self.recomputeLocalSegments(index)
        self.recomputeTotals()
        self.feasible = (
            route._req._is_feasible(self)
            if hasattr(route, "_req")
            else self.feasible
        )
        return self

    def reorderPOIs(self, route: "Route", index: List[int]) -> "Route":
        if index is None or len(index) != len(self.points):
            return route

        old = list(self.points)
        reordered: List[RoutePoint] = []
        for i in index:
            if i < 0 or i >= len(old):
                return route
            reordered.append(old[i])

        self.points = reordered
        for i, rp in enumerate(self.points):
            rp.index = i

        self.recomputeLocalSegments(0)
        self.recomputeTotals()
        self.feasible = (
            route._req._is_feasible(self)
            if hasattr(route, "_req")
            else self.feasible
        )
        return self

    def recomputeLocalSegments(self, index: int) -> None:
        self.segments = []
        if len(self.points) <= 1:
            return
        for i in range(len(self.points) - 1):
            a = self.points[i].poi
            b = self.points[i + 1].poi
            km = haversine_km(a.latitude, a.longtitude, b.latitude, b.longtitude)
            self.segments.append(
                RouteSegment(
                    fromIndex=i,
                    toIndex=i + 1,
                    durationSec=travel_seconds(km, self.travelMode),
                    distanceM=km_to_meters(km),
                )
            )

    def recomputeTotals(self) -> None:
        travel = sum(s.durationSec for s in self.segments)
        dist = sum(s.distanceM for s in self.segments)
        visits = sum(rp.plannedVisitMin for rp in self.points) * 60
        self.totalDurationSec = travel + visits
        self.totalDistanceM = dist
