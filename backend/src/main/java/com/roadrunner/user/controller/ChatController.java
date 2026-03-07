package com.roadrunner.user.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roadrunner.user.dto.request.AddMessageRequest;
import com.roadrunner.user.dto.request.CreateChatRequest;
import com.roadrunner.user.dto.response.ChatResponse;
import com.roadrunner.user.service.ChatService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/chats")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public ResponseEntity<List<ChatResponse>> getAllChats() {
        String userId = getCurrentUserId();
        return ResponseEntity.ok(chatService.getAllChats(userId));
    }

    @PostMapping("/new")
    public ResponseEntity<ChatResponse> createChat(
            @Valid @RequestBody CreateChatRequest request) {
        String userId = getCurrentUserId();
        ChatResponse response = chatService.createChat(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{chatId}")
    public ResponseEntity<ChatResponse> getChatById(@PathVariable String chatId) {
        String userId = getCurrentUserId();
        return ResponseEntity.ok(chatService.getChatById(userId, chatId));
    }

    @DeleteMapping("/{chatId}")
    public ResponseEntity<Void> deleteChat(@PathVariable String chatId) {
        String userId = getCurrentUserId();
        chatService.deleteChat(userId, chatId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{chatId}/title")
    public ResponseEntity<ChatResponse> updateChatTitle(
            @PathVariable String chatId,
            @RequestBody Map<String, String> body) {
        String userId = getCurrentUserId();
        String newTitle = body.get("title");
        return ResponseEntity.ok(chatService.updateChatTitle(userId, chatId, newTitle));
    }

    @PostMapping("/{chatId}/messages")
    public ResponseEntity<ChatResponse> addMessage(
            @PathVariable String chatId,
            @Valid @RequestBody AddMessageRequest request) {
        String userId = getCurrentUserId();
        ChatResponse response = chatService.addMessage(userId, chatId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private String getCurrentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
