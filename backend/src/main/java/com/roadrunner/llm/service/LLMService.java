package com.roadrunner.llm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.roadrunner.llm.dto.LLMChatRequest;
import com.roadrunner.llm.dto.LLMChatResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service that proxies chat requests to Utku's Flask LLM Server.
 * 
 * Architecture: Frontend → Spring Boot (this service) → Flask LLM Server (:5000)
 */
@Service
public class LLMService {

    private static final Logger logger = LoggerFactory.getLogger(LLMService.class);

    private final RestTemplate restTemplate;

    @Value("${llm.server.url:http://localhost:5000}")
    private String llmServerUrl;

    public LLMService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Sends a chat query to the Flask LLM server and returns the parsed response.
     *
     * @param query the user's message text
     * @param history the last N messages for context (may be null)
     * @return LLMChatResponse with status, response text, and optional tool_used
     */
    public LLMChatResponse chat(String query, String userId, List<LLMChatRequest.ChatMessageDto> history) {
        String endpoint = llmServerUrl + "/chatbot";
        logger.info("Forwarding chat query to LLM Server: {}", endpoint);

        try {
            // Build the request body matching Flask server's expected format
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("query", query);

            // Forward the authenticated user's ID to Flask for agent-level persona lookups
            if (userId != null && !userId.isEmpty()) {
                requestBody.put("user_id", userId);
            }

            // Include history if available
            if (history != null && !history.isEmpty()) {
                List<Map<String, String>> historyList = history.stream()
                        .map(msg -> {
                            Map<String, String> m = new HashMap<>();
                            m.put("role", msg.getRole());
                            m.put("content", msg.getContent());
                            return m;
                        })
                        .collect(Collectors.toList());
                requestBody.put("history", historyList);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<LLMChatResponse> responseEntity = restTemplate.postForEntity(
                    endpoint,
                    request,
                    LLMChatResponse.class
            );

            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                logger.info("LLM Server responded with status: {}, tool_used: {}",
                        responseEntity.getBody().getStatus(),
                        responseEntity.getBody().getToolUsed());
                return responseEntity.getBody();
            }

            // Non-2xx or null body
            LLMChatResponse errorResponse = new LLMChatResponse();
            errorResponse.setStatus("error");
            errorResponse.setMessage("LLM Server returned unexpected response: " + responseEntity.getStatusCode());
            return errorResponse;

        } catch (RestClientException e) {
            logger.error("Failed to communicate with LLM Server at {}: {}", endpoint, e.getMessage());

            LLMChatResponse errorResponse = new LLMChatResponse();
            errorResponse.setStatus("error");
            errorResponse.setMessage("LLM Server bağlantı hatası: " + e.getMessage());
            return errorResponse;
        }
    }

    /**
     * Generates a short title for a chat session based on the user's first query.
     *
     * @param firstMessage the user's initial message
     * @return a concise title string
     */
    public String generateTitle(String firstMessage) {
        try {
            String titleQuery = "Generate a short title (max 60 chars) for this travel chat: \"" + firstMessage + "\"";
            LLMChatResponse response = chat(titleQuery, null, null);

            if ("success".equals(response.getStatus()) && response.getResponse() != null) {
                String title = response.getResponse()
                        .replaceAll("^[\"']|[\"']$", "")
                        .trim();
                if (!title.isEmpty() && title.length() <= 80) {
                    return title;
                }
            }
        } catch (Exception e) {
            logger.warn("LLM title generation failed, using fallback: {}", e.getMessage());
        }

        // Fallback: extract keywords from the message
        return generateTitleLocal(firstMessage);
    }

    private String generateTitleLocal(String query) {
        String[] stopWords = {"a", "an", "the", "to", "in", "for", "of", "and", "or",
                "is", "are", "i", "want", "would", "like", "please", "can",
                "you", "me", "my", "trip", "plan", "visit",
                "bir", "bana", "öner", "bul", "ben", "istiyorum"};
        java.util.Set<String> stopSet = new java.util.HashSet<>(java.util.Arrays.asList(stopWords));

        String[] words = query.trim().toLowerCase().split("\\s+");
        StringBuilder title = new StringBuilder();
        int count = 0;

        for (String word : words) {
            if (!stopSet.contains(word) && word.length() > 2 && count < 3) {
                if (title.length() > 0) title.append(" ");
                title.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
                count++;
            }
        }

        return title.length() > 0 ? title.toString() : "New Trip";
    }
}
