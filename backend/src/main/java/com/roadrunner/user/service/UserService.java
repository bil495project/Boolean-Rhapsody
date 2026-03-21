package com.roadrunner.user.service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.roadrunner.user.dto.request.ChangePasswordRequest;
import com.roadrunner.user.dto.request.CreateTravelPlanRequest;
import com.roadrunner.user.dto.request.TravelPersonaRequest;
import com.roadrunner.user.dto.request.UpdateProfileRequest;
import com.roadrunner.user.dto.response.TravelPersonaResponse;
import com.roadrunner.user.dto.response.TravelPlanResponse;
import com.roadrunner.user.dto.response.UserResponse;
import com.roadrunner.user.entity.TravelPersona;
import com.roadrunner.user.entity.TravelPlan;
import com.roadrunner.user.entity.User;
import com.roadrunner.user.repository.TravelPersonaRepository;
import com.roadrunner.user.repository.TravelPlanRepository;
import com.roadrunner.user.repository.UserRepository;

@Service
@SuppressWarnings("null")
public class UserService {

    private final UserRepository userRepository;
    private final TravelPersonaRepository travelPersonaRepository;
    private final TravelPlanRepository travelPlanRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
            TravelPersonaRepository travelPersonaRepository,
            TravelPlanRepository travelPlanRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.travelPersonaRepository = travelPersonaRepository;
        this.travelPlanRepository = travelPlanRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse getCurrentUser(String userId) {
        User user = findUserById(userId);
        return mapToUserResponse(user);
    }

    public UserResponse updateProfile(String userId, UpdateProfileRequest req) {
        User user = findUserById(userId);

        if (req.getName() != null) {
            user.setName(req.getName());
        }
        if (req.getEmail() != null && !req.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(req.getEmail())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
            }
            user.setEmail(req.getEmail());
        }
        user.setAvatar(req.getAvatar());

        user = userRepository.save(user);
        return mapToUserResponse(user);
    }

    public void changePassword(String userId, ChangePasswordRequest req) {
        User user = findUserById(userId);

        if (!passwordEncoder.matches(req.getOldPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Old password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
    }

    // --- Travel Persona methods ---

    public TravelPersonaResponse addTravelPersona(String userId, TravelPersonaRequest req) {
        User user = findUserById(userId);

        TravelPersona persona = TravelPersona.builder()
                .user(user)
                .travelStyles(req.getTravelStyles())
                .interests(req.getInterests())
                .travelFrequency(req.getTravelFrequency())
                .preferredPace(req.getPreferredPace())
                .build();

        persona = travelPersonaRepository.save(persona);
        return mapToPersonaResponse(persona);
    }

    public TravelPersonaResponse updateTravelPersona(String userId, String personaId,
            TravelPersonaRequest req) {
        TravelPersona persona = travelPersonaRepository.findById(personaId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Travel persona not found"));

        if (!persona.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        if (req.getTravelStyles() != null) {
            persona.setTravelStyles(req.getTravelStyles());
        }
        if (req.getInterests() != null) {
            persona.setInterests(req.getInterests());
        }
        if (req.getTravelFrequency() != null) {
            persona.setTravelFrequency(req.getTravelFrequency());
        }
        if (req.getPreferredPace() != null) {
            persona.setPreferredPace(req.getPreferredPace());
        }

        persona = travelPersonaRepository.save(persona);
        return mapToPersonaResponse(persona);
    }

    public void deleteTravelPersona(String userId, String personaId) {
        TravelPersona persona = travelPersonaRepository.findById(personaId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Travel persona not found"));

        if (!persona.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        travelPersonaRepository.delete(persona);
    }

    public List<TravelPersonaResponse> getAllPersonas(String userId) {
        findUserById(userId);
        return travelPersonaRepository.findByUserId(userId).stream()
                .map(this::mapToPersonaResponse)
                .collect(Collectors.toList());
    }

    public TravelPlanResponse createTravelPlan(String userId, CreateTravelPlanRequest req) {
        User user = findUserById(userId);

        TravelPlan plan = TravelPlan.builder()
                .user(user)
                .selectedPlaceIds(req.getSelectedPlaceIds())
                .build();

        plan = travelPlanRepository.save(plan);
        return mapToPlanResponse(plan);
    }

    public List<TravelPlanResponse> getAllTravelPlans(String userId) {
        findUserById(userId);
        return travelPlanRepository.findByUserId(userId).stream()
                .map(this::mapToPlanResponse)
                .collect(Collectors.toList());
    }

    public TravelPlanResponse getTravelPlanById(String userId, String planId) {
        TravelPlan plan = travelPlanRepository.findById(planId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Travel plan not found"));

        if (!plan.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        return mapToPlanResponse(plan);
    }

    public void deleteTravelPlan(String userId, String planId) {
        TravelPlan plan = travelPlanRepository.findById(planId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Travel plan not found"));

        if (!plan.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        travelPlanRepository.delete(plan);
    }

    public TravelPlanResponse updateTravelPlan(String userId, String planId, CreateTravelPlanRequest req) {
        TravelPlan plan = travelPlanRepository.findById(planId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Travel plan not found"));

        if (!plan.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        if (req.getSelectedPlaceIds() != null) {
            plan.setSelectedPlaceIds(req.getSelectedPlaceIds());
        }

        plan = travelPlanRepository.save(plan);
        return mapToPlanResponse(plan);
    }

    public void deleteAccount(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        userRepository.delete(user);
    }

    private User findUserById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .avatar(user.getAvatar())
                .travelPersonas(
                        user.getTravelPersonas() != null
                                ? user.getTravelPersonas().stream()
                                        .map(this::mapToPersonaResponse)
                                        .collect(Collectors.toList())
                                : Collections.emptyList())
                .build();
    }

    private TravelPersonaResponse mapToPersonaResponse(TravelPersona persona) {
        return TravelPersonaResponse.builder()
                .id(persona.getId())
                .travelStyles(persona.getTravelStyles() != null ? persona.getTravelStyles() : Collections.emptyList())
                .interests(persona.getInterests() != null ? persona.getInterests() : Collections.emptyList())
                .travelFrequency(persona.getTravelFrequency())
                .preferredPace(persona.getPreferredPace())
                .build();
    }

    private TravelPlanResponse mapToPlanResponse(TravelPlan plan) {
        return TravelPlanResponse.builder()
                .id(plan.getId())
                .selectedPlaceIds(plan.getSelectedPlaceIds())
                .createdAt(plan.getCreatedAt())
                .build();
    }

    public List<User> getAllUsersEntity() {
        return userRepository.findAll();
    }

    public User getUserByIdEntity(String id) {
        return findUserById(id);
    }

    public User getUserByNameEntity(String name) {
        return userRepository.findAll().stream()
                .filter(u -> u.getName() != null && u.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public void setUserName(String userId, String name) {
        User user = findUserById(userId);
        user.setName(name);
        userRepository.save(user);
    }

    public void setUserEmail(String userId, String email) {
        User user = findUserById(userId);
        user.setEmail(email);
        userRepository.save(user);
    }

    public void addSelectedPlace(String userId, long placeId) {
        User user = findUserById(userId);
        TravelPlan plan;
        if (user.getTravelPlans() == null || user.getTravelPlans().isEmpty()) {
            plan = TravelPlan.builder()
                .user(user)
                .selectedPlaceIds(new java.util.ArrayList<>())
                .build();
            if (user.getTravelPlans() == null) {
                user.setTravelPlans(new java.util.ArrayList<>());
            }
            user.getTravelPlans().add(plan);
        } else {
            plan = user.getTravelPlans().get(user.getTravelPlans().size() - 1);
        }
        if (plan.getSelectedPlaceIds() == null) {
            plan.setSelectedPlaceIds(new java.util.ArrayList<>());
        }
        plan.getSelectedPlaceIds().add(String.valueOf(placeId));
        userRepository.save(user);
    }

}
