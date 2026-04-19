import argparse
import csv
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
ANKARA_PLACES_DIR = REPO_ROOT / "backend" / "src" / "main" / "resources" / "ankara_places"
CANDIDATES_CSV = REPO_ROOT / "data-collector" / "popular_ankara_place_candidates.csv"
PHOTO_IDS_CSV = REPO_ROOT / "data-collector" / "place_photo_ids.csv"
PHOTO_DIR = REPO_ROOT / "frontend" / "public" / "place-photos"

API_KEY_ENV = "GOOGLE_PLACES_API_KEY"
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


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Promote reviewed popular Ankara candidates to app CSV categories and download photos."
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=0,
        help="Max number of new places to promote. 0 means no limit.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Show what would be promoted without writing CSV/photo files.",
    )
    parser.add_argument(
        "--include-existing",
        action="store_true",
        help="Also process candidates marked already_in_system=yes for missing photos only.",
    )
    return parser.parse_args()


def log(message: str) -> None:
    encoding = sys.stdout.encoding or "utf-8"
    print(message.encode(encoding, errors="replace").decode(encoding))


def parse_types(raw: str) -> list[str]:
    if not raw:
        return []
    try:
        parsed = json.loads(raw)
        if isinstance(parsed, list):
            return [str(item).strip() for item in parsed if str(item).strip()]
    except json.JSONDecodeError:
        pass
    return [item.strip() for item in raw.split(",") if item.strip()]


def normalize_type(value: str) -> str:
    return value.strip().lower().replace(" ", "_")


def infer_category(row: dict[str, str]) -> str | None:
    suggested = (row.get("suggested_category") or "").strip()
    if suggested in CATEGORY_TO_CSV:
        return suggested

    categories = {
        TYPE_TO_CATEGORY[normalized]
        for type_name in parse_types(row.get("types") or "")
        for normalized in [normalize_type(type_name)]
        if normalized in TYPE_TO_CATEGORY
    }
    for category in CATEGORY_PRIORITY:
        if category in categories:
            return category
    return None


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


def csv_path_for_category(category: str) -> Path:
    return ANKARA_PLACES_DIR / CATEGORY_TO_CSV[category]


def table_name_for_category(category: str) -> str:
    return f"{category}_places"


def append_place(row: dict[str, str], category: str) -> None:
    path = csv_path_for_category(category)
    with path.open("a", encoding="utf-8", newline="") as csv_file:
        writer = csv.DictWriter(csv_file, fieldnames=PLACE_CSV_FIELDS)
        writer.writerow(
            {
                "id": row.get("id", ""),
                "name": row.get("name", ""),
                "formatted_address": row.get("formatted_address", ""),
                "lat": row.get("lat", ""),
                "lng": row.get("lng", ""),
                "types": row.get("types", ""),
                "rating": row.get("rating", ""),
                "user_rating_count": row.get("user_rating_count", ""),
                "price_level": row.get("price_level", ""),
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


def read_candidates() -> list[dict[str, str]]:
    if not CANDIDATES_CSV.exists():
        raise SystemExit(f"Missing candidates CSV: {CANDIDATES_CSV}")
    with CANDIDATES_CSV.open("r", encoding="utf-8-sig", newline="") as csv_file:
        return list(csv.DictReader(csv_file))


def main() -> int:
    args = parse_args()
    if args.limit < 0:
        print("--limit must be 0 or a positive integer.", file=sys.stderr)
        return 2

    api_key = os.environ.get(API_KEY_ENV)
    if not api_key and not args.dry_run:
        print(f"Missing {API_KEY_ENV} environment variable.", file=sys.stderr)
        return 1

    existing_place_ids = load_existing_place_ids()
    photo_ids = load_photo_ids()
    candidates = read_candidates()

    promoted = 0
    skipped_existing = 0
    skipped_uncategorized = 0
    skipped_duplicate_candidate = 0
    photos_downloaded = 0
    photos_existing = 0
    failed_photos = 0
    seen_candidate_ids: set[str] = set()

    for row in candidates:
        place_id = (row.get("id") or "").strip()
        if not place_id:
            continue
        if place_id in seen_candidate_ids:
            skipped_duplicate_candidate += 1
            continue
        seen_candidate_ids.add(place_id)

        category = infer_category(row)
        if category is None:
            skipped_uncategorized += 1
            log(f"skip uncategorized: {row.get('name')} [{place_id}]")
            continue

        already_in_system = place_id in existing_place_ids
        if already_in_system and not args.include_existing:
            skipped_existing += 1
            continue

        if not already_in_system:
            if args.limit > 0 and promoted >= args.limit:
                break
            if not args.dry_run:
                append_place(row, category)
                existing_place_ids.add(place_id)
            promoted += 1
            log(f"promote: {row.get('name')} -> {CATEGORY_TO_CSV[category]}")
        else:
            log(f"photo-check existing: {row.get('name')} -> {CATEGORY_TO_CSV[category]}")

        photo_name = (row.get("primary_photo_name") or "").strip()
        if photo_name:
            table_name = table_name_for_category(category)
            photo_ids[(table_name, place_id)] = photo_name
            output_path = PHOTO_DIR / table_name / f"{place_id}.jpg"
            if output_path.exists():
                photos_existing += 1
            elif not args.dry_run:
                try:
                    if download_photo(photo_name, output_path, api_key):
                        photos_downloaded += 1
                        time.sleep(REQUEST_DELAY_SECONDS)
                except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, OSError) as error:
                    failed_photos += 1
                    log(f"failed photo {place_id} {row.get('name')}: {error}")

    if not args.dry_run:
        write_photo_ids(photo_ids)

    log(
        "Done. "
        f"promoted={promoted}, skipped_existing={skipped_existing}, "
        f"skipped_uncategorized={skipped_uncategorized}, "
        f"skipped_duplicate_candidate={skipped_duplicate_candidate}, "
        f"photos_downloaded={photos_downloaded}, photos_existing={photos_existing}, "
        f"failed_photos={failed_photos}"
    )
    return 0 if failed_photos == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
