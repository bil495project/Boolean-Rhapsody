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
            "Retrieves and describes all travel personas saved by the current user. "
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


# ---------------------------------------------------------------------------
# Agent: RouteGenerationFormatAgent
# ---------------------------------------------------------------------------

class RouteGenerationFormatAgent(BaseAgent):
    """
    Input formatter for the Route Generation Algorithm.

    Gathers the user's travel preferences (from their saved persona),
    resolves every named location to a real placeId from the backend,
    and returns a strict JSON payload ready for the Route Generation Algorithm.
    """

    tool_template = {
        "name": "generate_route_format",
        "description": (
            "Collects the user's travel preferences and named locations, resolves each location "
            "to a real place ID from the local database, and returns a strict JSON payload "
            "formatted for the Route Generation Algorithm. Use this when the user wants to plan "
            "a route or trip and provides details such as start/end points, desired stops, "
            "meal preferences, or lodging needs.\n"
            "IMPORTANT: After receiving the result from this tool, you MUST narrate the generated itinerary "
            "back to the user in a rich, descriptive, and engaging way. Fully utilize the detailed location "
            "metadata (place types, ratings, review counts, price levels, and explicit addresses) provided "
            "in the tool's output to make the summary informative and appealing.\n\n"
            "CRITICAL RULES:\n"
            "1. named_locations: List EVERY specific place name the user mentions by full exact name "
            "   (e.g. 'Şimşek Aspava', 'Anıtkabir'). Never omit a named place from this list.\n"
            "2. start_location: If the user explicitly says they START or BEGIN at a named place or type, "
            "   you MUST set start_location to that exact name or type. For example, if user says "
            "   'I will start at Anıtkabir' set start_location to 'Anıtkabir'.\n"
            "3. poi_slots: For each named place (type=PLACE), use the exact full name as given by the user. "
            "   Do NOT shorten or genericise the name (e.g. use 'Şimşek Aspava', NOT just 'Aspava').\n"
            "4. If the user starts at a specific named place, include it as the FIRST poi_slot with type=PLACE "
            "   AND also set it as start_location.\n"
            "5. HOTEL restriction: NEVER include 'HOTEL' as a poi_slot (with type=TYPE or PLACE), as hotels "
            "   cannot be used as interior route points. To include a hotel in the itinerary, you MUST either "
            "   set 'stay_at_hotel' to true, or use 'start_location' / 'end_location' with 'HOTEL' if the user "
            "   explicitly starts or ends at a hotel."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "named_locations": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": (
                        "ALL specific place names mentioned by the user, using the user's EXACT wording "
                        "(e.g. ['Şimşek Aspava', 'Anıtkabir', 'Hotel Metropol']). "
                        "Include EVERY named place — including the start location if it is a named place. "
                        "Never shorten or paraphrase place names."
                    )
                },
                "start_location": {
                    "type": "string",
                    "description": (
                        "The exact name or type of the starting point, copied verbatim from the user's message. "
                        "MUST be set whenever the user explicitly mentions where they will start "
                        "(e.g. 'I'll start at Anıtkabir' → 'Anıtkabir')."
                    )
                },
                "end_location": {
                    "type": "string",
                    "description": "The name or type of the ending point."
                },
                "poi_slots": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "type": {
                                "type": "string",
                                "enum": ["PLACE", "TYPE"],
                                "description": "Whether this slot is a named place or a category type."
                            },
                            "name": {
                                "type": "string",
                                "description": (
                                    "EXACT full place name as stated by the user (required when type == 'PLACE'). "
                                    "e.g. 'Şimşek Aspava', NOT just 'Aspava'."
                                )
                            },
                            "poiType": {
                                "type": "string",
                                "enum": ["KAFE", "RESTAURANT", "PARK", "HISTORIC_PLACE", "LANDMARK", "BAR"],
                                "description": "POI category (required when type == 'TYPE'). DO NOT use 'HOTEL' here; set stay_at_hotel = true or use start/end_location."
                            },
                            "filters": {
                                "type": "object",
                                "description": "Optional quality filters.",
                                "properties": {
                                    "minRating": {"type": "number"},
                                    "minRatingCount": {"type": "integer"}
                                }
                            }
                        },
                        "required": ["type"]
                    },
                    "description": "Ordered list of desired stops."
                },
                "meal_preferences": {
                    "type": "object",
                    "description": "Meal needs extracted from the user's message.",
                    "properties": {
                        "needsBreakfast": {"type": "boolean"},
                        "needsLunch":     {"type": "boolean"},
                        "needsDinner":    {"type": "boolean"}
                    }
                },
                "stay_at_hotel": {
                    "type": "boolean",
                    "description": "Whether the user needs hotel accommodation."
                },
                "requested_visit_count": {
                    "type": "integer",
                    "description": "Number of POI stops the user wants. Defaults to 5."
                },
                "k": {
                    "type": "integer",
                    "description": "Number of route alternatives to generate. Defaults to 3."
                },
                "persona_id": {
                    "type": "string",
                    "description": "ID of a specific persona to use. If omitted, the default persona is used."
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
        requested_visit_count: int = 5,
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
            "requestedVisitCount": int(requested_visit_count),
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

        # ── 9. Format human-readable summary for the LLM ─────────────────────
        def _fmt_duration(total_sec: int) -> str:
            h, m = divmod(int(total_sec) // 60, 60)
            if h and m:
                return f"{h}h {m}min"
            if h:
                return f"{h}h"
            return f"{m}min"

        def _fmt_distance(metres: float) -> str:
            km = metres / 1000.0
            return f"{km:.1f} km"

        lines = [f"### Generated {len(routes)} Route Alternative{'s' if len(routes) != 1 else ''}\n"]

        for i, route in enumerate(routes, start=1):
            route_id     = route.get("routeId", "N/A")
            travel_mode  = route.get("travelMode", "N/A")
            duration_str = _fmt_duration(route.get("totalDurationSec", 0))
            distance_str = _fmt_distance(route.get("totalDistanceM", 0))
            feasible     = "Yes" if route.get("feasible", False) else "No"

            lines.append(f"#### Route {i} (ID: {route_id})")
            lines.append(f"**Travel Mode:** {travel_mode} | **Duration:** {duration_str} | **Distance:** {distance_str} | **Feasible:** {feasible}\n")

            lines.append("| Stop | POI Name | Visit Time | Type | Rating | Price Level | Address | Coordinates |")
            lines.append("|---|---|---|---|---|---|---|---|")

            points = route.get("points") or []
            for pt in points:
                # Basic point info
                stop_idx   = pt.get("index", 0)
                visit_min  = pt.get("plannedVisitMin", 0)
                is_anchor  = pt.get("fixedAnchor", False)
                anchor_tag = " *(anchor)*" if is_anchor else ""

                # Full location details
                poi_name   = str(pt.get("poiName") or "Unknown").replace("|", "-")
                types      = str(pt.get("types") or "N/A").replace("|", "-")
                address    = str(pt.get("formattedAddress") or "N/A").replace("|", "-")
                lat        = pt.get("latitude")
                lng        = pt.get("longitude")
                rating     = pt.get("ratingScore")
                r_count    = pt.get("ratingCount")
                price      = pt.get("priceLevel")

                coords = f"({lat:.4f}°N, {lng:.4f}°E)" if lat is not None and lng is not None else "N/A"

                if rating is not None and r_count is not None:
                    rating_str = f"{rating} ⭐ ({r_count:,} reviews)"
                elif rating is not None:
                    rating_str = f"{rating} ⭐"
                else:
                    rating_str = "N/A"

                price_str  = str(_PRICE_LEVEL_LABELS.get(price, price) if price else "N/A").replace("|", "-")

                lines.append(
                    f"| {stop_idx + 1} | **{poi_name}**{anchor_tag} | {visit_min} min | {types} | {rating_str} | {price_str} | {address} | {coords} |"
                )

            lines.append("")  # blank line between routes

        if warnings:
            lines.append("⚠️  Resolution warnings:")
            for w in warnings:
                lines.append(f"  • {w}")

        return "\n".join(lines)