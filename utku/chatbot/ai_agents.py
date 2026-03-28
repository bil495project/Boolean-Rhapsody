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


# ---------------------------------------------------------------------------
# Agent: POI_data_agent
# ---------------------------------------------------------------------------

# Price-level tokens returned by the backend → human-readable labels
_PRICE_LEVEL_LABELS = {
    "PRICE_LEVEL_FREE":        "Free",
    "PRICE_LEVEL_INEXPENSIVE": "Inexpensive (₺)",
    "PRICE_LEVEL_MODERATE":    "Moderate (₺₺)",
    "PRICE_LEVEL_EXPENSIVE":   "Expensive (₺₺₺)",
    "PRICE_LEVEL_VERY_EXPENSIVE": "Very expensive (₺₺₺₺)",
}


def _format_place(p: dict) -> str:
    """
    Converts a single PlaceResponse dict (from the backend JSON)
    into a clean, human-readable one-liner that lists every attribute.

    Example output:
        Place_name: Anıtkabir, Place_type: point_of_interest, historical,
        Address: Anıttepe, 06570 Ankara, Coordinates: (39.9255°N, 32.8378°E),
        Rating: 4.8 ⭐ (12,345 reviews), Price_level: Free, Status: Operational
    """
    name     = p.get("name", "Unknown")
    types    = p.get("types") or "N/A"
    address  = p.get("formattedAddress") or "N/A"
    lat      = p.get("latitude")
    lng      = p.get("longitude")
    rating   = p.get("ratingScore")
    r_count  = p.get("ratingCount")
    price    = p.get("priceLevel")
    status   = p.get("businessStatus")

    coords = f"({lat:.4f}°N, {lng:.4f}°E)" if lat is not None and lng is not None else "N/A"

    if rating is not None and r_count is not None:
        rating_str = f"{rating} ⭐ ({r_count:,} reviews)"
    elif rating is not None:
        rating_str = f"{rating} ⭐"
    else:
        rating_str = "N/A"

    price_str  = _PRICE_LEVEL_LABELS.get(price, price) if price else "N/A"
    status_str = status.replace("_", " ").capitalize() if status else "N/A"

    return (
        f"Place_name: {name}, "
        f"Place_type: {types}, "
        f"Address: {address}, "
        f"Coordinates: {coords}, "
        f"Rating: {rating_str}, "
        f"Price_level: {price_str}, "
        f"Status: {status_str}"
    )


class POI_data_agent(BaseAgent):
    """
    Fetches POI details from the local database via the Spring Boot backend.

    Behaviour
    ---------
    • The agent calls ``GET /api/places/search?name=<poi_name>`` which performs a
      case-insensitive *substring* search across all rows in the places table.
    • If one result is returned it is treated as a well-known, unique place
      (e.g. "Anıtkabir") and its details are shown directly.
    • If multiple results are returned (e.g. searching "Aspava") all candidates
      are listed so the user can see every matching location.
    • All database columns (name, types, address, coordinates, rating, price,
      status) are included in the output.

    The LLM should call this tool whenever the user asks about a specific place,
    a category of places, or wants to know the details of a POI.
    """

    tool_template = {
        "name": "get_poi_details",
        "description": (
            "Searches the local POI database by name and returns the full details "
            "(type, address, coordinates, rating, price level, status) of every matching place. "
            "Use this for BOTH well-known singular places (e.g. 'Anıtkabir', 'Kocatepe Camii') "
            "AND generic place names that may have multiple instances (e.g. 'Aspava', 'Starbucks'). "
            "Always prefer this tool over guessing when the user asks about a specific location."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "poi_name": {
                    "type": "string",
                    "description": (
                        "The name (or partial name) of the place to look up. "
                        "Examples: 'Anıtkabir', 'Aspava', 'cafe', 'museum'."
                    )
                }
            },
            "required": ["poi_name"]
        }
    }

    def __call__(self, poi_name: str) -> str:
        print(f"[SYSTEM] POI_data_agent: searching database for '{poi_name}'")

        try:
            # Use the existing /api/places/search endpoint (substring, case-insensitive).
            # The endpoint is permit-all so no auth token is needed for this
            # server-to-server call.
            url    = f"{BACKEND_URL}/api/places/search"
            params = {"name": poi_name, "size": 50}   # fetch up to 50 matches
            resp   = requests.get(url, params=params, timeout=5)

            if resp.status_code != 200:
                print(f"[WARN] POI_data_agent: backend returned HTTP {resp.status_code}")
                return (
                    f"I couldn't retrieve information for '{poi_name}' from the database "
                    f"(HTTP {resp.status_code}). Please try again later."
                )

            data    = resp.json()
            # Spring's Page<PlaceResponse> wraps results in a 'content' array
            results = data.get("content", data) if isinstance(data, dict) else data

            if not results:
                return (
                    f"No places matching '{poi_name}' were found in the database. "
                    "The place may not be listed yet or the name may be spelled differently."
                )

            if len(results) == 1:
                # Single match — treat as a definitive, well-known place
                place_line = _format_place(results[0])
                return f"Here are the details for **{results[0].get('name', poi_name)}**:\n\n{place_line}"

            # Multiple matches — list all of them
            header = (
                f"Found **{len(results)}** places matching '{poi_name}'. "
                "Here are all of them:\n"
            )
            lines = [f"{i + 1}. {_format_place(p)}" for i, p in enumerate(results)]
            return header + "\n".join(lines)

        except requests.exceptions.ConnectionError:
            print("[ERROR] POI_data_agent: cannot connect to backend")
            return (
                "I'm currently unable to reach the place database. "
                "Please ensure the backend server is running."
            )
        except Exception as e:
            print(f"[ERROR] POI_data_agent: {str(e)}")
            return f"An unexpected error occurred while searching for '{poi_name}': {str(e)}"


# ---------------------------------------------------------------------------
# Agent: POI_search_agent
# ---------------------------------------------------------------------------

# The 7 canonical categories recognised by the backend.
# Shown verbatim in the LLM tool description so the model always picks a valid one.
_VALID_CATEGORIES = {
    "BARS_AND_NIGHTCLUBS": "Bars & Nightclubs",
    "CAFES_AND_DESSERTS":  "Cafes & Desserts",
    "HISTORIC_PLACES":     "Historic Places",
    "HOTELS":              "Hotels",
    "LANDMARKS":           "Landmarks",
    "PARKS":               "Parks",
    "RESTAURANTS":         "Restaurants",
}


class POI_search_agent(BaseAgent):
    """
    Searches the local POI database by place category and returns the
    top-rated matches with all their attributes.

    The LLM is responsible for mapping the user's natural-language request
    to one of the 7 fixed category keys.  The agent then calls:

        GET /api/places/by-category?category=<CATEGORY>&size=<limit>

    Results are sorted by rating (highest first) and formatted using the
    same ``_format_place`` helper as ``POI_data_agent``.

    Category → use when the user asks about
    ─────────────────────────────────────────
    BARS_AND_NIGHTCLUBS  → bars, pubs, clubs, nightlife
    CAFES_AND_DESSERTS   → cafes, coffee shops, bakeries, desserts
    HISTORIC_PLACES      → museums, churches, mosques, historical sites
    HOTELS               → hotels, accommodations, lodging, places to stay
    LANDMARKS            → landmarks, famous buildings, stadiums, city halls
    PARKS                → parks, gardens, nature, outdoor spaces
    RESTAURANTS          → restaurants, food places, eateries, dining
    """

    tool_template = {
        "name": "search_poi_by_category",
        "description": (
            "Searches the local POI database for places that belong to a specific category "
            "and returns the top-rated matches with full details (name, type, address, "
            "coordinates, rating, price level, status). "
            "Use this when the user asks for a RECOMMENDATION or wants to EXPLORE a type of place "
            "(e.g. 'find me a cafe', 'suggest a restaurant', 'what museums are there?'). "
            "Map the user's intent to EXACTLY ONE of the 7 valid category values: "
            "BARS_AND_NIGHTCLUBS, CAFES_AND_DESSERTS, HISTORIC_PLACES, HOTELS, "
            "LANDMARKS, PARKS, RESTAURANTS."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "place_category": {
                    "type": "string",
                    "enum": list(_VALID_CATEGORIES.keys()),
                    "description": (
                        "The category of place to search for. Must be one of: "
                        "BARS_AND_NIGHTCLUBS, CAFES_AND_DESSERTS, HISTORIC_PLACES, "
                        "HOTELS, LANDMARKS, PARKS, RESTAURANTS."
                    )
                },
                "limit": {
                    "type": "integer",
                    "description": (
                        "Maximum number of results to return. Defaults to 10. "
                        "Use a higher value (up to 20) when the user asks for many options."
                    )
                }
            },
            "required": ["place_category"]
        }
    }

    def __call__(self, place_category: str, limit: int = 10) -> str:
        # Normalise input — accept both 'cafes_and_desserts' and 'CAFES_AND_DESSERTS'
        key = place_category.strip().upper()

        if key not in _VALID_CATEGORIES:
            valid = ", ".join(_VALID_CATEGORIES.keys())
            return (
                f"'{place_category}' is not a recognised category. "
                f"Please use one of: {valid}."
            )

        category_label = _VALID_CATEGORIES[key]
        effective_limit = max(1, min(limit, 50))
        print(f"[SYSTEM] POI_search_agent: searching '{key}' (limit={effective_limit})")

        try:
            url    = f"{BACKEND_URL}/api/places/by-category"
            params = {"category": key, "size": effective_limit}
            resp   = requests.get(url, params=params, timeout=5)

            if resp.status_code != 200:
                print(f"[WARN] POI_search_agent: backend returned HTTP {resp.status_code}")
                return (
                    f"I couldn't retrieve {category_label} places from the database "
                    f"(HTTP {resp.status_code}). Please try again later."
                )

            results = resp.json()  # backend returns a plain List<PlaceResponse>

            if not results:
                return (
                    f"No {category_label} places were found in the database. "
                    "The database may not have entries for this category yet."
                )

            count = len(results)
            header = f"Found **{count}** {category_label} place{'s' if count != 1 else ''}, sorted by rating:\n"
            lines  = [f"{i + 1}. {_format_place(p)}" for i, p in enumerate(results)]
            return header + "\n".join(lines)

        except requests.exceptions.ConnectionError:
            print("[ERROR] POI_search_agent: cannot connect to backend")
            return (
                "I'm currently unable to reach the place database. "
                "Please ensure the backend server is running."
            )
        except Exception as e:
            print(f"[ERROR] POI_search_agent: {str(e)}")
            return f"An unexpected error occurred while searching for {category_label}: {str(e)}"

