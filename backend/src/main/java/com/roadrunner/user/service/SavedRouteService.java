package com.roadrunner.user.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadrunner.route.dto.request.GenerateRoutesRequest;
import com.roadrunner.route.dto.response.RoutePointResponse;
import com.roadrunner.route.dto.response.RouteResponse;
import com.roadrunner.user.dto.request.RenameSavedRouteRequest;
import com.roadrunner.user.dto.request.SavedRouteWriteRequest;
import com.roadrunner.user.dto.response.SavedRouteDetailResponse;
import com.roadrunner.user.dto.response.SavedRouteSummaryResponse;
import com.roadrunner.user.entity.SavedRoute;
import com.roadrunner.user.entity.User;
import com.roadrunner.user.repository.SavedRouteRepository;
import com.roadrunner.user.repository.UserRepository;

@Service
@SuppressWarnings("null")
public class SavedRouteService {

    private static final DateTimeFormatter DEFAULT_TITLE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final SavedRouteRepository savedRouteRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public SavedRouteService(
            SavedRouteRepository savedRouteRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper) {
        this.savedRouteRepository = savedRouteRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public List<SavedRouteSummaryResponse> getAllSavedRoutes(String userId) {
        findUserById(userId);
        return savedRouteRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    public SavedRouteDetailResponse getSavedRouteById(String userId, String savedRouteId) {
        SavedRoute savedRoute = findOwnedSavedRoute(userId, savedRouteId);
        return toDetailResponse(savedRoute);
    }

    public SavedRouteDetailResponse createSavedRoute(String userId, SavedRouteWriteRequest request) {
        User user = findUserById(userId);
        validateRoutePayload(request);

        SavedRoute savedRoute = SavedRoute.builder()
                .user(user)
                .build();
        applyWriteRequest(savedRoute, request);
        return toDetailResponse(savedRouteRepository.save(savedRoute));
    }

    public SavedRouteDetailResponse updateSavedRoute(
            String userId,
            String savedRouteId,
            SavedRouteWriteRequest request) {
        SavedRoute savedRoute = findOwnedSavedRoute(userId, savedRouteId);
        validateRoutePayload(request);
        applyWriteRequest(savedRoute, request);
        return toDetailResponse(savedRouteRepository.save(savedRoute));
    }

    public SavedRouteSummaryResponse renameSavedRoute(
            String userId,
            String savedRouteId,
            RenameSavedRouteRequest request) {
        SavedRoute savedRoute = findOwnedSavedRoute(userId, savedRouteId);
        savedRoute.setTitle(normalizeTitle(request.getTitle()));
        return toSummaryResponse(savedRouteRepository.save(savedRoute));
    }

    public void deleteSavedRoute(String userId, String savedRouteId) {
        SavedRoute savedRoute = findOwnedSavedRoute(userId, savedRouteId);
        savedRouteRepository.delete(savedRoute);
    }

    private void applyWriteRequest(SavedRoute savedRoute, SavedRouteWriteRequest request) {
        RouteResponse route = request.getRoute();
        GenerateRoutesRequest generateRequest = request.getGenerateRequest();

        savedRoute.setTitle(resolveTitle(request.getTitle(), savedRoute.getTitle()));
        savedRoute.setOrderedPlaceIds(extractOrderedPlaceIds(route));
        savedRoute.setRouteSnapshotJson(writeJson(route));
        savedRoute.setGenerateRequestJson(writeJson(generateRequest));
        savedRoute.setTravelMode(route.getTravelMode());
        savedRoute.setTotalDurationSec(route.getTotalDurationSec());
        savedRoute.setTotalDistanceM(route.getTotalDistanceM());
        savedRoute.setFeasible(route.isFeasible());
    }

    private void validateRoutePayload(SavedRouteWriteRequest request) {
        if (request.getRoute() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Route payload is required");
        }
        if (request.getGenerateRequest() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Generate request payload is required");
        }
        if (request.getRoute().getPoints() == null || request.getRoute().getPoints().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Route snapshot must contain at least one point");
        }
    }

    private List<String> extractOrderedPlaceIds(RouteResponse route) {
        List<String> orderedPlaceIds = new ArrayList<>();
        for (RoutePointResponse point : route.getPoints()) {
            if (point != null && point.getPoiId() != null && !point.getPoiId().isBlank()) {
                orderedPlaceIds.add(point.getPoiId());
            }
        }
        return orderedPlaceIds;
    }

    private SavedRouteSummaryResponse toSummaryResponse(SavedRoute savedRoute) {
        return SavedRouteSummaryResponse.builder()
                .id(savedRoute.getId())
                .title(savedRoute.getTitle())
                .orderedPlaceIds(savedRoute.getOrderedPlaceIds())
                .stopCount(savedRoute.getOrderedPlaceIds() != null ? savedRoute.getOrderedPlaceIds().size() : 0)
                .travelMode(savedRoute.getTravelMode())
                .totalDurationSec(savedRoute.getTotalDurationSec())
                .totalDistanceM(savedRoute.getTotalDistanceM())
                .feasible(savedRoute.isFeasible())
                .createdAt(savedRoute.getCreatedAt())
                .updatedAt(savedRoute.getUpdatedAt())
                .build();
    }

    private SavedRouteDetailResponse toDetailResponse(SavedRoute savedRoute) {
        RouteResponse route = readJson(savedRoute.getRouteSnapshotJson(), RouteResponse.class);
        GenerateRoutesRequest generateRequest =
                readJson(savedRoute.getGenerateRequestJson(), GenerateRoutesRequest.class);

        return SavedRouteDetailResponse.builder()
                .id(savedRoute.getId())
                .title(savedRoute.getTitle())
                .orderedPlaceIds(savedRoute.getOrderedPlaceIds())
                .stopCount(savedRoute.getOrderedPlaceIds() != null ? savedRoute.getOrderedPlaceIds().size() : 0)
                .travelMode(savedRoute.getTravelMode())
                .totalDurationSec(savedRoute.getTotalDurationSec())
                .totalDistanceM(savedRoute.getTotalDistanceM())
                .feasible(savedRoute.isFeasible())
                .createdAt(savedRoute.getCreatedAt())
                .updatedAt(savedRoute.getUpdatedAt())
                .route(route)
                .generateRequest(generateRequest)
                .build();
    }

    private SavedRoute findOwnedSavedRoute(String userId, String savedRouteId) {
        SavedRoute savedRoute = savedRouteRepository.findById(savedRouteId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Saved route not found"));

        if (!savedRoute.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return savedRoute;
    }

    private User findUserById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));
    }

    private String resolveTitle(String title, String existingTitle) {
        if (title != null && !title.isBlank()) {
            return normalizeTitle(title);
        }
        if (existingTitle != null && !existingTitle.isBlank()) {
            return existingTitle;
        }
        return "Route " + DEFAULT_TITLE_FORMATTER.format(Instant.ofEpochMilli(System.currentTimeMillis()));
    }

    private String normalizeTitle(String title) {
        return title == null ? "" : title.trim();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to serialize saved route payload",
                    e);
        }
    }

    private <T> T readJson(String json, Class<T> targetType) {
        try {
            return objectMapper.readValue(json, targetType);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to deserialize saved route payload",
                    e);
        }
    }
}
