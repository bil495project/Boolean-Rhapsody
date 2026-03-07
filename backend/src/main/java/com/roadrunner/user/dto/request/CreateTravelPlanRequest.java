package com.roadrunner.user.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// TODO: POI service yok, travel plan oluşturulamıyor.
public class CreateTravelPlanRequest {

    @NotNull
    private List<String> selectedPlaceIds;
}
