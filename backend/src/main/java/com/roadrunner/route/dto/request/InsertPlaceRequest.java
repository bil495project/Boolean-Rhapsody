package com.roadrunner.route.dto.request;

import java.util.Map;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InsertPlaceRequest {

    @Min(0)
    private int index;

    @NotBlank
    private String poiId;

    @NotNull
    private Map<String, String> originalUserVector;
}
