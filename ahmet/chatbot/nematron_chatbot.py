import torch
import json
import sys
import time
import os
import re
from openai import OpenAI
from chatbot.ai_agents import (
    calculatorAgent, weatherAgent, UserProfileAgent_SetInfo,
    UserFeedbackAgent, XAIJustificationAgent, Route_search_agent,
    POI_suggest_agent, ItineraryModificationAgent, ChatTitleAgent,
    POI_data_agent, POI_search_agent, UserPersonaListAgent,
    RouteGenerationFormatAgent
)

# Connect to your local llama.cpp server
client = OpenAI(
    base_url="http://localhost:8001/v1",
    api_key="lm-studio" # Not required, but good practice
)

# --- Tool Definitions (Standard JSON Schema) ---
# Dynamically build the TOOLS list for the LLM
TOOLS = [
    {"type": "function", "function": calculatorAgent.tool_template},
    {"type": "function", "function": weatherAgent.tool_template},
    {"type": "function", "function": UserProfileAgent_SetInfo.tool_template},
    {"type": "function", "function": UserFeedbackAgent.tool_template},
    {"type": "function", "function": XAIJustificationAgent.tool_template},
    {"type": "function", "function": Route_search_agent.tool_template},
    {"type": "function", "function": POI_suggest_agent.tool_template},
    {"type": "function", "function": ItineraryModificationAgent.tool_template},
    {"type": "function", "function": ChatTitleAgent.tool_template},
    {"type": "function", "function": POI_data_agent.tool_template},
    {"type": "function", "function": POI_search_agent.tool_template},
    {"type": "function", "function": UserPersonaListAgent.tool_template},
    {"type": "function", "function": RouteGenerationFormatAgent.tool_template},
]

def get_openai_tools():
    return TOOLS

# chatbot.py updates

def extract_tool_calls(response_text):
    """
    Improved extraction for Qwen2.5 which often uses <tool_call> tags 
    or specific JSON structures.
    """
    try:
        # Regex to find JSON inside the response
        match = re.search(r'\{.*\}', response_text, re.DOTALL)
        if match:
            data = json.loads(match.group())
            # The following is commented due to Nematron implementation
            # # Normalize Qwen's 'arguments' to 'parameters' if needed
            # if "arguments" in data and "parameters" not in data:
            #     data["parameters"] = data["arguments"]
            return data
    except Exception as e:
        print(f"Extraction Error: {e}")
        return None

def ask_question(messages: list): # Accept the whole history

    response = client.chat.completions.create(
        model="Nemotron-3-Nano-30B-A3B",
        messages=messages,
        tools=TOOLS,
        tool_choice="auto",
        temperature=0.1 # Low temperature for precise tool calling
    )

    #The context of raw response as follows:
    #content
    #refusal
    #role
    #annotations
    #audio
    #function_call
    #tool_calls
    #reasoning_content
    raw_response = response.choices[0].message
    tool_calls = raw_response.tool_calls or []
    response_content = raw_response.content or ""
    reasoning_content = raw_response.reasoning_content
    tool_calls_dict = [tc.to_dict() for tc in tool_calls] if tool_calls else tool_calls
    return {
        "role": "assistant",
        "tool_calls": tool_calls_dict,
        "content": response_content, 
        "reasoning": reasoning_content
        }