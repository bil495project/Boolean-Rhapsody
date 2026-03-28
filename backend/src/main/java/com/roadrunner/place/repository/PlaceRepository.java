package com.roadrunner.place.repository;

import com.roadrunner.place.entity.Place;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Place}.
 * All query methods support pagination via {@link Pageable} overloads.
 */
@Repository
public interface PlaceRepository extends JpaRepository<Place, String> {

    /**
     * Returns all places whose 'types' column contains the given type token.
     * The LIKE match is intentionally broad; exact-token matching can be
     * added via a full-text index or a normalised junction table.
     */
    Page<Place> findByTypesContaining(String type, Pageable pageable);

    /**
     * Returns all places with a rating score equal to or above {@code minScore}.
     * Null-rating rows are excluded.
     */
    Page<Place> findByRatingScoreGreaterThanEqual(double minScore, Pageable pageable);

    /**
     * Case-insensitive name lookup (exact name).
     * Returns Optional because the name is not guaranteed to be unique.
     */
    Optional<Place> findByNameIgnoreCase(String name);

    /**
     * Partial / fuzzy name search — returns all places whose name contains
     * the given substring (case-insensitive).
     */
    Page<Place> findByNameContainingIgnoreCase(String namePart, Pageable pageable);

    /**
     * Filters places by their price-level token.
     */
    Page<Place> findByPriceLevel(String priceLevel, Pageable pageable);

    /**
     * Bulk retrieval by a supplied list of Place IDs.
     * Used by the /bulk endpoint and the travel-plan resolution path.
     */
    List<Place> findAllByIdIn(List<String> ids);

    /**
     * Performs a PostgreSQL ILIKE substring match on the {@code types} column
     * for a single keyword (e.g. "cafe", "bar", "museum").
     * Called repeatedly by the service layer — once per keyword in a category —
     * so that multi-keyword categories work via deduplication in code.
     *
     * @param keyword a Google Place type token to search for (case-insensitive substring)
     * @return all matching places ordered by rating score (highest first, nulls last)
     */
    @Query(
        nativeQuery = true,
        value = "SELECT * FROM places " +
                "WHERE LOWER(types) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                "ORDER BY rating_score DESC NULLS LAST"
    )
    List<Place> findByTypeKeyword(@Param("keyword") String keyword);
}
