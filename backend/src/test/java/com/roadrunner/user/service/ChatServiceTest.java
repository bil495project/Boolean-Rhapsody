package com.roadrunner.user.service;

import com.roadrunner.user.dto.request.AddMessageRequest;
import com.roadrunner.user.dto.request.CreateChatRequest;
import com.roadrunner.user.dto.response.ChatResponse;
import com.roadrunner.user.entity.Chat;
import com.roadrunner.user.entity.Message;
import com.roadrunner.user.entity.User;
import com.roadrunner.user.repository.ChatRepository;
import com.roadrunner.user.repository.MessageRepository;
import com.roadrunner.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
@DisplayName("Unit Tests - ChatService")
class ChatServiceTest {

    private static final String TEST_USER_ID = "user-id-123";
    private static final String TEST_CHAT_ID = "chat-id-456";
    private static final String OTHER_USER_ID = "other-user-id-789";

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ChatService chatService;

    // --- createChat ---

    @Test
    @DisplayName("TC-US-ChatCreate: Chat oluşturma başarılı (Kapsam dışı ama gerekli)")
    void shouldReturnChatResponse_whenChatIsCreated() {
        // given
        User user = buildTestUser();
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));

        CreateChatRequest req = CreateChatRequest.builder()
                .title("Tokyo Trip")
                .duration("3 days")
                .build();

        Chat savedChat = Chat.builder()
                .id(TEST_CHAT_ID)
                .title("Tokyo Trip")
                .duration("3 days")
                .user(user)
                .createdAt(System.currentTimeMillis())
                .updatedAt(System.currentTimeMillis())
                .messages(new ArrayList<>())
                .build();

        when(chatRepository.save(any(Chat.class))).thenReturn(savedChat);

        // when
        ChatResponse response = chatService.createChat(TEST_USER_ID, req);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Tokyo Trip");
        assertThat(response.getDuration()).isEqualTo("3 days");
        verify(chatRepository, times(1)).save(any(Chat.class));
    }

    @Test
    @DisplayName("TC-US-ChatCreate: Chat oluşturulduğunda createdAt/updatedAt atanır")
    void shouldSetCreatedAtAndUpdatedAt_whenChatIsCreated() {
        // given
        User user = buildTestUser();
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));

        CreateChatRequest req = CreateChatRequest.builder()
                .title("Trip")
                .build();

        long now = System.currentTimeMillis();
        Chat savedChat = Chat.builder()
                .id(TEST_CHAT_ID)
                .title("Trip")
                .user(user)
                .createdAt(now)
                .updatedAt(now)
                .messages(new ArrayList<>())
                .build();

        when(chatRepository.save(any(Chat.class))).thenReturn(savedChat);

        // when
        ChatResponse response = chatService.createChat(TEST_USER_ID, req);

        // then
        assertThat(response.getCreatedAt()).isGreaterThan(0);
        assertThat(response.getUpdatedAt()).isGreaterThan(0);
    }

    // --- getAllChats ---

    @Test
    @DisplayName("TC-US-020: Kullanıcının chat listesi updatedAt’e göre azalan sırada geliyor mu")
    void shouldReturnChatsOrderedByUpdatedAtDesc_whenMultipleChatsExist() {
        // given
        User user = buildTestUser();
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));

        Chat chat1 = Chat.builder().id("c1").title("Old").user(user).updatedAt(1000L).messages(new ArrayList<>())
                .build();
        Chat chat2 = Chat.builder().id("c2").title("New").user(user).updatedAt(3000L).messages(new ArrayList<>())
                .build();
        Chat chat3 = Chat.builder().id("c3").title("Mid").user(user).updatedAt(2000L).messages(new ArrayList<>())
                .build();

        // Repository returns in order (desc)
        when(chatRepository.findByUserIdOrderByUpdatedAtDesc(TEST_USER_ID))
                .thenReturn(Arrays.asList(chat2, chat3, chat1));

        // when
        List<ChatResponse> result = chatService.getAllChats(TEST_USER_ID);

        // then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getUpdatedAt()).isGreaterThanOrEqualTo(result.get(1).getUpdatedAt());
        assertThat(result.get(1).getUpdatedAt()).isGreaterThanOrEqualTo(result.get(2).getUpdatedAt());
    }

    @Test
    @DisplayName("TC-US-021: Kullanıcının hiç chat’i yoksa boş liste dönüyor mu")
    void shouldReturnEmptyList_whenUserHasNoChats() {
        // given
        User user = buildTestUser();
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(chatRepository.findByUserIdOrderByUpdatedAtDesc(TEST_USER_ID)).thenReturn(Collections.emptyList());

        // when
        List<ChatResponse> result = chatService.getAllChats(TEST_USER_ID);

        // then
        assertThat(result).isNotNull().isEmpty();
    }

    // --- getChatById ---

    @Test
    @DisplayName("TC-US-022: Kullanıcı kendine ait chat’i mesajlarıyla birlikte alabiliyor mu")
    void shouldReturnChatWithMessages_whenOwnershipIsValid() {
        // given
        User user = buildTestUser();
        Chat chat = buildTestChat(user);

        Message msg = Message.builder().id("msg-1").role("user").content("Hello").timestamp(1000L).chat(chat).build();
        chat.setMessages(List.of(msg));

        when(chatRepository.findById(TEST_CHAT_ID)).thenReturn(Optional.of(chat));

        // when
        ChatResponse response = chatService.getChatById(TEST_USER_ID, TEST_CHAT_ID);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getMessages()).hasSize(1);
        assertThat(response.getMessages().get(0).getContent()).isEqualTo("Hello");
    }

    @Test
    @DisplayName("TC-US-022b: Chat mesajları timestamp'e göre artan sırada dönüyor mu")
    void shouldReturnMessagesOrderedByTimestampAsc_whenChatMessagesAreUnordered() {
        // given
        User user = buildTestUser();
        Chat chat = buildTestChat(user);

        Message newerMessage = Message.builder()
                .id("msg-2")
                .role("assistant")
                .content("Second")
                .timestamp(2000L)
                .chat(chat)
                .build();

        Message olderMessage = Message.builder()
                .id("msg-1")
                .role("user")
                .content("First")
                .timestamp(1000L)
                .chat(chat)
                .build();

        chat.setMessages(List.of(newerMessage, olderMessage));

        when(chatRepository.findById(TEST_CHAT_ID)).thenReturn(Optional.of(chat));

        // when
        ChatResponse response = chatService.getChatById(TEST_USER_ID, TEST_CHAT_ID);

        // then
        assertThat(response.getMessages()).hasSize(2);
        assertThat(response.getMessages().get(0).getContent()).isEqualTo("First");
        assertThat(response.getMessages().get(1).getContent()).isEqualTo("Second");
    }

    @Test
    @DisplayName("TC-US-ChatGet: Başkasına ait chat istenince 403 Forbidden döner")
    void shouldThrowForbidden_whenChatBelongsToDifferentUser() {
        // given
        User otherUser = User.builder().id(OTHER_USER_ID).email("other@test.com").build();
        Chat chat = buildTestChat(otherUser);

        when(chatRepository.findById(TEST_CHAT_ID)).thenReturn(Optional.of(chat));

        // when / then
        assertThatThrownBy(() -> chatService.getChatById(TEST_USER_ID, TEST_CHAT_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
    }

    @Test
    @DisplayName("TC-US-ChatGet: Olmayan chat istenince 404 NotFound döner")
    void shouldThrowNotFound_whenChatDoesNotExist() {
        // given
        when(chatRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> chatService.getChatById(TEST_USER_ID, "nonexistent"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    // --- addMessage ---

    @Test
    @DisplayName("TC-US-ChatMsg: Chat'e yeni mesaj eklendiğinde mesaj listesi doluyor mu")
    void shouldReturnUpdatedChat_whenMessageIsAdded() {
        // given
        User user = buildTestUser();
        Chat chat = buildTestChat(user);

        when(chatRepository.findById(TEST_CHAT_ID)).thenReturn(Optional.of(chat));

        Message savedMsg = Message.builder()
                .id("msg-1").role("user").content("Hello").timestamp(System.currentTimeMillis()).chat(chat).build();
        when(messageRepository.save(any(Message.class))).thenReturn(savedMsg);

        Chat updatedChat = Chat.builder()
                .id(TEST_CHAT_ID).title("Trip").user(user)
                .createdAt(chat.getCreatedAt()).updatedAt(System.currentTimeMillis())
                .messages(List.of(savedMsg)).build();
        when(chatRepository.save(any(Chat.class))).thenReturn(updatedChat);

        // For the reload
        when(chatRepository.findById(TEST_CHAT_ID))
                .thenReturn(Optional.of(chat))
                .thenReturn(Optional.of(updatedChat));

        AddMessageRequest req = AddMessageRequest.builder()
                .role("user")
                .content("Hello")
                .build();

        // when
        ChatResponse response = chatService.addMessage(TEST_USER_ID, TEST_CHAT_ID, req);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getMessages()).isNotEmpty();
        verify(chatRepository, times(1)).save(any(Chat.class));
    }

    @Test
    @DisplayName("TC-US-ChatMsg: Mesaj eklendiğinde Chat'in updatedAt değeri güncelleniyor mu")
    void shouldUpdateChatUpdatedAt_whenMessageIsAdded() {
        // given
        User user = buildTestUser();
        long originalTime = 1000L;
        Chat chat = Chat.builder()
                .id(TEST_CHAT_ID).title("Trip").user(user)
                .createdAt(originalTime).updatedAt(originalTime)
                .messages(new ArrayList<>()).build();

        when(chatRepository.findById(TEST_CHAT_ID)).thenReturn(Optional.of(chat));
        when(messageRepository.save(any(Message.class))).thenReturn(
                Message.builder().id("msg-1").role("user").content("Hi").timestamp(System.currentTimeMillis())
                        .chat(chat).build());

        Chat updatedChat = Chat.builder()
                .id(TEST_CHAT_ID).title("Trip").user(user)
                .createdAt(originalTime).updatedAt(System.currentTimeMillis())
                .messages(new ArrayList<>()).build();
        when(chatRepository.save(any(Chat.class))).thenReturn(updatedChat);

        // Reload returns the updated chat
        when(chatRepository.findById(TEST_CHAT_ID))
                .thenReturn(Optional.of(chat))
                .thenReturn(Optional.of(updatedChat));

        AddMessageRequest req = AddMessageRequest.builder().role("user").content("Hi").build();

        // when
        ChatResponse response = chatService.addMessage(TEST_USER_ID, TEST_CHAT_ID, req);

        // then
        assertThat(response.getUpdatedAt()).isGreaterThanOrEqualTo(originalTime);
    }

    @Test
    @DisplayName("TC-US-ChatMsg: Mesaj eklenecek Chat yoksa 404 döner")
    void shouldThrowNotFound_whenChatDoesNotExistForMessage() {
        // given
        when(chatRepository.findById("nonexistent")).thenReturn(Optional.empty());

        AddMessageRequest req = AddMessageRequest.builder().role("user").content("Hi").build();

        // when / then
        assertThatThrownBy(() -> chatService.addMessage(TEST_USER_ID, "nonexistent", req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    @DisplayName("TC-US-ChatMsg: Başkasına ait Chat'e mesaj eklenirse 403 Forbidden döner")
    void shouldThrowForbidden_whenChatBelongsToDifferentUserForMessage() {
        // given
        User otherUser = User.builder().id(OTHER_USER_ID).email("other@test.com").build();
        Chat chat = buildTestChat(otherUser);

        when(chatRepository.findById(TEST_CHAT_ID)).thenReturn(Optional.of(chat));

        AddMessageRequest req = AddMessageRequest.builder().role("user").content("Hi").build();

        // when / then
        assertThatThrownBy(() -> chatService.addMessage(TEST_USER_ID, TEST_CHAT_ID, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
    }

    // --- deleteChat ---

    @Test
    @DisplayName("TC-US-ChatDel: Kendi Chat'ini silme işlemi başarılı")
    void shouldDeleteChat_whenOwnershipIsValid() {
        // given
        User user = buildTestUser();
        Chat chat = buildTestChat(user);

        when(chatRepository.findById(TEST_CHAT_ID)).thenReturn(Optional.of(chat));

        // when
        chatService.deleteChat(TEST_USER_ID, TEST_CHAT_ID);

        // then
        verify(chatRepository, times(1)).delete(chat);
    }

    @Test
    @DisplayName("TC-US-ChatDel: Başkasına ait Chat silinmek istendiğinde 403 Forbidden döner")
    void shouldThrowForbidden_whenDeletingChatBelongsToDifferentUser() {
        // given
        User otherUser = User.builder().id(OTHER_USER_ID).email("other@test.com").build();
        Chat chat = buildTestChat(otherUser);

        when(chatRepository.findById(TEST_CHAT_ID)).thenReturn(Optional.of(chat));

        // when / then
        assertThatThrownBy(() -> chatService.deleteChat(TEST_USER_ID, TEST_CHAT_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
    }

    // --- updateChatTitle ---

    @Test
    @DisplayName("TC-US-ChatUpdate: Chat başlığı başarıyla güncellenir")
    void shouldReturnUpdatedChat_whenTitleIsChanged() {
        // given
        User user = buildTestUser();
        Chat chat = buildTestChat(user);

        when(chatRepository.findById(TEST_CHAT_ID)).thenReturn(Optional.of(chat));

        Chat updatedChat = Chat.builder()
                .id(TEST_CHAT_ID).title("New Title").user(user)
                .createdAt(chat.getCreatedAt()).updatedAt(System.currentTimeMillis())
                .messages(new ArrayList<>()).build();
        when(chatRepository.save(any(Chat.class))).thenReturn(updatedChat);

        // when
        ChatResponse response = chatService.updateChatTitle(TEST_USER_ID, TEST_CHAT_ID, "New Title");

        // then
        assertThat(response.getTitle()).isEqualTo("New Title");
        verify(chatRepository, times(1)).save(any(Chat.class));
    }

    @Test
    @DisplayName("TC-US-ChatUpdate: Başka kullanıcının Chat başlığı güncellenince 403 Forbidden döner")
    void shouldThrowForbidden_whenChatBelongsToDifferentUserForTitleUpdate() {
        // given
        User otherUser = User.builder().id(OTHER_USER_ID).email("other@test.com").build();
        Chat chat = buildTestChat(otherUser);

        when(chatRepository.findById(TEST_CHAT_ID)).thenReturn(Optional.of(chat));

        // when / then
        assertThatThrownBy(() -> chatService.updateChatTitle(TEST_USER_ID, TEST_CHAT_ID, "New Title"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
    }

    // --- helpers ---

    private User buildTestUser() {
        return User.builder()
                .id(TEST_USER_ID)
                .email("test@roadrunner.com")
                .name("Test User")
                .passwordHash("hash")
                .travelPersonas(new ArrayList<>())
                .travelPlans(new ArrayList<>())
                .chats(new ArrayList<>())
                .build();
    }

    private Chat buildTestChat(User user) {
        return Chat.builder()
                .id(TEST_CHAT_ID)
                .title("Trip")
                .user(user)
                .createdAt(System.currentTimeMillis())
                .updatedAt(System.currentTimeMillis())
                .messages(new ArrayList<>())
                .build();
    }
}
