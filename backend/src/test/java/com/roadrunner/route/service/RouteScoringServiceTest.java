package com.roadrunner.route.service;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;

import java.util.List;

import com.roadrunner.place.entity.Place;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Route Generation")
@Feature("Unit Tests")
@DisplayName("Unit Tests - RouteScoringService (Weight-Based)")
class RouteScoringServiceTest {

    private static final double ANKARA_LAT = 39.9208;
    private static final double ANKARA_LNG = 32.8541;

    private PlaceLabelService labelService;
    private RouteScoringService scoringService;

    @BeforeEach
    void setUp() {
        labelService = new PlaceLabelService(new DefaultPlaceRouteLabelService());
        scoringService = new RouteScoringService(labelService);
    }

    private Place buildPlace(String id, String name, String types,
                             double lat, double lng, double rating,
                             int ratingCount, String businessStatus) {
        return Place.builder()
                .id(id).name(name).types(types)
                .latitude(lat).longitude(lng)
                .ratingScore(rating).ratingCount(ratingCount)
                .businessStatus(businessStatus)
                .build();
    }

    private Place buildPlaceWithPrice(String id, String name, String types,
                                      double lat, double lng, double rating,
                                      int ratingCount, String priceLevel) {
        return Place.builder()
                .id(id).name(name).types(types)
                .latitude(lat).longitude(lng)
                .ratingScore(rating).ratingCount(ratingCount)
                .businessStatus("OPERATIONAL")
                .priceLevel(priceLevel)
                .build();
    }

    @Test
    @DisplayName("Candidate pool non-operational ve dusuk review count kayitlarini eliyor")
    void shouldBuildCandidatePool() {
        Place open = buildPlace("p1", "Open", "restaurant", ANKARA_LAT, ANKARA_LNG, 4.0, 200, "OPERATIONAL");
        Place lowCount = buildPlace("p2", "Low", "restaurant", ANKARA_LAT, ANKARA_LNG, 4.0, 50, "OPERATIONAL");
        Place closed = buildPlace("p3", "Closed", "restaurant", ANKARA_LAT, ANKARA_LNG, 4.0, 200, "CLOSED_TEMPORARILY");

        List<Place> pool = scoringService.buildCandidatePool(List.of(open, lowCount, closed), 100);

        assertThat(pool).extracting(Place::getId).containsExactly("p1");
    }

    @Test
    @DisplayName("Hotel center bias yuksekken merkezi otel one cikiyor")
    void shouldScoreCentralHotelHigher() {
        Place central = buildPlace("h1", "Central", "hotel", ANKARA_LAT, ANKARA_LNG, 4.0, 200, "OPERATIONAL");
        Place far = buildPlace("h2", "Far", "hotel", ANKARA_LAT + 0.2, ANKARA_LNG + 0.2, 4.0, 200, "OPERATIONAL");

        assertThat(scoringService.scoreHotelCandidate(central, 0.9, ANKARA_LAT, ANKARA_LNG))
                .isGreaterThan(scoringService.scoreHotelCandidate(far, 0.9, ANKARA_LAT, ANKARA_LNG));
    }

    @Test
    @DisplayName("Quality confidence target siralamayi sagliyor")
    void shouldPreferQualityConfidenceOrdering() {
        Place strong = buildPlace("r1", "Strong", "restaurant", ANKARA_LAT, ANKARA_LNG, 4.9, 2000, "OPERATIONAL");
        Place broad = buildPlace("r2", "Broad", "restaurant", ANKARA_LAT, ANKARA_LNG, 4.7, 4000, "OPERATIONAL");
        Place tinyPerfect = buildPlace("r3", "Tiny", "restaurant", ANKARA_LAT, ANKARA_LNG, 5.0, 150, "OPERATIONAL");

        double strongScore = scoringService.qualityConfidenceScore(strong);
        double broadScore = scoringService.qualityConfidenceScore(broad);
        double tinyScore = scoringService.qualityConfidenceScore(tinyPerfect);

        assertThat(strongScore).isGreaterThan(broadScore);
        assertThat(broadScore).isGreaterThan(tinyScore);
    }

    @Test
    @DisplayName("Review count etkisi doygunluga ulasiyor ama rating yakin bandda belirleyici kaliyor")
    void shouldUseDiminishingPopularity() {
        Place lowReviews = buildPlace("r1", "Low", "restaurant", ANKARA_LAT, ANKARA_LNG, 4.8, 150, "OPERATIONAL");
        Place midReviews = buildPlace("r2", "Mid", "restaurant", ANKARA_LAT, ANKARA_LNG, 4.8, 1200, "OPERATIONAL");
        Place veryHighReviews = buildPlace("r3", "High", "restaurant", ANKARA_LAT, ANKARA_LNG, 4.8, 5000, "OPERATIONAL");

        double low = scoringService.popularityConfidence(lowReviews);
        double mid = scoringService.popularityConfidence(midReviews);
        double high = scoringService.popularityConfidence(veryHighReviews);

        assertThat(mid - low).isGreaterThan(high - mid);
    }

    @Test
    @DisplayName("Budget scoring su an notr")
    void shouldTreatBudgetAsNeutral() {
        Place place = buildPlaceWithPrice("r1", "Neutral", "restaurant",
                ANKARA_LAT, ANKARA_LNG, 4.5, 350, "PRICE_LEVEL_EXPENSIVE");

        double lowBudgetScore = scoringService.scoreInteriorCandidate(
                place, 0.8, 0.0, false, 0.5, ANKARA_LAT, ANKARA_LNG, 0.0);
        double highBudgetScore = scoringService.scoreInteriorCandidate(
                place, 0.8, 1.0, false, 0.5, ANKARA_LAT, ANKARA_LNG, 0.0);

        assertThat(lowBudgetScore).isEqualTo(highBudgetScore);
        assertThat(scoringService.computeBudgetCompatibility(place, 0.1)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Dusuk sparsity uzak POI icin daha fazla ceza uyguluyor")
    void shouldPenalizeDistanceMoreAtLowSparsity() {
        Place far = buildPlace("p1", "Far", "park", ANKARA_LAT + 0.2, ANKARA_LNG + 0.2, 4.5, 220, "OPERATIONAL");

        double compactScore = scoringService.scoreInteriorCandidate(
                far, 0.7, 0.5, false, 0.1, ANKARA_LAT, ANKARA_LNG, 0.0);
        double spreadScore = scoringService.scoreInteriorCandidate(
                far, 0.7, 0.5, false, 0.9, ANKARA_LAT, ANKARA_LNG, 0.0);

        assertThat(compactScore).isLessThan(spreadScore);
    }

    @Test
    @DisplayName("Visit durations semantic labela gore hesaplanir")
    void shouldEstimateVisitMinutesByLabel() {
        assertThat(scoringService.estimateVisitMinutes(buildPlace("h", "Hotel", "hotel", ANKARA_LAT, ANKARA_LNG, 4.0, 200, "OPERATIONAL"))).isEqualTo(20);
        assertThat(scoringService.estimateVisitMinutes(buildPlace("m", "Museum", "museum", ANKARA_LAT, ANKARA_LNG, 4.0, 200, "OPERATIONAL"))).isEqualTo(75);
        assertThat(scoringService.estimateVisitMinutes(buildPlace("r", "Restaurant", "restaurant", ANKARA_LAT, ANKARA_LNG, 4.0, 200, "OPERATIONAL"))).isEqualTo(70);
        assertThat(scoringService.estimateVisitMinutes(buildPlace("c", "Cafe", "cafe", ANKARA_LAT, ANKARA_LNG, 4.0, 200, "OPERATIONAL"))).isEqualTo(45);
    }
}
