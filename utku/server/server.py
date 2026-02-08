from flask import Flask, render_template, request, jsonify
import sys
import os
import json
# Bridge the path to the 'chatbot' directory
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from chatbot.chatbot import ask_question, load_model
from chatbot.ai_agents import calculatorAgent, weatherAgent

app = Flask(__name__)

TOOL_REGISTRY = {
    "calculator_agent": calculatorAgent(),
    "weather_agent": weatherAgent(),
}


# Pre-load model on startup to avoid delays on first request
print("[SYSTEM] Initializing AI Engine...")
load_model()

@app.route('/')
def index():
    """Renders the professional dashboard."""
    return render_template('index.html')

@app.route('/chatbot', methods=['POST'])
def handle_chat():
    """API Endpoint for AI inference."""
    data = request.json
    query = data.get("query", "")
    
    if not query:
        return jsonify({"status": "error", "message": "Query is empty"}), 400
    
    try:
        long_ans = ""
        # 1. Ask the LLM. 
        # ask_question is configured to return JSON if a tool is needed.
        llm_output = ask_question(query)
        print(llm_output)
        long_ans += f"This is llm's initial output: {llm_output}\n\n"
        # 2. Check if the LLM wants to call a tool
        if llm_output["type"] == "tool_call":
            tool_name = llm_output["data"]["tool_call"]["name"]
            params = llm_output["data"]["tool_call"]["parameters"]    
            print(tool_name, params)
            
            long_ans += f"The tool {tool_name}, with parameters : {params} is invoked\n\n"

            # 3. Execute the local function
            tool_result = invoke_action(tool_name, params)

            long_ans += f"This is tool's result {tool_result}\n\n"

            # 4. Send the result back to the LLM to generate a final natural response
            final_response = ask_question(f"The tool {tool_name} returned: {tool_result}. Summarize this for the user.")
            
            return jsonify({
                "status": "success",
                "response": long_ans + "\n\n" + final_response["data"]
            })
        else: 
            return jsonify({
                "status": "success",
                "response": llm_output["data"]
            })
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500


def invoke_action(tool_name, parameters):
    """
    Dynamically calls the requested tool.
    tool_name: str (e.g., "calculator_agent")
    parameters: dict (e.g., {"expression": "2+2"})
    """
    if tool_name not in TOOL_REGISTRY:
        return f"Error: Tool '{tool_name}' not found."
    
    try:
        result = TOOL_REGISTRY[tool_name](**parameters)
        return result
    except Exception as e:
        return f"Error executing {tool_name}: {str(e)}"
    
@app.route('/tools', methods=["GET"])
def get_tools_list():
    # Return the tool_template metadata instead of the live objects
    return jsonify([agent.tool_template for agent in TOOL_REGISTRY.values()])

if __name__ == "__main__":
    # Running on internal port 5000
    app.run(host="0.0.0.0", port=5000, debug=False)