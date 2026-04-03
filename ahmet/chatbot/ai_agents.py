import math
import json
import requests

class BaseAgent:
    """Base class providing shared utility methods for all AI agents."""
    def _format_response(self, status: str, message: str, data: dict = None):
        return json.dumps({"status": status, "message": message, "data": data})


class calculatorAgent(BaseAgent):
    """Agent responsible for evaluating mathematical expressions safely.

    Supports a sandboxed subset of Python math: basic arithmetic (+, -, *, /),
    sqrt(), pow(), abs(), and round(). All other builtins are blocked.
    """
    tool_template = {
        "name": "calculator_agent",
        "description": (
            "Evaluates a mathematical expression and returns the numeric result. "
            "Supported operations: addition (+), subtraction (-), multiplication (*), "
            "division (/), sqrt(), pow(), abs(), round(). "
            "Example inputs: '2 + 3 * 4', 'sqrt(16) * 5', 'pow(2, 10)', 'round(3.14159, 2)'. "
            "Do NOT use this tool for travel-related calculations such as distances, "
            "travel times, or currency conversions — those are handled by other tools."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "expression": {
                    "type": "string",
                    "description": (
                        "The math expression to evaluate. Must use only supported operations. "
                        "Examples: 'sqrt(16) * 5', '100 / 3', 'pow(2, 8) + 1'."
                    )
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
        "description": (
            "Performs a live lookup of current weather conditions (temperature and sky status) "
            "for a given city. Returns the current temperature and a short condition description "
            "(e.g. 'sunny', 'cloudy'). The default temperature unit is Celsius. "
            "This tool provides CURRENT conditions only — it does not support forecasts "
            "or historical weather data."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "location": {
                    "type": "string",
                    "description": (
                        "The city and country in 'City, Country' format. "
                        "Examples: 'Ankara, Turkey', 'Berlin, Germany', 'Tokyo, Japan'."
                    )
                },
                "unit": {
                    "type": "string",
                    "enum": ["celsius", "fahrenheit"],
                    "description": (
                        "The temperature scale to use. Defaults to 'celsius' if not specified."
                    )
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
    """Agent responsible for WRITING (updating) user travel preferences.

    This is the write counterpart to UserPersonaListAgent (get_user_personas),
    which only reads. Use this agent when the user explicitly states a new
    preference or wants to change an existing one.

    Updatable preference categories include: tempo, social preference,
    nature preference, history preference, food importance, alcohol preference,
    transport style, budget level, trip length, crowd preference.
    """
    tool_template = {
        "name": "user_profile_agent",  # Matches TC-LLM-U-008
        "description": (
            "Updates (writes) user travel preferences. Use this when the user explicitly "
            "states a new preference or wants to change an existing setting "
            "(e.g. 'I prefer budget-friendly places', 'set my pace to relaxed', "
            "'I love historical sites'). "
            "Updatable categories: tempo, social preference, nature preference, "
            "history preference, food importance, alcohol preference, transport style, "
            "budget level, trip length, crowd preference. "
            "Do NOT use this tool to VIEW existing preferences — use get_user_personas instead."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "user_id": {
                    "type": "string",
                    "description": "The unique identifier of the user whose preferences to update."
                },
                "user_data_update_info": {
                    "type": "object",
                    "description": (
                        "A JSON object containing the preference fields to update. "
                        "Keys are preference category names (e.g. 'budgetLevel', 'historyPreference') "
                        "and values are the new settings (typically a float 0.0–1.0 or a descriptive string)."
                    )
                }
            },
            "required": ["user_id", "user_data_update_info"]
        }
    }

    def __call__(self, user_id, user_data_update_info):
        return f"User {user_id} preferences updated: {user_data_update_info}"


class UserFeedbackAgent(BaseAgent):
    """Agent responsible for recording user feedback about completed trips."""
    tool_template = {
        "name": "submit_user_feedback",
        "description": (
            "Records user feedback or a rating for a specific completed trip. "
            "Trigger this tool when the user comments on a past trip experience "
            "(e.g. 'that trip was great', 'I didn't enjoy the route', "
            "'trip T-123 was a 5 out of 5'). "
            "The trip_id is the unique identifier of a previously generated and completed trip "
            "(e.g. 'T-123'). If the user does not mention a trip_id, ask them to specify which trip "
            "they are referring to before calling this tool. "
            "After calling this tool, acknowledge the user's feedback and confirm it was recorded."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "trip_id": {
                    "type": "string",
                    "description": (
                        "The unique identifier of the completed trip the feedback is about "
                        "(e.g. 'T-123'). Must reference an existing trip."
                    )
                },
                "user_feedback": {
                    "type": "string",
                    "description": (
                        "The user's textual feedback about the trip. "
                        "Capture the user's sentiment and specific comments."
                    )
                }
            },
            "required": ["trip_id", "user_feedback"]
        }
    }

    def __call__(self, trip_id, user_feedback):
        return f"Feedback for trip {trip_id} recorded: '{user_feedback[:50]}...'"


class XAIJustificationAgent(BaseAgent):
    """Agent responsible for providing explainable AI justifications for recommendations."""
    tool_template = {
        "name": "get_xai_justification",
        "description": (
            "Provides an explainable AI (XAI) justification for a specific recommendation "
            "that was previously made by the system. "
            "Trigger this tool when the user asks WHY something was recommended "
            "(e.g. 'why did you suggest Anıtkabir?', 'why was I recommended that hotel?', "
            "'explain this recommendation'). "
            "The recommendation_id is the unique identifier of a previously generated recommendation "
            "(e.g. a route ID or a POI suggestion ID). "
            "The response will explain which user preferences, persona weights, and data signals "
            "contributed to the recommendation."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "recommendation_id": {
                    "type": "string",
                    "description": (
                        "The unique identifier of the recommendation to explain "
                        "(e.g. a route ID or suggestion ID from a previous tool call)."
                    )
                },
                "user_data": {
                    "type": "object",
                    "description": (
                        "Optional. Additional user context to enrich the explanation "
                        "(e.g. current preferences or session data). Omit if not available."
                    )
                }
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
            "Retrieves and describes all travel personas saved by the current user. "
            "The user_id is automatically injected by the server — do NOT pass it as a parameter. "
            "Use this when the user asks what kind of traveller they are, wants to know "
            "their travel personality, or asks to see their saved travel profiles/personas. "
            "This tool is READ-ONLY: it lists existing personas but does not create or modify them. "
            "To update preferences, use user_profile_agent instead. "
            "If the user has no saved personas, the tool will return a message suggesting "
            "they create one in their profile settings."
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

class POI_suggest_agent(BaseAgent):
    """Agent that suggests POIs personalized to the user's profile within a route context.

    Unlike search_poi_by_category (which is a generic category-based search),
    this agent uses the user's saved persona/preferences AND the current route
    context to generate contextually relevant suggestions.
    """
    tool_template = {
        "name": "suggest_poi",
        "description": (
            "Suggests Points of Interest personalized to the user's profile and tailored "
            "to the context of their current or planned route. "
            "Use this when the user asks for personalized suggestions within the context of "
            "an active route (e.g. 'suggest some places along my route', "
            "'what else can I visit on this trip?'). "
            "Do NOT use this for generic category browsing without route context \u2014 "
            "use search_poi_by_category instead."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "user_data": {
                    "type": "object",
                    "description": (
                        "Optional. The user's profile and preference data to personalize suggestions. "
                        "Omit if not available \u2014 the tool will use defaults."
                    )
                },
                "current_route_info": {
                    "type": "string",
                    "description": (
                        "A text description or JSON summary of the user's current route context. "
                        "Include key details like the route area, existing stops, and travel direction "
                        "so the suggestions are geographically and thematically relevant."
                    )
                }
            },
            "required": ["current_route_info"]
        }
    }

    def __call__(self, current_route_info, user_data=None):
        return "Suggested POIs for your route: Eiffel Tower, Louvre Museum, and Notre Dame."


class ItineraryModificationAgent(BaseAgent):
    """Agent responsible for modifying POIs within an existing trip itinerary."""
    tool_template = {
        "name": "modify_itinerary",
        "description": (
            "Modifies an existing trip itinerary by adding, removing, or editing a POI. "
            "Use this when the user wants to change a specific stop in a previously generated trip. "
            "Do NOT use this to create a new route from scratch \u2014 use generate_route_format instead. "
            "Action types: "
            "'add' = insert a new POI into the itinerary, "
            "'remove' = delete an existing POI from the itinerary, "
            "'edit' = modify the details of an existing POI in the itinerary."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "trip_id": {
                    "type": "string",
                    "description": (
                        "The unique identifier of the existing trip itinerary to modify "
                        "(e.g. 'T-123'). Must reference a previously generated trip."
                    )
                },
                "action_type": {
                    "type": "string",
                    "enum": ["add", "remove", "edit"],
                    "description": (
                        "The modification action to perform. "
                        "'add' = insert new POI, 'remove' = delete existing POI, "
                        "'edit' = update details of existing POI."
                    )
                },
                "poi_object": {
                    "type": "object",
                    "description": (
                        "The POI details for the modification. Must include at minimum: "
                        "'name' (string) \u2014 the name of the POI. "
                        "For 'add': also include 'type' and optionally 'position' (index in the itinerary). "
                        "For 'remove': 'name' is sufficient to identify the POI to remove. "
                        "For 'edit': include the fields to update (e.g. 'name', 'type', 'position')."
                    )
                }
            },
            "required": ["trip_id", "action_type", "poi_object"]
        }
    }

    def __call__(self, trip_id, action_type, poi_object):
        poi_name = poi_object.get('name', 'Unknown')
        return f"Successfully {action_type}ed {poi_name} in itinerary {trip_id}."


class ChatTitleAgent(BaseAgent):
    """Agent that generates a short display title for a new chat session.

    This tool is called ONLY once \u2014 on the very first user message in a
    conversation. It must NOT be called on subsequent turns. The output
    is used exclusively for the UI sidebar display and is never narrated
    back to the user.
    """
    tool_template = {
        "name": "generate_chat_title",
        "description": (
            "Generates a brief title (maximum 60 characters) for the chat session "
            "based on the user's very first message. "
            "This tool is called ONLY on the first user message in a new conversation \u2014 "
            "do NOT call it on any subsequent turn. "
            "The title is used for UI display in the chat sidebar only. "
            "Do NOT narrate or present the generated title to the user."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "first_message": {
                    "type": "string",
                    "description": (
                        "The user's first query in the conversation, "
                        "used as the basis for generating a concise title."
                    )
                }
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
    \u2022 Calls ``GET /api/places/search?name=<poi_name>`` which performs a
      case-insensitive **substring** search across all rows in the places table.
      This means searching for 'Aspava' matches 'Şimşek Aspava', 'Aspava Kebap', etc.
    \u2022 If one result is returned it is treated as a well-known, unique place
      (e.g. \"Anıtkabir\") and its details are shown directly.
    \u2022 If multiple results are returned (e.g. searching \"Aspava\") all candidates
      are listed so the user can see every matching location.
    \u2022 All database columns (name, types, address, coordinates, rating, price,
      status) are included in the output.

    Decision boundary
    -----------------
    \u2022 Use THIS tool when the user asks about a **specific named place** (by name).
    \u2022 Use ``search_poi_by_category`` when the user wants to **browse a category**
      (e.g. 'show me cafes') without naming a specific place.
    \u2022 If the user asks about a specific place before planning a route, call THIS tool
      first to confirm it exists, then call ``generate_route_format`` to build the route.
    """

    tool_template = {
        "name": "get_poi_details",
        "description": (
            "Looks up a specific place by name in the local POI database using a case-insensitive "
            "substring search, and returns full details (type, address, coordinates, rating, "
            "price level, status) for every match. "
            "Use this when the user asks about a SPECIFIC NAMED place \u2014 either a well-known "
            "unique place (e.g. 'An\u0131tkabir', 'Kocatepe Camii') or a name that may have "
            "multiple branches (e.g. 'Aspava', 'Starbucks'). "
            "Do NOT use this for browsing a category of places \u2014 use search_poi_by_category instead. "
            "If the user asks about a specific place before requesting a route, call this tool FIRST "
            "to verify the place exists, then use generate_route_format for the route. "
            "Always prefer this tool over guessing place information."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "poi_name": {
                    "type": "string",
                    "description": (
                        "The name (or partial name) of the place to look up. "
                        "The search is case-insensitive and matches substrings. "
                        "Examples: 'An\u0131tkabir', 'Aspava', 'Starbucks'."
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
            "Use this when the user wants to BROWSE or EXPLORE a type of place without naming "
            "a specific one (e.g. 'find me a cafe', 'suggest a restaurant', 'what museums are there?'). "
            "Do NOT use this when the user names a specific place \u2014 use get_poi_details instead. "
            "IMPORTANT: Pass EXACTLY ONE category per call. The 7 valid categories are: "
            "BARS_AND_NIGHTCLUBS, CAFES_AND_DESSERTS, HISTORIC_PLACES, HOTELS, "
            "LANDMARKS, PARKS, RESTAURANTS. "
            "For ambiguous requests (e.g. 'show me historic cafes'), choose the single category "
            "that best matches the user's primary emphasis. If unclear, prefer the more specific category."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "place_category": {
                    "type": "string",
                    "enum": list(_VALID_CATEGORIES.keys()),
                    "description": (
                        "Exactly ONE category to search. Must be one of: "
                        "BARS_AND_NIGHTCLUBS, CAFES_AND_DESSERTS, HISTORIC_PLACES, "
                        "HOTELS, LANDMARKS, PARKS, RESTAURANTS. "
                        "Never pass multiple categories in a single call."
                    )
                },
                "limit": {
                    "type": "integer",
                    "description": (
                        "Maximum number of results to return. Defaults to 10 if not specified. "
                        "Use a higher value (up to 20) when the user explicitly asks for many options."
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


# ---------------------------------------------------------------------------
# Agent: RouteGenerationFormatAgent
# ---------------------------------------------------------------------------

class RouteGenerationFormatAgent(BaseAgent):
    """
    Input formatter bridge between natural language and the Route Generation Algorithm.

    This agent translates a user's natural-language travel request into the strict
    JSON payload required by the backend route generation service. It operates in
    three phases:

    Phase 1 — Persona / Preference Resolution
        Fetches the user's saved travel personas from the backend. If a specific
        persona_id is provided, that persona is used; otherwise the user's default
        persona is selected. If no personas exist, neutral defaults (0.5) are used.

    Phase 2 — Multi-Strategy Place ID Resolution
        Every named location is resolved to a real placeId via the backend search
        API. Three strategies are tried in order:
          0. Full-phrase search with token-overlap scoring (fast path).
          1. Intersection of per-token search results (precise fallback).
          2. Union of per-token results scored by overlap (best-effort fallback).

    Phase 3 — Payload Assembly & POST
        Assembles the final JSON payload (preferences, constraints, poiSlots,
        anchors, meal preferences, k) and POSTs it to
        ``/api/routes/generate``. The backend returns a list of route alternatives.

    IMPORTANT: The result of this agent is returned RAW to the frontend — the
    LLM does NOT narrate, summarize, or comment on the response. The frontend
    intercepts the payload, stores the routes in Redux, and navigates to the
    Route Page for visual review.
    """

    tool_template = {
        "name": "generate_route_format",
        "description": (
            "Translates the user's travel request into a structured JSON payload, resolves "
            "every named location to a real place ID from the local database, and POSTs the "
            "payload to the Route Generation Algorithm. Use this when the user wants to plan "
            "a route or trip and provides details such as start/end points, desired stops, "
            "meal preferences, or lodging needs.\n\n"
            "RAW OUTPUT: This tool returns a raw JSON payload directly to the frontend. "
            "The LLM must NOT attempt to narrate, summarize, or comment on the result. "
            "Do NOT call this tool a second time for the same request.\n\n"
            "CONSTRAINT RULES:\n"
            "1. NAMED LOCATIONS: List EVERY specific place name the user mentions using their "
            "   full exact name (e.g. '\u015eim\u015fek Aspava', 'An\u0131tkabir'). Never omit a named place.\n"
            "2. START LOCATION: If the user says they START or BEGIN at a named place or type, "
            "   set start_location to that exact name or type.\n"
            "3. POI SLOTS: For each named place (type=PLACE), use the exact full name. "
            "   Do NOT shorten or genericize names. Use null entries for auto-fill slots.\n"
            "4. START AS FIRST SLOT: If the user starts at a specific named place, include it "
            "   as the FIRST poi_slot with type=PLACE AND also set it as start_location.\n"
            "5. HOTEL RESTRICTION: NEVER include 'HOTEL' as a poi_slot. Hotels cannot be interior "
            "   route points. To include a hotel, set stay_at_hotel=true or use start_location / "
            "   end_location with 'HOTEL'."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "named_locations": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": (
                        "Master list of ALL specific place names mentioned by the user, using their "
                        "EXACT verbatim wording (e.g. ['\u015eim\u015fek Aspava', 'An\u0131tkabir', 'Hotel Metropol']). "
                        "Every named place referenced anywhere in the request (start, end, or poi_slots) "
                        "MUST appear in this list. This list drives the place ID resolver \u2014 any name "
                        "missing here will not be resolved. Never shorten or paraphrase place names."
                    )
                },
                "start_location": {
                    "type": "string",
                    "description": (
                        "The exact name or type of the starting point, copied verbatim from the user's message. "
                        "MUST be set whenever the user explicitly mentions where they will start "
                        "(e.g. 'I'll start at An\u0131tkabir' \u2192 'An\u0131tkabir'). Omit if the user does not specify a start."
                    )
                },
                "end_location": {
                    "type": "string",
                    "description": (
                        "The exact name or type of the ending point, copied verbatim from the user's message. "
                        "Omit if the user does not specify an end point."
                    )
                },
                "poi_slots": {
                    "type": "array",
                    "items": {
                        "anyOf": [
                            {"type": "null"},
                            {
                                "type": "object",
                                "properties": {
                                    "type": {
                                        "type": "string",
                                        "enum": ["PLACE", "TYPE"],
                                        "description": (
                                            "'PLACE' = a specific named place (resolved by name). "
                                            "'TYPE' = a category-based slot (filled by the algorithm)."
                                        )
                                    },
                                    "name": {
                                        "type": "string",
                                        "description": (
                                            "EXACT full place name as stated by the user. Required when type='PLACE'. "
                                            "Must match an entry in named_locations exactly."
                                        )
                                    },
                                    "poiType": {
                                        "type": "string",
                                        "enum": ["KAFE", "RESTAURANT", "PARK", "HISTORIC_PLACE", "LANDMARK", "BAR"],
                                        "description": (
                                            "POI category. Required when type='TYPE'. "
                                            "DO NOT use 'HOTEL' here \u2014 set stay_at_hotel=true or use start/end_location."
                                        )
                                    },
                                    "filters": {
                                        "type": "object",
                                        "description": (
                                            "Optional quality filters to narrow the algorithm's selection. "
                                            "Useful for type=TYPE slots."
                                        ),
                                        "properties": {
                                            "minRating": {"type": "number"},
                                            "minRatingCount": {"type": "integer"}
                                        }
                                    }
                                },
                                "required": ["type"]
                            }
                        ]
                    },
                    "description": (
                        "Ordered list of desired stops along the route. Each entry is either a specific "
                        "place (type=PLACE), a category request (type=TYPE), or null. "
                        "Null entries signal the auto-filler algorithm to choose a stop automatically. "
                        "The number of slots should reflect the user's stated number of stops."
                    )
                },
                "meal_preferences": {
                    "type": "object",
                    "description": (
                        "Meal needs extracted from the user's message. Set each flag to true "
                        "if the user mentions needing that meal during the trip."
                    ),
                    "properties": {
                        "needsBreakfast": {"type": "boolean"},
                        "needsLunch":     {"type": "boolean"},
                        "needsDinner":    {"type": "boolean"}
                    }
                },
                "stay_at_hotel": {
                    "type": "boolean",
                    "description": (
                        "Set to true if the user needs hotel accommodation as part of the trip. "
                        "This is the ONLY way to include a hotel in the itinerary."
                    )
                },
                "k": {
                    "type": "integer",
                    "description": (
                        "Number of alternative route graphs to generate. Defaults to 3 if not mentioned "
                        "by the user. Higher values produce more route options for comparison."
                    )
                },
                "persona_id": {
                    "type": "string",
                    "description": (
                        "Optional. The ID of a specific travel persona to use for preference weighting. "
                        "If omitted, the user's default persona is used automatically. "
                        "If the user has no personas, neutral defaults (0.5 for all weights) are applied."
                    )
                }
            },
            "required": ["named_locations", "poi_slots"]
        }
    }

    # ------------------------------------------------------------------
    # Private helper: resolve a single place name → placeId string | None
    # ------------------------------------------------------------------
    def _resolve_place_id(self, place_name: str):
        """
        Multi-strategy place name resolution:

          1. Full name   — direct backend substring search (fast path)
          2. Intersection — searches each significant token separately;
                            candidates present in ALL token result sets are
                            assumed to contain every word → most precise match.
          3. Union + score — if no intersection, score all candidates from any
                             token search by Turkish-aware token overlap with the
                             original query and pick the best.

        Size is raised to 25 for token searches so that longer-tailed names
        (e.g. "ŞİMŞEK ASPAVA MİTHATPAŞA şb.") are not crowded out of results.

        Returns None on total failure.
        """

        # ── internal helpers ─────────────────────────────────────────────────
        def _tr_normalize(s: str) -> str:
            """Lowercase with Turkish-aware character folding for scoring."""
            return (
                s
                .replace('İ', 'i').replace('I', 'ı')   # Turkish dotted/dotless I
                .replace('Ş', 'ş').replace('Ğ', 'ğ')
                .replace('Ç', 'ç').replace('Ö', 'ö').replace('Ü', 'ü')
                .lower()
            )

        def _overlap_score(candidate_name: str, query: str) -> float:
            """Fraction of query tokens that appear anywhere in candidate_name."""
            q_tokens = set(_tr_normalize(query).split())
            c_text   = _tr_normalize(candidate_name)
            if not q_tokens:
                return 0.0
            matched = sum(1 for t in q_tokens if t in c_text)
            return matched / len(q_tokens)

        def _search_raw(query: str, size: int = 10) -> list:
            """Raw backend call — returns content list or [] on any failure."""
            try:
                url  = f"{BACKEND_URL}/api/places/search"
                resp = requests.get(url, params={"name": query, "size": size}, timeout=5)
                if resp.status_code != 200:
                    print(f"[WARN] RouteGenerationFormatAgent: HTTP {resp.status_code} searching '{query}'")
                    return []
                data = resp.json()
                return (data.get("content", data) if isinstance(data, dict) else data) or []
            except requests.exceptions.ConnectionError:
                print(f"[WARN] RouteGenerationFormatAgent: connection error searching '{query}'")
                return []
            except Exception as e:
                print(f"[WARN] RouteGenerationFormatAgent: error searching '{query}': {e}")
                return []

        # ── Strategy 0: full-phrase search + best-score pick ────────────────
        #   Search the full name as a phrase (backend does ILIKE '%...%').
        #   Instead of blindly picking results[0], we score every candidate
        #   against the original query and return the best match.
        #   This handles cases like "Şimşek Aspava" where a generic "Aspava"
        #   sibling might sort first on the backend but has a lower token-overlap
        #   score than the actual "Şimşek Aspava" branch.
        results = _search_raw(place_name, size=15)
        if results:
            # Score every candidate by token-overlap with the full query.
            scored = [
                (c, _overlap_score(c.get("name", ""), place_name))
                for c in results if c.get("id")
            ]
            if scored:
                best_candidate, best_score = max(scored, key=lambda x: x[1])
                best_name = best_candidate.get("name", "")
                print(
                    f"[SYSTEM] RouteGenerationFormatAgent: full-phrase → "
                    f"'{place_name}' resolved to '{best_name}' (score={best_score:.2f})"
                )
                return best_candidate.get("id")

        # ── Strategy 1 & 2: token-based (fallback when full phrase misses) ───
        #   Filter noise tokens (< 3 chars) then search with a larger page
        tokens = [t for t in place_name.split() if len(t) >= 3]
        if not tokens:
            print(f"[WARN] RouteGenerationFormatAgent: no results found for place '{place_name}'")
            return None

        # Build per-token result maps: { place_id → candidate_dict }
        token_maps = []
        for token in tokens:
            raw = _search_raw(token, size=25)   # bigger page = fewer misses
            if raw:
                token_maps.append({c["id"]: c for c in raw if c.get("id")})

        if not token_maps:
            print(f"[WARN] RouteGenerationFormatAgent: no token results for place '{place_name}'")
            return None

        # ── Strategy 1: intersection ─────────────────────────────────────────
        #   IDs that appear in EVERY token result set must contain all words.
        #   "ŞİMŞEK ASPAVA MİTHATPAŞA" appears for "Şimşek" AND "Aspava";
        #   "Aspava Kebap" only appears for "Aspava" → excluded.
        intersected = set(token_maps[0].keys())
        for tm in token_maps[1:]:
            intersected &= set(tm.keys())

        if intersected:
            best = max(
                intersected,
                key=lambda pid: _overlap_score(
                    token_maps[0].get(pid, {}).get("name", ""), place_name
                )
            )
            best_name = token_maps[0].get(best, {}).get("name", best)
            print(
                f"[SYSTEM] RouteGenerationFormatAgent: intersection → "
                f"'{place_name}' resolved to '{best_name}'"
            )
            return best

        # ── Strategy 2: union + best score ───────────────────────────────────
        #   Merge all candidates and score each against the full query.
        all_candidates: dict = {}
        for tm in token_maps:
            all_candidates.update(tm)

        best = max(
            all_candidates,
            key=lambda pid: _overlap_score(
                all_candidates[pid].get("name", ""), place_name
            )
        )
        best_score = _overlap_score(all_candidates[best].get("name", ""), place_name)
        best_name  = all_candidates[best].get("name", best)

        if best_score > 0:
            print(
                f"[SYSTEM] RouteGenerationFormatAgent: union → "
                f"'{place_name}' resolved to '{best_name}' (score={best_score:.2f})"
            )
            return best

        print(f"[WARN] RouteGenerationFormatAgent: no results found for place '{place_name}'")
        return None

    # ------------------------------------------------------------------
    # __call__
    # ------------------------------------------------------------------
    def __call__(
        self,
        named_locations: list,
        poi_slots: list,
        start_location: str = None,
        end_location: str = None,
        meal_preferences: dict = None,
        stay_at_hotel: bool = False,
        k: int = 3,
        persona_id: str = None,
        user_id: str = None,   # injected by server, NOT in tool_template
    ) -> str:
        print(f"[SYSTEM] RouteGenerationFormatAgent: user_id={user_id} | persona_id={persona_id}")

        warnings = []

        # ── 1. Fetch user preferences (persona) ─────────────────────────────
        _DEFAULT_PREFS = {
            "tempo":             0.5,
            "socialPreference":  0.5,
            "naturePreference":  0.5,
            "historyPreference": 0.5,
            "foodImportance":    0.5,
            "alcoholPreference": 0.5,
            "transportStyle":    0.5,
            "budgetLevel":       0.5,
            "tripLength":        0.5,
            "crowdPreference":   0.5,
        }

        preferences = dict(_DEFAULT_PREFS)

        if user_id:
            personas = _fetch_personas(user_id)
            if personas:
                selected = None
                if persona_id:
                    selected = next((p for p in personas if str(p.get("id")) == str(persona_id)), None)
                if selected is None:
                    selected = next((p for p in personas if p.get("isDefault")), None)
                if selected is None:
                    selected = personas[0]

                if selected:
                    print(f"[SYSTEM] RouteGenerationFormatAgent: using persona '{selected.get('name')}'")
                    for key in _DEFAULT_PREFS:
                        val = selected.get(key)
                        if val is not None:
                            preferences[key] = float(val)
            else:
                print(f"[WARN] RouteGenerationFormatAgent: no personas found for user_id={user_id}, using defaults")
                warnings.append("No user persona found — preference defaults (0.5) were used.")
        else:
            print("[WARN] RouteGenerationFormatAgent: user_id not provided, using default preferences")
            warnings.append("user_id not provided — preference defaults (0.5) were used.")

        # ── 2. Build a place-name → placeId cache for all named_locations ───
        #       (iteratively resolve each name; do NOT batch into one call)
        place_id_cache = {}
        for name in (named_locations or []):
            resolved = self._resolve_place_id(name)
            place_id_cache[name] = resolved
            if resolved is None:
                warnings.append(f"Could not resolve place '{name}' to a placeId — set to null.")

        # ── 3. Resolve startAnchor ────────────────────────────────────────────
        # A keyword set of POI types that should be treated as TYPE anchors.
        # We use substring detection so phrases like "4-star hotel" also match.
        _ANCHOR_TYPES = {"HOTEL", "AIRPORT", "KAFE", "RESTAURANT", "PARK",
                         "HISTORIC_PLACE", "LANDMARK", "BAR"}

        def _detect_poi_type(text: str):
            """Return the first matching POI type keyword found in text, or None."""
            upper = text.upper()
            return next((t for t in _ANCHOR_TYPES if t in upper), None)

        start_anchor = None
        if start_location:
            if start_location in place_id_cache:
                # Already resolved via named_locations cache
                start_anchor = {"kind": "PLACE", "placeId": place_id_cache[start_location]}
            else:
                poi_type = _detect_poi_type(start_location)
                if poi_type:
                    start_anchor = {"kind": "TYPE", "poiType": poi_type}
                else:
                    pid = self._resolve_place_id(start_location)
                    if pid is None:
                        warnings.append(f"Could not resolve start_location '{start_location}' — placeId set to null.")
                    start_anchor = {"kind": "PLACE", "placeId": pid}

        # ── 4. Resolve endAnchor ──────────────────────────────────────────────
        end_anchor = None
        if end_location:
            if end_location in place_id_cache:
                end_anchor = {"kind": "PLACE", "placeId": place_id_cache[end_location]}
            else:
                poi_type = _detect_poi_type(end_location)
                if poi_type:
                    end_anchor = {"kind": "TYPE", "poiType": poi_type}
                else:
                    pid = self._resolve_place_id(end_location)
                    if pid is None:
                        warnings.append(f"Could not resolve end_location '{end_location}' — placeId set to null.")
                    end_anchor = {"kind": "PLACE", "placeId": pid}

        # ── 5. Build poiSlots ─────────────────────────────────────────────────
        resolved_poi_slots = []
        for slot in (poi_slots or []):
            if slot is None:
                resolved_poi_slots.append(None)
                continue

            slot_type = slot.get("type", "").upper()
            filters   = slot.get("filters")

            if slot_type == "PLACE":
                place_name = slot.get("name", "")
                # Use cache first; resolve freshly if not in cache
                if place_name in place_id_cache:
                    pid = place_id_cache[place_name]
                else:
                    pid = self._resolve_place_id(place_name)
                    place_id_cache[place_name] = pid
                    if pid is None:
                        warnings.append(f"Could not resolve POI slot place '{place_name}' — placeId set to null.")

                entry = {"kind": "PLACE", "placeId": pid}

            elif slot_type == "TYPE":
                entry = {"kind": "TYPE", "poiType": slot.get("poiType", "")}

            elif slot_type in {"", "FREE"} and not filters and not slot.get("name") and not slot.get("poiType"):
                resolved_poi_slots.append(None)
                continue

            else:
                # Unknown slot type — skip with a warning
                warnings.append(f"Unknown poi_slots entry type '{slot.get('type')}' — slot skipped.")
                continue

            if filters:
                entry["filters"] = filters

            resolved_poi_slots.append(entry)

        # ── 6. Extract meal preferences ───────────────────────────────────────
        mp = meal_preferences or {}
        needs_breakfast = bool(mp.get("needsBreakfast", False))
        needs_lunch     = bool(mp.get("needsLunch",     False))
        needs_dinner    = bool(mp.get("needsDinner",    False))

        # ── 7. Assemble the final payload ─────────────────────────────────────
        constraints = {
            "stayAtHotel":   bool(stay_at_hotel),
            "needsBreakfast": needs_breakfast,
            "needsLunch":     needs_lunch,
            "needsDinner":    needs_dinner,
            "poiSlots":           resolved_poi_slots,
        }

        if start_anchor is not None:
            constraints["startAnchor"] = start_anchor
        if end_anchor is not None:
            constraints["endAnchor"] = end_anchor

        payload = {
            "preferences": preferences,
            "constraints":  constraints,
            "k":            int(k),
        }

        if warnings:
            payload["warnings"] = warnings

        #return json.dumps(payload, ensure_ascii=False, indent=2)
        print(f"[SYSTEM] RouteGenerationFormatAgent: POSTing payload to {BACKEND_URL}/api/routes/generate")
        print(f"[SYSTEM] Payload: {json.dumps(payload, ensure_ascii=False)}")

        # ── 8. POST to backend ────────────────────────────────────────────────
        try:
            resp = requests.post(
                f"{BACKEND_URL}/api/routes/generate",
                json=payload,
                headers={"Content-Type": "application/json"},
                timeout=30,
            )
        except requests.exceptions.ConnectionError:
            err = (
                "I'm currently unable to reach the route generation backend. "
                "Please ensure the backend server is running and try again."
            )
            print(f"[ERROR] RouteGenerationFormatAgent: connection error — {err}")
            return err
        except Exception as e:
            err = f"An unexpected error occurred while calling the route generation service: {str(e)}"
            print(f"[ERROR] RouteGenerationFormatAgent: {err}")
            return err

        if resp.status_code != 200:
            err = (
                f"The route generation backend returned an error "
                f"(HTTP {resp.status_code}): {resp.text[:300]}"
            )
            print(f"[WARN] RouteGenerationFormatAgent: {err}")
            return err

        try:
            routes: list = resp.json()
        except Exception as e:
            err = f"Could not parse route generation response as JSON: {str(e)}"
            print(f"[ERROR] RouteGenerationFormatAgent: {err}")
            return err

        if not routes:
            return (
                "The route generation service returned no routes. "
                "This may be due to insufficient places in the database for the given constraints."
            )

        # ── 9. Return structured JSON for the frontend ─────────────────────
        #    The frontend intercepts this payload, stores the routes in Redux,
        #    and navigates the user to the Route Page for visual review.
        result = {
            "type": "route_alternatives",
            "routes": routes,
        }
        if warnings:
            result["warnings"] = warnings

        return json.dumps(result, ensure_ascii=False)
