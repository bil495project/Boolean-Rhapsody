package com.roadrunner.llm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO mapping the Flask LLM Server's JSON response.
 * 
 * Flask server returns:
 * Success with tool: { "status": "success", "tool_used": "<name>", "response":
 * "<text>" }
 * Success no tool: { "status": "success", "response": "<text>" }
 * Error: { "status": "error", "message": "<error_text>" }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LLMChatResponse {

    private String status;
    private String response;
    @JsonAlias("tool_used")
    @JsonProperty("toolUsed")
    private String toolUsed;
    @JsonAlias("tool_params")
    @JsonProperty("toolParams")
    private Object toolParams;
    @JsonProperty("routeData")
    private Object routeData;
    private String message;

    public LLMChatResponse() {
    }

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

    public Object getToolParams() {
        return toolParams;
    }

    public void setToolParams(Object toolParams) {
        this.toolParams = toolParams;
    }

    public Object getRouteData() {
        return routeData;
    }

    public void setRouteData(Object routeData) {
        this.routeData = routeData;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
