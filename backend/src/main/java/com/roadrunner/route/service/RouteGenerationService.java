package com.roadrunner.route.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.roadrunner.place.entity.Place;
import com.roadrunner.place.repository.PlaceRepository;
import com.roadrunner.route.entity.Route;
import com.roadrunner.route.entity.RoutePoint;
import com.roadrunner.route.entity.RouteSegment;

/**
 * Core route-generation service — direct port of Python's {@code TripRequest}.
 * Stateless: every method receives all required context as parameters.
 */
@Service
public class RouteGenerationService {

    private final PlaceRepository placeRepository;
    private final RouteScoringService scoring;

    public RouteGenerationService(PlaceRepository placeRepository,
                                  RouteScoringService scoring) {
        this.placeRepository = placeRepository;
        this.scoring = scoring;
    }

    // ------------------------------------------------------------------
    // ParsedRequest — holds all fields parsed from the user vector
    // ------------------------------------------------------------------

    private record ParsedRequest(
            String requestId,
            int maxStops,
            int maxBudgetMin,
            Set<String> mandatoryTypes,
            Map<String, Double> overrideWeights,
            Double centerLat,
            Double centerLng,
            Double radiusKm,
            String travelMode,
            double minRating,
            String maxPriceLevel
    ) {}

    // ------------------------------------------------------------------
    // Parsing & validation
    // ------------------------------------------------------------------

    private ParsedRequest parseUserVector(Map<String, String> userVector) {
        Map<String, String> uv = userVector != null ? userVector : Map.of();

        // requestId
        String requestId = firstNonBlank(uv.get("requestId"),
                                         uv.get("tripId"), "req").strip();

        // maxStops
        int maxStops = Math.max(1, Math.min(
                GeoUtils.safeInt(uv.get("maxStops"), 6), 30));

        // maxBudgetMin
        String budgetRaw = uv.get("maxBudgetMin");
        if (budgetRaw == null || budgetRaw.isBlank()) {
            budgetRaw = uv.get("maxDurationMin");
        }
        int maxBudgetMin = Math.max(1, GeoUtils.safeInt(budgetRaw, 480));

        // mandatoryTypes
        String mand = uv.getOrDefault("mandatoryTypes", "").strip();
        Set<String> mandatoryTypes = new HashSet<>();
        if (!mand.isEmpty()) {
            for (String x : mand.replace(";", ",").split(",")) {
                String t = x.strip().toLowerCase();
                if (!t.isEmpty()) mandatoryTypes.add(t);
            }
        }

        // overrideWeights
        Map<String, Double> overrideWeights = new HashMap<>();
        for (Map.Entry<String, String> entry : uv.entrySet()) {
            if (entry.getKey().startsWith("weight_")) {
                String t = entry.getKey().substring("weight_".length())
                                .strip().toLowerCase();
                double w = GeoUtils.safeFloat(entry.getValue(), 0.0);
                if (!t.isEmpty() && w > 0.0) {
                    overrideWeights.put(t, w);
                }
            }
        }

        // center / radius
        Double centerLat = parseNullableDouble(uv.get("centerLat"));
        Double centerLng = parseNullableDouble(uv.get("centerLng"));
        Double radiusKm  = parseNullableDouble(uv.get("radiusKm"));

        // travelMode
        String travelMode = firstNonBlank(uv.get("mode"),
                uv.get("travelMode"), "driving").strip().toLowerCase();

        // minRating
        double minRating = GeoUtils.safeFloat(uv.get("minRating"), 0.0);

        // maxPriceLevel
        String maxPriceLevel = uv.getOrDefault("maxPriceLevel", "").strip();

        return new ParsedRequest(requestId, maxStops, maxBudgetMin,
                mandatoryTypes, overrideWeights, centerLat, centerLng,
                radiusKm, travelMode, minRating, maxPriceLevel);
    }

    private boolean validate(ParsedRequest req) {
        return req.maxStops() > 0 && req.maxBudgetMin() > 0;
    }

    // ------------------------------------------------------------------
    // Public API: Generate Routes
    // ------------------------------------------------------------------

    /**
     * Generates {@code k} route alternatives from the given user vector.
     * Direct port of Python {@code TripRequest.generateRoutes()}.
     */
    public List<Route> generateRoutes(Map<String, String> userVector, int k) {
        ParsedRequest req = parseUserVector(userVector);
        if (!validate(req)) return List.of();

        List<Place> allPlaces = placeRepository.findAll();
        List<Place> candidatePool = scoring.buildCandidatePool(
                allPlaces, req.centerLat(), req.centerLng(),
                req.radiusKm(), req.minRating());

        k = Math.max(1, k);
        List<Route> routes = new ArrayList<>();

        for (int i = 0; i < k; i++) {
            Random rnd = new Random(
                    Objects.hash(req.requestId(), i, "routegen"));

            List<Place> selected = selectPlaces(candidatePool, req, rnd);
            List<Place> ordered = orderNearestNeighbor(selected, req);

            Route route = new Route();
            route.setRouteId("route-" + req.requestId() + "-" + i);
            route.setTravelMode(req.travelMode());

            List<RoutePoint> points = new ArrayList<>();
            for (int j = 0; j < ordered.size(); j++) {
                Place p = ordered.get(j);
                points.add(RoutePoint.builder()
                        .index(j)
                        .poi(p)
                        .plannedVisitMin(scoring.estimateVisitMinutes(p))
                        .build());
            }
            route.setPoints(points);

            recomputeLocalSegments(route);
            recomputeTotals(route);
            route.setFeasible(isFeasible(route, req));
            routes.add(route);
        }

        return routes;
    }

    // ------------------------------------------------------------------
    // Public API: Route mutations
    // ------------------------------------------------------------------

    /**
     * Rerolls a single POI at the given index.
     * Ported from Python {@code TripRequest.rerollRoutePoint()} + {@code Route.rerollPoint()}.
     */
    public Route rerollRoutePoint(Route route, int index,
                                  Map<String, String> indexParams,
                                  Map<String, String> originalUserVector) {
        if (index < 0 || index >= route.getPoints().size()) return route;

        ParsedRequest req = parseUserVector(originalUserVector);
        Set<String> lockedIds = lockUnchangedPOIs(route, index);

        String desiredType = indexParams != null
                ? indexParams.getOrDefault("type", "") : "";
        if (desiredType.isBlank()) {
            Place cur = route.getPoints().get(index).getPoi();
            if (cur != null) {
                List<String> curTypes = GeoUtils.parseTypesFromEntity(cur.getTypes());
                if (!curTypes.isEmpty()) desiredType = curTypes.get(0);
            }
        }

        double minRating = GeoUtils.safeFloat(
                indexParams != null ? indexParams.get("minRating") : null,
                req.minRating());
        String maxPrice = indexParams != null
                ? indexParams.getOrDefault("maxPriceLevel", req.maxPriceLevel())
                : req.maxPriceLevel();

        List<Place> allPlaces = placeRepository.findAll();
        List<Place> pool = scoring.buildCandidatePool(
                allPlaces, req.centerLat(), req.centerLng(),
                req.radiusKm(), req.minRating());

        Optional<Place> replacement = scoring.pickOnePlace(
                pool, desiredType, lockedIds, minRating, maxPrice,
                req.overrideWeights(), req.centerLat(), req.centerLng());

        if (replacement.isEmpty()) return route;

        route.getPoints().get(index).assignPOI(replacement.get());
        route.getPoints().get(index).setPlannedVisitMin(
                scoring.estimateVisitMinutes(replacement.get()));

        recomputeLocalSegments(route);
        recomputeTotals(route);
        route.setFeasible(isFeasible(route, req));
        return route;
    }

    /**
     * Inserts a manually chosen POI at the given index.
     * Ported from Python {@code Route.insertManualPOI()}.
     */
    public Route insertManualPOI(Route route, int index, String poiId,
                                 Map<String, String> originalUserVector) {
        Optional<Place> optPlace = placeRepository.findById(poiId);
        if (optPlace.isEmpty()) return route;

        Place p = optPlace.get();
        int idx = Math.max(0, Math.min(index, route.getPoints().size()));

        route.getPoints().add(idx, RoutePoint.builder()
                .index(idx)
                .poi(p)
                .plannedVisitMin(45)
                .build());

        reindexPoints(route);
        recomputeLocalSegments(route);
        recomputeTotals(route);

        ParsedRequest req = parseUserVector(originalUserVector);
        route.setFeasible(isFeasible(route, req));
        return route;
    }

    /**
     * Removes the point at the given index.
     * Ported from Python {@code Route.removePoint()}.
     */
    public Route removePoint(Route route, int index,
                             Map<String, String> originalUserVector) {
        if (index < 0 || index >= route.getPoints().size()) return route;

        route.getPoints().remove(index);
        reindexPoints(route);
        recomputeLocalSegments(route);
        recomputeTotals(route);

        ParsedRequest req = parseUserVector(originalUserVector);
        route.setFeasible(isFeasible(route, req));
        return route;
    }

    /**
     * Reorders POIs according to the supplied index permutation.
     * Ported from Python {@code Route.reorderPOIs()}.
     */
    public Route reorderPOIs(Route route, List<Integer> newOrder,
                             Map<String, String> originalUserVector) {
        if (newOrder == null || newOrder.size() != route.getPoints().size())
            return route;

        List<RoutePoint> old = new ArrayList<>(route.getPoints());
        List<RoutePoint> reordered = new ArrayList<>();
        for (int i : newOrder) {
            if (i < 0 || i >= old.size()) return route;
            reordered.add(old.get(i));
        }

        route.setPoints(reordered);
        reindexPoints(route);
        recomputeLocalSegments(route);
        recomputeTotals(route);

        ParsedRequest req = parseUserVector(originalUserVector);
        route.setFeasible(isFeasible(route, req));
        return route;
    }

    // ------------------------------------------------------------------
    // Core algorithms (private)
    // ------------------------------------------------------------------

    /**
     * Selects places for a route from the candidate pool.
     * Ported from Python {@code _select_places()}.
     */
    private List<Place> selectPlaces(List<Place> pool,
                                     ParsedRequest req, Random rnd) {
        if (pool.isEmpty()) return List.of();

        Set<String> used = new HashSet<>();
        List<Place> selected = new ArrayList<>();

        // Phase 1 — mandatory types
        List<String> mand = new ArrayList<>(req.mandatoryTypes());
        Collections.shuffle(mand, rnd);
        for (String t : mand) {
            if (selected.size() >= req.maxStops()) break;
            List<Place> candidates = new ArrayList<>();
            for (Place p : pool) {
                if (!used.contains(p.getId()) && scoring.hasType(p, t)) {
                    candidates.add(p);
                }
            }
            if (candidates.isEmpty()) continue;
            candidates.sort(Comparator.comparingDouble(
                    (Place p) -> scoring.scorePlace(p, req.overrideWeights(),
                            req.centerLat(), req.centerLng())).reversed());
            Place chosen = candidates.get(0);
            selected.add(chosen);
            used.add(chosen.getId());
        }

        // Phase 2 — fill remaining slots
        if (selected.size() < req.maxStops()) {
            List<Place> remaining = new ArrayList<>();
            for (Place p : pool) {
                if (!used.contains(p.getId())) remaining.add(p);
            }
            remaining.sort(Comparator.comparingDouble(
                    (Place p) -> scoring.scorePlace(p, req.overrideWeights(),
                            req.centerLat(), req.centerLng())).reversed());

            int topM = Math.min(
                    Math.max(req.maxStops() * 10, 40), remaining.size());
            List<Place> head = new ArrayList<>(remaining.subList(0, topM));
            Collections.shuffle(head, rnd);

            for (Place p : head) {
                if (selected.size() >= req.maxStops()) break;
                selected.add(p);
                used.add(p.getId());
            }
        }

        // Phase 3 — fallback random fill
        while (selected.size() < req.maxStops() && used.size() < pool.size()) {
            Place p = pool.get(rnd.nextInt(pool.size()));
            if (used.contains(p.getId())) continue;
            selected.add(p);
            used.add(p.getId());
        }

        return selected.subList(0, Math.min(selected.size(), req.maxStops()));
    }

    /**
     * Orders places using nearest-neighbor heuristic.
     * Ported from Python {@code _order_nearest_neighbor()}.
     */
    private List<Place> orderNearestNeighbor(List<Place> places,
                                             ParsedRequest req) {
        if (places.size() <= 2) return new ArrayList<>(places);

        Place start;
        if (req.centerLat() != null && req.centerLng() != null) {
            start = places.stream().min(Comparator.comparingDouble(
                    p -> GeoUtils.haversineKm(req.centerLat(), req.centerLng(),
                            p.getLatitude(), p.getLongitude())
            )).orElse(places.get(0));
        } else {
            start = places.stream().max(Comparator.comparingDouble(
                    p -> scoring.scorePlace(p, req.overrideWeights(),
                            req.centerLat(), req.centerLng())
            )).orElse(places.get(0));
        }

        List<Place> remaining = new ArrayList<>(places);
        remaining.remove(start);

        List<Place> ordered = new ArrayList<>();
        ordered.add(start);
        Place cur = start;

        while (!remaining.isEmpty()) {
            final Place current = cur;
            Place next = remaining.stream().min(Comparator.comparingDouble(
                    p -> GeoUtils.haversineKm(current.getLatitude(), current.getLongitude(),
                            p.getLatitude(), p.getLongitude())
            )).orElse(remaining.get(0));
            ordered.add(next);
            remaining.remove(next);
            cur = next;
        }

        return ordered;
    }

    // ------------------------------------------------------------------
    // Segment & totals computation
    // ------------------------------------------------------------------

    /**
     * Recomputes route segments from consecutive point pairs.
     * Ported from Python {@code Route.recomputeLocalSegments()}.
     */
    public void recomputeLocalSegments(Route route) {
        route.getSegments().clear();
        List<RoutePoint> pts = route.getPoints();
        if (pts.size() <= 1) return;

        for (int i = 0; i < pts.size() - 1; i++) {
            Place a = pts.get(i).getPoi();
            Place b = pts.get(i + 1).getPoi();
            double km = GeoUtils.haversineKm(
                    a.getLatitude(), a.getLongitude(),
                    b.getLatitude(), b.getLongitude());
            route.getSegments().add(RouteSegment.builder()
                    .fromIndex(i)
                    .toIndex(i + 1)
                    .durationSec(GeoUtils.travelSeconds(km, route.getTravelMode()))
                    .distanceM(GeoUtils.kmToMeters(km))
                    .build());
        }
    }

    /**
     * Recomputes total duration and distance for a route.
     * Ported from Python {@code Route.recomputeTotals()}.
     */
    public void recomputeTotals(Route route) {
        int travel = 0;
        double dist = 0.0;
        for (RouteSegment seg : route.getSegments()) {
            travel += seg.getDurationSec();
            dist += seg.getDistanceM();
        }
        int visits = 0;
        for (RoutePoint rp : route.getPoints()) {
            visits += rp.getPlannedVisitMin() * 60;
        }
        route.setTotalDurationSec(travel + visits);
        route.setTotalDistanceM(dist);
    }

    /**
     * Checks route feasibility against constraints.
     * Ported from Python {@code _is_feasible()}.
     */
    private boolean isFeasible(Route route, ParsedRequest req) {
        if (!req.mandatoryTypes().isEmpty()) {
            Set<String> present = new HashSet<>();
            for (RoutePoint rp : route.getPoints()) {
                List<String> types = GeoUtils.parseTypesFromEntity(
                        rp.getPoi().getTypes());
                for (String t : types) {
                    present.add(t.strip().toLowerCase());
                }
            }
            for (String m : req.mandatoryTypes()) {
                if (!present.contains(m)) return false;
            }
        }
        return route.getTotalDurationSec() <= req.maxBudgetMin() * 60;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Set<String> lockUnchangedPOIs(Route route, int index) {
        Set<String> locked = new HashSet<>();
        List<RoutePoint> pts = route.getPoints();
        for (int i = 0; i < pts.size(); i++) {
            if (i == index) continue;
            Place poi = pts.get(i).getPoi();
            if (poi != null && poi.getId() != null) {
                locked.add(poi.getId());
            }
        }
        return locked;
    }

    private void reindexPoints(Route route) {
        List<RoutePoint> pts = route.getPoints();
        for (int i = 0; i < pts.size(); i++) {
            pts.get(i).setIndex(i);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    private static Double parseNullableDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Double.parseDouble(s.strip());
        } catch (Exception e) {
            return null;
        }
    }
}
