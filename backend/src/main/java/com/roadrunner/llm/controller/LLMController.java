package com.roadrunner.llm.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.roadrunner.llm.dto.LLMChatRequest;
import com.roadrunner.llm.dto.LLMChatResponse;
import com.roadrunner.llm.service.LLMService;
import com.roadrunner.security.JwtTokenProvider;

import java.util.Map;

/**
 * REST Controller that exposes LLM chat endpoints to the frontend.
 *
 * User ID Strategy:
 *   The frontend sends user_id directly in the POST request body (from Redux state.auth.user.id).
 *   The backend reads it via @JsonProperty("user_id") on LLMChatRequest.userId and
 *   forwards it to Flask where persona-aware agents use it.
 *   As a fallback, we also try to extract userId directly from the JWT token.
 */
@RestController
@RequestMapping("/api/llm")
public class LLMController {

    private final LLMService llmService;
    private final JwtTokenProvider jwtTokenProvider;

    public LLMController(LLMService llmService, JwtTokenProvider jwtTokenProvider) {
        this.llmService = llmService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/chat")
    public ResponseEntity<LLMChatResponse> chat(
            @RequestBody LLMChatRequest request,
            HttpServletRequest httpRequest) {

        if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            LLMChatResponse error = new LLMChatResponse();
            error.setStatus("error");
            error.setMessage("Query cannot be empty");
            return ResponseEntity.badRequest().body(error);
        }

        // Primary: userId sent directly in the request body by the frontend (most reliable)
        // Fallback: extract from JWT token in the Authorization header
        if (request.getUserId() == null || request.getUserId().isEmpty()) {
            String userId = extractUserIdFromJwt(httpRequest);
            if (userId != null) {
                request.setUserId(userId);
            }
        }

        System.out.println("[LLMController] chat() userId=" + request.getUserId());

        LLMChatResponse response = llmService.chat(
                request.getQuery(), request.getUserId(), request.getHistory());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/title")
    public ResponseEntity<Map<String, String>> generateTitle(@RequestBody LLMChatRequest request) {
        if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("title", "New Trip"));
        }
        String title = llmService.generateTitle(request.getQuery());
        return ResponseEntity.ok(Map.of("title", title));
    }

    /**
     * Directly explains a route by calling Flask /explain_route, skipping the
     * LLM tool-call extraction step that breaks on Turkish / special characters.
     *
     * Request body (JSON):
     *   { "route_stop_names": ["Stop A", "Stop B", ...],
     *     "route_summary": { "total_duration_min": 90, "total_distance_km": 5.2, "travel_mode": "walking" },
     *     "user_id": "optional" }
     */
    @PostMapping("/explain-route")
    public ResponseEntity<LLMChatResponse> explainRoute(
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {

        @SuppressWarnings("unchecked")
        java.util.List<String> stopNames = (java.util.List<String>) body.get("route_stop_names");

        if (stopNames == null || stopNames.isEmpty()) {
            LLMChatResponse err = new LLMChatResponse();
            err.setStatus("error");
            err.setMessage("route_stop_names is required");
            return ResponseEntity.badRequest().body(err);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> routeSummary = (Map<String, Object>) body.get("route_summary");

        // Resolve userId from body or JWT fallback
        String userId = body.containsKey("user_id") ? String.valueOf(body.get("user_id")) : null;
        if (userId == null || userId.equals("null")) {
            userId = extractUserIdFromJwt(httpRequest);
        }

        LLMChatResponse response = llmService.explainRoute(stopNames, routeSummary, userId);
        return ResponseEntity.ok(response);
    }

    /** Tries to parse the sub claim from the Bearer JWT, returns null on failure. */
    private String extractUserIdFromJwt(HttpServletRequest httpRequest) {
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                if (jwtTokenProvider.validateToken(token)) {
                    return jwtTokenProvider.getUserIdFromToken(token);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
}
