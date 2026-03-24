from flask import Flask, render_template, request, jsonify
import sys
import os
import json
# Bridge the path to the 'chatbot' directory
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from chatbot.chatbot import ask_question, load_model
# Import the agents from the sibling file
from chatbot.ai_agents import (
    calculatorAgent, weatherAgent, UserProfileAgent_SetInfo, 
    UserFeedbackAgent, XAIJustificationAgent, Route_search_agent, 
    POI_suggest_agent, ItineraryModificationAgent, ChatTitleAgent, POIDataAgent
)

app = Flask(__name__)

# Now the classes are defined and accessible here

TOOL_REGISTRY = {
    "calculator_agent": calculatorAgent(),
    "weather_agent": weatherAgent(),
    "user_profile_agent": UserProfileAgent_SetInfo(),      # Matches TC-LLM-U-008
    "submit_user_feedback": UserFeedbackAgent(),          # Matches TC-LLM-U-005
    "get_xai_justification": XAIJustificationAgent(),      # Matches TC-LLM-U-006
    "search_route": Route_search_agent(),                 # Matches TC-LLM-U-002
    "suggest_poi": POI_suggest_agent(),                   # Matches TC-LLM-U-003
    "modify_itinerary": ItineraryModificationAgent(),     # Matches TC-LLM-U-007
    "generate_chat_title": ChatTitleAgent(),              # Matches TC-LLM-U-015
    "get_poi_details": POIDataAgent(),                    # Supports context for justification
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
    history = data.get("history", [])  # List of {role, content} dicts from frontend
    
    if not user_query:
        return jsonify({"status": "error", "message": "Query is empty"}), 400
    
    try:
        # 1. Start message history with system prompt
        messages = [
            {"role": "system", "content": "You are a helpful assistant with access to tools."},
        ]
        
        # 2. Append previous conversation history (last N messages from frontend)
        for msg in history:
            role = msg.get("role", "user")
            content = msg.get("content", "")
            if role == "assistant":
                role = "assistant"  # keep as-is for the LLM
            messages.append({"role": role, "content": content})
        
        # 3. Append the current user query
        messages.append({"role": "user", "content": user_query})

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

            print("tool_used: " + tool_name)
            print("tool_params:\n")
            print(params)
            print("response:\n")
            print(final_output["content"])
            print("Tool output as follows:",tool_result)

            return jsonify({
                "status": "success",
                "tool_used": tool_name,
                "tool_params": params,
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
    """
    Dynamically resolves and executes a tool from the registry.
    """
    # 1. Validation: Check if the tool exists in our registry
    if tool_name not in TOOL_REGISTRY:
        print(f"[ERROR] Tool '{tool_name}' requested by LLM but not found in Registry.")
        return f"Error: Tool '{tool_name}' is not currently available."
    
    try:
        # 2. Safety: Ensure parameters is a dictionary
        if isinstance(parameters, str):
            parameters = json.loads(parameters)
            
        print(f"[SYSTEM] Executing {tool_name} with params: {parameters}")

        # 3. Execution: Call the agent instance using the ** unpacking operator
        # This triggers the __call__ method defined in your ai_agents.py classes
        agent_instance = TOOL_REGISTRY[tool_name]
        result = agent_instance(**parameters)
        
        return result

    except TypeError as e:
        return f"Parameter Error: The tool '{tool_name}' received invalid arguments. {str(e)}"
    except Exception as e:
        return f"Execution Error in {tool_name}: {str(e)}"

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False)
