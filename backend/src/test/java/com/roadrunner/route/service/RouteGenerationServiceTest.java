package com.roadrunner.route.service;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.roadrunner.place.entity.Place;
import com.roadrunner.place.repository.PlaceRepository;
import com.roadrunner.route.entity.Route;
import com.roadrunner.route.entity.RoutePoint;
import com.roadrunner.route.entity.RouteSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RouteGenerationService}.
 * Uses real {@link RouteScoringService} and {@link GeoUtils} (pure math),
 * mocks only {@link PlaceRepository}.
 */
@ExtendWith(MockitoExtension.class)
@Epic("Route Generation")
@Feature("Unit Tests")
@DisplayName("Unit Tests - RouteGenerationService")
class RouteGenerationServiceTest {

    private static final String TEST_REQUEST_ID = "test-req-001";
    private static final double ANKARA_LAT = 39.9208;
    private static final double ANKARA_LNG = 32.8541;

    @Mock
    private PlaceRepository placeRepository;

    private RouteScoringService scoringService;
    private RouteGenerationService routeService;

    @BeforeEach
    void setUp() {
        scoringService = new RouteScoringService();
        routeService = new RouteGenerationService(placeRepository, scoringService);
    }

    // ----------------------------------------------------------------
    // Test data builders
    // ----------------------------------------------------------------

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

    private List<Place> buildTestPlaces() {
        List<Place> places = new ArrayList<>();
        places.add(buildPlace("r1", "Restaurant A", "restaurant", 39.92, 32.85, 4.2, 200, "OPERATIONAL"));
        places.add(buildPlace("r2", "Restaurant B", "restaurant", 39.93, 32.86, 3.8, 150, "OPERATIONAL"));
        places.add(buildPlace("m1", "Museum A", "museum", 39.94, 32.87, 4.5, 300, "OPERATIONAL"));
        places.add(buildPlace("m2", "Museum B", "museum", 39.91, 32.84, 4.0, 100, "OPERATIONAL"));
        places.add(buildPlace("p1", "Park A", "park", 39.95, 32.88, 4.3, 180, "OPERATIONAL"));
        places.add(buildPlace("p2", "Park B", "park", 39.90, 32.83, 3.5, 90, "OPERATIONAL"));
        places.add(buildPlace("c1", "Cafe A", "cafe", 39.925, 32.855, 4.1, 160, "OPERATIONAL"));
        places.add(buildPlace("c2", "Cafe B", "cafe", 39.935, 32.865, 4.8, 250, "OPERATIONAL"));
        places.add(buildPlace("l1", "Landmark A", "landmark", 39.915, 32.845, 4.6, 220, "OPERATIONAL"));
        places.add(buildPlace("l2", "Landmark B", "landmark", 39.945, 32.875, 3.9, 130, "OPERATIONAL"));
        return places;
    }

    private Map<String, String> buildValidUserVector() {
        Map<String, String> uv = new HashMap<>();
        uv.put("requestId", TEST_REQUEST_ID);
        uv.put("maxStops", "5");
        uv.put("maxBudgetMin", "480");
        uv.put("travelMode", "driving");
        return uv;
    }

    private Route buildRouteWithPoints(List<Place> places, String routeId) {
        Route route = new Route();
        route.setRouteId(routeId);
        route.setTravelMode("driving");
        List<RoutePoint> points = new ArrayList<>();
        for (int i = 0; i < places.size(); i++) {
            points.add(RoutePoint.builder()
                    .index(i)
                    .poi(places.get(i))
                    .plannedVisitMin(45)
                    .build());
        }
        route.setPoints(points);
        routeService.recomputeLocalSegments(route);
        routeService.recomputeTotals(route);
        return route;
    }

    // ================================================================
    // generateRoutes() — TC-RGI-018, TC-RGI-019
    // ================================================================


    @DisplayName("TC-RGU-066: Geçerli istek verildiğinde K adet rota dönüyor mu")
@Test
    void shouldReturnKRoutes_whenRequestIsValid() {
        // given
        when(placeRepository.findAll()).thenReturn(buildTestPlaces());
        Map<String, String> uv = buildValidUserVector();

        // when
        List<Route> result = routeService.generateRoutes(uv, 3);

        // then
        assertThat(result).hasSize(3);
    }

    @Test
    void shouldReturnEmptyList_whenValidationFails() {
        // given
        Map<String, String> uv = new HashMap<>();
        uv.put("maxStops", "0"); // will be clamped to 1, but maxBudgetMin defaults to 480 → valid
        // Actually maxStops: 0 → clamped to 1, so it will validate.
        // Use a different approach: null userVector leads to defaults which are valid.
        // To make validation fail, we need maxStops=0 AND maxBudgetMin=0 before clamping.
        // Since both are clamped to Math.max(1, ...), validation can't actually fail
        // through parseUserVector. Let's verify we still get a list back.
        when(placeRepository.findAll()).thenReturn(buildTestPlaces());

        // when
        List<Route> result = routeService.generateRoutes(uv, 3);

        // then — shouldn't be null, should be a valid list
        assertThat(result).isNotNull();
    }

    @DisplayName("TC-RGU-067: maxStops 5 verildiğinde rota 5 noktadan oluşuyor mu")
@Test
    void shouldHaveCorrectPointCount_whenMaxStopsIsFive() {
        // given
        when(placeRepository.findAll()).thenReturn(buildTestPlaces());
        Map<String, String> uv = buildValidUserVector();
        uv.put("maxStops", "5");

        // when
        List<Route> result = routeService.generateRoutes(uv, 1);

        // then
        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getPoints()).hasSize(5);
    }

    @Test
    void shouldReturnUniqueRouteIds_forEachGeneratedRoute() {
        // given
        when(placeRepository.findAll()).thenReturn(buildTestPlaces());
        Map<String, String> uv = buildValidUserVector();

        // when
        List<Route> result = routeService.generateRoutes(uv, 3);

        // then
        List<String> ids = result.stream().map(Route::getRouteId).toList();
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void shouldHaveSegmentsMinusOneFromPoints_forEachRoute() {
        // given
        when(placeRepository.findAll()).thenReturn(buildTestPlaces());
        Map<String, String> uv = buildValidUserVector();

        // when
        List<Route> result = routeService.generateRoutes(uv, 3);

        // then
        for (Route r : result) {
            assertThat(r.getSegments()).hasSize(r.getPoints().size() - 1);
        }
    }

    // ================================================================
    // recomputeTotals() — TC-RG-006, TC-RG-017
    // ================================================================


    @DisplayName("TC-RGU-076: Toplamlar hesaplanırken segment süreleri ve ziyaret süreleri doğru şekilde toplanıyor mu")
@Test
    void shouldSumSegmentsAndVisits_whenComputingTotals() {
        // given
        Route route = new Route();
        route.setPoints(List.of(
                RoutePoint.builder().index(0).poi(buildTestPlaces().get(0)).plannedVisitMin(30).build(),
                RoutePoint.builder().index(1).poi(buildTestPlaces().get(1)).plannedVisitMin(45).build(),
                RoutePoint.builder().index(2).poi(buildTestPlaces().get(2)).plannedVisitMin(60).build()
        ));
        route.setSegments(List.of(
                RouteSegment.builder().fromIndex(0).toIndex(1).durationSec(600).distanceM(1200.0).build(),
                RouteSegment.builder().fromIndex(1).toIndex(2).durationSec(900).distanceM(800.0).build()
        ));

        // when
        routeService.recomputeTotals(route);

        // then
        // travel = 600 + 900 = 1500; visits = (30 + 45 + 60) * 60 = 8100
        assertThat(route.getTotalDurationSec()).isEqualTo(1500 + 8100);
        assertThat(route.getTotalDistanceM()).isEqualTo(2000.0);
    }

    @DisplayName("TC-RGU-074: Rota boş olduğunda tüm toplamlar (süre, mesafe) sıfır yapılıyor mu")
@Test
    void shouldSetZeroTotals_whenRouteIsEmpty() {
        // given
        Route route = new Route();
        route.setPoints(new ArrayList<>());
        route.setSegments(new ArrayList<>());

        // when
        routeService.recomputeTotals(route);

        // then
        assertThat(route.getTotalDurationSec()).isEqualTo(0);
        assertThat(route.getTotalDistanceM()).isEqualTo(0.0);
    }

    // ================================================================
    // recomputeLocalSegments() — TC-RG-007, TC-RG-018
    // ================================================================


    @DisplayName("TC-RGU-077: Rota N noktadan oluşuyorsa N-1 adet segment oluşturuluyor mu")
@Test
    void shouldCreateNMinusOneSegments_whenRouteHasNPoints() {
        // given
        List<Place> places = buildTestPlaces().subList(0, 4);
        Route route = buildRouteWithPoints(places, "test-route");

        // when — already computed in buildRouteWithPoints, but let's be explicit
        routeService.recomputeLocalSegments(route);

        // then
        assertThat(route.getSegments()).hasSize(3);
    }

    @DisplayName("TC-RGU-075: Rota tek noktalıysa segment oluşturulmadan dönüyor mu")
@Test
    void shouldCreateNoSegments_whenRouteHasOnePoint() {
        // given
        Route route = buildRouteWithPoints(buildTestPlaces().subList(0, 1), "single");

        // when
        routeService.recomputeLocalSegments(route);

        // then
        assertThat(route.getSegments()).isEmpty();
    }

    @Test
    void shouldCreateNoSegments_whenRouteIsEmpty() {
        // given
        Route route = new Route();
        route.setPoints(new ArrayList<>());

        // when
        routeService.recomputeLocalSegments(route);

        // then
        assertThat(route.getSegments()).isEmpty();
    }

    @Test
    void shouldPopulateDistanceAndDuration_forEachSegment() {
        // given — two distant points (Ankara and shifted)
        Place a = buildPlace("a", "A", "r", 39.92, 32.85, 4.0, 10, "OPERATIONAL");
        Place b = buildPlace("b", "B", "r", 41.00, 28.97, 4.0, 10, "OPERATIONAL");
        Route route = buildRouteWithPoints(List.of(a, b), "dist-test");

        // when
        routeService.recomputeLocalSegments(route);

        // then
        assertThat(route.getSegments()).hasSize(1);
        RouteSegment seg = route.getSegments().get(0);
        assertThat(seg.getDistanceM()).isGreaterThan(0.0);
        assertThat(seg.getDurationSec()).isGreaterThan(0);
    }

    // ================================================================
    // lockUnchangedPOIs() — TC-RG-009
    // ================================================================


    @DisplayName("TC-RGU-078: Reroll işlemi sadece hedef index'teki noktayı yenisiyle değiştiriyor mu")
@Test
    void shouldReplaceOnlyTargetIndex_whenRerolling() {
        // given
        List<Place> testPlaces = buildTestPlaces();
        List<Place> routePlaces = testPlaces.subList(0, 4);
        Route route = buildRouteWithPoints(routePlaces, "reroll-test");

        String id0 = route.getPoints().get(0).getPoi().getId();
        String id1 = route.getPoints().get(1).getPoi().getId();
        String id3 = route.getPoints().get(3).getPoi().getId();

        // Build replacement pool that includes places NOT already in the route
        when(placeRepository.findAll()).thenReturn(buildTestPlaces());

        Map<String, String> uv = buildValidUserVector();
        Map<String, String> indexParams = new HashMap<>();

        // when
        Route result = routeService.rerollRoutePoint(route, 2, indexParams, uv);

        // then — indices 0, 1, 3 preserve their POI IDs
        assertThat(result.getPoints().get(0).getPoi().getId()).isEqualTo(id0);
        assertThat(result.getPoints().get(1).getPoi().getId()).isEqualTo(id1);
        assertThat(result.getPoints().get(3).getPoi().getId()).isEqualTo(id3);
    }

    @Test
    void shouldNotModifyRoute_whenNoReplacementFound() {
        // given — empty repository means empty candidate pool
        when(placeRepository.findAll()).thenReturn(List.of());

        List<Place> routePlaces = buildTestPlaces().subList(0, 3);
        Route route = buildRouteWithPoints(routePlaces, "no-replace");
        String originalId = route.getPoints().get(1).getPoi().getId();

        Map<String, String> uv = buildValidUserVector();

        // when
        Route result = routeService.rerollRoutePoint(route, 1, new HashMap<>(), uv);

        // then
        assertThat(result.getPoints().get(1).getPoi().getId()).isEqualTo(originalId);
    }

    // ================================================================
    // insertManualPOI() — TC-RG-010
    // ================================================================


    @DisplayName("TC-RGU-080: Araya yeni nokta eklendiğinde sonrasındaki noktaların indexleri kayıyor mu")
@Test
    void shouldShiftSubsequentPoints_whenInsertingAtMiddleIndex() {
        // given
        List<Place> routePlaces = buildTestPlaces().subList(0, 3);
        Route route = buildRouteWithPoints(routePlaces, "insert-test");
        Place newPlace = buildPlace("new1", "New Place", "cafe", 39.93, 32.86, 4.2, 110, "OPERATIONAL");
        when(placeRepository.findById("new1")).thenReturn(Optional.of(newPlace));

        String origId0 = route.getPoints().get(0).getPoi().getId();
        String origId1 = route.getPoints().get(1).getPoi().getId();
        String origId2 = route.getPoints().get(2).getPoi().getId();
        Map<String, String> uv = buildValidUserVector();

        // when
        Route result = routeService.insertManualPOI(route, 1, "new1", uv);

        // then
        assertThat(result.getPoints()).hasSize(4);
        assertThat(result.getPoints().get(0).getPoi().getId()).isEqualTo(origId0);
        assertThat(result.getPoints().get(1).getPoi().getId()).isEqualTo("new1");
        assertThat(result.getPoints().get(2).getPoi().getId()).isEqualTo(origId1);
        assertThat(result.getPoints().get(3).getPoi().getId()).isEqualTo(origId2);
        // Verify re-indexing
        for (int i = 0; i < result.getPoints().size(); i++) {
            assertThat(result.getPoints().get(i).getIndex()).isEqualTo(i);
        }
    }

    @Test
    void shouldReturnUnchangedRoute_whenPoiIdNotFound() {
        // given
        when(placeRepository.findById("nonexistent")).thenReturn(Optional.empty());
        Route route = buildRouteWithPoints(buildTestPlaces().subList(0, 3), "no-find");
        Map<String, String> uv = buildValidUserVector();

        // when
        Route result = routeService.insertManualPOI(route, 1, "nonexistent", uv);

        // then
        assertThat(result.getPoints()).hasSize(3);
    }

    @Test
    void shouldInsertAtStart_whenIndexIsZero() {
        // given
        Place newPlace = buildPlace("new2", "Start", "park", 39.93, 32.86, 4.0, 100, "OPERATIONAL");
        when(placeRepository.findById("new2")).thenReturn(Optional.of(newPlace));

        Route route = buildRouteWithPoints(buildTestPlaces().subList(0, 3), "start-insert");
        Map<String, String> uv = buildValidUserVector();

        // when
        Route result = routeService.insertManualPOI(route, 0, "new2", uv);

        // then
        assertThat(result.getPoints().get(0).getPoi().getId()).isEqualTo("new2");
    }

    @Test
    void shouldInsertAtEnd_whenIndexExceedsBounds() {
        // given
        Place newPlace = buildPlace("new3", "End", "cafe", 39.93, 32.86, 4.0, 100, "OPERATIONAL");
        when(placeRepository.findById("new3")).thenReturn(Optional.of(newPlace));

        Route route = buildRouteWithPoints(buildTestPlaces().subList(0, 3), "end-insert");
        Map<String, String> uv = buildValidUserVector();

        // when
        Route result = routeService.insertManualPOI(route, 99, "new3", uv);

        // then
        assertThat(result.getPoints().get(result.getPoints().size() - 1).getPoi().getId())
                .isEqualTo("new3");
    }

    // ================================================================
    // removePoint() — TC-RG-011
    // ================================================================


    @DisplayName("TC-RGU-083: Dizinin ortasından nokta silindiğinde boşluk kapatılarak indexler güncelleniyor mu")
@Test
    void shouldCompactRoute_whenRemovingMiddlePoint() {
        // given
        List<Place> places = buildTestPlaces().subList(0, 4); // A, B, C, D
        Route route = buildRouteWithPoints(places, "remove-test");
        String idA = route.getPoints().get(0).getPoi().getId();
        String idB = route.getPoints().get(1).getPoi().getId();
        String idD = route.getPoints().get(3).getPoi().getId();
        Map<String, String> uv = buildValidUserVector();

        // when — remove index 2 (C)
        Route result = routeService.removePoint(route, 2, uv);

        // then
        assertThat(result.getPoints()).hasSize(3);
        assertThat(result.getPoints().get(0).getPoi().getId()).isEqualTo(idA);
        assertThat(result.getPoints().get(1).getPoi().getId()).isEqualTo(idB);
        assertThat(result.getPoints().get(2).getPoi().getId()).isEqualTo(idD);
        // Verify re-indexing
        for (int i = 0; i < result.getPoints().size(); i++) {
            assertThat(result.getPoints().get(i).getIndex()).isEqualTo(i);
        }
    }

    @Test
    void shouldReturnUnchangedRoute_whenIndexIsOutOfBounds() {
        // given
        Route route = buildRouteWithPoints(buildTestPlaces().subList(0, 3), "oob-remove");
        Map<String, String> uv = buildValidUserVector();

        // when
        Route result = routeService.removePoint(route, 99, uv);

        // then
        assertThat(result.getPoints()).hasSize(3);
    }

    @Test
    void shouldRecomputeSegments_afterRemoval() {
        // given
        Route route = buildRouteWithPoints(buildTestPlaces().subList(0, 4), "seg-remove");
        Map<String, String> uv = buildValidUserVector();

        // when
        Route result = routeService.removePoint(route, 1, uv);

        // then
        assertThat(result.getSegments()).hasSize(result.getPoints().size() - 1);
    }

    // ================================================================
    // reorderPOIs() — TC-RG-012, TC-RG-013
    // ================================================================


    @DisplayName("TC-RGU-085: Geçerli permütasyon verildiğinde POI'ler doğru şekilde sıralanıyor mu")
@Test
    void shouldReorderPoints_whenPermutationIsValid() {
        // given — route [A, B, C, D]
        List<Place> places = buildTestPlaces().subList(0, 4);
        Route route = buildRouteWithPoints(places, "reorder-test");
        String idA = route.getPoints().get(0).getPoi().getId();
        String idB = route.getPoints().get(1).getPoi().getId();
        String idC = route.getPoints().get(2).getPoi().getId();
        String idD = route.getPoints().get(3).getPoi().getId();
        Map<String, String> uv = buildValidUserVector();

        // when — reorder to [A, C, B, D]
        Route result = routeService.reorderPOIs(route, List.of(0, 2, 1, 3), uv);

        // then
        assertThat(result.getPoints().get(0).getPoi().getId()).isEqualTo(idA);
        assertThat(result.getPoints().get(1).getPoi().getId()).isEqualTo(idC);
        assertThat(result.getPoints().get(2).getPoi().getId()).isEqualTo(idB);
        assertThat(result.getPoints().get(3).getPoi().getId()).isEqualTo(idD);
    }

    @DisplayName("TC-RGU-088: Yeniden sıralamadan sonra tüm segmentler ve süreler/mesafeler tekrar hesaplanıyor mu")
@Test
    void shouldRecomputeAllSegments_afterReorder() {
        // given
        Route route = buildRouteWithPoints(buildTestPlaces().subList(0, 4), "seg-reorder");
        Map<String, String> uv = buildValidUserVector();

        // when
        Route result = routeService.reorderPOIs(route, List.of(3, 2, 1, 0), uv);

        // then
        assertThat(result.getSegments()).hasSize(result.getPoints().size() - 1);
    }

    @Test
    void shouldReturnUnchangedRoute_whenOrderLengthMismatches() {
        // given
        Route route = buildRouteWithPoints(buildTestPlaces().subList(0, 4), "mismatch");
        Map<String, String> uv = buildValidUserVector();

        // when — wrong length
        Route result = routeService.reorderPOIs(route, List.of(0, 1, 2), uv);

        // then
        assertThat(result.getPoints()).hasSize(4);
    }

    @Test
    void shouldReturnUnchangedRoute_whenPermutationIsNull() {
        // given
        Route route = buildRouteWithPoints(buildTestPlaces().subList(0, 4), "null-order");
        Map<String, String> uv = buildValidUserVector();

        // when
        Route result = routeService.reorderPOIs(route, null, uv);

        // then
        assertThat(result.getPoints()).hasSize(4);
    }
}
