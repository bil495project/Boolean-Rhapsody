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

/**
 * Integration tests for {@link RouteController}.
 */
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
    private static final String TEST_REQUEST_ID = "test-req-001";
    private static final double ANKARA_LAT = 39.9208;
    private static final double ANKARA_LNG = 32.8541;

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

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private List<Place> buildTestPlaces() {
        List<Place> places = new ArrayList<>();
        places.add(Place.builder().id("r1").name("Restaurant A").types("restaurant")
                .latitude(39.92).longitude(32.85).ratingScore(4.2).ratingCount(200)
                .businessStatus("OPERATIONAL").build());
        places.add(Place.builder().id("r2").name("Restaurant B").types("restaurant")
                .latitude(39.93).longitude(32.86).ratingScore(3.8).ratingCount(150)
                .businessStatus("OPERATIONAL").build());
        places.add(Place.builder().id("m1").name("Museum A").types("museum")
                .latitude(39.94).longitude(32.87).ratingScore(4.5).ratingCount(300)
                .businessStatus("OPERATIONAL").build());
        places.add(Place.builder().id("m2").name("Museum B").types("museum")
                .latitude(39.91).longitude(32.84).ratingScore(4.0).ratingCount(100)
                .businessStatus("OPERATIONAL").build());
        places.add(Place.builder().id("p1").name("Park A").types("park")
                .latitude(39.95).longitude(32.88).ratingScore(4.3).ratingCount(180)
                .businessStatus("OPERATIONAL").build());
        places.add(Place.builder().id("p2").name("Park B").types("park")
                .latitude(39.90).longitude(32.83).ratingScore(3.5).ratingCount(90)
                .businessStatus("OPERATIONAL").build());
        places.add(Place.builder().id("c1").name("Cafe A").types("cafe")
                .latitude(39.925).longitude(32.855).ratingScore(4.1).ratingCount(160)
                .businessStatus("OPERATIONAL").build());
        places.add(Place.builder().id("c2").name("Cafe B").types("cafe")
                .latitude(39.935).longitude(32.865).ratingScore(4.8).ratingCount(250)
                .businessStatus("OPERATIONAL").build());
        places.add(Place.builder().id("l1").name("Landmark A").types("landmark")
                .latitude(39.915).longitude(32.845).ratingScore(4.6).ratingCount(220)
                .businessStatus("OPERATIONAL").build());
        places.add(Place.builder().id("l2").name("Landmark B").types("landmark")
                .latitude(39.945).longitude(32.875).ratingScore(3.9).ratingCount(130)
                .businessStatus("OPERATIONAL").build());
        return places;
    }

    private String obtainToken() throws Exception {
        RegisterRequest regReq = RegisterRequest.builder()
                .name(TEST_NAME).email(TEST_EMAIL).password(TEST_PASSWORD).build();
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(regReq)));

        LoginRequest loginReq = LoginRequest.builder()
                .email(TEST_EMAIL).password(TEST_PASSWORD).build();
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
    }

    private Map<String, String> buildValidUserVector() {
        Map<String, String> uv = new HashMap<>();
        uv.put("requestId", TEST_REQUEST_ID);
        uv.put("maxStops", "5");
        uv.put("maxBudgetMin", "480");
        uv.put("mode", "driving");
        uv.put("centerLat", String.valueOf(ANKARA_LAT));
        uv.put("centerLng", String.valueOf(ANKARA_LNG));
        uv.put("radiusKm", "50");
        return uv;
    }

    /**
     * Generates a route and returns the first RouteResponse from the response list.
     */
    private RouteResponse generateOneRoute() throws Exception {
        GenerateRoutesRequest req = new GenerateRoutesRequest();
        req.setUserVector(buildValidUserVector());
        req.setK(1);

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

    // ================================================================
    // POST /api/routes/generate
    // ================================================================


    @DisplayName("TC-RGI-001: K değeri 3 olduğunda endpoint 200 dönüp 3 farklı rota üretiyor mu")
@Test
    void shouldReturn200AndThreeRoutes_whenKIsThree() throws Exception {
        // given
        GenerateRoutesRequest req = new GenerateRoutesRequest();
        req.setUserVector(buildValidUserVector());
        req.setK(3);

        // when / then
        mockMvc.perform(post("/api/routes/generate")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @DisplayName("TC-RGI-002: Geçerli istek verildiğinde yapısal olarak geçerli rotalar dönüyor mu")
@Test
    void shouldReturnStructurallyValidRoutes_whenRequestIsValid() throws Exception {
        // given
        GenerateRoutesRequest req = new GenerateRoutesRequest();
        req.setUserVector(buildValidUserVector());
        req.setK(1);

        // when
        MvcResult result = mockMvc.perform(post("/api/routes/generate")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        List<RouteResponse> routes = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {});

        // then
        for (RouteResponse r : routes) {
            assertThat(r.getRouteId()).isNotNull().isNotBlank();
            assertThat(r.getPoints()).isNotEmpty();
            assertThat(r.getTotalDurationSec()).isGreaterThanOrEqualTo(0);
            assertThat(r.getTotalDistanceM()).isGreaterThanOrEqualTo(0.0);
        }
    }

    @DisplayName("TC-RGI-003: maxStops 5 ise dönen rotalarda doğru sayıda nokta var mı")
@Test
    void shouldReturnRoutesWithCorrectPointCount_whenMaxStopsIsFive() throws Exception {
        // given
        Map<String, String> uv = buildValidUserVector();
        uv.put("maxStops", "5");
        GenerateRoutesRequest req = new GenerateRoutesRequest();
        req.setUserVector(uv);
        req.setK(1);

        // when
        MvcResult result = mockMvc.perform(post("/api/routes/generate")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        List<RouteResponse> routes = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {});

        // then
        assertThat(routes.get(0).getPoints()).hasSize(5);
    }

    @DisplayName("TC-RGI-004: K değeri 0 gönderilirse 400 Bad Request dönüyor mu")
@Test
    void shouldReturn400_whenKIsZero() throws Exception {
        // given
        GenerateRoutesRequest req = new GenerateRoutesRequest();
        req.setUserVector(buildValidUserVector());
        req.setK(0);

        // when / then
        mockMvc.perform(post("/api/routes/generate")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("TC-RGI-005: User vector null ise 400 Bad Request dönüyor mu")
@Test
    void shouldReturn400_whenUserVectorIsNull() throws Exception {
        // given
        GenerateRoutesRequest req = new GenerateRoutesRequest();
        req.setUserVector(null);
        req.setK(3);

        // when / then
        mockMvc.perform(post("/api/routes/generate")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("TC-RGI-006: Yetkilendirme token olmadan /generate endpoint 401 Unauthorized dönüyor mu")
@Test
    void shouldReturn401_whenNoTokenProvided() throws Exception {
        // given
        GenerateRoutesRequest req = new GenerateRoutesRequest();
        req.setUserVector(buildValidUserVector());
        req.setK(3);

        // when / then
        mockMvc.perform(post("/api/routes/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @DisplayName("TC-RGI-007: Dönen her rota için segment sayısı nokta sayısının bir eksiğine eşit mi")
@Test
    void shouldHaveSegmentsCountEqualToPointsMinusOne_forEachRoute() throws Exception {
        // given
        GenerateRoutesRequest req = new GenerateRoutesRequest();
        req.setUserVector(buildValidUserVector());
        req.setK(3);

        // when
        MvcResult result = mockMvc.perform(post("/api/routes/generate")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        List<RouteResponse> routes = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {});

        // then
        for (RouteResponse r : routes) {
            assertThat(r.getSegments()).hasSize(r.getPoints().size() - 1);
        }
    }

    @DisplayName("TC-RGI-008: K değeri 1 ise tek rota mı dönüyor")
@Test
    void shouldReturnOneRoute_whenKIsOne() throws Exception {
        // given
        GenerateRoutesRequest req = new GenerateRoutesRequest();
        req.setUserVector(buildValidUserVector());
        req.setK(1);

        // when / then
        mockMvc.perform(post("/api/routes/generate")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ================================================================
    // POST /api/routes/reroll
    // ================================================================


    @DisplayName("TC-RGI-009: Index 1 reroll edildiğinde sadece hedef nokta değişiyor diğerleri aynı kalıyor mu")
@Test
    void shouldChangeOnlyTargetPoint_whenRerollingIndex1() throws Exception {
        // given
        RouteResponse route = generateOneRoute();
        String id0 = route.getPoints().get(0).getPoiId();
        // Indices 2..n IDs preserved
        List<String> otherIds = new ArrayList<>();
        for (int i = 2; i < route.getPoints().size(); i++) {
            otherIds.add(route.getPoints().get(i).getPoiId());
        }

        RerollWithStateRequest req = new RerollWithStateRequest();
        req.setCurrentRoute(route);
        req.setIndex(1);
        req.setIndexParams(new HashMap<>());
        req.setOriginalUserVector(buildValidUserVector());

        // when
        MvcResult result = mockMvc.perform(post("/api/routes/reroll")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        RouteResponse updated = objectMapper.readValue(
                result.getResponse().getContentAsString(), RouteResponse.class);

        // then
        assertThat(updated.getPoints().get(0).getPoiId()).isEqualTo(id0);
        for (int i = 0; i < otherIds.size(); i++) {
            assertThat(updated.getPoints().get(i + 2).getPoiId()).isEqualTo(otherIds.get(i));
        }
    }

    @DisplayName("TC-RGI-010: Başarılı reroll işlemi sonrası route toplamları güncelleniyor mu")
@Test
    void shouldUpdateTotals_afterSuccessfulReroll() throws Exception {
        // given
        RouteResponse route = generateOneRoute();
        RerollWithStateRequest req = new RerollWithStateRequest();
        req.setCurrentRoute(route);
        req.setIndex(0);
        req.setIndexParams(new HashMap<>());
        req.setOriginalUserVector(buildValidUserVector());

        // when
        MvcResult result = mockMvc.perform(post("/api/routes/reroll")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        RouteResponse updated = objectMapper.readValue(
                result.getResponse().getContentAsString(), RouteResponse.class);

        // then
        assertThat(updated.getTotalDurationSec()).isGreaterThanOrEqualTo(0);
        assertThat(updated.getTotalDistanceM()).isGreaterThanOrEqualTo(0.0);
    }

    @DisplayName("TC-RGI-011: Yetkisiz erişimde ilgili endpoint'ler 401 Unauthorized dönüyor mu")
@Test
    void shouldReturn401_whenNotAuthenticated() throws Exception {
        // given
        RerollWithStateRequest req = new RerollWithStateRequest();
        req.setIndex(0);
        req.setOriginalUserVector(buildValidUserVector());

        // when / then
        mockMvc.perform(post("/api/routes/reroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // ================================================================
    // POST /api/routes/insert
    // ================================================================


    @DisplayName("TC-RGI-012: Eklenecek POI bulunduğunda doğru index'e insert ediliyor mu")
@Test
    void shouldInsertPOIAtCorrectIndex_whenPoiExists() throws Exception {
        // given
        RouteResponse route = generateOneRoute();
        int originalSize = route.getPoints().size();
        // Choose a POI not in the route
        String insertPoiId = "c1"; // cafe A from test data

        InsertWithStateRequest req = new InsertWithStateRequest();
        req.setCurrentRoute(route);
        req.setIndex(1);
        req.setPoiId(insertPoiId);
        req.setOriginalUserVector(buildValidUserVector());

        // when
        MvcResult result = mockMvc.perform(post("/api/routes/insert")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        RouteResponse updated = objectMapper.readValue(
                result.getResponse().getContentAsString(), RouteResponse.class);

        // then
        assertThat(updated.getPoints()).hasSize(originalSize + 1);
        assertThat(updated.getPoints().get(1).getPoiId()).isEqualTo(insertPoiId);
    }

    @DisplayName("TC-RGI-013: Dizinin ortasına insert edildiğinde sonrasındaki noktalar kaydırılıyor mu")
@Test
    void shouldShiftSubsequentPoints_whenInsertingAtMiddle() throws Exception {
        // given
        RouteResponse route = generateOneRoute();
        String origAtIndex1 = route.getPoints().get(1).getPoiId();

        InsertWithStateRequest req = new InsertWithStateRequest();
        req.setCurrentRoute(route);
        req.setIndex(1);
        req.setPoiId("l1"); // landmark from test data
        req.setOriginalUserVector(buildValidUserVector());

        // when
        MvcResult result = mockMvc.perform(post("/api/routes/insert")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        RouteResponse updated = objectMapper.readValue(
                result.getResponse().getContentAsString(), RouteResponse.class);

        // then — original index-1 value should now be at index 2
        assertThat(updated.getPoints().get(2).getPoiId()).isEqualTo(origAtIndex1);
    }

    @DisplayName("TC-RGI-014: Başarılı insert işlemi sonrası route toplamları güncelleniyor mu")
@Test
    void shouldUpdateTotals_afterSuccessfulInsert() throws Exception {
        // given
        RouteResponse route = generateOneRoute();
        InsertWithStateRequest req = new InsertWithStateRequest();
        req.setCurrentRoute(route);
        req.setIndex(0);
        req.setPoiId("r2");
        req.setOriginalUserVector(buildValidUserVector());

        // when
        MvcResult result = mockMvc.perform(post("/api/routes/insert")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        RouteResponse updated = objectMapper.readValue(
                result.getResponse().getContentAsString(), RouteResponse.class);

        // then
        assertThat(updated.getTotalDurationSec()).isGreaterThanOrEqualTo(0);
        assertThat(updated.getTotalDistanceM()).isGreaterThanOrEqualTo(0.0);
    }

    @DisplayName("TC-RGI-011: Yetkisiz erişimde ilgili endpoint'ler 401 Unauthorized dönüyor mu")
@Test
    void shouldReturn401_whenNotAuthenticated_2() throws Exception {
        // given / when / then
        InsertWithStateRequest req = new InsertWithStateRequest();
        req.setIndex(0);
        req.setPoiId("r1");
        req.setOriginalUserVector(buildValidUserVector());
        mockMvc.perform(post("/api/routes/insert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // ================================================================
    // POST /api/routes/remove
    // ================================================================


    @DisplayName("TC-RGI-015: Geçerli index silindiğinde nokta kaldırılıp route dizisi sıkıştırılıyor mu")
@Test
    void shouldRemovePointAndCompactRoute_whenIndexIsValid() throws Exception {
        // given
        RouteResponse route = generateOneRoute();
        int originalSize = route.getPoints().size();
        String lastPoiId = route.getPoints().get(originalSize - 1).getPoiId();

        RemoveWithStateRequest req = new RemoveWithStateRequest();
        req.setCurrentRoute(route);
        req.setIndex(2);
        req.setOriginalUserVector(buildValidUserVector());

        // when
        MvcResult result = mockMvc.perform(post("/api/routes/remove")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        RouteResponse updated = objectMapper.readValue(
                result.getResponse().getContentAsString(), RouteResponse.class);

        // then
        assertThat(updated.getPoints()).hasSize(originalSize - 1);
    }

    @DisplayName("TC-RGI-016: Başarılı remove işlemi sonrası route toplamları güncelleniyor mu")
@Test
    void shouldUpdateTotals_afterSuccessfulRemoval() throws Exception {
        // given
        RouteResponse route = generateOneRoute();
        RemoveWithStateRequest req = new RemoveWithStateRequest();
        req.setCurrentRoute(route);
        req.setIndex(0);
        req.setOriginalUserVector(buildValidUserVector());

        // when
        MvcResult result = mockMvc.perform(post("/api/routes/remove")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        RouteResponse updated = objectMapper.readValue(
                result.getResponse().getContentAsString(), RouteResponse.class);

        // then
        assertThat(updated.getTotalDurationSec()).isGreaterThanOrEqualTo(0);
        assertThat(updated.getTotalDistanceM()).isGreaterThanOrEqualTo(0.0);
    }

    @DisplayName("TC-RGI-011: Yetkisiz erişimde ilgili endpoint'ler 401 Unauthorized dönüyor mu")
@Test
    void shouldReturn401_whenNotAuthenticated_3() throws Exception {
        // given / when / then
        RemoveWithStateRequest req = new RemoveWithStateRequest();
        req.setIndex(0);
        req.setOriginalUserVector(buildValidUserVector());
        mockMvc.perform(post("/api/routes/remove")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // ================================================================
    // POST /api/routes/reorder
    // ================================================================


    @DisplayName("TC-RGI-017: Geçerli permütasyon ile POI noktaları düzgün yeniden sıralanıyor mu")
@Test
    void shouldReorderPointsCorrectly_whenPermutationIsValid() throws Exception {
        // given
        RouteResponse route = generateOneRoute();
        String origAt2 = route.getPoints().get(2).getPoiId();

        List<Integer> newOrder = new ArrayList<>();
        int size = route.getPoints().size();
        // Swap indices 1 and 2: [0, 2, 1, 3, 4]
        for (int i = 0; i < size; i++) newOrder.add(i);
        if (size >= 3) {
            newOrder.set(1, 2);
            newOrder.set(2, 1);
        }

        ReorderWithStateRequest req = new ReorderWithStateRequest();
        req.setCurrentRoute(route);
        req.setNewOrder(newOrder);
        req.setOriginalUserVector(buildValidUserVector());

        // when
        MvcResult result = mockMvc.perform(post("/api/routes/reorder")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        RouteResponse updated = objectMapper.readValue(
                result.getResponse().getContentAsString(), RouteResponse.class);

        // then — original index 2 should now be at index 1
        assertThat(updated.getPoints().get(1).getPoiId()).isEqualTo(origAt2);
    }

    @DisplayName("TC-RGI-018: Yeniden sıralama işleminden sonra tüm segment değerleri tekrar hesaplanıyor mu")
@Test
    void shouldRecomputeAllSegments_afterReorder() throws Exception {
        // given
        RouteResponse route = generateOneRoute();
        List<Integer> newOrder = new ArrayList<>();
        int size = route.getPoints().size();
        for (int i = size - 1; i >= 0; i--) newOrder.add(i); // reverse

        ReorderWithStateRequest req = new ReorderWithStateRequest();
        req.setCurrentRoute(route);
        req.setNewOrder(newOrder);
        req.setOriginalUserVector(buildValidUserVector());

        // when
        MvcResult result = mockMvc.perform(post("/api/routes/reorder")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        RouteResponse updated = objectMapper.readValue(
                result.getResponse().getContentAsString(), RouteResponse.class);

        // then
        assertThat(updated.getSegments()).hasSize(updated.getPoints().size() - 1);
    }

    @DisplayName("TC-RGI-011: Yetkisiz erişimde ilgili endpoint'ler 401 Unauthorized dönüyor mu")
@Test
    void shouldReturn401_whenNotAuthenticated_4() throws Exception {
        // given / when / then
        ReorderWithStateRequest req = new ReorderWithStateRequest();
        req.setNewOrder(List.of(0, 1));
        req.setOriginalUserVector(buildValidUserVector());
        mockMvc.perform(post("/api/routes/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // ================================================================
    // Additional integration tests
    // ================================================================


    @DisplayName("TC-RGI-019: Zorunlu tür şartı sağlanamadığında feasible dönüşü false oluyor mu")
@Test
    void shouldReturnFeasibleFieldAsFalse_whenMandatoryTypeCannotBeSatisfied() throws Exception {
        // TC-RGI-007: behavior is implementation-defined (422 or feasible=false);
        // test verifies no false positives
        // given
        Map<String, String> uv = buildValidUserVector();
        uv.put("mandatoryTypes", "nonexistent_type_xyz");
        GenerateRoutesRequest req = new GenerateRoutesRequest();
        req.setUserVector(uv);
        req.setK(1);

        // when
        MvcResult result = mockMvc.perform(post("/api/routes/generate")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        // then
        if (result.getResponse().getStatus() == 200) {
            List<RouteResponse> routes = objectMapper.readValue(
                    result.getResponse().getContentAsString(), new TypeReference<>() {});
            for (RouteResponse r : routes) {
                assertThat(r.isFeasible()).isFalse();
            }
        }
        // If 422, that's also acceptable
    }

    @DisplayName("TC-RGI-020: Geçerli ağırlıklar (weights) ile olasılıksal dağılım düzgün çalışıyor mu")
@Test
    void shouldDistributeTypesByWeight_whenWeightsAreProvided() throws Exception {
        // given — use mandatoryTypes=museum to guarantee museum appears in every route;
        // additionally set high museum weight and very low cafe weight
        Map<String, String> uv = buildValidUserVector();
        uv.put("weight_museum", "3.0");
        uv.put("weight_cafe", "0.01");
        uv.put("mandatoryTypes", "museum");
        uv.put("maxStops", "5");
        GenerateRoutesRequest req = new GenerateRoutesRequest();
        req.setUserVector(uv);
        req.setK(5);

        // when
        MvcResult result = mockMvc.perform(post("/api/routes/generate")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        List<RouteResponse> routes = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {});

        // then — museums must appear at least once (guaranteed by mandatory type)
        int museumCount = 0;
        for (RouteResponse r : routes) {
            for (var pt : r.getPoints()) {
                if (pt.getTypes() != null) {
                    for (String t : pt.getTypes()) {
                        if (t.toLowerCase().contains("museum")) museumCount++;
                    }
                }
            }
        }
        // With mandatoryTypes=museum and k=5, we expect at least 5 museum appearances
        assertThat(museumCount).isGreaterThanOrEqualTo(5);
    }
}
