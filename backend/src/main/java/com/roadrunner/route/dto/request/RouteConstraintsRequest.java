package com.roadrunner.route.dto.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RouteConstraintsRequest {

    private Boolean stayAtHotel;
    private Boolean needsBreakfast;
    private Boolean needsLunch;
    private Boolean needsDinner;
    private Boolean startWithPoi;
    private Boolean endWithPoi;
    private Boolean startWithHotel;
    private Boolean endWithHotel;

    @Valid
    private RouteBoundarySelectionRequest startPoint;

    @Valid
    private RouteBoundarySelectionRequest endPoint;

    @Valid
    private RouteAnchorRequest startAnchor;

    @Valid
    private RouteAnchorRequest endAnchor;

    @Valid
    private List<RoutePoiSlotRequest> poiSlots;
}
