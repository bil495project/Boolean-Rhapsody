"""Yardımcı fonksiyonlar: haversine, travel_seconds, parse helpers."""

from __future__ import annotations

import json
import math
from typing import List, Optional


def haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    R = 6371.0088
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = (
        math.sin(dlat / 2) ** 2
        + math.cos(math.radians(lat1))
        * math.cos(math.radians(lat2))
        * math.sin(dlon / 2) ** 2
    )
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return R * c


def km_to_meters(km: float) -> float:
    return km * 1000.0


def travel_seconds(distance_km: float, mode: str) -> int:
    mode = (mode or "driving").lower()
    if mode == "walking":
        kmh = 4.8
    elif mode == "cycling":
        kmh = 14.0
    else:
        kmh = 25.0  # driving default (şehir içi)
    hours = distance_km / max(kmh, 1e-9)
    return int(round(hours * 3600.0))


def safe_float(x: Optional[str], default: float = 0.0) -> float:
    try:
        if x is None:
            return default
        s = str(x).strip()
        if not s:
            return default
        return float(s)
    except Exception:
        return default


def safe_int(x: Optional[str], default: int = 0) -> int:
    try:
        if x is None:
            return default
        s = str(x).strip()
        if not s:
            return default
        return int(float(s))
    except Exception:
        return default


def parse_types_cell(cell: str) -> List[str]:
    if cell is None:
        return []
    s = str(cell).strip()
    if not s:
        return []
    # JSON liste string'i  (ör: ["hotel","lodging",...])
    try:
        v = json.loads(s)
        if isinstance(v, list):
            return [str(t).strip() for t in v if str(t).strip()]
    except Exception:
        pass
    # alternatif: "a|b|c" veya "a,b,c"
    if "|" in s:
        return [t.strip() for t in s.split("|") if t.strip()]
    if "," in s:
        return [t.strip() for t in s.split(",") if t.strip()]
    return [s]
