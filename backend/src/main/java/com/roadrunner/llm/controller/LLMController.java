package com.roadrunner.llm.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.roadrunner.llm.dto.LLMChatRequest;
import com.roadrunner.llm.dto.LLMChatResponse;
import com.roadrunner.llm.service.LLMService;

import java.util.Map;

/**
 * REST Controller that exposes LLM chat endpoints to the frontend.
 * 
 * Architecture: Frontend → /api/llm/** (this controller) → Flask LLM Server
 */
@RestController
@RequestMapping("/api/llm")
public class LLMController {

    private final LLMService llmService;

    public LLMController(LLMService llmService) {
        this.llmService = llmService;
    }

    /**
     * POST /api/llm/chat
     * Sends a chat message through the LLM pipeline.
     * 
     * Request body: { "query": "user message", "chatId": "optional" }
     * Response: { "status": "success|error", "response": "text", "toolUsed": "optional" }
     */
    @PostMapping("/chat")
    public ResponseEntity<LLMChatResponse> chat(@RequestBody LLMChatRequest request) {
        if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            LLMChatResponse error = new LLMChatResponse();
            error.setStatus("error");
            error.setMessage("Query cannot be empty");
            return ResponseEntity.badRequest().body(error);
        }

        LLMChatResponse response = llmService.chat(request.getQuery(), request.getHistory());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/llm/title
     * Generates a short title for a chat session.
     * 
     * Request body: { "query": "first user message" }
     * Response: { "title": "generated title" }
     */
    @PostMapping("/title")
    public ResponseEntity<Map<String, String>> generateTitle(@RequestBody LLMChatRequest request) {
        if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("title", "New Trip"));
        }

        String title = llmService.generateTitle(request.getQuery());
        return ResponseEntity.ok(Map.of("title", title));
    }
}
