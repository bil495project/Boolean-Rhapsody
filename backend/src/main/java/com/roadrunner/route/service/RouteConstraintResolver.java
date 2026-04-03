package com.roadrunner.route.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.roadrunner.route.dto.request.GenerateRoutesRequest;
import com.roadrunner.route.dto.request.RouteAnchorRequest;
import com.roadrunner.route.dto.request.RouteBoundarySelectionRequest;
import com.roadrunner.route.dto.request.RouteConstraintsRequest;
import com.roadrunner.route.dto.request.RoutePoiSlotRequest;
import com.roadrunner.route.service.RouteConstraintSpec.BoundaryKind;
import com.roadrunner.route.service.RouteConstraintSpec.BoundaryRequirement;
import com.roadrunner.route.service.RouteConstraintSpec.InteriorRequirement;
import com.roadrunner.route.service.RouteConstraintSpec.InteriorRequirementKind;
import com.roadrunner.route.service.RouteConstraintSpec.InteriorSlot;
import com.roadrunner.route.service.RouteConstraintSpec.InteriorSlotKind;
import com.roadrunner.route.service.RouteConstraintSpec.MealRequirement;

@Service
public class RouteConstraintResolver {

    public boolean shouldUseLegacyFallback(RouteConstraintsRequest constraints) {
        if (constraints == null) {
            return true;
        }
        return constraints.getStartWithPoi() == null
                && constraints.getEndWithPoi() == null
                && constraints.getStartWithHotel() == null
                && constraints.getEndWithHotel() == null
                && constraints.getStartPoint() == null
                && constraints.getEndPoint() == null
                && constraints.getStartAnchor() == null
                && constraints.getEndAnchor() == null
                && (constraints.getPoiSlots() == null || constraints.getPoiSlots().isEmpty())
                && !Boolean.TRUE.equals(constraints.getNeedsBreakfast())
                && !Boolean.TRUE.equals(constraints.getNeedsLunch())
                && !Boolean.TRUE.equals(constraints.getNeedsDinner());
    }

    public RouteConstraintSpec resolve(GenerateRoutesRequest req, Map<String, String> userVector) {
        RouteConstraintsRequest constraints = req.getConstraints();
        if (constraints == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "constraints are required for constrained route generation");
        }

        validateAnchor(constraints.getStartAnchor(), "startAnchor");
        validateAnchor(constraints.getEndAnchor(), "endAnchor");
        validateBoundarySelection(constraints.getStartPoint(), "startPoint");
        validateBoundarySelection(constraints.getEndPoint(), "endPoint");
        validateSlots(constraints.getPoiSlots());

        boolean usesExplicitBoundaryModel = constraints.getStartPoint() != null
                || constraints.getEndPoint() != null;

        BoundaryRequirement startBoundary;
        BoundaryRequirement endBoundary;
        if (usesExplicitBoundaryModel) {
            rejectLegacyBoundaryMix(constraints, "start");
            rejectLegacyBoundaryMix(constraints, "end");
            startBoundary = resolveExplicitBoundary(constraints.getStartPoint(), "start");
            endBoundary = resolveExplicitBoundary(constraints.getEndPoint(), "end");
        } else {
            boolean stayAtHotel = Boolean.TRUE.equals(constraints.getStayAtHotel());
            boolean startWithPoi = Boolean.TRUE.equals(constraints.getStartWithPoi());
            boolean endWithPoi = Boolean.TRUE.equals(constraints.getEndWithPoi());
            boolean startWithHotel = resolveHotelBoundaryFlag(
                    constraints.getStartWithHotel(),
                    startWithPoi,
                    stayAtHotel);
            boolean endWithHotel = resolveHotelBoundaryFlag(
                    constraints.getEndWithHotel(),
                    endWithPoi,
                    stayAtHotel);

            startBoundary = resolveBoundary(
                    startWithPoi, startWithHotel, constraints.getStartAnchor(), "start");
            endBoundary = resolveBoundary(
                    endWithPoi, endWithHotel, constraints.getEndAnchor(), "end");
        }

        boolean sameHotelLoop = startBoundary.kind() == BoundaryKind.HOTEL
                && endBoundary.kind() == BoundaryKind.HOTEL;

        int targetInteriorCount = defaultFreeInteriorCount(userVector);
        List<InteriorSlot> orderedInteriorSlots = new ArrayList<>();
        List<InteriorRequirement> hardSlots = new ArrayList<>();
        if (constraints.getPoiSlots() != null) {
            targetInteriorCount = constraints.getPoiSlots().size();
            for (RoutePoiSlotRequest slot : constraints.getPoiSlots()) {
                if (slot == null || slot.isGeneratedSlotPlaceholder()) {
                    orderedInteriorSlots.add(new InteriorSlot(InteriorSlotKind.GENERATED, null));
                    continue;
                }
                InteriorRequirement requirement = new InteriorRequirement(
                        normalizeSlotKind(slot.getKind()),
                        slot.getPlaceId(),
                        slot.getPoiType(),
                        slot.getFilters());
                orderedInteriorSlots.add(new InteriorSlot(InteriorSlotKind.FIXED, requirement));
                hardSlots.add(requirement);
            }
        }

        List<MealRequirement> mealRequirements = new ArrayList<>();
        if (Boolean.TRUE.equals(constraints.getNeedsBreakfast())) {
            mealRequirements.add(MealRequirement.BREAKFAST);
        }
        if (Boolean.TRUE.equals(constraints.getNeedsLunch())) {
            mealRequirements.add(MealRequirement.LUNCH);
        }
        if (Boolean.TRUE.equals(constraints.getNeedsDinner())) {
            mealRequirements.add(MealRequirement.DINNER);
        }

        return new RouteConstraintSpec(
                startBoundary,
                endBoundary,
                sameHotelLoop,
                targetInteriorCount,
                List.copyOf(orderedInteriorSlots),
                List.copyOf(hardSlots),
                List.copyOf(mealRequirements));
    }

    private BoundaryRequirement resolveBoundary(
            boolean withPoi,
            boolean withHotel,
            RouteAnchorRequest anchor,
            String side) {
        if (withHotel) {
            if (anchor != null && "PLACE".equalsIgnoreCase(anchor.getKind())
                    && anchor.getPlaceId() != null && anchor.getPoiType() == null) {
                return new BoundaryRequirement(BoundaryKind.HOTEL, anchor.getPlaceId(), null, null);
            }
            if (anchor != null && "TYPE".equalsIgnoreCase(anchor.getKind())
                    && isHotelType(anchor.getPoiType())) {
                return new BoundaryRequirement(BoundaryKind.HOTEL, null, anchor.getPoiType(), anchor.getFilters());
            }
            return new BoundaryRequirement(BoundaryKind.HOTEL, null, null, null);
        }
        if (!withPoi) {
            return new BoundaryRequirement(BoundaryKind.NONE, null, null, null);
        }
        if (anchor == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, side + "WithPoi requires a matching anchor");
        }
        if ("PLACE".equalsIgnoreCase(anchor.getKind())) {
            return new BoundaryRequirement(BoundaryKind.PLACE, anchor.getPlaceId(), null, null);
        }
        return new BoundaryRequirement(BoundaryKind.TYPE, null, anchor.getPoiType(), anchor.getFilters());
    }

    private boolean resolveHotelBoundaryFlag(Boolean explicitHotelFlag,
                                             boolean withPoi,
                                             boolean stayAtHotel) {
        if (explicitHotelFlag != null) {
            return explicitHotelFlag;
        }
        return stayAtHotel && !withPoi;
    }

    private BoundaryRequirement resolveExplicitBoundary(RouteBoundarySelectionRequest boundary, String side) {
        if (boundary == null || "NONE".equals(safeUpper(boundary.getType()))) {
            return new BoundaryRequirement(BoundaryKind.NONE, null, null, null);
        }

        String type = safeUpper(boundary.getType());
        return switch (type) {
            case "HOTEL" -> new BoundaryRequirement(BoundaryKind.HOTEL, null, null, null);
            case "PLACE" -> new BoundaryRequirement(BoundaryKind.PLACE, boundary.getPlaceId(), null, null);
            case "TYPE" -> new BoundaryRequirement(BoundaryKind.TYPE, null, boundary.getPoiType(), boundary.getFilters());
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, side + "Point type must be NONE, HOTEL, PLACE, or TYPE");
        };
    }

    private void validateAnchor(RouteAnchorRequest anchor, String fieldName) {
        if (anchor == null) {
            return;
        }
        String kind = safeUpper(anchor.getKind());
        if ("PLACE".equals(kind)) {
            if (anchor.getPlaceId() == null || anchor.getPlaceId().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " PLACE requires placeId");
            }
            if (anchor.getPoiType() != null && !anchor.getPoiType().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        fieldName + " PLACE must not include poiType");
            }
            if (anchor.getFilters() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        fieldName + " PLACE must not include filters");
            }
            return;
        }
        if ("TYPE".equals(kind)) {
            if (anchor.getPoiType() == null || anchor.getPoiType().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " TYPE requires poiType");
            }
            if (anchor.getPlaceId() != null && !anchor.getPlaceId().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " TYPE must not include placeId");
            }
            return;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " kind must be PLACE or TYPE");
    }

    private void validateBoundarySelection(RouteBoundarySelectionRequest boundary, String fieldName) {
        if (boundary == null) {
            return;
        }

        String type = safeUpper(boundary.getType());
        switch (type) {
            case "", "NONE" -> {
                if (hasText(boundary.getPlaceId()) || hasText(boundary.getPoiType()) || boundary.getFilters() != null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            fieldName + " NONE must not include placeId, poiType, or filters");
                }
            }
            case "HOTEL" -> {
                if (hasText(boundary.getPlaceId()) || hasText(boundary.getPoiType()) || boundary.getFilters() != null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            fieldName + " HOTEL must not include placeId, poiType, or filters");
                }
            }
            case "PLACE" -> {
                if (!hasText(boundary.getPlaceId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " PLACE requires placeId");
                }
                if (hasText(boundary.getPoiType()) || boundary.getFilters() != null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            fieldName + " PLACE must not include poiType or filters");
                }
            }
            case "TYPE" -> {
                if (!hasText(boundary.getPoiType())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " TYPE requires poiType");
                }
                if (hasText(boundary.getPlaceId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            fieldName + " TYPE must not include placeId");
                }
            }
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, fieldName + " type must be NONE, HOTEL, PLACE, or TYPE");
        }
    }

    private void validateSlots(List<RoutePoiSlotRequest> slots) {
        if (slots == null) {
            return;
        }
        for (int i = 0; i < slots.size(); i++) {
            RoutePoiSlotRequest slot = slots.get(i);
            String fieldName = "poiSlots[" + i + "]";
            if (slot == null || slot.isGeneratedSlotPlaceholder()) {
                continue;
            }
            if (!slot.hasAnyConfiguration()) {
                continue;
            }
            String kind = safeUpper(slot.getKind());
            if (kind.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        fieldName + " must be null, empty, PLACE, or TYPE");
            }
            if ("PLACE".equals(kind)) {
                if (slot.getPlaceId() == null || slot.getPlaceId().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " PLACE requires placeId");
                }
                if (slot.getPoiType() != null && !slot.getPoiType().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            fieldName + " PLACE must not include poiType");
                }
                if (slot.getFilters() != null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            fieldName + " PLACE must not include filters");
                }
                continue;
            }
            if ("TYPE".equals(kind)) {
                if (slot.getPoiType() == null || slot.getPoiType().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " TYPE requires poiType");
                }
                if (slot.getPlaceId() != null && !slot.getPlaceId().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            fieldName + " TYPE must not include placeId");
                }
                continue;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    fieldName + " kind must be PLACE or TYPE");
        }
    }

    private void rejectLegacyBoundaryMix(RouteConstraintsRequest constraints, String side) {
        boolean hasLegacyFields = "start".equals(side)
                ? Boolean.TRUE.equals(constraints.getStartWithPoi())
                    || Boolean.TRUE.equals(constraints.getStartWithHotel())
                    || constraints.getStartAnchor() != null
                : Boolean.TRUE.equals(constraints.getEndWithPoi())
                    || Boolean.TRUE.equals(constraints.getEndWithHotel())
                    || constraints.getEndAnchor() != null;

        RouteBoundarySelectionRequest explicitBoundary = "start".equals(side)
                ? constraints.getStartPoint()
                : constraints.getEndPoint();

        if (explicitBoundary != null && hasLegacyFields) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    side + " boundary must use either explicit startPoint/endPoint or legacy startWith*/endWith* fields, not both");
        }
    }

    private int defaultFreeInteriorCount(Map<String, String> userVector) {
        double density = GeoUtils.safeFloat(userVector.get("weight_toplamPoiYogunlugu"), 0.5);
        return Math.max(1, Math.min(10, (int) Math.round(1 + 9 * density)));
    }

    private InteriorRequirementKind normalizeSlotKind(String kind) {
        String normalized = safeUpper(kind);
        return switch (normalized) {
            case "PLACE" -> InteriorRequirementKind.PLACE;
            case "TYPE" -> InteriorRequirementKind.TYPE;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "poiSlot kind must be PLACE or TYPE");
        };
    }

    private boolean isHotelType(String poiType) {
        String normalized = safeUpper(poiType);
        return "HOTEL".equals(normalized) || "LODGING".equals(normalized) || "GUEST_HOUSE".equals(normalized);
    }

    private String safeUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
