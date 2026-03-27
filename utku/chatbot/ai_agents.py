import math
import json
import requests

class BaseAgent:
    """Base class providing shared utility methods for all AI agents."""
    def _format_response(self, status: str, message: str, data: dict = None):
        return json.dumps({"status": status, "message": message, "data": data})


class calculatorAgent(BaseAgent):
    """Agent responsible for evaluating mathematical expressions safely."""
    tool_template = {
        "name": "calculator_agent",
        "description": "Performs mathematical calculations. Supports basic arithmetic, sqrt, and power functions.",
        "parameters": {
            "type": "object",
            "properties": {
                "expression": {
                    "type": "string",
                    "description": "The math expression to solve (e.g., 'sqrt(16) * 5')."
                }
            },
            "required": ["expression"]
        }
    }

    def __call__(self, expression: str):
        try:
            allowed_names = {
                "math": math,
                "sqrt": math.sqrt,
                "pow": math.pow,
                "abs": abs,
                "round": round
            }
            result = eval(expression, {"__builtins__": None}, allowed_names)
            return f"The result of '{expression}' is {result}."
        except Exception as e:
            return f"Calculation Error: {str(e)}"


class weatherAgent(BaseAgent):
    """Agent responsible for fetching real-time weather data for a specific location."""
    tool_template = {
        "name": "weather_agent",
        "description": "Retrieves current weather, temperature, and atmospheric conditions.",
        "parameters": {
            "type": "object",
            "properties": {
                "location": {
                    "type": "string",
                    "description": "The city and country (e.g., 'Berlin, Germany')."
                },
                "unit": {
                    "type": "string",
                    "enum": ["celsius", "fahrenheit"],
                    "description": "The temperature scale to use. Defaults to celsius."
                }
            },
            "required": ["location"]
        }
    }

    def __call__(self, location: str, unit: str = "celsius"):
        try:
            temp = 22 if unit == "celsius" else 72
            condition = "sunny"
            return f"The current weather in {location} is {temp}° {unit.capitalize()} and {condition}."
        except Exception as e:
            return f"Weather Service Error: {str(e)}"


class UserProfileAgent_SetInfo(BaseAgent):
    tool_template = {
        "name": "user_profile_agent",  # Matches TC-LLM-U-008
        "description": "Updates user preferences (e.g., budget-friendly, historical).",
        "parameters": {
            "type": "object",
            "properties": {
                "user_id": {"type": "string"},
                "user_data_update_info": {"type": "object"}
            },
            "required": ["user_id", "user_data_update_info"]
        }
    }

    def __call__(self, user_id, user_data_update_info):
        return f"User {user_id} preferences updated: {user_data_update_info}"


class UserFeedbackAgent(BaseAgent):
    tool_template = {
        "name": "submit_user_feedback",
        "description": "Logs user feedback for a specific completed trip.",
        "parameters": {
            "type": "object",
            "properties": {
                "trip_id": {"type": "string"},
                "user_feedback": {"type": "string", "description": "The textual feedback from the user."}
            },
            "required": ["trip_id", "user_feedback"]
        }
    }

    def __call__(self, trip_id, user_feedback):
        return f"Feedback for trip {trip_id} recorded: '{user_feedback[:50]}...'"


class XAIJustificationAgent(BaseAgent):
    tool_template = {
        "name": "get_xai_justification",
        "description": "Provides an explainable AI justification for a specific recommendation.",
        "parameters": {
            "type": "object",
            "properties": {
                "recommendation_id": {"type": "string"},
                "user_data": {"type": "object", "description": "Relevant user context for the explanation."}
            },
            "required": ["recommendation_id"]
        }
    }

    def __call__(self, recommendation_id, user_data=None):
        return (
            f"Justification for {recommendation_id}: This was recommended based on "
            "your preference for high-rated historical sites."
        )


# ---------------------------------------------------------------------------
# Shared helpers: fetch & describe travel personas from the backend
# ---------------------------------------------------------------------------
BACKEND_URL = "http://localhost:8080"


def _fetch_personas(user_id: str) -> list:
    """
    Calls the internal Spring Boot endpoint to retrieve the user's travel personas.
    This is a server-to-server call on localhost — no JWT token required.
    Returns a list of persona dicts, or an empty list on failure.
    """
    if not user_id:
        return []
    try:
        url = f"{BACKEND_URL}/api/users/{user_id}/personas"
        resp = requests.get(url, timeout=5)
        if resp.status_code == 200:
            return resp.json()
    except Exception as e:
        print(f"[WARN] _fetch_personas failed for user_id={user_id}: {e}")
    return []


def _describe_persona(p: dict) -> str:
    """
    Converts a raw persona dict (from the backend JSON) into a human-readable string.
    Numeric weights (0.0–1.0) are mapped to qualitative labels.
    """
    def label(value, low="low", mid="moderate", high="high"):
        if value is None:
            return "unknown"
        if value < 0.35:
            return low
        if value < 0.65:
            return mid
        return high

    name_line = f"• **{p.get('name', 'Unnamed Persona')}**"
    if p.get("isDefault"):
        name_line += " *(default)*"

    weights = {
        "Tempo":             p.get("tempo"),
        "Social preference": p.get("socialPreference"),
        "Nature preference": p.get("naturePreference"),
        "History preference":p.get("historyPreference"),
        "Food importance":   p.get("foodImportance"),
        "Alcohol preference":p.get("alcoholPreference"),
        "Transport style":   p.get("transportStyle"),
        "Budget level":      p.get("budgetLevel"),
        "Trip length":       p.get("tripLength"),
        "Crowd preference":  p.get("crowdPreference"),
    }

    detail_parts = [f"{k}: {label(v)}" for k, v in weights.items() if v is not None]
    lines = [name_line]
    if detail_parts:
        lines.append("  " + ", ".join(detail_parts))
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Agent: UserPersonaListAgent  (NEW)
# ---------------------------------------------------------------------------
class UserPersonaListAgent(BaseAgent):
    """
    Lists and narrates all travel personas saved by the current user.
    The user_id is injected at call-time by server.py from the Flask request context.
    """
    tool_template = {
        "name": "get_user_personas",
        "description": (
            "Retrieves and describes all travel personas saved by the current user. ",
            "No need to pass the user_id to the tool, since the tool gets the user_id inside the function"
            "Use this when the user asks what kind of traveller they are, wants to know "
            "their travel personality, or asks to see their saved travel profiles/personas."
        ),
        "parameters": {
            "type": "object",
            "properties": {},
            "required": []
        }
    }

    def __call__(self, user_id: str = None):
        print(f"[SYSTEM] UserPersonaListAgent: user_id={user_id}")

        if not user_id:
            return (
                "I couldn't identify your account to look up your personas. "
                "Please make sure you are logged in."
            )

        personas = _fetch_personas(user_id)

        if not personas:
            return (
                "You don't have any travel personas saved yet. "
                "You can create one in your profile settings to personalise your routes."
            )

        count = len(personas)
        header = f"You have {count} travel persona{'s' if count != 1 else ''} saved:\n\n"
        body = "\n\n".join(_describe_persona(p) for p in personas)
        return header + body


# ---------------------------------------------------------------------------
# Agent: Route_search_agent  (UPDATED)
# ---------------------------------------------------------------------------
class Route_search_agent(BaseAgent):
    tool_template = {
        "name": "search_route",
        "description": (
            "Calculates and returns an optimised travel route through a list of Points of Interest (POIs). "
            "When the user talks about their travel style or preferences, this agent automatically loads "
            "their saved persona weights from the backend to personalise the route. "
            "Use persona_id to select a specific saved persona, or omit it to use the user's default one."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "POI_data": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": "Ordered list of POI names to visit."
                },
                "persona_id": {
                    "type": "string",
                    "description": (
                        "Optional. ID of a specific saved travel persona to use for weighting this route. "
                        "If omitted, the user's default persona is used automatically."
                    )
                }
            },
            "required": ["POI_data"]
        }
    }

    def __call__(self, POI_data: list, persona_id: str = None, user_id: str = None):
        """
        Generates a personalised route using the user's saved persona weights.
        user_id is NOT in the tool schema — it is injected at runtime by server.py.
        """
        print(f"[SYSTEM] Route_search_agent: user_id={user_id} | persona_id={persona_id}")

        persona_summary = None

        if user_id:
            personas = _fetch_personas(user_id)
            if personas:
                selected = None
                if persona_id:
                    selected = next((p for p in personas if p.get("id") == persona_id), None)
                if selected is None:
                    selected = next((p for p in personas if p.get("isDefault")), None)
                if selected is None:
                    selected = personas[0]  # any available persona
                if selected:
                    persona_summary = _describe_persona(selected)
                    print(f"[SYSTEM] Route using persona: {selected.get('name')}")

        poi_list = ", ".join(POI_data)

        if persona_summary:
            return (
                f"Optimised route generated through: {poi_list}.\n\n"
                f"Route personalised based on your travel persona:\n{persona_summary}"
            )
        return (
            f"Optimised route generated through: {poi_list}. "
            "(No saved persona found — using balanced default weights.)"
        )


class POI_suggest_agent(BaseAgent):
    tool_template = {
        "name": "suggest_poi",
        "description": "Suggests Points of Interest based on user profile and current route.",
        "parameters": {
            "type": "object",
            "properties": {
                "user_data": {"type": "object"},
                "current_route_info": {"type": "string"}
            },
            "required": ["current_route_info"]
        }
    }

    def __call__(self, current_route_info, user_data=None):
        return "Suggested POIs for your route: Eiffel Tower, Louvre Museum, and Notre Dame."


class ItineraryModificationAgent(BaseAgent):
    tool_template = {
        "name": "modify_itinerary",
        "description": "Modifies an existing trip itinerary (Add/Remove/Edit POIs).",
        "parameters": {
            "type": "object",
            "properties": {
                "trip_id": {"type": "string"},
                "action_type": {"type": "string", "enum": ["add", "remove", "edit"]},
                "poi_object": {"type": "object", "description": "The POI details to be modified."}
            },
            "required": ["trip_id", "action_type", "poi_object"]
        }
    }

    def __call__(self, trip_id, action_type, poi_object):
        poi_name = poi_object.get('name', 'Unknown')
        return f"Successfully {action_type}ed {poi_name} in itinerary {trip_id}."


class ChatTitleAgent(BaseAgent):
    tool_template = {
        "name": "generate_chat_title",
        "description": "Generates a brief (max 60 chars) title for the chat session based on the first message.",
        "parameters": {
            "type": "object",
            "properties": {
                "first_message": {"type": "string", "description": "The user's first query."}
            },
            "required": ["first_message"]
        }
    }

    def __call__(self, first_message: str):
        return first_message[:50] + "..." if len(first_message) > 50 else first_message


class POIDataAgent(BaseAgent):
    tool_template = {
        "name": "get_poi_details",
        "description": "Fetches historical and cultural facts about a specific POI to provide context for recommendations.",
        "parameters": {
            "type": "object",
            "properties": {
                "poi_name": {"type": "string"}
            },
            "required": ["poi_name"]
        }
    }

    def __call__(self, poi_name: str):
        """Fetches historical/cultural summary from Wikipedia."""
        print(f"[SYSTEM] POIDataAgent: Fetching Wikipedia summary for '{poi_name}'")
        try:
            url = f"https://en.wikipedia.org/api/rest_v1/page/summary/{poi_name.replace(' ', '_')}"
            response = requests.get(url, timeout=5)
            if response.status_code == 200:
                return response.json().get('extract', f"Detailed information about {poi_name}.")

            url_tr = f"https://tr.wikipedia.org/api/rest_v1/page/summary/{poi_name.replace(' ', '_')}"
            response_tr = requests.get(url_tr, timeout=5)
            if response_tr.status_code == 200:
                return response_tr.json().get('extract', f"{poi_name} hakkında detaylı bilgi.")
        except Exception as e:
            print(f"[ERROR] POIDataAgent Wikipedia call failed: {str(e)}")

        # Hardcoded fallback
        db = {
            "Anitkabir": "A symbol of modern Turkey, matching your interest in 'history' and 'architecture'.",
            "Kocatepe Mosque": "The largest mosque in Ankara, fitting 'culture' and 'landmarks'."
        }
        return db.get(poi_name, "A popular landmark matching your travel profile.")
