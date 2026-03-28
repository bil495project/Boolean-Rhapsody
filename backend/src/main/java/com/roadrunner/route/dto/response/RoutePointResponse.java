package com.roadrunner.route.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Outbound DTO for a single route point.
 * Flattens {@code Place} entity fields — never exposes the JPA entity directly.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutePointResponse {

    private int index;
    private String poiId;
    private String poiName;
    private double latitude;
    private double longitude;
    private String formattedAddress;
    private List<String> types;
    private double ratingScore;
    private int ratingCount;
    private String priceLevel;
    private int plannedVisitMin;
    private boolean fixedAnchor;
    private boolean protectedPoint;
    private String protectionReason;
}
