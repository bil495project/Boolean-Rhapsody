package com.roadrunner.user.dto.request;

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
public class TravelPersonaRequest {
    private List<String> travelStyles;
    private List<String> interests;
    private String travelFrequency;
    private String preferredPace;
}
