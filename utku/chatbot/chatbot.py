import torch
from transformers import AutoModelForCausalLM, AutoTokenizer
import os
import time
import sys
import json
import re

# Model selection
MODEL_ID = "Qwen/Qwen3-0.6B" 

DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")

# --- Global Model and Tokenizer Initialization ---
tokenizer = None
model = None

def load_model():
    global tokenizer, model
    try:
        print(f"Targeting device: {DEVICE}")
        print(f"Loading model '{MODEL_ID}' onto {DEVICE}...")
        start_time = time.time()
        
        tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)
        
        if DEVICE.type == 'cuda':
            model = AutoModelForCausalLM.from_pretrained(
                MODEL_ID,
                torch_dtype=torch.float16,
                device_map="auto" 
            )
        else:
            model = AutoModelForCausalLM.from_pretrained(
                MODEL_ID,
                torch_dtype=torch.float32 
            )
        
        end_time = time.time()
        print(f"Model loaded successfully in {end_time - start_time:.2f} seconds.")

    except Exception as e:
        print(f"An unexpected error occurred during model loading: {e}")
        sys.exit(1)


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

If you need to use a tool, you MUST return a JSON object in this format:
{{"tool_call": {{"name": "tool_name", "parameters": {{"param": "value"}}}}}}

If no tool is needed, respond with normal text."""

def ask_question(question: str) -> str:
    """
    Passes a user question to the globally loaded Qwen model.
    """
    print("Model is generating an answer...\n")
    if model is None:
        load_model()

    messages = [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": question}
    ]
    
    inputs = tokenizer.apply_chat_template(
        messages,
        add_generation_prompt=True,
        tokenize=True,
        return_tensors="pt",
        return_dict=True
    )
    
    inputs_on_device = {k: v.to(model.device) for k, v in inputs.items()}

    outputs = model.generate(
        **inputs_on_device,
        max_new_tokens=1024,
        do_sample=False
    )

    input_len = inputs_on_device["input_ids"].shape[-1]
    raw_response = tokenizer.decode(outputs[0][input_len:], skip_special_tokens=True).strip()
    
    # Try to extract a tool call
    potential_json = extract_json_from_text(raw_response)
    
    if potential_json and "tool_call" in potential_json:
        return potential_json  # Returns the dict for server.py
        
    return raw_response # Returns plain text if no tool found

if __name__ == "__main__":
    # Check if a question was provided as a command line argument
    if len(sys.argv) > 1:
        # Join all arguments after the script name into a single string
        q = " ".join(sys.argv[1:])
    else:
        # Default question if none is provided
        q = "List best places to visit in Berlin"

    # Send the question to the model and get the result
    answer = ask_question(q)
    
    # Print the formatted output
    print("-" * 50)
    print(f"USER QUESTION:\n{q}\n")
    print(f"QWEN RESPONSE ({DEVICE.type.upper()}):\n{answer}")
    print("-" * 50)