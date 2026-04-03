# Route Input Guide

This guide documents how to call `POST /api/routes/generate`.

Base URL in local development:

```text
http://localhost:8080/api/routes/generate
```

## Quick Rules

- You must send at least one of:
  - `preferences`
  - `userVector`
- `k` is optional. Default is `3`.
- `constraints` is optional.
- If `constraints` is omitted, legacy route generation fallback is used.
- If `constraints` is present, boundary and slot behavior follows the rules below.

## Minimal Valid Request

```json
{
  "preferences": {}
}
```

This is valid. Backend fills missing preference values with defaults.

## Request Shape

```json
{
  "userVector": {
    "weight_parkVeSeyirNoktalari": "0.5"
  },
  "preferences": {
    "tempo": 1.0,
    "socialPreference": 0.5,
    "naturePreference": 0.5,
    "historyPreference": 0.75,
    "foodImportance": 0.75,
    "alcoholPreference": 0.0,
    "transportStyle": 0.66,
    "budgetLevel": 1.0,
    "tripLength": 0.5,
    "crowdPreference": 0.25
  },
  "constraints": {
    "stayAtHotel": true,
    "needsBreakfast": true,
    "needsLunch": true,
    "needsDinner": true,
    "startWithPoi": false,
    "endWithPoi": false,
    "startWithHotel": null,
    "endWithHotel": null,
    "startPoint": null,
    "endPoint": null,
    "startAnchor": null,
    "endAnchor": null,
    "poiSlots": null
  },
  "centerLat": 39.9208,
  "centerLng": 32.8541,
  "k": 3
}
```

## Preferences vs User Vector

Use either:

- `preferences` if you want the backend to derive route weights for you
- `userVector` if you want to send route weights directly

You can also send both. In that case:

- backend first maps `preferences` into route weights
- then `userVector` overrides any overlapping keys

## Constraint Semantics

### `stayAtHotel`

`stayAtHotel: true` means:

- if there is no explicit override, route starts at a hotel
- if there is no explicit override, route ends at a hotel

In constrained mode this now has real boundary semantics. It is not advisory only.

### Boundary override precedence

More specific side-level constraints override `stayAtHotel` on that side only.

Examples:

- `stayAtHotel: true` + `startWithPoi: true`:
  - start is POI
  - end is still HOTEL
- `stayAtHotel: true` + `endWithPoi: true`:
  - start is still HOTEL
  - end is POI

### Explicit boundary model

You can use:

- `startPoint`
- `endPoint`

with:

- `type: "NONE"`
- `type: "HOTEL"`
- `type: "PLACE"`
- `type: "TYPE"`

When you use `startPoint` or `endPoint`, do not mix that same side with legacy fields like:

- `startWithPoi`
- `startWithHotel`
- `startAnchor`
- `endWithPoi`
- `endWithHotel`
- `endAnchor`

Mixed explicit and legacy boundary fields on the same side return `400`.

### Anchors do not activate boundaries by themselves

`startAnchor` and `endAnchor` are ignored unless the corresponding boundary is active.

Examples:

- `endAnchor` alone does nothing
- `endAnchor` + `endWithPoi: true` works
- `startAnchor` + `startWithHotel: true` can pin the hotel side

## `poiSlots` Semantics

`poiSlots` define interior visit slots only.

They do not define meal capacity.

Valid slot forms:

- `null`
- `{}`
- `{ "kind": "PLACE", "placeId": "..." }`
- `{ "kind": "TYPE", "poiType": "PARK" }`

### Slot behavior

- `null` or `{}` means generated slot placeholder
- `PLACE` means exact POI must be used
- `TYPE` means backend must pick a POI matching that type
- ordered slot order is preserved in the final route

### Meal behavior

Meal flags:

- `needsBreakfast`
- `needsLunch`
- `needsDinner`

Meals are handled separately from `poiSlots`:

- if an existing interior point can satisfy a meal, backend reuses it
- otherwise backend appends an extra protected meal stop
- lunch and dinner do not reuse the same POI

This means `3` slots plus `3` meals is valid.

## Common Payloads

### 1. Minimal default generation

```json
{
  "preferences": {}
}
```

### 2. Hotel loop with 3 park stops and all meals

```json
{
  "preferences": {
    "tempo": 1.0,
    "socialPreference": 0.5,
    "naturePreference": 0.5,
    "historyPreference": 0.75,
    "foodImportance": 0.75,
    "alcoholPreference": 0.0,
    "transportStyle": 0.66,
    "budgetLevel": 1.0,
    "tripLength": 0.5,
    "crowdPreference": 0.25
  },
  "constraints": {
    "stayAtHotel": true,
    "needsBreakfast": true,
    "needsLunch": true,
    "needsDinner": true,
    "poiSlots": [
      { "kind": "TYPE", "poiType": "PARK" },
      { "kind": "TYPE", "poiType": "PARK" },
      { "kind": "TYPE", "poiType": "PARK" }
    ]
  },
  "k": 3
}
```

Expected behavior:

- route starts at hotel
- route ends at hotel
- 3 protected park slots are present
- breakfast, lunch, dinner appear as reused or extra protected stops

### 3. Start from a specific place, end at hotel

```json
{
  "preferences": {},
  "constraints": {
    "stayAtHotel": true,
    "startWithPoi": true,
    "startAnchor": {
      "kind": "PLACE",
      "placeId": "l1"
    }
  },
  "k": 1
}
```

Expected behavior:

- start is place `l1`
- end is hotel because `stayAtHotel` still applies on the end side

### 4. Explicit boundary model

```json
{
  "preferences": {},
  "constraints": {
    "startPoint": {
      "type": "HOTEL"
    },
    "endPoint": {
      "type": "PLACE",
      "placeId": "m1"
    },
    "poiSlots": [
      { "kind": "TYPE", "poiType": "RESTAURANT" },
      {}
    ]
  },
  "k": 1
}
```

## Invalid Payload Patterns

### Mixed explicit and legacy fields on the same side

Invalid:

```json
{
  "preferences": {},
  "constraints": {
    "startPoint": { "type": "HOTEL" },
    "startWithHotel": true
  }
}
```

### `PLACE` slot without `placeId`

Invalid:

```json
{
  "preferences": {},
  "constraints": {
    "poiSlots": [
      { "kind": "PLACE" }
    ]
  }
}
```

### `TYPE` slot without `poiType`

Invalid:

```json
{
  "preferences": {},
  "constraints": {
    "poiSlots": [
      { "kind": "TYPE" }
    ]
  }
}
```

## Practical Recommendations

- Use `preferences` unless you have a good reason to build `userVector` manually.
- Use `stayAtHotel: true` for hotel-loop behavior.
- Use `startWithHotel` / `endWithHotel` only when you want side-specific explicitness.
- Use `startPoint` / `endPoint` only if you want explicit boundary control.
- Do not send `endAnchor` or `startAnchor` alone and expect them to apply.
- Treat `poiSlots` as visit preferences, not meal capacity.
