package com.roadrunner.route.service;

import java.util.List;

import com.roadrunner.route.dto.request.RouteCandidateFiltersRequest;

public record RouteConstraintSpec(
        BoundaryRequirement startBoundary,
        BoundaryRequirement endBoundary,
        boolean sameHotelLoop,
        int targetInteriorCount,
        List<InteriorSlot> orderedInteriorSlots,
        List<InteriorRequirement> hardSlots,
        List<MealRequirement> mealRequirements) {

    public boolean hasFixedStart() {
        return startBoundary != null && startBoundary.kind() != BoundaryKind.NONE;
    }

    public boolean hasFixedEnd() {
        return endBoundary != null && endBoundary.kind() != BoundaryKind.NONE;
    }

    public boolean hasOrderedInteriorSlots() {
        return orderedInteriorSlots != null && !orderedInteriorSlots.isEmpty();
    }

    public enum BoundaryKind {
        NONE,
        HOTEL,
        PLACE,
        TYPE
    }

    public enum InteriorRequirementKind {
        PLACE,
        TYPE
    }

    public enum InteriorSlotKind {
        GENERATED,
        FIXED
    }

    public enum MealRequirement {
        BREAKFAST,
        LUNCH,
        DINNER
    }

    public record BoundaryRequirement(
            BoundaryKind kind,
            String placeId,
            String poiType,
            RouteCandidateFiltersRequest filters) {
    }

    public record InteriorRequirement(
            InteriorRequirementKind kind,
            String placeId,
            String poiType,
            RouteCandidateFiltersRequest filters) {
    }

    public record InteriorSlot(
            InteriorSlotKind kind,
            InteriorRequirement requirement) {
    }
}
