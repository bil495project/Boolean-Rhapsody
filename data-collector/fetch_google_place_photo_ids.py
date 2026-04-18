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
SOURCE_CSV_DIR = REPO_ROOT / "backend" / "src" / "main" / "resources" / "ankara_places"
PHOTO_IDS_CSV = REPO_ROOT / "data-collector" / "place_photo_ids.csv"
API_KEY_ENV = "GOOGLE_PLACES_API_KEY"


def known_tables() -> list[str]:
    return [f"{csv_path.stem}_places" for csv_path in sorted(SOURCE_CSV_DIR.glob("*.csv"))]


def csv_path_for_table(table: str) -> Path:
    return SOURCE_CSV_DIR / f"{table.removesuffix('_places')}.csv"


def parse_photo_count(value: str) -> int | None:
    try:
        count = int(value)
    except ValueError:
        return None
    return count if count >= 0 else None


def selected_tables_and_count() -> tuple[list[str], int] | None:
    tables = known_tables()

    if len(sys.argv) == 1:
        return tables, 0

    if len(sys.argv) == 2:
        value = sys.argv[1].strip()
        if value in tables:
            return [value], 0
        count = parse_photo_count(value)
        if count is not None:
            return tables, count
        print(f"Unknown table '{value}'. Allowed: {', '.join(tables)}", file=sys.stderr)
        return None

    if len(sys.argv) != 3:
        print("Usage: python data-collector/fetch_google_place_photo_ids.py [table_name] [photo_count]", file=sys.stderr)
        return None

    table = sys.argv[1].strip()
    if table not in tables:
        print(f"Unknown table '{table}'. Allowed: {', '.join(tables)}", file=sys.stderr)
        return None

    count = parse_photo_count(sys.argv[2].strip())
    if count is None:
        print("photo_count must be a non-negative integer.", file=sys.stderr)
        return None

    return [table], count


def read_place_ids(table: str) -> list[str]:
    seen: set[str] = set()
    place_ids: list[str] = []

    with csv_path_for_table(table).open("r", encoding="utf-8-sig", newline="") as csv_file:
        reader = csv.DictReader(csv_file)
        for row in reader:
            place_id = (row.get("id") or "").strip()
            if place_id and place_id not in seen:
                seen.add(place_id)
                place_ids.append(place_id)

    return place_ids


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


def fetch_primary_photo_name(place_id: str, api_key: str) -> str | None:
    request = urllib.request.Request(
        f"https://places.googleapis.com/v1/places/{urllib.parse.quote(place_id, safe='')}",
        headers={
            "Content-Type": "application/json",
            "X-Goog-Api-Key": api_key,
            "X-Goog-FieldMask": "photos",
        },
    )

    with urllib.request.urlopen(request, timeout=30) as response:
        data = json.loads(response.read().decode("utf-8"))

    photos = data.get("photos") or []
    if not photos:
        return None

    photo_name = photos[0].get("name")
    return photo_name if isinstance(photo_name, str) and photo_name else None


def process_table(table: str, api_key: str, photo_ids: dict[tuple[str, str], str], limit: int) -> tuple[int, int, int, int]:
    pending_place_ids = [
        place_id
        for place_id in read_place_ids(table)
        if (table, place_id) not in photo_ids
    ]
    if limit > 0:
        pending_place_ids = pending_place_ids[:limit]

    saved = 0
    no_photo = 0
    failed = 0
    processed = 0
    print(f"[{table}] {len(pending_place_ids)} places missing photo id in CSV.")

    for index, place_id in enumerate(pending_place_ids, start=1):
        processed += 1
        try:
            photo_name = fetch_primary_photo_name(place_id, api_key)
            time.sleep(0.05)
            if not photo_name:
                no_photo += 1
                continue

            photo_ids[(table, place_id)] = photo_name
            saved += 1
            if saved % 25 == 0:
                write_photo_ids(photo_ids)
                print(f"[{table}] saved {saved}/{len(pending_place_ids)} photo ids")
        except (
            urllib.error.HTTPError,
            urllib.error.URLError,
            TimeoutError,
            OSError,
            json.JSONDecodeError,
        ) as error:
            failed += 1
            print(f"[{table}] failed {index}/{len(pending_place_ids)} {place_id}: {error}", file=sys.stderr)

    write_photo_ids(photo_ids)
    print(f"[{table}] done. saved={saved}, no_photo={no_photo}, failed={failed}")
    return saved, no_photo, failed, processed


def main() -> int:
    parsed = selected_tables_and_count()
    if parsed is None:
        return 2
    tables, photo_count = parsed

    api_key = os.environ.get(API_KEY_ENV)
    if not api_key:
        print(f"Missing {API_KEY_ENV} environment variable.", file=sys.stderr)
        return 1

    photo_ids = load_photo_ids()
    total_failed = 0
    total_processed = 0

    for table in tables:
        remaining = 0 if photo_count == 0 else max(photo_count - total_processed, 0)
        if photo_count > 0 and remaining == 0:
            break

        _saved, _no_photo, failed, processed = process_table(table, api_key, photo_ids, remaining)
        total_failed += failed
        total_processed += processed

    return 0 if total_failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
