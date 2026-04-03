import torch
from transformers import AutoModelForCausalLM, AutoTokenizer
import json
import re
import sys
import time
from chatbot.ai_agents import (
    CalculatorAgent, WeatherAgent, UserProfileUpdateAgent,
    TripFeedbackAgent, RecommendationExplainerAgent, RouteGenerationFormatAgent,
    POISuggestionAgent, ItineraryModificationAgent, ChatTitleAgent,
    POIDataAgent, POI_search_agent, UserPersonaListAgent,
    GeneratedRouteExplanationAgent,
)

# --- Configuration ---
MODEL_ID = "Qwen/Qwen2.5-1.5B-Instruct" # Qwen2.5/3 are optimized for tools
if torch.cuda.is_available():
    DEVICE = torch.device("cuda")
elif torch.backends.mps.is_available():
    DEVICE = torch.device("mps")
else:
    DEVICE = torch.device("cpu")

tokenizer = None
model = None

def load_model():
    global tokenizer, model
    print(f"Loading {MODEL_ID} on {DEVICE}...")
    tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)
    
    # Best practice: use flash_attention_2 if on GPU for speed
    model = AutoModelForCausalLM.from_pretrained(
        MODEL_ID,
        torch_dtype=torch.float16 if DEVICE.type in ['cuda', 'mps'] else torch.float32,
        device_map="auto"
    )

# --- Tool Definitions (Standard JSON Schema) ---
# Dynamically build the TOOLS list for the LLM
TOOLS = [
    {"type": "function", "function": CalculatorAgent.tool_template},
    {"type": "function", "function": WeatherAgent.tool_template},
    {"type": "function", "function": UserProfileUpdateAgent.tool_template},
    {"type": "function", "function": TripFeedbackAgent.tool_template},
    {"type": "function", "function": RecommendationExplainerAgent.tool_template},
    {"type": "function", "function": RouteGenerationFormatAgent.tool_template},
    {"type": "function", "function": POISuggestionAgent.tool_template},
    {"type": "function", "function": ItineraryModificationAgent.tool_template},
    {"type": "function", "function": ChatTitleAgent.tool_template},
    {"type": "function", "function": POIDataAgent.tool_template},
    {"type": "function", "function": POI_search_agent.tool_template},
    {"type": "function", "function": UserPersonaListAgent.tool_template},
    {"type": "function", "function": GeneratedRouteExplanationAgent.tool_template},
]

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
            # Normalize Qwen's 'arguments' to 'parameters' if needed
            if "arguments" in data and "parameters" not in data:
                data["parameters"] = data["arguments"]
            return data
    except Exception as e:
        print(f"Extraction Error: {e}")
        return None

def ask_question(messages: list): # Accept the whole history
    if model is None: load_model()

    # 2. Apply Chat Template with Tool Support
    text = tokenizer.apply_chat_template(
        messages,
        tools=TOOLS,
        add_generation_prompt=True,
        tokenize=False
    )

    inputs = tokenizer([text], return_tensors="pt").to(model.device)

    outputs = model.generate(
        **inputs,
        max_new_tokens=512,
        do_sample=False, # Set to False for more reliable tool calls
        temperature=0.1,
        top_p=0.9
    )

    response_ids = outputs[0][len(inputs.input_ids[0]):]
    raw_response = tokenizer.decode(response_ids, skip_special_tokens=True).strip()

    tool_data = extract_tool_calls(raw_response)
    
    # Logic to detect if it's actually a tool call
    if tool_data and ("name" in tool_data):
        return {"type": "tool_call", "content": tool_data, "raw": raw_response}
    
    return {"type": "text", "content": raw_response}


if __name__ == "__main__":
    query = sys.argv[1] if len(sys.argv) > 1 else "What is the weather in Paris in celsius?"
    
    print("\n" + "="*50)
    result = run_conversation(query)
    print(f"USER: {query}")
    print(f"RESULT TYPE: {result['type']}")
    print(f"RAW DATA: {result['content']}")
    print("="*50 + "\n")