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

    public boolean isGeneratedSlotPlaceholder() {
        return isBlank(kind)
                && isBlank(placeId)
                && isBlank(poiType)
                && filters == null;
    }

    public boolean hasAnyConfiguration() {
        return !isBlank(kind)
                || !isBlank(placeId)
                || !isBlank(poiType)
                || filters != null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
