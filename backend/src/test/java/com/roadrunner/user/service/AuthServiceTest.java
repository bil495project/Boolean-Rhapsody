package com.roadrunner.user.service;

import com.roadrunner.security.JwtTokenProvider;
import com.roadrunner.security.RecaptchaService;
import com.roadrunner.user.dto.request.LoginRequest;
import com.roadrunner.user.dto.request.RegisterRequest;
import com.roadrunner.user.dto.response.AuthResponse;
import com.roadrunner.user.entity.User;
import com.roadrunner.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class AuthServiceTest {

        private static final String TEST_EMAIL = "test@roadrunner.com";
        private static final String TEST_PASSWORD = "password123";
        private static final String TEST_NAME = "Test User";
        private static final String TEST_USER_ID = "user-id-123";
        private static final String TEST_TOKEN = "jwt.token.here";
        private static final String TEST_HASHED_PASSWORD = "$2a$10$hashedpassword";

        @Mock
        private UserRepository userRepository;

        @Mock
        private PasswordEncoder passwordEncoder;

        @Mock
        private JwtTokenProvider jwtTokenProvider;

        @Mock
        private RecaptchaService recaptchaService;

        @InjectMocks
        private AuthService authService;

        // --- register ---

        @Test
        void shouldReturnAuthResponse_whenRegistrationDataIsValid() {
                // given
                RegisterRequest req = RegisterRequest.builder()
                                .name(TEST_NAME)
                                .email(TEST_EMAIL)
                                .password(TEST_PASSWORD)
                                .build();

                when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
                when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn(TEST_HASHED_PASSWORD);

                User savedUser = User.builder()
                                .id(TEST_USER_ID)
                                .name(TEST_NAME)
                                .email(TEST_EMAIL)
                                .passwordHash(TEST_HASHED_PASSWORD)
                                .travelPersonas(new ArrayList<>())
                                .build();
                when(userRepository.save(any(User.class))).thenReturn(savedUser);
                when(jwtTokenProvider.generateToken(TEST_USER_ID)).thenReturn(TEST_TOKEN);
                when(jwtTokenProvider.getExpirationMs()).thenReturn(3600000L);

                // when
                AuthResponse response = authService.register(req);

                // then
                assertThat(response).isNotNull();
                assertThat(response.getToken()).isNotNull().isNotBlank();
                assertThat(response.getUser().getEmail()).isEqualTo(TEST_EMAIL);
                assertThat(response.getUser().getName()).isEqualTo(TEST_NAME);
                verify(userRepository, times(1)).save(any(User.class));
        }

        @Test
        void shouldThrowConflict_whenEmailAlreadyExists() {
                // given
                RegisterRequest req = RegisterRequest.builder()
                                .name(TEST_NAME)
                                .email(TEST_EMAIL)
                                .password(TEST_PASSWORD)
                                .build();

                when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(true);

                // when / then
                assertThatThrownBy(() -> authService.register(req))
                                .isInstanceOf(ResponseStatusException.class)
                                .satisfies(ex -> {
                                        ResponseStatusException rse = (ResponseStatusException) ex;
                                        assertThat(rse.getStatusCode().value()).isEqualTo(409);
                                });

                verify(userRepository, never()).save(any());
        }

        @Test
        void shouldStoreHashedPassword_notPlaintext() {
                // given
                RegisterRequest req = RegisterRequest.builder()
                                .name(TEST_NAME)
                                .email(TEST_EMAIL)
                                .password(TEST_PASSWORD)
                                .build();

                when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
                when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn(TEST_HASHED_PASSWORD);

                User savedUser = User.builder()
                                .id(TEST_USER_ID)
                                .name(TEST_NAME)
                                .email(TEST_EMAIL)
                                .passwordHash(TEST_HASHED_PASSWORD)
                                .travelPersonas(new ArrayList<>())
                                .build();
                when(userRepository.save(any(User.class))).thenReturn(savedUser);
                when(jwtTokenProvider.generateToken(anyString())).thenReturn(TEST_TOKEN);
                when(jwtTokenProvider.getExpirationMs()).thenReturn(3600000L);

                ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

                // when
                authService.register(req);

                // then
                verify(userRepository).save(userCaptor.capture());
                User capturedUser = userCaptor.getValue();
                assertThat(capturedUser.getPasswordHash()).isNotEqualTo(TEST_PASSWORD);
                assertThat(capturedUser.getPasswordHash()).isNotBlank();
        }

        // --- login ---

        @Test
        void shouldReturnAuthResponse_whenCredentialsAreValid() {
                // given
                LoginRequest req = LoginRequest.builder()
                                .email(TEST_EMAIL)
                                .password(TEST_PASSWORD)
                                .build();

                User user = User.builder()
                                .id(TEST_USER_ID)
                                .name(TEST_NAME)
                                .email(TEST_EMAIL)
                                .passwordHash(TEST_HASHED_PASSWORD)
                                .travelPersonas(new ArrayList<>())
                                .build();

                when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
                when(passwordEncoder.matches(TEST_PASSWORD, TEST_HASHED_PASSWORD)).thenReturn(true);
                when(jwtTokenProvider.generateToken(TEST_USER_ID)).thenReturn(TEST_TOKEN);
                when(jwtTokenProvider.getExpirationMs()).thenReturn(3600000L);

                // when
                AuthResponse response = authService.login(req);

                // then
                assertThat(response).isNotNull();
                assertThat(response.getToken()).isEqualTo(TEST_TOKEN);
                assertThat(response.getUser().getEmail()).isEqualTo(TEST_EMAIL);
        }

        @Test
        void shouldThrowUnauthorized_whenEmailDoesNotExist() {
                // given
                LoginRequest req = LoginRequest.builder()
                                .email("nonexistent@test.com")
                                .password(TEST_PASSWORD)
                                .build();

                when(userRepository.findByEmail("nonexistent@test.com")).thenReturn(Optional.empty());

                // when / then
                assertThatThrownBy(() -> authService.login(req))
                                .isInstanceOf(ResponseStatusException.class)
                                .satisfies(ex -> {
                                        ResponseStatusException rse = (ResponseStatusException) ex;
                                        assertThat(rse.getStatusCode().value()).isEqualTo(401);
                                });
        }

        @Test
        void shouldThrowUnauthorized_whenPasswordIsIncorrect() {
                // given
                LoginRequest req = LoginRequest.builder()
                                .email(TEST_EMAIL)
                                .password("wrongpassword")
                                .build();

                User user = User.builder()
                                .id(TEST_USER_ID)
                                .email(TEST_EMAIL)
                                .passwordHash(TEST_HASHED_PASSWORD)
                                .travelPersonas(new ArrayList<>())
                                .build();

                when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
                when(passwordEncoder.matches("wrongpassword", TEST_HASHED_PASSWORD)).thenReturn(false);

                // when / then
                assertThatThrownBy(() -> authService.login(req))
                                .isInstanceOf(ResponseStatusException.class)
                                .satisfies(ex -> {
                                        ResponseStatusException rse = (ResponseStatusException) ex;
                                        assertThat(rse.getStatusCode().value()).isEqualTo(401);
                                });
        }
}
