package com.roadrunner.user.dto.response;

import java.util.List;

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
public class SavedRouteSummaryResponse {

    private String id;
    private String title;
    private List<String> orderedPlaceIds;
    private int stopCount;
    private String travelMode;
    private int totalDurationSec;
    private double totalDistanceM;
    private boolean feasible;
    private long createdAt;
    private long updatedAt;
}
