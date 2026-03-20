package com.roadrunner.place.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Outbound DTO that exposes Place data to API consumers.
 * Decoupled from the JPA entity so the API contract can evolve independently.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceResponse {

    /** Stable Google Places identifier. */
    private String id;

    /** Human-readable place name. */
    private String name;

    /** Full formatted address. */
    private String formattedAddress;

    /** WGS-84 latitude. */
    private Double latitude;

    /** WGS-84 longitude. */
    private Double longitude;

    /** Comma-separated type tags. */
    private String types;

    /** Average star-rating (nullable). */
    private Double ratingScore;

    /** Total number of user ratings (nullable). */
    private Integer ratingCount;

    /** Price level token (nullable). */
    private String priceLevel;

    /** Business operational status token (nullable). */
    private String businessStatus;
}
