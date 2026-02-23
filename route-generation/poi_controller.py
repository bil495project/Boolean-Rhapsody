"""POIController — CSV'lerden Place nesnelerini yükler ve sorgular."""

from __future__ import annotations

import csv
import os
from typing import Dict, Iterable, List, Optional

from place import Place
from utils import parse_types_cell, safe_float, safe_int


class POIController:
    def __init__(self) -> None:
        self.places: List[Place] = []
        self._by_id: Dict[str, Place] = {}

    def load_csv(self, csv_path: str) -> None:
        """
        CSV şeması:
        id,name,formatted_address,lat,lng,types,rating,user_rating_count
        """
        with open(csv_path, "r", encoding="utf-8", newline="") as f:
            reader = csv.DictReader(f)
            for row in reader:
                pid = (row.get("id") or "").strip()
                if not pid:
                    continue

                name = (row.get("name") or "").strip()
                addr = (
                    row.get("formatted_address") or row.get("formattedAddress") or ""
                ).strip()
                lat = safe_float(row.get("lat") or row.get("latitude"), 0.0)
                lng = safe_float(
                    row.get("lng") or row.get("longtitude") or row.get("longitude"), 0.0
                )
                types = parse_types_cell(row.get("types") or "")
                rating = safe_float(
                    row.get("rating") or row.get("ratingScore"), 0.0
                )
                rcount = safe_int(
                    row.get("user_rating_count") or row.get("ratingCount"), 0
                )
                price_level = (
                    row.get("priceLevel") or row.get("price_level") or ""
                ).strip()
                business_status = (
                    row.get("businessStatus") or row.get("business_status") or ""
                ).strip()

                p = Place(
                    latitude=lat,
                    longtitude=lng,
                    id=pid,
                    name=name,
                    formattedAddress=addr,
                    types=types,
                    ratingCount=rcount,
                    ratingScore=rating,
                    priceLevel=price_level,
                    businessStatus=business_status,
                )
                self._by_id[pid] = p

        self.places = list(self._by_id.values())

    def load_directory(self, dir_path: str) -> None:
        """Bir klasördeki tüm .csv dosyalarını yükler."""
        for filename in sorted(os.listdir(dir_path)):
            if filename.lower().endswith(".csv"):
                self.load_csv(os.path.join(dir_path, filename))

    # --- LLD method isimleri ---

    def getAllPlaces(self) -> List[Place]:
        return list(self.places)

    def getPlacesByType(self, type: str) -> List[Place]:
        t = (type or "").strip().lower()
        if not t:
            return []
        return [p for p in self.places if p.has_type(t)]

    def getPlacesByRatingScore(self, minScore: float) -> List[Place]:
        return [p for p in self.places if p.ratingScore >= float(minScore)]

    def getPlaceByName(self, name: str) -> List[Place]:
        q = (name or "").strip().lower()
        if not q:
            return []
        return [p for p in self.places if q in (p.name or "").lower()]

    def getPlacesByPriceLevel(self, priceLevel: str) -> List[Place]:
        pl = (priceLevel or "").strip()
        if not pl:
            return []
        return [p for p in self.places if (p.priceLevel or "").strip() == pl]

    def getPlaceById(self, id) -> Optional[Place]:
        return self._by_id.get(str(id))

    def getPlacesByIds(self, ids: Iterable) -> List[Place]:
        out = []
        for x in ids or []:
            p = self.getPlaceById(x)
            if p is not None:
                out.append(p)
        return out
