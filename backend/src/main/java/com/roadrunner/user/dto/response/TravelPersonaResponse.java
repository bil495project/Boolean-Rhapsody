package com.roadrunner.user.dto.response;

import java.util.List;

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
    private List<String> travelStyles;
    private List<String> interests;
    private String travelFrequency;
    private String preferredPace;
}
