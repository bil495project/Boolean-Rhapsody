package com.roadrunner.route.service;

import com.roadrunner.place.entity.Place;

/**
 * Integration seam for assigning exactly one semantic route label to a place.
 */
public interface PlaceRouteLabeler {

    RouteLabel label(Place place);
}
