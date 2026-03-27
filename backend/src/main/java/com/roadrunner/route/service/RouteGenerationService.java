package com.roadrunner.route.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.roadrunner.place.entity.Place;
import com.roadrunner.place.repository.PlaceRepository;
import com.roadrunner.route.dto.request.RouteCandidateFiltersRequest;
import com.roadrunner.route.entity.Route;
import com.roadrunner.route.entity.RoutePoint;
import com.roadrunner.route.entity.RouteSegment;
import com.roadrunner.route.service.RouteConstraintSpec.BoundaryKind;
import com.roadrunner.route.service.RouteConstraintSpec.BoundaryRequirement;
import com.roadrunner.route.service.RouteConstraintSpec.InteriorRequirement;
import com.roadrunner.route.service.RouteConstraintSpec.InteriorRequirementKind;
import com.roadrunner.route.service.RouteConstraintSpec.MealRequirement;

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

    private record BoundarySelection(
            RoutePoint startPoint,
            RoutePoint endPoint,
            double referenceLat,
            double referenceLng,
            Place anchorHotel) {
    }

    ParsedWeightRequest parseUserVector(Map<String, String> userVector) {
        Map<String, String> uv = userVector != null ? userVector : Map.of();

        String requestId = firstNonBlank(uv.get("requestId"), "req").strip();
        double hotelCenterBias = clamp01(GeoUtils.safeFloat(uv.get("weight_hotelCenterBias"), 0.5));
        String travelMode = firstNonBlank(uv.get("mode"), uv.get("travelMode"));
        if (travelMode.isBlank()) {
            travelMode = "";
        }
        travelMode = travelMode.strip().toLowerCase(Locale.ROOT);
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
        RouteMode mode = stayAtHotel ? RouteMode.HOTEL_LOOP : RouteMode.CENTER_START;

        List<Place> pool = scoring.buildCandidatePool(placeRepository.findAll(), MIN_RATING_COUNT);
        int basePointCount = (int) Math.max(3, Math.min(12,
                Math.round(3 + 9 * req.toplamPoiYogunlugu())));
        int freeInteriorCount = mode == RouteMode.CENTER_START ? basePointCount : basePointCount - 2;

        List<Route> routes = new ArrayList<>();
        List<Set<String>> priorInteriorIds = new ArrayList<>();
        Set<String> priorHotelIds = new HashSet<>();

        for (int i = 0; i < Math.max(1, k); i++) {
            RouteVariantProfile variant = variantFor(i);
            Random rnd = new Random(Objects.hash(req.requestId(), i, "routegen"));

            Place hotel = null;
            if (mode == RouteMode.HOTEL_LOOP) {
                hotel = selectHotel(pool, req, rnd, variant, priorHotelIds, null, null);
                if (hotel == null) {
                    break;
                }
            }

            double anchorLat = mode == RouteMode.CENTER_START
                    ? req.effectiveCenterLat()
                    : hotel.getLatitude();
            double anchorLng = mode == RouteMode.CENTER_START
                    ? req.effectiveCenterLng()
                    : hotel.getLongitude();

            Map<RouteLabel, Integer> quotas = computeCategoryQuotas(req, freeInteriorCount, variant.quotaExponent());
            List<Place> interiorPois = selectInteriorPois(
                    pool,
                    quotas,
                    req,
                    anchorLat,
                    anchorLng,
                    rnd,
                    variant,
                    priorInteriorIds,
                    hotel != null ? Set.of(hotel.getId()) : Set.of());

            List<RoutePoint> orderedInterior = orderNearestNeighbor(
                    interiorPois.stream().map(this::freePoint).toList(),
                    anchorLat,
                    anchorLng,
                    req.sparsity());

            Route route = assembleLegacyRoute(mode, hotel, orderedInterior, req, i);
            finalizeRoute(route);
            routes.add(route);
            priorInteriorIds.add(collectInteriorIds(route));
            if (hotel != null && hotel.getId() != null) {
                priorHotelIds.add(hotel.getId());
            }
        }

        return routes;
    }

    public List<Route> generateRoutes(ResolvedRouteGenerationRequest resolved, int k) {
        if (resolved.legacyMode()) {
            return generateRoutes(resolved.userVector(), resolved.stayAtHotel(), k);
        }
        return generateConstrainedRoutes(resolved.userVector(), resolved.constraintSpec(), k);
    }

    public Route rerollRoutePoint(Route route, int index,
                                  Map<String, String> indexParams,
                                  Map<String, String> originalUserVector) {
        List<RoutePoint> points = route.getPoints();
        if (index < 0 || index >= points.size()) {
            return route;
        }
        if (!isMutableInteriorPoint(route, index)) {
            return route;
        }

        ParsedWeightRequest req = parseUserVector(originalUserVector);
        Place currentPoi = points.get(index).getPoi();
        RouteLabel slotLabel = currentPoi != null ? labelService.label(currentPoi) : RouteLabel.UNKNOWN;

        Set<String> usedIds = collectUsedIds(route);
        if (currentPoi != null && currentPoi.getId() != null) {
            usedIds.remove(currentPoi.getId());
        }

        Optional<Place> replacement = pickBestCandidate(
                scoring.buildCandidatePool(placeRepository.findAll(), MIN_RATING_COUNT),
                slotLabel,
                usedIds,
                req,
                effectiveReferenceLat(route),
                effectiveReferenceLng(route),
                new Random(Objects.hash(route.getRouteId(), index, req.requestId(), "reroll")),
                variantFor(1),
                List.of(),
                null,
                Set.of());

        if (replacement.isEmpty()) {
            return route;
        }

        RoutePoint point = points.get(index);
        point.assignPOI(replacement.get());
        point.setPlannedVisitMin(scoring.estimateVisitMinutes(replacement.get()));
        finalizeRoute(route);
        return route;
    }

    public Route insertManualPOI(Route route, int index, String poiId,
                                 Map<String, String> originalUserVector) {
        Optional<Place> optPlace = placeRepository.findById(poiId);
        if (optPlace.isEmpty()) {
            return route;
        }

        Place place = optPlace.get();
        if (labelService.label(place) == RouteLabel.HOTEL) {
            return route;
        }
        if (collectAllNonBoundaryIds(route).contains(place.getId())) {
            return route;
        }

        int lowerBound = hasFixedStart(route) ? 1 : 0;
        int upperBound = hasFixedEnd(route)
                ? route.getPoints().size() - 1
                : route.getPoints().size();
        int idx = Math.max(lowerBound, Math.min(index, upperBound));

        route.getPoints().add(idx, freePoint(place));
        reindexPoints(route);
        finalizeRoute(route);
        return route;
    }

    public Route removePoint(Route route, int index,
                             Map<String, String> originalUserVector) {
        if (!isMutableInteriorPoint(route, index)) {
            return route;
        }
        route.getPoints().remove(index);
        reindexPoints(route);
        finalizeRoute(route);
        return route;
    }

    public Route reorderPOIs(Route route, List<Integer> newInteriorOrder,
                             Map<String, String> originalUserVector) {
        List<RoutePoint> points = route.getPoints();
        if (points.isEmpty()) {
            return route;
        }

        int startIdx = hasFixedStart(route) ? 1 : 0;
        int endExclusive = hasFixedEnd(route) ? points.size() - 1 : points.size();
        if (startIdx >= endExclusive) {
            return route;
        }

        List<RoutePoint> interior = new ArrayList<>(points.subList(startIdx, endExclusive));
        List<Integer> mutableInteriorIndices = new ArrayList<>();
        for (int i = 0; i < interior.size(); i++) {
            if (!interior.get(i).isProtectedPoint()) {
                mutableInteriorIndices.add(i);
            }
        }
        if (mutableInteriorIndices.size() <= 1) {
            return route;
        }

        List<Integer> normalizedOrder = normalizeInteriorOrder(newInteriorOrder, mutableInteriorIndices.size());
        if (normalizedOrder.size() != mutableInteriorIndices.size()) {
            return route;
        }

        List<RoutePoint> mutablePoints = new ArrayList<>();
        for (int idx : mutableInteriorIndices) {
            mutablePoints.add(interior.get(idx));
        }

        List<RoutePoint> reorderedMutable = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (int idx : normalizedOrder) {
            if (idx < 0 || idx >= mutablePoints.size() || !seen.add(idx)) {
                return route;
            }
            reorderedMutable.add(mutablePoints.get(idx));
        }

        List<RoutePoint> reorderedInterior = new ArrayList<>(interior);
        for (int i = 0; i < mutableInteriorIndices.size(); i++) {
            reorderedInterior.set(mutableInteriorIndices.get(i), reorderedMutable.get(i));
        }

        List<RoutePoint> rebuilt = new ArrayList<>();
        if (hasFixedStart(route)) {
            rebuilt.add(points.get(0));
        }
        rebuilt.addAll(reorderedInterior);
        if (hasFixedEnd(route)) {
            rebuilt.add(points.get(points.size() - 1));
        }

        route.setPoints(rebuilt);
        reindexPoints(route);
        finalizeRoute(route);
        return route;
    }

    Place selectHotel(List<Place> pool,
                      ParsedWeightRequest req,
                      Random rnd,
                      RouteVariantProfile variant,
                      Set<String> priorHotelIds,
                      String fixedHotelId,
                      RouteCandidateFiltersRequest filters) {
        if (fixedHotelId != null && !fixedHotelId.isBlank()) {
            Place fixedHotel = placeRepository.findById(fixedHotelId).orElse(null);
            if (fixedHotel == null || labelService.label(fixedHotel) != RouteLabel.HOTEL) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fixed hotel anchor could not be resolved");
            }
            if (!matchesFilters(fixedHotel, filters)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fixed hotel anchor does not satisfy hotel filters");
            }
            return fixedHotel;
        }

        List<Place> hotels = new ArrayList<>();
        for (Place place : pool) {
            if (labelService.label(place) == RouteLabel.HOTEL && matchesFilters(place, filters)) {
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
        Map<RouteLabel, Integer> quotas = new HashMap<>();
        for (RouteLabel category : categories) {
            quotas.put(category, 0);
        }
        if (interiorPoiCount <= 0) {
            return quotas;
        }

        double[] weights = new double[categories.length];
        double totalWeight = 0.0;
        for (int i = 0; i < categories.length; i++) {
            double weight = req.weightFor(categories[i]);
            weights[i] = Math.pow(weight, quotaExponent);
            totalWeight += weights[i];
        }

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
            quotas.put(categories[i], floors[i]);
        }

        Integer[] indices = new Integer[categories.length];
        for (int i = 0; i < categories.length; i++) {
            indices[i] = i;
        }
        Arrays.sort(indices, (a, b) -> Double.compare(
                rawQuotas[b] - floors[b],
                rawQuotas[a] - floors[a]));

        int remainingSlots = interiorPoiCount - floorSum;
        for (int i = 0; i < remainingSlots && i < indices.length; i++) {
            RouteLabel label = categories[indices[i]];
            quotas.put(label, quotas.get(label) + 1);
        }

        return quotas;
    }

    private List<Route> generateConstrainedRoutes(Map<String, String> userVector,
                                                  RouteConstraintSpec spec,
                                                  int k) {
        ParsedWeightRequest req = parseUserVector(userVector);
        List<Place> pool = scoring.buildCandidatePool(placeRepository.findAll(), MIN_RATING_COUNT);
        List<Route> routes = new ArrayList<>();
        List<Set<String>> priorInteriorIds = new ArrayList<>();
        Set<String> priorHotelIds = new HashSet<>();

        for (int i = 0; i < Math.max(1, k); i++) {
            RouteVariantProfile variant = variantFor(i);
            Random rnd = new Random(Objects.hash(req.requestId(), i, "route-constraint"));
            BoundarySelection boundaries = resolveBoundaries(spec, pool, req, rnd, variant, priorHotelIds, i);

            Set<String> usedIds = new LinkedHashSet<>();
            addUsedId(usedIds, boundaries.startPoint());
            addUsedId(usedIds, boundaries.endPoint());

            List<RoutePoint> protectedInterior = resolveProtectedInteriorPoints(
                    spec,
                    pool,
                    req,
                    boundaries.referenceLat(),
                    boundaries.referenceLng(),
                    variant,
                    priorInteriorIds,
                    usedIds,
                    i);

            Map<RouteLabel, Integer> quotas = computeCategoryQuotas(
                    req, spec.freeInteriorCount(), variant.quotaExponent());
            List<Place> freeInteriorPlaces = selectInteriorPois(
                    pool,
                    quotas,
                    req,
                    boundaries.referenceLat(),
                    boundaries.referenceLng(),
                    rnd,
                    variant,
                    priorInteriorIds,
                    usedIds);

            if (freeInteriorPlaces.size() < spec.freeInteriorCount()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Could not satisfy requested free interior visit count");
            }

            List<RoutePoint> interiorPoints = new ArrayList<>(protectedInterior);
            for (Place place : freeInteriorPlaces) {
                if (!usedIds.add(place.getId())) {
                    continue;
                }
                interiorPoints.add(freePoint(place));
            }

            List<RoutePoint> orderedInterior = orderNearestNeighbor(
                    interiorPoints,
                    boundaries.referenceLat(),
                    boundaries.referenceLng(),
                    req.sparsity());

            Route route = assembleConstrainedRoute(boundaries, orderedInterior, req, i);
            finalizeRoute(route);
            routes.add(route);
            priorInteriorIds.add(collectInteriorIds(route));
            if (boundaries.anchorHotel() != null && boundaries.anchorHotel().getId() != null) {
                priorHotelIds.add(boundaries.anchorHotel().getId());
            }
        }

        return routes;
    }

    private BoundarySelection resolveBoundaries(RouteConstraintSpec spec,
                                                List<Place> pool,
                                                ParsedWeightRequest req,
                                                Random rnd,
                                                RouteVariantProfile variant,
                                                Set<String> priorHotelIds,
                                                int routeIndex) {
        Place selectedHotel = null;
        if (spec.sameHotelLoop()
                || spec.startBoundary().kind() == BoundaryKind.HOTEL
                || spec.endBoundary().kind() == BoundaryKind.HOTEL) {
            BoundaryRequirement hotelBoundary = spec.startBoundary().kind() == BoundaryKind.HOTEL
                    ? spec.startBoundary()
                    : spec.endBoundary();
            selectedHotel = selectHotel(
                    pool,
                    req,
                    rnd,
                    variant,
                    priorHotelIds,
                    hotelBoundary.placeId(),
                    hotelBoundary.filters());
            if (selectedHotel == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not resolve hotel anchor");
            }
        }

        RoutePoint startPoint = resolveBoundaryPoint(spec.startBoundary(), selectedHotel, pool, req, routeIndex, true);
        RoutePoint endPoint = resolveBoundaryPoint(spec.endBoundary(), selectedHotel, pool, req, routeIndex, false);

        double referenceLat = startPoint != null ? startPoint.effectiveLatitude()
                : (endPoint != null ? endPoint.effectiveLatitude() : req.effectiveCenterLat());
        double referenceLng = startPoint != null ? startPoint.effectiveLongitude()
                : (endPoint != null ? endPoint.effectiveLongitude() : req.effectiveCenterLng());

        return new BoundarySelection(startPoint, endPoint, referenceLat, referenceLng, selectedHotel);
    }

    private RoutePoint resolveBoundaryPoint(BoundaryRequirement boundary,
                                            Place selectedHotel,
                                            List<Place> pool,
                                            ParsedWeightRequest req,
                                            int routeIndex,
                                            boolean startSide) {
        if (boundary == null || boundary.kind() == BoundaryKind.NONE) {
            return null;
        }
        if (boundary.kind() == BoundaryKind.HOTEL) {
            if (selectedHotel == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hotel anchor could not be resolved");
            }
            return fixedPoiPoint(selectedHotel, startSide ? "start-anchor:hotel" : "end-anchor:hotel");
        }

        Place boundaryPlace = resolveBoundaryPlace(boundary, pool, req, routeIndex, startSide);
        return fixedPoiPoint(boundaryPlace, startSide ? "start-anchor:poi" : "end-anchor:poi");
    }

    private Place resolveBoundaryPlace(BoundaryRequirement boundary,
                                       List<Place> pool,
                                       ParsedWeightRequest req,
                                       int routeIndex,
                                       boolean startSide) {
        if (boundary.kind() == BoundaryKind.PLACE) {
            Place place = placeRepository.findById(boundary.placeId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Boundary place could not be resolved"));
            if (!matchesFilters(place, boundary.filters())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Boundary place does not satisfy filters");
            }
            return place;
        }

        Optional<Place> candidate = pool.stream()
                .filter(place -> matchesRequestedType(place, boundary.poiType()))
                .filter(place -> matchesFilters(place, boundary.filters()))
                .sorted(Comparator.comparingDouble((Place place) -> scoreBoundaryPlace(place, req, routeIndex, startSide)).reversed())
                .findFirst();

        return candidate.orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Boundary TYPE anchor could not be resolved"));
    }

    private double scoreBoundaryPlace(Place place, ParsedWeightRequest req, int routeIndex, boolean startSide) {
        double base = scoring.qualityConfidenceScore(place);
        double distancePenalty = GeoUtils.haversineKm(
                req.effectiveCenterLat(),
                req.effectiveCenterLng(),
                place.getLatitude(),
                place.getLongitude()) * 0.02;
        double sideNoise = deterministicNoise(place.getId(), req.requestId(), startSide ? routeIndex : routeIndex + 31) * 0.05;
        return base + sideNoise - distancePenalty;
    }

    private List<RoutePoint> resolveProtectedInteriorPoints(RouteConstraintSpec spec,
                                                            List<Place> pool,
                                                            ParsedWeightRequest req,
                                                            double anchorLat,
                                                            double anchorLng,
                                                            RouteVariantProfile variant,
                                                            List<Set<String>> priorInteriorIds,
                                                            Set<String> usedIds,
                                                            int routeIndex) {
        List<RoutePoint> protectedPoints = new ArrayList<>();
        Map<MealRequirement, String> mealAssignments = new EnumMap<>(MealRequirement.class);

        for (InteriorRequirement requirement : spec.hardSlots()) {
            Place place = resolveInteriorRequirementPlace(
                    requirement, pool, req, anchorLat, anchorLng, variant, priorInteriorIds, usedIds, routeIndex);
            usedIds.add(place.getId());
            RoutePoint point = freePoint(place);
            point.setProtectedPoint(true);
            point.setProtectionReason(requirement.kind() == InteriorRequirementKind.PLACE ? "slot:place" : "slot:type");
            protectedPoints.add(point);
        }

        for (MealRequirement meal : spec.mealRequirements()) {
            RoutePoint existing = findReusableMealPoint(protectedPoints, meal, mealAssignments);
            if (existing != null) {
                mealAssignments.put(meal, existing.getPoi().getId());
                existing.setProtectionReason(appendProtectionReason(existing.getProtectionReason(), "meal:" + meal.name().toLowerCase(Locale.ROOT)));
                continue;
            }

            Place mealPlace = resolveMealPlace(
                    meal, pool, req, anchorLat, anchorLng, variant, priorInteriorIds, usedIds, routeIndex, mealAssignments);
            usedIds.add(mealPlace.getId());
            RoutePoint point = freePoint(mealPlace);
            point.setProtectedPoint(true);
            point.setProtectionReason("meal:" + meal.name().toLowerCase(Locale.ROOT));
            protectedPoints.add(point);
            mealAssignments.put(meal, mealPlace.getId());
        }

        return protectedPoints;
    }

    private Place resolveInteriorRequirementPlace(InteriorRequirement requirement,
                                                  List<Place> pool,
                                                  ParsedWeightRequest req,
                                                  double anchorLat,
                                                  double anchorLng,
                                                  RouteVariantProfile variant,
                                                  List<Set<String>> priorInteriorIds,
                                                  Set<String> usedIds,
                                                  int routeIndex) {
        if (requirement.kind() == InteriorRequirementKind.PLACE) {
            Place place = placeRepository.findById(requirement.placeId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Required POI slot could not be resolved"));
            if (labelService.label(place) == RouteLabel.HOTEL) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hotel points cannot be used as interior POIs");
            }
            if (!matchesFilters(place, requirement.filters())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Required POI slot does not satisfy filters");
            }
            if (usedIds.contains(place.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Required POI slot duplicates an existing route point");
            }
            return place;
        }

        RouteLabel preferredLabel = inferRequestedRouteLabel(requirement.poiType());
        Optional<Place> match = pickBestTypedCandidate(
                pool,
                requirement.poiType(),
                preferredLabel,
                requirement.filters(),
                usedIds,
                req,
                anchorLat,
                anchorLng,
                variant,
                priorInteriorIds,
                routeIndex,
                Set.of());

        return match.orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Required TYPE slot could not be resolved"));
    }

    private Place resolveMealPlace(MealRequirement meal,
                                   List<Place> pool,
                                   ParsedWeightRequest req,
                                   double anchorLat,
                                   double anchorLng,
                                   RouteVariantProfile variant,
                                   List<Set<String>> priorInteriorIds,
                                   Set<String> usedIds,
                                   int routeIndex,
                                   Map<MealRequirement, String> mealAssignments) {
        Set<String> disallowedIds = new HashSet<>();
        if (meal == MealRequirement.LUNCH && mealAssignments.containsKey(MealRequirement.DINNER)) {
            disallowedIds.add(mealAssignments.get(MealRequirement.DINNER));
        }
        if (meal == MealRequirement.DINNER && mealAssignments.containsKey(MealRequirement.LUNCH)) {
            disallowedIds.add(mealAssignments.get(MealRequirement.LUNCH));
        }

        return pool.stream()
                .filter(place -> !usedIds.contains(place.getId()))
                .filter(place -> !disallowedIds.contains(place.getId()))
                .filter(place -> labelService.label(place) != RouteLabel.HOTEL)
                .filter(place -> matchesMealRequirement(place, meal))
                .max(Comparator.comparingDouble(place -> scoreMealCandidate(
                        place, meal, req, anchorLat, anchorLng, variant, priorInteriorIds, routeIndex)))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Could not satisfy " + meal.name().toLowerCase(Locale.ROOT) + " requirement"));
    }

    private double scoreMealCandidate(Place place,
                                      MealRequirement meal,
                                      ParsedWeightRequest req,
                                      double anchorLat,
                                      double anchorLng,
                                      RouteVariantProfile variant,
                                      List<Set<String>> priorInteriorIds,
                                      int routeIndex) {
        RouteLabel label = labelService.label(place);
        double categoryWeight = switch (meal) {
            case BREAKFAST -> Math.max(req.weightFor(RouteLabel.KAFE_TATLI), req.weightFor(RouteLabel.RESTORAN_TOLERANSI));
            case LUNCH, DINNER -> req.weightFor(RouteLabel.RESTORAN_TOLERANSI);
        };
        double score = scoring.scoreInteriorCandidate(
                place,
                label,
                categoryWeight,
                req.butceSeviyesi(),
                req.sparsity(),
                anchorLat,
                anchorLng,
                deterministicNoise(place.getId(), req.requestId(), routeIndex));
        score -= overlapPenalty(place.getId(), priorInteriorIds) * (0.06 + variant.overlapPenalty());
        return score;
    }

    private RoutePoint findReusableMealPoint(List<RoutePoint> protectedPoints,
                                             MealRequirement meal,
                                             Map<MealRequirement, String> mealAssignments) {
        for (RoutePoint point : protectedPoints) {
            Place poi = point.getPoi();
            if (poi == null || !matchesMealRequirement(poi, meal)) {
                continue;
            }
            if ((meal == MealRequirement.LUNCH || meal == MealRequirement.DINNER)
                    && mealAssignments.containsValue(poi.getId())) {
                continue;
            }
            return point;
        }
        return null;
    }

    private List<Place> selectInteriorPois(List<Place> pool,
                                           Map<RouteLabel, Integer> quotas,
                                           ParsedWeightRequest req,
                                           double anchorLat,
                                           double anchorLng,
                                           Random rnd,
                                           RouteVariantProfile variant,
                                           List<Set<String>> priorInteriorIds,
                                           Set<String> excludedIds) {
        List<Place> selections = new ArrayList<>();
        Set<String> usedIds = new LinkedHashSet<>(excludedIds);

        for (RouteLabel label : RouteLabel.VISIT_CATEGORIES) {
            int quota = quotas.getOrDefault(label, 0);
            for (int i = 0; i < quota; i++) {
                Optional<Place> candidate = pickBestCandidate(
                        pool,
                        label,
                        usedIds,
                        req,
                        anchorLat,
                        anchorLng,
                        rnd,
                        variant,
                        priorInteriorIds,
                        null,
                        Set.of());
                if (candidate.isEmpty()) {
                    break;
                }
                Place place = candidate.get();
                usedIds.add(place.getId());
                selections.add(place);
            }
        }

        int targetCount = quotas.values().stream().mapToInt(Integer::intValue).sum();
        while (selections.size() < targetCount) {
            Optional<Place> fallback = pickBestAnyVisitCandidate(
                    pool, usedIds, req, anchorLat, anchorLng, rnd, variant, priorInteriorIds, Set.of());
            if (fallback.isEmpty()) {
                break;
            }
            Place place = fallback.get();
            usedIds.add(place.getId());
            selections.add(place);
        }

        return selections;
    }

    private Optional<Place> pickBestAnyVisitCandidate(List<Place> pool,
                                                      Set<String> usedIds,
                                                      ParsedWeightRequest req,
                                                      double anchorLat,
                                                      double anchorLng,
                                                      Random rnd,
                                                      RouteVariantProfile variant,
                                                      List<Set<String>> priorInteriorIds,
                                                      Set<String> extraExcluded) {
        List<Place> candidates = new ArrayList<>();
        for (Place place : pool) {
            if (usedIds.contains(place.getId()) || extraExcluded.contains(place.getId())) {
                continue;
            }
            RouteLabel label = labelService.label(place);
            if (!label.isVisitCategory()) {
                continue;
            }
            candidates.add(place);
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        candidates.sort(Comparator.comparingDouble((Place place) -> scoreVisitCandidate(
                place, labelService.label(place), req, anchorLat, anchorLng, variant, priorInteriorIds)).reversed());
        int bandSize = Math.max(1, Math.min(candidates.size(), 1 + variant.routeIndex()));
        int index = deterministicIndex(bandSize, req.requestId(), variant.routeIndex(), 91, rnd);
        return Optional.of(candidates.get(index));
    }

    private Optional<Place> pickBestCandidate(List<Place> pool,
                                              RouteLabel label,
                                              Set<String> usedIds,
                                              ParsedWeightRequest req,
                                              double anchorLat,
                                              double anchorLng,
                                              Random rnd,
                                              RouteVariantProfile variant,
                                              List<Set<String>> priorInteriorIds,
                                              RouteCandidateFiltersRequest filters,
                                              Set<String> extraExcluded) {
        List<Place> candidates = new ArrayList<>();
        for (Place place : pool) {
            if (usedIds.contains(place.getId()) || extraExcluded.contains(place.getId())) {
                continue;
            }
            RouteLabel candidateLabel = labelService.label(place);
            if (candidateLabel != label || !label.isVisitCategory()) {
                continue;
            }
            if (!matchesFilters(place, filters)) {
                continue;
            }
            candidates.add(place);
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        candidates.sort(Comparator.comparingDouble((Place place) -> scoreVisitCandidate(
                place, label, req, anchorLat, anchorLng, variant, priorInteriorIds)).reversed());
        int bandSize = Math.max(1, Math.min(candidates.size(), 1 + variant.routeIndex()));
        int selectedIndex = deterministicIndex(bandSize, req.requestId(), variant.routeIndex(),
                label.ordinal(), rnd);
        return Optional.of(candidates.get(selectedIndex));
    }

    private Optional<Place> pickBestTypedCandidate(List<Place> pool,
                                                   String poiType,
                                                   RouteLabel preferredLabel,
                                                   RouteCandidateFiltersRequest filters,
                                                   Set<String> usedIds,
                                                   ParsedWeightRequest req,
                                                   double anchorLat,
                                                   double anchorLng,
                                                   RouteVariantProfile variant,
                                                   List<Set<String>> priorInteriorIds,
                                                   int routeIndex,
                                                   Set<String> extraExcluded) {
        List<Place> candidates = new ArrayList<>();
        for (Place place : pool) {
            if (usedIds.contains(place.getId()) || extraExcluded.contains(place.getId())) {
                continue;
            }
            if (labelService.label(place) == RouteLabel.HOTEL) {
                continue;
            }
            if (!matchesRequestedType(place, poiType) || !matchesFilters(place, filters)) {
                continue;
            }
            candidates.add(place);
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        candidates.sort(Comparator.comparingDouble((Place place) -> {
            RouteLabel label = preferredLabel != null ? preferredLabel : labelService.label(place);
            double baseScore = scoreVisitCandidate(place, label, req, anchorLat, anchorLng, variant, priorInteriorIds);
            return baseScore + deterministicNoise(place.getId(), req.requestId(), routeIndex) * 0.03;
        }).reversed());
        return Optional.of(candidates.get(0));
    }

    private double scoreVisitCandidate(Place place,
                                       RouteLabel label,
                                       ParsedWeightRequest req,
                                       double anchorLat,
                                       double anchorLng,
                                       RouteVariantProfile variant,
                                       List<Set<String>> priorInteriorIds) {
        double score = scoring.scoreInteriorCandidate(
                place,
                label,
                req.weightFor(label),
                req.butceSeviyesi(),
                req.sparsity(),
                anchorLat,
                anchorLng,
                deterministicNoise(place.getId(), req.requestId(), variant.routeIndex()) * variant.explorationFactor());
        score -= overlapPenalty(place.getId(), priorInteriorIds) * (0.08 + variant.overlapPenalty());
        return score;
    }

    private List<RoutePoint> orderNearestNeighbor(List<RoutePoint> points,
                                                  double anchorLat,
                                                  double anchorLng,
                                                  double sparsity) {
        List<RoutePoint> remaining = new ArrayList<>(points);
        List<RoutePoint> ordered = new ArrayList<>();
        double currentLat = anchorLat;
        double currentLng = anchorLng;

        while (!remaining.isEmpty()) {
            int nextIndex = 0;
            double nextScore = Double.POSITIVE_INFINITY;
            for (int i = 0; i < remaining.size(); i++) {
                RoutePoint candidate = remaining.get(i);
                double distance = GeoUtils.haversineKm(
                        currentLat,
                        currentLng,
                        candidate.effectiveLatitude(),
                        candidate.effectiveLongitude());
                double anchorDistance = GeoUtils.haversineKm(
                        anchorLat,
                        anchorLng,
                        candidate.effectiveLatitude(),
                        candidate.effectiveLongitude());
                double score = (distance * (1.0 - (0.65 * sparsity))) + (anchorDistance * 0.15 * sparsity);
                if (score < nextScore) {
                    nextScore = score;
                    nextIndex = i;
                }
            }
            RoutePoint selected = remaining.remove(nextIndex);
            ordered.add(selected);
            currentLat = selected.effectiveLatitude();
            currentLng = selected.effectiveLongitude();
        }

        return ordered;
    }

    private Route assembleLegacyRoute(RouteMode mode,
                                      Place hotel,
                                      List<RoutePoint> orderedInterior,
                                      ParsedWeightRequest req,
                                      int routeIndex) {
        List<RoutePoint> points = new ArrayList<>();
        if (mode == RouteMode.HOTEL_LOOP) {
            points.add(fixedPoiPoint(hotel, "start-anchor:hotel"));
            points.addAll(orderedInterior);
            points.add(fixedPoiPoint(hotel, "end-anchor:hotel"));
        } else {
            points.add(fixedCustomAnchor("City Center", req.effectiveCenterLat(), req.effectiveCenterLng()));
            points.addAll(orderedInterior);
        }

        Route route = new Route();
        route.setRouteId(buildRouteId(req.requestId(), routeIndex));
        route.setTravelMode(req.travelMode());
        route.setPoints(points);
        return route;
    }

    private Route assembleConstrainedRoute(BoundarySelection boundaries,
                                           List<RoutePoint> orderedInterior,
                                           ParsedWeightRequest req,
                                           int routeIndex) {
        List<RoutePoint> points = new ArrayList<>();
        if (boundaries.startPoint() != null) {
            points.add(boundaries.startPoint());
        }
        points.addAll(orderedInterior);
        if (boundaries.endPoint() != null) {
            points.add(boundaries.endPoint());
        }
        if (points.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resolved route is empty");
        }

        Route route = new Route();
        route.setRouteId(buildRouteId(req.requestId(), routeIndex));
        route.setTravelMode(req.travelMode());
        route.setPoints(points);
        return route;
    }

    private Route finalizeRoute(Route route) {
        reindexPoints(route);
        recomputeLocalSegments(route);
        recomputeTotals(route);
        route.setFeasible(isFeasible(route));
        return route;
    }

    private void reindexPoints(Route route) {
        for (int i = 0; i < route.getPoints().size(); i++) {
            route.getPoints().get(i).setIndex(i);
        }
    }

    private void recomputeLocalSegments(Route route) {
        List<RouteSegment> segments = new ArrayList<>();
        List<RoutePoint> points = route.getPoints();
        for (int i = 0; i < points.size() - 1; i++) {
            RoutePoint from = points.get(i);
            RoutePoint to = points.get(i + 1);
            double distanceKm = GeoUtils.haversineKm(
                    from.effectiveLatitude(),
                    from.effectiveLongitude(),
                    to.effectiveLatitude(),
                    to.effectiveLongitude());
            segments.add(RouteSegment.builder()
                    .fromIndex(i)
                    .toIndex(i + 1)
                    .distanceM(GeoUtils.kmToMeters(distanceKm))
                    .durationSec(GeoUtils.travelSeconds(distanceKm, route.getTravelMode()))
                    .build());
        }
        route.setSegments(segments);
    }

    private void recomputeTotals(Route route) {
        int totalDurationSec = 0;
        double totalDistanceM = 0.0;
        for (RouteSegment segment : route.getSegments()) {
            totalDurationSec += segment.getDurationSec();
            totalDistanceM += segment.getDistanceM();
        }
        for (RoutePoint point : route.getPoints()) {
            totalDurationSec += Math.max(0, point.getPlannedVisitMin()) * 60;
        }
        route.setTotalDurationSec(totalDurationSec);
        route.setTotalDistanceM(totalDistanceM);
    }

    private boolean isFeasible(Route route) {
        List<RoutePoint> points = route.getPoints();
        if (points == null || points.isEmpty()) {
            return false;
        }
        if (route.getSegments().size() != Math.max(0, points.size() - 1)) {
            return false;
        }
        if (route.getTotalDurationSec() < 0 || route.getTotalDistanceM() < 0.0) {
            return false;
        }

        Set<String> interiorIds = new HashSet<>();
        int startIdx = hasFixedStart(route) ? 1 : 0;
        int endExclusive = hasFixedEnd(route) ? points.size() - 1 : points.size();
        for (int i = startIdx; i < endExclusive; i++) {
            Place poi = points.get(i).getPoi();
            if (poi == null) {
                continue;
            }
            if (labelService.label(poi) == RouteLabel.HOTEL) {
                return false;
            }
            if (!interiorIds.add(poi.getId())) {
                return false;
            }
        }

        if (isHotelLoopRoute(route)) {
            boolean hasProtectedExpansion = points.stream().anyMatch(RoutePoint::isProtectedPoint);
            if (!hasProtectedExpansion && (points.size() < 3 || points.size() > 12)) {
                return false;
            }
        }
        return true;
    }

    private RoutePoint freePoint(Place place) {
        return RoutePoint.builder()
                .poi(place)
                .plannedVisitMin(scoring.estimateVisitMinutes(place))
                .build();
    }

    private RoutePoint fixedPoiPoint(Place place, String reason) {
        return RoutePoint.builder()
                .poi(place)
                .plannedVisitMin(scoring.estimateVisitMinutes(place))
                .fixedAnchor(true)
                .protectedPoint(true)
                .protectionReason(reason)
                .build();
    }

    private RoutePoint fixedCustomAnchor(String name, double lat, double lng) {
        return RoutePoint.builder()
                .anchorName(name)
                .anchorLatitude(lat)
                .anchorLongitude(lng)
                .plannedVisitMin(0)
                .fixedAnchor(true)
                .protectedPoint(true)
                .protectionReason("anchor:custom")
                .build();
    }

    private boolean hasFixedStart(Route route) {
        return route.getPoints() != null
                && !route.getPoints().isEmpty()
                && route.getPoints().get(0).isFixedAnchor();
    }

    private boolean hasFixedEnd(Route route) {
        return route.getPoints() != null
                && !route.getPoints().isEmpty()
                && route.getPoints().get(route.getPoints().size() - 1).isFixedAnchor();
    }

    private boolean isMutableInteriorPoint(Route route, int index) {
        if (index < 0 || index >= route.getPoints().size()) {
            return false;
        }
        if (hasFixedStart(route) && index == 0) {
            return false;
        }
        if (hasFixedEnd(route) && index == route.getPoints().size() - 1) {
            return false;
        }
        return !route.getPoints().get(index).isProtectedPoint();
    }

    private Set<String> collectUsedIds(Route route) {
        Set<String> ids = new HashSet<>();
        if (route.getPoints() == null) {
            return ids;
        }
        for (RoutePoint point : route.getPoints()) {
            addUsedId(ids, point);
        }
        return ids;
    }

    private Set<String> collectAllNonBoundaryIds(Route route) {
        Set<String> ids = new HashSet<>();
        List<RoutePoint> points = route.getPoints();
        int startIdx = hasFixedStart(route) ? 1 : 0;
        int endExclusive = hasFixedEnd(route) ? points.size() - 1 : points.size();
        for (int i = startIdx; i < endExclusive; i++) {
            addUsedId(ids, points.get(i));
        }
        return ids;
    }

    private Set<String> collectInteriorIds(Route route) {
        return collectAllNonBoundaryIds(route);
    }

    private void addUsedId(Set<String> ids, RoutePoint point) {
        if (point == null || point.getPoi() == null || point.getPoi().getId() == null) {
            return;
        }
        ids.add(point.getPoi().getId());
    }

    private List<Integer> normalizeInteriorOrder(List<Integer> newInteriorOrder, int expectedSize) {
        if (newInteriorOrder == null) {
            return List.of();
        }
        if (newInteriorOrder.size() == expectedSize) {
            return new ArrayList<>(newInteriorOrder);
        }
        List<Integer> normalized = new ArrayList<>();
        for (int i = 0; i < Math.min(expectedSize, newInteriorOrder.size()); i++) {
            normalized.add(newInteriorOrder.get(i));
        }
        return normalized;
    }

    private double effectiveReferenceLat(Route route) {
        if (hasFixedStart(route)) {
            return route.getPoints().get(0).effectiveLatitude();
        }
        if (hasFixedEnd(route)) {
            return route.getPoints().get(route.getPoints().size() - 1).effectiveLatitude();
        }
        return KIZILAY_LAT;
    }

    private double effectiveReferenceLng(Route route) {
        if (hasFixedStart(route)) {
            return route.getPoints().get(0).effectiveLongitude();
        }
        if (hasFixedEnd(route)) {
            return route.getPoints().get(route.getPoints().size() - 1).effectiveLongitude();
        }
        return KIZILAY_LNG;
    }

    private RouteVariantProfile variantFor(int routeIndex) {
        double quotaExponent = Math.max(0.9, 1.35 - (routeIndex * 0.12));
        double explorationFactor = Math.min(0.28, 0.08 + (routeIndex * 0.06));
        double overlapPenalty = Math.min(0.32, 0.08 + (routeIndex * 0.04));
        return new RouteVariantProfile(routeIndex, quotaExponent, explorationFactor, overlapPenalty);
    }

    private int overlapPenalty(String placeId, List<Set<String>> priorInteriorIds) {
        int overlapCount = 0;
        for (Set<String> prior : priorInteriorIds) {
            if (prior.contains(placeId)) {
                overlapCount++;
            }
        }
        return overlapCount;
    }

    private int hotelCandidateBandSize(int size, RouteVariantProfile variant) {
        return Math.max(1, Math.min(size, 1 + variant.routeIndex()));
    }

    private int deterministicIndex(int bandSize,
                                   String requestId,
                                   int routeIndex,
                                   int salt,
                                   Random fallbackRandom) {
        if (bandSize <= 1) {
            return 0;
        }
        int hash = Objects.hash(requestId, routeIndex, salt);
        int seed = Math.abs(hash == Integer.MIN_VALUE ? 0 : hash);
        if (seed == 0 && fallbackRandom != null) {
            seed = fallbackRandom.nextInt(Integer.MAX_VALUE);
        }
        return seed % bandSize;
    }

    private double deterministicNoise(String placeId, String requestId, int routeIndex) {
        Random rnd = new Random(Objects.hash(placeId, requestId, routeIndex));
        return rnd.nextDouble();
    }

    private boolean matchesFilters(Place place, RouteCandidateFiltersRequest filters) {
        if (filters == null) {
            return true;
        }
        if (filters.getMinRating() != null) {
            double rating = place.getRatingScore() != null ? place.getRatingScore() : 0.0;
            if (rating < filters.getMinRating()) {
                return false;
            }
        }
        if (filters.getMinRatingCount() != null) {
            int count = place.getRatingCount() != null ? place.getRatingCount() : 0;
            if (count < filters.getMinRatingCount()) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesRequestedType(Place place, String poiType) {
        if (poiType == null || poiType.isBlank()) {
            return false;
        }
        String normalized = normalizeToken(poiType);
        RouteLabel requestedLabel = inferRequestedRouteLabel(poiType);
        if (requestedLabel != null) {
            return labelService.label(place) == requestedLabel;
        }
        for (String type : GeoUtils.parseTypesFromEntity(place.getTypes())) {
            if (normalizeToken(type).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private RouteLabel inferRequestedRouteLabel(String poiType) {
        String normalized = normalizeToken(poiType);
        return switch (normalized) {
            case "HOTEL", "OTEL", "LODGING", "GUESTHOUSE", "GUEST_HOUSE" -> RouteLabel.HOTEL;
            case "KAFE", "CAFE", "COFFEESHOP", "COFFEE_SHOP", "BAKERY", "KAFE_TATLI" -> RouteLabel.KAFE_TATLI;
            case "RESTAURANT", "RESTORAN", "RESTAURANTS", "RESTORAN_TOLERANSI", "BARANDGRILL", "BAR_AND_GRILL" -> RouteLabel.RESTORAN_TOLERANSI;
            case "GECEHAYATI", "GECE_HAYATI", "BAR", "NIGHTCLUB", "NIGHT_CLUB" -> RouteLabel.GECE_HAYATI;
            case "PARK", "PARK_VE_SEYIR_NOKTALARI", "SEYIRNOKTASI", "SEYIR_NOKTASI", "GARDEN" -> RouteLabel.PARK_VE_SEYIR_NOKTALARI;
            case "DOGALALANLAR", "DOGAL_ALANLAR", "NATURE", "NATUREPRESERVE", "NATURE_PRESERVE" -> RouteLabel.DOGAL_ALANLAR;
            case "TARIHIALANLAR", "TARIHI_ALANLAR", "MUSEUM", "HISTORICALPLACE", "HISTORICAL_PLACE", "HISTORIC" -> RouteLabel.TARIHI_ALANLAR;
            case "LANDMARK", "TOURISTATTRACTION", "TOURIST_ATTRACTION", "STADIUM", "CONCERTHALL", "CONCERT_HALL" -> RouteLabel.LANDMARK;
            default -> null;
        };
    }

    private boolean matchesMealRequirement(Place place, MealRequirement meal) {
        List<String> normalizedTypes = GeoUtils.parseTypesFromEntity(place.getTypes()).stream()
                .map(this::normalizeToken)
                .toList();
        return switch (meal) {
            case BREAKFAST -> isBreakfastCompatible(normalizedTypes);
            case LUNCH, DINNER -> isRestaurantLike(normalizedTypes);
        };
    }

    private boolean isBreakfastCompatible(List<String> normalizedTypes) {
        return normalizedTypes.contains("BREAKFASTRESTAURANT")
                || normalizedTypes.contains("BREAKFAST_RESTAURANT")
                || normalizedTypes.contains("BRUNCHRESTAURANT")
                || normalizedTypes.contains("BRUNCH_RESTAURANT")
                || normalizedTypes.contains("CAFE")
                || normalizedTypes.contains("COFFEESHOP")
                || normalizedTypes.contains("COFFEE_SHOP")
                || normalizedTypes.contains("BAKERY")
                || normalizedTypes.contains("TEAHOUSE")
                || normalizedTypes.contains("TEA_HOUSE")
                || normalizedTypes.contains("RESTAURANT");
    }

    private boolean isRestaurantLike(List<String> normalizedTypes) {
        return normalizedTypes.contains("RESTAURANT")
                || normalizedTypes.contains("TURKISHRESTAURANT")
                || normalizedTypes.contains("TURKISH_RESTAURANT")
                || normalizedTypes.contains("FINEDININGRESTAURANT")
                || normalizedTypes.contains("FINE_DINING_RESTAURANT")
                || normalizedTypes.contains("FASTFOODRESTAURANT")
                || normalizedTypes.contains("FAST_FOOD_RESTAURANT")
                || normalizedTypes.contains("BARANDGRILL")
                || normalizedTypes.contains("BAR_AND_GRILL")
                || normalizedTypes.contains("MEALTAKEAWAY")
                || normalizedTypes.contains("MEAL_TAKEAWAY");
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
                && first.getPoi().getId().equals(last.getPoi().getId())
                && labelService.label(first.getPoi()) == RouteLabel.HOTEL;
    }

    private String appendProtectionReason(String existing, String addition) {
        if (existing == null || existing.isBlank()) {
            return addition;
        }
        if (existing.contains(addition)) {
            return existing;
        }
        return existing + "+" + addition;
    }

    private String buildRouteId(String requestId, int routeIndex) {
        return firstNonBlank(requestId, "req") + "-route-" + (routeIndex + 1);
    }

    private String normalizeToken(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String firstNonBlank(String value, String defaultValue) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return defaultValue != null ? defaultValue : "";
    }

    private static Double parseOptionalCoordinate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
