package com.roadrunner.route.entity;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * In-memory domain object representing a complete generated route.
 * NOT a JPA entity — routes are stateless computation results held
 * by the client between API calls.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Route {

    private String routeId;

    @Builder.Default
    private List<RoutePoint> points = new ArrayList<>();

    @Builder.Default
    private List<RouteSegment> segments = new ArrayList<>();

    private int totalDurationSec;
    private double totalDistanceM;
    private boolean feasible;

    @Builder.Default
    private String travelMode = "driving";
}
