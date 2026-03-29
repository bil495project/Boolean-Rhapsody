from flask import Flask, render_template, request, jsonify, g
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
    POI_suggest_agent, ItineraryModificationAgent, ChatTitleAgent,
    POI_data_agent, POI_search_agent, UserPersonaListAgent,
    RouteGenerationFormatAgent
)

app = Flask(__name__)

# Now the classes are defined and accessible here

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
    "get_poi_details":          POI_data_agent(),        # Lookup by name (single or multi-instance)
    "search_poi_by_category":   POI_search_agent(),      # Browse by category (cafes, restaurants…)
    "get_user_personas":        UserPersonaListAgent(),  # Lists user's travel personas
    "generate_route_format":    RouteGenerationFormatAgent(),  # Formats payload for Route Generation Algorithm
}
USER_ID_AWARE_TOOLS = {"get_user_personas", "search_route", "generate_route_format"}

# Tools whose output is returned verbatim to the frontend — NO second LLM call.
RAW_OUTPUT_TOOLS = {"generate_route_format"}

MAX_HISTORY = 10

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/chatbot', methods=['POST'])
def handle_chat():
    data = request.json
    user_query = data.get("query", "")
    history = data.get("history", [])  # List of {role, content} dicts from frontend
    user_id = data.get("user_id", None)
    
    g.user_id = user_id

    print("****************************************************************")
    print(f"History\n", format_conversation(history))
    print("****************************************************************")
    
    if not user_query:
        return jsonify({"status": "error", "message": "Query is empty"}), 400
    
    try:
        # 1. Start message history with system prompt
        messages = [
            {
                "role": "system",
                "content": (
                    "You are a helpful travel assistant. You MUST use the provided tools for every relevant request. "
                    "NEVER answer from memory or reason through a task yourself when a tool exists for it.\n\n"

                    "## Tool Selection Rules (follow in order)\n\n"

                    "1. ROUTE PLANNING — generate_route_format\n"
                    "   Trigger: user mentions ANY of: a starting point, a specific named place to visit, "
                    "   meal needs (breakfast/lunch/dinner), hotel stay, or a number of stops.\n"
                    "   Examples: 'plan a route', 'I want to start at X', 'visit Y for lunch', "
                    "   'end at a hotel', 'I need breakfast', '6 stops total'.\n"
                    "   Action: ALWAYS call generate_route_format. Follow these extraction rules STRICTLY:\n"
                    "   a) named_locations: include EVERY specific named place the user mentions, "
                    "      using their EXACT verbatim name (e.g. 'Şimşek Aspava', 'Anıtkabir'). "
                    "      NEVER shorten or paraphrase place names.\n"
                    "   b) start_location: if user says 'I'll start at X' or 'starting from X', "
                    "      set start_location to that exact name. This field MUST be set when a start is given.\n"
                    "   c) poi_slots: for each named place use type=PLACE with the EXACT full name. "
                    "      Include the start place as the FIRST poi_slot.\n"
                    "   d) Example: user says 'Start at Anıtkabir, visit Şimşek Aspava for lunch, end at 4-star hotel, 6 stops'\n"
                    "      → named_locations: ['Anıtkabir','Şimşek Aspava']\n"
                    "      → start_location: 'Anıtkabir'\n"
                    "      → poi_slots: [{type:PLACE,name:'Anıtkabir'},{type:PLACE,name:'Şimşek Aspava'},{type:TYPE,poiType:'HOTEL',filters:{minRating:4}}]\n"
                    "      → meal_preferences: {needsLunch:true}\n"
                    "   Do NOT answer with text — call the tool.\n\n"

                    "2. SIMPLE POI-LIST ROUTE — search_route\n"
                    "   Trigger: user gives only a plain list of POIs with no start/end/meals/hotel constraints.\n"
                    "   Example: 'optimise a route through Anıtkabir, Kocatepe, Kuğulu Park'.\n"
                    "   Do NOT use search_route when the user mentions meals, a starting point, or hotel needs "
                    "   — use generate_route_format instead.\n\n"

                    "3. PLACE LOOKUP — get_poi_details\n"
                    "   Trigger: user asks about a specific named place (details, address, rating, etc.).\n"
                    "   Never guess or make up place information.\n\n"

                    "4. CATEGORY BROWSE — search_poi_by_category\n"
                    "   Trigger: user wants recommendations for a type of place "
                    "   (cafes, restaurants, parks, hotels, museums, bars, landmarks).\n\n"

                    "5. TRAVEL PERSONA — get_user_personas\n"
                    "   Trigger: user asks about their travel personality or saved profiles.\n\n"

                    "## Output Rules\n"
                    "- For generate_route_format: return the raw tool result JSON exactly as-is. "
                    "  Do NOT narrate, summarise, or wrap it in prose.\n"
                    "- For all other tools: present results exactly as returned — do not omit details."
                )

            },
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

            # 4. Short-circuit: return raw tool output verbatim for structured-data tools
            #    (same response shape as other tools — tool_used, tool_params, response)
            if tool_name in RAW_OUTPUT_TOOLS:
                print(f"[SYSTEM] Raw output shortcut for '{tool_name}' — skipping second LLM call")
                print("tool_used: " + tool_name)
                print("tool_params:", arguments)
                return jsonify({
                    "status": "success",
                    "tool_used": tool_name,
                    "tool_params": arguments,
                    "response": tool_result
                })

            # 4b. Second LLM call — convert tool result into a natural language answer
            final_output = ask_question(messages)
            messages.append(final_output)
            print("Final Response Output", format_conversation(final_output))
            print()
            print("tool_used: " + tool_name)
            print("tool_params:", arguments)
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

        # 3a. Inject user_id for tools that are user-aware
        if tool_name in USER_ID_AWARE_TOOLS:
            from flask import g
            parameters["user_id"] = getattr(g, "user_id", None)

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
