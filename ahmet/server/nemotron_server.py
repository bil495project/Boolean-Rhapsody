from flask import Flask, render_template, request, jsonify
import sys
import os
import json
# Bridge the path to the 'chatbot' directory
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
print(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from chatbot.ai_agents import calculatorAgent, weatherAgent
from chatbot.nemotron_chatbot_v1 import ask_question

app = Flask(__name__)

TOOL_REGISTRY = {
    "calculator_agent": calculatorAgent(),
    "weather_agent": weatherAgent(),
}

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

conversation = [{"role": "system", "content": SYSTEM_PROMPT}]

# # Pre-load model on startup to avoid delays on first request
# print("[SYSTEM] Initializing AI Engine...")
# load_model()

@app.route('/')
def index():
    """Renders the professional dashboard."""
    return render_template('index.html')

@app.route('/chatbot', methods=['POST'])
def handle_chat():
    """API Endpoint for AI inference."""
    global conversation
    data = request.json
    query = data.get("query", "")
    
    if not query:
        return jsonify({"status": "error", "message": "Query is empty"}), 400
    
    try:
        conversation.append({"role": "user", "content": query})
        # 1. Ask the LLM. 
        # ask_question is configured to return JSON if a tool is needed.
        conversation = ask_question(conversation) # llm output is appended as returned conversation
        llm_output = conversation[-1]
        # 2. Check if the LLM wants to call a tool
        if llm_output["tool_calls"]:
            tool_name = llm_output["tool_calls"][0]["function"]["name"]
            params = json.loads(llm_output["tool_calls"][0]["function"]["arguments"])
            _id = llm_output["tool_calls"][0]["id"]
            # 3. Execute the local function
            tool_result = invoke_action(tool_name, params)
            conversation.append({"role": "tool", "tool_call_id": _id, "name": tool_name, "content": str(tool_result)})

            # 4. Send the result back to the LLM to generate a final natural response
            conversation = ask_question(conversation)
            final_response = conversation[-1]

            print(f"Toolu COntext: {final_response['content']}")
            print(conversation)
            return jsonify({
                "status": "success",
                "response": final_response["content"]
            })
        else: 
            print(conversation)
            return jsonify({
                "status": "success",
                "response": llm_output["content"]
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