package com.roadrunner.route.dto.request;

import java.util.List;
import java.util.Map;

import com.roadrunner.route.dto.response.RouteResponse;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stateful wrapper for reorder requests — includes the current route
 * state plus the new ordering permutation.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReorderWithStateRequest {

    @NotNull
    private RouteResponse currentRoute;

    @NotNull
    private List<Integer> newOrder;

    @NotNull
    private Map<String, String> originalUserVector;
}
