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
import com.roadrunner.route.service.ResolvedRouteGenerationRequest;
import com.roadrunner.route.service.RouteGenerationService;
import com.roadrunner.route.service.RouteRequestInterpreter;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final RouteGenerationService routeService;
    private final RouteRequestInterpreter requestInterpreter;
    private final PlaceRepository placeRepository;

    public RouteController(RouteGenerationService routeService,
                           RouteRequestInterpreter requestInterpreter,
                           PlaceRepository placeRepository) {
        this.routeService = routeService;
        this.requestInterpreter = requestInterpreter;
        this.placeRepository = placeRepository;
    }

    @PostMapping("/generate")
    public List<RouteResponse> generateRoutes(
            @RequestBody @Valid GenerateRoutesRequest req) {
        ResolvedRouteGenerationRequest resolved = requestInterpreter.interpret(req);
        List<Route> routes = routeService.generateRoutes(resolved, req.getK());
        return routes.stream()
                .map(RouteResponse::fromRoute)
                .toList();
    }

    @PostMapping("/reroll")
    public RouteResponse rerollPoint(
            @RequestBody @Valid RerollWithStateRequest req) {
        Route route = reconstructRoute(req.getCurrentRoute());
        Route updated = routeService.rerollRoutePoint(
                route, req.getIndex(), req.getIndexParams(),
                req.getOriginalUserVector());
        return RouteResponse.fromRoute(updated);
    }

    @PostMapping("/insert")
    public RouteResponse insertPoint(
            @RequestBody @Valid InsertWithStateRequest req) {
        Route route = reconstructRoute(req.getCurrentRoute());
        Route updated = routeService.insertManualPOI(
                route, req.getIndex(), req.getPoiId(), req.getOriginalUserVector());
        return RouteResponse.fromRoute(updated);
    }

    @PostMapping("/remove")
    public RouteResponse removePoint(
            @RequestBody @Valid RemoveWithStateRequest req) {
        Route route = reconstructRoute(req.getCurrentRoute());
        Route updated = routeService.removePoint(
                route, req.getIndex(), req.getOriginalUserVector());
        return RouteResponse.fromRoute(updated);
    }

    @PostMapping("/reorder")
    public RouteResponse reorderPoints(
            @RequestBody @Valid ReorderWithStateRequest req) {
        Route route = reconstructRoute(req.getCurrentRoute());
        List<Integer> interiorOrder = translateMutableInteriorOrder(route, req.getNewOrder());
        Route updated = routeService.reorderPOIs(
                route, interiorOrder, req.getOriginalUserVector());
        return RouteResponse.fromRoute(updated);
    }

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
                        .plannedVisitMin(rpr.getPlannedVisitMin())
                        .fixedAnchor(rpr.isFixedAnchor())
                        .protectedPoint(rpr.isProtectedPoint())
                        .protectionReason(rpr.getProtectionReason());
                if (place == null) {
                    builder.anchorName(rpr.getPoiName())
                            .anchorLatitude(rpr.getLatitude())
                            .anchorLongitude(rpr.getLongitude());
                }
                points.add(builder.build());
            }
        }
        route.setPoints(points);
        return route;
    }

    private List<Integer> translateMutableInteriorOrder(Route route, List<Integer> newOrder) {
        if (newOrder == null) {
            return List.of();
        }

        List<RoutePoint> points = route.getPoints();
        Map<Integer, Integer> routeIndexToMutableIndex = new HashMap<>();

        int mutableCursor = 0;
        for (int routeIndex = 0; routeIndex < points.size(); routeIndex++) {
            if (points.get(routeIndex).getPoi() != null) {
                routeIndexToMutableIndex.put(routeIndex, mutableCursor++);
            }
        }

        List<Integer> translated = new ArrayList<>();
        for (Integer routeIndex : newOrder) {
            Integer mutableIndex = routeIndexToMutableIndex.get(routeIndex);
            if (mutableIndex != null) {
                translated.add(mutableIndex);
            }
        }

        if (!translated.isEmpty()) {
            return translated;
        }
        return new ArrayList<>(newOrder);
    }
}
