package com.roadrunner.route.performance;

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
import com.roadrunner.route.dto.response.RouteResponse;
import com.roadrunner.user.dto.request.LoginRequest;
import com.roadrunner.user.dto.request.RegisterRequest;
import com.roadrunner.user.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Performance tests measure wall-clock time including Spring's MockMvc overhead.
// These tests are inherently environment-dependent and may fail on slow CI machines.
// Thresholds are based on NFR-PERF-01: single route ≤ 5s, three routes ≤ 8s.

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SuppressWarnings("null")
@Epic("Route Generation")
@Feature("Performance Tests")
@DisplayName("Performance Tests - RouteGeneration")
class RouteGenerationPerformanceTest {

    private static final String TEST_EMAIL = "perf-test@roadrunner.com";
    private static final String TEST_PASSWORD = "password123";
    private static final double ANKARA_LAT = 39.9208;
    private static final double ANKARA_LNG = 32.8541;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PlaceRepository placeRepository;
    @Autowired private UserRepository userRepository;

    private String jwtToken;

    @BeforeAll
    void setUpAll() throws Exception {
        userRepository.deleteAll();
        placeRepository.deleteAll();
        placeRepository.saveAll(build50TestPlaces());
        jwtToken = obtainToken();
    }

    @AfterAll
    void tearDown() {
        placeRepository.deleteAll();
        userRepository.deleteAll();
    }

    private List<Place> build50TestPlaces() {
        String[] types = {"restaurant", "museum", "park", "cafe", "landmark",
                "hotel", "tourist_attraction", "historical", "nature", "lodging"};
        List<Place> places = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            String type = types[i % types.length];
            places.add(Place.builder()
                    .id("perf-" + i)
                    .name("PerfPlace " + i)
                    .types(type)
                    .latitude(ANKARA_LAT + (i * 0.005) - 0.125)
                    .longitude(ANKARA_LNG + (i * 0.005) - 0.125)
                    .ratingScore(3.0 + (i % 20) * 0.1)
                    .ratingCount(50 + i * 10)
                    .businessStatus("OPERATIONAL")
                    .build());
        }
        return places;
    }

    private String obtainToken() throws Exception {
        RegisterRequest regReq = RegisterRequest.builder()
                .name("Perf User").email(TEST_EMAIL).password(TEST_PASSWORD).build();
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

    private Map<String, String> buildUserVector() {
        Map<String, String> uv = new HashMap<>();
        uv.put("requestId", "perf-test");
        uv.put("maxStops", "6");
        uv.put("maxBudgetMin", "480");
        uv.put("mode", "driving");
        uv.put("centerLat", String.valueOf(ANKARA_LAT));
        uv.put("centerLng", String.valueOf(ANKARA_LNG));
        uv.put("radiusKm", "50");
        return uv;
    }

    @DisplayName("TC-RGP-001: Tek bir rota 5 saniyenin altında üretilebiliyor mu")
    @Test
    void shouldGenerateSingleRouteUnderFiveSeconds() throws Exception {
        // given
        GenerateRoutesRequest req = new GenerateRoutesRequest();
        req.setUserVector(buildUserVector());
        req.setK(1);

        // when
        long start = System.currentTimeMillis();
        MvcResult result = mockMvc.perform(post("/api/routes/generate")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        long durationMs = System.currentTimeMillis() - start;

        List<RouteResponse> routes = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {});

        // then
        assertThat(durationMs).isLessThanOrEqualTo(5000L);
        assertThat(routes).hasSize(1);
    }

    @DisplayName("TC-RGP-002: 3 adet rota isteği 8 saniyenin altında cevaplanabiliyor mu")
    @Test
    void shouldGenerateThreeRoutesUnderEightSeconds() throws Exception {
        // given
        GenerateRoutesRequest req = new GenerateRoutesRequest();
        req.setUserVector(buildUserVector());
        req.setK(3);

        // when
        long start = System.currentTimeMillis();
        MvcResult result = mockMvc.perform(post("/api/routes/generate")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        long durationMs = System.currentTimeMillis() - start;

        List<RouteResponse> routes = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<>() {});

        // then
        assertThat(durationMs).isLessThanOrEqualTo(8000L);
        assertThat(routes).hasSize(3);
    }
}
