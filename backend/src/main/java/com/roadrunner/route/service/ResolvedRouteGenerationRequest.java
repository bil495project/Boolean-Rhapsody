package com.roadrunner.route.service;

import java.util.Map;

public record ResolvedRouteGenerationRequest(
        Map<String, String> userVector,
        boolean legacyMode,
        boolean stayAtHotel,
        RouteConstraintSpec constraintSpec) {
}
