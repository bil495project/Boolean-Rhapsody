package com.roadrunner.route.controller;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadrunner.place.entity.Place;
import com.roadrunner.place.repository.PlaceRepository;
import com.roadrunner.route.dto.request.GenerateRoutesRequest;
import com.roadrunner.route.dto.request.InsertWithStateRequest;
import com.roadrunner.route.dto.request.RemoveWithStateRequest;
import com.roadrunner.route.dto.request.RouteAnchorRequest;
import com.roadrunner.route.dto.request.RouteBoundarySelectionRequest;
import com.roadrunner.route.dto.request.RouteCandidateFiltersRequest;
import com.roadrunner.route.dto.request.RouteConstraintsRequest;
import com.roadrunner.route.dto.request.RoutePreferencesRequest;
import com.roadrunner.route.dto.request.ReorderWithStateRequest;
import com.roadrunner.route.dto.request.RerollWithStateRequest;
import com.roadrunner.route.dto.response.RouteResponse;
import com.roadrunner.user.dto.request.LoginRequest;
import com.roadrunner.user.dto.request.RegisterRequest;
import com.roadrunner.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@SuppressWarnings("null")
@Epic("Route Generation")
@Feature("Integration Tests")
@DisplayName("Integration Tests - RouteController")
class RouteControllerIntegrationTest {

    private static final String TEST_EMAIL = "route-test@roadrunner.com";
    private static final String TEST_PASSWORD = "password123";
    private static final String TEST_NAME = "Route Test User";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private UserRepository userRepository;

    private String jwtToken;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();
        placeRepository.deleteAll();
        placeRepository.saveAll(buildTestPlaces());
        jwtToken = obtainToken();
    }

    private List<Place> buildTestPlaces() {
        List<Place> places = new ArrayList<>();
        places.add(place("h1", "Central Hotel", "hotel", 39.9209, 32.8540, 4.4, 450, null));
        places.add(place("h2", "Scenic Hotel", "hotel", 39.9570, 32.9140, 4.9, 720, null));
        places.add(place("r1", "Budget Lokanta", "restaurant", 39.9218, 32.8512, 4.1, 180, "PRICE_LEVEL_INEXPENSIVE"));
        places.add(place("r2", "Luxury Grill", "restaurant", 39.9300, 32.8640, 4.6, 320, "PRICE_LEVEL_EXPENSIVE"));
        places.add(place("m1", "Museum A", "museum", 39.9230, 32.8580, 4.7, 410, null));
        places.add(place("m2", "Historic Hall", "historical_landmark", 39.9150, 32.8420, 4.2, 170, null));
        places.add(place("p1", "Park A", "park", 39.9280, 32.8610, 4.1, 210, null));
        places.add(place("p2", "Garden B", "garden", 39.9100, 32.8350, 3.9, 130, null));
        places.add(place("c1", "Cafe A", "cafe", 39.9240, 32.8550, 4.3, 190, "PRICE_LEVEL_INEXPENSIVE"));
        places.add(place("c2", "Dessert Bar", "bakery", 39.9340, 32.8670, 4.8, 260, "PRICE_LEVEL_MODERATE"));
        places.add(place("l1", "Landmark A", "tourist_attraction", 39.9160, 32.8460, 4.5, 220, null));
        places.add(place("l2", "Arena", "stadium", 39.9440, 32.8770, 4.0, 150, null));
        places.add(place("n1", "Bar A", "bar", 39.9220, 32.8600, 4.0, 180, "PRICE_LEVEL_EXPENSIVE"));
        places.add(place("n2", "Club B", "night_club", 39.9320, 32.8520, 4.2, 240, "PRICE_LEVEL_VERY_EXPENSIVE"));
        places.add(place("d1", "Nature Spot", "nature_preserve", 39.9500, 32.8890, 4.1, 115, null));
        places.add(place("x1", "Insert Cafe", "cafe", 39.9180, 32.8500, 4.4, 160, "PRICE_LEVEL_INEXPENSIVE"));
        return places;
    }

    private Place place(String id, String name, String types, double lat, double lng,
                        double rating, int ratingCount, String priceLevel) {
        return Place.builder()
                .id(id)
                .name(name)
                .types(types)
                .latitude(lat)
                .longitude(lng)
                .ratingScore(rating)
                .ratingCount(ratingCount)
                .priceLevel(priceLevel)
                .businessStatus("OPERATIONAL")
                .build();
    }

    private String obtainToken() throws Exception {
        RegisterRequest register = RegisterRequest.builder()
                .name(TEST_NAME)
                .email(TEST_EMAIL)
                .password(TEST_PASSWORD)
                .build();
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)));

        LoginRequest login = LoginRequest.builder()
                .email(TEST_EMAIL)
                .password(TEST_PASSWORD)
                .build();
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token")
                .asText();
    }

    private Map<String, String> buildValidUserVector() {
        Map<String, String> uv = new HashMap<>();
        uv.put("requestId", "integration-test");
        uv.put("weight_parkVeSeyirNoktalari", "0.4");
        uv.put("weight_geceHayati", "0.2");
        uv.put("weight_restoranToleransi", "0.7");
        uv.put("weight_landmark", "0.3");
        uv.put("weight_dogalAlanlar", "0.2");
        uv.put("weight_tarihiAlanlar", "0.8");
        uv.put("weight_kafeTatli", "0.4");
        uv.put("weight_toplamPoiYogunlugu", "0.5");
        uv.put("weight_sparsity", "0.5");
        uv.put("weight_hotelCenterBias", "0.7");
        uv.put("weight_butceSeviyesi", "0.4");
        return uv;
    }

    private Map<String, String> buildCenterUserVector() {
        Map<String, String> uv = buildValidUserVector();
        return uv;
    }

    private RoutePreferencesRequest buildPreferences() {
        return new RoutePreferencesRequest(
                0.6, 0.5, 0.4, 0.7, 0.8,
                0.3, 0.4, 0.5, 0.6, 0.5);
    }

    private RouteConstraintsRequest constraints(boolean stayAtHotel,
                                                boolean needsBreakfast,
                                                boolean needsLunch,
                                                boolean needsDinner) {
        RouteConstraintsRequest constraints = new RouteConstraintsRequest();
        constraints.setStayAtHotel(stayAtHotel);
        constraints.setNeedsBreakfast(needsBreakfast);
        constraints.setNeedsLunch(needsLunch);
        constraints.setNeedsDinner(needsDinner);
        constraints.setStartAnchor(null);
        constraints.setEndAnchor(null);
        constraints.setPoiSlots(null);
        constraints.setRequestedVisitCount(null);
        return constraints;
    }

    private RouteResponse generateOneRoute() throws Exception {
        GenerateRoutesRequest req = new GenerateRoutesRequest();
        req.setUserVector(buildValidUserVector());
        req.setPreferences(buildPreferences());
        req.setK(1);
        req.setConstraints(constraints(true, true, false, true));

        MvcResult result = mockMvc.perform(post("/api/routes/generate")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        List<RouteResponse> routes = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {});
        return routes.get(0);
    }

    @Test
    @DisplayName("Generate yeni contract ile 200 donup k rota uretiyor")
    void shouldGenerateKRoutes() throws Exception {
        GenerateRoutesRequest req = new GenerateRoutesRequest();
        req.setUserVector(buildValidUserVector());
        req.setPreferences(buildPreferences());
        req.setK(3);
        RouteConstraintsRequest constraints = constraints(true, false, true, true);
        constraints.setStartAnchor(new RouteAnchorRequest("TYPE", null, "HOTEL", null));
        req.setConstraints(constraints);

        mockMvc.perform(post("/api/routes/generate")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("Generate stayAtHotel false ve center ile custom anchorli rota dondurur")
    void shouldGenerateCenterAnchoredRouteWhenHotelStayIsFalse() throws Exception {
        GenerateRoutesRequest req = new GenerateRoutesRequest();
        req.setUserVector(buildCenterUserVector());
        req.setPreferences(buildPreferences());
        req.setK(1);
        req.setConstraints(constraints(false, false, false, false));
        req.setCenterLat(39.9208);
        req.setCenterLng(32.8541);

        mockMvc.perform(post("/api/routes/generate")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].points[0].poiId").isEmpty())
                .andExpect(jsonPath("$[0].points[0].latitude").value(39.9208))
                .andExpect(jsonPath("$[0].points[0].longitude").value(32.8541));
    }

    @Test
    @DisplayName("Generate stayAtHotel false ve center yoksa Kizilay anchori kullanir")
    void shouldFallbackToKizilayAnchorWhenCenterMissing() throws Exception {
        GenerateRoutesRequest req = new GenerateRoutesRequest();
        req.setUserVector(buildValidUserVector());
        req.setPreferences(buildPreferences());
        req.setK(1);
        req.setConstraints(constraints(false, false, false, false));

        mockMvc.perform(post("/api/routes/generate")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].points[0].poiId").isEmpty())
                .andExpect(jsonPath("$[0].points[0].latitude").value(39.9208))
                .andExpect(jsonPath("$[0].points[0].longitude").value(32.8541));
    }

    @Test
    @DisplayName("Generate explicit start=end model ile hotelden place'e rota kurar")
    void shouldGenerateWithExplicitBoundarySelections() throws Exception {
        GenerateRoutesRequest req = new GenerateRoutesRequest();
        req.setUserVector(buildValidUserVector());
        req.setPreferences(buildPreferences());
        req.setK(1);

        RouteConstraintsRequest constraints = constraints(false, true, false, false);
        constraints.setStartPoint(new RouteBoundarySelectionRequest("HOTEL", null, null, null));
        constraints.setEndPoint(new RouteBoundarySelectionRequest("PLACE", "m1", null, null));
        req.setConstraints(constraints);

        MvcResult result = mockMvc.perform(post("/api/routes/generate")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        List<RouteResponse> routes = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {});

        RouteResponse route = routes.get(0);
        assertThat(route.getPoints().get(0).getPoiId()).isNotBlank();
        assertThat(route.getPoints().get(0).getTypes()).contains("hotel");
        assertThat(route.getPoints().get(route.getPoints().size() - 1).getPoiId()).isEqualTo("m1");
    }

    @Test
    @DisplayName("Generate explicit ve legacy boundary alanlari karistiksa 400 doner")
    void shouldRejectMixedExplicitAndLegacyBoundaryFields() throws Exception {
        GenerateRoutesRequest req = new GenerateRoutesRequest();
        req.setUserVector(buildValidUserVector());
        req.setPreferences(buildPreferences());
        req.setK(1);

        RouteConstraintsRequest constraints = constraints(false, false, false, false);
        constraints.setStartWithHotel(true);
        constraints.setStartPoint(new RouteBoundarySelectionRequest("HOTEL", null, null, null));
        req.setConstraints(constraints);

        mockMvc.perform(post("/api/routes/generate")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Generate sonucu ayni hotel ile baslayip bitiyor")
    void shouldReturnHotelAnchoredRoute() throws Exception {
        RouteResponse route = generateOneRoute();

        assertThat(route.getPoints()).hasSizeBetween(3, 12);
        assertThat(route.getPoints().get(0).getPoiId())
                .isEqualTo(route.getPoints().get(route.getPoints().size() - 1).getPoiId());
        assertThat(route.getSegments()).hasSize(route.getPoints().size() - 1);
        assertThat(route.isFeasible()).isTrue();
    }

    @Test
    @DisplayName("Generate k sifirken 400 donuyor")
    void shouldRejectInvalidK() throws Exception {
        GenerateRoutesRequest req = new GenerateRoutesRequest();
        req.setUserVector(buildValidUserVector());
        req.setPreferences(buildPreferences());
        req.setK(0);

        mockMvc.perform(post("/api/routes/generate")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Generate token olmadan 401 donuyor")
    void shouldRequireAuthForGenerate() throws Exception {
        GenerateRoutesRequest req = new GenerateRoutesRequest();
        req.setUserVector(buildValidUserVector());
        req.setPreferences(buildPreferences());
        req.setK(1);

        mockMvc.perform(post("/api/routes/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Reroll hotel anchorlarini da degistirebilir")
    void shouldAllowRerollingHotelAnchor() throws Exception {
        RouteResponse route = generateOneRoute();
        String hotelId = route.getPoints().get(0).getPoiId();

        RerollWithStateRequest req = new RerollWithStateRequest();
        req.setCurrentRoute(route);
        req.setIndex(0);
        req.setIndexParams(new HashMap<>());
        req.setOriginalUserVector(buildValidUserVector());

        MvcResult result = mockMvc.perform(post("/api/routes/reroll")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        RouteResponse updated = objectMapper.readValue(
                result.getResponse().getContentAsString(), RouteResponse.class);

        assertThat(updated.getPoints().get(0).getPoiId()).isNotEqualTo(hotelId);
    }

    @Test
    @DisplayName("Insert ic bolgeye ekleyip hotel anchorlari koruyor")
    void shouldInsertWithoutBreakingAnchors() throws Exception {
        RouteResponse route = generateOneRoute();
        String hotelId = route.getPoints().get(0).getPoiId();

        InsertWithStateRequest req = new InsertWithStateRequest();
        req.setCurrentRoute(route);
        req.setIndex(1);
        req.setPoiId("x1");
        req.setOriginalUserVector(buildValidUserVector());

        MvcResult result = mockMvc.perform(post("/api/routes/insert")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        RouteResponse updated = objectMapper.readValue(
                result.getResponse().getContentAsString(), RouteResponse.class);

        assertThat(updated.getPoints().get(0).getPoiId()).isEqualTo(hotelId);
        assertThat(updated.getPoints().get(updated.getPoints().size() - 1).getPoiId()).isEqualTo(hotelId);
        assertThat(updated.getPoints()).hasSize(route.getPoints().size() + 1);
    }

    @Test
    @DisplayName("Remove hotel anchor noktasini da silebilir")
    void shouldAllowRemovingAnchorPoints() throws Exception {
        RouteResponse route = generateOneRoute();
        String hotelId = route.getPoints().get(0).getPoiId();

        RemoveWithStateRequest req = new RemoveWithStateRequest();
        req.setCurrentRoute(route);
        req.setIndex(0);
        req.setOriginalUserVector(buildValidUserVector());

        MvcResult result = mockMvc.perform(post("/api/routes/remove")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        RouteResponse updated = objectMapper.readValue(
                result.getResponse().getContentAsString(), RouteResponse.class);

        assertThat(updated.getPoints()).hasSize(route.getPoints().size() - 1);
        assertThat(updated.getPoints().get(0).getPoiId()).isNotEqualTo(hotelId);
        assertThat(updated.getSegments()).hasSize(updated.getPoints().size() - 1);
    }

    @Test
    @DisplayName("Reorder tum poi noktalarini yeniden siralayabilir")
    void shouldReorderAllPoiPoints() throws Exception {
        RouteResponse route = generateOneRoute();
        int size = route.getPoints().size();
        String originalSecondPoiId = route.getPoints().get(size - 2).getPoiId();

        List<Integer> fullOrder = new ArrayList<>();
        for (int i = size - 1; i >= 0; i--) {
            fullOrder.add(i);
        }

        ReorderWithStateRequest req = new ReorderWithStateRequest();
        req.setCurrentRoute(route);
        req.setNewOrder(fullOrder);
        req.setOriginalUserVector(buildValidUserVector());

        MvcResult result = mockMvc.perform(post("/api/routes/reorder")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        RouteResponse updated = objectMapper.readValue(
                result.getResponse().getContentAsString(), RouteResponse.class);

        assertThat(updated.getPoints().get(1).getPoiId()).isEqualTo(originalSecondPoiId);
        assertThat(updated.getSegments()).hasSize(updated.getPoints().size() - 1);
    }

    @Test
    @DisplayName("Center anchorli route mutasyonlarda ilk noktayi korur")
    void shouldKeepCenterAnchorOnMutations() throws Exception {
        GenerateRoutesRequest generateReq = new GenerateRoutesRequest();
        generateReq.setUserVector(buildCenterUserVector());
        generateReq.setPreferences(buildPreferences());
        generateReq.setK(1);
        generateReq.setConstraints(constraints(false, false, false, false));
        generateReq.setCenterLat(39.9208);
        generateReq.setCenterLng(32.8541);

        RouteCandidateFiltersRequest filters = new RouteCandidateFiltersRequest(4.5, 2000);
        RouteAnchorRequest endAnchor = new RouteAnchorRequest("TYPE", null, "HOTEL", filters);
        generateReq.getConstraints().setEndAnchor(endAnchor);

        MvcResult generated = mockMvc.perform(post("/api/routes/generate")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(generateReq)))
                .andExpect(status().isOk())
                .andReturn();

        RouteResponse route = objectMapper.readValue(
                generated.getResponse().getContentAsString(),
                new TypeReference<List<RouteResponse>>() {}).get(0);

        RerollWithStateRequest rerollReq = new RerollWithStateRequest();
        rerollReq.setCurrentRoute(route);
        rerollReq.setIndex(0);
        rerollReq.setIndexParams(new HashMap<>());
        rerollReq.setOriginalUserVector(buildCenterUserVector());

        MvcResult rerollResult = mockMvc.perform(post("/api/routes/reroll")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rerollReq)))
                .andExpect(status().isOk())
                .andReturn();

        RouteResponse rerolled = objectMapper.readValue(
                rerollResult.getResponse().getContentAsString(), RouteResponse.class);
        assertThat(rerolled.getPoints().get(0).getPoiId()).isNull();
        assertThat(rerolled.getPoints().get(0).getLatitude()).isEqualTo(39.9208);

        RemoveWithStateRequest removeReq = new RemoveWithStateRequest();
        removeReq.setCurrentRoute(route);
        removeReq.setIndex(0);
        removeReq.setOriginalUserVector(buildCenterUserVector());

        MvcResult removeResult = mockMvc.perform(post("/api/routes/remove")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(removeReq)))
                .andExpect(status().isOk())
                .andReturn();

        RouteResponse removed = objectMapper.readValue(
                removeResult.getResponse().getContentAsString(), RouteResponse.class);
        assertThat(removed.getPoints().get(0).getPoiId()).isNull();
        assertThat(removed.getPoints().get(0).getLatitude()).isEqualTo(39.9208);
    }
}
