package com.roadrunner.user.repository;

import com.roadrunner.user.entity.Chat;
import com.roadrunner.user.entity.TravelPersona;
import com.roadrunner.user.entity.TravelPlan;
import com.roadrunner.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Arrays;

@DataJpaTest
@ActiveProfiles("test")
@SuppressWarnings("null")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Integration Tests - UserRepository")
class UserRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TravelPersonaRepository travelPersonaRepository;

    @Autowired
    private TravelPlanRepository travelPlanRepository;

    @Autowired
    private ChatRepository chatRepository;

    private static final String TEST_EMAIL = "test@roadrunner.com";
    private static final String TEST_NAME = "Test User";
    private static final String TEST_PASSWORD_HASH = "$2a$10$hashedpassword";

    @BeforeEach
    void setUp() {
        chatRepository.deleteAll();
        travelPersonaRepository.deleteAll();
        travelPlanRepository.deleteAll();
        userRepository.deleteAll();
    }

    // --- findByEmail ---

    @Test
    void shouldReturnUser_whenEmailExists() {
        // given
        User user = User.builder()
                .email(TEST_EMAIL)
                .name(TEST_NAME)
                .passwordHash(TEST_PASSWORD_HASH)
                .build();
        userRepository.save(user);

        // when
        Optional<User> found = userRepository.findByEmail(TEST_EMAIL);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(found.get().getName()).isEqualTo(TEST_NAME);
    }

    @Test
    @DisplayName("TC-USI-UserRepo: Olmayan email findByEmail ile boş döner")
    void shouldReturnEmpty_whenEmailDoesNotExist() {
        // given — no user saved

        // when
        Optional<User> found = userRepository.findByEmail("nonexistent@test.com");

        // then
        assertThat(found).isEmpty();
    }

    // --- existsByEmail ---

    @Test
    @DisplayName("TC-USI-UserRepo: existsByEmail var olan email için true döner")
    void shouldReturnTrue_whenEmailExists() {
        // given
        User user = User.builder()
                .email(TEST_EMAIL)
                .name(TEST_NAME)
                .passwordHash(TEST_PASSWORD_HASH)
                .build();
        userRepository.save(user);

        // when
        boolean exists = userRepository.existsByEmail(TEST_EMAIL);

        // then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("TC-USI-UserRepo: existsByEmail olmayan email için false döner")
    void shouldReturnFalse_whenEmailDoesNotExist() {
        // given — no user saved

        // when
        boolean exists = userRepository.existsByEmail("nonexistent@test.com");

        // then
        assertThat(exists).isFalse();
    }

    // --- save / UUID auto-generation ---

    @Test
    void shouldAutoGenerateId_whenUserIsSaved() {
        // given
        User user = User.builder()
                .email(TEST_EMAIL)
                .name(TEST_NAME)
                .passwordHash(TEST_PASSWORD_HASH)
                .build();

        // when
        User saved = userRepository.save(user);

        // then
        assertThat(saved.getId()).isNotNull().isNotBlank();
    }

    // --- findById ---

    @Test
    void shouldReturnUser_whenIdExists() {
        // given
        User user = User.builder()
                .email(TEST_EMAIL)
                .name(TEST_NAME)
                .passwordHash(TEST_PASSWORD_HASH)
                .build();
        User saved = userRepository.save(user);

        // when
        Optional<User> found = userRepository.findById(saved.getId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo(TEST_EMAIL);
    }

    @Test
    void shouldReturnEmpty_whenIdDoesNotExist() {
        // given — random UUID

        // when
        Optional<User> found = userRepository.findById("non-existent-id-12345");

        // then
        assertThat(found).isEmpty();
    }

    // --- Cascade: TravelPersona ---

    @Test
    void shouldDeletePersonas_whenUserIsDeleted() {
        // given
        User user = User.builder()
                .email(TEST_EMAIL)
                .name(TEST_NAME)
                .passwordHash(TEST_PASSWORD_HASH)
                .travelPersonas(new ArrayList<>())
                .build();
        user = userRepository.save(user);

        TravelPersona p1 = TravelPersona.builder().user(user).name("Profil 1").userVector(Map.of("weight_landmark", "0.6")).build();
        TravelPersona p2 = TravelPersona.builder().user(user).name("Profil 2").userVector(Map.of("weight_tarihiAlanlar", "0.8")).build();
        travelPersonaRepository.save(p1);
        travelPersonaRepository.save(p2);

        entityManager.flush();
        entityManager.clear();

        assertThat(travelPersonaRepository.findByUserId(user.getId())).hasSize(2);

        // when
        User freshUser = userRepository.findById(user.getId()).orElseThrow();
        userRepository.delete(freshUser);
        userRepository.flush();

        // then
        assertThat(travelPersonaRepository.findAll()).isEmpty();
    }

    // --- Cascade: TravelPlan ---

    @Test
    void shouldDeletePlans_whenUserIsDeleted() {
        // given
        User user = User.builder()
                .email(TEST_EMAIL)
                .name(TEST_NAME)
                .passwordHash(TEST_PASSWORD_HASH)
                .travelPlans(new ArrayList<>())
                .build();
        user = userRepository.save(user);

        TravelPlan plan = TravelPlan.builder().user(user).selectedPlaceIds(Arrays.asList("p1", "p2")).build();
        travelPlanRepository.save(plan);

        entityManager.flush();
        entityManager.clear();

        assertThat(travelPlanRepository.findByUserId(user.getId())).hasSize(1);

        // when
        User freshUser = userRepository.findById(user.getId()).orElseThrow();
        userRepository.delete(freshUser);
        userRepository.flush();

        // then
        assertThat(travelPlanRepository.findAll()).isEmpty();
    }

    // --- Cascade: Chat ---

    @Test
    void shouldDeleteChats_whenUserIsDeleted() {
        // given
        User user = User.builder()
                .email(TEST_EMAIL)
                .name(TEST_NAME)
                .passwordHash(TEST_PASSWORD_HASH)
                .chats(new ArrayList<>())
                .build();
        user = userRepository.save(user);

        Chat chat = Chat.builder().title("Trip").user(user).messages(new ArrayList<>()).build();
        chatRepository.save(chat);

        entityManager.flush();
        entityManager.clear();

        assertThat(chatRepository.findByUserIdOrderByUpdatedAtDesc(user.getId())).hasSize(1);

        // when
        User freshUser = userRepository.findById(user.getId()).orElseThrow();
        userRepository.delete(freshUser);
        userRepository.flush();

        // then
        assertThat(chatRepository.findAll()).isEmpty();
    }
}
