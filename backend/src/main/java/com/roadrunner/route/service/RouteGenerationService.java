package com.roadrunner.route.service;

import java.util.ArrayList;
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
 * Core route-generation service using the new weight-based model.
 * Routes are anchored by a hotel at first and last position;
 * interior POIs are selected by per-category quotas derived from
 * the 7 visit-category weights.
 */
@Service
public class RouteGenerationService {

    static final double KIZILAY_LAT = 39.9208;
    static final double KIZILAY_LNG = 32.8541;

    private static final int MIN_RATING_COUNT = 100;

    private final PlaceRepository placeRepository;
    private final RouteScoringService scoring;
    private final PlaceLabelService labelService;

    public RouteGenerationService(PlaceRepository placeRepository,
                                  RouteScoringService scoring,
                                  PlaceLabelService labelService) {
        this.placeRepository = placeRepository;
        this.scoring = scoring;
        this.labelService = labelService;
    }

    record ParsedWeightRequest(
            String requestId,
            String travelMode,
            Double centerLat,
            Double centerLng,
            double parkVeSeyirNoktalari,
            double geceHayati,
            double restoranToleransi,
            double landmark,
            double dogalAlanlar,
            double tarihiAlanlar,
            double kafeTatli,
            double toplamPoiYogunlugu,
            double sparsity,
            double hotelCenterBias,
            double butceSeviyesi) {

        double weightFor(RouteLabel label) {
            return switch (label) {
                case PARK_VE_SEYIR_NOKTALARI -> parkVeSeyirNoktalari;
                case GECE_HAYATI -> geceHayati;
                case RESTORAN_TOLERANSI -> restoranToleransi;
                case LANDMARK -> landmark;
                case DOGAL_ALANLAR -> dogalAlanlar;
                case TARIHI_ALANLAR -> tarihiAlanlar;
                case KAFE_TATLI -> kafeTatli;
                default -> 0.0;
            };
        }

        boolean hasCenterAnchor() {
            return centerLat != null && centerLng != null;
        }

        double effectiveCenterLat() {
            return centerLat != null ? centerLat : KIZILAY_LAT;
        }

        double effectiveCenterLng() {
            return centerLng != null ? centerLng : KIZILAY_LNG;
        }
    }

    private record RouteVariantProfile(int routeIndex,
                                       double quotaExponent,
                                       double explorationFactor,
                                       double overlapPenalty) {
    }

    private enum RouteMode {
        HOTEL_LOOP,
        CENTER_START
    }

    ParsedWeightRequest parseUserVector(Map<String, String> userVector) {
        Map<String, String> uv = userVector != null ? userVector : Map.of();

        String requestId = firstNonBlank(uv.get("requestId"), "req").strip();
        double hotelCenterBias = clamp01(GeoUtils.safeFloat(uv.get("weight_hotelCenterBias"), 0.5));
        String travelMode = firstNonBlank(uv.get("mode"), uv.get("travelMode"), "").strip().toLowerCase();
        if (travelMode.isEmpty()) {
            travelMode = hotelCenterBias >= 0.66 ? "walking" : "driving";
        }

        return new ParsedWeightRequest(
                requestId,
                travelMode,
                parseOptionalCoordinate(uv.get("centerLat")),
                parseOptionalCoordinate(uv.get("centerLng")),
                clamp01(GeoUtils.safeFloat(uv.get("weight_parkVeSeyirNoktalari"), 0.0)),
                clamp01(GeoUtils.safeFloat(uv.get("weight_geceHayati"), 0.0)),
                clamp01(GeoUtils.safeFloat(uv.get("weight_restoranToleransi"), 0.0)),
                clamp01(GeoUtils.safeFloat(uv.get("weight_landmark"), 0.0)),
                clamp01(GeoUtils.safeFloat(uv.get("weight_dogalAlanlar"), 0.0)),
                clamp01(GeoUtils.safeFloat(uv.get("weight_tarihiAlanlar"), 0.0)),
                clamp01(GeoUtils.safeFloat(uv.get("weight_kafeTatli"), 0.0)),
                clamp01(GeoUtils.safeFloat(uv.get("weight_toplamPoiYogunlugu"), 0.5)),
                clamp01(GeoUtils.safeFloat(uv.get("weight_sparsity"), 0.5)),
                hotelCenterBias,
                clamp01(GeoUtils.safeFloat(uv.get("weight_butceSeviyesi"), 0.5))
        );
    }

    public List<Route> generateRoutes(Map<String, String> userVector, int k) {
        return generateRoutes(userVector, true, k);
    }

    public List<Route> generateRoutes(Map<String, String> userVector,
                                      boolean stayAtHotel,
                                      int k) {
        ParsedWeightRequest req = parseUserVector(userVector);
        RouteMode mode = resolveRouteMode(req, stayAtHotel);

        List<Place> allPlaces = placeRepository.findAll();
        List<Place> pool = scoring.buildCandidatePool(allPlaces, MIN_RATING_COUNT);

        int basePointCount = (int) Math.max(3, Math.min(12,
                Math.round(3 + 9 * req.toplamPoiYogunlugu())));
        int poiSelectionCount = mode == RouteMode.CENTER_START
                ? basePointCount
                : basePointCount - 2;

        k = Math.max(1, k);
        List<Route> routes = new ArrayList<>();
        List<Set<String>> priorInteriorIds = new ArrayList<>();
        Set<String> priorHotelIds = new HashSet<>();

        for (int i = 0; i < k; i++) {
            RouteVariantProfile variant = variantFor(i);
            Random rnd = new Random(Objects.hash(req.requestId(), i, "routegen"));
            Place hotel = mode == RouteMode.HOTEL_LOOP
                    ? selectHotel(pool, req, rnd, variant, priorHotelIds)
                    : null;
            if (mode == RouteMode.HOTEL_LOOP && hotel == null) {
                return routes;
            }
            Map<RouteLabel, Integer> quotas = computeCategoryQuotas(req, poiSelectionCount, variant.quotaExponent());

            double anchorLat = mode == RouteMode.CENTER_START ? req.effectiveCenterLat() : hotel.getLatitude();
            double anchorLng = mode == RouteMode.CENTER_START ? req.effectiveCenterLng() : hotel.getLongitude();
            List<Place> interiorPois = selectInteriorPois(pool, quotas, req, hotel, anchorLat, anchorLng, rnd, variant, priorInteriorIds);
            List<Place> orderedInterior = orderNearestNeighbor(interiorPois, anchorLat, anchorLng, req.sparsity());

            Route route = assembleRoute(mode, hotel, orderedInterior, req, i);
            recomputeLocalSegments(route);
            recomputeTotals(route);
            route.setFeasible(isFeasible(route));
            routes.add(route);

            priorInteriorIds.add(collectInteriorIds(route));
            if (hotel != null && hotel.getId() != null) {
                priorHotelIds.add(hotel.getId());
            }
        }

        return routes;
    }

    public Route rerollRoutePoint(Route route, int index,
                                  Map<String, String> indexParams,
                                  Map<String, String> originalUserVector) {
        List<RoutePoint> points = route.getPoints();
        if (index <= 0) {
            return route;
        }
        if (isHotelLoopRoute(route) && index >= points.size() - 1) {
            return route;
        }

        ParsedWeightRequest req = parseUserVector(originalUserVector);
        Place currentPoi = points.get(index).getPoi();
        RouteLabel slotLabel = currentPoi != null ? labelService.label(currentPoi) : RouteLabel.UNKNOWN;

        Set<String> usedIds = collectUsedIds(route);
        if (currentPoi != null && currentPoi.getId() != null) {
            usedIds.remove(currentPoi.getId());
        }

        List<Place> pool = scoring.buildCandidatePool(placeRepository.findAll(), MIN_RATING_COUNT);
        RoutePoint anchorPoint = points.get(0);
        double anchorLat = anchorPoint.effectiveLatitude();
        double anchorLng = anchorPoint.effectiveLongitude();

        Optional<Place> replacement = pickBestCandidate(
                pool,
                slotLabel,
                usedIds,
                req,
                anchorLat,
                anchorLng,
                new Random(Objects.hash(route.getRouteId(), index, req.requestId(), "reroll")),
                variantFor(1),
                List.of()
        );

        if (replacement.isEmpty()) {
            return route;
        }

        points.get(index).assignPOI(replacement.get());
        points.get(index).setPlannedVisitMin(scoring.estimateVisitMinutes(replacement.get()));
        recomputeLocalSegments(route);
        recomputeTotals(route);
        route.setFeasible(isFeasible(route));
        return route;
    }

    public Route insertManualPOI(Route route, int index, String poiId,
                                 Map<String, String> originalUserVector) {
        if (route.getPoints().size() >= maxPointCount(route)) {
            return route;
        }

        Optional<Place> optPlace = placeRepository.findById(poiId);
        if (optPlace.isEmpty()) {
            return route;
        }

        Place place = optPlace.get();
        if (labelService.label(place) == RouteLabel.HOTEL) {
            return route;
        }
        if (collectInteriorIds(route).contains(place.getId())) {
            return route;
        }

        int maxInsertIndex = isHotelLoopRoute(route)
                ? route.getPoints().size() - 1
                : route.getPoints().size();
        int idx = Math.max(1, Math.min(index, maxInsertIndex));
        route.getPoints().add(idx, RoutePoint.builder()
                .index(idx)
                .poi(place)
                .plannedVisitMin(scoring.estimateVisitMinutes(place))
                .build());

        reindexPoints(route);
        recomputeLocalSegments(route);
        recomputeTotals(route);
        route.setFeasible(isFeasible(route));
        return route;
    }

    public Route removePoint(Route route, int index,
                             Map<String, String> originalUserVector) {
        List<RoutePoint> points = route.getPoints();
        if (index <= 0) {
            return route;
        }
        if (isHotelLoopRoute(route) && index >= points.size() - 1) {
            return route;
        }
        if (points.size() <= minPointCount(route)) {
            return route;
        }

        points.remove(index);
        reindexPoints(route);
        recomputeLocalSegments(route);
        recomputeTotals(route);
        route.setFeasible(isFeasible(route));
        return route;
    }

    public Route reorderPOIs(Route route, List<Integer> newInteriorOrder,
                             Map<String, String> originalUserVector) {
        List<RoutePoint> points = route.getPoints();
        if (points.size() < minPointCount(route)) {
            return route;
        }

        int interiorSize = interiorPointCount(route);
        if (newInteriorOrder == null || newInteriorOrder.size() != interiorSize) {
            return route;
        }

        List<RoutePoint> interior = new ArrayList<>(points.subList(1, interiorEndIndex(route)));
        Set<Integer> seen = new HashSet<>();
        List<RoutePoint> reorderedInterior = new ArrayList<>();
        for (int i : newInteriorOrder) {
            if (i < 0 || i >= interior.size() || !seen.add(i)) {
                return route;
            }
            reorderedInterior.add(interior.get(i));
        }

        List<RoutePoint> reorderedPoints = new ArrayList<>();
        reorderedPoints.add(points.get(0));
        reorderedPoints.addAll(reorderedInterior);
        if (isHotelLoopRoute(route)) {
            reorderedPoints.add(points.get(points.size() - 1));
        }

        route.setPoints(reorderedPoints);
        reindexPoints(route);
        recomputeLocalSegments(route);
        recomputeTotals(route);
        route.setFeasible(isFeasible(route));
        return route;
    }

    Place selectHotel(List<Place> pool,
                      ParsedWeightRequest req,
                      Random rnd,
                      RouteVariantProfile variant,
                      Set<String> priorHotelIds) {
        List<Place> hotels = new ArrayList<>();
        for (Place place : pool) {
            if (labelService.label(place) == RouteLabel.HOTEL) {
                hotels.add(place);
            }
        }

        if (hotels.isEmpty()) {
            return null;
        }

        Map<String, Double> scores = new HashMap<>();
        for (Place hotel : hotels) {
            double baseScore = scoring.scoreHotelCandidate(
                    hotel, req.hotelCenterBias(), KIZILAY_LAT, KIZILAY_LNG);
            double overlapPenalty = priorHotelIds.contains(hotel.getId())
                    ? 0.12 + variant.overlapPenalty()
                    : 0.0;
            double noise = deterministicNoise(hotel.getId(), req.requestId(), variant.routeIndex()) * 0.05;
            scores.put(hotel.getId(), baseScore + noise - overlapPenalty);
        }

        List<Place> ranked = new ArrayList<>(hotels);
        ranked.sort(Comparator.comparingDouble((Place hotel) -> scores.getOrDefault(hotel.getId(), 0.0))
                .reversed());

        int bandSize = hotelCandidateBandSize(ranked.size(), variant);
        int selectedIndex = deterministicIndex(bandSize, req.requestId(), variant.routeIndex(), 0, rnd);
        return ranked.get(selectedIndex);
    }

    Map<RouteLabel, Integer> computeCategoryQuotas(ParsedWeightRequest req, int interiorPoiCount) {
        return computeCategoryQuotas(req, interiorPoiCount, 1.0);
    }

    Map<RouteLabel, Integer> computeCategoryQuotas(ParsedWeightRequest req,
                                                   int interiorPoiCount,
                                                   double quotaExponent) {
        RouteLabel[] categories = RouteLabel.VISIT_CATEGORIES;
        double[] weights = new double[categories.length];
        double totalWeight = 0.0;

        for (int i = 0; i < categories.length; i++) {
            double weight = req.weightFor(categories[i]);
            weights[i] = Math.pow(weight, quotaExponent);
            totalWeight += weights[i];
        }

        Map<RouteLabel, Integer> quotas = new HashMap<>();
        if (totalWeight == 0.0) {
            int base = interiorPoiCount / categories.length;
            int remaining = interiorPoiCount % categories.length;
            for (int i = 0; i < categories.length; i++) {
                quotas.put(categories[i], base + (i < remaining ? 1 : 0));
            }
            return quotas;
        }

        double[] rawQuotas = new double[categories.length];
        int[] floors = new int[categories.length];
        int floorSum = 0;
        for (int i = 0; i < categories.length; i++) {
            rawQuotas[i] = interiorPoiCount * (weights[i] / totalWeight);
            floors[i] = (int) Math.floor(rawQuotas[i]);
            floorSum += floors[i];
        }

        Integer[] indices = new Integer[categories.length];
        for (int i = 0; i < categories.length; i++) {
            indices[i] = i;
            quotas.put(categories[i], floors[i]);
        }

        java.util.Arrays.sort(indices, (a, b) -> Double.compare(
                rawQuotas[b] - floors[b],
                rawQuotas[a] - floors[a]));

        int remainingSlots = interiorPoiCount - floorSum;
        for (int i = 0; i < remainingSlots && i < indices.length; i++) {
            RouteLabel label = categories[indices[i]];
            quotas.put(label, quotas.get(label) + 1);
        }

        return quotas;
    }

    private List<Place> selectInteriorPois(List<Place> pool,
                                           Map<RouteLabel, Integer> quotas,
                                           ParsedWeightRequest req,
                                           Place hotel,
                                           double anchorLat,
                                           double anchorLng,
                                           Random rnd,
                                           RouteVariantProfile variant,
                                           List<Set<String>> priorInteriorIds) {
        Set<String> usedIds = new HashSet<>();
        if (hotel != null) {
            usedIds.add(hotel.getId());
        }

        List<Place> selected = new ArrayList<>();

        for (RouteLabel label : RouteLabel.VISIT_CATEGORIES) {
            int count = quotas.getOrDefault(label, 0);
            if (count <= 0) {
                continue;
            }

            List<Place> candidates = buildCandidatesForLabel(pool, usedIds, label);
            List<Place> picked = chooseTopCandidates(
                    candidates, count, req.weightFor(label), req, anchorLat, anchorLng, rnd, variant, priorInteriorIds);
            for (Place place : picked) {
                selected.add(place);
                usedIds.add(place.getId());
            }
        }

        int totalNeeded = quotas.values().stream().mapToInt(Integer::intValue).sum();
        if (selected.size() < totalNeeded) {
            List<Place> fallback = new ArrayList<>();
            for (Place place : pool) {
                if (!usedIds.contains(place.getId()) && labelService.label(place) != RouteLabel.HOTEL) {
                    fallback.add(place);
                }
            }
            List<Place> filler = chooseTopCandidates(
                    fallback,
                    totalNeeded - selected.size(),
                    0.35,
                    req,
                    anchorLat,
                    anchorLng,
                    rnd,
                    variant,
                    priorInteriorIds
            );
            selected.addAll(filler);
        }

        return selected;
    }

    private Optional<Place> pickBestCandidate(List<Place> pool,
                                              RouteLabel label,
                                              Set<String> excludedIds,
                                              ParsedWeightRequest req,
                                              double anchorLat,
                                              double anchorLng,
                                              Random rnd,
                                              RouteVariantProfile variant,
                                              List<Set<String>> priorInteriorIds) {
        List<Place> candidates = buildCandidatesForLabel(pool, excludedIds, label);
        if (candidates.isEmpty()) {
            for (Place place : pool) {
                if (!excludedIds.contains(place.getId()) && labelService.label(place) != RouteLabel.HOTEL) {
                    candidates.add(place);
                }
            }
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        List<Place> picked = chooseTopCandidates(
                candidates, 1, req.weightFor(label), req, anchorLat, anchorLng, rnd, variant, priorInteriorIds);
        return picked.isEmpty() ? Optional.empty() : Optional.of(picked.get(0));
    }

    private List<Place> buildCandidatesForLabel(List<Place> pool,
                                                Set<String> excludedIds,
                                                RouteLabel label) {
        List<Place> candidates = new ArrayList<>();
        for (Place place : pool) {
            if (excludedIds.contains(place.getId())) {
                continue;
            }
            if (labelService.label(place) == label) {
                candidates.add(place);
            }
        }
        return candidates;
    }

    private List<Place> chooseTopCandidates(List<Place> candidates,
                                            int count,
                                            double categoryWeight,
                                            ParsedWeightRequest req,
                                            double anchorLat,
                                            double anchorLng,
                                            Random rnd,
                                            RouteVariantProfile variant,
                                            List<Set<String>> priorInteriorIds) {
        if (count <= 0 || candidates.isEmpty()) {
            return List.of();
        }

        Map<String, Double> scores = new HashMap<>();
        for (Place candidate : candidates) {
            double baseScore = scoring.scoreInteriorCandidate(
                    candidate,
                    categoryWeight,
                    req.butceSeviyesi(),
                    labelService.label(candidate) == RouteLabel.GECE_HAYATI,
                    req.sparsity(),
                    anchorLat,
                    anchorLng,
                    deterministicNoise(candidate.getId(), req.requestId(), variant.routeIndex()));
            double overlapPenalty = seenInPreviousRoutes(candidate.getId(), priorInteriorIds)
                    ? variant.overlapPenalty()
                    : 0.0;
            scores.put(candidate.getId(), baseScore - overlapPenalty);
        }

        List<Place> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator.comparingDouble((Place place) -> scores.getOrDefault(place.getId(), 0.0))
                .reversed());

        List<Place> picked = new ArrayList<>();
        Set<String> localIds = new HashSet<>();
        while (picked.size() < count && !ranked.isEmpty()) {
            int bandSize = candidateBandSize(ranked.size(), categoryWeight, variant);
            int selectedIndex = deterministicIndex(bandSize, req.requestId(), variant.routeIndex(), picked.size(), rnd);
            Place chosen = ranked.remove(selectedIndex);
            if (!localIds.add(chosen.getId())) {
                continue;
            }
            picked.add(chosen);
        }

        return picked;
    }

    private List<Place> orderNearestNeighbor(List<Place> interiorPois,
                                             double startLat,
                                             double startLng,
                                             double sparsity) {
        if (interiorPois.size() <= 1) {
            return new ArrayList<>(interiorPois);
        }

        List<Place> remaining = new ArrayList<>(interiorPois);
        List<Place> ordered = new ArrayList<>();
        double curLat = startLat;
        double curLng = startLng;

        while (!remaining.isEmpty()) {
            final double lat = curLat;
            final double lng = curLng;
            final double hotelLat = startLat;
            final double hotelLng = startLng;

            Place next = remaining.stream()
                    .min(Comparator.comparingDouble(place ->
                            orderingScore(place, lat, lng, hotelLat, hotelLng, sparsity)))
                    .orElse(remaining.get(0));

            ordered.add(next);
            remaining.remove(next);
            curLat = next.getLatitude();
            curLng = next.getLongitude();
        }

        return ordered;
    }

    private Route assembleRoute(RouteMode mode,
                                Place hotel, List<Place> orderedInterior,
                                ParsedWeightRequest req, int routeIndex) {
        Route route = new Route();
        route.setRouteId("route-" + req.requestId() + "-" + routeIndex);
        route.setTravelMode(req.travelMode());

        List<RoutePoint> points = new ArrayList<>();
        int idx = 0;
        if (mode == RouteMode.CENTER_START) {
            points.add(RoutePoint.builder()
                    .index(idx++)
                    .anchorName("Start Point")
                    .anchorLatitude(req.effectiveCenterLat())
                    .anchorLongitude(req.effectiveCenterLng())
                    .plannedVisitMin(0)
                    .build());
        } else {
            points.add(RoutePoint.builder()
                    .index(idx++)
                    .poi(hotel)
                    .plannedVisitMin(scoring.estimateVisitMinutes(hotel))
                    .build());
        }

        for (Place place : orderedInterior) {
            points.add(RoutePoint.builder()
                    .index(idx++)
                    .poi(place)
                    .plannedVisitMin(scoring.estimateVisitMinutes(place))
                    .build());
        }

        if (mode == RouteMode.HOTEL_LOOP) {
            points.add(RoutePoint.builder()
                    .index(idx)
                    .poi(hotel)
                    .plannedVisitMin(scoring.estimateVisitMinutes(hotel))
                    .build());
        }

        route.setPoints(points);
        return route;
    }

    public void recomputeLocalSegments(Route route) {
        route.getSegments().clear();
        List<RoutePoint> points = route.getPoints();
        if (points.size() <= 1) {
            return;
        }

        for (int i = 0; i < points.size() - 1; i++) {
            double km = GeoUtils.haversineKm(
                    points.get(i).effectiveLatitude(), points.get(i).effectiveLongitude(),
                    points.get(i + 1).effectiveLatitude(), points.get(i + 1).effectiveLongitude());
            route.getSegments().add(RouteSegment.builder()
                    .fromIndex(i)
                    .toIndex(i + 1)
                    .durationSec(GeoUtils.travelSeconds(km, route.getTravelMode()))
                    .distanceM(GeoUtils.kmToMeters(km))
                    .build());
        }
    }

    public void recomputeTotals(Route route) {
        int travel = 0;
        double dist = 0.0;
        for (RouteSegment segment : route.getSegments()) {
            travel += segment.getDurationSec();
            dist += segment.getDistanceM();
        }

        int visits = 0;
        for (RoutePoint point : route.getPoints()) {
            visits += point.getPlannedVisitMin() * 60;
        }

        route.setTotalDurationSec(travel + visits);
        route.setTotalDistanceM(dist);
    }

    boolean isFeasible(Route route) {
        List<RoutePoint> points = route.getPoints();
        if (isHotelLoopRoute(route)) {
            return isHotelLoopFeasible(route);
        }
        if (points.size() < 3 || points.size() > 13) {
            return false;
        }
        if (!points.get(0).isCustomAnchor()) {
            return false;
        }

        Set<String> interiorIds = new HashSet<>();
        for (int i = 1; i < points.size(); i++) {
            Place poi = points.get(i).getPoi();
            if (poi == null || labelService.label(poi) == RouteLabel.HOTEL) {
                return false;
            }
            if (!interiorIds.add(poi.getId())) {
                return false;
            }
        }

        if (route.getSegments().size() != points.size() - 1) {
            return false;
        }
        return route.getTotalDurationSec() >= 0 && route.getTotalDistanceM() >= 0.0;
    }

    private Set<String> collectUsedIds(Route route) {
        Set<String> ids = new HashSet<>();
        for (RoutePoint point : route.getPoints()) {
            if (point.getPoi() != null && point.getPoi().getId() != null) {
                ids.add(point.getPoi().getId());
            }
        }
        return ids;
    }

    private void reindexPoints(Route route) {
        List<RoutePoint> points = route.getPoints();
        for (int i = 0; i < points.size(); i++) {
            points.get(i).setIndex(i);
        }
    }

    private Set<String> collectInteriorIds(Route route) {
        Set<String> ids = new HashSet<>();
        List<RoutePoint> points = route.getPoints();
        for (int i = 1; i < interiorEndIndex(route); i++) {
            Place poi = points.get(i).getPoi();
            if (poi != null && poi.getId() != null) {
                ids.add(poi.getId());
            }
        }
        return ids;
    }

    private static double orderingScore(Place place,
                                        double currentLat,
                                        double currentLng,
                                        double anchorLat,
                                        double anchorLng,
                                        double sparsity) {
        double fromCurrent = GeoUtils.haversineKm(currentLat, currentLng,
                place.getLatitude(), place.getLongitude());
        double fromAnchor = GeoUtils.haversineKm(anchorLat, anchorLng,
                place.getLatitude(), place.getLongitude());
        double compactWeight = 1.0 - sparsity;
        double spreadWeight = sparsity;
        return (fromCurrent * (1.05 + (1.10 * compactWeight)))
                - (fromAnchor * 0.04 * spreadWeight);
    }

    private static RouteVariantProfile variantFor(int routeIndex) {
        if (routeIndex <= 0) {
            return new RouteVariantProfile(routeIndex, 1.35, 0.08, 0.00);
        }
        if (routeIndex == 1) {
            return new RouteVariantProfile(routeIndex, 1.00, 0.28, 0.20);
        }
        return new RouteVariantProfile(routeIndex, 0.82, 0.52, 0.38);
    }

    private static int candidateBandSize(int rankedSize,
                                         double categoryWeight,
                                         RouteVariantProfile variant) {
        double ratio = 0.12
                + (variant.explorationFactor() * 0.45)
                + ((1.0 - categoryWeight) * 0.28);
        int bandSize = (int) Math.ceil(rankedSize * Math.min(0.85, ratio));
        return Math.max(1, Math.min(rankedSize, bandSize));
    }

    private static int hotelCandidateBandSize(int rankedSize,
                                              RouteVariantProfile variant) {
        double ratio = 0.25 + (variant.explorationFactor() * 0.40);
        int bandSize = (int) Math.ceil(rankedSize * Math.min(0.60, ratio));
        return Math.max(1, Math.min(rankedSize, bandSize));
    }

    private static boolean seenInPreviousRoutes(String placeId, List<Set<String>> priorInteriorIds) {
        for (Set<String> routeIds : priorInteriorIds) {
            if (routeIds.contains(placeId)) {
                return true;
            }
        }
        return false;
    }

    private static int deterministicIndex(int bandSize,
                                          String requestId,
                                          int routeIndex,
                                          int pickIndex,
                                          Random rnd) {
        if (bandSize <= 1) {
            return 0;
        }
        int seed = Math.abs(Objects.hash(requestId, routeIndex, pickIndex, rnd.nextInt(10_000)));
        return seed % bandSize;
    }

    private static double deterministicNoise(String placeId, String requestId, int routeIndex) {
        int seed = Math.abs(Objects.hash(placeId, requestId, routeIndex, "noise"));
        return (seed % 1000) / 1000.0 * 0.10;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static Double parseOptionalCoordinate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static RouteMode resolveRouteMode(ParsedWeightRequest req, boolean stayAtHotel) {
        if (!stayAtHotel) {
            return RouteMode.CENTER_START;
        }
        return RouteMode.HOTEL_LOOP;
    }

    private boolean isHotelLoopFeasible(Route route) {
        List<RoutePoint> points = route.getPoints();
        if (points.size() < 3 || points.size() > 12) {
            return false;
        }

        Place first = points.get(0).getPoi();
        Place last = points.get(points.size() - 1).getPoi();
        if (first == null || last == null) {
            return false;
        }
        if (!Objects.equals(first.getId(), last.getId())) {
            return false;
        }
        if (labelService.label(first) != RouteLabel.HOTEL) {
            return false;
        }

        Set<String> interiorIds = new HashSet<>();
        for (int i = 1; i < points.size() - 1; i++) {
            Place poi = points.get(i).getPoi();
            if (poi == null || labelService.label(poi) == RouteLabel.HOTEL) {
                return false;
            }
            if (!interiorIds.add(poi.getId())) {
                return false;
            }
        }

        if (route.getSegments().size() != points.size() - 1) {
            return false;
        }
        return route.getTotalDurationSec() >= 0 && route.getTotalDistanceM() >= 0.0;
    }

    private boolean isHotelLoopRoute(Route route) {
        List<RoutePoint> points = route.getPoints();
        if (points.size() < 2) {
            return false;
        }
        RoutePoint first = points.get(0);
        RoutePoint last = points.get(points.size() - 1);
        if (first == null || last == null || first.isCustomAnchor() || last.isCustomAnchor()) {
            return false;
        }
        Place firstPoi = first.getPoi();
        Place lastPoi = last.getPoi();
        return firstPoi != null
                && lastPoi != null
                && Objects.equals(firstPoi.getId(), lastPoi.getId())
                && labelService.label(firstPoi) == RouteLabel.HOTEL;
    }

    private int interiorEndIndex(Route route) {
        return isHotelLoopRoute(route) ? route.getPoints().size() - 1 : route.getPoints().size();
    }

    private int interiorPointCount(Route route) {
        return interiorEndIndex(route) - 1;
    }

    private int maxPointCount(Route route) {
        return isHotelLoopRoute(route) ? 12 : 13;
    }

    private int minPointCount(Route route) {
        return 3;
    }
}
