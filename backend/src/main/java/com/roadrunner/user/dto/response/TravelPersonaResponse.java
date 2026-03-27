package com.roadrunner.user.dto.response;

import java.util.Map;

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
public class TravelPersonaResponse {
    private String id;
    private String name;
    private Boolean isDefault;
    private Double tempo;
    private Double socialPreference;
    private Double naturePreference;
    private Double historyPreference;
    private Double foodImportance;
    private Double alcoholPreference;
    private Double transportStyle;
    private Double budgetLevel;
    private Double tripLength;
    private Double crowdPreference;
    private Map<String, String> userVector;
}
