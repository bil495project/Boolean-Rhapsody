package com.roadrunner.user.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.roadrunner.route.dto.request.GenerateRoutesRequest;
import com.roadrunner.route.dto.response.RouteResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SavedRouteWriteRequest {

    private String title;

    @Valid
    @NotNull
    private RouteResponse route;

    @Valid
    @NotNull
    private GenerateRoutesRequest generateRequest;
}
