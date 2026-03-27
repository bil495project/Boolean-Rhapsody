package com.roadrunner.user.service;

import java.util.Collections;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.roadrunner.security.JwtTokenProvider;
import com.roadrunner.security.RecaptchaService;
import com.roadrunner.user.dto.request.LoginRequest;
import com.roadrunner.user.dto.request.RegisterRequest;
import com.roadrunner.user.dto.response.AuthResponse;
import com.roadrunner.user.dto.response.TravelPersonaResponse;
import com.roadrunner.user.dto.response.UserResponse;
import com.roadrunner.user.entity.User;
import com.roadrunner.user.repository.UserRepository;

@Service
@SuppressWarnings("null")
public class AuthService {

        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtTokenProvider jwtTokenProvider;
        private final RecaptchaService recaptchaService;

        public AuthService(UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        JwtTokenProvider jwtTokenProvider,
                        RecaptchaService recaptchaService) {
                this.userRepository = userRepository;
                this.passwordEncoder = passwordEncoder;
                this.jwtTokenProvider = jwtTokenProvider;
                this.recaptchaService = recaptchaService;
        }

        public AuthResponse register(RegisterRequest req) {
                recaptchaService.verify(req.getRecaptchaToken(), "signup");

                if (userRepository.existsByEmail(req.getEmail())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
                }

                User user = User.builder()
                                .email(req.getEmail())
                                .name(req.getName())
                                .passwordHash(passwordEncoder.encode(req.getPassword()))
                                .build();

                user = userRepository.save(user);

                String token = jwtTokenProvider.generateToken(user.getId());
                long expiresAt = System.currentTimeMillis() + jwtTokenProvider.getExpirationMs();

                return AuthResponse.builder()
                                .token(token)
                                .expiresAt(expiresAt)
                                .user(mapToUserResponse(user))
                                .build();
        }

        public AuthResponse login(LoginRequest req) {
                recaptchaService.verify(req.getRecaptchaToken(), "login");

                User user = userRepository.findByEmail(req.getEmail())
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "Invalid email or password"));

                if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
                        throw new ResponseStatusException(
                                        HttpStatus.UNAUTHORIZED, "Invalid email or password");
                }

                String token = jwtTokenProvider.generateToken(user.getId());
                long expiresAt = System.currentTimeMillis() + jwtTokenProvider.getExpirationMs();

                return AuthResponse.builder()
                                .token(token)
                                .expiresAt(expiresAt)
                                .user(mapToUserResponse(user))
                                .build();
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
                                                                                .filter(p -> p.getName() != null
                                                                                                && !p.getName().isBlank()
                                                                                                && p.getUserVector() != null
                                                                                                && !p.getUserVector().isEmpty())
                                                                                .map(p -> TravelPersonaResponse
                                                                                                .builder()
                                                                                                .id(p.getId())
                                                                                                .name(p.getName())
                                                                                                .isDefault(Boolean.TRUE.equals(p.getIsDefault()))
                                                                                                .tempo(p.getTempo())
                                                                                                .socialPreference(p.getSocialPreference())
                                                                                                .naturePreference(p.getNaturePreference())
                                                                                                .historyPreference(p.getHistoryPreference())
                                                                                                .foodImportance(p.getFoodImportance())
                                                                                                .alcoholPreference(p.getAlcoholPreference())
                                                                                                .transportStyle(p.getTransportStyle())
                                                                                                .budgetLevel(p.getBudgetLevel())
                                                                                                .tripLength(p.getTripLength())
                                                                                                .crowdPreference(p.getCrowdPreference())
                                                                                                .userVector(p.getUserVector())
                                                                                                .build())
                                                                                .collect(Collectors.toList())
                                                                : Collections.emptyList())
                                .build();
        }
}
