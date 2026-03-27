package com.roadrunner.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadrunner.security.JwtTokenProvider;
import com.roadrunner.user.dto.request.*;
import com.roadrunner.user.entity.TravelPersona;
import com.roadrunner.user.entity.TravelPlan;
import com.roadrunner.user.entity.User;
import com.roadrunner.user.repository.TravelPersonaRepository;
import com.roadrunner.user.repository.TravelPlanRepository;
import com.roadrunner.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SuppressWarnings("null")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Integration Tests - UserController")
class UserControllerIntegrationTest {

        private static final String TEST_EMAIL = "user-test@roadrunner.com";
        private static final String TEST_PASSWORD = "password123";
        private static final String TEST_NAME = "Test User";
        private static final String OTHER_EMAIL = "other@roadrunner.com";

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private TravelPersonaRepository travelPersonaRepository;

        @Autowired
        private TravelPlanRepository travelPlanRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @Autowired
        private JwtTokenProvider jwtTokenProvider;

        private String testToken;
        private User testUser;
        private String otherToken;
        private User otherUser;

        @BeforeEach
        void setUp() {
                travelPlanRepository.deleteAll();
                travelPersonaRepository.deleteAll();
                userRepository.deleteAll();

                testUser = User.builder()
                                .email(TEST_EMAIL)
                                .name(TEST_NAME)
                                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                                .build();
                testUser = userRepository.save(testUser);
                testToken = jwtTokenProvider.generateToken(testUser.getId());

                otherUser = User.builder()
                                .email(OTHER_EMAIL)
                                .name("Other User")
                                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                                .build();
                otherUser = userRepository.save(otherUser);
                otherToken = jwtTokenProvider.generateToken(otherUser.getId());
        }

        // --- GET /api/users/me ---

        @Test
        @DisplayName("TC-USI-011: Token ile GET /api/users/me çağrısı başarılı mı, passwordHash gizli mi")
        void shouldReturn200AndUserData_whenTokenIsValid() throws Exception {
                // given / when / then
                String responseBody = mockMvc.perform(withAuth(get("/api/users/me"), testToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.email").value(TEST_EMAIL))
                                .andExpect(jsonPath("$.id", is(not(emptyOrNullString()))))
                                .andReturn().getResponse().getContentAsString();

                org.assertj.core.api.Assertions.assertThat(responseBody)
                                .doesNotContain("passwordHash")
                                .doesNotContain("password_hash");
        }

        @Test
        @DisplayName("TC-USI-012: Token yoksa veya token expired ise /api/users/me 401 veriyor mu")
        void shouldReturn401_whenNoTokenIsProvided() throws Exception {
                mockMvc.perform(get("/api/users/me"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("TC-USI-012: Token malformed ise /api/users/me 401 veriyor mu")
        void shouldReturn401_whenTokenIsMalformed() throws Exception {
                mockMvc.perform(withAuth(get("/api/users/me"), "notavalidtoken"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("TC-USI-012: Token expired ise /api/users/me 401 veriyor mu")
        void shouldReturn401_whenTokenIsExpired() throws Exception {
                // given
                JwtTokenProvider expiredProvider = new JwtTokenProvider(
                                "dGVzdFNlY3JldEtleUZvclJvYWRSdW5uZXJUZXN0aW5nMTIz", -1000L);
                String expiredToken = expiredProvider.generateToken(testUser.getId());

                // when / then
                mockMvc.perform(withAuth(get("/api/users/me"), expiredToken))
                                .andExpect(status().isUnauthorized());
        }

        // --- PUT /api/users/me ---

        @Test
        @DisplayName("TC-USI-013: PUT /api/users/me ile profil güncelleme başarılı mı")
        void shouldReturn200_whenProfileUpdateIsValid() throws Exception {
                // given
                UpdateProfileRequest req = UpdateProfileRequest.builder()
                                .name("New Name")
                                .build();

                // when / then
                mockMvc.perform(withAuth(put("/api/users/me"), testToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value("New Name"));
        }

        @Test
        @DisplayName("TC-USI-013b: Başka kullanıcının e-postasıyla update 409 dönüyor mu")
        void shouldReturn409_whenUpdatingToEmailAlreadyUsedByAnotherUser() throws Exception {
                // given
                UpdateProfileRequest req = UpdateProfileRequest.builder()
                                .email(OTHER_EMAIL) // already used by otherUser
                                .build();

                // when / then
                mockMvc.perform(withAuth(put("/api/users/me"), testToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("TC-USI-014: Auth olmadan profil güncelleme 401 veriyor mu")
        void shouldReturn401_whenNotAuthenticatedForProfileUpdate() throws Exception {
                mockMvc.perform(put("/api/users/me")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                                .andExpect(status().isUnauthorized());
        }

        // --- PUT /api/users/me/password ---

        @Test
        @DisplayName("TC-USI-015: Şifre değiştirme endpoint’i doğru eski şifre ile 204 dönüyor mu")
        void shouldReturn204_whenPasswordChangeIsValid() throws Exception {
                // given
                ChangePasswordRequest req = ChangePasswordRequest.builder()
                                .oldPassword(TEST_PASSWORD)
                                .newPassword("newpassword123")
                                .build();

                // when
                mockMvc.perform(withAuth(put("/api/users/me/password"), testToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isNoContent());

                // then — verify login works with new password
                LoginRequest loginReq = LoginRequest.builder()
                                .email(TEST_EMAIL)
                                .password("newpassword123")
                                .build();

                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginReq)))
                                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("TC-USI-016: Yanlış eski şifreyle password change 401 veriyor mu")
        void shouldReturn401_whenOldPasswordIsWrong() throws Exception {
                // given
                ChangePasswordRequest req = ChangePasswordRequest.builder()
                                .oldPassword("wrongpassword")
                                .newPassword("newpassword123")
                                .build();

                // when / then
                mockMvc.perform(withAuth(put("/api/users/me/password"), testToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("TC-USI-016b: Kısa yeni şifre ile password change 400 veriyor mu")
        void shouldReturn400_whenNewPasswordIsTooShort() throws Exception {
                // given
                ChangePasswordRequest req = ChangePasswordRequest.builder()
                                .oldPassword(TEST_PASSWORD)
                                .newPassword("short")
                                .build();

                // when / then
                mockMvc.perform(withAuth(put("/api/users/me/password"), testToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("TC-USI-017: Auth olmadan password change 401 veriyor mu")
        void shouldReturn401_whenNotAuthenticatedForPasswordChange() throws Exception {
                mockMvc.perform(put("/api/users/me/password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                                .andExpect(status().isUnauthorized());
        }

        // --- POST /api/users/me/personas ---

        @Test
        @DisplayName("TC-USI-018: Yeni persona oluşturma başarılı mı")
        void shouldReturn201AndPersona_whenRequestIsValid() throws Exception {
                // given
                TravelPersonaRequest req = buildPersonaRequest("Tarih Rotam", true);

                // when / then
                mockMvc.perform(withAuth(post("/api/users/me/personas/new"), testToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id", is(not(emptyOrNullString()))))
                                .andExpect(jsonPath("$.name").value("Tarih Rotam"))
                                .andExpect(jsonPath("$.isDefault").value(true))
                                .andExpect(jsonPath("$.userVector.weight_tarihiAlanlar").value("0.900"));
        }

        @Test
        @DisplayName("TC-USI-019: Auth olmadan persona oluşturma 401 veriyor mu")
        void shouldReturn401_whenNotAuthenticatedForPersonaCreation() throws Exception {
                mockMvc.perform(post("/api/users/me/personas/new")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("TC-USI-020: Tüm personalar listelenebiliyor mu")
        void shouldReturn200AndPersonaList_whenGettingAllPersonas() throws Exception {
                // given — create 2 personas
                createPersonaForUser(testUser);
                createPersonaForUser(testUser);

                // when / then
                mockMvc.perform(withAuth(get("/api/users/me/personas"), testToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(2)));
        }

        // --- PUT /api/users/me/personas/{personaId} ---

        @Test
        @DisplayName("TC-USI-021: Persona güncelleme başarılı mı")
        void shouldReturn200AndUpdatedPersona_whenUpdateIsValid() throws Exception {
                // given
                TravelPersona persona = createPersonaForUser(testUser);

                TravelPersonaRequest req = TravelPersonaRequest.builder()
                                .name("Aksam Profili")
                                .socialPreference(0.90)
                                .userVector(Map.of("weight_geceHayati", "0.900"))
                                .build();

                // when / then
                mockMvc.perform(withAuth(put("/api/users/me/personas/" + persona.getId()), testToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value("Aksam Profili"));
        }

        @Test
        @DisplayName("TC-USI-039: Başka user'ın personasını update 403 veriyor mu")
        void shouldReturn403_whenPersonaBelongsToAnotherUser() throws Exception {
                // given
                TravelPersona otherPersona = createPersonaForUser(otherUser);

                TravelPersonaRequest req = TravelPersonaRequest.builder()
                                .name("Izin Yok")
                                .build();

                // when / then
                mockMvc.perform(withAuth(put("/api/users/me/personas/" + otherPersona.getId()), testToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("TC-USI-022: Olmayan persona güncellenmeye çalışılınca 404 dönüyor mu")
        void shouldReturn404_whenPersonaDoesNotExist() throws Exception {
                // given
                TravelPersonaRequest req = TravelPersonaRequest.builder().build();

                // when / then
                mockMvc.perform(withAuth(put("/api/users/me/personas/nonexistent-id"), testToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isNotFound());
        }

        // --- DELETE /api/users/me/personas/{personaId} ---

        @Test
        @DisplayName("TC-USI-023: Persona silme başarılı mı")
        void shouldReturn204_whenDeletingOwnPersona() throws Exception {
                // given
                TravelPersona persona = createPersonaForUser(testUser);

                // when / then
                mockMvc.perform(withAuth(delete("/api/users/me/personas/" + persona.getId()), testToken))
                                .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("TC-USI-041: Başka user'ın personasını silme 403 veriyor mu")
        void shouldReturn403_whenDeletingPersonaBelongsToAnotherUser() throws Exception {
                // given
                TravelPersona otherPersona = createPersonaForUser(otherUser);

                // when / then
                mockMvc.perform(withAuth(delete("/api/users/me/personas/" + otherPersona.getId()), testToken))
                                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("TC-USI-024: Olmayan persona silinmeye çalışılınca 404 dönüyor mu")
        void shouldReturn404_whenDeletingNonexistentPersona() throws Exception {
                mockMvc.perform(withAuth(delete("/api/users/me/personas/nonexistent-id"), testToken))
                                .andExpect(status().isNotFound());
        }

        // --- POST /api/users/me/plans ---

        @Test
        @DisplayName("TC-USI-025: Travel plan oluşturma endpoint’i başarılı mı")
        void shouldReturn201AndPlan_whenRequestIsValid() throws Exception {
                // given
                CreateTravelPlanRequest req = CreateTravelPlanRequest.builder()
                                .selectedPlaceIds(Arrays.asList("place1", "place2"))
                                .build();

                // when / then
                mockMvc.perform(withAuth(post("/api/users/me/plans/new"), testToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.selectedPlaceIds", hasSize(2)))
                                .andExpect(jsonPath("$.selectedPlaceIds", containsInAnyOrder("place1", "place2")));
        }

        @Test
        @DisplayName("TC-USI-025b: Auth olmadan travel plan oluşturma 401 veriyor mu")
        void shouldReturn401_whenNotAuthenticatedForPlanCreation() throws Exception {
                mockMvc.perform(post("/api/users/me/plans/new")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                                .andExpect(status().isUnauthorized());
        }

        // --- GET /api/users/me/plans/{planId} ---

        @Test
        @DisplayName("TC-USI-026: Kullanıcı kendine ait planı getirebiliyor mu")
        void shouldReturn200AndPlan_whenPlanBelongsToUser() throws Exception {
                // given
                TravelPlan plan = createPlanForUser(testUser);

                // when / then
                mockMvc.perform(withAuth(get("/api/users/me/plans/" + plan.getId()), testToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(plan.getId()));
        }

        @Test
        @DisplayName("TC-USI-026b: Başkasının travel planına erişim 403 dönüyor mu")
        void shouldReturn403_whenPlanBelongsToAnotherUser() throws Exception {
                // given
                TravelPlan otherPlan = createPlanForUser(otherUser);

                // when / then
                mockMvc.perform(withAuth(get("/api/users/me/plans/" + otherPlan.getId()), testToken))
                                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("TC-USI-027: Olmayan plan çağrılınca 404 dönüyor mu")
        void shouldReturn404_whenPlanDoesNotExist() throws Exception {
                mockMvc.perform(withAuth(get("/api/users/me/plans/nonexistent-id"), testToken))
                                .andExpect(status().isNotFound());
        }

        // --- DELETE /api/users/me/plans/{planId} ---

        @Test
        @DisplayName("TC-USI-028: Plan silme başarılı mı")
        void shouldReturn204_whenDeletingOwnPlan() throws Exception {
                // given
                TravelPlan plan = createPlanForUser(testUser);

                // when / then
                mockMvc.perform(withAuth(delete("/api/users/me/plans/" + plan.getId()), testToken))
                                .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("TC-USI-028b: Başkasının planını silmeye çalışma 403 dönüyor mu")
        void shouldReturn403_whenDeletingPlanBelongsToAnotherUser() throws Exception {
                // given
                TravelPlan otherPlan = createPlanForUser(otherUser);

                // when / then
                mockMvc.perform(withAuth(delete("/api/users/me/plans/" + otherPlan.getId()), testToken))
                                .andExpect(status().isForbidden());
        }

        // --- Helpers ---

        private MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder builder, String token) {
                return builder.header("Authorization", "Bearer " + token);
        }

        private TravelPersonaRequest buildPersonaRequest(String name, boolean isDefault) {
                return TravelPersonaRequest.builder()
                                .name(name)
                                .isDefault(isDefault)
                                .tempo(0.75)
                                .socialPreference(0.50)
                                .naturePreference(0.40)
                                .historyPreference(0.90)
                                .foodImportance(0.80)
                                .alcoholPreference(0.0)
                                .transportStyle(0.33)
                                .budgetLevel(0.50)
                                .tripLength(0.50)
                                .crowdPreference(0.25)
                                .userVector(Map.of(
                                                "weight_tarihiAlanlar", "0.900",
                                                "weight_restoranToleransi", "0.800"))
                                .build();
        }

        private TravelPersona createPersonaForUser(User user) {
                TravelPersona persona = TravelPersona.builder()
                                .user(user)
                                .name("Kayitli Profil")
                                .isDefault(false)
                                .tempo(0.75)
                                .socialPreference(0.50)
                                .naturePreference(0.40)
                                .historyPreference(0.90)
                                .foodImportance(0.80)
                                .alcoholPreference(0.0)
                                .transportStyle(0.33)
                                .budgetLevel(0.50)
                                .tripLength(0.50)
                                .crowdPreference(0.25)
                                .userVector(Map.of(
                                                "weight_tarihiAlanlar", "0.900",
                                                "weight_restoranToleransi", "0.800"))
                                .build();
                return travelPersonaRepository.save(persona);
        }

        private TravelPlan createPlanForUser(User user) {
                TravelPlan plan = TravelPlan.builder()
                                .user(user)
                                .selectedPlaceIds(Arrays.asList("place1", "place2"))
                                .build();
                return travelPlanRepository.save(plan);
        }
}
