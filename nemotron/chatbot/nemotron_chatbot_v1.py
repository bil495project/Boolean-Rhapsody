import torch
import os
import time
import sys
import json
import re
from openai import OpenAI
from .ai_agents import calculatorAgent, weatherAgent

# Connect to your local llama.cpp server
client = OpenAI(
    base_url="http://localhost:8001/v1",
    api_key="lm-studio" # Not required, but good practice
)


TOOLS_DEFINITION = [
    {
        "name": "calculator_agent",
        "description": "Performs math. Use this for any numerical calculation.",
        "parameters": {"expression": "string"}
    },
    {
        "name": "weather_agent",
        "description": "Gets current weather for a location.",
        "parameters": {"location": "string", "unit": "celsius or fahrenheit"}
    }
]

def extract_json_from_text(text: str):
    """
    Extracts a JSON object from a string that might contain conversational text.
    """
    try:
        # Find anything between curly braces
        match = re.search(r'\{.*\}', text, re.DOTALL)
        if match:
            json_str = match.group()
            return json.loads(json_str)
    except Exception:
        return None
    return None



SYSTEM_PROMPT = f"""You are a helpful assistant. You have access to these tools:
{json.dumps(TOOLS_DEFINITION, indent=2)}
""" +"""If you need to use a tool, you MUST return a VALID JSON object in this format:
{
  "tool_call": {
    "name": "tool_name",
    "parameters": {
      "param": "value"
    }
  }
}


an example tool call is as follows:
{
  "tool_call": {
    "name": "weather_agent",
    "parameters": {
      "location": "Tokyo, Japan",
      "unit": "celsius"
    }
  }
}

If no tool is needed, respond with normal text."""

def get_tools_list():
    """
    Converts the templates from ai_agents.py into the format 
    expected by the OpenAI/llama-server API.
    """
    tools = []
    
    # 1. Calculator Wrapper
    tools.append({
        "type": "function",
        "function":
            calculatorAgent.tool_template
    })

    # 2. Weather Wrapper
    tools.append({
        "type": "function",
        "function": 
            weatherAgent.tool_template
    })
    
    return tools

def ask_question(messages: dict) -> dict:
    tools_list = get_tools_list()   

    response = client.chat.completions.create(
        model="Nemotron-3-Nano-30B-A3B",
        messages=messages,
        tools=tools_list,
        tool_choice="auto",
        temperature=0.1 # Low temperature for precise tool calling
    )
    raw_response = response.choices[0].message
    
    # Try to extract a tool call
    tool_calls = response.choices[0].message.tool_calls or []
    content = response.choices[0].message.content or ""
    tool_calls_dict = [tc.to_dict() for tc in tool_calls] if tool_calls else tool_calls
    messages.append({"role": "assistant", "tool_calls": tool_calls_dict, "content": content,})
    
    return messages

if __name__ == "__main__":
    # Check if a question was provided as a command line argument
    if len(sys.argv) > 1:
        # Join all arguments after the script name into a single string
        q = " ".join(sys.argv[1:])
    else:
        # Default question if none is provided
        q = "What is what is PI + sin(19)?"

    # Send the question to the model and get the result
    messages = [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": q}
    ]
    conversation = ask_question(messages)
    
    # Print the formatted output
    print("-" * 50)
    print(f"Conversation:\n{conversation}\n")
    print(f"USER QUESTION:\n{q}\n")
    print(f"Nemotron RESPONSE:\n{conversation[-1]}\n")
    print(type(conversation))
    
    tool_calls = conversation[-1]["tool_calls"]
    print(tool_calls)
    print("-" * 50)