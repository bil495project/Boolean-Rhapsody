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

/**
 * Performance tests for the new weight-based route generation.
 * Thresholds: single route ≤ 5s, three routes ≤ 8s.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SuppressWarnings("null")
@Epic("Route Generation")
@Feature("Performance Tests")
@DisplayName("Performance Tests - RouteGeneration (Weight-Based)")
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

    /**
     * Builds 50 test places across all route label categories.
     * All have rating count >= 100 to pass the candidate pool filter.
     */
    private List<Place> build50TestPlaces() {
        // Distribute types across the 7 categories + hotel
        String[] types = {
                "hotel", "restaurant", "museum", "park", "cafe",
                "tourist_attraction", "bar", "nature_preserve",
                "turkish_restaurant", "historical_landmark"
        };
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
                    .ratingCount(100 + i * 10)
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

    @DisplayName("TC-RGP-001: Tek rota 5 saniyenin altında üretiliyor")
    @Test
    void shouldGenerateSingleRouteUnderFiveSeconds() throws Exception {
        GenerateRoutesRequest req = new GenerateRoutesRequest();
        req.setUserVector(buildUserVector());
        req.setK(1);

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

        assertThat(durationMs).isLessThanOrEqualTo(5000L);
        assertThat(routes).hasSize(1);
    }

    @DisplayName("TC-RGP-002: 3 rota 8 saniyenin altında üretiliyor")
    @Test
    void shouldGenerateThreeRoutesUnderEightSeconds() throws Exception {
        GenerateRoutesRequest req = new GenerateRoutesRequest();
        req.setUserVector(buildUserVector());
        req.setK(3);

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

        assertThat(durationMs).isLessThanOrEqualTo(8000L);
        assertThat(routes).hasSize(3);
    }
}
