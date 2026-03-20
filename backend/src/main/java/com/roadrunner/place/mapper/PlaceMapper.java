package com.roadrunner.place.mapper;

import com.roadrunner.place.dto.response.PlaceResponse;
import com.roadrunner.place.entity.Place;
import org.springframework.stereotype.Component;

/**
 * Manual mapper between {@link Place} (entity) and {@link PlaceResponse} (DTO).
 * Using a manual mapper keeps compile-time safety without a code-generation
 * lib.
 */
@Component
public class PlaceMapper {

    /**
     * Converts a {@link Place} entity into its outbound {@link PlaceResponse}.
     *
     * @param place non-null entity
     * @return fully populated response DTO
     */
    public PlaceResponse toResponse(Place place) {
        return PlaceResponse.builder()
                .id(place.getId())
                .name(place.getName())
                .formattedAddress(place.getFormattedAddress())
                .latitude(place.getLatitude())
                .longitude(place.getLongitude())
                .types(place.getTypes())
                .ratingScore(place.getRatingScore())
                .ratingCount(place.getRatingCount())
                .priceLevel(place.getPriceLevel())
                .businessStatus(place.getBusinessStatus())
                .build();
    }
}
