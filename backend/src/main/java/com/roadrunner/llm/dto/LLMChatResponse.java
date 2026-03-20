package com.roadrunner.llm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response DTO mapping the Flask LLM Server's JSON response.
 * 
 * Flask server returns:
 *   Success with tool: { "status": "success", "tool_used": "<name>", "response": "<text>" }
 *   Success no tool:   { "status": "success", "response": "<text>" }
 *   Error:             { "status": "error", "message": "<error_text>" }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LLMChatResponse {

    private String status;
    private String response;
    private String toolUsed;
    private String message;

    public LLMChatResponse() {}

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String getToolUsed() {
        return toolUsed;
    }

    public void setToolUsed(String toolUsed) {
        this.toolUsed = toolUsed;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
