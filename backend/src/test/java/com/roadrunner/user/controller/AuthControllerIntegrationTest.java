package com.roadrunner.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadrunner.user.dto.request.LoginRequest;
import com.roadrunner.user.dto.request.RegisterRequest;
import com.roadrunner.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SuppressWarnings("null")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Integration Tests - AuthController")
class AuthControllerIntegrationTest {

        private static final String TEST_EMAIL = "auth-test@roadrunner.com";
        private static final String TEST_PASSWORD = "password123";
        private static final String TEST_NAME = "Auth Test User";

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private UserRepository userRepository;

        @BeforeEach
        void setUp() {
                userRepository.deleteAll();
        }

        // --- POST /api/auth/register ---

        @Test
        @DisplayName("TC-USI-001: POST /api/auth/register ile geçerli kayıt yapılıyor mu, 201 dönüyor mu")
        void shouldReturn201AndToken_whenRegistrationIsValid() throws Exception {
                // given
                RegisterRequest req = RegisterRequest.builder()
                                .name(TEST_NAME)
                                .email(TEST_EMAIL)
                                .password(TEST_PASSWORD)
                                .build();

                // when / then
                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.token", is(not(emptyOrNullString()))))
                                .andExpect(jsonPath("$.user.email").value(TEST_EMAIL))
                                .andExpect(jsonPath("$.user.name").value(TEST_NAME));
        }

        @Test
        @DisplayName("TC-USI-002: Aynı e-posta ile tekrar register edilince 409 dönüyor mu")
        void shouldReturn409_whenEmailIsAlreadyRegistered() throws Exception {
                // given — register first
                RegisterRequest req = RegisterRequest.builder()
                                .name(TEST_NAME)
                                .email(TEST_EMAIL)
                                .password(TEST_PASSWORD)
                                .build();

                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)));

                // when — try again with same email
                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("TC-USI-003: Boş isim ile register isteği 400 veriyor mu")
        void shouldReturn400_whenNameIsBlank() throws Exception {
                // given
                RegisterRequest req = RegisterRequest.builder()
                                .name("")
                                .email(TEST_EMAIL)
                                .password(TEST_PASSWORD)
                                .build();

                // when / then
                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.errors.name").exists());
        }

        @Test
        @DisplayName("TC-USI-004: Geçersiz e-posta formatı 400 veriyor mu")
        void shouldReturn400_whenEmailIsInvalidFormat() throws Exception {
                // given
                RegisterRequest req = RegisterRequest.builder()
                                .name(TEST_NAME)
                                .email("notanemail")
                                .password(TEST_PASSWORD)
                                .build();

                // when / then
                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.errors.email").exists());
        }

        @Test
        @DisplayName("TC-USI-005: Kısa şifre 400 veriyor mu")
        void shouldReturn400_whenPasswordIsTooShort() throws Exception {
                // given
                RegisterRequest req = RegisterRequest.builder()
                                .name(TEST_NAME)
                                .email(TEST_EMAIL)
                                .password("short")
                                .build();

                // when / then
                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.errors.password").exists());
        }

        @Test
        @DisplayName("TC-USI-006: Boş request body ile register 400 veriyor mu")
        void shouldReturn400_whenBodyIsEmpty() throws Exception {
                // given / when / then
                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("TC-USI-Register: Password hash response icinde gosterilmemeli")
        void shouldNotExposePasswordHashInResponse() throws Exception {
                // given
                RegisterRequest req = RegisterRequest.builder()
                                .name(TEST_NAME)
                                .email(TEST_EMAIL)
                                .password(TEST_PASSWORD)
                                .build();

                // when / then
                String responseBody = mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                org.assertj.core.api.Assertions.assertThat(responseBody)
                                .doesNotContain("passwordHash")
                                .doesNotContain("password_hash");
        }

        // --- POST /api/auth/login ---

        @Test
        @DisplayName("TC-USI-007: Register sonrası login endpoint’i doğru credential ile 200 dönüyor mu")
        void shouldReturn200AndToken_whenCredentialsAreValid() throws Exception {
                // given — register first
                registerTestUser();

                LoginRequest req = LoginRequest.builder()
                                .email(TEST_EMAIL)
                                .password(TEST_PASSWORD)
                                .build();

                // when / then
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.token", is(not(emptyOrNullString()))));
        }

        @Test
        @DisplayName("TC-USI-008: Yanlış şifre ile login 401 veriyor mu")
        void shouldReturn401_whenPasswordIsIncorrect() throws Exception {
                // given
                registerTestUser();

                LoginRequest req = LoginRequest.builder()
                                .email(TEST_EMAIL)
                                .password("wrongpassword")
                                .build();

                // when / then
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("TC-USI-009: Olmayan e-posta ile login 401 veriyor mu")
        void shouldReturn401_whenEmailDoesNotExist() throws Exception {
                // given
                LoginRequest req = LoginRequest.builder()
                                .email("unknown@test.com")
                                .password(TEST_PASSWORD)
                                .build();

                // when / then
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("TC-USI-010: Boş email ile login 400 veriyor mu")
        void shouldReturn400_whenLoginEmailIsBlank() throws Exception {
                // given
                LoginRequest req = LoginRequest.builder()
                                .email("")
                                .password(TEST_PASSWORD)
                                .build();

                // when / then
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("TC-USI-010: Boş password ile login 400 veriyor mu")
        void shouldReturn400_whenLoginPasswordIsBlank() throws Exception {
                // given
                LoginRequest req = LoginRequest.builder()
                                .email(TEST_EMAIL)
                                .password("")
                                .build();

                // when / then
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("TC-USI-Login: Token valid jwt formatinda olmali")
        void shouldReturnTokenThatIsValidJwt_whenLoginIsSuccessful() throws Exception {
                // given
                registerTestUser();

                LoginRequest req = LoginRequest.builder()
                                .email(TEST_EMAIL)
                                .password(TEST_PASSWORD)
                                .build();

                // when
                String responseBody = mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                // then — JWT has 3 parts separated by dots
                String token = objectMapper.readTree(responseBody).get("token").asText();
                String[] parts = token.split("\\.");
                org.assertj.core.api.Assertions.assertThat(parts).hasSize(3);
        }

        // --- Helpers ---

        private void registerTestUser() throws Exception {
                RegisterRequest req = RegisterRequest.builder()
                                .name(TEST_NAME)
                                .email(TEST_EMAIL)
                                .password(TEST_PASSWORD)
                                .build();

                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)));
        }
}
