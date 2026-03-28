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
    private String anchorName;
    private Double anchorLatitude;
    private Double anchorLongitude;
    private boolean fixedAnchor;
    private boolean protectedPoint;
    private String protectionReason;

    public void assignPOI(Place poi) {
        this.poi = poi;
        if (poi != null) {
            this.anchorName = null;
            this.anchorLatitude = null;
            this.anchorLongitude = null;
        }
    }

    public boolean isCustomAnchor() {
        return poi == null
                && anchorLatitude != null
                && anchorLongitude != null;
    }

    public double effectiveLatitude() {
        if (poi != null) {
            return poi.getLatitude();
        }
        return anchorLatitude != null ? anchorLatitude : 0.0;
    }

    public double effectiveLongitude() {
        if (poi != null) {
            return poi.getLongitude();
        }
        return anchorLongitude != null ? anchorLongitude : 0.0;
    }
}
