package com.roadrunner.route.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * In-memory domain object representing the travel segment between two
 * consecutive route points. NOT a JPA entity.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RouteSegment {

    private int fromIndex;
    private int toIndex;
    private int durationSec;
    private double distanceM;

    /** Always false — geometry support is reserved for future OSRM integration. */
    public boolean hasGeometry() {
        return false;
    }
}
