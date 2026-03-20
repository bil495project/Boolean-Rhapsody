package com.roadrunner.route.dto.request;

import java.util.HashMap;
import java.util.Map;

import com.roadrunner.route.dto.response.RouteResponse;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stateful wrapper for reroll requests — includes the current route
 * state (sent back by the client) plus reroll-specific parameters.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RerollWithStateRequest {

    @NotNull
    private RouteResponse currentRoute;

    @Min(0)
    private int index;

    private Map<String, String> indexParams = new HashMap<>();

    @NotNull
    private Map<String, String> originalUserVector;
}
