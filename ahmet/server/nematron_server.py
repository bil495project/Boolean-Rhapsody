from flask import Flask, render_template, request, jsonify
import sys
import os
import json
# Bridge the path to the 'chatbot' directory
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from chatbot.nematron_chatbot import ask_question

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
MAX_HISTORY = 10

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/chatbot', methods=['POST'])
def handle_chat():
    data = request.json
    user_query = data.get("query", "")
    history = data.get("history", [])  # List of {role, content} dicts from frontend

    print("****************************************************************")
    print(f"History\n", format_conversation(history))
    print("****************************************************************")
    
    if not user_query:
        return jsonify({"status": "error", "message": "Query is empty"}), 400
    
    try:
        # 1. Start message history with system prompt
        messages = [
            {"role": "system", "content": "You are a helpful assistant with access to tools."},
        ]
        
        # 2. Append previous conversation history (last N messages from frontend)
        for msg in history:
            messages.append(msg)
            # role = msg.get("role", "user")
            # content = msg.get("content", "").strip()

            # # Skip empty messages
            # if not content:
            #     continue

            # # Skip server error messages
            # if "LLM Server bağlantı hatası" in content or "500 INTERNAL SERVER ERROR" in content:
            #     continue

            # messages.append({"role": role, "content": content})
        
        # 3. Append the current user query
        messages.append({"role": "user", "content": user_query})

        # 2. First LLM Call
        llm_output = ask_question(messages) #-1 if nematron
        messages.append(llm_output) #CRITICAL: Append the tool call AND the result to history
        
        if llm_output["tool_calls"]:
            print("It is in tools_calls: ", llm_output["tool_calls"][0])
            #example output as follows:
            #'tool_calls': [{'id': 'kKZVAyhago62mlvdQqWwKywJmwuzbX8z', 'function': {'arguments': '{"poi_name":"Anıtkabir"}', 'name': 'get_poi_details'}, 'type': 'function'}]
            tool_call_output = llm_output["tool_calls"][0]
            _id = tool_call_output["id"]
            arguments = tool_call_output["function"]["arguments"]
            tool_name = tool_call_output["function"]["name"]

            print("ID: ",_id)
            print("Arguments: ", arguments)
            print("Tool_name:", tool_name)
            
            # Execute tool
            tool_result = invoke_action(tool_name, arguments)
            print("Tool output as follows:", tool_result)
            print()

            # This follows the ChatML / Tool-use standard
            messages.append({
                "role": "tool", 
                "tool_call_id": _id,
                "name": tool_name, 
                "content": str(tool_result)
            })

            # 4. Final LLM Call with history to get the natural language answer
            final_output = ask_question(messages) #-1 if nematron
            #example output
            # "final_output": {
            #     "role": "assistant",
            #     "tool_calls": [],
            #     "content": "Anıtkabir is much more than just a mausoleum—it’s a sprawling complex that blends modern architecture with symbolic elements reflecting the life and legacy of Mustafa Kemal Atatürk. Here’s a deeper look at its key features:\n\n### 1. Architectural Highlights\n- **Design Collaboration**: Designed by architects Emin Onat and Orhan Arda. Their design combines classical Turkish motifs with modernist principles.\n- **Structure**: The central cylindrical tower is capped by a conical roof. The tower is surrounded by a circular colonnade that supports a large marble canopy.\n- **Materials**: Over 110,000 m³ of marble from Afyon was used.\n- **Symbolic Layout**: Organized around a grand ceremonial axis symbolizing the journey to the eternal resting place of Atatürk.\n\n### 2. Key Areas Within the Complex\n| Area | What to See | Highlights |\n|------|-------------|------------|\n| **The Hall of Honor** | The actual tomb of Atatürk | A massive red marble sarcophagus beneath a golden dome. |\n| **The Museum** | Exhibits on Atatürk’s life | Personal belongings, original documents, and a reconstructed study. |\n| **The Peace Park** | Landscaped garden | Features olive trees, cypress avenues, and a reflecting pool. |\n| **Changing of the Guard** | Daily ceremony | A precise military ritual performed by the Presidential Guard. |\n\n### 3. Visiting Practicalities\n- **Location**: Çankaya district, Ankara, Turkey.\n- **Opening Hours**: Open daily from 09:00 am to 05:00 pm.\n- **Admission**: Free of charge for all visitors.\n- **Best Time to Visit**: Early morning or late afternoon for the best lighting on the marble façade.",
            #     "reasoning": "The assistant provided a comprehensive overview of Anıtkabir covering history, architecture, symbolism, and visiting information in response to the user's general inquiry."
            # }
            print("Final Response Output", format_conversation(final_output))
            print()
            print("tool_used: " + tool_name)
            print("tool_params:", arguments)

            #for debug purposes
            final_output = ask_question(messages)
            messages.append(final_output)
            print(f"Updated Conversation: \n", format_conversation(messages))

            return jsonify({
                "status": "success",
                "tool_used": tool_name,
                "tool_params": arguments,
                "response": final_output["content"]
            })
        
        else:
            print(f"Updated Conversation: \n", format_conversation(messages))
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

def format_conversation(messages):
    """
    Formats a list of message dictionaries into a readable JSON string.
    """
    # We use ensure_ascii=False to keep special characters (like 'ı' in Anıtkabir)
    # and indent=2 to create the 'tree' structure.
    readable_json = json.dumps(messages, indent=2, ensure_ascii=False)
    
    # Optional: This part cleans up the 'escaped' newlines (\n) 
    # so the text inside looks like a real paragraph when printed.
    readable_json = readable_json.replace('\\n', '\n')
    
    return readable_json

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False)
