package com.roadrunner.route.dto.request;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GenerateRoutesRequest {

    @NotNull
    private Map<String, String> userVector;

    @Valid
    private RoutePreferencesRequest preferences;

    @Min(1)
    @Max(10)
    private int k = 3;

    @Valid
    private RouteConstraintsRequest constraints;

    private Double centerLat;

    private Double centerLng;
}
