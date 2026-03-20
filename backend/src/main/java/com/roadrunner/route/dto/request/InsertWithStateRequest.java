package com.roadrunner.route.dto.request;

import java.util.Map;

import com.roadrunner.route.dto.response.RouteResponse;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stateful wrapper for insert requests — includes the current route
 * state plus the POI to insert.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InsertWithStateRequest {

    @NotNull
    private RouteResponse currentRoute;

    @Min(0)
    private int index;

    @NotBlank
    private String poiId;

    @NotNull
    private Map<String, String> originalUserVector;
}
