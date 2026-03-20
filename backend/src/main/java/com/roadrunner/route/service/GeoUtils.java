package com.roadrunner.route.service;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Pure-math utility class ported from Python {@code utils.py}.
 * All methods are static — no Spring wiring needed.
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0088;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GeoUtils() { /* utility class */ }

    // ------------------------------------------------------------------
    // Distance & travel
    // ------------------------------------------------------------------

    /**
     * Haversine great-circle distance between two WGS-84 points.
     * Ported exactly from Python {@code haversine_km()}.
     */
    public static double haversineKm(double lat1, double lon1,
                                     double lat2, double lon2) {
        double dlat = Math.toRadians(lat2 - lat1);
        double dlon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dlat / 2) * Math.sin(dlat / 2)
                 + Math.cos(Math.toRadians(lat1))
                 * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dlon / 2) * Math.sin(dlon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    /** Converts kilometres to metres. */
    public static double kmToMeters(double km) {
        return km * 1000.0;
    }

    /**
     * Estimates travel time in seconds for a given distance and mode.
     * Ported from Python {@code travel_seconds()}.
     * <ul>
     *   <li>{@code "walking"} → 4.8 km/h</li>
     *   <li>{@code "cycling"} → 14.0 km/h</li>
     *   <li>default (driving) → 25.0 km/h (city)</li>
     * </ul>
     */
    public static int travelSeconds(double distanceKm, String mode) {
        String m = (mode == null ? "driving" : mode).toLowerCase();
        double kmh;
        switch (m) {
            case "walking" -> kmh = 4.8;
            case "cycling" -> kmh = 14.0;
            default        -> kmh = 25.0;
        }
        double hours = distanceKm / Math.max(kmh, 1e-9);
        return (int) Math.round(hours * 3600.0);
    }

    // ------------------------------------------------------------------
    // Safe parsing helpers
    // ------------------------------------------------------------------

    /** Returns parsed double or {@code defaultVal} on any failure. */
    public static double safeFloat(String x, double defaultVal) {
        try {
            if (x == null) return defaultVal;
            String s = x.strip();
            if (s.isEmpty()) return defaultVal;
            return Double.parseDouble(s);
        } catch (Exception e) {
            return defaultVal;
        }
    }

    /** Returns parsed int (via float truncation) or {@code defaultVal} on any failure. */
    public static int safeInt(String x, int defaultVal) {
        try {
            if (x == null) return defaultVal;
            String s = x.strip();
            if (s.isEmpty()) return defaultVal;
            return (int) Double.parseDouble(s);
        } catch (Exception e) {
            return defaultVal;
        }
    }

    /**
     * Parses a types cell that may be JSON array, pipe-delimited,
     * comma-delimited, or a singleton value.
     * Ported from Python {@code parse_types_cell()}.
     */
    public static List<String> parseTypesList(String cell) {
        if (cell == null) return List.of();
        String s = cell.strip();
        if (s.isEmpty()) return List.of();

        // 1) Try JSON array
        try {
            List<Object> arr = MAPPER.readValue(s, new TypeReference<>() {});
            List<String> result = new ArrayList<>();
            for (Object o : arr) {
                String t = String.valueOf(o).strip();
                if (!t.isEmpty()) result.add(t);
            }
            return result;
        } catch (Exception ignored) { /* not JSON */ }

        // 2) Pipe-delimited
        if (s.contains("|")) {
            return splitAndTrim(s, "\\|");
        }
        // 3) Comma-delimited
        if (s.contains(",")) {
            return splitAndTrim(s, ",");
        }
        // 4) Singleton
        return List.of(s);
    }

    /**
     * Parses a comma-separated types string from the Place entity.
     * This handles the JPA entity's {@code types} field which is stored
     * as a plain comma-separated string in the database.
     */
    public static List<String> parseTypesFromEntity(String types) {
        if (types == null || types.isBlank()) return List.of();
        return splitAndTrim(types, ",");
    }

    // ------------------------------------------------------------------
    // internal
    // ------------------------------------------------------------------

    private static List<String> splitAndTrim(String s, String delimiter) {
        List<String> result = new ArrayList<>();
        for (String part : s.split(delimiter)) {
            String t = part.strip();
            if (!t.isEmpty()) result.add(t);
        }
        return result;
    }
}
