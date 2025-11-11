# kullanım:
#   export GMAPS_KEY="..."
#   python3 places_by_region.py --mode polygons --geojson regions/ankara_ilceler.geojson --target 3000
#
# alternatif (bbox modu):
#   python3 places_by_region.py --mode bboxes --target 3000
#
# python 3.8+  ve requests yeterli.

import os, sys, json, csv, math, time, argparse, pathlib, requests
from typing import List, Tuple, Dict, Any

API_KEY = os.environ.get("GMAPS_KEY")
if not API_KEY:
    print("GMAPS_KEY yok. export GMAPS_KEY='...'", file=sys.stderr)
    sys.exit(1)

OUT_DIR = pathlib.Path("data/ankara")
OUT_DIR.mkdir(parents=True, exist_ok=True)
MASTER_CSV = OUT_DIR / "master.csv"

# curated tipler: gezi-değer şeyler
CURATED_TYPES = [
    "restaurant","cafe","bar",
    "tourist_attraction","museum","art_gallery","park","zoo","aquarium","amusement_park",
    "shopping_mall","book_store","clothing_store","shoe_store","jewelry_store","department_store",
    "bakery","meal_takeaway","meal_delivery",
    "stadium","movie_theater","night_club",
    "library","university",
    "mosque","church","synagogue",
    "point_of_interest"
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

MAX_PER_REQ = 20
SLEEP_BETWEEN_REQ = 0.35   # ~3 req/sn. gerekirse 0.5 yap
MAX_RETRIES = 6
OVERLAP = 0.25             # daireler arası örtüşme

# merkezden metreyi dereceye çeviren yardımcılar
def meters_to_deg_lat(m: float) -> float:
    return m / 111320.0

def meters_to_deg_lon(m: float, lat_deg: float) -> float:
    return m / (111320.0 * math.cos(math.radians(lat_deg)))

# ray-casting point in polygon (Polygon: [ [x,y], ... ])
def point_in_ring(x: float, y: float, ring: List[Tuple[float,float]]) -> bool:
    inside = False
    n = len(ring)
    for i in range(n):
        x1, y1 = ring[i]
        x2, y2 = ring[(i+1) % n]
        # en basit ray-casting (lon=x, lat=y)
        if ((y1 > y) != (y2 > y)) and (x < (x2 - x1) * (y - y1) / (y2 - y1 + 1e-15) + x1):
            inside = not inside
    return inside

# MultiPolygon desteği: polygon = [outer, hole1, hole2, ...]; multipolygon = [polygon1, polygon2, ...]
def point_in_polygon(x: float, y: float, geom: Dict[str, Any]) -> bool:
    gtype = geom["type"]
    if gtype == "Polygon":
        # coordinates: [ [ [lon,lat], ... ], [hole...], ...]
        rings = geom["coordinates"]
        outer = [(pt[0], pt[1]) for pt in rings[0]]
        if not point_in_ring(x, y, outer):
            return False
        # delikler (holes)
        for hole in rings[1:]:
            if point_in_ring(x, y, [(pt[0], pt[1]) for pt in hole]):
                return False
        return True
    elif gtype == "MultiPolygon":
        for poly in geom["coordinates"]:
            outer = [(pt[0], pt[1]) for pt in poly[0]]
            if point_in_ring(x, y, outer):
                hole_hit = False
                for hole in poly[1:]:
                    if point_in_ring(x, y, [(pt[0], pt[1]) for pt in hole]):
                        hole_hit = True; break
                if not hole_hit:
                    return True
        return False
    else:
        return False

def bbox_of_geom(geom: Dict[str, Any]) -> Tuple[float,float,float,float]:
    xs, ys = [], []
    if geom["type"] == "Polygon":
        for ring in geom["coordinates"]:
            for x,y in ring:
                xs.append(x); ys.append(y)
    elif geom["type"] == "MultiPolygon":
        for poly in geom["coordinates"]:
            for ring in poly:
                for x,y in ring:
                    xs.append(x); ys.append(y)
    return (min(xs), min(ys), max(xs), max(ys))

def generate_grid_in_bbox(lat_min, lat_max, lon_min, lon_max, radius_m, overlap=0.2) -> List[Tuple[float,float]]:
    step_factor = 1 - overlap
    lat_step = meters_to_deg_lat(radius_m * 2 * step_factor)
    avg_lat = (lat_min + lat_max) / 2.0
    lon_step = meters_to_deg_lon(radius_m * 2 * step_factor, avg_lat)
    centers = []
    lat = lat_min
    while lat <= lat_max:
        lon = lon_min
        while lon <= lon_max:
            centers.append((lat, lon))
            lon += lon_step
        lat += lat_step
    return centers

def chunk(lst, n):
    for i in range(0, len(lst), n):
        yield lst[i:i+n]

def post_search(center_lat: float, center_lon: float, radius_m: float, types: List[str]):
    body = {
        "includedTypes": types,
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

def ensure_writer(path: pathlib.Path, extra_fields=None):
    path.parent.mkdir(parents=True, exist_ok=True)
    fexists = path.exists() and path.stat().st_size > 0
    fh = open(path, "a", newline="", encoding="utf-8")
    base = ["id","name","formatted_address","lat","lng","types","rating","user_rating_count","price_level","business_status","region"]
    if extra_fields:
        base += extra_fields
    writer = csv.DictWriter(fh, fieldnames=base)
    if not fexists:
        writer.writeheader()
    return fh, writer

def load_master_seen():
    seen = set()
    if MASTER_CSV.exists():
        with open(MASTER_CSV, newline="", encoding="utf-8") as f:
            for r in csv.DictReader(f):
                seen.add(r["id"])
    return seen

def run_polygon_mode(geojson_path: str, target_total: int, base_radius=1800, hotspot_radius=900):
    with open(geojson_path, encoding="utf-8") as f:
        gj = json.load(f)

    # master writer
    master_fh, master_w = ensure_writer(MASTER_CSV)
    seen_ids = load_master_seen()

    type_batches = list(chunk(CURATED_TYPES, 4))
    total_written = 0
    total_req = 0

    # bazı ilçelere daha sık örnekleme (ör: Çankaya, Altındağ, Yenimahalle)
    dense_names = {"cankaya","altindag","yenimahalle","mamak","keçiören","kecioren","etimesgut","sincan","golbasi","gölbaşı","ulus","kizilay","kızılay"}
    # normalize helper
    def norm(s): return s.lower().replace("ı","i").replace("ş","s").replace("ğ","g").replace("ç","c").replace("ö","o").replace("ü","u")

    for feat in gj["features"]:
        props = feat.get("properties", {})
        name = props.get("name") or props.get("ilce") or props.get("Ilce") or "Region"
        nname = norm(name)
        geom = feat["geometry"]
        lon_min, lat_min, lon_max, lat_max = bbox_of_geom(geom)

        # grid üret
        rad = hotspot_radius if any(x in nname for x in dense_names) else base_radius
        bbox_centers = generate_grid_in_bbox(lat_min, lat_max, lon_min, lon_max, rad, overlap=OVERLAP)
        # poligon filtresi
        centers = [(la, lo) for (la, lo) in bbox_centers if point_in_polygon(lo, la, geom)]
        print(f"[{name}] bbox_centers={len(bbox_centers)} -> inside={len(centers)} radius={rad}")

        # ilçe csv writer
        region_csv = OUT_DIR / f"{name.replace(' ','_')}.csv"
        rfh, rw = ensure_writer(region_csv)

        # resume: ilçe csv’dekileri de görmezden gel
        with open(region_csv, newline="", encoding="utf-8") as rf:
            for r in csv.DictReader(rf):
                seen_ids.add(r["id"])

        for ci, (lat, lon) in enumerate(centers, 1):
            if total_written >= target_total:
                break
            for bi, tbatch in enumerate(type_batches, 1):
                if total_written >= target_total:
                    break

                backoff = 1.0
                for attempt in range(MAX_RETRIES):
                    try:
                        resp = post_search(lat, lon, rad, tbatch)
                    except requests.RequestException as e:
                        print(f"[{name}] net hatası {e}; attempt {attempt+1}/{MAX_RETRIES}")
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
                            namep = p.get("displayName", {}).get("text") if isinstance(p.get("displayName"), dict) else p.get("displayName")
                            addr = p.get("formattedAddress")
                            loc = p.get("location", {}) or {}
                            plat, plon = loc.get("latitude"), loc.get("longitude")
                            types = p.get("types", [])
                            rating = p.get("rating")
                            urc = p.get("userRatingCount") or p.get("userRatingsTotal")
                            price = p.get("priceLevel")
                            bstat = p.get("businessStatus")

                            row = {
                                "id": pid,
                                "name": namep,
                                "formatted_address": addr or "",
                                "lat": plat, "lng": plon,
                                "types": json.dumps(types, ensure_ascii=False),
                                "rating": rating,
                                "user_rating_count": urc,
                                "price_level": price,
                                "business_status": bstat,
                                "region": name
                            }
                            rw.writerow(row)
                            master_w.writerow(row)
                            seen_ids.add(pid)
                            new_count += 1
                            total_written += 1

                        rfh.flush(); master_fh.flush()
                        print(f"[{name}] {ci}/{len(centers)} batch {bi}/{len(type_batches)} "
                              f"got={len(places)} new={new_count} total_written={total_written} req={total_req}")
                        time.sleep(SLEEP_BETWEEN_REQ)
                        break
                    elif resp.status_code in (429, 500, 503):
                        wait = backoff + attempt * 0.5
                        print(f"[{name}] {resp.status_code} backoff {wait:.1f}s (attempt {attempt+1})")
                        time.sleep(wait); backoff *= 2
                        continue
                    else:
                        print(f"[{name}] {resp.status_code} unexpected: {resp.text[:240]}")
                        time.sleep(0.8)
                        break
        rfh.close()

    master_fh.close()
    print(f"[+] bitti. toplam yazılan: {total_written} (master: {MASTER_CSV})")

def run_bbox_mode(target_total: int, base_radius=1800, hotspot_radius=900):
    """
    bbox modu: elinle ilçeler/semtler için dikdörtgenler tanımlarsın.
    lütfen koordinatları kendin doldur: örnek şablon bıraktım.
    """
    REGIONS = {
        # "Cankaya":  (lat_min, lat_max, lon_min, lon_max),
        # "Altindag": (...),
        # "Kecioren": (...),
        # "Yenimahalle": (...),
        # "Mamak": (...),
        # "Etimesgut": (...),
        # "Sincan": (...),
        # "Golbasi": (...),
        # örnek placeholder (uydurma), doldurman lazım:
        "Kizilay": (39.90, 39.94, 32.83, 32.88),
        "Tunali_Kugulu": (39.90, 39.92, 32.85, 32.88),
        "Bahcelievler_7Cd": (39.91, 39.93, 32.81, 32.84),
        "Anitkabir": (39.92, 39.93, 32.83, 32.85),
    }

    # master
    master_fh, master_w = ensure_writer(MASTER_CSV)
    seen_ids = load_master_seen()
    type_batches = list(chunk(CURATED_TYPES, 4))
    total_written = 0; total_req = 0

    for name, (lat_min, lat_max, lon_min, lon_max) in REGIONS.items():
        rad = hotspot_radius if "Kizilay" in name or "Tunali" in name else base_radius
        centers = generate_grid_in_bbox(lat_min, lat_max, lon_min, lon_max, rad, overlap=OVERLAP)
        print(f"[{name}] centers={len(centers)} radius={rad}")

        region_csv = OUT_DIR / f"{name}.csv"
        rfh, rw = ensure_writer(region_csv)

        # resume
        with open(region_csv, newline="", encoding="utf-8") as rf:
            for r in csv.DictReader(rf):
                seen_ids.add(r["id"])

        for ci, (lat, lon) in enumerate(centers, 1):
            if total_written >= target_total:
                break
            for bi, tbatch in enumerate(type_batches, 1):
                if total_written >= target_total:
                    break
                backoff = 1.0
                for attempt in range(MAX_RETRIES):
                    try:
                        resp = post_search(lat, lon, rad, tbatch)
                    except requests.RequestException as e:
                        print(f"[{name}] net hatası {e}; attempt {attempt+1}/{MAX_RETRIES}")
                        time.sleep(backoff); backoff *= 2
                        continue

                    total_req += 1
                    if resp.status_code == 200:
                        data = resp.json()
                        places = data.get("places", [])
                        new_count = 0
                        for p in places:
                            pid = p.get("id")
                            if not pid or pid in seen_ids: continue
                            namep = p.get("displayName", {}).get("text") if isinstance(p.get("displayName"), dict) else p.get("displayName")
                            addr = p.get("formattedAddress")
                            loc = p.get("location", {}) or {}
                            plat, plon = loc.get("latitude"), loc.get("longitude")
                            types = p.get("types", [])
                            rating = p.get("rating")
                            urc = p.get("userRatingCount") or p.get("userRatingsTotal")
                            price = p.get("priceLevel")
                            bstat = p.get("businessStatus")
                            row = {
                                "id": pid, "name": namep, "formatted_address": addr or "",
                                "lat": plat, "lng": plon,
                                "types": json.dumps(types, ensure_ascii=False),
                                "rating": rating, "user_rating_count": urc,
                                "price_level": price, "business_status": bstat,
                                "region": name
                            }
                            rw.writerow(row); master_w.writerow(row)
                            seen_ids.add(pid); new_count += 1; total_written += 1
                        rfh.flush(); master_fh.flush()
                        print(f"[{name}] {ci}/{len(centers)} batch {bi} got={len(places)} new={new_count} total={total_written} req={total_req}")
                        time.sleep(SLEEP_BETWEEN_REQ)
                        break
                    elif resp.status_code in (429,500,503):
                        wait = 1.0 + attempt * 0.5
                        print(f"[{name}] {resp.status_code} backoff {wait:.1f}s (attempt {attempt+1})")
                        time.sleep(wait)
                        continue
                    else:
                        print(f"[{name}] {resp.status_code} unexpected: {resp.text[:240]}")
                        time.sleep(0.8)
                        break
        rfh.close()

    master_fh.close()
    print(f"[+] bitti. toplam yazılan: {total_written} (master: {MASTER_CSV})")

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--mode", choices=["polygons","bboxes"], required=True, help="region belirleme modu")
    ap.add_argument("--geojson", help="poligon modu için FeatureCollection geojson yolu")
    ap.add_argument("--target", type=int, default=3000, help="toplam hedef kayıt sayısı")
    ap.add_argument("--base_radius", type=int, default=1800, help="genel grid yarıçapı (m)")
    ap.add_argument("--hotspot_radius", type=int, default=900, help="yoğun bölgelerde yarıçap (m)")
    args = ap.parse_args()

    if args.mode == "polygons":
        if not args.geojson:
            print("--geojson zorunlu (polygons modu için).", file=sys.stderr); sys.exit(2)
        run_polygon_mode(args.geojson, args.target, base_radius=args.base_radius, hotspot_radius=args.hotspot_radius)
    else:
        run_bbox_mode(args.target, base_radius=args.base_radius, hotspot_radius=args.hotspot_radius)

if __name__ == "__main__":
    main()
