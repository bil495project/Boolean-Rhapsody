import os
import time
import math
import csv
import json
import requests

# ===================== AYARLAR =====================
API_KEY = os.environ.get("GOOGLE_MAPS_API_KEY") or ""
OUTPUT_CSV = "ankara_places_new.csv"

ANKARA_CENTER_LAT = 39.92077
ANKARA_CENTER_LNG = 32.85411
INITIAL_SIDE_M   = 60_000
MAX_RESULT_COUNT = 20
DEBUG            = True
# ===================================================

def meters_per_degree(lat_deg: float):
    lat_rad = math.radians(lat_deg)
    m_per_deg_lat = 111_132.92 - 559.82*math.cos(2*lat_rad) + 1.175*math.cos(4*lat_rad) - 0.0023*math.cos(6*lat_rad)
    m_per_deg_lng = 111_412.84*math.cos(lat_rad) - 93.5*math.cos(3*lat_rad) + 0.118*math.cos(5*lat_rad)
    return m_per_deg_lat, m_per_deg_lng

def offset_latlng(lat, lng, dx_east_m, dy_north_m):
    m_per_deg_lat, m_per_deg_lng = meters_per_degree(lat)
    dlat = dy_north_m / m_per_deg_lat
    dlng = dx_east_m / m_per_deg_lng
    return lat + dlat, lng + dlng

def radius_for_square(side_m):
    return side_m * math.sqrt(2) / 2.0

def nearby_search_new(lat, lng, radius_m):
    url = "https://places.googleapis.com/v1/places:searchNearby"
    payload = {
        "maxResultCount": MAX_RESULT_COUNT,
        "locationRestriction": {
            "circle": {
                "center": {"latitude": lat, "longitude": lng},
                "radius": radius_m
            }
        }
    }
    headers = {
        "Content-Type": "application/json",
        "X-Goog-Api-Key": API_KEY,
        "X-Goog-FieldMask": "places.id,places.displayName.text,places.location.latitude,places.location.longitude"
    }
    r = requests.post(url, headers=headers, data=json.dumps(payload), timeout=30)
    data = r.json()
    places = data.get("places", [])
    saturated = len(places) >= MAX_RESULT_COUNT
    if DEBUG:
        print(f"[nearby] {len(places)} sonuç, radius={radius_m:.0f}m, saturated={saturated}")
    return places, saturated

class Square:
    def __init__(self, lat, lng, side_m, depth=0):
        self.lat, self.lng, self.side_m, self.depth = lat, lng, side_m, depth
    
    def subdivide(self):
        child_side = self.side_m / 2.0
        q = self.side_m / 4.0
        children = []
        for dy in (-q, q):
            for dx in (-q, q):
                c_lat, c_lng = offset_latlng(self.lat, self.lng, dx, dy)
                children.append(Square(c_lat, c_lng, child_side, self.depth + 1))
        return children

def extract_row(p):
    loc = p.get("location", {})
    name = (p.get("displayName") or {}).get("text")
    return {
        "id": p.get("id"),
        "displayName": name,
        "lat": loc.get("latitude"),
        "lng": loc.get("longitude")
    }

def write_csv(rows, path):
    fields = ["id","displayName","lat","lng"]
    with open(path, "a", newline="", encoding="utf-8-sig") as f:
        w = csv.DictWriter(f, fieldnames=fields)
        if f.tell() == 0:
            w.writeheader()
        w.writerows(rows)
    print(f"Yazıldı: {len(rows)} kayıt")

def collect_places_over_ankara():
    root = Square(ANKARA_CENTER_LAT, ANKARA_CENTER_LNG, INITIAL_SIDE_M, 0)
    stack = [root]  # DFS
    seen_ids = set()

    while stack:
        sq = stack.pop()
        rad = radius_for_square(sq.side_m)
        
        if DEBUG:
            print(f"\n[square] depth={sq.depth}, side={sq.side_m:.0f}m, center=({sq.lat:.5f},{sq.lng:.5f})")

        places, saturated = nearby_search_new(sq.lat, sq.lng, rad)
        
        if saturated:
            stack.extend(sq.subdivide())
        else:
            rows = []
            for p in places:
                pid = p.get("id")
                if pid and pid not in seen_ids:
                    seen_ids.add(pid)
                    rows.append(extract_row(p))
            write_csv(rows, OUTPUT_CSV)

        time.sleep(0.1)

if __name__ == "__main__":
    collect_places_over_ankara()