package com.roadrunner.place.controller;

import com.roadrunner.place.dto.request.PlaceBulkRequest;
import com.roadrunner.place.dto.response.PlaceResponse;
import com.roadrunner.place.service.PlaceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing all POI / Place endpoints under {@code /api/places}.
 *
 * <pre>
 * GET  /api/places                  — paginated list (optional filters: type, minRating, priceLevel)
 * GET  /api/places/{id}             — single place by id
 * GET  /api/places/search?name=     — partial/fuzzy name search
 * POST /api/places/bulk             — bulk retrieval by ids
 * </pre>
 */
@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
@Validated
public class PlaceController {

    private final PlaceService placeService;

    /**
     * Returns a paginated list of places.
     * When a filter parameter is present, only that filter is applied (first wins).
     * Combined filtering can be added in a follow-up iteration.
     *
     * @param type       optional type filter
     * @param minRating  optional minimum rating (must be 0.0 – 5.0)
     * @param priceLevel optional price-level token filter
     * @param pageable   Spring-resolved pagination/sort (default size 20)
     */
    @GetMapping
    public ResponseEntity<Page<PlaceResponse>> getPlaces(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DecimalMin(value = "0.0", message = "minRating must be >= 0.0") @DecimalMax(value = "5.0", message = "minRating must be <= 5.0") Double minRating,
            @RequestParam(required = false) String priceLevel,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<PlaceResponse> result;

        if (type != null) {
            result = placeService.getPlacesByType(type, pageable);
        } else if (minRating != null) {
            result = placeService.getPlacesByRatingScore(minRating, pageable);
        } else if (priceLevel != null) {
            result = placeService.getPlacesByPriceLevel(priceLevel, pageable);
        } else {
            result = placeService.getAllPlaces(pageable);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Retrieves a single place by its Google Place ID.
     * Returns 404 if the id is not recognised.
     *
     * @param id Google Place ID path variable
     */
    @GetMapping("/{id}")
    public ResponseEntity<PlaceResponse> getPlaceById(@PathVariable String id) {
        return ResponseEntity.ok(placeService.getPlaceById(id));
    }

    /**
     * Partial / fuzzy name search.
     *
     * @param name     substring to search in place names
     * @param pageable pagination parameters
     */
    @GetMapping("/search")
    public ResponseEntity<Page<PlaceResponse>> searchByName(
            @RequestParam String name,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(placeService.searchPlacesByName(name, pageable));
    }

    /**
     * Bulk retrieval of places by a list of stable Google Place IDs.
     * Unrecognised IDs are silently omitted.
     *
     * @param request body containing non-empty list of ids
     */
    @PostMapping("/bulk")
    public ResponseEntity<List<PlaceResponse>> getBulkPlaces(
            @Valid @RequestBody PlaceBulkRequest request) {
        return ResponseEntity.ok(placeService.getPlacesByIds(request.getIds()));
    }

    /**
     * Returns places that belong to one of the 7 application-level categories.
     *
     * <p>Valid category values (case-insensitive):
     * <ul>
     *   <li>BARS_AND_NIGHTCLUBS</li>
     *   <li>CAFES_AND_DESSERTS</li>
     *   <li>HISTORIC_PLACES</li>
     *   <li>HOTELS</li>
     *   <li>LANDMARKS</li>
     *   <li>PARKS</li>
     *   <li>RESTAURANTS</li>
     * </ul>
     *
     * <p>Results are pre-sorted by rating (highest first) and capped at {@code size}.
     *
     * @param category one of the 7 category names listed above
     * @param size     maximum number of results; capped at 50 (default: 20)
     */
    @GetMapping("/by-category")
    public ResponseEntity<List<PlaceResponse>> getPlacesByCategory(
            @RequestParam String category,
            @RequestParam(defaultValue = "20") int size) {

        int limit = Math.min(size, 50); // hard cap — never return more than 50
        return ResponseEntity.ok(placeService.getPlacesByCategory(category, limit));
    }
}
