package com.roadrunner.route.service;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;


import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.roadrunner.place.entity.Place;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RouteScoringService}.
 * Uses real GeoUtils (pure math — never mocked).
 */
@ExtendWith(MockitoExtension.class)
@Epic("Route Generation")
@Feature("Unit Tests")
@DisplayName("Unit Tests - RouteScoringService")
class RouteScoringServiceTest {

    private static final String TEST_REQUEST_ID = "test-req-001";
    private static final double ANKARA_LAT = 39.9208;
    private static final double ANKARA_LNG = 32.8541;

    @InjectMocks
    private RouteScoringService scoringService;

    private Place buildPlace(String id, String name, String types,
                             double lat, double lng, double rating,
                             int ratingCount, String businessStatus) {
        return Place.builder()
                .id(id)
                .name(name)
                .types(types)
                .latitude(lat)
                .longitude(lng)
                .ratingScore(rating)
                .ratingCount(ratingCount)
                .businessStatus(businessStatus)
                .build();
    }

    // ================================================================
    // scorePlace()
    // ================================================================


    @DisplayName("TC-RGU-036: Rating yüksek olduğunda skor daha yüksek çıkıyor mu")
@Test
    void shouldReturnHigherScore_whenRatingIsHigher() {
        // given
        Place highRated = buildPlace("p1", "High", "restaurant",
                ANKARA_LAT, ANKARA_LNG, 4.5, 100, "OPERATIONAL");
        Place lowRated = buildPlace("p2", "Low", "restaurant",
                ANKARA_LAT, ANKARA_LNG, 3.0, 100, "OPERATIONAL");

        // when
        double scoreHigh = scoringService.scorePlace(highRated, Map.of(), null, null);
        double scoreLow = scoringService.scorePlace(lowRated, Map.of(), null, null);

        // then
        assertThat(scoreHigh).isGreaterThan(scoreLow);
    }

    @DisplayName("TC-RGU-037: Tür ağırlığı (weight) eşleştiğinde skor artıyor mu")
@Test
    void shouldReturnHigherScore_whenTypeWeightMatches() {
        // given
        Place museum = buildPlace("p1", "Museum", "museum",
                ANKARA_LAT, ANKARA_LNG, 4.0, 100, "OPERATIONAL");
        Map<String, Double> weights = Map.of("museum", 1.5);

        // when
        double scoreWithWeight = scoringService.scorePlace(museum, weights, null, null);
        double scoreNoWeight = scoringService.scorePlace(museum, Map.of(), null, null);

        // then
        assertThat(scoreWithWeight).isGreaterThan(scoreNoWeight);
    }

    @DisplayName("TC-RGU-038: Merkez noktası verildiğinde uzak olan yere mesafe cezası uygulanıyor mu")
@Test
    void shouldApplyDistancePenalty_whenCenterIsProvided() {
        // given — one place close, one far away
        Place closePlace = buildPlace("p1", "Close", "restaurant",
                ANKARA_LAT + 0.01, ANKARA_LNG + 0.01, 4.0, 100, "OPERATIONAL");
        Place farPlace = buildPlace("p2", "Far", "restaurant",
                ANKARA_LAT + 0.2, ANKARA_LNG + 0.2, 4.0, 100, "OPERATIONAL");

        // when
        double scoreClose = scoringService.scorePlace(closePlace, Map.of(), ANKARA_LAT, ANKARA_LNG);
        double scoreFar = scoringService.scorePlace(farPlace, Map.of(), ANKARA_LAT, ANKARA_LNG);

        // then
        assertThat(scoreClose).isGreaterThan(scoreFar);
    }

    @DisplayName("TC-RGU-039: Merkez noktası null olduğunda mesafe cezası uygulanmıyor mu")
@Test
    void shouldNotApplyDistancePenalty_whenCenterIsNull() {
        // given
        Place place = buildPlace("p1", "Place", "restaurant",
                50.0, 10.0, 4.0, 100, "OPERATIONAL");

        // when
        double scoreNoCenter = scoringService.scorePlace(place, Map.of(), null, null);
        double scoreWithCenter = scoringService.scorePlace(place, Map.of(), ANKARA_LAT, ANKARA_LNG);

        // then — without center should be >= with center (no penalty)
        assertThat(scoreNoCenter).isGreaterThanOrEqualTo(scoreWithCenter);
    }

    @DisplayName("TC-RGU-040: Popülerlik hesaplamasında doğal logaritma kullanılıyor mu")
@Test
    void shouldUseNaturalLog_forPopularityCalculation() {
        // given
        Place zeroCount = buildPlace("p1", "Zero", "restaurant",
                ANKARA_LAT, ANKARA_LNG, 4.0, 0, "OPERATIONAL");
        Place nineCount = buildPlace("p2", "Nine", "restaurant",
                ANKARA_LAT, ANKARA_LNG, 4.0, 9, "OPERATIONAL");

        // when
        double scoreZero = scoringService.scorePlace(zeroCount, Map.of(), null, null);
        double scoreNine = scoringService.scorePlace(nineCount, Map.of(), null, null);

        // then — log(1)=0, log(10)≈2.302 → difference ≈ 0.4*2.302 ≈ 0.921
        double expectedDiff = 0.4 * (Math.log(10.0) - Math.log(1.0));
        assertThat(scoreNine - scoreZero).isCloseTo(expectedDiff, org.assertj.core.data.Offset.offset(0.01));
    }

    @DisplayName("TC-RGU-041: Hiçbir ağırlık eşleşmediğinde tür bonusu sıfır oluyor mu")
@Test
    void shouldReturnZeroTypeBonus_whenNoWeightsMatch() {
        // given
        Place museum = buildPlace("p1", "Museum", "museum",
                ANKARA_LAT, ANKARA_LNG, 4.0, 100, "OPERATIONAL");
        Map<String, Double> weights = Map.of("restaurant", 1.0);

        // when
        double scoreWithWeight = scoringService.scorePlace(museum, weights, null, null);
        double scoreNoWeight = scoringService.scorePlace(museum, Map.of(), null, null);

        // then — unmatched weight should have no effect
        assertThat(scoreWithWeight).isEqualTo(scoreNoWeight);
    }

    // ================================================================
    // buildCandidatePool()
    // ================================================================


    @DisplayName("TC-RGU-042: İşletme durumu OPERATIONAL olmayan yerler aday havuzundan çıkarılıyor mu")
@Test
    void shouldExcludeNonOperationalPlaces_whenBusinessStatusIsSet() {
        // given
        Place operational = buildPlace("p1", "Open", "restaurant",
                ANKARA_LAT, ANKARA_LNG, 4.0, 100, "OPERATIONAL");
        Place closed = buildPlace("p2", "Closed", "restaurant",
                ANKARA_LAT, ANKARA_LNG, 4.0, 100, "CLOSED_TEMPORARILY");

        // when
        List<Place> pool = scoringService.buildCandidatePool(
                List.of(operational, closed), null, null, null, 0.0);

        // then
        assertThat(pool).hasSize(1);
        assertThat(pool.get(0).getId()).isEqualTo("p1");
    }

    @DisplayName("TC-RGU-043: İşletme durumu boş olan yerler aday havuzuna dahil ediliyor mu")
@Test
    void shouldIncludePlacesWithBlankStatus_whenStatusIsEmpty() {
        // given
        Place blank = buildPlace("p1", "Blank", "restaurant",
                ANKARA_LAT, ANKARA_LNG, 4.0, 100, "");
        Place nullStatus = buildPlace("p2", "Null", "restaurant",
                ANKARA_LAT, ANKARA_LNG, 4.0, 100, null);

        // when
        List<Place> pool = scoringService.buildCandidatePool(
                List.of(blank, nullStatus), null, null, null, 0.0);

        // then
        assertThat(pool).hasSize(2);
    }

    @DisplayName("TC-RGU-044: Minimum rating değeri verildiğinde filtreleme düzgün çalışıyor mu")
@Test
    void shouldFilterByMinRating_whenMinRatingIsPositive() {
        // given
        Place low = buildPlace("p1", "Low", "restaurant", ANKARA_LAT, ANKARA_LNG, 3.0, 10, "OPERATIONAL");
        Place mid = buildPlace("p2", "Mid", "restaurant", ANKARA_LAT, ANKARA_LNG, 4.0, 10, "OPERATIONAL");
        Place high = buildPlace("p3", "High", "restaurant", ANKARA_LAT, ANKARA_LNG, 4.5, 10, "OPERATIONAL");

        // when
        List<Place> pool = scoringService.buildCandidatePool(
                List.of(low, mid, high), null, null, null, 4.0);

        // then
        assertThat(pool).hasSize(2);
        assertThat(pool).extracting(Place::getId).containsExactlyInAnyOrder("p2", "p3");
    }

    @DisplayName("TC-RGU-045: Minimum rating 0 olduğunda rating filtrelemesi uygulanmadan geçiliyor mu")
@Test
    void shouldNotFilterByRating_whenMinRatingIsZero() {
        // given
        Place low = buildPlace("p1", "Low", "restaurant", ANKARA_LAT, ANKARA_LNG, 1.0, 10, "OPERATIONAL");
        Place high = buildPlace("p2", "High", "restaurant", ANKARA_LAT, ANKARA_LNG, 4.5, 10, "OPERATIONAL");

        // when
        List<Place> pool = scoringService.buildCandidatePool(
                List.of(low, high), null, null, null, 0.0);

        // then
        assertThat(pool).hasSize(2);
    }

    @DisplayName("TC-RGU-046: Merkez ve yarıçap belirlendiğinde mesafe filtresi çalışıyor mu")
@Test
    void shouldFilterByRadius_whenCenterAndRadiusAreSet() {
        // given — close place (~1.5 km away), far place (~25 km away)
        Place close = buildPlace("p1", "Close", "restaurant",
                ANKARA_LAT + 0.01, ANKARA_LNG + 0.01, 4.0, 10, "OPERATIONAL");
        Place far = buildPlace("p2", "Far", "restaurant",
                ANKARA_LAT + 0.2, ANKARA_LNG + 0.2, 4.0, 10, "OPERATIONAL");

        // when
        List<Place> pool = scoringService.buildCandidatePool(
                List.of(close, far), ANKARA_LAT, ANKARA_LNG, 5.0, 0.0);

        // then
        assertThat(pool).hasSize(1);
        assertThat(pool.get(0).getId()).isEqualTo("p1");
    }

    @DisplayName("TC-RGU-047: Merkez null olduğunda yarıçap filtresi uygulanmıyor mu")
@Test
    void shouldNotFilterByRadius_whenCenterIsNull() {
        // given
        Place close = buildPlace("p1", "Close", "restaurant",
                ANKARA_LAT, ANKARA_LNG, 4.0, 10, "OPERATIONAL");
        Place far = buildPlace("p2", "Far", "restaurant",
                50.0, 10.0, 4.0, 10, "OPERATIONAL");

        // when
        List<Place> pool = scoringService.buildCandidatePool(
                List.of(close, far), null, null, 5.0, 0.0);

        // then — no center → no radius check
        assertThat(pool).hasSize(2);
    }

    @DisplayName("TC-RGU-048: Tüm yerler filtrelendiğinde boş liste dönüyor mu")
@Test
    void shouldReturnEmptyList_whenAllPlacesAreFiltered() {
        // given
        Place low1 = buildPlace("p1", "Low1", "restaurant", ANKARA_LAT, ANKARA_LNG, 2.0, 10, "OPERATIONAL");
        Place low2 = buildPlace("p2", "Low2", "restaurant", ANKARA_LAT, ANKARA_LNG, 1.5, 10, "OPERATIONAL");

        // when
        List<Place> pool = scoringService.buildCandidatePool(
                List.of(low1, low2), null, null, null, 5.0);

        // then
        assertThat(pool).isEmpty();
    }

    // ================================================================
    // estimateVisitMinutes()
    // ================================================================


    @DisplayName("TC-RGU-049: Place türü hotel olduğunda ziyaret süresi 20dk hesaplanıyor mu")
@Test
    void shouldReturn20_whenTypeIsHotel() {
        // given
        Place p = buildPlace("p1", "Hotel", "hotel", ANKARA_LAT, ANKARA_LNG, 4.0, 10, "OPERATIONAL");
        // when / then
        assertThat(scoringService.estimateVisitMinutes(p)).isEqualTo(20);
    }

    @DisplayName("TC-RGU-050: Place türü lodging olduğunda ziyaret süresi 20dk hesaplanıyor mu")
@Test
    void shouldReturn20_whenTypeIsLodging() {
        // given
        Place p = buildPlace("p1", "Lodging", "lodging", ANKARA_LAT, ANKARA_LNG, 4.0, 10, "OPERATIONAL");
        // when / then
        assertThat(scoringService.estimateVisitMinutes(p)).isEqualTo(20);
    }

    @DisplayName("TC-RGU-051: Place türü museum olduğunda ziyaret süresi 90dk hesaplanıyor mu")
@Test
    void shouldReturn90_whenTypeIsMuseum() {
        // given
        Place p = buildPlace("p1", "Museum", "museum", ANKARA_LAT, ANKARA_LNG, 4.0, 10, "OPERATIONAL");
        // when / then
        assertThat(scoringService.estimateVisitMinutes(p)).isEqualTo(90);
    }

    @DisplayName("TC-RGU-052: Place türü restaurant olduğunda ziyaret süresi 70dk hesaplanıyor mu")
@Test
    void shouldReturn70_whenTypeIsRestaurant() {
        // given
        Place p = buildPlace("p1", "Restaurant", "restaurant", ANKARA_LAT, ANKARA_LNG, 4.0, 10, "OPERATIONAL");
        // when / then
        assertThat(scoringService.estimateVisitMinutes(p)).isEqualTo(70);
    }

    @DisplayName("TC-RGU-053: Place türü cafe olduğunda ziyaret süresi 50dk hesaplanıyor mu")
@Test
    void shouldReturn50_whenTypeIsCafe() {
        // given
        Place p = buildPlace("p1", "Cafe", "cafe", ANKARA_LAT, ANKARA_LNG, 4.0, 10, "OPERATIONAL");
        // when / then
        assertThat(scoringService.estimateVisitMinutes(p)).isEqualTo(50);
    }

    @DisplayName("TC-RGU-054: Place türü park olduğunda ziyaret süresi 60dk hesaplanıyor mu")
@Test
    void shouldReturn60_whenTypeIsPark() {
        // given
        Place p = buildPlace("p1", "Park", "park", ANKARA_LAT, ANKARA_LNG, 4.0, 10, "OPERATIONAL");
        // when / then
        assertThat(scoringService.estimateVisitMinutes(p)).isEqualTo(60);
    }

    @DisplayName("TC-RGU-055: Place türü landmark olduğunda ziyaret süresi 60dk hesaplanıyor mu")
@Test
    void shouldReturn60_whenTypeIsLandmark() {
        // given
        Place p = buildPlace("p1", "Landmark", "landmark", ANKARA_LAT, ANKARA_LNG, 4.0, 10, "OPERATIONAL");
        // when / then
        assertThat(scoringService.estimateVisitMinutes(p)).isEqualTo(60);
    }

    @DisplayName("TC-RGU-056: Bilinmeyen bir place türünde ziyaret süresi 45dk dönüyor mu")
@Test
    void shouldReturn45_whenTypeIsUnrecognized() {
        // given
        Place p = buildPlace("p1", "Aquarium", "aquarium", ANKARA_LAT, ANKARA_LNG, 4.0, 10, "OPERATIONAL");
        // when / then
        assertThat(scoringService.estimateVisitMinutes(p)).isEqualTo(45);
    }

    @DisplayName("TC-RGU-057: Birden fazla tür olduğunda ilk eşleşen türe göre süre hesaplanıyor mu")
@Test
    void shouldMatchFirstRecognizedType_whenMultipleTypesPresent() {
        // given — hotel comes before restaurant in the type list
        Place p = buildPlace("p1", "Hotel Rest", "hotel,restaurant",
                ANKARA_LAT, ANKARA_LNG, 4.0, 10, "OPERATIONAL");
        // when / then
        assertThat(scoringService.estimateVisitMinutes(p)).isEqualTo(20);
    }

    @DisplayName("TC-RGU-058: Tür listesi boş olduğunda süre varsayılan (45dk) dönüyor mu")
@Test
    void shouldReturn45_whenTypesListIsEmpty() {
        // given
        Place p = buildPlace("p1", "Empty", "", ANKARA_LAT, ANKARA_LNG, 4.0, 10, "OPERATIONAL");
        // when / then
        assertThat(scoringService.estimateVisitMinutes(p)).isEqualTo(45);
    }

    // ================================================================
    // pickOnePlace()
    // ================================================================


    @DisplayName("TC-RGU-059: Aday havuzu boş olduğunda seçim boş dönüyor mu")
@Test
    void shouldReturnEmpty_whenNoCandidatesExist() {
        // given
        List<Place> empty = List.of();

        // when
        Optional<Place> result = scoringService.pickOnePlace(
                empty, null, Set.of(), 0.0, "", Map.of(), null, null);

        // then
        assertThat(result).isEmpty();
    }

    @DisplayName("TC-RGU-060: Tüm adaylar hariç tutulanlar listesindeyse seçim boş dönüyor mu")
@Test
    void shouldReturnEmpty_whenAllCandidatesAreExcluded() {
        // given
        Place p1 = buildPlace("p1", "A", "museum", ANKARA_LAT, ANKARA_LNG, 4.0, 100, "OPERATIONAL");
        Place p2 = buildPlace("p2", "B", "museum", ANKARA_LAT, ANKARA_LNG, 4.5, 200, "OPERATIONAL");

        // when
        Optional<Place> result = scoringService.pickOnePlace(
                List.of(p1, p2), null, Set.of("p1", "p2"), 0.0, "", Map.of(), null, null);

        // then
        assertThat(result).isEmpty();
    }

    @DisplayName("TC-RGU-061: Tür filtresi hiçbir adayla eşleşmediğinde seçim boş dönüyor mu")
@Test
    void shouldReturnEmpty_whenTypeFilterMatchesNoPlaces() {
        // given
        Place r1 = buildPlace("p1", "R1", "restaurant", ANKARA_LAT, ANKARA_LNG, 4.0, 100, "OPERATIONAL");
        Place r2 = buildPlace("p2", "R2", "restaurant", ANKARA_LAT, ANKARA_LNG, 4.5, 200, "OPERATIONAL");

        // when
        Optional<Place> result = scoringService.pickOnePlace(
                List.of(r1, r2), "museum", Set.of(), 0.0, "", Map.of(), null, null);

        // then
        assertThat(result).isEmpty();
    }

    @DisplayName("TC-RGU-062: İstenen tür belirlendiğinde sadece o türden bir place seçiliyor mu")
@Test
    void shouldReturnOnlyMatchingType_whenDesiredTypeIsSet() {
        // given
        Place museum = buildPlace("p1", "Museum", "museum", ANKARA_LAT, ANKARA_LNG, 4.0, 100, "OPERATIONAL");
        Place restaurant = buildPlace("p2", "Rest", "restaurant", ANKARA_LAT, ANKARA_LNG, 4.5, 200, "OPERATIONAL");

        // when
        Optional<Place> result = scoringService.pickOnePlace(
                List.of(museum, restaurant), "museum", Set.of(), 0.0, "", Map.of(), null, null);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("p1");
    }

    @DisplayName("TC-RGU-063: Min rating değeri olduğunda düşük ratingli adaylar eleniyor mu")
@Test
    void shouldExcludeByMinRating_whenMinRatingIsSet() {
        // given
        Place low = buildPlace("p1", "Low", "museum", ANKARA_LAT, ANKARA_LNG, 3.0, 100, "OPERATIONAL");
        Place high = buildPlace("p2", "High", "museum", ANKARA_LAT, ANKARA_LNG, 4.5, 200, "OPERATIONAL");

        // when
        Optional<Place> result = scoringService.pickOnePlace(
                List.of(low, high), null, Set.of(), 4.0, "", Map.of(), null, null);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("p2");
    }

    @DisplayName("TC-RGU-064: Geçerli adaylar olduğunda bir aday başarıyla seçiliyor mu")
@Test
    void shouldReturnAResult_whenValidCandidatesExist() {
        // given
        List<Place> pool = List.of(
                buildPlace("p1", "A", "museum", ANKARA_LAT, ANKARA_LNG, 4.0, 100, "OPERATIONAL"),
                buildPlace("p2", "B", "park", ANKARA_LAT, ANKARA_LNG, 4.2, 150, "OPERATIONAL"),
                buildPlace("p3", "C", "cafe", ANKARA_LAT, ANKARA_LNG, 3.8, 80, "OPERATIONAL"),
                buildPlace("p4", "D", "restaurant", ANKARA_LAT, ANKARA_LNG, 4.5, 200, "OPERATIONAL"),
                buildPlace("p5", "E", "landmark", ANKARA_LAT, ANKARA_LNG, 4.1, 120, "OPERATIONAL")
        );

        // when
        Optional<Place> result = scoringService.pickOnePlace(
                pool, null, Set.of(), 0.0, "", Map.of(), null, null);

        // then
        assertThat(result).isPresent();
        assertThat(pool).extracting(Place::getId).contains(result.get().getId());
    }

    @DisplayName("TC-RGU-065: Tüm havuzdan değil en yüksek puanlı (Top 25) adaylardan seçim yapılıyor mu")
@Test
    void shouldPickFromTopN_notFromEntirePool() {
        // given — create 30 candidates with descending scores
        List<Place> pool = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) {
            pool.add(buildPlace("p" + i, "Place " + i, "museum",
                    ANKARA_LAT, ANKARA_LNG, 5.0 - (i * 0.05), 100, "OPERATIONAL"));
        }

        // when — run multiple times
        boolean allFromTop25 = true;
        for (int trial = 0; trial < 20; trial++) {
            Optional<Place> result = scoringService.pickOnePlace(
                    pool, null, Set.of(), 0.0, "", Map.of(), null, null);
            if (result.isPresent()) {
                // The top-25 by score have IDs p0..p24
                String id = result.get().getId();
                int idx = Integer.parseInt(id.substring(1));
                if (idx >= 25) {
                    allFromTop25 = false;
                    break;
                }
            }
        }

        // then
        assertThat(allFromTop25).isTrue();
    }
}
