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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
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

    // --- getCurrentUser ---

    @Test
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

        TravelPersonaRequest req = TravelPersonaRequest.builder()
                .travelStyles(Arrays.asList("adventure", "relaxation"))
                .interests(Arrays.asList("history", "food"))
                .travelFrequency("monthly")
                .preferredPace("fast")
                .build();

        TravelPersona savedPersona = TravelPersona.builder()
                .id("persona-id-1")
                .user(user)
                .travelStyles("adventure,relaxation")
                .interests("history,food")
                .travelFrequency("monthly")
                .preferredPace("fast")
                .build();

        when(travelPersonaRepository.save(any(TravelPersona.class))).thenReturn(savedPersona);

        // when
        TravelPersonaResponse response = userService.addTravelPersona(TEST_USER_ID, req);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("persona-id-1");
        assertThat(response.getTravelStyles()).containsExactly("adventure", "relaxation");
        assertThat(response.getInterests()).containsExactly("history", "food");
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
        TravelPersona persona = TravelPersona.builder()
                .id("persona-id-1")
                .user(user)
                .travelStyles("old")
                .interests("old")
                .build();

        when(travelPersonaRepository.findById("persona-id-1")).thenReturn(Optional.of(persona));
        when(travelPersonaRepository.save(any(TravelPersona.class))).thenReturn(persona);

        TravelPersonaRequest req = TravelPersonaRequest.builder()
                .travelStyles(Arrays.asList("new-style"))
                .interests(Arrays.asList("new-interest"))
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
                TravelPersona.builder().id("p1").user(user).travelStyles("a").interests("b").build(),
                TravelPersona.builder().id("p2").user(user).travelStyles("c").interests("d").build(),
                TravelPersona.builder().id("p3").user(user).travelStyles("e").interests("f").build());
        when(travelPersonaRepository.findByUserId(TEST_USER_ID)).thenReturn(personas);

        // when
        List<TravelPersonaResponse> result = userService.getAllPersonas(TEST_USER_ID);

        // then
        assertThat(result).hasSize(3);
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
                .selectedPlaceIds("place1,place2")
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
                .selectedPlaceIds("id1,id2,id3")
                .createdAt(System.currentTimeMillis())
                .build();
        when(travelPlanRepository.save(any(TravelPlan.class))).thenReturn(savedPlan);

        ArgumentCaptor<TravelPlan> planCaptor = ArgumentCaptor.forClass(TravelPlan.class);

        // when
        userService.createTravelPlan(TEST_USER_ID, req);

        // then
        verify(travelPlanRepository).save(planCaptor.capture());
        assertThat(planCaptor.getValue().getSelectedPlaceIds()).isEqualTo("id1,id2,id3");
    }

    // --- getTravelPlanById ---

    @Test
    void shouldReturnPlan_whenOwnershipIsValid() {
        // given
        User user = buildTestUser();
        TravelPlan plan = TravelPlan.builder()
                .id("plan-id-1")
                .user(user)
                .selectedPlaceIds("p1,p2")
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
