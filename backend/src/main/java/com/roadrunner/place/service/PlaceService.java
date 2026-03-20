package com.roadrunner.place.service;

import com.roadrunner.place.dto.response.PlaceResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Business-logic contract for the POI (Place) domain.
 * All read operations are paginated where a result-set is returned.
 */
public interface PlaceService {

    /**
     * Returns every Place in the database, page-aware.
     *
     * @param pageable pagination / sorting parameters
     * @return page of PlaceResponse DTOs
     */
    Page<PlaceResponse> getAllPlaces(Pageable pageable);

    /**
     * Filters places by a single type token (e.g. "restaurant").
     *
     * @param type     Google place-type string
     * @param pageable pagination parameters
     * @return matching places; empty page when no matches exist
     */
    Page<PlaceResponse> getPlacesByType(String type, Pageable pageable);

    /**
     * Filters places by a minimum rating score threshold.
     *
     * @param minScore lower bound (inclusive) for ratingScore; must be in [0, 5]
     * @param pageable pagination parameters
     * @return places with ratingScore >= minScore; empty page when none match
     */
    Page<PlaceResponse> getPlacesByRatingScore(double minScore, Pageable pageable);

    /**
     * Looks up a single place by exact name (case-insensitive).
     *
     * @param name exact place name
     * @return matching PlaceResponse
     * @throws com.roadrunner.place.exception.PlaceNotFoundException if not found
     */
    PlaceResponse getPlaceByName(String name);

    /**
     * Partial/fuzzy name search returning one or more matching places.
     *
     * @param namePart substring to search for
     * @param pageable pagination parameters
     * @return matching places
     */
    Page<PlaceResponse> searchPlacesByName(String namePart, Pageable pageable);

    /**
     * Filters places by price-level token.
     *
     * @param priceLevel price-level string (e.g. "PRICE_LEVEL_MODERATE")
     * @param pageable   pagination parameters
     * @return matching places; empty page when none match
     */
    Page<PlaceResponse> getPlacesByPriceLevel(String priceLevel, Pageable pageable);

    /**
     * Retrieves a single place by its stable Google Place ID.
     *
     * @param id Google Place ID string
     * @return matching PlaceResponse
     * @throws com.roadrunner.place.exception.PlaceNotFoundException if not found
     */
    PlaceResponse getPlaceById(String id);

    /**
     * Bulk retrieval by a list of Place IDs.
     * Unrecognised IDs are silently omitted (no exception thrown).
     *
     * @param ids non-empty list of Google Place IDs
     * @return list of matching PlaceResponse DTOs (order not guaranteed)
     */
    List<PlaceResponse> getPlacesByIds(List<String> ids);
}
