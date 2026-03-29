package com.roadrunner.user.service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.roadrunner.user.dto.request.AddMessageRequest;
import com.roadrunner.user.dto.request.CreateChatRequest;
import com.roadrunner.user.dto.response.ChatResponse;
import com.roadrunner.user.dto.response.MessageResponse;
import com.roadrunner.user.entity.Chat;
import com.roadrunner.user.entity.Message;
import com.roadrunner.user.entity.User;
import com.roadrunner.user.repository.ChatRepository;
import com.roadrunner.user.repository.MessageRepository;
import com.roadrunner.user.repository.UserRepository;

@Service
@SuppressWarnings("null")
@Transactional
public class ChatService {

    private static final Comparator<Message> MESSAGE_ORDER = Comparator
            .comparingLong(Message::getTimestamp)
            .thenComparing(message -> message.getId() == null ? "" : message.getId());

    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    public ChatService(ChatRepository chatRepository,
            MessageRepository messageRepository,
            UserRepository userRepository) {
        this.chatRepository = chatRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    public ChatResponse createChat(String userId, CreateChatRequest req) {
        User user = findUserById(userId);

        Chat chat = Chat.builder()
                .title(req.getTitle())
                .duration(req.getDuration())
                .user(user)
                .build();

        chat = chatRepository.save(chat);
        return mapToChatResponse(chat);
    }

    public List<ChatResponse> getAllChats(String userId) {
        findUserById(userId);
        return chatRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::mapToChatResponse)
                .collect(Collectors.toList());
    }

    public ChatResponse getChatById(String userId, String chatId) {
        Chat chat = findChatById(chatId);
        verifyOwnership(chat, userId);
        return mapToChatResponse(chat);
    }

    public ChatResponse addMessage(String userId, String chatId, AddMessageRequest req) {
        Chat chat = findChatById(chatId);
        verifyOwnership(chat, userId);

        Message message = Message.builder()
                .role(req.getRole())
                .content(req.getContent())
                .toolUsed(req.getToolUsed())
                .toolParams(req.getToolParams())
                .chat(chat)
                .build();

        messageRepository.save(message);
        chat.getMessages().add(message);

        chat.setUpdatedAt(System.currentTimeMillis());
        chat = chatRepository.save(chat);

        return mapToChatResponse(chat);
    }

    public void deleteChat(String userId, String chatId) {
        System.out.println("Deleting chat: " + chatId + " for user: " + userId);
        Chat chat = findChatById(chatId);
        verifyOwnership(chat, userId);
        chatRepository.delete(chat);
        System.out.println("Chat deleted successfully: " + chatId);
    }

    public ChatResponse updateChatTitle(String userId, String chatId, String newTitle) {
        Chat chat = findChatById(chatId);
        verifyOwnership(chat, userId);

        chat.setTitle(newTitle);
        chat = chatRepository.save(chat);
        return mapToChatResponse(chat);
    }

    private User findUserById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));
    }

    private Chat findChatById(String chatId) {
        return chatRepository.findById(chatId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Chat not found"));
    }

    private void verifyOwnership(Chat chat, String userId) {
        if (!chat.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    private ChatResponse mapToChatResponse(Chat chat) {
        List<MessageResponse> orderedMessages = chat.getMessages() != null
                ? chat.getMessages().stream()
                        .sorted(MESSAGE_ORDER)
                        .map(this::mapToMessageResponse)
                        .collect(Collectors.toList())
                : Collections.emptyList();

        return ChatResponse.builder()
                .id(chat.getId())
                .title(chat.getTitle())
                .duration(chat.getDuration())
                .createdAt(chat.getCreatedAt())
                .updatedAt(chat.getUpdatedAt())
                .messages(orderedMessages)
                .build();
    }

    private MessageResponse mapToMessageResponse(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .toolUsed(message.getToolUsed())
                .toolParams(message.getToolParams())
                .timestamp(message.getTimestamp())
                .build();
    }
}
