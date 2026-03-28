package com.roadrunner.route.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.roadrunner.place.entity.Place;

/**
 * Default fallback route labeler backed by Google Place types.
 * Keeps a single semantic routing label per POI.
 */
@Service
@Primary
public class DefaultPlaceRouteLabelService implements PlaceRouteLabeler {

    private static final Set<String> EXCLUDED_TYPES = Set.of("gas_station");
    private static final Map<String, RouteLabel> TYPE_TO_LABEL;

    static {
        TYPE_TO_LABEL = new java.util.HashMap<>();

        for (String t : List.of("hotel", "lodging", "guest_house")) {
            TYPE_TO_LABEL.put(t, RouteLabel.HOTEL);
        }
        for (String t : List.of("bar", "night_club")) {
            TYPE_TO_LABEL.put(t, RouteLabel.GECE_HAYATI);
        }
        for (String t : List.of(
                "historical_place", "historical_landmark", "museum",
                "art_gallery", "cultural_center", "library",
                "city_hall", "courthouse")) {
            TYPE_TO_LABEL.put(t, RouteLabel.TARIHI_ALANLAR);
        }
        for (String t : List.of(
                "restaurant", "turkish_restaurant", "breakfast_restaurant",
                "fine_dining_restaurant", "fast_food_restaurant",
                "hamburger_restaurant", "american_restaurant",
                "middle_eastern_restaurant", "sandwich_shop", "bagel_shop",
                "dessert_restaurant", "brunch_restaurant", "steak_house",
                "bar_and_grill", "cafeteria", "food_court",
                "meal_takeaway", "meal_delivery", "food_delivery")) {
            TYPE_TO_LABEL.put(t, RouteLabel.RESTORAN_TOLERANSI);
        }
        for (String t : List.of(
                "cafe", "coffee_shop", "bakery", "tea_house",
                "chocolate_factory")) {
            TYPE_TO_LABEL.put(t, RouteLabel.KAFE_TATLI);
        }
        for (String t : List.of(
                "park", "national_park", "playground", "picnic_ground",
                "garden", "botanical_garden", "hiking_area", "campground",
                "athletic_field", "golf_course")) {
            TYPE_TO_LABEL.put(t, RouteLabel.PARK_VE_SEYIR_NOKTALARI);
        }
        for (String t : List.of(
                "tourist_attraction", "amusement_park", "water_park",
                "amusement_center", "stadium", "concert_hall", "auditorium",
                "sports_complex", "ski_resort", "tour_agency", "travel_agency")) {
            TYPE_TO_LABEL.put(t, RouteLabel.LANDMARK);
        }
        for (String t : List.of("nature_preserve", "nature")) {
            TYPE_TO_LABEL.put(t, RouteLabel.DOGAL_ALANLAR);
        }
    }

    @Override
    public RouteLabel label(Place place) {
        if (place == null) {
            return RouteLabel.UNKNOWN;
        }

        List<String> types = GeoUtils.parseTypesFromEntity(place.getTypes());
        if (types.isEmpty()) {
            return RouteLabel.UNKNOWN;
        }

        for (String type : types) {
            if (EXCLUDED_TYPES.contains(type.toLowerCase())) {
                return RouteLabel.UNKNOWN;
            }
        }

        if (isHistoricMosque(types)) {
            return RouteLabel.TARIHI_ALANLAR;
        }

        RouteLabel best = null;
        int bestPriority = Integer.MAX_VALUE;
        for (String type : types) {
            RouteLabel mapped = TYPE_TO_LABEL.get(type.strip().toLowerCase());
            if (mapped == null) {
                continue;
            }
            int priority = labelPriority(mapped);
            if (priority < bestPriority) {
                bestPriority = priority;
                best = mapped;
            }
        }

        return best != null ? best : RouteLabel.UNKNOWN;
    }

    private static int labelPriority(RouteLabel label) {
        if (label == RouteLabel.HOTEL) {
            return 0;
        }
        if (label == RouteLabel.GECE_HAYATI) {
            return 1;
        }
        if (label == RouteLabel.TARIHI_ALANLAR) {
            return 2;
        }
        if (label == RouteLabel.RESTORAN_TOLERANSI) {
            return 3;
        }
        if (label == RouteLabel.KAFE_TATLI) {
            return 4;
        }
        if (label == RouteLabel.PARK_VE_SEYIR_NOKTALARI) {
            return 5;
        }
        if (label == RouteLabel.LANDMARK) {
            return 6;
        }
        if (label == RouteLabel.DOGAL_ALANLAR) {
            return 7;
        }
        return 8;
    }

    private static boolean isHistoricMosque(List<String> types) {
        boolean mosque = false;
        boolean historic = false;
        for (String type : types) {
            String lower = type.strip().toLowerCase();
            if ("mosque".equals(lower) || "place_of_worship".equals(lower)) {
                mosque = true;
            }
            if ("historical_place".equals(lower) || "historical_landmark".equals(lower)) {
                historic = true;
            }
        }
        return mosque && historic;
    }
}
