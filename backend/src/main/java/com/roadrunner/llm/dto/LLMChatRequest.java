package com.roadrunner.llm.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for the LLM chat endpoint.
 * Frontend sends this to Spring Boot, which proxies it to the Flask LLM Server.
 */
public class LLMChatRequest {

    private String query;
    private String chatId;
    @JsonProperty("user_id")
    private String userId;
    private List<ChatMessageDto> history;

    public LLMChatRequest() {}

    public LLMChatRequest(String query, String chatId, String userId, List<ChatMessageDto> history) {
        this.query = query;
        this.chatId = chatId;
        this.userId = userId;
        this.history = history;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<ChatMessageDto> getHistory() {
        return history;
    }

    public void setHistory(List<ChatMessageDto> history) {
        this.history = history;
    }

    /**
     * Represents a single message in the chat history.
     */
    public static class ChatMessageDto {
        private String role;    // "user" or "assistant"
        private String content;

        public ChatMessageDto() {}

        public ChatMessageDto(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
