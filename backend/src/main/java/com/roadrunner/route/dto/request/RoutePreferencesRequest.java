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
public class RoutePreferencesRequest {

    private Double tempo;
    private Double socialPreference;
    private Double naturePreference;
    private Double historyPreference;
    private Double foodImportance;
    private Double alcoholPreference;
    private Double transportStyle;
    private Double budgetLevel;
    private Double tripLength;
    private Double crowdPreference;
}
