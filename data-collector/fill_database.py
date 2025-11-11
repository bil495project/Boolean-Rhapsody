# ankara_center_add.py
# hedef: ankara merkez ilçeleri (etimesgut, çankaya, pursaklar) için ek çekim
# kullanım:
#   export GMAPS_KEY="your_api_key"
#   python3 ankara_center_add.py
#
# gereksinim: python 3.8+  |  pip install requests

import os, time, math, csv, json, requests
from typing import List, Tuple

API_KEY = "AIzaSyCbrwph0Ff8voOTK-bJn3-r1zUuq-P1Osw"
if not API_KEY:
    raise SystemExit("GMAPS_KEY env yok. export GMAPS_KEY='...' yap.")

OUT_CSV = "places_ankara.csv"     # var olana append
SLEEP_BETWEEN_REQ = 0.45          # ~2.2 req/sn; sakin kal
MAX_RETRIES = 6
MAX_PER_REQ = 20                  # API sınırı

# merkez yoğun olduğu için küçük yarıçap + biraz yüksek overlap
REGIONS = {
    # isim: (lat_min, lat_max, lon_min, lon_max, radius_m, overlap)
    # bbox'lar Ankara için pratik, gerektikçe 1-2 kademe genişletilebilir.
    "Cankaya":    (39.80, 40.00, 32.70, 32.95, 800, 0.33),
    "Etimesgut":  (39.85, 40.08, 32.57, 32.73, 900, 0.30),
    "Pursaklar":  (39.95, 40.16, 32.85, 33.08, 900, 0.28),
}

CURATED_TYPES = [
    "restaurant","cafe","bar",
    "tourist_attraction","museum","art_gallery","park","zoo","aquarium","amusement_park",
    "shopping_mall","book_store","clothing_store","shoe_store","jewelry_store","department_store",
    "bakery","meal_takeaway","meal_delivery",
    "stadium","movie_theater","night_club",
    "library","university",
    "mosque","church","synagogue",
]

FIELDS = ",".join([
    "places.id",
    "places.displayName",
    "places.formattedAddress",
    "places.location",
    "places.types",
    "places.rating",
    "places.userRatingCount",
    "places.priceLevel",
    "places.businessStatus"
])
SEARCH_URL = f"https://places.googleapis.com/v1/places:searchNearby?fields={FIELDS}"

# ---------------- yardımcılar ----------------

def meters_to_deg_lat(m: float) -> float:
    return m / 111320.0

def meters_to_deg_lon(m: float, lat_deg: float) -> float:
    return m / (111320.0 * math.cos(math.radians(lat_deg)))

def generate_grid_in_bbox(lat_min, lat_max, lon_min, lon_max, radius_m, overlap=0.2) -> List[Tuple[float,float]]:
    step_factor = 1 - overlap
    lat_step = meters_to_deg_lat(radius_m * 2 * step_factor)
    avg_lat = (lat_min + lat_max) / 2.0
    lon_step = meters_to_deg_lon(radius_m * 2 * step_factor, avg_lat)

    centers = []
    lat = lat_min
    # üst sınırları da kapsasın diye küçük +epsilon
    while lat <= lat_max + 1e-9:
        lon = lon_min
        while lon <= lon_max + 1e-9:
            centers.append((lat, lon))
            lon += lon_step
        lat += lat_step
    return centers

def post_search(center_lat: float, center_lon: float, radius_m: float):
    body = {
        "includedTypes": CURATED_TYPES,      # tek batch
        "maxResultCount": MAX_PER_REQ,
        "locationRestriction": {
            "circle": {
                "center": {"latitude": center_lat, "longitude": center_lon},
                "radius": float(radius_m)
            }
        }
    }
    headers = {"Content-Type": "application/json", "X-Goog-Api-Key": API_KEY}
    return requests.post(SEARCH_URL, headers=headers, json=body, timeout=30)

# ---------------- csv/dedup ----------------

fieldnames = [
    "id","name","formatted_address","lat","lng","types",
    "rating","user_rating_count","price_level","business_status","layer"
]

seen_ids = set()
if os.path.exists(OUT_CSV) and os.stat(OUT_CSV).st_size > 0:
    with open(OUT_CSV, newline="", encoding="utf-8") as f:
        for r in csv.DictReader(f):
            seen_ids.add(r["id"])

csv_fh = open(OUT_CSV, "a", newline="", encoding="utf-8")
writer = csv.DictWriter(csv_fh, fieldnames=fieldnames)
if os.stat(OUT_CSV).st_size == 0:
    writer.writeheader()

# ---------------- ana akış ----------------

total_req = 0
total_new = 0

for region, (lat_min, lat_max, lon_min, lon_max, radius_m, overlap) in REGIONS.items():
    centers = generate_grid_in_bbox(lat_min, lat_max, lon_min, lon_max, radius_m, overlap=overlap)
    print(f"[{region}] centers={len(centers)} R={radius_m}m overlap={overlap}")

    # verimsiz merkezleri hızlı geçmek için: peş peşe N merkezden 0 yeni geldiyse "skip window"
    zero_streak = 0
    ZERO_STREAK_LIMIT = 6

    for ci, (lat, lon) in enumerate(centers, 1):
        if zero_streak >= ZERO_STREAK_LIMIT:
            # büyük ihtimal 20’lik tavan hep aynı yeri veriyor; grid üstünde ileri atla
            zero_streak = 0
            continue

        backoff = 1.0
        for attempt in range(MAX_RETRIES):
            try:
                resp = post_search(lat, lon, radius_m)
            except requests.RequestException as e:
                print(f"[{region}] net hatası {e}; attempt {attempt+1}/{MAX_RETRIES}")
                time.sleep(backoff); backoff *= 2
                continue

            total_req += 1
            if resp.status_code == 200:
                data = resp.json()
                places = data.get("places", [])
                new_count = 0

                for p in places:
                    pid = p.get("id")
                    if not pid or pid in seen_ids:
                        continue
                    loc = p.get("location", {}) or {}
                    plat, plon = loc.get("latitude"), loc.get("longitude")

                    writer.writerow({
                        "id": pid,
                        "name": (p.get("displayName", {}) or {}).get("text") if isinstance(p.get("displayName"), dict) else p.get("displayName"),
                        "formatted_address": p.get("formattedAddress") or "",
                        "lat": plat, "lng": plon,
                        "types": json.dumps(p.get("types", []), ensure_ascii=False),
                        "rating": p.get("rating"),
                        "user_rating_count": p.get("userRatingCount") or p.get("userRatingsTotal"),
                        "price_level": p.get("priceLevel"),
                        "business_status": p.get("businessStatus"),
                        "layer": f"CENTER_{region.upper()}"
                    })
                    csv_fh.flush()
                    seen_ids.add(pid)
                    new_count += 1
                    total_new += 1

                zero_streak = 0 if new_count > 0 else zero_streak + 1
                print(f"[{region}] {ci}/{len(centers)} center={lat:.5f},{lon:.5f} got={len(places)} new={new_count} "
                      f"total_new={total_new} req={total_req} zero_streak={zero_streak}")
                time.sleep(SLEEP_BETWEEN_REQ)
                break

            elif resp.status_code in (429, 500, 503):
                wait = backoff + attempt * 0.5
                print(f"[{region}] {resp.status_code} backoff {wait:.1f}s (attempt {attempt+1})")
                time.sleep(wait); backoff *= 2
                continue
            else:
                print(f"[{region}] {resp.status_code} unexpected: {resp.text[:240]}")
                time.sleep(0.8)
                break

csv_fh.close()
print(f"[+] bitti. yeni eklenen: {total_new} | toplam req: {total_req} | dosya: {OUT_CSV}")
