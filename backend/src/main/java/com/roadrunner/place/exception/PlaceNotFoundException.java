package com.roadrunner.place.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Thrown by the service layer when a Place with the requested
 * identifier or criteria cannot be found in the database.
 * Maps to HTTP 404 Not Found.
 */
public class PlaceNotFoundException extends ResponseStatusException {

    /** Constructs an exception with a descriptive message. */
    public PlaceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }

    /** Convenience constructor for id-based lookups. */
    public static PlaceNotFoundException forId(String id) {
        return new PlaceNotFoundException("Place not found with id: " + id);
    }

    /** Convenience constructor for name-based lookups. */
    public static PlaceNotFoundException forName(String name) {
        return new PlaceNotFoundException("Place not found with name: " + name);
    }
}
