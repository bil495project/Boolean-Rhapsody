import argparse
import csv
import json
import math
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[1]
ANKARA_PLACES_DIR = REPO_ROOT / "backend" / "src" / "main" / "resources" / "ankara_places"
PHOTO_IDS_CSV = REPO_ROOT / "data-collector" / "place_photo_ids.csv"
PHOTO_DIR = REPO_ROOT / "frontend" / "public" / "place-photos"

API_KEY_ENV = "GOOGLE_PLACES_API_KEY"
NEARBY_SEARCH_URL = "https://places.googleapis.com/v1/places:searchNearby"
ANKARA_CENTER = {"latitude": 39.9334, "longitude": 32.8597}
ANKARA_RADIUS_M = 30000.0
PHOTO_WIDTH_PX = 800
REQUEST_DELAY_SECONDS = 0.12

PLACE_CSV_FIELDS = [
    "id",
    "name",
    "formatted_address",
    "lat",
    "lng",
    "types",
    "rating",
    "user_rating_count",
    "price_level",
]

CATEGORY_TO_CSV = {
    "bars_nightclubs": "bars_nightclubs.csv",
    "cafes_desserts": "cafes_desserts.csv",
    "historic_places": "historic_places.csv",
    "hotels": "hotels.csv",
    "landmarks": "landmarks.csv",
    "parks": "parks.csv",
    "restaurants": "restaurants.csv",
}

CATEGORY_PRIORITY = [
    "hotels",
    "bars_nightclubs",
    "restaurants",
    "cafes_desserts",
    "parks",
    "historic_places",
    "landmarks",
]

TYPE_TO_CATEGORY = {
    "lodging": "hotels",
    "hotel": "hotels",
    "bar": "bars_nightclubs",
    "night_club": "bars_nightclubs",
    "pub": "bars_nightclubs",
    "restaurant": "restaurants",
    "turkish_restaurant": "restaurants",
    "middle_eastern_restaurant": "restaurants",
    "fast_food_restaurant": "restaurants",
    "fine_dining_restaurant": "restaurants",
    "food": "restaurants",
    "meal_takeaway": "restaurants",
    "cafe": "cafes_desserts",
    "coffee_shop": "cafes_desserts",
    "bakery": "cafes_desserts",
    "dessert_shop": "cafes_desserts",
    "park": "parks",
    "national_park": "parks",
    "garden": "parks",
    "museum": "historic_places",
    "historical_place": "historic_places",
    "historical_landmark": "historic_places",
    "cultural_landmark": "historic_places",
    "mosque": "historic_places",
    "church": "historic_places",
    "synagogue": "historic_places",
    "place_of_worship": "historic_places",
    "monument": "landmarks",
    "tourist_attraction": "landmarks",
    "landmark": "landmarks",
    "sculpture": "landmarks",
    "plaza": "landmarks",
}


@dataclass(frozen=True)
class SearchBatch:
    index: int
    center: dict[str, float]


@dataclass
class PlaceRow:
    place_id: str
    name: str
    formatted_address: str
    lat: float | None
    lng: float | None
    types: list[str]
    rating: float | None
    rating_count: int | None
    price_level: str
    business_status: str
    primary_photo_name: str | None
    source: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Discover mixed popular Ankara places with Nearby Search, immediately "
            "categorize them, append CSV rows, and download photos."
        )
    )
    parser.add_argument(
        "query_count",
        nargs="?",
        type=int,
        default=1,
        help="Number of Nearby Search API calls to make. Example: 100 sends 100 queries.",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=0,
        help="Max number of new places to add. 0 means no add limit.",
    )
    parser.add_argument(
        "--min-rating-count",
        type=int,
        default=100,
        help="Only add places with at least this many Google ratings.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Fetch and show actions without writing CSV/photo files.",
    )
    return parser.parse_args()


def log(message: str) -> None:
    encoding = sys.stdout.encoding or "utf-8"
    print(message.encode(encoding, errors="replace").decode(encoding))


def load_existing_place_ids() -> set[str]:
    ids: set[str] = set()
    for path in ANKARA_PLACES_DIR.glob("*.csv"):
        with path.open("r", encoding="utf-8-sig", newline="") as csv_file:
            reader = csv.DictReader(csv_file)
            for row in reader:
                place_id = (row.get("id") or "").strip()
                if place_id:
                    ids.add(place_id)
    return ids


def load_photo_ids() -> dict[tuple[str, str], str]:
    if not PHOTO_IDS_CSV.exists():
        return {}
    with PHOTO_IDS_CSV.open("r", encoding="utf-8-sig", newline="") as csv_file:
        reader = csv.DictReader(csv_file)
        return {
            (row["table_name"], row["place_id"]): row["primary_photo_name"]
            for row in reader
            if row.get("table_name") and row.get("place_id") and row.get("primary_photo_name")
        }


def write_photo_ids(photo_ids: dict[tuple[str, str], str]) -> None:
    PHOTO_IDS_CSV.parent.mkdir(parents=True, exist_ok=True)
    with PHOTO_IDS_CSV.open("w", encoding="utf-8", newline="") as csv_file:
        writer = csv.DictWriter(csv_file, fieldnames=["table_name", "place_id", "primary_photo_name"])
        writer.writeheader()
        for (table, place_id), photo_name in sorted(photo_ids.items()):
            writer.writerow(
                {
                    "table_name": table,
                    "place_id": place_id,
                    "primary_photo_name": photo_name,
                }
            )


def search_centers(query_count: int) -> list[dict[str, float]]:
    if query_count <= 1:
        return [ANKARA_CENTER]

    centers = [ANKARA_CENTER]
    golden_angle = math.pi * (3 - math.sqrt(5))
    for index in range(1, query_count):
        ratio = index / max(query_count - 1, 1)
        distance_m = ANKARA_RADIUS_M * math.sqrt(ratio)
        angle = index * golden_angle
        lat = ANKARA_CENTER["latitude"] + meters_to_lat_degrees(distance_m * math.sin(angle))
        lng = ANKARA_CENTER["longitude"] + meters_to_lng_degrees(
            distance_m * math.cos(angle),
            ANKARA_CENTER["latitude"],
        )
        centers.append({"latitude": lat, "longitude": lng})
    return centers


def meters_to_lat_degrees(meters: float) -> float:
    return meters / 111_320.0


def meters_to_lng_degrees(meters: float, latitude: float) -> float:
    return meters / (111_320.0 * math.cos(math.radians(latitude)))


def http_json_request(url: str, api_key: str, payload: dict[str, Any]) -> dict[str, Any]:
    data = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=data,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "X-Goog-Api-Key": api_key,
            "X-Goog-FieldMask": (
                "places.id,"
                "places.displayName,"
                "places.formattedAddress,"
                "places.location,"
                "places.types,"
                "places.rating,"
                "places.userRatingCount,"
                "places.priceLevel,"
                "places.businessStatus,"
                "places.photos.name"
            ),
        },
    )
    with urllib.request.urlopen(request, timeout=40) as response:
        return json.loads(response.read().decode("utf-8"))


def fetch_nearby_batch(batch: SearchBatch, api_key: str) -> list[PlaceRow]:
    payload = {
        "rankPreference": "POPULARITY",
        "locationRestriction": {
            "circle": {
                "center": batch.center,
                "radius": ANKARA_RADIUS_M,
            }
        },
    }
    data = http_json_request(NEARBY_SEARCH_URL, api_key, payload)
    return [
        place_from_google(raw, f"mixed_nearby_{batch.index}")
        for raw in data.get("places") or []
        if isinstance(raw, dict)
    ]


def place_from_google(raw: dict[str, Any], source: str) -> PlaceRow:
    display_name = raw.get("displayName") or {}
    location = raw.get("location") or {}
    photos = raw.get("photos") or []
    photo_name = photos[0].get("name") if photos and isinstance(photos[0], dict) else None

    return PlaceRow(
        place_id=raw.get("id") or "",
        name=display_name.get("text") if isinstance(display_name, dict) else str(display_name),
        formatted_address=raw.get("formattedAddress") or "",
        lat=location.get("latitude"),
        lng=location.get("longitude"),
        types=list(raw.get("types") or []),
        rating=raw.get("rating"),
        rating_count=raw.get("userRatingCount"),
        price_level=raw.get("priceLevel") or "",
        business_status=raw.get("businessStatus") or "",
        primary_photo_name=photo_name if isinstance(photo_name, str) and photo_name else None,
        source=source,
    )


def passes_filter(place: PlaceRow, min_rating_count: int) -> bool:
    return bool(place.place_id) and (place.rating_count or 0) >= min_rating_count


def infer_category(place: PlaceRow) -> str | None:
    categories = {
        TYPE_TO_CATEGORY[normalized]
        for type_name in place.types
        for normalized in [type_name.strip().lower().replace(" ", "_")]
        if normalized in TYPE_TO_CATEGORY
    }
    for category in CATEGORY_PRIORITY:
        if category in categories:
            return category
    return None


def csv_path_for_category(category: str) -> Path:
    return ANKARA_PLACES_DIR / CATEGORY_TO_CSV[category]


def table_name_for_category(category: str) -> str:
    return f"{category}_places"


def append_place(place: PlaceRow, category: str) -> None:
    path = csv_path_for_category(category)
    with path.open("a", encoding="utf-8", newline="") as csv_file:
        writer = csv.DictWriter(csv_file, fieldnames=PLACE_CSV_FIELDS)
        writer.writerow(
            {
                "id": place.place_id,
                "name": place.name,
                "formatted_address": place.formatted_address,
                "lat": "" if place.lat is None else place.lat,
                "lng": "" if place.lng is None else place.lng,
                "types": json.dumps(place.types, ensure_ascii=False),
                "rating": "" if place.rating is None else place.rating,
                "user_rating_count": "" if place.rating_count is None else place.rating_count,
                "price_level": place.price_level,
            }
        )


def download_photo(photo_name: str, output_path: Path, api_key: str) -> bool:
    if output_path.exists():
        return False

    encoded_name = urllib.parse.quote(photo_name, safe="/")
    request = urllib.request.Request(
        f"https://places.googleapis.com/v1/{encoded_name}/media"
        f"?maxWidthPx={PHOTO_WIDTH_PX}&key={urllib.parse.quote(api_key)}"
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        data = response.read()

    output_path.parent.mkdir(parents=True, exist_ok=True)
    temp_path = output_path.with_suffix(output_path.suffix + ".tmp")
    temp_path.write_bytes(data)
    temp_path.replace(output_path)
    return True


def process_place(
    place: PlaceRow,
    existing_place_ids: set[str],
    photo_ids: dict[tuple[str, str], str],
    api_key: str,
    dry_run: bool,
) -> tuple[str, bool]:
    if place.place_id in existing_place_ids:
        return "exists", False

    category = infer_category(place)
    if category is None:
        return "uncategorized", False

    table_name = table_name_for_category(category)
    downloaded = False
    if not dry_run:
        append_place(place, category)
        existing_place_ids.add(place.place_id)

    if place.primary_photo_name:
        photo_ids[(table_name, place.place_id)] = place.primary_photo_name
        output_path = PHOTO_DIR / table_name / f"{place.place_id}.jpg"
        if not dry_run:
            downloaded = download_photo(place.primary_photo_name, output_path, api_key)
            if downloaded:
                time.sleep(REQUEST_DELAY_SECONDS)

    log(
        f"  added: {place.name} -> {CATEGORY_TO_CSV[category]} "
        f"({place.rating}, {place.rating_count} ratings) photo={'yes' if downloaded else 'no'}"
    )
    return "added", downloaded


def main() -> int:
    args = parse_args()
    if args.query_count <= 0:
        print("query_count must be a positive integer.", file=sys.stderr)
        return 2
    if args.limit < 0:
        print("--limit must be 0 or a positive integer.", file=sys.stderr)
        return 2
    if args.min_rating_count < 0:
        print("--min-rating-count must be 0 or a positive integer.", file=sys.stderr)
        return 2

    api_key = os.environ.get(API_KEY_ENV)
    if not api_key:
        print(f"Missing {API_KEY_ENV} environment variable.", file=sys.stderr)
        return 1

    existing_place_ids = load_existing_place_ids()
    photo_ids = load_photo_ids()
    batches = [
        SearchBatch(index=index, center=center)
        for index, center in enumerate(search_centers(args.query_count), start=1)
    ]

    added = 0
    exists = 0
    uncategorized = 0
    filtered = 0
    failed = 0
    photos_downloaded = 0

    log(f"Dry run: {'yes' if args.dry_run else 'no'}")
    log(
        f"Running {len(batches)} mixed Nearby Search requests within "
        f"{int(ANKARA_RADIUS_M / 1000)} km. Retry disabled."
    )

    try:
        for batch in batches:
            if args.limit > 0 and added >= args.limit:
                break

            try:
                places = fetch_nearby_batch(batch, api_key)
            except KeyboardInterrupt:
                raise
            except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, OSError, json.JSONDecodeError) as error:
                failed += 1
                log(f"[{batch.index}/{len(batches)}] failed mixed_nearby_{batch.index}: {error}")
                continue

            kept = 0
            batch_added = 0
            for place in places:
                if not passes_filter(place, args.min_rating_count):
                    filtered += 1
                    continue
                kept += 1
                status, downloaded = process_place(place, existing_place_ids, photo_ids, api_key, args.dry_run)
                if status == "added":
                    added += 1
                    batch_added += 1
                    photos_downloaded += 1 if downloaded else 0
                elif status == "exists":
                    exists += 1
                elif status == "uncategorized":
                    uncategorized += 1

                if args.limit > 0 and added >= args.limit:
                    break

            if not args.dry_run:
                write_photo_ids(photo_ids)

            log(
                f"[{batch.index}/{len(batches)}] got={len(places)} kept={kept} "
                f"added={batch_added}"
            )
            time.sleep(REQUEST_DELAY_SECONDS)
    except KeyboardInterrupt:
        log("Interrupted. Changes already written up to the last completed place/batch.")
    finally:
        if not args.dry_run:
            write_photo_ids(photo_ids)

    log(
        "Done. "
        f"added={added}, exists={exists}, uncategorized={uncategorized}, "
        f"filtered={filtered}, photos_downloaded={photos_downloaded}, failed={failed}"
    )
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
