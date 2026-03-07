package com.roadrunner.user.repository;

import com.roadrunner.user.entity.Chat;
import com.roadrunner.user.entity.Message;
import com.roadrunner.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@SuppressWarnings("null")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ChatRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private User otherUser;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        chatRepository.deleteAll();
        userRepository.deleteAll();

        testUser = User.builder()
                .email("test@roadrunner.com")
                .name("Test User")
                .passwordHash("hash")
                .build();
        testUser = userRepository.save(testUser);

        otherUser = User.builder()
                .email("other@roadrunner.com")
                .name("Other User")
                .passwordHash("hash")
                .build();
        otherUser = userRepository.save(otherUser);
    }

    @Test
    void shouldPersistChatWithMessages_whenSavedWithCascade() {
        // given
        Chat chat = Chat.builder()
                .title("Trip")
                .user(testUser)
                .messages(new ArrayList<>())
                .build();
        chat = chatRepository.save(chat);

        Message m1 = Message.builder().role("user").content("Hello").chat(chat).build();
        Message m2 = Message.builder().role("assistant").content("Hi there").chat(chat).build();
        Message m3 = Message.builder().role("user").content("Plan trip").chat(chat).build();
        messageRepository.save(m1);
        messageRepository.save(m2);
        messageRepository.save(m3);

        // when
        List<Message> allMessages = messageRepository.findAll();

        // then
        assertThat(allMessages).hasSize(3);
    }

    @Test
    void shouldDeleteMessages_whenChatIsDeleted() {
        // given
        Chat chat = Chat.builder()
                .title("Trip")
                .user(testUser)
                .messages(new ArrayList<>())
                .build();
        chat = chatRepository.save(chat);

        Message m1 = Message.builder().role("user").content("Hello").chat(chat).build();
        Message m2 = Message.builder().role("assistant").content("Hi").chat(chat).build();
        messageRepository.save(m1);
        messageRepository.save(m2);

        entityManager.flush();
        entityManager.clear();

        assertThat(messageRepository.findAll()).hasSize(2);

        // when
        Chat freshChat = chatRepository.findById(chat.getId()).orElseThrow();
        chatRepository.delete(freshChat);
        chatRepository.flush();

        // then
        assertThat(messageRepository.findAll()).isEmpty();
    }

    @Test
    void shouldReturnChatsForUser_whenMultipleUsersExist() {
        // given
        Chat chat1 = Chat.builder().title("Trip 1").user(testUser).messages(new ArrayList<>()).build();
        Chat chat2 = Chat.builder().title("Trip 2").user(testUser).messages(new ArrayList<>()).build();
        Chat chat3 = Chat.builder().title("Other Trip").user(otherUser).messages(new ArrayList<>()).build();
        chatRepository.save(chat1);
        chatRepository.save(chat2);
        chatRepository.save(chat3);

        // when
        List<Chat> testUserChats = chatRepository.findByUserIdOrderByUpdatedAtDesc(testUser.getId());

        // then
        assertThat(testUserChats).hasSize(2);
        assertThat(testUserChats).allMatch(c -> c.getUser().getId().equals(testUser.getId()));
    }

    @Test
    void shouldReturnChatsOrderedByUpdatedAt_whenQueryingByUser() {
        // given
        Chat chat1 = Chat.builder().title("Old").user(testUser).messages(new ArrayList<>()).build();
        chat1 = chatRepository.save(chat1);
        chat1.setUpdatedAt(1000L);
        chatRepository.save(chat1);

        Chat chat2 = Chat.builder().title("New").user(testUser).messages(new ArrayList<>()).build();
        chat2 = chatRepository.save(chat2);
        chat2.setUpdatedAt(3000L);
        chatRepository.save(chat2);

        Chat chat3 = Chat.builder().title("Mid").user(testUser).messages(new ArrayList<>()).build();
        chat3 = chatRepository.save(chat3);
        chat3.setUpdatedAt(2000L);
        chatRepository.save(chat3);

        // when
        List<Chat> chats = chatRepository.findByUserIdOrderByUpdatedAtDesc(testUser.getId());

        // then
        assertThat(chats).hasSize(3);
        assertThat(chats.get(0).getUpdatedAt()).isGreaterThanOrEqualTo(chats.get(1).getUpdatedAt());
        assertThat(chats.get(1).getUpdatedAt()).isGreaterThanOrEqualTo(chats.get(2).getUpdatedAt());
    }
}
