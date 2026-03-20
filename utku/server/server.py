from flask import Flask, render_template, request, jsonify
import sys
import os
import json
# Bridge the path to the 'chatbot' directory
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from chatbot.chatbot import ask_question, load_model
from chatbot.ai_agents import calculatorAgent, weatherAgent

app = Flask(__name__)

# Initialize the agents
TOOL_REGISTRY = {
    "calculator_agent": calculatorAgent(),
    "weather_agent": weatherAgent(),
}

# Pre-load model on startup
print("[SYSTEM] Initializing AI Engine...")
load_model()

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/chatbot', methods=['POST'])
def handle_chat():
    data = request.json
    user_query = data.get("query", "")
    
    if not user_query:
        return jsonify({"status": "error", "message": "Query is empty"}), 400
    
    try:
        # 1. Start message history
        messages = [
            {"role": "system", "content": "You are a helpful assistant with access to tools."},
            {"role": "user", "content": user_query}
        ]

        # 2. First LLM Call
        llm_output = ask_question(messages)
        
        if llm_output["type"] == "tool_call":
            tool_info = llm_output["content"]
            tool_name = tool_info.get("name")
            params = tool_info.get("parameters", {}) # Now normalized in chatbot.py
            
            # Execute tool
            tool_result = invoke_action(tool_name, params)

            # 3. CRITICAL: Append the tool call AND the result to history
            # This follows the ChatML / Tool-use standard
            messages.append({"role": "assistant", "content": llm_output.get("raw", "")})
            messages.append({
                "role": "tool", 
                "name": tool_name, 
                "content": str(tool_result)
            })

            # 4. Final LLM Call with history to get the natural language answer
            final_output = ask_question(messages)
            
            return jsonify({
                "status": "success",
                "tool_used": tool_name,
                "response": final_output["content"]
            })
        
        else: 
            return jsonify({
                "status": "success",
                "response": llm_output["content"]
            })

    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500



def invoke_action(tool_name, parameters):
    if tool_name not in TOOL_REGISTRY:
        return f"Error: Tool '{tool_name}' not found."
    
    try:
        # Ensure parameters is a dict
        if isinstance(parameters, str):
            parameters = json.loads(parameters)
            
        # Calling the __call__ method of the agent classes
        result = TOOL_REGISTRY[tool_name](**parameters)
        return result
    except Exception as e:
        return f"Error executing {tool_name}: {str(e)}"

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False)