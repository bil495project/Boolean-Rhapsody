package com.roadrunner.route.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.roadrunner.place.entity.Place;

/**
 * Stateless scoring and candidate-filtering service for the new
 * weight-based route generation model.
 */
@Service
public class RouteScoringService {

    private final PlaceLabelService labelService;

    public RouteScoringService(PlaceLabelService labelService) {
        this.labelService = labelService;
    }

    /**
     * Builds the candidate pool from all places.
     * Filters out non-operational places and those with fewer than
     * {@code minRatingCount} reviews.
     */
    public List<Place> buildCandidatePool(List<Place> allPlaces,
                                          int minRatingCount) {
        List<Place> pool = new ArrayList<>();
        for (Place p : allPlaces) {
            String businessStatus = p.getBusinessStatus();
            if (businessStatus != null && !businessStatus.isBlank()
                    && !businessStatus.strip().equalsIgnoreCase("OPERATIONAL")) {
                continue;
            }

            int ratingCount = p.getRatingCount() != null ? p.getRatingCount() : 0;
            if (ratingCount < minRatingCount) {
                continue;
            }
            pool.add(p);
        }
        return pool;
    }

    /**
     * Scores a hotel candidate.
     * Higher {@code hotelCenterBias} means stronger centrality preference.
     */
    public double scoreHotelCandidate(Place hotel,
                                      double hotelCenterBias,
                                      double kizilayLat,
                                      double kizilayLng) {
        double ratingNorm = ratingQualityScore(hotel);
        double popNorm = popularityConfidence(hotel);

        double distKm = GeoUtils.haversineKm(kizilayLat, kizilayLng,
                hotel.getLatitude(), hotel.getLongitude());
        double centralityNorm = 1.0 - Math.min(distKm / 30.0, 1.0);

        double centerWeight = 0.20 + 0.55 * hotelCenterBias;
        double ratingWeight = 0.65 - 0.45 * hotelCenterBias;
        double popWeight = 0.15;

        return ratingNorm * ratingWeight
                + centralityNorm * centerWeight
                + popNorm * popWeight;
    }

    /**
     * Scores an interior POI candidate.
     * Budget is intentionally neutral for now because price data is not usable.
     */
    public double scoreInteriorCandidate(Place place,
                                         RouteLabel label,
                                         double categoryWeight,
                                         double budgetLevel,
                                         double sparsity,
                                         double anchorLat,
                                         double anchorLng,
                                         double randomBonus) {
        double ratingNorm = ratingQualityScore(place);
        double popNorm = popularityConfidence(place);
        double qualityScore = qualityConfidenceScore(place);
        double budgetCompatibility = switch (label) {
            case RESTORAN_TOLERANSI, KAFE_TATLI -> computeBudgetCompatibility(place, budgetLevel);
            default -> 1.0;
        };

        double distKm = GeoUtils.haversineKm(anchorLat, anchorLng,
                place.getLatitude(), place.getLongitude());
        double distPenaltyFactor = 1.0 - sparsity;
        double distPenalty = 0.028 * distKm * distPenaltyFactor;

        return (0.52 * qualityScore)
                + (0.18 * ratingNorm)
                + (0.08 * popNorm)
                + (0.08 * budgetCompatibility)
                + (0.18 * categoryWeight)
                + (0.04 * randomBonus)
                - distPenalty;
    }

    /**
     * Kept for compatibility with existing tests and future reactivation.
     * Currently neutral because price data is not populated reliably.
     */
    public double computeBudgetCompatibility(Place place, double budgetLevel) {
        int priceOrdinal = parsePriceOrdinal(place.getPriceLevel());
        if (priceOrdinal < 0) {
            return 0.65;
        }
        double target = 0.5 + (Math.max(0.0, Math.min(1.0, budgetLevel)) * 3.0);
        double distance = Math.abs(priceOrdinal - target);
        return Math.max(0.0, 1.0 - (distance / 4.0));
    }

    double qualityConfidenceScore(Place place) {
        return (0.58 * ratingQualityScore(place))
                + (0.42 * popularityConfidence(place));
    }

    double ratingQualityScore(Place place) {
        double rating = place.getRatingScore() != null ? place.getRatingScore() : 0.0;
        double normalized = Math.max(0.0, Math.min(1.0, (rating - 3.5) / 1.5));
        return Math.pow(normalized, 1.65);
    }

    double popularityConfidence(Place place) {
        int ratingCount = place.getRatingCount() != null ? place.getRatingCount() : 0;
        double confidence = 1.0 - Math.exp(-Math.max(0, ratingCount) / 900.0);
        return Math.max(0.0, Math.min(1.0, confidence));
    }

    /**
     * Estimates visit duration based on the place's semantic label.
     */
    public int estimateVisitMinutes(Place p) {
        RouteLabel label = labelService.label(p);
        return switch (label) {
            case HOTEL -> 20;
            case TARIHI_ALANLAR -> 75;
            case RESTORAN_TOLERANSI -> 70;
            case KAFE_TATLI -> 45;
            case PARK_VE_SEYIR_NOKTALARI -> 60;
            case DOGAL_ALANLAR -> 60;
            case LANDMARK -> 60;
            case GECE_HAYATI -> 90;
            case UNKNOWN -> 45;
        };
    }

    static int parsePriceOrdinal(String priceLevel) {
        if (priceLevel == null || priceLevel.isBlank()) {
            return -1;
        }
        String pl = priceLevel.strip().toUpperCase();

        if (pl.contains("FREE")) return 0;
        if (pl.contains("INEXPENSIVE")) return 1;
        if (pl.contains("MODERATE")) return 2;
        if (pl.contains("EXPENSIVE") && !pl.contains("VERY")) return 3;
        if (pl.contains("VERY_EXPENSIVE") || pl.contains("VERY EXPENSIVE")) return 4;

        try {
            int value = (int) Double.parseDouble(pl);
            return Math.max(0, Math.min(value, 4));
        } catch (Exception e) {
            return -1;
        }
    }
}
