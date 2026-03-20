package com.roadrunner.route.service;

import org.springframework.stereotype.Service;

import com.roadrunner.place.entity.Place;

/**
 * Backward-compatible facade for the route labeler seam.
 */
@Service
public class PlaceLabelService {

    private final PlaceRouteLabeler labeler;

    public PlaceLabelService(PlaceRouteLabeler labeler) {
        this.labeler = labeler;
    }

    public RouteLabel label(Place place) {
        return labeler.label(place);
    }
}
