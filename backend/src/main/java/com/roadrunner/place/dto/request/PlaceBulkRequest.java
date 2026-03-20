package com.roadrunner.place.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Inbound DTO for the bulk-id retrieval endpoint.
 * Clients supply a list of Place IDs; the service returns matching records.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlaceBulkRequest {

    /** Non-empty list of Google Place IDs to retrieve. */
    @NotEmpty(message = "ids must not be empty")
    private List<String> ids;
}
