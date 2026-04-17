package com.roadrunner.performance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadrunner.place.repository.PlaceRepository;
import com.roadrunner.route.dto.request.GenerateRoutesRequest;
import com.roadrunner.route.dto.response.RouteResponse;
import com.roadrunner.user.dto.request.LoginRequest;
import com.roadrunner.user.dto.request.RegisterRequest;
import com.roadrunner.user.repository.UserRepository;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real database smoke/performance test for 10 concurrent users.
 * Enable explicitly with -Dreal.db.concurrent.enabled=true.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "app.recaptcha.enabled=false",
                "app.jwt.secret=dGVzdFNlY3JldEtleUZvclJvYWRSdW5uZXJUZXN0aW5nMTIz",
                "app.jwt.expiration-ms=86400000",
                "spring.datasource.driver-class-name=org.postgresql.Driver",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
                "spring.jpa.show-sql=false"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "real.db.concurrent.enabled", matches = "true")
@SuppressWarnings("null")
@Epic("Application Performance")
@Feature("Concurrent Users - Real Database")
@DisplayName("Performance Tests - 10 Concurrent Users on Real Database")
class TenConcurrentUsersRealDatabaseTest {

    private static final int USER_COUNT = 10;
    private static final String PASSWORD = "password123";
    private static final long MAX_TOTAL_DURATION_MS = 20_000L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PlaceRepository placeRepository;
    @Autowired private UserRepository userRepository;

    private final List<String> createdEmails = new ArrayList<>();
    private String runId;

    @BeforeEach
    void setUp() throws Exception {
        runId = Long.toString(System.currentTimeMillis());

        assertThat(placeRepository.count())
                .as("The real database must already contain POIs; this test never seeds or deletes real POIs.")
                .isGreaterThanOrEqualTo(10L);

        registerUsers();
    }

    @AfterEach
    void tearDown() {
        for (String email : createdEmails) {
            userRepository.findByEmail(email).ifPresent(userRepository::delete);
        }
    }

    @Test
    @DisplayName("TC-PERF-REAL-010: Gercek DB ile 10 kullanici ayni anda temel akislarini tamamlayabiliyor")
    void shouldSupportTenConcurrentUsersWithRealDatabase() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(USER_COUNT);
        CountDownLatch startGate = new CountDownLatch(1);

        try {
            List<Future<UserFlowResult>> futures = new ArrayList<>();
            for (int userIndex = 0; userIndex < USER_COUNT; userIndex++) {
                int currentUser = userIndex;
                futures.add(executor.submit(runUserFlowWhenReleased(currentUser, startGate)));
            }

            long startedAt = System.currentTimeMillis();
            startGate.countDown();

            List<UserFlowResult> results = new ArrayList<>();
            for (Future<UserFlowResult> future : futures) {
                results.add(future.get(MAX_TOTAL_DURATION_MS, TimeUnit.MILLISECONDS));
            }
            long totalDurationMs = System.currentTimeMillis() - startedAt;

            assertThat(results)
                    .hasSize(USER_COUNT)
                    .allSatisfy(result -> {
                        assertThat(result.routeCount()).isEqualTo(1);
                        assertThat(result.routePointCount()).isGreaterThanOrEqualTo(3);
                        assertThat(result.durationMs()).isLessThanOrEqualTo(MAX_TOTAL_DURATION_MS);
                    });
            assertThat(totalDurationMs).isLessThanOrEqualTo(MAX_TOTAL_DURATION_MS);

            long slowestUserMs = results.stream()
                    .mapToLong(UserFlowResult::durationMs)
                    .max()
                    .orElse(0L);
            System.out.printf(
                    "10 real-db concurrent user flow completed: total=%d ms, slowestUser=%d ms%n",
                    totalDurationMs,
                    slowestUserMs);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Callable<UserFlowResult> runUserFlowWhenReleased(int userIndex, CountDownLatch startGate) {
        return () -> {
            startGate.await();
            long userStartedAt = System.currentTimeMillis();

            String token = login(userIndex);
            fetchPlaces();
            fetchFilteredPlaces();
            List<RouteResponse> routes = generateRoute(token, userIndex);
            // Chat akisi LLM'e baglanmadan simule edilmek istenirse asagidaki helper aktif edilebilir:
            // createChatAndSendMessages(token, userIndex);

            int routePointCount = routes.get(0).getPoints() == null ? 0 : routes.get(0).getPoints().size();
            return new UserFlowResult(
                    routes.size(),
                    routePointCount,
                    System.currentTimeMillis() - userStartedAt);
        };
    }

    private void registerUsers() throws Exception {
        for (int userIndex = 0; userIndex < USER_COUNT; userIndex++) {
            String email = emailFor(userIndex);
            createdEmails.add(email);

            RegisterRequest request = RegisterRequest.builder()
                    .name("Real DB Concurrent User " + userIndex)
                    .email(email)
                    .password(PASSWORD)
                    .build();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }
    }

    private String login(int userIndex) throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email(emailFor(userIndex))
                .password(PASSWORD)
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("token").asText();
    }

    private void fetchPlaces() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/places")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("content").size()).isEqualTo(10);
    }

    private void fetchFilteredPlaces() throws Exception {
        assertPagedPlaceFilterReturnsOk(get("/api/places")
                .param("type", "restaurant")
                .param("page", "0")
                .param("size", "5"));

        assertPagedPlaceFilterReturnsOk(get("/api/places")
                .param("minRating", "4.5")
                .param("page", "0")
                .param("size", "5"));

        assertPagedPlaceFilterReturnsOk(get("/api/places")
                .param("priceLevel", "PRICE_LEVEL_MODERATE")
                .param("page", "0")
                .param("size", "5"));

        MvcResult categoryResult = mockMvc.perform(get("/api/places/by-category")
                        .param("category", "RESTAURANTS")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(categoryResult.getResponse().getContentAsString()).isArray()).isTrue();
    }

    private void assertPagedPlaceFilterReturnsOk(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("content").isArray()).isTrue();
    }

    /*
    private void createChatAndSendMessages(String token, int userIndex) throws Exception {
        String createChatPayload = """
                {"title":"Real DB concurrent chat %d","duration":"1 day"}
                """.formatted(userIndex);

        MvcResult chatResult = mockMvc.perform(post("/api/chats/new")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createChatPayload))
                .andExpect(status().isCreated())
                .andReturn();

        String chatId = objectMapper.readTree(chatResult.getResponse().getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(post("/api/chats/" + chatId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"user","content":"Ankara'da tarihi yerler ve iyi yemek iceren bir rota oner."}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/chats/" + chatId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"assistant","content":"Tarihi alanlar, restoranlar ve kafeleri dengeleyen bir rota hazirlayabilirim."}
                                """))
                .andExpect(status().isCreated());
    }
    */

    private List<RouteResponse> generateRoute(String token, int userIndex) throws Exception {
        GenerateRoutesRequest request = new GenerateRoutesRequest();
        request.setUserVector(buildUserVector(userIndex));
        request.setK(1);

        MvcResult result = mockMvc.perform(post("/api/routes/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {});
    }

    private Map<String, String> buildUserVector(int userIndex) {
        Map<String, String> userVector = new HashMap<>();
        userVector.put("requestId", "real-db-concurrent-user-" + runId + "-" + userIndex);
        userVector.put("weight_parkVeSeyirNoktalari", weight(0.35, userIndex));
        userVector.put("weight_geceHayati", weight(0.20, userIndex));
        userVector.put("weight_restoranToleransi", weight(0.65, userIndex));
        userVector.put("weight_landmark", weight(0.45, userIndex));
        userVector.put("weight_dogalAlanlar", weight(0.30, userIndex));
        userVector.put("weight_tarihiAlanlar", weight(0.60, userIndex));
        userVector.put("weight_kafeTatli", weight(0.50, userIndex));
        userVector.put("weight_toplamPoiYogunlugu", "0.50");
        userVector.put("weight_sparsity", "0.50");
        userVector.put("weight_hotelCenterBias", "0.55");
        userVector.put("weight_butceSeviyesi", "0.45");
        return userVector;
    }

    private String weight(double base, int userIndex) {
        double adjusted = Math.min(0.95, base + (userIndex % 5) * 0.05);
        return String.format(java.util.Locale.US, "%.2f", adjusted);
    }

    private String emailFor(int userIndex) {
        return "real-db-concurrent-" + runId + "-" + userIndex + "@loadtest.local";
    }

    private record UserFlowResult(
            int routeCount,
            int routePointCount,
            long durationMs) {
    }
}
