package com.roadrunner.route.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Outbound DTO for a travel segment between two route points.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteSegmentResponse {

    private int fromIndex;
    private int toIndex;
    private int durationSec;
    private double distanceM;
}
