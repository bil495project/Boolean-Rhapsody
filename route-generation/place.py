"""Place dataclass — LLD uyumlu POI modeli."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import List


@dataclass(eq=True, frozen=False)
class Place:
    latitude: float
    longtitude: float  # LLD'deki typo'yu koruyoruz
    id: str
    name: str
    formattedAddress: str = ""
    types: List[str] = field(default_factory=list)
    ratingCount: int = 0
    ratingScore: float = 0.0
    priceLevel: str = ""
    businessStatus: str = ""

    def has_type(self, t: str) -> bool:
        if not t:
            return False
        tt = t.strip().lower()
        return any((x or "").strip().lower() == tt for x in self.types)
