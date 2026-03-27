package com.roadrunner.user.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roadrunner.user.dto.request.RenameSavedRouteRequest;
import com.roadrunner.user.dto.request.SavedRouteWriteRequest;
import com.roadrunner.user.dto.response.SavedRouteDetailResponse;
import com.roadrunner.user.dto.response.SavedRouteSummaryResponse;
import com.roadrunner.user.service.SavedRouteService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users/me/saved-routes")
public class SavedRouteController {

    private final SavedRouteService savedRouteService;

    public SavedRouteController(SavedRouteService savedRouteService) {
        this.savedRouteService = savedRouteService;
    }

    @GetMapping
    public ResponseEntity<List<SavedRouteSummaryResponse>> getAllSavedRoutes() {
        return ResponseEntity.ok(savedRouteService.getAllSavedRoutes(getCurrentUserId()));
    }

    @GetMapping("/{savedRouteId}")
    public ResponseEntity<SavedRouteDetailResponse> getSavedRouteById(@PathVariable String savedRouteId) {
        return ResponseEntity.ok(savedRouteService.getSavedRouteById(getCurrentUserId(), savedRouteId));
    }

    @PostMapping
    public ResponseEntity<SavedRouteDetailResponse> createSavedRoute(
            @Valid @RequestBody SavedRouteWriteRequest request) {
        SavedRouteDetailResponse response = savedRouteService.createSavedRoute(getCurrentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{savedRouteId}")
    public ResponseEntity<SavedRouteDetailResponse> updateSavedRoute(
            @PathVariable String savedRouteId,
            @Valid @RequestBody SavedRouteWriteRequest request) {
        return ResponseEntity.ok(savedRouteService.updateSavedRoute(getCurrentUserId(), savedRouteId, request));
    }

    @PatchMapping("/{savedRouteId}/title")
    public ResponseEntity<SavedRouteSummaryResponse> renameSavedRoute(
            @PathVariable String savedRouteId,
            @Valid @RequestBody RenameSavedRouteRequest request) {
        return ResponseEntity.ok(savedRouteService.renameSavedRoute(getCurrentUserId(), savedRouteId, request));
    }

    @DeleteMapping("/{savedRouteId}")
    public ResponseEntity<Void> deleteSavedRoute(@PathVariable String savedRouteId) {
        savedRouteService.deleteSavedRoute(getCurrentUserId(), savedRouteId);
        return ResponseEntity.noContent().build();
    }

    private String getCurrentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
