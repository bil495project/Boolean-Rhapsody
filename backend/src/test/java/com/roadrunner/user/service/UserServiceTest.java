package com.roadrunner.user.service;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
@DisplayName("Unit Tests - UserService")
class UserServiceTest {

    private static final String TEST_USER_ID = "user-id-123";
    private static final String TEST_EMAIL = "test@roadrunner.com";
    private static final String TEST_NAME = "Test User";
    private static final String TEST_PASSWORD_HASH = "$2a$10$hashedpassword";
    private static final String OTHER_USER_ID = "other-user-id-456";

    @Mock
    private UserRepository userRepository;

    @Mock
    private TravelPersonaRepository travelPersonaRepository;

    @Mock
    private TravelPlanRepository travelPlanRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @org.junit.jupiter.api.BeforeEach
    void initDefaults() {
        lenient().when(travelPersonaRepository.findByUserId(any())).thenReturn(Collections.emptyList());
    }

    // --- getCurrentUser ---

    @Test
    @DisplayName("TC-US-007: Geçerli kullanıcı ID'si ile mevcut kullanıcı bilgisi getiriliyor")
    void shouldReturnUserResponse_whenUserExists() {
        // given
        User user = buildTestUser();
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));

        // when
        UserResponse response = userService.getCurrentUser(TEST_USER_ID);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(TEST_USER_ID);
        assertThat(response.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(response.getName()).isEqualTo(TEST_NAME);
    }

    @Test
    @DisplayName("TC-US-008: Olmayan kullanıcı ID'sinde 404 dönülüyor")
    void shouldThrowNotFound_whenUserDoesNotExist() {
        // given
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> userService.getCurrentUser(TEST_USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    // --- updateProfile ---

    @Test
    @DisplayName("TC-US-009: Profil adı güncelleme başarılı")
    void shouldReturnUpdatedUserResponse_whenProfileUpdateIsValid() {
        // given
        User user = buildTestUser();
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        UpdateProfileRequest req = UpdateProfileRequest.builder()
                .name("New Name")
                .avatar("new-avatar.png")
                .build();

        // when
        UserResponse response = userService.updateProfile(TEST_USER_ID, req);

        // then
        assertThat(response).isNotNull();
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("TC-US-010: Başka kullanıcıya ait alınmış e-posta ile profil güncelleme conflict veriyor")
    void shouldThrowConflict_whenNewEmailIsAlreadyTakenByAnotherUser() {
        // given
        User user = buildTestUser();
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));

        UpdateProfileRequest req = UpdateProfileRequest.builder()
                .email("taken@roadrunner.com")
                .build();

        when(userRepository.existsByEmail("taken@roadrunner.com")).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> userService.updateProfile(TEST_USER_ID, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(409));
    }

    @Test
    @DisplayName("TC-US-011: Kullanıcının kendi mevcut e-postasını tekrar vermesi conflict üretmeden kabul ediliyor")
    void shouldAllowEmailUpdate_whenNewEmailBelongsToSameUser() {
        // given
        User user = buildTestUser();
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        UpdateProfileRequest req = UpdateProfileRequest.builder()
                .email(TEST_EMAIL) // same email as current
                .build();

        // when
        UserResponse response = userService.updateProfile(TEST_USER_ID, req);

        // then
        assertThat(response).isNotNull();
        verify(userRepository, times(1)).save(any(User.class));
    }

    // --- changePassword ---

    @Test
    @DisplayName("TC-US-012: Eski şifre doğruysa şifre başarıyla değişiyor")
    void shouldChangePassword_whenOldPasswordMatches() {
        // given
        User user = buildTestUser();
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldPass123", TEST_PASSWORD_HASH)).thenReturn(true);
        when(passwordEncoder.encode("newPass456")).thenReturn("$2a$10$newhash");

        ChangePasswordRequest req = ChangePasswordRequest.builder()
                .oldPassword("oldPass123")
                .newPassword("newPass456")
                .build();

        // when
        userService.changePassword(TEST_USER_ID, req);

        // then
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("TC-US-013: Eski şifre yanlışsa şifre değişikliği reddediliyor")
    void shouldThrowUnauthorized_whenOldPasswordIsIncorrect() {
        // given
        User user = buildTestUser();
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongOld", TEST_PASSWORD_HASH)).thenReturn(false);

        ChangePasswordRequest req = ChangePasswordRequest.builder()
                .oldPassword("wrongOld")
                .newPassword("newPass456")
                .build();

        // when / then
        assertThatThrownBy(() -> userService.changePassword(TEST_USER_ID, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(401));

        verify(userRepository, never()).save(any());
    }

    // --- addTravelPersona ---

    @Test
    void shouldReturnPersonaResponse_whenPersonaIsCreated() {
        // given
        User user = buildTestUser();
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        TravelPersonaRequest req = buildPersonaRequest("Tarih Rotam", false);
        TravelPersona savedPersona = buildPersona(user, "persona-id-1", "Tarih Rotam", false);

        when(travelPersonaRepository.save(any(TravelPersona.class))).thenReturn(savedPersona);

        // when
        TravelPersonaResponse response = userService.addTravelPersona(TEST_USER_ID, req);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("persona-id-1");
        assertThat(response.getName()).isEqualTo("Tarih Rotam");
        assertThat(response.getUserVector()).containsEntry("weight_tarihiAlanlar", "0.900");
        verify(travelPersonaRepository, times(1)).save(any(TravelPersona.class));
    }

    @Test
    void shouldThrowNotFound_whenUserDoesNotExistForPersonaCreation() {
        // given
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.empty());

        TravelPersonaRequest req = TravelPersonaRequest.builder().build();

        // when / then
        assertThatThrownBy(() -> userService.addTravelPersona(TEST_USER_ID, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    // --- updateTravelPersona ---

    @Test
    void shouldReturnUpdatedPersonaResponse_whenOwnershipIsValid() {
        // given
        User user = buildTestUser();
        TravelPersona persona = buildPersona(user, "persona-id-1", "Eski Profil", false);

        when(travelPersonaRepository.findById("persona-id-1")).thenReturn(Optional.of(persona));
        when(travelPersonaRepository.save(any(TravelPersona.class))).thenReturn(persona);

        TravelPersonaRequest req = TravelPersonaRequest.builder()
                .name("Yeni Profil")
                .historyPreference(0.95)
                .userVector(Map.of("weight_tarihiAlanlar", "0.950"))
                .build();

        // when
        TravelPersonaResponse response = userService.updateTravelPersona(TEST_USER_ID, "persona-id-1", req);

        // then
        assertThat(response).isNotNull();
        verify(travelPersonaRepository, times(1)).save(any(TravelPersona.class));
    }

    @Test
    void shouldThrowNotFound_whenPersonaDoesNotExist() {
        // given
        when(travelPersonaRepository.findById("nonexistent")).thenReturn(Optional.empty());

        TravelPersonaRequest req = TravelPersonaRequest.builder().build();

        // when / then
        assertThatThrownBy(() -> userService.updateTravelPersona(TEST_USER_ID, "nonexistent", req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void shouldThrowForbidden_whenPersonaBelongsToDifferentUser() {
        // given
        User otherUser = User.builder().id(OTHER_USER_ID).email("other@test.com").build();
        TravelPersona persona = TravelPersona.builder()
                .id("persona-id-1")
                .user(otherUser)
                .build();

        when(travelPersonaRepository.findById("persona-id-1")).thenReturn(Optional.of(persona));

        TravelPersonaRequest req = TravelPersonaRequest.builder().build();

        // when / then
        assertThatThrownBy(() -> userService.updateTravelPersona(TEST_USER_ID, "persona-id-1", req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
    }

    // --- deleteTravelPersona ---

    @Test
    void shouldDeletePersona_whenOwnershipIsValid() {
        // given
        User user = buildTestUser();
        TravelPersona persona = TravelPersona.builder()
                .id("persona-id-1")
                .user(user)
                .build();

        when(travelPersonaRepository.findById("persona-id-1")).thenReturn(Optional.of(persona));

        // when
        userService.deleteTravelPersona(TEST_USER_ID, "persona-id-1");

        // then
        verify(travelPersonaRepository, times(1)).delete(persona);
    }

    @Test
    void shouldThrowForbidden_whenDeletingPersonaBelongsToDifferentUser() {
        // given
        User otherUser = User.builder().id(OTHER_USER_ID).email("other@test.com").build();
        TravelPersona persona = TravelPersona.builder()
                .id("persona-id-1")
                .user(otherUser)
                .build();

        when(travelPersonaRepository.findById("persona-id-1")).thenReturn(Optional.of(persona));

        // when / then
        assertThatThrownBy(() -> userService.deleteTravelPersona(TEST_USER_ID, "persona-id-1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void shouldThrowNotFound_whenDeletingNonexistentPersona() {
        // given
        when(travelPersonaRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> userService.deleteTravelPersona(TEST_USER_ID, "nonexistent"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    // --- getAllPersonas ---

    @Test
    void shouldReturnAllPersonas_whenUserHasMultiplePersonas() {
        // given
        User user = buildTestUser();
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));

        List<TravelPersona> personas = Arrays.asList(
                buildPersona(user, "p1", "Profil 1", false),
                buildPersona(user, "p2", "Profil 2", true),
                buildPersona(user, "p3", "Profil 3", false));
        when(travelPersonaRepository.findByUserId(TEST_USER_ID)).thenReturn(personas);

        // when
        List<TravelPersonaResponse> result = userService.getAllPersonas(TEST_USER_ID);

        // then
        assertThat(result).hasSize(3);
    }

    @Test
    void shouldKeepSingleDefault_whenCreatingNewDefaultPersona() {
        User user = buildTestUser();
        TravelPersona existingDefault = buildPersona(user, "p1", "Varsayilan", true);
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(travelPersonaRepository.findByUserId(TEST_USER_ID)).thenReturn(List.of(existingDefault));
        when(travelPersonaRepository.save(any(TravelPersona.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TravelPersonaResponse response = userService.addTravelPersona(TEST_USER_ID, buildPersonaRequest("Yeni Varsayilan", true));

        assertThat(response.getIsDefault()).isTrue();
        verify(travelPersonaRepository, atLeast(2)).save(any(TravelPersona.class));
        assertThat(existingDefault.getIsDefault()).isFalse();
    }

    @Test
    void shouldReturnEmptyList_whenUserHasNoPersonas() {
        // given
        User user = buildTestUser();
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(travelPersonaRepository.findByUserId(TEST_USER_ID)).thenReturn(Collections.emptyList());

        // when
        List<TravelPersonaResponse> result = userService.getAllPersonas(TEST_USER_ID);

        // then
        assertThat(result).isNotNull().isEmpty();
    }

    // --- createTravelPlan ---

    @Test
    @DisplayName("TC-US-014: Kullanıcı seçili place ID'leriyle travel plan oluşturabiliyor")
    void shouldReturnTravelPlanResponse_whenPlanIsCreated() {
        // given
        User user = buildTestUser();
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));

        CreateTravelPlanRequest req = CreateTravelPlanRequest.builder()
                .selectedPlaceIds(Arrays.asList("place1", "place2"))
                .build();

        TravelPlan savedPlan = TravelPlan.builder()
                .id("plan-id-1")
                .user(user)
                .selectedPlaceIds(Arrays.asList("place1", "place2"))
                .createdAt(System.currentTimeMillis())
                .build();

        when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(savedPlan);

        // when
        TravelPlanResponse response = userService.createTravelPlan(TEST_USER_ID, req);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getSelectedPlaceIds()).containsExactly("place1", "place2");
        verify(travelPlanRepository, times(1)).save(any(TravelPlan.class));
    }

    @Test
    void shouldStoreCommaSeparatedIds_whenMultipleIdsProvided() {
        // given
        User user = buildTestUser();
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));

        CreateTravelPlanRequest req = CreateTravelPlanRequest.builder()
                .selectedPlaceIds(Arrays.asList("id1", "id2", "id3"))
                .build();

        TravelPlan savedPlan = TravelPlan.builder()
                .id("plan-id-1")
                .user(user)
                .selectedPlaceIds(Arrays.asList("id1", "id2", "id3"))
                .createdAt(System.currentTimeMillis())
                .build();
        when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(savedPlan);

        ArgumentCaptor<TravelPlan> planCaptor = ArgumentCaptor.forClass(TravelPlan.class);

        // when
        userService.createTravelPlan(TEST_USER_ID, req);

        // then
        verify(travelPlanRepository).save(planCaptor.capture());
        assertThat(planCaptor.getValue().getSelectedPlaceIds()).containsExactly("id1", "id2", "id3");
    }

    // --- getTravelPlanById ---

    @Test
    @DisplayName("TC-US-015: Kullanıcı kendisine ait travel plan'i ID ile alabiliyor")
    void shouldReturnPlan_whenOwnershipIsValid() {
        // given
        User user = buildTestUser();
        TravelPlan plan = TravelPlan.builder()
                .id("plan-id-1")
                .user(user)
                .selectedPlaceIds(Arrays.asList("p1", "p2"))
                .createdAt(System.currentTimeMillis())
                .build();

        when(travelPlanRepository.findById("plan-id-1")).thenReturn(Optional.of(plan));

        // when
        TravelPlanResponse response = userService.getTravelPlanById(TEST_USER_ID, "plan-id-1");

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("plan-id-1");
    }

    @Test
    @DisplayName("TC-US-016: Başkasına ait travel plan'e erişim forbidden veriyor")
    void shouldThrowForbidden_whenPlanBelongsToDifferentUser() {
        // given
        User otherUser = User.builder().id(OTHER_USER_ID).email("other@test.com").build();
        TravelPlan plan = TravelPlan.builder()
                .id("plan-id-1")
                .user(otherUser)
                .build();

        when(travelPlanRepository.findById("plan-id-1")).thenReturn(Optional.of(plan));

        // when / then
        assertThatThrownBy(() -> userService.getTravelPlanById(TEST_USER_ID, "plan-id-1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
    }

    @Test
    @DisplayName("TC-US-017: Olmayan travel plan istendiğinde 404 dönüyor")
    void shouldThrowNotFound_whenPlanDoesNotExist() {
        // given
        when(travelPlanRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> userService.getTravelPlanById(TEST_USER_ID, "nonexistent"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    // --- deleteTravelPlan ---

    @Test
    @DisplayName("TC-US-018: Kullanıcı kendine ait travel plan'i silebiliyor")
    void shouldDeletePlan_whenOwnershipIsValid() {
        // given
        User user = buildTestUser();
        TravelPlan plan = TravelPlan.builder()
                .id("plan-id-1")
                .user(user)
                .build();

        when(travelPlanRepository.findById("plan-id-1")).thenReturn(Optional.of(plan));

        // when
        userService.deleteTravelPlan(TEST_USER_ID, "plan-id-1");

        // then
        verify(travelPlanRepository, times(1)).delete(plan);
    }

    @Test
    @DisplayName("TC-US-019: Başkasına ait travel plan'i silmeye çalışınca forbidden veriyor")
    void shouldThrowForbidden_whenDeletingPlanBelongsToDifferentUser() {
        // given
        User otherUser = User.builder().id(OTHER_USER_ID).email("other@test.com").build();
        TravelPlan plan = TravelPlan.builder()
                .id("plan-id-1")
                .user(otherUser)
                .build();

        when(travelPlanRepository.findById("plan-id-1")).thenReturn(Optional.of(plan));

        // when / then
        assertThatThrownBy(() -> userService.deleteTravelPlan(TEST_USER_ID, "plan-id-1"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
    }

    // --- helper ---

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

    private TravelPersona buildPersona(User user, String id, String name, boolean isDefault) {
        return TravelPersona.builder()
                .id(id)
                .user(user)
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

    private User buildTestUser() {
        return User.builder()
                .id(TEST_USER_ID)
                .email(TEST_EMAIL)
                .name(TEST_NAME)
                .passwordHash(TEST_PASSWORD_HASH)
                .travelPersonas(new ArrayList<>())
                .travelPlans(new ArrayList<>())
                .chats(new ArrayList<>())
                .build();
    }
}
