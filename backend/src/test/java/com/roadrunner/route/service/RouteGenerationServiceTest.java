package com.roadrunner.route.service;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.roadrunner.place.entity.Place;
import com.roadrunner.place.repository.PlaceRepository;
import com.roadrunner.route.dto.request.GenerateRoutesRequest;
import com.roadrunner.route.dto.request.RouteAnchorRequest;
import com.roadrunner.route.dto.request.RouteCandidateFiltersRequest;
import com.roadrunner.route.dto.request.RouteBoundarySelectionRequest;
import com.roadrunner.route.dto.request.RouteConstraintsRequest;
import com.roadrunner.route.dto.request.RoutePreferencesRequest;
import com.roadrunner.route.dto.request.RoutePoiSlotRequest;
import com.roadrunner.route.entity.Route;
import com.roadrunner.route.entity.RoutePoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Epic("Route Generation")
@Feature("Unit Tests")
@DisplayName("Unit Tests - RouteGenerationService (Weight-Based)")
class RouteGenerationServiceTest {

    private static final String TEST_REQUEST_ID = "test-req-001";
    private static final double ANKARA_LAT = 39.9208;
    private static final double ANKARA_LNG = 32.8541;

    @Mock
    private PlaceRepository placeRepository;

    private PlaceLabelService labelService;
    private RouteScoringService scoringService;
    private RouteGenerationService routeService;
    private RouteRequestInterpreter requestInterpreter;

    @BeforeEach
    void setUp() {
        labelService = new PlaceLabelService(new DefaultPlaceRouteLabelService());
        scoringService = new RouteScoringService(labelService);
        routeService = new RouteGenerationService(placeRepository, scoringService, labelService);
        requestInterpreter = new RouteRequestInterpreter(
                new RoutePreferenceVectorMapper(),
                new RouteConstraintResolver());
    }

    private Place place(String id, String name, String types, double lat, double lng,
                        double rating, int ratingCount) {
        return Place.builder()
                .id(id)
                .name(name)
                .types(types)
                .latitude(lat)
                .longitude(lng)
                .ratingScore(rating)
                .ratingCount(ratingCount)
                .businessStatus("OPERATIONAL")
                .build();
    }

    private List<Place> buildTestPlaces() {
        List<Place> places = new ArrayList<>();

        places.add(place("h1", "Hotel Central", "hotel", 39.9210, 32.8540, 4.5, 500));
        places.add(place("h2", "Hotel Scenic", "hotel", 39.9520, 32.9040, 4.9, 2000));

        places.add(place("r1", "Restaurant Elite", "restaurant", 39.9205, 32.8535, 4.9, 2000));
        places.add(place("r2", "Restaurant Massive", "restaurant", 39.9240, 32.8600, 4.7, 4000));
        places.add(place("r3", "Restaurant Tiny Perfect", "restaurant", 39.9260, 32.8610, 5.0, 150));
        places.add(place("r4", "bar and grill", "bar_and_grill", 39.9290, 32.8620, 4.6, 900));

        places.add(place("t1", "Museum A", "museum", 39.9230, 32.8580, 4.8, 1800));
        places.add(place("t2", "Historic Hall", "historical_landmark", 39.9150, 32.8420, 4.5, 1200));
        places.add(place("t3", "Art Gallery", "art_gallery", 39.9300, 32.8510, 4.3, 600));
        places.add(place("t4", "Archive", "museum", 39.9340, 32.8670, 4.1, 300));

        places.add(place("p1", "Park A", "park", 39.9280, 32.8610, 4.2, 700));
        places.add(place("p2", "Garden B", "garden", 39.9100, 32.8350, 4.0, 450));
        places.add(place("p3", "Scenic Ridge", "park", 39.9440, 32.8770, 4.6, 1400));

        places.add(place("c1", "Cafe A", "cafe", 39.9240, 32.8550, 4.6, 1000));
        places.add(place("c2", "Bakery B", "bakery", 39.9340, 32.8670, 4.5, 1500));
        places.add(place("c3", "Coffee C", "coffee_shop", 39.9170, 32.8480, 4.1, 350));

        places.add(place("l1", "Landmark A", "tourist_attraction", 39.9160, 32.8460, 4.7, 1700));
        places.add(place("l2", "Arena", "stadium", 39.9440, 32.8770, 4.2, 1100));
        places.add(place("l3", "Concert Hall", "concert_hall", 39.9310, 32.8530, 4.4, 800));

        places.add(place("n1", "Bar A", "bar", 39.9220, 32.8600, 4.2, 1300));
        places.add(place("n2", "Night Club B", "night_club", 39.9320, 32.8520, 4.5, 2100));
        places.add(place("n3", "Bar C", "bar", 39.9380, 32.8680, 4.0, 500));

        places.add(place("d1", "Nature Spot", "nature_preserve", 39.9500, 32.8890, 4.6, 1200));
        places.add(place("d2", "Nature Walk", "nature", 39.9600, 32.8980, 4.3, 600));

        return places;
    }

    private List<Place> buildComparableHotelPlaces() {
        List<Place> places = new ArrayList<>();
        places.add(place("h1", "Hotel Alpha", "hotel", 39.9210, 32.8540, 4.7, 1500));
        places.add(place("h2", "Hotel Beta", "hotel", 39.9230, 32.8510, 4.8, 1600));
        places.add(place("h3", "Hotel Gamma", "hotel", 39.9245, 32.8570, 4.6, 1700));

        places.add(place("r1", "Restaurant Elite", "restaurant", 39.9205, 32.8535, 4.9, 2000));
        places.add(place("r2", "Restaurant Massive", "restaurant", 39.9240, 32.8600, 4.7, 4000));
        places.add(place("t1", "Museum A", "museum", 39.9230, 32.8580, 4.8, 1800));
        places.add(place("p1", "Park A", "park", 39.9280, 32.8610, 4.2, 700));
        places.add(place("c1", "Cafe A", "cafe", 39.9240, 32.8550, 4.6, 1000));
        places.add(place("l1", "Landmark A", "tourist_attraction", 39.9160, 32.8460, 4.7, 1700));
        places.add(place("n1", "Bar A", "bar", 39.9220, 32.8600, 4.2, 1300));
        places.add(place("d1", "Nature Spot", "nature_preserve", 39.9500, 32.8890, 4.6, 1200));
        return places;
    }

    private Map<String, String> buildWeightUserVector() {
        Map<String, String> uv = new HashMap<>();
        uv.put("requestId", TEST_REQUEST_ID);
        uv.put("weight_parkVeSeyirNoktalari", "0.5");
        uv.put("weight_geceHayati", "0.3");
        uv.put("weight_restoranToleransi", "0.7");
        uv.put("weight_landmark", "0.4");
        uv.put("weight_dogalAlanlar", "0.2");
        uv.put("weight_tarihiAlanlar", "0.6");
        uv.put("weight_kafeTatli", "0.5");
        uv.put("weight_toplamPoiYogunlugu", "0.5");
        uv.put("weight_sparsity", "0.5");
        uv.put("weight_hotelCenterBias", "0.5");
        uv.put("weight_butceSeviyesi", "0.5");
        return uv;
    }

    private Map<String, String> buildCenterUserVector() {
        Map<String, String> uv = buildWeightUserVector();
        uv.put("centerLat", String.valueOf(ANKARA_LAT));
        uv.put("centerLng", String.valueOf(ANKARA_LNG));
        return uv;
    }

    private Route buildRouteFromService(List<Place> places) {
        when(placeRepository.findAll()).thenReturn(places);
        List<Route> routes = routeService.generateRoutes(buildWeightUserVector(), 1);
        assertThat(routes).isNotEmpty();
        return routes.get(0);
    }

    private GenerateRoutesRequest buildConstrainedRequest() {
        GenerateRoutesRequest req = new GenerateRoutesRequest();
        req.setPreferences(new RoutePreferencesRequest(
                0.6, 0.5, 0.4, 0.7, 0.8,
                0.3, 0.4, 0.5, 0.6, 0.5));
        req.setK(1);
        req.setConstraints(new RouteConstraintsRequest());
        return req;
    }

    @Test
    @DisplayName("Generated route ayni hotel ile baslar ve biter")
    void shouldHaveSameHotelAtStartAndEnd() {
        when(placeRepository.findAll()).thenReturn(buildTestPlaces());

        List<Route> routes = routeService.generateRoutes(buildWeightUserVector(), 3);

        assertThat(routes).isNotEmpty();
        for (Route route : routes) {
            assertThat(route.getPoints().size()).isBetween(3, 12);
            assertThat(route.getPoints().get(0).getPoi().getId())
                    .isEqualTo(route.getPoints().get(route.getPoints().size() - 1).getPoi().getId());
            assertThat(labelService.label(route.getPoints().get(0).getPoi())).isEqualTo(RouteLabel.HOTEL);
        }
    }

    @Test
    @DisplayName("Interior point count toplamdan iki eksiktir ve hotel icermez")
    void shouldKeepInteriorInvariant() {
        when(placeRepository.findAll()).thenReturn(buildTestPlaces());

        List<Route> routes = routeService.generateRoutes(buildWeightUserVector(), 3);
        for (Route route : routes) {
            assertThat(route.getPoints().size() - 2).isEqualTo(route.getPoints().subList(1, route.getPoints().size() - 1).size());
            for (int i = 1; i < route.getPoints().size() - 1; i++) {
                assertThat(labelService.label(route.getPoints().get(i).getPoi())).isNotEqualTo(RouteLabel.HOTEL);
            }
        }
    }

    @Test
    @DisplayName("Category quotas tam olarak interior countu doldurur ve low weight sifir alabilir")
    void shouldComputeQuotasWithZeroForLowWeight() {
        Map<String, String> uv = buildWeightUserVector();
        uv.put("weight_geceHayati", "0.01");
        uv.put("weight_tarihiAlanlar", "1.0");
        uv.put("weight_toplamPoiYogunlugu", "0.2");

        RouteGenerationService.ParsedWeightRequest req = routeService.parseUserVector(uv);
        int totalPointCount = (int) Math.max(3, Math.min(12, Math.round(3 + 9 * req.toplamPoiYogunlugu())));
        int interiorPoiCount = totalPointCount - 2;

        Map<RouteLabel, Integer> quotas = routeService.computeCategoryQuotas(req, interiorPoiCount, 1.35);

        assertThat(quotas.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(interiorPoiCount);
        assertThat(quotas.get(RouteLabel.GECE_HAYATI)).isEqualTo(0);
    }

    @Test
    @DisplayName("History-heavy request daha fazla tarihi POI uretir")
    void shouldProduceMoreHistoricPoisWhenHistoryWeightIsHigh() {
        when(placeRepository.findAll()).thenReturn(buildTestPlaces());

        Map<String, String> historyHeavy = buildWeightUserVector();
        historyHeavy.put("weight_tarihiAlanlar", "1.0");
        historyHeavy.put("weight_restoranToleransi", "0.1");
        historyHeavy.put("weight_parkVeSeyirNoktalari", "0.1");
        historyHeavy.put("weight_kafeTatli", "0.1");
        historyHeavy.put("weight_landmark", "0.1");
        historyHeavy.put("weight_geceHayati", "0.1");
        historyHeavy.put("weight_dogalAlanlar", "0.1");

        Map<String, String> historyLight = buildWeightUserVector();
        historyLight.put("weight_tarihiAlanlar", "0.1");
        historyLight.put("weight_restoranToleransi", "1.0");

        long heavyHistoric = routeService.generateRoutes(historyHeavy, 1).get(0).getPoints().stream()
                .filter(point -> labelService.label(point.getPoi()) == RouteLabel.TARIHI_ALANLAR)
                .count();

        when(placeRepository.findAll()).thenReturn(buildTestPlaces());
        long lightHistoric = routeService.generateRoutes(historyLight, 1).get(0).getPoints().stream()
                .filter(point -> labelService.label(point.getPoi()) == RouteLabel.TARIHI_ALANLAR)
                .count();

        assertThat(heavyHistoric).isGreaterThan(lightHistoric);
    }

    @Test
    @DisplayName("Restaurant label icinde yuksek quality confidence one cikiyor")
    void shouldPreferHighQualityRestaurant() {
        when(placeRepository.findAll()).thenReturn(buildTestPlaces());

        Map<String, String> restaurantHeavy = buildWeightUserVector();
        restaurantHeavy.put("weight_restoranToleransi", "1.0");
        restaurantHeavy.put("weight_tarihiAlanlar", "0.0");
        restaurantHeavy.put("weight_landmark", "0.0");
        restaurantHeavy.put("weight_parkVeSeyirNoktalari", "0.0");
        restaurantHeavy.put("weight_kafeTatli", "0.0");
        restaurantHeavy.put("weight_geceHayati", "0.0");
        restaurantHeavy.put("weight_dogalAlanlar", "0.0");
        restaurantHeavy.put("weight_toplamPoiYogunlugu", "0.1");

        Route route = routeService.generateRoutes(restaurantHeavy, 1).get(0);
        List<String> restaurantIds = route.getPoints().stream()
                .map(point -> point.getPoi().getId())
                .filter(id -> id.startsWith("r"))
                .toList();

        assertThat(restaurantIds).contains("r1", "r2");
        assertThat(restaurantIds).doesNotContain("r3");
    }

    @Test
    @DisplayName("Alternatif rotalar ayni interior id setini tekrar etmez")
    void shouldGenerateVisiblyDifferentAlternativeRoutes() {
        when(placeRepository.findAll()).thenReturn(buildTestPlaces());

        List<Route> routes = routeService.generateRoutes(buildWeightUserVector(), 3);

        assertThat(routes).hasSize(3);
        Set<List<String>> signatures = new HashSet<>();
        for (Route route : routes) {
            List<String> interiorIds = route.getPoints().subList(1, route.getPoints().size() - 1).stream()
                    .map(point -> point.getPoi().getId())
                    .toList();
            signatures.add(interiorIds);
        }
        assertThat(signatures).hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("Ayni constrained istek tekrarlandiginda rota tek imzaya kilitlenmez")
    void shouldDiversifyRepeatedConstrainedGenerations() {
        when(placeRepository.findAll()).thenReturn(buildTestPlaces());

        GenerateRoutesRequest req = buildConstrainedRequest();
        req.setK(1);
        RouteConstraintsRequest constraints = req.getConstraints();
        constraints.setStartPoint(new RouteBoundarySelectionRequest("HOTEL", null, null, null));
        constraints.setEndPoint(new RouteBoundarySelectionRequest("HOTEL", null, null, null));

        ResolvedRouteGenerationRequest resolved = requestInterpreter.interpret(req);

        Set<List<String>> signatures = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            Route route = routeService.generateRoutes(resolved, 1).get(0);
            signatures.add(route.getPoints().stream()
                    .map(point -> point.getPoi() != null ? point.getPoi().getId() : point.getAnchorName())
                    .toList());
        }

        assertThat(signatures).hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("Benzer kaliteli hotel adaylarinda alternatif rotalar farkli hotellere yayilabilir")
    void shouldDiversifyHotelsAcrossAlternativeRoutes() {
        when(placeRepository.findAll()).thenReturn(buildComparableHotelPlaces());

        List<Route> routes = routeService.generateRoutes(buildWeightUserVector(), 3);

        Set<String> hotelIds = new HashSet<>();
        for (Route route : routes) {
            hotelIds.add(route.getPoints().get(0).getPoi().getId());
        }
        assertThat(hotelIds).hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("Ilk rota dominant categoryye daha siki baglidir")
    void shouldMakeFirstRouteMoreDominantThanLaterRoutes() {
        when(placeRepository.findAll()).thenReturn(buildTestPlaces());

        Map<String, String> historyHeavy = buildWeightUserVector();
        historyHeavy.put("weight_tarihiAlanlar", "1.0");
        historyHeavy.put("weight_restoranToleransi", "0.05");
        historyHeavy.put("weight_parkVeSeyirNoktalari", "0.05");
        historyHeavy.put("weight_kafeTatli", "0.05");
        historyHeavy.put("weight_landmark", "0.05");
        historyHeavy.put("weight_geceHayati", "0.05");
        historyHeavy.put("weight_dogalAlanlar", "0.05");
        historyHeavy.put("weight_toplamPoiYogunlugu", "0.7");

        List<Route> routes = routeService.generateRoutes(historyHeavy, 3);

        long route0Historic = routes.get(0).getPoints().stream()
                .filter(point -> labelService.label(point.getPoi()) == RouteLabel.TARIHI_ALANLAR)
                .count();
        long route2Historic = routes.get(2).getPoints().stream()
                .filter(point -> labelService.label(point.getPoi()) == RouteLabel.TARIHI_ALANLAR)
                .count();

        assertThat(route0Historic).isGreaterThanOrEqualTo(route2Historic);
    }

    @Test
    @DisplayName("Reroll hotel anchorlarini degistirebilir")
    void shouldAllowRerollingHotelAnchors() {
        List<Place> places = buildTestPlaces();
        when(placeRepository.findAll()).thenReturn(places);

        Route startRoute = buildRouteFromService(places);
        String hotelStartId = startRoute.getPoints().get(0).getPoi().getId();
        Route afterStartReroll = routeService.rerollRoutePoint(startRoute, 0, new HashMap<>(), buildWeightUserVector());

        when(placeRepository.findAll()).thenReturn(places);
        Route endRoute = buildRouteFromService(places);
        String hotelEndId = endRoute.getPoints().get(endRoute.getPoints().size() - 1).getPoi().getId();
        Route afterEndReroll = routeService.rerollRoutePoint(
                endRoute, endRoute.getPoints().size() - 1, new HashMap<>(), buildWeightUserVector());

        assertThat(afterStartReroll.getPoints().get(0).getPoi().getId()).isNotEqualTo(hotelStartId);
        assertThat(afterEndReroll.getPoints().get(afterEndReroll.getPoints().size() - 1).getPoi().getId())
                .isNotEqualTo(hotelEndId);
    }

    @Test
    @DisplayName("Insert remove reorder hotel anchorlarini korur")
    void shouldKeepHotelAnchorsOnMutations() {
        List<Place> places = buildTestPlaces();
        when(placeRepository.findAll()).thenReturn(places);

        Route route = buildRouteFromService(places);
        String hotelId = route.getPoints().get(0).getPoi().getId();

        Place insertPlace = place("ins1", "Insert Cafe", "cafe", 39.93, 32.86, 4.4, 650);
        when(placeRepository.findById("ins1")).thenReturn(java.util.Optional.of(insertPlace));

        Route inserted = routeService.insertManualPOI(route, 1, "ins1", buildWeightUserVector());
        assertThat(inserted.getPoints().get(0).getPoi().getId()).isEqualTo(hotelId);
        assertThat(inserted.getPoints().get(inserted.getPoints().size() - 1).getPoi().getId()).isEqualTo(hotelId);

        Route removed = routeService.removePoint(inserted, 1, buildWeightUserVector());
        assertThat(removed.getPoints().get(0).getPoi().getId()).isEqualTo(hotelId);
        assertThat(removed.getPoints().get(removed.getPoints().size() - 1).getPoi().getId()).isEqualTo(hotelId);

        int interiorSize = removed.getPoints().size() - 2;
        if (interiorSize >= 2) {
            List<Integer> reverse = new ArrayList<>();
            for (int i = interiorSize - 1; i >= 0; i--) {
                reverse.add(i);
            }
            Route reordered = routeService.reorderPOIs(removed, reverse, buildWeightUserVector());
            assertThat(reordered.getPoints().get(0).getPoi().getId()).isEqualTo(hotelId);
            assertThat(reordered.getPoints().get(reordered.getPoints().size() - 1).getPoi().getId()).isEqualTo(hotelId);
        }
    }

    @Test
    @DisplayName("Walking profile daha merkezi hotel secer")
    void shouldSelectMoreCentralHotelWhenWalkingProfile() {
        List<Place> places = buildTestPlaces();
        when(placeRepository.findAll()).thenReturn(places);

        Map<String, String> walking = buildWeightUserVector();
        walking.put("weight_hotelCenterBias", "0.9");

        Map<String, String> driving = buildWeightUserVector();
        driving.put("weight_hotelCenterBias", "0.1");

        Place walkingHotel = routeService.generateRoutes(walking, 1).get(0).getPoints().get(0).getPoi();
        when(placeRepository.findAll()).thenReturn(places);
        Place drivingHotel = routeService.generateRoutes(driving, 1).get(0).getPoints().get(0).getPoi();

        double walkingDist = GeoUtils.haversineKm(ANKARA_LAT, ANKARA_LNG, walkingHotel.getLatitude(), walkingHotel.getLongitude());
        double drivingDist = GeoUtils.haversineKm(ANKARA_LAT, ANKARA_LNG, drivingHotel.getLatitude(), drivingHotel.getLongitude());

        assertThat(walkingDist).isLessThanOrEqualTo(drivingDist);
    }

    @Test
    @DisplayName("Segments ve totals her zaman yeniden hesaplanir")
    void shouldRecomputeSegmentsAndTotals() {
        when(placeRepository.findAll()).thenReturn(buildTestPlaces());

        Route route = routeService.generateRoutes(buildWeightUserVector(), 1).get(0);

        assertThat(route.getSegments()).hasSize(route.getPoints().size() - 1);
        assertThat(route.getTotalDurationSec()).isGreaterThan(0);
        assertThat(route.getTotalDistanceM()).isGreaterThan(0.0);
        assertThat(route.isFeasible()).isTrue();
    }

    @Test
    @DisplayName("Center-start route custom anchor ile baslar ve son POI ile biter")
    void shouldStartFromCenterAnchorWhenStayAtHotelIsFalse() {
        when(placeRepository.findAll()).thenReturn(buildTestPlaces());

        Route route = routeService.generateRoutes(buildCenterUserVector(), false, 1).get(0);

        assertThat(route.getPoints().get(0).isCustomAnchor()).isTrue();
        assertThat(route.getPoints().get(0).getPoi()).isNull();
        assertThat(route.getPoints().get(0).getAnchorLatitude()).isEqualTo(ANKARA_LAT);
        assertThat(route.getPoints().get(0).getAnchorLongitude()).isEqualTo(ANKARA_LNG);
        assertThat(route.getPoints().get(route.getPoints().size() - 1).getPoi()).isNotNull();
        assertThat(route.getPoints()).hasSizeBetween(4, 13);
        assertThat(route.isFeasible()).isTrue();
    }

    @Test
    @DisplayName("Center verilmediginde ve otel secilmediginde Kizilaydan baslar")
    void shouldFallbackToKizilayWhenCenterMissingAndStayAtHotelIsFalse() {
        when(placeRepository.findAll()).thenReturn(buildTestPlaces());

        Route route = routeService.generateRoutes(buildWeightUserVector(), false, 1).get(0);

        assertThat(route.getPoints().get(0).isCustomAnchor()).isTrue();
        assertThat(route.getPoints().get(0).getPoi()).isNull();
        assertThat(route.getPoints().get(0).getAnchorLatitude()).isEqualTo(ANKARA_LAT);
        assertThat(route.getPoints().get(0).getAnchorLongitude()).isEqualTo(ANKARA_LNG);
        assertThat(route.isFeasible()).isTrue();
    }

    @Test
    @DisplayName("Center-start total point count base POI count artı bir olur")
    void shouldAddCenterOnTopOfExistingPointCountRule() {
        Map<String, String> uv = buildCenterUserVector();
        RouteGenerationService.ParsedWeightRequest req = routeService.parseUserVector(uv);
        int basePointCount = (int) Math.max(3, Math.min(12, Math.round(3 + 9 * req.toplamPoiYogunlugu())));

        when(placeRepository.findAll()).thenReturn(buildTestPlaces());
        Route route = routeService.generateRoutes(uv, false, 1).get(0);

        assertThat(route.getPoints()).hasSize(basePointCount + 1);
    }

    @Test
    @DisplayName("Center-start reroll insert remove reorder index sifir anchorini korur")
    void shouldKeepCenterAnchorOnMutations() {
        List<Place> places = buildTestPlaces();
        when(placeRepository.findAll()).thenReturn(places);

        Route route = routeService.generateRoutes(buildCenterUserVector(), false, 1).get(0);
        assertThat(route.getPoints().get(0).isCustomAnchor()).isTrue();

        Route afterStartReroll = routeService.rerollRoutePoint(route, 0, new HashMap<>(), buildCenterUserVector());
        assertThat(afterStartReroll.getPoints().get(0).isCustomAnchor()).isTrue();

        Place insertPlace = place("ins2", "Insert Cafe Center", "cafe", 39.9195, 32.8560, 4.5, 900);
        when(placeRepository.findById("ins2")).thenReturn(java.util.Optional.of(insertPlace));

        Route inserted = routeService.insertManualPOI(route, route.getPoints().size(), "ins2", buildCenterUserVector());
        assertThat(inserted.getPoints().get(0).isCustomAnchor()).isTrue();

        Route removed = routeService.removePoint(inserted, 0, buildCenterUserVector());
        assertThat(removed.getPoints().get(0).isCustomAnchor()).isTrue();

        int interiorSize = inserted.getPoints().size() - 1;
        if (interiorSize >= 2) {
            List<Integer> reverse = new ArrayList<>();
            reverse.add(interiorSize - 1);
            for (int i = interiorSize - 2; i >= 0; i--) {
                reverse.add(i);
            }
            Route reordered = routeService.reorderPOIs(inserted, reverse, buildCenterUserVector());
            assertThat(reordered.getPoints().get(0).isCustomAnchor()).isTrue();
        }
    }

    @Test
    @DisplayName("StayAtHotel true iken center tamamen ignore edilir")
    void shouldIgnoreCenterWhenStayAtHotelIsTrue() {
        when(placeRepository.findAll()).thenReturn(buildTestPlaces());

        Route route = routeService.generateRoutes(buildCenterUserVector(), true, 1).get(0);

        assertThat(route.getPoints().get(0).isCustomAnchor()).isFalse();
        assertThat(route.getPoints().get(0).getPoi().getId())
                .isEqualTo(route.getPoints().get(route.getPoints().size() - 1).getPoi().getId());
    }

    @Test
    @DisplayName("Constrained hotel loop meal duraklarini poi slotlarinin ustune ekleyebilir")
    void shouldAddMealsBeyondConfiguredPoiSlots() {
        when(placeRepository.findAll()).thenReturn(buildTestPlaces());
        when(placeRepository.findById("c1")).thenReturn(
                java.util.Optional.of(buildTestPlaces().stream()
                        .filter(place -> "c1".equals(place.getId()))
                        .findFirst()
                        .orElseThrow()));

        GenerateRoutesRequest req = buildConstrainedRequest();
        RouteConstraintsRequest constraints = req.getConstraints();
        constraints.setStartWithHotel(true);
        constraints.setEndWithHotel(true);
        constraints.setNeedsBreakfast(true);
        constraints.setNeedsLunch(true);
        constraints.setNeedsDinner(true);
        List<RoutePoiSlotRequest> slots = new ArrayList<>();
        slots.add(null);
        slots.add(null);
        slots.add(new RoutePoiSlotRequest("PLACE", "c1", null, null));
        slots.add(new RoutePoiSlotRequest("TYPE", null, "RESTAURANT", null));
        constraints.setPoiSlots(slots);

        ResolvedRouteGenerationRequest resolved = requestInterpreter.interpret(req);
        Route route = routeService.generateRoutes(resolved, 1).get(0);

        assertThat(route.getPoints().get(0).getPoi().getId())
                .isEqualTo(route.getPoints().get(route.getPoints().size() - 1).getPoi().getId());
        assertThat(route.getPoints()).hasSize(7);
        assertThat(route.getPoints().subList(1, route.getPoints().size() - 1)).hasSize(5);
        assertThat(route.getPoints().stream()
                .filter(point -> point.getProtectionReason() != null && point.getProtectionReason().contains("meal:dinner"))
                .count()).isEqualTo(1);
        assertThat(route.getPoints().stream()
                .filter(point -> point.getProtectionReason() != null && point.getProtectionReason().contains("slot"))
                .count()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Meal talepleri hard poi slotlari kilitlediginde ekstra durak olarak eklenir")
    void shouldAddMealStopsWhenPoiSlotsAreFullyReserved() {
        when(placeRepository.findAll()).thenReturn(buildTestPlaces());

        GenerateRoutesRequest req = buildConstrainedRequest();
        RouteConstraintsRequest constraints = req.getConstraints();
        constraints.setStartWithHotel(true);
        constraints.setEndWithHotel(true);
        constraints.setNeedsBreakfast(true);
        constraints.setNeedsLunch(true);
        constraints.setNeedsDinner(true);
        constraints.setPoiSlots(List.of(
                new RoutePoiSlotRequest("TYPE", null, "PARK", null),
                new RoutePoiSlotRequest("TYPE", null, "PARK", null),
                new RoutePoiSlotRequest("TYPE", null, "PARK", null)));

        ResolvedRouteGenerationRequest resolved = requestInterpreter.interpret(req);
        Route route = routeService.generateRoutes(resolved, 1).get(0);

        List<RoutePoint> interior = route.getPoints().subList(1, route.getPoints().size() - 1);
        assertThat(interior).hasSizeGreaterThan(3);
        assertThat(interior.stream()
                .filter(point -> point.getProtectionReason() != null && point.getProtectionReason().contains("meal:"))
                .count()).isGreaterThanOrEqualTo(3);
        assertThat(interior.stream()
                .map(RoutePoint::getPoi)
                .map(labelService::label)
                .filter(label -> label == RouteLabel.PARK_VE_SEYIR_NOKTALARI)
                .count()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("Anchor boole false ise gelen poi anchor ignore edilir")
    void shouldIgnoreAnchorObjectWhenBooleanIsFalse() {
        GenerateRoutesRequest req = buildConstrainedRequest();
        RouteConstraintsRequest constraints = req.getConstraints();
        constraints.setStartWithPoi(false);
        constraints.setEndWithHotel(true);
        constraints.setStartAnchor(new RouteAnchorRequest("PLACE", "l1", null, null));

        ResolvedRouteGenerationRequest resolved = requestInterpreter.interpret(req);

        assertThat(resolved.constraintSpec().startBoundary().kind())
                .isEqualTo(RouteConstraintSpec.BoundaryKind.NONE);
        assertThat(resolved.constraintSpec().endBoundary().kind())
                .isEqualTo(RouteConstraintSpec.BoundaryKind.HOTEL);
    }

    @Test
    @DisplayName("Null poi slot sayisi generated stop sayisini belirler")
    void shouldDeriveGeneratedStopsFromNullPoiSlots() {
        List<Place> places = buildTestPlaces();
        when(placeRepository.findAll()).thenReturn(places);
        when(placeRepository.findById("l1")).thenReturn(
                java.util.Optional.of(places.stream()
                        .filter(place -> "l1".equals(place.getId()))
                        .findFirst()
                        .orElseThrow()));

        GenerateRoutesRequest req = buildConstrainedRequest();
        RouteConstraintsRequest constraints = req.getConstraints();
        constraints.setStartWithHotel(true);
        constraints.setEndWithHotel(true);
        List<RoutePoiSlotRequest> slots = new ArrayList<>();
        slots.add(null);
        slots.add(null);
        slots.add(null);
        slots.add(new RoutePoiSlotRequest("TYPE", null, "KAFE", null));
        slots.add(new RoutePoiSlotRequest("TYPE", null, "RESTAURANT", new RouteCandidateFiltersRequest(4.5, 1000)));
        slots.add(new RoutePoiSlotRequest("PLACE", "l1", null, null));
        constraints.setPoiSlots(slots);

        ResolvedRouteGenerationRequest resolved = requestInterpreter.interpret(req);
        Route route = routeService.generateRoutes(resolved, 1).get(0);

        assertThat(route.getPoints()).hasSize(8);
        assertThat(route.getPoints().subList(1, route.getPoints().size() - 1)).hasSize(6);
    }

    @Test
    @DisplayName("Poi slot sirasi verildigi gibi korunur")
    void shouldPreservePoiSlotOrder() {
        List<Place> places = buildTestPlaces();
        when(placeRepository.findAll()).thenReturn(places);
        when(placeRepository.findById("l1")).thenReturn(
                java.util.Optional.of(places.stream()
                        .filter(place -> "l1".equals(place.getId()))
                        .findFirst()
                        .orElseThrow()));
        when(placeRepository.findById("c1")).thenReturn(
                java.util.Optional.of(places.stream()
                        .filter(place -> "c1".equals(place.getId()))
                        .findFirst()
                        .orElseThrow()));

        GenerateRoutesRequest req = buildConstrainedRequest();
        RouteConstraintsRequest constraints = req.getConstraints();
        constraints.setStartWithHotel(true);
        constraints.setEndWithHotel(true);
        List<RoutePoiSlotRequest> slots = new ArrayList<>();
        slots.add(new RoutePoiSlotRequest("PLACE", "l1", null, null));
        slots.add(new RoutePoiSlotRequest("PLACE", "c1", null, null));
        slots.add(new RoutePoiSlotRequest("TYPE", null, "PARK", null));
        slots.add(null);
        constraints.setPoiSlots(slots);

        ResolvedRouteGenerationRequest resolved = requestInterpreter.interpret(req);
        Route route = routeService.generateRoutes(resolved, 1).get(0);

        List<RoutePoint> interior = route.getPoints().subList(1, route.getPoints().size() - 1);
        assertThat(interior.get(0).getPoi().getId()).isEqualTo("l1");
        assertThat(interior.get(1).getPoi().getId()).isEqualTo("c1");
        assertThat(labelService.label(interior.get(2).getPoi())).isEqualTo(RouteLabel.PARK_VE_SEYIR_NOKTALARI);
    }

    @Test
    @DisplayName("Saglanamayan hard type slot hata verir")
    void shouldFailWhenHardTypeSlotCannotBeSatisfied() {
        when(placeRepository.findAll()).thenReturn(buildTestPlaces());

        GenerateRoutesRequest req = buildConstrainedRequest();
        RouteConstraintsRequest constraints = req.getConstraints();
        constraints.setStartWithHotel(true);
        constraints.setEndWithHotel(true);
        constraints.setPoiSlots(List.of(
                new RoutePoiSlotRequest("TYPE", null, "RESTAURANT", new RouteCandidateFiltersRequest(5.0, 10000))));

        ResolvedRouteGenerationRequest resolved = requestInterpreter.interpret(req);

        assertThatThrownBy(() -> routeService.generateRoutes(resolved, 1))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("TYPE slot");
    }

    @Test
    @DisplayName("Bos poi slot objesi generated stop placeholderi olarak kabul edilir")
    void shouldTreatEmptyPoiSlotObjectAsGeneratedStop() {
        List<Place> places = buildTestPlaces();
        when(placeRepository.findAll()).thenReturn(places);
        when(placeRepository.findById("l1")).thenReturn(
                java.util.Optional.of(places.stream()
                        .filter(place -> "l1".equals(place.getId()))
                        .findFirst()
                        .orElseThrow()));

        GenerateRoutesRequest req = buildConstrainedRequest();
        RouteConstraintsRequest constraints = req.getConstraints();
        constraints.setStartWithHotel(true);
        constraints.setEndWithHotel(true);
        constraints.setPoiSlots(List.of(
                new RoutePoiSlotRequest(),
                new RoutePoiSlotRequest("PLACE", "l1", null, null)));

        ResolvedRouteGenerationRequest resolved = requestInterpreter.interpret(req);
        Route route = routeService.generateRoutes(resolved, 1).get(0);

        assertThat(route.getPoints()).hasSize(4);
        assertThat(route.getPoints().subList(1, route.getPoints().size() - 1)).hasSize(2);
    }

    @Test
    @DisplayName("Legacy hotel anchor placeId ile belirli hoteli sabitler")
    void shouldPinSpecificHotelWithLegacyHotelAnchor() {
        List<Place> places = buildTestPlaces();
        when(placeRepository.findAll()).thenReturn(places);
        when(placeRepository.findById("h2")).thenReturn(
                java.util.Optional.of(places.stream()
                        .filter(place -> "h2".equals(place.getId()))
                        .findFirst()
                        .orElseThrow()));

        GenerateRoutesRequest req = buildConstrainedRequest();
        RouteConstraintsRequest constraints = req.getConstraints();
        constraints.setStartWithHotel(true);
        constraints.setEndWithHotel(true);
        constraints.setStartAnchor(new RouteAnchorRequest("PLACE", "h2", null, null));

        ResolvedRouteGenerationRequest resolved = requestInterpreter.interpret(req);
        Route route = routeService.generateRoutes(resolved, 1).get(0);

        assertThat(route.getPoints().get(0).getPoi().getId()).isEqualTo("h2");
        assertThat(route.getPoints().get(route.getPoints().size() - 1).getPoi().getId()).isEqualTo("h2");
    }

    @Test
    @DisplayName("Legacy start anchor place ile rota belirli POI'dan baslar")
    void shouldStartRouteFromLegacyPlaceAnchor() {
        List<Place> places = buildTestPlaces();
        when(placeRepository.findAll()).thenReturn(places);
        when(placeRepository.findById("l1")).thenReturn(
                java.util.Optional.of(places.stream()
                        .filter(place -> "l1".equals(place.getId()))
                        .findFirst()
                        .orElseThrow()));

        GenerateRoutesRequest req = buildConstrainedRequest();
        RouteConstraintsRequest constraints = req.getConstraints();
        constraints.setStartWithPoi(true);
        constraints.setEndWithHotel(true);
        constraints.setStartAnchor(new RouteAnchorRequest("PLACE", "l1", null, null));

        ResolvedRouteGenerationRequest resolved = requestInterpreter.interpret(req);
        Route route = routeService.generateRoutes(resolved, 1).get(0);

        assertThat(route.getPoints().get(0).getPoi().getId()).isEqualTo("l1");
        assertThat(labelService.label(route.getPoints().get(route.getPoints().size() - 1).getPoi()))
                .isEqualTo(RouteLabel.HOTEL);
    }
}
