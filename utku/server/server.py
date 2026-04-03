from flask import Flask, render_template, request, jsonify, g
import sys
import os
import json
# Bridge the path to the 'chatbot' directory
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from chatbot.chatbot import ask_question, load_model
# Import the agents from the sibling file
from chatbot.ai_agents import (
    CalculatorAgent, WeatherAgent, UserProfileUpdateAgent,
    TripFeedbackAgent, RecommendationExplainerAgent, RouteGenerationFormatAgent,
    POISuggestionAgent, ItineraryModificationAgent, ChatTitleAgent,
    POIDataAgent, POI_search_agent, UserPersonaListAgent,
    GeneratedRouteExplanationAgent,
)

app = Flask(__name__)

# ✅ Fixed — keys must exactly match tool_template["name"]
TOOL_REGISTRY = {
    "calculator":              CalculatorAgent(),
    "get_weather":             WeatherAgent(),
    "update_user_profile":     UserProfileUpdateAgent(),
    "submit_trip_feedback":    TripFeedbackAgent(),
    "explain_recommendation":  RecommendationExplainerAgent(),
    "generate_route_format":   RouteGenerationFormatAgent(),
    "suggest_poi":             POISuggestionAgent(),
    "modify_itinerary":        ItineraryModificationAgent(),
    "generate_chat_title":     ChatTitleAgent(),
    "get_poi_details":         POIDataAgent(),
    "search_poi_by_category":  POI_search_agent(),
    "list_user_personas":      UserPersonaListAgent(),
    "explain_generated_route": GeneratedRouteExplanationAgent(),
}

# Agents that require the user_id to be injected at call time.
# These agents accept a 'user_id' kwarg even though it is NOT exposed in the
# LLM tool schema (to keep the LLM's job simple).
USER_ID_AWARE_TOOLS = {"list_user_personas", "generate_route_format", "explain_recommendation", "update_user_profile"}


# Tools whose output is returned verbatim to the frontend — NO second LLM call.
RAW_OUTPUT_TOOLS = {"generate_route_format"} #set()

# Pre-load model on startup
print("[SYSTEM] Initializing AI Engine...")
load_model()


@app.route('/')
def index():
    return render_template('index.html')

nematron_sys_prompt=(
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
        print("query recieved:\n")
        print(user_query)
        SYSTEM_PROMPT = """You are a friendly and knowledgeable travel assistant for a route planning app.

        CRITICAL RULES:
        - NEVER invent place names, addresses, ratings, or coordinates from memory.
        - ALL place and route data MUST come from a tool call. No exceptions.
        - If a tool returns no results, say so honestly rather than filling in from memory.
        - Always present tool results in a natural, engaging way — don't just dump raw data.
        - The app's database is focused on Ankara, Turkey. Scope your responses accordingly.
        """
        # 1. Build message history with system prompt
        messages = [
            {
                "role": "system",
                "content": (nematron_sys_prompt)
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

            print("\n\nTool Result")
            print(str(tool_result))

            # ... [Tool execution happens above, result stored in tool_result] ...

            # 5. Short-circuit: return raw tool output verbatim for structured-data tools
            # This bypasses the second LLM call to save time/tokens when raw data is preferred.
            if tool_name in RAW_OUTPUT_TOOLS:
                print(f"[SYSTEM] Raw output shortcut for '{tool_name}' — skipping second LLM call")
                
                return jsonify({
                    "status": "success",
                    "tool_used": tool_name,
                    "tool_params": params,
                    "response": tool_result
                })

            # 6. Standard Flow: Append tool call + result to history (ChatML / Tool-use standard)
            messages.append({"role": "assistant", "content": llm_output.get("raw", "")})
            messages.append({
                "role": "tool",
                "name": tool_name,
                "content": str(tool_result)
            })

            # 7. Final LLM call to generate the natural language answer
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
            # Case where no tool was called by the LLM
            return jsonify({
                "status": "success",
                "response": llm_output["content"]
            })

    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500


# server.py içindeki invoke_action fonksiyonunu güncelle

import inspect # En üste ekle

def invoke_action(tool_name: str, parameters: dict, user_id: str = None):
    if tool_name not in TOOL_REGISTRY:
        return f"Error: Tool '{tool_name}' is not currently available."

    try:
        if isinstance(parameters, str):
            parameters = json.loads(parameters)

        agent_instance = TOOL_REGISTRY[tool_name]
        
        # 🚀 KRİTİK FİLTRELEME: Metodun sadece beklediği parametreleri ayıkla
        sig = inspect.signature(agent_instance.__call__)
        valid_params = [p.name for p in sig.parameters.values()]
        
        # Sadece metodun imzasında (signature) olan parametreleri al
        filtered_params = {k: v for k, v in parameters.items() if k in valid_params}
        
        print(f"[SYSTEM] Executing {tool_name} with filtered params: {filtered_params.keys()}")

        # user_id enjeksiyonu
        if "user_id" in valid_params and user_id:
            result = agent_instance(**filtered_params, user_id=user_id)
        else:
            result = agent_instance(**filtered_params)

        return result

    except Exception as e:
        return f"Execution Error in {tool_name}: {str(e)}"


# ---------------------------------------------------------------------------
# /explain_route  — direct route explanation (no LLM tool-call parsing)
# ---------------------------------------------------------------------------
# Called by the frontend "Ask LLM about Route" button. Accepts the route
# points directly so we never have to parse the LLM's tool-call JSON (which
# breaks on Turkish / special characters). Instead:
#   1. Call GeneratedRouteExplanationAgent deterministically.
#   2. Ask the LLM to write a friendly narrative from the structured output.

@app.route('/explain_route', methods=['POST'])
def explain_route():
    data        = request.json or {}
    stop_names  = data.get("route_stop_names", [])
    route_summary = data.get("route_summary", {})
    user_id     = data.get("user_id")

    if not stop_names:
        return jsonify({"status": "error", "message": "route_stop_names is required"}), 400

    try:
        # Step 1 — run the deterministic agent (no LLM, just DB lookups + formatting)
        agent    = TOOL_REGISTRY["explain_generated_route"]
        raw_data = agent(route_stop_names=stop_names, route_summary=route_summary or None)

        print(f"[SYSTEM] /explain_route: agent returned {len(raw_data)} chars")

        # Step 2 — single LLM call: turn the structured data into a friendly narrative
        narrative_prompt = (
            "The following structured data block contains every database field for each stop "
            "on the user's route. Your job:\n"
            "1. Print the ROUTE OVERVIEW section verbatim.\n"
            "2. For each stop, print its data block verbatim, then immediately follow it with "
            "a 2-3 sentence friendly description (what the place is, why it is interesting, "
            "any practical tips).\n"
            "3. End with an overall verdict: highlights, total time estimate, budget level, "
            "and one practical travel tip.\n\n"
            "DATA BLOCK:\n"
            f"{raw_data}"
        )

        messages = [
            {
                "role": "system",
                "content": (
                    "You are a friendly Ankara travel guide. "
                    "Present the route data clearly and add warm, helpful narrative commentary. "
                    "Never invent information not present in the data block."
                ),
            },
            {"role": "user", "content": narrative_prompt},
        ]

        llm_output = ask_question(messages)
        narrative  = llm_output.get("content", raw_data)

        return jsonify({
            "status":   "success",
            "tool_used": "explain_generated_route",
            "response": narrative,
        })

    except Exception as e:
        print(f"[ERROR] /explain_route: {e}")
        return jsonify({"status": "error", "message": str(e)}), 500


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False)