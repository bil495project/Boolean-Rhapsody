package com.roadrunner.route.dto.request;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReorderRequest {

    @NotNull
    private List<Integer> newOrder;

    @NotNull
    private Map<String, String> originalUserVector;
}
