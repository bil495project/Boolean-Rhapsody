package com.roadrunner.route.dto.request;

import java.util.Map;

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
public class GenerateRoutesRequest {

    @NotNull
    private Map<String, String> userVector;

    @Min(1)
    @Max(10)
    private int k = 3;
}
