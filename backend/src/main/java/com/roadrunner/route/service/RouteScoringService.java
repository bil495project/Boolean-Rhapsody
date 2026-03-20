package com.roadrunner.route.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.roadrunner.place.entity.Place;

/**
 * Stateless scoring and candidate-filtering service.
 * Ported from the private scoring methods of Python's {@code TripRequest}.
 */
@Service
public class RouteScoringService {

    /**
     * Scores a Place for ranking purposes.
     * Ported from Python {@code _score_place()}.
     *
     * @param p               the place to score
     * @param overrideWeights type→weight map (may be null/empty)
     * @param centerLat       optional center latitude for distance penalty
     * @param centerLng       optional center longitude for distance penalty
     * @return computed score (higher is better)
     */
    public double scorePlace(Place p,
            Map<String, Double> overrideWeights,
            Double centerLat,
            Double centerLng) {
        double rating = p.getRatingScore() != null ? p.getRatingScore() : 0.0;
        int rCount = p.getRatingCount() != null ? p.getRatingCount() : 0;
        double pop = Math.log(Math.max(rCount, 0) + 1.0);

        double typeBonus = 0.0;
        if (overrideWeights != null && !overrideWeights.isEmpty()) {
            List<String> types = GeoUtils.parseTypesFromEntity(p.getTypes());
            for (String t : types) {
                String tt = t.strip().toLowerCase();
                typeBonus += overrideWeights.getOrDefault(tt, 0.0);
            }
        }

        double distPenalty = 0.0;
        if (centerLat != null && centerLng != null) {
            double d = GeoUtils.haversineKm(
                    centerLat, centerLng,
                    p.getLatitude(), p.getLongitude());
            distPenalty = 0.05 * d;
        }

        return (1.0 * rating) + (0.4 * pop) + (1.0 * typeBonus) - distPenalty;
    }

    /**
     * Builds the filtered candidate pool from all places.
     * Ported from Python {@code _build_candidate_pool()}.
     *
     * @param allPlaces every place from the repository
     * @param centerLat optional center latitude for radius filter
     * @param centerLng optional center longitude for radius filter
     * @param radiusKm  optional radius in km
     * @param minRating minimum rating threshold (0 = disabled)
     * @return filtered list of candidate places
     */
    public List<Place> buildCandidatePool(List<Place> allPlaces,
            Double centerLat,
            Double centerLng,
            Double radiusKm,
            double minRating) {
        List<Place> pool = new ArrayList<>();
        for (Place p : allPlaces) {
            // Filter out non-operational businesses
            String bs = p.getBusinessStatus();
            if (bs != null && !bs.isBlank()
                    && !bs.strip().equalsIgnoreCase("OPERATIONAL")) {
                continue;
            }
            // Filter by minimum rating
            double score = p.getRatingScore() != null ? p.getRatingScore() : 0.0;
            if (minRating > 0 && score < minRating) {
                continue;
            }
            // Filter by radius
            if (centerLat != null && centerLng != null && radiusKm != null) {
                double dist = GeoUtils.haversineKm(
                        centerLat, centerLng,
                        p.getLatitude(), p.getLongitude());
                if (dist > radiusKm)
                    continue;
            }
            pool.add(p);
        }
        return pool;
    }

    /**
     * Estimates visit duration based on place types.
     * Ported from Python {@code _estimate_visit_minutes()}.
     */
    public int estimateVisitMinutes(Place p) {
        List<String> types = GeoUtils.parseTypesFromEntity(p.getTypes());
        Set<String> ts = new java.util.HashSet<>();
        for (String t : types)
            ts.add(t.toLowerCase());

        if (ts.contains("hotel") || ts.contains("lodging") || ts.contains("guest_house"))
            return 20;
        if (ts.contains("museum"))
            return 90;
        if (ts.contains("restaurant"))
            return 70;
        if (ts.contains("cafe"))
            return 50;
        if (ts.contains("park") || ts.contains("nature"))
            return 60;
        if (ts.contains("historical") || ts.contains("landmark")
                || ts.contains("tourist_attraction"))
            return 60;
        return 45;
    }

    /**
     * Picks one random place from the top-25 scored candidates matching
     * the given filters.
     * Ported from Python {@code _pick_one_place()}.
     *
     * @param candidatePool   pre-built candidate pool
     * @param desiredType     required type (null/blank = any)
     * @param excludedIds     POI IDs to exclude
     * @param minRating       minimum rating threshold
     * @param maxPriceLevel   max price level as string-integer (blank = any)
     * @param overrideWeights type weight overrides for scoring
     * @param centerLat       center latitude for scoring
     * @param centerLng       center longitude for scoring
     * @return a randomly chosen place from the top-25, or empty
     */
    public Optional<Place> pickOnePlace(List<Place> candidatePool,
            String desiredType,
            Set<String> excludedIds,
            double minRating,
            String maxPriceLevel,
            Map<String, Double> overrideWeights,
            Double centerLat,
            Double centerLng) {
        String dt = (desiredType == null ? "" : desiredType).strip().toLowerCase();
        Integer maxPrice = parseIntSafe(maxPriceLevel);

        List<Place> candidates = new ArrayList<>();
        for (Place p : candidatePool) {
            if (excludedIds != null && excludedIds.contains(p.getId()))
                continue;

            double score = p.getRatingScore() != null ? p.getRatingScore() : 0.0;
            if (minRating > 0 && score < minRating)
                continue;

            if (!dt.isEmpty() && !hasType(p, dt))
                continue;

            if (maxPrice != null) {
                Integer placePrice = parseIntSafe(p.getPriceLevel());
                if (placePrice != null && placePrice > maxPrice)
                    continue;
            }

            candidates.add(p);
        }

        if (candidates.isEmpty())
            return Optional.empty();

        // Sort by score descending
        candidates.sort((a, b) -> Double.compare(
                scorePlace(b, overrideWeights, centerLat, centerLng),
                scorePlace(a, overrideWeights, centerLat, centerLng)));

        int topN = Math.min(25, candidates.size());
        List<Place> top = candidates.subList(0, topN);
        return Optional.of(top.get(new Random().nextInt(top.size())));
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Checks if a Place entity has a given type (case-insensitive).
     * Mirrors Python {@code Place.has_type()}.
     */
    public boolean hasType(Place p, String type) {
        if (type == null || type.isBlank())
            return false;
        String tt = type.strip().toLowerCase();
        List<String> types = GeoUtils.parseTypesFromEntity(p.getTypes());
        for (String t : types) {
            if (t.strip().toLowerCase().equals(tt))
                return true;
        }
        return false;
    }

    private static Integer parseIntSafe(String s) {
        if (s == null || s.isBlank())
            return null;
        try {
            return (int) Double.parseDouble(s.strip());
        } catch (Exception e) {
            return null;
        }
    }
}
