from flask import Flask, render_template, request, jsonify, g
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
    POI_suggest_agent, ItineraryModificationAgent, ChatTitleAgent,
    POIDataAgent, UserPersonaListAgent
)

app = Flask(__name__)

TOOL_REGISTRY = {
    "calculator_agent":    calculatorAgent(),
    "weather_agent":       weatherAgent(),
    "user_profile_agent":  UserProfileAgent_SetInfo(),     # Matches TC-LLM-U-008
    "submit_user_feedback":UserFeedbackAgent(),            # Matches TC-LLM-U-005
    "get_xai_justification":XAIJustificationAgent(),       # Matches TC-LLM-U-006
    "search_route":        Route_search_agent(),           # Matches TC-LLM-U-002
    "suggest_poi":         POI_suggest_agent(),            # Matches TC-LLM-U-003
    "modify_itinerary":    ItineraryModificationAgent(),   # Matches TC-LLM-U-007
    "generate_chat_title": ChatTitleAgent(),               # Matches TC-LLM-U-015
    "get_poi_details":     POIDataAgent(),                 # Supports context for justification
    "get_user_personas":   UserPersonaListAgent(),         # Lists user's travel personas
}

# Agents that require the user_id to be injected at call time.
# These agents accept a 'user_id' kwarg even though it is NOT exposed in the
# LLM tool schema (to keep the LLM's job simple).
USER_ID_AWARE_TOOLS = {"get_user_personas", "search_route"}

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
    history = data.get("history", [])   # List of {role, content} dicts from frontend
    user_id = data.get("user_id", None) # Injected by Spring Boot LLMController

    # Store user_id in Flask's per-request context so helpers can access it
    g.user_id = user_id

    if not user_query:
        return jsonify({"status": "error", "message": "Query is empty"}), 400

    try:
        # 1. Build message history with system prompt
        messages = [
            {
                "role": "system",
                "content": (
                    "You are a helpful travel assistant with access to tools. "
                    "When a user asks about their travel persona or what kind of traveller they are, "
                    "always use the get_user_personas tool. "
                    "When creating or optimising a route, use search_route — it will automatically "
                    "load the user's saved preferences from the database."
                )
            },
        ]

        # 2. Append previous conversation history
        for msg in history:
            role = msg.get("role", "user")
            content = msg.get("content", "")
            messages.append({"role": role, "content": content})

        # 3. Append the current user query
        messages.append({"role": "user", "content": user_query})

        # 4. First LLM call
        llm_output = ask_question(messages)

        if llm_output["type"] == "tool_call":
            tool_info = llm_output["content"]
            tool_name = tool_info.get("name")
            params = tool_info.get("parameters", {})

            # Execute tool (injects user_id for persona-aware agents)
            tool_result = invoke_action(tool_name, params, user_id)

            # 5. Append tool call + result to history (ChatML / Tool-use standard)
            messages.append({"role": "assistant", "content": llm_output.get("raw", "")})
            messages.append({
                "role": "tool",
                "name": tool_name,
                "content": str(tool_result)
            })

            # 6. Final LLM call to generate the natural language answer
            final_output = ask_question(messages)

            print(f"tool_used: {tool_name}")
            print(f"tool_params: {params}")
            print(f"response: {final_output['content']}")

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


def invoke_action(tool_name: str, parameters: dict, user_id: str = None):
    """
    Dynamically resolves and executes a tool from the registry.
    For persona-aware tools (USER_ID_AWARE_TOOLS), injects the user_id at call time.
    """
    if tool_name not in TOOL_REGISTRY:
        print(f"[ERROR] Tool '{tool_name}' not found in Registry.")
        return f"Error: Tool '{tool_name}' is not currently available."

    try:
        if isinstance(parameters, str):
            parameters = json.loads(parameters)

        print(f"[SYSTEM] Executing {tool_name} with params: {parameters}")

        agent_instance = TOOL_REGISTRY[tool_name]

        # Inject user_id into persona-aware agents without polluting the LLM schema
        if tool_name in USER_ID_AWARE_TOOLS and user_id:
            result = agent_instance(**parameters, user_id=user_id)
        else:
            result = agent_instance(**parameters)

        return result

    except TypeError as e:
        return f"Parameter Error: The tool '{tool_name}' received invalid arguments. {str(e)}"
    except Exception as e:
        return f"Execution Error in {tool_name}: {str(e)}"


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False)