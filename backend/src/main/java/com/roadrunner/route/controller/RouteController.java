package com.roadrunner.route.controller;

import java.util.ArrayList;
import java.util.List;

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
        List<Route> routes = routeService.generateRoutes(
                req.getUserVector(), req.getK());
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

        // The incoming newOrder covers all points including hotel anchors.
        // Extract interior-only indices (skip first and last hotel).
        List<Integer> fullOrder = req.getNewOrder();
        List<Integer> interiorOrder = new ArrayList<>();
        if (fullOrder != null && fullOrder.size() >= 3) {
            for (int i = 1; i < fullOrder.size() - 1; i++) {
                // Convert from full-route index to interior-only index
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
                points.add(RoutePoint.builder()
                        .index(rpr.getIndex())
                        .poi(place)
                        .plannedVisitMin(rpr.getPlannedVisitMin())
                        .build());
            }
        }
        route.setPoints(points);

        // Segments are recomputed on mutation, no need to reconstruct them
        return route;
    }
}
