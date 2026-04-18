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
PHOTO_DIR = REPO_ROOT / "frontend" / "public" / "place-photos"
API_KEY_ENV = "GOOGLE_PLACES_API_KEY"


def known_tables() -> list[str]:
    return [f"{csv_path.stem}_places" for csv_path in sorted(SOURCE_CSV_DIR.glob("*.csv"))]


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
        print("Usage: python data-collector/download_google_place_photos.py [table_name] [photo_count]", file=sys.stderr)
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


def read_photo_rows(table: str) -> list[tuple[str, str]]:
    if not PHOTO_IDS_CSV.exists():
        return []

    with PHOTO_IDS_CSV.open("r", encoding="utf-8-sig", newline="") as csv_file:
        reader = csv.DictReader(csv_file)
        rows = [
            (row["place_id"], row["primary_photo_name"])
            for row in reader
            if row.get("table_name") == table
            and row.get("place_id")
            and row.get("primary_photo_name")
        ]

    return sorted(rows)


def download_photo(photo_name: str, output_path: Path, api_key: str) -> None:
    encoded_name = urllib.parse.quote(photo_name, safe="/")
    request = urllib.request.Request(
        f"https://places.googleapis.com/v1/{encoded_name}/media"
        f"?maxWidthPx=800&key={urllib.parse.quote(api_key)}"
    )

    with urllib.request.urlopen(request, timeout=60) as response:
        data = response.read()

    temp_path = output_path.with_suffix(output_path.suffix + ".tmp")
    temp_path.write_bytes(data)
    temp_path.replace(output_path)


def process_table(table: str, api_key: str, limit: int) -> tuple[int, int, int]:
    table_photo_dir = PHOTO_DIR / table
    table_photo_dir.mkdir(parents=True, exist_ok=True)

    rows = [
        (place_id, photo_name)
        for place_id, photo_name in read_photo_rows(table)
        if not (table_photo_dir / f"{place_id}.jpg").exists()
    ]
    if limit > 0:
        rows = rows[:limit]

    downloaded = 0
    failed = 0
    processed = 0
    print(f"[{table}] {len(rows)} photos missing on disk.")

    for index, (place_id, photo_name) in enumerate(rows, start=1):
        processed += 1
        try:
            download_photo(photo_name, table_photo_dir / f"{place_id}.jpg", api_key)
            downloaded += 1
            if downloaded % 25 == 0:
                print(f"[{table}] downloaded {downloaded}/{len(rows)} photos")
            time.sleep(0.05)
        except (
            urllib.error.HTTPError,
            urllib.error.URLError,
            TimeoutError,
            OSError,
            json.JSONDecodeError,
        ) as error:
            failed += 1
            print(f"[{table}] failed {index}/{len(rows)} {place_id}: {error}", file=sys.stderr)

    print(f"[{table}] done. downloaded={downloaded}, failed={failed}")
    return downloaded, failed, processed


def main() -> int:
    parsed = selected_tables_and_count()
    if parsed is None:
        return 2
    tables, photo_count = parsed

    api_key = os.environ.get(API_KEY_ENV)
    if not api_key:
        print(f"Missing {API_KEY_ENV} environment variable.", file=sys.stderr)
        return 1

    total_failed = 0
    total_processed = 0

    for table in tables:
        remaining = 0 if photo_count == 0 else max(photo_count - total_processed, 0)
        if photo_count > 0 and remaining == 0:
            break

        _downloaded, failed, processed = process_table(table, api_key, remaining)
        total_failed += failed
        total_processed += processed

    return 0 if total_failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
