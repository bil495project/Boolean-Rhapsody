package com.roadrunner.route.dto.response;

import java.util.ArrayList;
import java.util.List;

import com.roadrunner.place.entity.Place;
import com.roadrunner.route.entity.Route;
import com.roadrunner.route.entity.RoutePoint;
import com.roadrunner.route.entity.RouteSegment;
import com.roadrunner.route.service.GeoUtils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Outbound DTO for a complete route.
 * Contains a static factory method {@link #fromRoute(Route)} that maps
 * the domain model to this response, flattening all nested entities.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteResponse {

    private String routeId;
    private List<RoutePointResponse> points;
    private List<RouteSegmentResponse> segments;
    private int totalDurationSec;
    private double totalDistanceM;
    private boolean feasible;
    private String travelMode;

    /**
     * Maps a {@link Route} domain object to a {@link RouteResponse} DTO.
     * Flattens {@link Place} entity fields into {@link RoutePointResponse}.
     */
    public static RouteResponse fromRoute(Route route) {
        List<RoutePointResponse> pts = new ArrayList<>();
        for (RoutePoint rp : route.getPoints()) {
            Place p = rp.getPoi();
            List<String> types = GeoUtils.parseTypesFromEntity(
                    p != null ? p.getTypes() : null);

            pts.add(RoutePointResponse.builder()
                    .index(rp.getIndex())
                    .poiId(p != null ? p.getId() : null)
                    .poiName(p != null ? p.getName() : rp.getAnchorName())
                    .latitude(rp.effectiveLatitude())
                    .longitude(rp.effectiveLongitude())
                    .formattedAddress(p != null ? p.getFormattedAddress() : null)
                    .types(types)
                    .ratingScore(p != null && p.getRatingScore() != null
                            ? p.getRatingScore() : 0.0)
                    .ratingCount(p != null && p.getRatingCount() != null
                            ? p.getRatingCount() : 0)
                    .priceLevel(p != null ? p.getPriceLevel() : null)
                    .plannedVisitMin(rp.getPlannedVisitMin())
                    .build());
        }

        List<RouteSegmentResponse> segs = new ArrayList<>();
        for (RouteSegment seg : route.getSegments()) {
            segs.add(RouteSegmentResponse.builder()
                    .fromIndex(seg.getFromIndex())
                    .toIndex(seg.getToIndex())
                    .durationSec(seg.getDurationSec())
                    .distanceM(seg.getDistanceM())
                    .build());
        }

        return RouteResponse.builder()
                .routeId(route.getRouteId())
                .points(pts)
                .segments(segs)
                .totalDurationSec(route.getTotalDurationSec())
                .totalDistanceM(route.getTotalDistanceM())
                .feasible(route.isFeasible())
                .travelMode(route.getTravelMode())
                .build();
    }
}
