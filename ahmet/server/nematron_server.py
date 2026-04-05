from flask import Flask, render_template, request, jsonify, g
import sys
import os
import json
# Bridge the path to the 'chatbot' directory
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from chatbot.nematron_chatbot import ask_question

# Import the agents from the sibling file
from chatbot.ai_agents import (
    calculatorAgent, weatherAgent, UserProfileUpdateAgent, #UserProfileAgent_SetInfo,
    UserFeedbackAgent, RecommendationExplainerAgent,#XAIJustificationAgent,
    POI_suggest_agent, ItineraryModificationAgent, ChatTitleAgent,
    POI_data_agent, POI_search_agent, UserPersonaListAgent,
    RouteGenerationFormatAgent, GeneratedRouteExplanationAgent
)
app = Flask(__name__)

# Now the classes are defined and accessible here

TOOL_REGISTRY = {
    "calculator_agent":    calculatorAgent(),
    "weather_agent":       weatherAgent(),
    "update_user_profile":  UserProfileUpdateAgent(),     # Matches TC-LLM-U-008
    "submit_user_feedback":UserFeedbackAgent(),            # Matches TC-LLM-U-005
    "explain_recommendation":RecommendationExplainerAgent(),       # Matches TC-LLM-U-006
    "suggest_poi":         POI_suggest_agent(),            # Matches TC-LLM-U-003
    "modify_itinerary":    ItineraryModificationAgent(),   # Matches TC-LLM-U-007
    "generate_chat_title": ChatTitleAgent(),               # Matches TC-LLM-U-015
    "get_poi_details":          POI_data_agent(),        # Lookup by name (single or multi-instance)
    "search_poi_by_category":   POI_search_agent(),      # Browse by category (cafes, restaurants…)
    "get_user_personas":        UserPersonaListAgent(),  # Lists user's travel personas
    "generate_route_format":    RouteGenerationFormatAgent(),  # Formats payload for Route Generation Algorithm
    "explain_generated_route": GeneratedRouteExplanationAgent(),
}
USER_ID_AWARE_TOOLS = {"get_user_personas", "generate_route_format"}

# Tools whose output is returned verbatim to the frontend — NO second LLM call.
RAW_OUTPUT_TOOLS = {"generate_route_format", "explain_generated_route"} #set()

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
                    # ── Section 1: Identity & Persona ─────────────────────────
                    "You are a travel-planning assistant specializing in Ankara, Turkey. "
                    "You help users discover places, plan routes, manage travel personas, and review past trips. "
                    "You MUST use the provided tools for every relevant request — NEVER answer from memory, "
                    "guess place details, or reason through a task yourself when a matching tool exists. "
                    "Call exactly one tool per turn.\n\n"

                    # ── Section 2: Tool Decision Guide ────────────────────────
                    "## Tool Decision Guide\n"
                    "Evaluate the user's message against the following tools IN ORDER. "
                    "Use the FIRST tool whose trigger conditions match.\n\n"

                    "### 1. generate_route_format — Route Planning\n"
                    "WHEN TO USE: The user wants to plan a trip or route and provides ANY of: "
                    "a starting point, specific named places to visit, meal needs (breakfast/lunch/dinner), "
                    "hotel/lodging, a desired number of stops, or a list of POIs to connect.\n"
                    "WHEN NOT TO USE: The user only asks about a single place's details (use get_poi_details) "
                    "or wants to browse a category without planning a route (use search_poi_by_category).\n"
                    "EXAMPLE: 'Start at Anıtkabir, visit Şimşek Aspava for lunch, end at a 4-star hotel, 6 stops' "
                    "→ call generate_route_format.\n"
                    "EXTRACTION RULES:\n"
                    "  a) named_locations — include EVERY specific named place using the user's EXACT verbatim name "
                    "(e.g. 'Şimşek Aspava', 'Anıtkabir'). NEVER shorten or paraphrase.\n"
                    "  b) start_location — if the user says 'start at X' or 'starting from X', set this to the exact name.\n"
                    "  c) poi_slots — for each named place use type=PLACE with the EXACT full name. "
                    "Include the start place as the FIRST poi_slot. Use null entries for auto-fill slots.\n"
                    "  d) Do NOT produce a text answer — just call the tool.\n\n"

                    "### 2. get_poi_details — Place Lookup\n"
                    "WHEN TO USE: The user asks about a specific named place — its address, rating, "
                    "price level, type, or general details.\n"
                    "WHEN NOT TO USE: The user wants to browse a category (use search_poi_by_category) "
                    "or plan a multi-stop route (use generate_route_format).\n"
                    "EXAMPLE: 'Tell me about Anıtkabir' → call get_poi_details with poi_name='Anıtkabir'.\n\n"

                    "### 3. search_poi_by_category — Category Browse\n"
                    "WHEN TO USE: The user wants recommendations for a TYPE of place "
                    "(cafes, restaurants, parks, hotels, museums, bars, landmarks) without naming a specific place.\n"
                    "WHEN NOT TO USE: The user names a specific place (use get_poi_details) "
                    "or wants to plan a route (use generate_route_format).\n"
                    "EXAMPLE: 'Show me the best restaurants' → call search_poi_by_category with "
                    "place_category='RESTAURANTS'.\n\n"

                    "### 4. list_user_personas — Travel Persona Retrieval\n"
                    "WHEN TO USE: The user asks about their travel personality, saved profiles, or personas.\n"
                    "WHEN NOT TO USE: The user wants to UPDATE their preferences (use user_profile_agent) "
                    "or plan a route (use generate_route_format).\n"
                    "EXAMPLE: 'What kind of traveller am I?' → call get_user_personas.\n\n"

                    "### 5. update_user_profile — Update User Preferences\n"
                    "WHEN TO USE: The user wants to change or set a travel preference "
                    "(e.g. 'I prefer budget-friendly places', 'set my pace to relaxed').\n"
                    "WHEN NOT TO USE: The user wants to VIEW their personas (use get_user_personas).\n"
                    "EXAMPLE: 'I prefer historical sites' → call user_profile_agent.\n\n"

                    "### 6. suggest_poi — Profile-Based POI Suggestions\n"
                    "WHEN TO USE: The user asks for personalized POI suggestions based on their profile "
                    "and an existing or current route context.\n"
                    "WHEN NOT TO USE: The user wants a generic category search without route context "
                    "(use search_poi_by_category).\n"
                    "EXAMPLE: 'Suggest some places along my current route' → call suggest_poi.\n\n"

                    "### 7. modify_itinerary — Itinerary Modification\n"
                    "WHEN TO USE: The user wants to add, remove, or edit a POI in an existing trip itinerary.\n"
                    "WHEN NOT TO USE: The user wants to create a new route from scratch "
                    "(use generate_route_format).\n"
                    "EXAMPLE: 'Remove the second stop from trip T-123' → call modify_itinerary.\n\n"

                    "### 8. submit_user_feedback — Trip Feedback\n"
                    "WHEN TO USE: The user provides feedback or a rating about a completed trip "
                    "(e.g. 'that trip was great', 'I didn't like the route').\n"
                    "WHEN NOT TO USE: The user is asking a general question or planning a new trip.\n"
                    "EXAMPLE: 'The trip T-456 was amazing, loved every stop' → call submit_user_feedback.\n\n"

                    "### 9. explain_recommendation — Explainable AI Justification\n"
                    "WHEN TO USE: The user asks WHY a specific recommendation was made "
                    "(e.g. 'why did you suggest Anıtkabir?', 'why was I recommended that hotel?').\n"
                    "WHEN NOT TO USE: The user asks general questions about a place (use get_poi_details).\n"
                    "EXAMPLE: 'Why did you recommend that restaurant?' → call get_xai_justification.\n\n"

                    "### 10. calculator_agent — Math Calculations\n"
                    "WHEN TO USE: The user asks a pure math question (arithmetic, sqrt, powers).\n"
                    "WHEN NOT TO USE: Travel-related distance or time calculations — those are handled "
                    "by route planning tools.\n"
                    "EXAMPLE: 'What is sqrt(144) * 3?' → call calculator_agent.\n\n"

                    "### 11. weather_agent — Weather Lookup\n"
                    "WHEN TO USE: The user asks about current weather conditions in a specific city.\n"
                    "WHEN NOT TO USE: The user asks about historical weather or forecasts (not supported).\n"
                    "EXAMPLE: 'What's the weather in Ankara?' → call weather_agent.\n\n"

                    "### 12. explain_generated_route — Route Explanation\n"
                    "WHEN TO USE: The user presents a list of route stops and asks you to explain, describe, "
                    "or summarise the route or any of its places in detail.\n"
                    "WHEN NOT TO USE: The user wants to CREATE a new route (use generate_route_format) "
                    "or look up a single place (use get_poi_details).\n"
                    "EXAMPLE: 'Tell me about this route: Anıtkabir → Kocatepe Camii → Aspava' "
                    "→ call explain_generated_route, passing every stop name in route_stop_names in order.\n"
                    "EXTRACTION RULES:\n"
                    "  a) route_stop_names — include EVERY stop exactly as written. Preserve Turkish characters.\n"
                    "  b) route_summary — populate total_duration_min, total_distance_km, and travel_mode "
                    "when the user provides that information in their message.\n\n"

                    # ── Section 3: Response Formatting ────────────────────────
                    "## Response Formatting\n\n"
                    "### RAW_OUTPUT tools (generate_route_format)\n"
                    "The result of this tool is returned DIRECTLY to the frontend — you will NOT get a chance "
                    "to narrate or comment on it. Simply call the tool with correct arguments. "
                    "Do NOT add any text before or after the tool call.\n\n"
                    "### All other tools\n"
                    "Present the tool's results to the user in natural language. "
                    "Preserve ALL details returned by the tool — do not omit addresses, ratings, or other attributes. "
                    "Be friendly, concise, and informative."
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

            # 4. Short-circuit for tools that bypass the generic second LLM call
            if tool_name in RAW_OUTPUT_TOOLS:
                print(f"[SYSTEM] Raw output shortcut for '{tool_name}' — skipping second LLM call")
                print("tool_used: " + tool_name)
                print("tool_params:", arguments)

                # explain_generated_route gets its own rendering pipeline
                if tool_name == "explain_generated_route":
                    rendered = _render_route_explanation(tool_result)
                    return jsonify({
                        "status": "success",
                        "tool_used": tool_name,
                        "tool_params": arguments,
                        "response": rendered
                    })

                # All other RAW_OUTPUT tools return verbatim
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

# ── Shared helper: render route explanation ──────────────────────────────
def _render_route_explanation(raw_json: str) -> str:
    """
    Takes the JSON string returned by GeneratedRouteExplanationAgent,
    renders deterministic data blocks per stop, calls the LLM for
    creative narration verdicts, and returns the final interleaved string.

    Used by both /explain_route and the handle_chat() intercept for
    the explain_generated_route tool.
    """
    agent_data = json.loads(raw_json)
    overview   = agent_data.get("route_overview", {})
    stops      = agent_data.get("stops", [])

    # ── 1. Route overview as Markdown (deterministic) ─────────────────
    md_lines = ["## 🗺️ Route Overview", ""]
    md_lines.append(f"- **Stops:** {overview.get('total_stops', len(stops))}")

    dur = overview.get("total_duration_min")
    if dur is not None:
        hrs, mins = divmod(int(dur), 60)
        md_lines.append(f"- **Est. Duration:** {f'{hrs}h {mins}m' if hrs else f'{mins} min'}")

    dist = overview.get("total_distance_km")
    if dist is not None:
        md_lines.append(f"- **Est. Distance:** {dist:.1f} km")

    mode = overview.get("travel_mode")
    if mode:
        md_lines.append(f"- **Travel Mode:** {mode.capitalize()}")

    md_lines.append("")
    md_lines.append("---")
    md_lines.append("")
    overview_md = "\n".join(md_lines)

    # ── 2. Render each stop's data block as plain text ────────────────
    def _render_stop_block(s: dict) -> str:
        if "error" in s:
            return (
                f"Stop {s['stop']}: {s['name']}\n"
                f"  ⚠  {s['error']}"
            )
        return (
            f"Stop {s['stop']}: {s['name']}\n"
            f"Type(s): {s['types']}\n"
            f"Address: {s['address']}\n"
            f"Coordinates: {s['coordinates']}\n"
            f"Rating: {s['rating']}\n"
            f"Price Level: {s['price_level']}\n"
            f"Status: {s['status']}\n"
            f"Planned Visit: {s['planned_visit']}"
        )

    rendered_blocks = [_render_stop_block(s) for s in stops]

    # ── 3. LLM call — creative verdicts only ─────────────────────────
    VERDICT_DELIM = "===NEXT==="

    llm_stop_lines = []
    for s in stops:
        if "error" in s:
            llm_stop_lines.append(f"- Stop {s['stop']}: {s['name']} (no data available)")
        else:
            llm_stop_lines.append(
                f"- Stop {s['stop']}: {s['name']} | "
                f"Type: {s['types']} | Rating: {s['rating']} | "
                f"Price: {s['price_level']}"
            )
    stops_summary = "\n".join(llm_stop_lines)

    narrative_system = (
        "You are a friendly, opinionated Ankara travel guide. "
        "You will receive a list of route stops. "
        "For EACH stop write exactly one paragraph (2–3 sentences): "
        "what the place is, why it is interesting, and one practical tip. "
        "Write in your own words — do NOT reproduce the raw data fields. "
        f"Separate each stop's paragraph with exactly: {VERDICT_DELIM}\n"
        "Do NOT include stop numbers, headings, or any other formatting — "
        "just the paragraph text for each stop separated by the delimiter. "
        "After the last stop's paragraph write one final paragraph as an "
        f"overall route verdict, also preceded by {VERDICT_DELIM}."
    )

    narrative_user = f"Stops:\n{stops_summary}"

    messages = [
        {"role": "system", "content": narrative_system},
        {"role": "user",   "content": narrative_user},
    ]

    llm_output    = ask_question(messages)
    raw_narrative = llm_output.get("content", "")

    # ── 4. Split verdicts and interleave with data blocks ────────────
    import re
    # Add tolerance for LLM formatting errors (e.g. |||NEXT||, ==NEXT===, etc)
    raw_narrative = re.sub(r'(\|+NEXT\|+|=+NEXT=+)', VERDICT_DELIM, raw_narrative)
    verdicts = [v.strip() for v in raw_narrative.split(VERDICT_DELIM) if v.strip()]

    parts = [overview_md]
    for i, block in enumerate(rendered_blocks):
        parts.append(block)
        if i < len(verdicts):
            parts.append(verdicts[i])

    # Append overall route verdict (last element) if present
    if len(verdicts) > len(rendered_blocks):
        parts.append("---")
        parts.append(verdicts[-1])
    # Fallback: if delimiter parsing failed, append raw narrative at end
    elif not verdicts and raw_narrative.strip():
        parts.append("---")
        parts.append(raw_narrative.strip())

    final_response = "\n\n".join(parts)
    print("[SYSTEM]: Finale response\t",final_response)
    return final_response


@app.route('/explain_route', methods=['POST'])
def explain_route():
    data        = request.json or {}
    stop_names  = data.get("route_stop_names", [])
    route_summary = data.get("route_summary", {})
    user_id     = data.get("user_id")

    if not stop_names:
        return jsonify({"status": "error", "message": "route_stop_names is required"}), 400

    try:
        agent    = TOOL_REGISTRY["explain_generated_route"]
        raw_json = agent(route_stop_names=stop_names, route_summary=route_summary or None)
        print("[SYSTEM]:\t",stop_names, route_summary)
        print(f"[SYSTEM] /explain_route: agent returned {len(raw_json)} chars")

        final_response = _render_route_explanation(raw_json)

        return jsonify({
            "status":    "success",
            "tool_used": "explain_generated_route",
            "response":  final_response,
        })

    except Exception as e:
        print(f"[ERROR] /explain_route: {e}")
        return jsonify({"status": "error", "message": str(e)}), 500


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False)