package com.roadrunner.route.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roadrunner.place.entity.Place;
import com.roadrunner.place.repository.PlaceRepository;
import com.roadrunner.route.dto.request.GenerateRoutesRequest;
import com.roadrunner.route.dto.request.InsertWithStateRequest;
import com.roadrunner.route.dto.request.RemoveWithStateRequest;
import com.roadrunner.route.dto.request.ReorderWithStateRequest;
import com.roadrunner.route.dto.request.RerollWithStateRequest;
import com.roadrunner.route.dto.response.RoutePointResponse;
import com.roadrunner.route.dto.response.RouteResponse;
import com.roadrunner.route.entity.Route;
import com.roadrunner.route.entity.RoutePoint;
import com.roadrunner.route.service.RouteGenerationService;

import jakarta.validation.Valid;

/**
 * REST controller for stateless route generation and mutation.
 * All endpoints require JWT authentication (covered by SecurityConfig's
 * {@code anyRequest().authenticated()}).
 */
@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final RouteGenerationService routeService;
    private final PlaceRepository placeRepository;

    public RouteController(RouteGenerationService routeService,
                           PlaceRepository placeRepository) {
        this.routeService = routeService;
        this.placeRepository = placeRepository;
    }

    // ------------------------------------------------------------------
    // Generate
    // ------------------------------------------------------------------

    @PostMapping("/generate")
    public List<RouteResponse> generateRoutes(
            @RequestBody @Valid GenerateRoutesRequest req) {
        boolean stayAtHotel = parseStayAtHotel(req);
        Map<String, String> generationVector = buildGenerationUserVector(req);
        List<Route> routes = routeService.generateRoutes(
                generationVector, stayAtHotel, req.getK());
        return routes.stream()
                .map(RouteResponse::fromRoute)
                .toList();
    }

    // ------------------------------------------------------------------
    // Reroll
    // ------------------------------------------------------------------

    @PostMapping("/reroll")
    public RouteResponse rerollPoint(
            @RequestBody @Valid RerollWithStateRequest req) {
        Route route = reconstructRoute(req.getCurrentRoute());
        Route updated = routeService.rerollRoutePoint(
                route, req.getIndex(), req.getIndexParams(),
                req.getOriginalUserVector());
        return RouteResponse.fromRoute(updated);
    }

    // ------------------------------------------------------------------
    // Insert
    // ------------------------------------------------------------------

    @PostMapping("/insert")
    public RouteResponse insertPlace(
            @RequestBody @Valid InsertWithStateRequest req) {
        Route route = reconstructRoute(req.getCurrentRoute());
        Route updated = routeService.insertManualPOI(
                route, req.getIndex(), req.getPoiId(),
                req.getOriginalUserVector());
        return RouteResponse.fromRoute(updated);
    }

    // ------------------------------------------------------------------
    // Remove
    // ------------------------------------------------------------------

    @PostMapping("/remove")
    public RouteResponse removePoint(
            @RequestBody @Valid RemoveWithStateRequest req) {
        Route route = reconstructRoute(req.getCurrentRoute());
        Route updated = routeService.removePoint(
                route, req.getIndex(), req.getOriginalUserVector());
        return RouteResponse.fromRoute(updated);
    }

    // ------------------------------------------------------------------
    // Reorder
    // ------------------------------------------------------------------

    @PostMapping("/reorder")
    public RouteResponse reorderPoints(
            @RequestBody @Valid ReorderWithStateRequest req) {
        Route route = reconstructRoute(req.getCurrentRoute());

        List<Integer> fullOrder = req.getNewOrder();
        List<Integer> interiorOrder = new ArrayList<>();
        boolean hotelLoop = isHotelLoopRoute(route);
        int exclusiveUpperBound = fullOrder != null
                ? (hotelLoop ? fullOrder.size() - 1 : fullOrder.size())
                : 0;
        if (fullOrder != null && fullOrder.size() >= 2) {
            for (int i = 1; i < exclusiveUpperBound; i++) {
                interiorOrder.add(fullOrder.get(i) - 1);
            }
        }

        Route updated = routeService.reorderPOIs(
                route, interiorOrder, req.getOriginalUserVector());
        return RouteResponse.fromRoute(updated);
    }

    // ------------------------------------------------------------------
    // Route reconstruction from DTO
    // ------------------------------------------------------------------

    /**
     * Rebuilds a {@link Route} domain object from the client-held
     * {@link RouteResponse} DTO by fetching each POI from the repository.
     */
    private Route reconstructRoute(RouteResponse dto) {
        Route route = new Route();
        route.setRouteId(dto.getRouteId());
        route.setTravelMode(dto.getTravelMode());
        route.setTotalDurationSec(dto.getTotalDurationSec());
        route.setTotalDistanceM(dto.getTotalDistanceM());
        route.setFeasible(dto.isFeasible());

        List<RoutePoint> points = new ArrayList<>();
        if (dto.getPoints() != null) {
            for (RoutePointResponse rpr : dto.getPoints()) {
                Place place = null;
                if (rpr.getPoiId() != null) {
                    place = placeRepository.findById(rpr.getPoiId())
                            .orElse(null);
                }
                RoutePoint.RoutePointBuilder builder = RoutePoint.builder()
                        .index(rpr.getIndex())
                        .poi(place)
                        .plannedVisitMin(rpr.getPlannedVisitMin());
                if (place == null && (rpr.getLatitude() != 0.0 || rpr.getLongitude() != 0.0)) {
                    builder.anchorName(rpr.getPoiName())
                            .anchorLatitude(rpr.getLatitude())
                            .anchorLongitude(rpr.getLongitude());
                }
                points.add(builder.build());
            }
        }
        route.setPoints(points);

        // Segments are recomputed on mutation, no need to reconstruct them
        return route;
    }

    private boolean parseStayAtHotel(GenerateRoutesRequest req) {
        if (req.getConstraints() == null) {
            return true;
        }
        return req.getConstraints().getStayAtHotel() == null
                || req.getConstraints().getStayAtHotel();
    }

    private Map<String, String> buildGenerationUserVector(GenerateRoutesRequest req) {
        Map<String, String> generationVector = new HashMap<>();
        if (req.getUserVector() != null) {
            generationVector.putAll(req.getUserVector());
        }
        if (req.getCenterLat() != null) {
            generationVector.put("centerLat", String.valueOf(req.getCenterLat()));
        }
        if (req.getCenterLng() != null) {
            generationVector.put("centerLng", String.valueOf(req.getCenterLng()));
        }
        return generationVector;
    }

    private boolean isHotelLoopRoute(Route route) {
        if (route.getPoints() == null || route.getPoints().size() < 2) {
            return false;
        }
        RoutePoint first = route.getPoints().get(0);
        RoutePoint last = route.getPoints().get(route.getPoints().size() - 1);
        return first.getPoi() != null
                && last.getPoi() != null
                && first.getPoi().getId() != null
                && first.getPoi().getId().equals(last.getPoi().getId());
    }
}
