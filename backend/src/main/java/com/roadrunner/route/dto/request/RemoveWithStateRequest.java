package com.roadrunner.route.dto.request;

import java.util.Map;

import com.roadrunner.route.dto.response.RouteResponse;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stateful wrapper for remove requests — includes the current route
 * state plus the index to remove.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RemoveWithStateRequest {

    @NotNull
    private RouteResponse currentRoute;

    @Min(0)
    private int index;

    @NotNull
    private Map<String, String> originalUserVector;
}
