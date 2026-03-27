package com.roadrunner.user.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
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
        purgeLegacyPersonas(userId);
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
        purgeLegacyPersonas(userId);
        clearDefaultsIfNeeded(userId, req.getIsDefault());

        TravelPersona persona = TravelPersona.builder()
                .user(user)
                .name(req.getName())
                .isDefault(Boolean.TRUE.equals(req.getIsDefault()))
                .tempo(req.getTempo())
                .socialPreference(req.getSocialPreference())
                .naturePreference(req.getNaturePreference())
                .historyPreference(req.getHistoryPreference())
                .foodImportance(req.getFoodImportance())
                .alcoholPreference(req.getAlcoholPreference())
                .transportStyle(req.getTransportStyle())
                .budgetLevel(req.getBudgetLevel())
                .tripLength(req.getTripLength())
                .crowdPreference(req.getCrowdPreference())
                .userVector(safeUserVector(req.getUserVector()))
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

        if (req.getName() != null) {
            persona.setName(req.getName());
        }
        if (req.getIsDefault() != null) {
            if (Boolean.TRUE.equals(req.getIsDefault())) {
                clearDefaultsIfNeeded(userId, true);
            }
            persona.setIsDefault(req.getIsDefault());
        }
        if (req.getTempo() != null) {
            persona.setTempo(req.getTempo());
        }
        if (req.getSocialPreference() != null) {
            persona.setSocialPreference(req.getSocialPreference());
        }
        if (req.getNaturePreference() != null) {
            persona.setNaturePreference(req.getNaturePreference());
        }
        if (req.getHistoryPreference() != null) {
            persona.setHistoryPreference(req.getHistoryPreference());
        }
        if (req.getFoodImportance() != null) {
            persona.setFoodImportance(req.getFoodImportance());
        }
        if (req.getAlcoholPreference() != null) {
            persona.setAlcoholPreference(req.getAlcoholPreference());
        }
        if (req.getTransportStyle() != null) {
            persona.setTransportStyle(req.getTransportStyle());
        }
        if (req.getBudgetLevel() != null) {
            persona.setBudgetLevel(req.getBudgetLevel());
        }
        if (req.getTripLength() != null) {
            persona.setTripLength(req.getTripLength());
        }
        if (req.getCrowdPreference() != null) {
            persona.setCrowdPreference(req.getCrowdPreference());
        }
        if (req.getUserVector() != null) {
            persona.setUserVector(safeUserVector(req.getUserVector()));
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
        purgeLegacyPersonas(userId);
        return travelPersonaRepository.findByUserId(userId).stream()
                .filter(persona -> !isLegacyPersona(persona))
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
                                        .filter(persona -> !isLegacyPersona(persona))
                                        .map(this::mapToPersonaResponse)
                                        .collect(Collectors.toList())
                                : Collections.emptyList())
                .build();
    }

    private TravelPersonaResponse mapToPersonaResponse(TravelPersona persona) {
        return TravelPersonaResponse.builder()
                .id(persona.getId())
                .name(persona.getName())
                .isDefault(Boolean.TRUE.equals(persona.getIsDefault()))
                .tempo(persona.getTempo())
                .socialPreference(persona.getSocialPreference())
                .naturePreference(persona.getNaturePreference())
                .historyPreference(persona.getHistoryPreference())
                .foodImportance(persona.getFoodImportance())
                .alcoholPreference(persona.getAlcoholPreference())
                .transportStyle(persona.getTransportStyle())
                .budgetLevel(persona.getBudgetLevel())
                .tripLength(persona.getTripLength())
                .crowdPreference(persona.getCrowdPreference())
                .userVector(safeUserVector(persona.getUserVector()))
                .build();
    }

    private void clearDefaultsIfNeeded(String userId, Boolean requestedDefault) {
        if (!Boolean.TRUE.equals(requestedDefault)) {
            return;
        }
        List<TravelPersona> personas = travelPersonaRepository.findByUserId(userId);
        for (TravelPersona persona : personas) {
            if (!isLegacyPersona(persona) && Boolean.TRUE.equals(persona.getIsDefault())) {
                persona.setIsDefault(Boolean.FALSE);
                travelPersonaRepository.save(persona);
            }
        }
    }

    private void purgeLegacyPersonas(String userId) {
        List<TravelPersona> personas = travelPersonaRepository.findByUserId(userId);
        List<TravelPersona> legacy = personas.stream()
                .filter(this::isLegacyPersona)
                .toList();
        if (!legacy.isEmpty()) {
            travelPersonaRepository.deleteAll(legacy);
        }
    }

    private boolean isLegacyPersona(TravelPersona persona) {
        return persona != null
                && (persona.getName() == null || persona.getName().isBlank()
                        || persona.getUserVector() == null
                        || persona.getUserVector().isEmpty());
    }

    private Map<String, String> safeUserVector(Map<String, String> userVector) {
        return userVector == null
                ? Collections.emptyMap()
                : userVector.entrySet().stream()
                        .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> right));
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
