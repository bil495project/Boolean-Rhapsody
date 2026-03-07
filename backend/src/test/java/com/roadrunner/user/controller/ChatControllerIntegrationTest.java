package com.roadrunner.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadrunner.security.JwtTokenProvider;
import com.roadrunner.user.dto.request.AddMessageRequest;
import com.roadrunner.user.dto.request.CreateChatRequest;
import com.roadrunner.user.entity.Chat;
import com.roadrunner.user.entity.Message;
import com.roadrunner.user.entity.User;
import com.roadrunner.user.repository.ChatRepository;
import com.roadrunner.user.repository.MessageRepository;
import com.roadrunner.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.ArrayList;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SuppressWarnings("null")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChatControllerIntegrationTest {

        private static final String TEST_EMAIL = "chat-test@roadrunner.com";
        private static final String TEST_PASSWORD = "password123";
        private static final String TEST_NAME = "Chat Test User";
        private static final String OTHER_EMAIL = "chat-other@roadrunner.com";

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private ChatRepository chatRepository;

        @Autowired
        private MessageRepository messageRepository;

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
                messageRepository.deleteAll();
                chatRepository.deleteAll();
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

        // --- POST /api/chats ---

        @Test
        void shouldReturn201AndChat_whenRequestIsValid() throws Exception {
                // given
                CreateChatRequest req = CreateChatRequest.builder()
                                .title("Tokyo Trip")
                                .duration("3 days")
                                .build();

                // when / then
                mockMvc.perform(withAuth(post("/api/chats/new"), testToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.title").value("Tokyo Trip"))
                                .andExpect(jsonPath("$.messages", hasSize(0)))
                                .andExpect(jsonPath("$.id", is(not(emptyOrNullString()))));
        }

        @Test
        void shouldReturn201WithNullDuration_whenDurationIsNotProvided() throws Exception {
                // given
                CreateChatRequest req = CreateChatRequest.builder()
                                .title("Trip")
                                .build();

                // when / then
                mockMvc.perform(withAuth(post("/api/chats/new"), testToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.title").value("Trip"));
        }

        @Test
        void shouldReturn400_whenTitleIsBlank() throws Exception {
                // given
                CreateChatRequest req = CreateChatRequest.builder()
                                .title("")
                                .build();

                // when / then
                mockMvc.perform(withAuth(post("/api/chats/new"), testToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn401_whenNotAuthenticatedForChatCreation() throws Exception {
                mockMvc.perform(post("/api/chats/new")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                                .andExpect(status().isUnauthorized());
        }

        // --- GET /api/chats ---

        @Test
        void shouldReturn200AndOrderedChatList_whenUserHasChats() throws Exception {
                // given — create 3 chats with distinct timestamps (insertion order determines
                // updatedAt)
                createChatForUser(testUser, "Old Chat");
                Thread.sleep(50);
                createChatForUser(testUser, "Mid Chat");
                Thread.sleep(50);
                createChatForUser(testUser, "New Chat");

                // when / then — ordered by updatedAt DESC (newest first)
                mockMvc.perform(withAuth(get("/api/chats"), testToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(3)))
                                .andExpect(jsonPath("$[0].title").value("New Chat"))
                                .andExpect(jsonPath("$[1].title").value("Mid Chat"))
                                .andExpect(jsonPath("$[2].title").value("Old Chat"));
        }

        @Test
        void shouldReturn200AndEmptyList_whenUserHasNoChats() throws Exception {
                mockMvc.perform(withAuth(get("/api/chats"), testToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void shouldReturn401_whenNotAuthenticatedForChatList() throws Exception {
                mockMvc.perform(get("/api/chats"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldNotReturnChatsOfOtherUsers() throws Exception {
                // given
                createChatForUser(testUser, "My Chat");
                createChatForUser(otherUser, "Other Chat");

                // when / then
                mockMvc.perform(withAuth(get("/api/chats"), testToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(1)))
                                .andExpect(jsonPath("$[0].title").value("My Chat"));
        }

        // --- GET /api/chats/{chatId} ---

        @Test
        void shouldReturn200AndChatWithMessages_whenChatBelongsToUser() throws Exception {
                // given
                Chat chat = createChatForUser(testUser, "Trip");
                addMessageToChat(chat, "user", "Hello");
                addMessageToChat(chat, "assistant", "Hi there");

                // when / then
                mockMvc.perform(withAuth(get("/api/chats/" + chat.getId()), testToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.messages", hasSize(2)));
        }

        @Test
        void shouldReturn403_whenChatBelongsToAnotherUser() throws Exception {
                // given
                Chat otherChat = createChatForUser(otherUser, "Other Trip");

                // when / then
                mockMvc.perform(withAuth(get("/api/chats/" + otherChat.getId()), testToken))
                                .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturn404_whenChatDoesNotExist() throws Exception {
                mockMvc.perform(withAuth(get("/api/chats/nonexistent-id"), testToken))
                                .andExpect(status().isNotFound());
        }

        // --- DELETE /api/chats/{chatId} ---

        @Test
        void shouldReturn204_whenDeletionIsValid() throws Exception {
                // given
                Chat chat = createChatForUser(testUser, "To Delete");

                // when
                mockMvc.perform(withAuth(delete("/api/chats/" + chat.getId()), testToken))
                                .andExpect(status().isNoContent());

                // then — verify it's gone
                mockMvc.perform(withAuth(get("/api/chats/" + chat.getId()), testToken))
                                .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn403_whenDeletingChatOfAnotherUser() throws Exception {
                // given
                Chat otherChat = createChatForUser(otherUser, "Other Trip");

                // when / then
                mockMvc.perform(withAuth(delete("/api/chats/" + otherChat.getId()), testToken))
                                .andExpect(status().isForbidden());
        }

        // --- PUT /api/chats/{chatId}/title ---

        @Test
        void shouldReturn200AndUpdatedTitle_whenTitleChangeIsValid() throws Exception {
                // given
                Chat chat = createChatForUser(testUser, "Old Title");

                Map<String, String> body = Map.of("title", "New Title");

                // when / then
                mockMvc.perform(withAuth(put("/api/chats/" + chat.getId() + "/title"), testToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.title").value("New Title"));
        }

        @Test
        void shouldReturn403_whenUpdatingTitleOfAnotherUsersChat() throws Exception {
                // given
                Chat otherChat = createChatForUser(otherUser, "Other Title");

                Map<String, String> body = Map.of("title", "Hacked Title");

                // when / then
                mockMvc.perform(withAuth(put("/api/chats/" + otherChat.getId() + "/title"), testToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                                .andExpect(status().isForbidden());
        }

        // --- POST /api/chats/{chatId}/messages ---

        @Test
        void shouldReturn201AndUpdatedChat_whenMessageIsAdded() throws Exception {
                // given
                Chat chat = createChatForUser(testUser, "Trip");

                AddMessageRequest req = AddMessageRequest.builder()
                                .role("user")
                                .content("Plan a trip to Kyoto")
                                .build();

                // when / then
                mockMvc.perform(withAuth(post("/api/chats/" + chat.getId() + "/messages"), testToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.messages[-1:].role", contains("user")))
                                .andExpect(jsonPath("$.messages[-1:].content", contains("Plan a trip to Kyoto")));
        }

        @Test
        void shouldReturn201AndAssistantMessage_whenAssistantRoleIsUsed() throws Exception {
                // given
                Chat chat = createChatForUser(testUser, "Trip");

                AddMessageRequest req = AddMessageRequest.builder()
                                .role("assistant")
                                .content("Here's your itinerary")
                                .build();

                // when / then
                mockMvc.perform(withAuth(post("/api/chats/" + chat.getId() + "/messages"), testToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.messages[-1:].role", contains("assistant")));
        }

        @Test
        void shouldReturn400_whenRoleIsBlank() throws Exception {
                // given
                Chat chat = createChatForUser(testUser, "Trip");

                AddMessageRequest req = AddMessageRequest.builder()
                                .role("")
                                .content("Hello")
                                .build();

                // when / then
                mockMvc.perform(withAuth(post("/api/chats/" + chat.getId() + "/messages"), testToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn400_whenContentIsBlank() throws Exception {
                // given
                Chat chat = createChatForUser(testUser, "Trip");

                AddMessageRequest req = AddMessageRequest.builder()
                                .role("user")
                                .content("")
                                .build();

                // when / then
                mockMvc.perform(withAuth(post("/api/chats/" + chat.getId() + "/messages"), testToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn403_whenAddingMessageToChatOfAnotherUser() throws Exception {
                // given
                Chat otherChat = createChatForUser(otherUser, "Other Trip");

                AddMessageRequest req = AddMessageRequest.builder()
                                .role("user")
                                .content("Hello")
                                .build();

                // when / then
                mockMvc.perform(withAuth(post("/api/chats/" + otherChat.getId() + "/messages"), testToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturn404_whenAddingMessageToNonexistentChat() throws Exception {
                // given
                AddMessageRequest req = AddMessageRequest.builder()
                                .role("user")
                                .content("Hello")
                                .build();

                // when / then
                mockMvc.perform(withAuth(post("/api/chats/nonexistent-id/messages"), testToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isNotFound());
        }

        @Test
        void shouldUpdateChatUpdatedAt_whenMessageIsAdded() throws Exception {
                // given
                Chat chat = createChatForUser(testUser, "Trip");
                long originalUpdatedAt = chat.getUpdatedAt();

                AddMessageRequest req = AddMessageRequest.builder()
                                .role("user")
                                .content("Hello")
                                .build();

                // when
                String responseBody = mockMvc
                                .perform(withAuth(post("/api/chats/" + chat.getId() + "/messages"), testToken)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                // then
                long updatedAt = objectMapper.readTree(responseBody).get("updatedAt").asLong();
                org.assertj.core.api.Assertions.assertThat(updatedAt).isGreaterThanOrEqualTo(originalUpdatedAt);
        }

        // --- Helpers ---

        private MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder builder, String token) {
                return builder.header("Authorization", "Bearer " + token);
        }

        private Chat createChatForUser(User user, String title) {
                Chat chat = Chat.builder()
                                .title(title)
                                .user(user)
                                .messages(new ArrayList<>())
                                .build();
                return chatRepository.save(chat);
        }

        private void addMessageToChat(Chat chat, String role, String content) {
                Message message = Message.builder()
                                .role(role)
                                .content(content)
                                .chat(chat)
                                .build();
                message = messageRepository.save(message);
                chat.getMessages().add(message);
        }
}
