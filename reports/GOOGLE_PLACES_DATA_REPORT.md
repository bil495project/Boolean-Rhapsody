# Google Places Data Usage Report

Date: 2026-04-20

## Executive Summary

Boolean Rhapsody uses Google Places primarily as an offline data ingestion source for Ankara POIs. The production app does not appear to call Google Places live when users browse places or generate routes. Instead, Python data-collector scripts fetch and enrich place records from Google Places API, persist them as CSV files under `backend/src/main/resources/ankara_places/`, download static place photos into `frontend/public/place-photos/`, and the Spring backend seeds those CSV rows into the database at startup.

At runtime:

- Backend place browsing uses the local `places` table through `/api/places`.
- Route generation uses local `PlaceRepository` data, not live Google calls.
- Frontend images prefer local downloaded Google place photos by Google Place ID.
- Google identifiers, types, ratings, review counts, price levels, business status, coordinates, addresses, and photo resource names are the key Google-derived fields.

## High-Level Flow

```text
Google Places API
  -> Python collector scripts
  -> backend/src/main/resources/ankara_places/*.csv
  -> Spring startup CSV seeders
  -> Postgres places / *_places tables
  -> /api/places and /api/routes
  -> React frontend map, cards, route UI

Google Places Photo media
  -> data-collector/place_photo_ids.csv
  -> frontend/public/place-photos/<category>_places/<placeId>.jpg
  -> frontend image resolver
```

## Google APIs Used

### 1. Nearby Search, Places API New

Main current ingestion script:

- `data-collector/add_popular_ankara_places.py`

It calls:

- `POST https://places.googleapis.com/v1/places:searchNearby`

Relevant implementation:

- Uses environment variable `GOOGLE_PLACES_API_KEY`.
- Uses `rankPreference: POPULARITY`.
- Uses a circular `locationRestriction`.
- Default center is Ankara: latitude `39.9334`, longitude `32.8597`.
- Search radius is `30000.0` meters.
- `query_count` controls how many nearby searches run. With more than one query, centers are distributed with a spiral/golden-angle pattern around Ankara.
- Default minimum review count is `100`.

Requested Google fields:

- `places.id`
- `places.displayName`
- `places.formattedAddress`
- `places.location`
- `places.types`
- `places.rating`
- `places.userRatingCount`
- `places.priceLevel`
- `places.businessStatus`
- `places.photos.name`

The script maps each Google response into a `PlaceRow`, infers an app category from Google place `types`, appends a row to the right CSV, stores the first photo resource name, and optionally downloads the corresponding photo.

### 2. Place Details Photo Lookup

Script:

- `data-collector/fetch_google_place_photo_ids.py`

It calls:

- `GET https://places.googleapis.com/v1/places/{place_id}`

Headers:

- `X-Goog-Api-Key`
- `X-Goog-FieldMask: photos`

Purpose:

- For each existing CSV place ID, fetch the primary Google photo resource name.
- Store that mapping in `data-collector/place_photo_ids.csv`.

Output schema:

```csv
table_name,place_id,primary_photo_name
```

### 3. Place Photo Media Download

Scripts:

- `data-collector/download_google_place_photos.py`
- `data-collector/add_popular_ankara_places.py`
- `data-collector/promote_popular_ankara_candidates.py`

They call:

- `GET https://places.googleapis.com/v1/{photo_name}/media?maxWidthPx=800&key=...`

Purpose:

- Download JPEG photos into `frontend/public/place-photos/<table_name>/<place_id>.jpg`.
- The app then serves these as static frontend assets.

## Collector Scripts

### `add_popular_ankara_places.py`

This is the most complete current ingestion path.

Inputs:

- `GOOGLE_PLACES_API_KEY`
- Optional `query_count`
- Optional `--limit`
- Optional `--min-rating-count`
- Optional `--dry-run`

Behavior:

- Loads all existing place IDs from `backend/src/main/resources/ankara_places/*.csv`.
- Performs one or more Nearby Search requests.
- Filters out rows with missing IDs or too few Google ratings.
- Categorizes each place by Google type.
- Appends new places to one of the seven category CSV files.
- Stores photo IDs in `data-collector/place_photo_ids.csv`.
- Downloads first Google photo to frontend static assets.
- Avoids duplicates based on Google Place ID.

Application categories:

- `bars_nightclubs`
- `cafes_desserts`
- `historic_places`
- `hotels`
- `landmarks`
- `parks`
- `restaurants`

Important type mapping examples:

- `hotel`, `lodging` -> hotels
- `bar`, `night_club`, `pub` -> bars/nightclubs
- `restaurant`, `food`, `meal_takeaway` -> restaurants
- `cafe`, `coffee_shop`, `bakery`, `dessert_shop` -> cafes/desserts
- `park`, `garden` -> parks
- `museum`, `historical_place`, `mosque`, `place_of_worship` -> historic places
- `tourist_attraction`, `monument`, `landmark`, `sculpture` -> landmarks

### `fetch_google_place_photo_ids.py`

This is a photo metadata enrichment script for existing places.

Behavior:

- Reads known category CSVs.
- Finds place IDs missing from `place_photo_ids.csv`.
- Calls Place Details with a `photos` field mask.
- Saves the first photo resource name.
- Can process all tables, one table, or a limited count.

### `download_google_place_photos.py`

This is a photo binary download script.

Behavior:

- Reads `data-collector/place_photo_ids.csv`.
- Finds local JPGs missing from `frontend/public/place-photos`.
- Downloads each photo media resource at max width 800 px.
- Writes each file as `<place_id>.jpg`.

### `promote_popular_ankara_candidates.py`

This promotes reviewed candidates from:

- `data-collector/popular_ankara_place_candidates.csv`

Behavior:

- Reads candidate rows already gathered from Google.
- Infers or uses `suggested_category`.
- Appends approved/new rows into the app's category CSVs.
- Stores photo IDs.
- Downloads photos if missing.
- Can run in `--dry-run`.
- Can include already-known places for photo repair with `--include-existing`.

### Legacy Collectors

Two older scripts also call Google Places Nearby Search:

- `data-collector/fill_database_bolgesel.py`
- `data-collector/fill_database.py`

`fill_database_bolgesel.py` uses `GMAPS_KEY` from the environment, supports polygon or bounding-box grids, batches included Google types, writes regional CSVs, and maintains a master CSV under `data/ankara/master.csv`.

`fill_database.py` appears to be an older center-district fetcher. It contains a hard-coded Google API key literal. That key should be treated as compromised and rotated.

## Stored Data

### Category CSVs

Primary app data lives in:

- `backend/src/main/resources/ankara_places/bars_nightclubs.csv`
- `backend/src/main/resources/ankara_places/cafes_desserts.csv`
- `backend/src/main/resources/ankara_places/historic_places.csv`
- `backend/src/main/resources/ankara_places/hotels.csv`
- `backend/src/main/resources/ankara_places/landmarks.csv`
- `backend/src/main/resources/ankara_places/parks.csv`
- `backend/src/main/resources/ankara_places/restaurants.csv`

CSV schema:

```csv
id,name,formatted_address,lat,lng,types,rating,user_rating_count,price_level
```

Field meanings:

- `id`: Google Place ID, also used as DB primary key.
- `name`: Google display name text.
- `formatted_address`: Google formatted address.
- `lat`, `lng`: WGS-84 coordinates from Google location.
- `types`: Google place type list stored as JSON-like text in CSV, later flattened in DB.
- `rating`: Google rating score.
- `user_rating_count`: Google user rating count.
- `price_level`: Google price level token when available.

Current dataset counts:

| CSV | Rows | Unique IDs | Rows with rating | Rows with price level |
| --- | ---: | ---: | ---: | ---: |
| `bars_nightclubs.csv` | 132 | 132 | 132 | 132 |
| `cafes_desserts.csv` | 864 | 864 | 864 | 864 |
| `historic_places.csv` | 122 | 122 | 122 | 0 |
| `hotels.csv` | 38 | 38 | 38 | 0 |
| `landmarks.csv` | 173 | 173 | 173 | 0 |
| `parks.csv` | 395 | 395 | 395 | 0 |
| `restaurants.csv` | 2013 | 2013 | 2013 | 1985 |
| Total | 3737 | 3215 | 3737 | 2982 |

There are duplicate Google Place IDs across category CSVs:

- Duplicate place IDs: 481
- Extra duplicate rows beyond unique IDs: 522

The single `places` table loader deduplicates across files in memory. The separated table loader intentionally keeps each category table separate, so duplicates can exist across those category-specific tables.

### Photo Metadata

File:

- `data-collector/place_photo_ids.csv`

Current rows by table:

| Table | Photo ID rows |
| --- | ---: |
| `bars_nightclubs_places` | 131 |
| `cafes_desserts_places` | 860 |
| `historic_places_places` | 122 |
| `hotels_places` | 38 |
| `landmarks_places` | 173 |
| `parks_places` | 391 |
| `restaurants_places` | 2005 |
| Total | 3720 |

### Downloaded Photos

Stored under:

- `frontend/public/place-photos/`

Current JPG counts:

| Folder | JPGs |
| --- | ---: |
| `bars_nightclubs_places` | 128 |
| `cafes_desserts_places` | 854 |
| `historic_places_places` | 122 |
| `hotels_places` | 38 |
| `landmarks_places` | 171 |
| `parks_places` | 387 |
| `restaurants_places` | 1991 |
| Total | 3691 |

## Backend Runtime Usage

### Database Seeding

Two Spring startup loaders read the same CSV source:

- `backend/src/main/java/com/roadrunner/place/loader/PlaceDataLoader.java`
- `backend/src/main/java/com/roadrunner/place/loader/SeparatedPlaceDataLoader.java`

Both are active outside the `test` profile.

`PlaceDataLoader`:

- Deletes all records from `places`.
- Reads all `classpath:ankara_places/*.csv`.
- Flattens the CSV `types` field.
- Prepends a category hint derived from the filename.
- Deduplicates by Google Place ID across files.
- Saves rows through `PlaceRepository`.

`SeparatedPlaceDataLoader`:

- Reads the same CSV files.
- Creates category-specific tables dynamically, such as `restaurants_places`.
- Clears each category table.
- Inserts rows through `JdbcTemplate`.

The main app code uses the `places` table most directly. The separated tables appear to be supplemental or legacy support; I did not find route/place service logic querying those category tables directly.

### Place Entity

File:

- `backend/src/main/java/com/roadrunner/place/entity/Place.java`

The DB entity preserves the Google-derived fields:

- `id`
- `name`
- `formattedAddress`
- `latitude`
- `longitude`
- `types`
- `ratingScore`
- `ratingCount`
- `priceLevel`
- `businessStatus`

Note: `businessStatus` exists on the entity and separated table schema, but the current category CSV headers do not include `business_status`. Because the loaders only insert or build up to `price_level`, production rows seeded from the checked-in CSVs will generally not populate `businessStatus`.

### Place API

File:

- `backend/src/main/java/com/roadrunner/place/controller/PlaceController.java`

Exposed endpoints:

- `GET /api/places`
- `GET /api/places/{id}`
- `GET /api/places/search?name=...`
- `POST /api/places/bulk`
- `GET /api/places/by-category?category=...&size=...`

Implementation:

- Uses `PlaceServiceImpl`.
- Uses `PlaceRepository` for local DB reads.
- Supports type substring filtering, minimum rating filtering, price-level filtering, name search, bulk ID lookup, and app category lookup.

No Google Places HTTP calls occur in the backend place API.

## Route Generation Usage

Route generation consumes local Google-derived place records.

Key files:

- `backend/src/main/java/com/roadrunner/route/service/RouteGenerationService.java`
- `backend/src/main/java/com/roadrunner/route/service/RouteScoringService.java`
- `backend/src/main/java/com/roadrunner/route/service/DefaultPlaceRouteLabelService.java`

Important behavior:

- `RouteGenerationService` loads candidates from `placeRepository.findAll()`.
- Candidate pool uses a minimum rating count of `100`.
- `RouteScoringService` filters out non-operational places only when `businessStatus` is populated and not `OPERATIONAL`.
- Google rating and rating count are central to scoring.
- Coordinates are used for Haversine distance, centrality, ordering, and travel-time estimates.
- `DefaultPlaceRouteLabelService` maps Google place types to route labels such as hotel, nightlife, historic areas, restaurants, cafes/desserts, parks/viewpoints, landmarks, and natural areas.
- Explicit route constraints can resolve fixed start/end/interior places by Google Place ID through `placeRepository.findById(...)`.

In other words, Google Places data drives route quality and categorization, but route generation does not call Google live.

## Frontend Runtime Usage

Key files:

- `frontend/src/services/placeService.ts`
- `frontend/src/utils/placeCategory.ts`
- `frontend/src/utils/placeImage.ts`

Behavior:

- `placeService.ts` calls backend `/api/places` endpoints.
- It maps `PlaceResponse` into frontend `MapDestination`.
- `placeCategory.ts` maps the comma-separated Google `types` string into a UI category.
- `placeImage.ts` prefers local Google-downloaded photos for IDs that start with `ChI`.

Photo URL pattern:

```text
/place-photos/<category_table>/<encoded_place_id>.jpg
```

If no local Google photo path is available, the frontend falls back to configured remote images or category fallback images.

## Configuration and Secrets

Current Google-related key usage:

- Current collector scripts use `GOOGLE_PLACES_API_KEY`.
- Legacy regional collector uses `GMAPS_KEY`.
- `data-collector/fill_database.py` contains a hard-coded Google API key literal.
- `frontend/src/services/geminiService.ts` also contains a hard-coded Google API key literal, though that is for Gemini rather than Places.

Recommendations:

- Rotate any hard-coded Google keys that have been committed.
- Remove hard-coded keys from source.
- Use environment variables or secret manager configuration.
- Consider adding secret scanning in CI.
- Consider documenting required collector env vars in `DEVELOPER_GUIDE.md` or a data-collector README.

## Operational Notes

### Data Freshness

The app's place catalog is only as fresh as the latest collector run. Ratings, review counts, price levels, business status, names, addresses, and photos can become stale after they are written into CSV/static assets.

### Google Terms and Attribution

The code stores Google-derived place data and photos locally. This should be checked against the applicable Google Maps Platform terms for caching, display, attribution, photo usage, and refresh requirements. I did not verify legal compliance in this code review.

### Business Status Gap

Collectors request `businessStatus`, but the checked-in app CSV schema omits `business_status`. The backend route scorer has logic to filter non-operational places, but that logic will not be effective for rows seeded from the current CSVs unless the schema and loaders preserve business status.

### Duplicate Loaders

Both startup loaders run under `!test`. If both are intended, that is fine, but they duplicate startup work. If the separated tables are unused, removing or disabling `SeparatedPlaceDataLoader` would simplify startup and avoid extra DB writes.

### CSV Parsing

The Java loaders use custom CSV parsing. The collector scripts write quoted CSV with JSON-like `types`. The parser handles common quoted comma cases, but a mature CSV parser library would reduce risk if addresses or names become more complex.

## Recommendations

1. Remove and rotate committed Google keys.
2. Add `business_status` to the category CSV schema if operational filtering matters.
3. Decide whether both `PlaceDataLoader` and `SeparatedPlaceDataLoader` are still needed.
4. Add a `data-collector/README.md` documenting exact commands, required env vars, expected outputs, and quotas.
5. Use one canonical type-to-category mapping shared or generated across collector, backend, and frontend to reduce drift.
6. Consider recording collection metadata: run date, query parameters, API fields, source script, and Google data refresh date.
7. Consider using a CSV library in the Java loaders.
8. Review Google Maps Platform storage, photo, and attribution requirements before production use.

## File Reference Index

Collector scripts:

- `data-collector/add_popular_ankara_places.py`
- `data-collector/fetch_google_place_photo_ids.py`
- `data-collector/download_google_place_photos.py`
- `data-collector/promote_popular_ankara_candidates.py`
- `data-collector/fill_database_bolgesel.py`
- `data-collector/fill_database.py`

Stored data:

- `backend/src/main/resources/ankara_places/*.csv`
- `data-collector/place_photo_ids.csv`
- `data-collector/popular_ankara_place_candidates.csv`
- `data-collector/places_ankara.csv`
- `frontend/public/place-photos/`

Backend:

- `backend/src/main/java/com/roadrunner/place/entity/Place.java`
- `backend/src/main/java/com/roadrunner/place/loader/PlaceDataLoader.java`
- `backend/src/main/java/com/roadrunner/place/loader/SeparatedPlaceDataLoader.java`
- `backend/src/main/java/com/roadrunner/place/controller/PlaceController.java`
- `backend/src/main/java/com/roadrunner/place/service/PlaceServiceImpl.java`
- `backend/src/main/java/com/roadrunner/route/service/RouteGenerationService.java`
- `backend/src/main/java/com/roadrunner/route/service/RouteScoringService.java`
- `backend/src/main/java/com/roadrunner/route/service/DefaultPlaceRouteLabelService.java`

Frontend:

- `frontend/src/services/placeService.ts`
- `frontend/src/utils/placeCategory.ts`
- `frontend/src/utils/placeImage.ts`
