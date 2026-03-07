package com.roadrunner.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadrunner.user.dto.request.LoginRequest;
import com.roadrunner.user.dto.request.RegisterRequest;
import com.roadrunner.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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
        void shouldReturn400_whenBodyIsEmpty() throws Exception {
                // given / when / then
                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                                .andExpect(status().isBadRequest());
        }

        @Test
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
