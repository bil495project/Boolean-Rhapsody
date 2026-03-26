package com.roadrunner.route.dto.request;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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

    @Min(1)
    @Max(10)
    private int k = 3;

    /**
     * Frontend may already send a richer constraints object while the backend
     * route generator is still driven by the flat userVector contract.
     * Keep binding permissive so route generation remains backward-compatible.
     */
    private Map<String, Object> constraints;
}
