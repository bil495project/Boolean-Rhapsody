package com.roadrunner.llm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final ObjectMapper objectMapper = new ObjectMapper();

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
                LLMChatResponse body = responseEntity.getBody();
                logger.info("LLM Server responded with status: {}, tool_used: {}",
                        body.getStatus(),
                        body.getToolUsed());

                // When the route generation tool was used, the response field contains
                // a JSON string with route alternatives. Parse it into routeData so the
                // frontend receives structured data instead of a raw JSON string.
                if ("generate_route_format".equals(body.getToolUsed()) && body.getResponse() != null) {
                    try {
                        Object parsed = objectMapper.readValue(body.getResponse(), Object.class);
                        body.setRouteData(parsed);
                        body.setResponse(null); // clear string response to avoid sending duplicate data
                    } catch (Exception e) {
                        logger.warn("Failed to parse route data from generate_route_format response: {}", e.getMessage());
                        // Fall through — frontend will receive the raw string in response
                    }
                }

                return body;
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
     * Directly calls Flask /explain_route, bypassing the LLM tool-call parser.
     * Used by the "Ask LLM about Route" button to avoid JSON extraction failures
     * on Turkish / special-character stop names.
     *
     * @param routeStopNames ordered list of stop names from the route
     * @param routeSummary   optional metadata (duration, distance, travel mode)
     * @param userId         authenticated user id (may be null)
     * @return LLMChatResponse with the narrative response
     */
    public LLMChatResponse explainRoute(
            List<String> routeStopNames,
            Map<String, Object> routeSummary,
            String userId) {

        String endpoint = llmServerUrl + "/explain_route";
        logger.info("Forwarding explain_route request to Flask: {}", endpoint);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("route_stop_names", routeStopNames);
            if (routeSummary != null && !routeSummary.isEmpty()) {
                requestBody.put("route_summary", routeSummary);
            }
            if (userId != null && !userId.isEmpty()) {
                requestBody.put("user_id", userId);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<LLMChatResponse> responseEntity = restTemplate.postForEntity(
                    endpoint, request, LLMChatResponse.class);

            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                return responseEntity.getBody();
            }

            LLMChatResponse err = new LLMChatResponse();
            err.setStatus("error");
            err.setMessage("explain_route returned: " + responseEntity.getStatusCode());
            return err;

        } catch (RestClientException e) {
            logger.error("explain_route connection failed: {}", e.getMessage());
            LLMChatResponse err = new LLMChatResponse();
            err.setStatus("error");
            err.setMessage("explain_route bağlantı hatası: " + e.getMessage());
            return err;
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
