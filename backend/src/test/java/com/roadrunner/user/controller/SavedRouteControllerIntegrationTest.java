package com.roadrunner.user.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadrunner.route.dto.request.GenerateRoutesRequest;
import com.roadrunner.route.dto.request.RouteAnchorRequest;
import com.roadrunner.route.dto.request.RouteConstraintsRequest;
import com.roadrunner.route.dto.request.RoutePreferencesRequest;
import com.roadrunner.route.dto.response.RoutePointResponse;
import com.roadrunner.route.dto.response.RouteResponse;
import com.roadrunner.route.dto.response.RouteSegmentResponse;
import com.roadrunner.security.JwtTokenProvider;
import com.roadrunner.user.dto.request.RenameSavedRouteRequest;
import com.roadrunner.user.dto.request.SavedRouteWriteRequest;
import com.roadrunner.user.entity.SavedRoute;
import com.roadrunner.user.entity.User;
import com.roadrunner.user.repository.SavedRouteRepository;
import com.roadrunner.user.repository.TravelPersonaRepository;
import com.roadrunner.user.repository.TravelPlanRepository;
import com.roadrunner.user.repository.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SuppressWarnings("null")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Integration Tests - SavedRouteController")
class SavedRouteControllerIntegrationTest {

    private static final String TEST_PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TravelPersonaRepository travelPersonaRepository;

    @Autowired
    private TravelPlanRepository travelPlanRepository;

    @Autowired
    private SavedRouteRepository savedRouteRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User testUser;
    private User otherUser;
    private String testToken;
    private String otherToken;

    @BeforeEach
    void setUp() {
        savedRouteRepository.deleteAll();
        travelPlanRepository.deleteAll();
        travelPersonaRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .email("saved-route-user@roadrunner.com")
                .name("Saved Route User")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .build());
        otherUser = userRepository.save(User.builder()
                .email("saved-route-other@roadrunner.com")
                .name("Other User")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .build());

        testToken = jwtTokenProvider.generateToken(testUser.getId());
        otherToken = jwtTokenProvider.generateToken(otherUser.getId());
    }

    @Test
    @DisplayName("Creates a saved route with exact snapshot, request payload, and ordered place ids")
    void shouldCreateSavedRouteFromSnapshot() throws Exception {
        SavedRouteWriteRequest request = buildWriteRequest(null, "route-A", "place-start", "place-middle");

        mockMvc.perform(withAuth(post("/api/users/me/saved-routes"), testToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.title", not(endsWith(" "))))
                .andExpect(jsonPath("$.title", org.hamcrest.Matchers.startsWith("Route ")))
                .andExpect(jsonPath("$.orderedPlaceIds", contains("place-start", "place-middle")))
                .andExpect(jsonPath("$.stopCount", is(2)))
                .andExpect(jsonPath("$.route.routeId", is("route-A")))
                .andExpect(jsonPath("$.route.points", hasSize(3)))
                .andExpect(jsonPath("$.generateRequest.userVector.requestId", is("req-123")))
                .andExpect(jsonPath("$.generateRequest.preferences.historyPreference", is(0.7)))
                .andExpect(jsonPath("$.generateRequest.constraints.startAnchor.placeId", is("poi-anchor")));
    }

    @Test
    @DisplayName("Lists only current user's saved routes")
    void shouldListOnlyCurrentUsersSavedRoutes() throws Exception {
        savedRouteRepository.save(buildSavedRoute(testUser, "Mine A", "route-a", "mine-1"));
        Thread.sleep(5L);
        savedRouteRepository.save(buildSavedRoute(testUser, "Mine B", "route-b", "mine-2"));
        savedRouteRepository.save(buildSavedRoute(otherUser, "Theirs", "route-c", "other-1"));

        mockMvc.perform(withAuth(get("/api/users/me/saved-routes"), testToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title", is("Mine B")))
                .andExpect(jsonPath("$[1].title", is("Mine A")));
    }

    @Test
    @DisplayName("Returns exact saved route detail for owner")
    void shouldReturnSavedRouteDetailForOwner() throws Exception {
        SavedRoute savedRoute = savedRouteRepository.save(buildSavedRoute(testUser, "My Route", "route-detail", "place-1"));

        mockMvc.perform(withAuth(get("/api/users/me/saved-routes/" + savedRoute.getId()), testToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(savedRoute.getId())))
                .andExpect(jsonPath("$.title", is("My Route")))
                .andExpect(jsonPath("$.route.routeId", is("route-detail")))
                .andExpect(jsonPath("$.route.points[0].poiId", is("place-1")))
                .andExpect(jsonPath("$.generateRequest.userVector.weight_tarihiAlanlar", is("0.900")));
    }

    @Test
    @DisplayName("Updates snapshot and summary fields")
    void shouldUpdateSavedRoute() throws Exception {
        SavedRoute existing = savedRouteRepository.save(buildSavedRoute(testUser, "Old Title", "route-old", "place-old"));
        Thread.sleep(5L);

        SavedRouteWriteRequest request = buildWriteRequest("Updated Title", "route-new", "place-a", "place-b");

        mockMvc.perform(withAuth(put("/api/users/me/saved-routes/" + existing.getId()), testToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Updated Title")))
                .andExpect(jsonPath("$.route.routeId", is("route-new")))
                .andExpect(jsonPath("$.orderedPlaceIds", contains("place-a", "place-b")))
                .andExpect(jsonPath("$.totalDurationSec", is(5400)))
                .andExpect(jsonPath("$.updatedAt", greaterThan(existing.getUpdatedAt())));
    }

    @Test
    @DisplayName("Renames saved route and refreshes updated timestamp")
    void shouldRenameSavedRoute() throws Exception {
        SavedRoute existing = savedRouteRepository.save(buildSavedRoute(testUser, "Original", "route-old", "place-old"));
        long originalUpdatedAt = existing.getUpdatedAt();
        Thread.sleep(5L);

        RenameSavedRouteRequest request = RenameSavedRouteRequest.builder()
                .title("Renamed Route")
                .build();

        mockMvc.perform(withAuth(patch("/api/users/me/saved-routes/" + existing.getId() + "/title"), testToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Renamed Route")))
                .andExpect(jsonPath("$.updatedAt", greaterThan(originalUpdatedAt)));
    }

    @Test
    @DisplayName("Deletes owned saved route")
    void shouldDeleteSavedRoute() throws Exception {
        SavedRoute existing = savedRouteRepository.save(buildSavedRoute(testUser, "To Delete", "route-delete", "place-delete"));

        mockMvc.perform(withAuth(delete("/api/users/me/saved-routes/" + existing.getId()), testToken))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(savedRouteRepository.findById(existing.getId())).isEmpty();
    }

    @Test
    @DisplayName("Rejects malformed or empty saved route payload")
    void shouldRejectInvalidSavedRoutePayload() throws Exception {
        SavedRouteWriteRequest request = SavedRouteWriteRequest.builder()
                .title("Broken Route")
                .route(RouteResponse.builder().routeId("bad").points(List.of()).segments(List.of()).build())
                .generateRequest(new GenerateRoutesRequest())
                .build();

        mockMvc.perform(withAuth(post("/api/users/me/saved-routes"), testToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Prevents cross-user access to saved routes")
    void shouldRejectCrossUserAccess() throws Exception {
        SavedRoute existing = savedRouteRepository.save(buildSavedRoute(otherUser, "Other Route", "route-other", "place-other"));

        mockMvc.perform(withAuth(get("/api/users/me/saved-routes/" + existing.getId()), testToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(withAuth(delete("/api/users/me/saved-routes/" + existing.getId()), testToken))
                .andExpect(status().isForbidden());
    }

    private SavedRouteWriteRequest buildWriteRequest(
            String title,
            String routeId,
            String firstPlaceId,
            String secondPlaceId) {
        RouteResponse route = RouteResponse.builder()
                .routeId(routeId)
                .points(List.of(
                        RoutePointResponse.builder()
                                .index(0)
                                .poiId(firstPlaceId)
                                .poiName("Start Place")
                                .latitude(39.92)
                                .longitude(32.85)
                                .types(List.of("restaurant"))
                                .plannedVisitMin(45)
                                .build(),
                        RoutePointResponse.builder()
                                .index(1)
                                .poiId(null)
                                .poiName("Custom Anchor")
                                .latitude(39.93)
                                .longitude(32.86)
                                .types(List.of())
                                .plannedVisitMin(0)
                                .build(),
                        RoutePointResponse.builder()
                                .index(2)
                                .poiId(secondPlaceId)
                                .poiName("End Place")
                                .latitude(39.94)
                                .longitude(32.87)
                                .types(List.of("museum"))
                                .plannedVisitMin(60)
                                .build()))
                .segments(List.of(
                        RouteSegmentResponse.builder().fromIndex(0).toIndex(1).durationSec(1200).distanceM(1000).build(),
                        RouteSegmentResponse.builder().fromIndex(1).toIndex(2).durationSec(1800).distanceM(1500).build()))
                .totalDurationSec(5400)
                .totalDistanceM(3500.0)
                .feasible(true)
                .travelMode("walking")
                .build();

        GenerateRoutesRequest generateRequest = new GenerateRoutesRequest();
        generateRequest.setUserVector(Map.of(
                "requestId", "req-123",
                "weight_tarihiAlanlar", "0.900"));
        generateRequest.setPreferences(new RoutePreferencesRequest(
                0.6, 0.5, 0.4, 0.7, 0.8, 0.3, 0.4, 0.5, 0.6, 0.5));
        generateRequest.setConstraints(new RouteConstraintsRequest(
                true, true, false, true,
                null, null, null, null,
                new RouteAnchorRequest("PLACE", "poi-anchor", null, null),
                new RouteAnchorRequest("TYPE", null, "HOTEL", null),
                List.of(),
                3));
        generateRequest.setCenterLat(39.93);
        generateRequest.setCenterLng(32.85);
        generateRequest.setK(3);

        return SavedRouteWriteRequest.builder()
                .title(title)
                .route(route)
                .generateRequest(generateRequest)
                .build();
    }

    private SavedRoute buildSavedRoute(User user, String title, String routeId, String placeId) throws Exception {
        SavedRouteWriteRequest request = buildWriteRequest(title, routeId, placeId, placeId + "-2");
        return SavedRoute.builder()
                .user(user)
                .title(title)
                .orderedPlaceIds(List.of(placeId, placeId + "-2"))
                .routeSnapshotJson(objectMapper.writeValueAsString(request.getRoute()))
                .generateRequestJson(objectMapper.writeValueAsString(request.getGenerateRequest()))
                .travelMode(request.getRoute().getTravelMode())
                .totalDurationSec(request.getRoute().getTotalDurationSec())
                .totalDistanceM(request.getRoute().getTotalDistanceM())
                .feasible(request.getRoute().isFeasible())
                .build();
    }

    private MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }
}
