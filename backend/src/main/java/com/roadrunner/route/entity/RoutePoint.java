package com.roadrunner.route.entity;

import com.roadrunner.place.entity.Place;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * In-memory domain object representing a single stop on a route.
 * NOT a JPA entity — this is a computation result held by the client.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoutePoint {

    private int index;
    private Place poi;
    private int plannedVisitMin;

    public void assignPOI(Place poi) {
        this.poi = poi;
    }
}
