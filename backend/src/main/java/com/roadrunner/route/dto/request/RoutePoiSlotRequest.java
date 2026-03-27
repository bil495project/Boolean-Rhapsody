package com.roadrunner.route.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RoutePoiSlotRequest {

    private String kind;
    private String placeId;
    private String poiType;
    private RouteCandidateFiltersRequest filters;
}
