package com.roadrunner.user.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.roadrunner.user.dto.request.ChangePasswordRequest;
import com.roadrunner.user.dto.request.CreateTravelPlanRequest;
import com.roadrunner.user.dto.request.TravelPersonaRequest;
import com.roadrunner.user.dto.request.UpdateProfileRequest;
import com.roadrunner.user.dto.response.TravelPersonaResponse;
import com.roadrunner.user.dto.response.TravelPlanResponse;
import com.roadrunner.user.dto.response.UserResponse;
import com.roadrunner.user.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        String userId = getCurrentUserId();
        return ResponseEntity.ok(userService.getCurrentUser(userId));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        String userId = getCurrentUserId();
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        String userId = getCurrentUserId();
        userService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/personas")
    public ResponseEntity<List<TravelPersonaResponse>> getAllPersonas() {
        String userId = getCurrentUserId();
        return ResponseEntity.ok(userService.getAllPersonas(userId));
    }

    @PostMapping("/me/personas/new")
    public ResponseEntity<TravelPersonaResponse> addTravelPersona(
            @Valid @RequestBody TravelPersonaRequest request) {
        String userId = getCurrentUserId();
        TravelPersonaResponse response = userService.addTravelPersona(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/me/personas/{personaId}")
    public ResponseEntity<TravelPersonaResponse> updateTravelPersona(
            @PathVariable String personaId,
            @Valid @RequestBody TravelPersonaRequest request) {
        String userId = getCurrentUserId();
        return ResponseEntity.ok(userService.updateTravelPersona(userId, personaId, request));
    }

    @DeleteMapping("/me/personas/{personaId}")
    public ResponseEntity<Void> deleteTravelPersona(@PathVariable String personaId) {
        String userId = getCurrentUserId();
        userService.deleteTravelPersona(userId, personaId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/plans")
    public ResponseEntity<List<TravelPlanResponse>> getAllTravelPlans() {
        String userId = getCurrentUserId();
        return ResponseEntity.ok(userService.getAllTravelPlans(userId));
    }

    @PostMapping("/me/plans/new")
    public ResponseEntity<TravelPlanResponse> createTravelPlan(
            @Valid @RequestBody CreateTravelPlanRequest request) {
        String userId = getCurrentUserId();
        TravelPlanResponse response = userService.createTravelPlan(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me/plans/{planId}")
    public ResponseEntity<TravelPlanResponse> getTravelPlanById(@PathVariable String planId) {
        String userId = getCurrentUserId();
        return ResponseEntity.ok(userService.getTravelPlanById(userId, planId));
    }

    @PutMapping("/me/plans/{planId}")
    public ResponseEntity<TravelPlanResponse> updateTravelPlan(
            @PathVariable String planId,
            @Valid @RequestBody CreateTravelPlanRequest request) {
        String userId = getCurrentUserId();
        return ResponseEntity.ok(userService.updateTravelPlan(userId, planId, request));
    }

    @DeleteMapping("/me/plans/{planId}")
    public ResponseEntity<Void> deleteTravelPlan(@PathVariable String planId) {
        String userId = getCurrentUserId();
        userService.deleteTravelPlan(userId, planId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount() {
        String userId = getCurrentUserId();
        userService.deleteAccount(userId);
    }

    @GetMapping("/{userId}/personas")
    public ResponseEntity<List<TravelPersonaResponse>> getPersonasByUserId(@PathVariable String userId) {
        // Internal endpoint: called by the Flask LLM agent on localhost (no JWT required).
        // Relies on network-level trust (both services run on the same host).
        return ResponseEntity.ok(userService.getAllPersonas(userId));
    }

    private String getCurrentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @GetMapping
    public List<com.roadrunner.user.entity.User> getAllUsers() {
        return userService.getAllUsersEntity();
    }

    @GetMapping("/{id}")
    public com.roadrunner.user.entity.User getUserById(@PathVariable int id) {
        return userService.getUserByIdEntity(String.valueOf(id));
    }

    @GetMapping("/search")
    public com.roadrunner.user.entity.User getUserByName(@org.springframework.web.bind.annotation.RequestParam String name) {
        return userService.getUserByNameEntity(name);
    }

    @PutMapping("/{userId}/name")
    public void setUserName(@PathVariable String userId, @RequestBody String name) {
        userService.setUserName(userId, name);
    }

    @PutMapping("/{userId}/email")
    public void setUserEmail(@PathVariable String userId, @RequestBody String email) {
        userService.setUserEmail(userId, email);
    }

    @PostMapping("/{userId}/places")
    public void addSelectedPlace(@PathVariable String userId, @RequestBody long placeId) {
        userService.addSelectedPlace(userId, placeId);
    }
}
