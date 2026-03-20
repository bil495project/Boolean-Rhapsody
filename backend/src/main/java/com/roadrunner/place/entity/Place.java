package com.roadrunner.place.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity that maps to the "places" table.
 * Reflects the Google Places CSV schema and LLD specification.
 */
@Entity
@Table(name = "places")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Place {

    /** Google Places stable identifier (e.g. ChIJ…). Acts as primary key. */
    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    /** Human-readable place name. */
    @Column(name = "name", nullable = false)
    private String name;

    /** Full formatted address string. */
    @Column(name = "formatted_address", columnDefinition = "TEXT")
    private String formattedAddress;

    /** WGS-84 latitude. */
    @Column(name = "lat", nullable = false)
    private Double latitude;

    /** WGS-84 longitude. */
    @Column(name = "lng", nullable = false)
    private Double longitude;

    /**
     * Comma-separated Google place-type tags stored as plain text.
     * Example: "restaurant,food,point_of_interest"
     */
    @Column(name = "types", columnDefinition = "TEXT")
    private String types;

    /** Average star-rating (1-5 scale, nullable when no reviews). */
    @Column(name = "rating_score")
    private Double ratingScore;

    /** Total number of user ratings. */
    @Column(name = "rating_count")
    private Integer ratingCount;

    /**
     * Price level token; mirrors Google's 0-4 scale stored as a string
     * (e.g. "PRICE_LEVEL_FREE", "PRICE_LEVEL_INEXPENSIVE", …).
     */
    @Column(name = "price_level", length = 64)
    private String priceLevel;

    /**
     * Operational status token
     * (e.g. "OPERATIONAL", "CLOSED_TEMPORARILY", "CLOSED_PERMANENTLY").
     */
    @Column(name = "business_status", length = 64)
    private String businessStatus;
}
