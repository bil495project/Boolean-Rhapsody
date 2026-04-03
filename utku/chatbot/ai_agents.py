import math
import json
import requests

class BaseAgent:
    """Base class providing shared utility methods for all AI agents."""
    def _format_response(self, status: str, message: str, data: dict = None):
        return json.dumps({"status": status, "message": message, "data": data})


class CalculatorAgent(BaseAgent):
    """Evaluates mathematical expressions in a sandboxed environment."""
    tool_template = {
        "name": "calculator",
        "description": (
            "Evaluates a mathematical expression and returns the result. "
            "Supports basic arithmetic (+, -, *, /), sqrt(), pow(), abs(), and round(). "
            "Use this whenever the user asks to compute or calculate a numeric value. "
            "Do NOT use for date arithmetic or unit conversions."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "expression": {
                    "type": "string",
                    "description": (
                        "A valid mathematical expression to evaluate. "
                        "Examples: '12 * (3 + 4)', 'sqrt(144)', 'pow(2, 10) / 4'."
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


class WeatherAgent(BaseAgent):
    """Fetches current weather conditions for a given location."""
    tool_template = {
        "name": "get_weather",
        "description": (
            "Returns the current temperature and weather conditions for a specified city. "
            "Use this when the user asks about the weather, temperature, or whether to pack "
            "certain clothing for a destination."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "location": {
                    "type": "string",
                    "description": (
                        "The city and country to fetch weather for. "
                        "Format: 'City, Country'. Example: 'Istanbul, Turkey'."
                    )
                },
                "unit": {
                    "type": "string",
                    "enum": ["celsius", "fahrenheit"],
                    "description": "Temperature unit. Defaults to 'celsius'."
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


class UserProfileUpdateAgent(BaseAgent):
    """Updates persistent user preferences stored in the backend."""
    tool_template = {
        "name": "update_user_profile",
        "description": (
            "Updates one or more preference fields on the user's travel persona "
            "(e.g. preferred budget level, interest in history, food importance, pace/tempo, nature, social). "
            "CRITICAL INSTRUCTION: ALWAYS CALL THIS TOOL IMMEDIATELY whenever the user asks to change, update, or "
            "set a new preference (like 'set my tempo to low', 'update my profile', 'I want less history'). "
            "DO NOT ask for confirmation before calling! DO NOT just talk about updating it! YOU MUST CALL THE TOOL! "
            "Fields are double values from 0.0 to 1.0. Adjust the values based on "
            "the user's request. For example, if they say 'totally no history', set historyPreference to 0.0. "
            "If they say 'I want to chill around', set tempo to a low value (e.g. 0.2)."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "user_data_update_info": {
                    "type": "object",
                    "description": (
                        "A key-value map of the fields to update. Valid keys: "
                        "'historyPreference', 'naturePreference', 'foodImportance', "
                        "'budgetLevel', 'socialPreference', 'tempo'. "
                        "Values MUST be doubles between 0.0 and 1.0. "
                        "Example: {'historyPreference': 0.5, 'naturePreference': 1.0}."
                    )
                }
            },
            "required": ["user_data_update_info"]
        }
    }

    def __call__(self, user_data_update_info: dict, user_id: str = None) -> str:
        print(f"[SYSTEM] UserProfileUpdateAgent: user_id={user_id}, updates={user_data_update_info}")
        
        if not user_id:
            return "I couldn't identify your account. Please log in to update your profile preferences."
            
        personas = _fetch_personas(user_id)
        if not personas:
            return "You do not have any travel personas yet. Please create one before updating preferences."
            
        # Allowed keys to update
        valid_keys = {"historyPreference", "naturePreference", "foodImportance", "budgetLevel", "socialPreference", "tempo"}
        processed_updates = {}
        
        for k, v in user_data_update_info.items():
            if k in valid_keys and isinstance(v, (int, float)):
                processed_updates[k] = max(0.0, min(1.0, float(v)))
                
        if not processed_updates:
            return "No valid preference updates were provided. Valid keys are: historyPreference, naturePreference, foodImportance, budgetLevel, socialPreference, tempo."
            
        updated_count = 0
        for persona in personas:
            persona_id = persona.get("id")
            if not persona_id:
                continue
                
            # Copy existing fields and overwrite with new changes
            updated_persona = persona.copy()
            updated_persona.update(processed_updates)
            
            # The backend API expects the full payload for PUT /me/personas/{id}
            result = _set_persona(user_id, persona_id, updated_persona)
            if result.get("success"):
                updated_count += 1
                
        if updated_count > 0:
            return f"Successfully updated your preferences ({', '.join([f'{k}={v}' for k,v in processed_updates.items()])}) across {updated_count} persona(s)."
        else:
            return "Failed to update your personas in the database."


class TripFeedbackAgent(BaseAgent):
    """Records user feedback for a completed trip."""
    tool_template = {
        "name": "submit_trip_feedback",
        "description": (
            "Saves the user's feedback for a specific completed trip. "
            "Use this after a trip when the user rates their experience, mentions "
            "what they liked or disliked, or leaves a comment. "
            "Do NOT use for updating general profile preferences — use 'update_user_profile' for that."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "trip_id": {
                    "type": "string",
                    "description": "The unique identifier of the completed trip."
                },
                "user_feedback": {
                    "type": "string",
                    "description": (
                        "The user's feedback in free text. "
                        "Example: 'The route was great but the museum visit was too long.'"
                    )
                }
            },
            "required": ["trip_id", "user_feedback"]
        }
    }

    def __call__(self, trip_id, user_feedback):
        return f"Feedback for trip {trip_id} recorded: '{user_feedback[:50]}...'"


class RecommendationExplainerAgent(BaseAgent):
    """Explains why a specific place or route was recommended to the user."""
    tool_template = {
        "name": "explain_recommendation",
        "description": (
            "Returns an explainable AI (XAI) justification for why a specific place "
            "was recommended, based on the user's travel persona features and the place's features. "
            "Use this when the user asks 'Why was X recommended to me?' or 'Why did you recommend this place?'"
            "ALWAYS USE THIS AGENT IF USER ASKS WHY A ROUTE/PLACE WAS RECOMMENDED. EXAMPLE USES: 'why did you recommend me place X?', 'Why did you showed this?', 'Bana niye bunu önerdin?'"
            
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "place_name": {
                    "type": "string",
                    "description": "The exact name of the recommended place to explain (e.g. 'Anıtkabir', 'Eymir Gölü')."
                }
            },
            "required": ["place_name"]
        }
    }

    def __call__(self, place_name: str, user_id: str = None) -> str:
        import requests
        print(f"[SYSTEM] RecommendationExplainerAgent: explaining '{place_name}' for user_id={user_id}")
        
        if not user_id:
            return "I couldn't identify your account. Please log in to get a personalized explanation."

        # 1. Fetch place from DB
        try:
            url = f"{BACKEND_URL}/api/places/search"
            resp = requests.get(url, params={"name": place_name, "size": 1}, timeout=5)
            if resp.status_code != 200:
                return f"Could not retrieve place data for '{place_name}' (HTTP {resp.status_code})."
            
            data = resp.json()
            results = data.get("content", data) if isinstance(data, dict) else data
            if not results:
                return f"Could not find any place named '{place_name}' in the database."
                
            place = results[0]
            p_name = place.get("name", place_name)
            p_types = place.get("types", "Unknown")
            p_rating = place.get("ratingScore", "N/A")
            p_price = place.get("priceLevel", "N/A")
        except Exception as e:
            print(f"[ERROR] RecommendationExplainerAgent place lookup: {e}")
            return f"An error occurred while looking up '{place_name}'."
            
        # 2. Fetch user personas
        personas = _fetch_personas(user_id)
        if not personas:
            return f"Place '{p_name}' has types '{p_types}' and rating {p_rating}, but you don't have any saved travel personas, so I cannot explain the personalization."
            
        # Use default persona, or first if none is default
        persona = next((p for p in personas if p.get('isDefault')), personas[0])
        
        # 3. Construct explanation text for LLM
        explanation = [
            f"Here is the feature breakdown for explaining why '{p_name}' was recommended:",
            "",
            "**Place Features:**",
            f"- Types/Categories: {p_types}",
            f"- Rating: {p_rating} ⭐",
        ]
        if p_price and p_price != "N/A":
            explanation.append(f"- Price Level: {p_price}")
            
        explanation.append("")
        explanation.append("**User's Persona Preferences:**")
        
        # Helper to convert float to qualitative level
        def label(v):
            if v is None: return "Unknown"
            return "Low" if v < 0.35 else "Moderate" if v < 0.65 else "High"
            
        weights = {
            "History preference": persona.get("historyPreference"),
            "Nature preference": persona.get("naturePreference"),
            "Food importance": persona.get("foodImportance"),
            "Budget capacity": persona.get("budgetLevel"),
            "Social preference": persona.get("socialPreference"),
            "Pace / Tempo": persona.get("tempo")
        }
        for k, v in weights.items():
            if v is not None:
                explanation.append(f"- {k}: {label(v)} ({v})")
                
        explanation.append("")
        explanation.append("Instructions for the assistant:")
        explanation.append("Using the above place features and user's persona scores, first print the information on place and user exactly as you recieved. Then after printing these information, move on with explaining to the user in a friendly way why this place is a strong match for them. E.g., if the place is historical and they have a High History preference, point that out.")
        
        return "\n".join(explanation)


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

def _set_persona(user_id: str, persona_id: str, persona_data: dict) -> dict:
    """
    Spring Boot endpoint'ini çağırarak seyahat personasını günceller.
    Hata durumlarında detaylı açıklama döner.
    """
    # 1. Temel Doğrulama
    if not user_id or not persona_id:
        return {"success": False, "message": "Eksik parametre: user_id veya persona_id bulunamadı."}
        
    try:
        url = f"{BACKEND_URL}/api/users/{user_id}/personas/{persona_id}"
        
        # 2. Payload Hazırlama (Sadece None olmayanları al)
        fields = [
            "name", "isDefault", "tempo", "socialPreference", "naturePreference",
            "historyPreference", "foodImportance", "alcoholPreference",
            "transportStyle", "budgetLevel", "tripLength", "crowdPreference", "userVector"
        ]
        
        payload = {
            key: persona_data[key] 
            for key in fields 
            if key in persona_data and persona_data[key] is not None
        }
        
        if not payload:
            return {"success": True, "message": "Güncellenecek yeni bir veri sağlanmadı, işlem atlandı."}

        # 3. İstek Gönderimi
        resp = requests.put(url, json=payload, timeout=5)
        
        # 4. HTTP Durum Kodlarına Göre Açıklama
        if resp.status_code == 200:
            return {"success": True, "message": "Persona başarıyla güncellendi."}
        elif resp.status_code == 404:
            return {"success": False, "message": f"Hata 404: Kullanıcı (ID: {user_id}) veya Persona (ID: {persona_id}) sistemde bulunamadı."}
        elif resp.status_code == 400:
            return {"success": False, "message": f"Hata 400: Geçersiz veri formatı. Backend yanıtı: {resp.text}"}
        elif resp.status_code == 403:
            return {"success": False, "message": "Hata 403: Bu işlem için yetkiniz yok."}
        else:
            return {"success": False, "message": f"Beklenmedik HTTP hatası ({resp.status_code}): {resp.text}"}

    # 5. Network ve Beklenmedik Hatalar
    except requests.exceptions.Timeout:
        return {"success": False, "message": "Bağlantı zaman aşımına uğradı. Backend sunucusu yanıt vermiyor."}
    except requests.exceptions.ConnectionError:
        return {"success": False, "message": "Sunucuya bağlanılamadı. Lütfen BACKEND_URL'in doğruluğunu ve sunucunun çalıştığını kontrol edin."}
    except Exception as e:
        return {"success": False, "message": f"Beklenmedik bir sistem hatası oluştu: {str(e)}"}

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
        "name": "list_user_personas",
        "description": (
            "Retrieves and describes all travel personas saved by the current user. "
            "A persona contains weighted preferences such as tempo, budget level, "
            "nature/history interest, and social preference. "
            "Use this when the user asks: 'What kind of traveller am I?', "
            "'Show me my travel profiles', or 'What are my saved personas?'. "
            "The user_id is resolved automatically — do not ask the user for it."
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

class POISuggestionAgent(BaseAgent):
    """Suggests Points of Interest relevant to the user's current route and profile."""
    tool_template = {
        "name": "suggest_poi",
        "description": (
            "Suggests Points of Interest (POIs) that complement the user's current route "
            "and match their travel preferences. "
            "Use this when the user asks for ideas of what to add to an existing route, "
            "or says something like 'What else could I visit nearby?'. "
            "For searching POIs by category from scratch, use 'search_poi_by_category' instead."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "current_route_info": {
                    "type": "string",
                    "description": (
                        "A description or ID of the user's current route or itinerary context. "
                        "Example: 'Route starting at Anıtkabir, visiting 3 historical sites in Ankara.'"
                    )
                },
                "user_data": {
                    "type": "object",
                    "description": "Optional. The user's preference profile to personalise suggestions."
                }
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


class POIDataAgent(BaseAgent):
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

    """Fetches full details for one or more named places from the local database."""
    tool_template = {
        "name": "get_poi_details",
        "description": (
            "Looks up a place by name in the local POI database and returns its full details: "
            "type, address, coordinates, rating, price level, and operational status. "
            "Supports both unique landmarks (e.g. 'Anıtkabir') and names shared by multiple "
            "branches (e.g. 'Aspava'). When multiple matches are found, all are returned. "
            "Use this when the user asks about a SPECIFIC named place. "
            "For browsing by category (e.g. 'find me a café'), use 'search_poi_by_category' instead."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "poi_name": {
                    "type": "string",
                    "description": (
                        "The name or partial name of the place to look up. "
                        "Examples: 'Anıtkabir', 'Kocatepe Camii', 'Aspava'."
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


# ---------------------------------------------------------------------------
# Agent: GeneratedRouteExplanationAgent
# ---------------------------------------------------------------------------

class GeneratedRouteExplanationAgent(BaseAgent):
    """
    Explains an already-generated route to the user.

    Workflow
    --------
    1.  Receives an ordered list of place names that make up the route.
    2.  For each name, fetches the full database record from the backend
        using the same multi-strategy resolver as RouteGenerationFormatAgent
        (full-phrase search → token intersection → token union).
    3.  Builds a structured, deterministic data block that lists *every*
        database field for every stop.
    4.  Returns that block as a string so the LLM can:
          a) Print the raw data for each place (verbatim).
          b) Write a friendly narrative summary of the whole route.

    The LLM is instructed NOT to skip any field and NOT to replace the raw
    block with paraphrasing — it must include both the data block AND a
    human-friendly summary.
    """

    tool_template = {
        "name": "explain_generated_route",
        "description": (
            "Fetches the full database record for every stop in a route and returns "
            "a structured data block so the assistant can explain the route to the user. "
            "ALWAYS USE THIS TOOL when the user has an already-generated route (a list of stops) "
            "and asks the assistant to explain, summarise, or describe that route. "
            "The tool returns raw place data; the assistant MUST print that raw block verbatim "
            "AND follow it with a friendly narrative summary."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "route_stop_names": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": (
                        "Ordered list of place names exactly as they appear in the route "
                        "(e.g. ['Anıtkabir', 'Kocatepe Camii', 'Aspava']). "
                        "Include EVERY stop in route order. Do NOT shorten or paraphrase names."
                    ),
                },
                "route_summary": {
                    "type": "object",
                    "description": (
                        "High-level route metadata to include in the explanation. "
                        "All fields are optional but should be provided when available."
                    ),
                    "properties": {
                        "total_duration_min": {
                            "type": "number",
                            "description": "Estimated total trip duration in minutes.",
                        },
                        "total_distance_km": {
                            "type": "number",
                            "description": "Estimated total distance in kilometres.",
                        },
                        "travel_mode": {
                            "type": "string",
                            "description": "Primary travel mode (e.g. 'walking', 'driving').",
                        },
                    },
                },
            },
            "required": ["route_stop_names"],
        },
    }

    # ── private resolver (same algorithm as RouteGenerationFormatAgent) ──────

    def _resolve_place(self, place_name: str) -> dict | None:
        """
        Fetches the best-matching full place record from the backend for the
        given name.  Returns the raw dict from the API, or None on failure.
        """

        def _tr_normalize(s: str) -> str:
            return (
                s
                .replace('İ', 'i').replace('I', 'ı')
                .replace('Ş', 'ş').replace('Ğ', 'ğ')
                .replace('Ç', 'ç').replace('Ö', 'ö').replace('Ü', 'ü')
                .lower()
            )

        def _overlap(candidate: str, query: str) -> float:
            q_tokens = set(_tr_normalize(query).split())
            c_text   = _tr_normalize(candidate)
            if not q_tokens:
                return 0.0
            return sum(1 for t in q_tokens if t in c_text) / len(q_tokens)

        def _search(q: str, size: int = 15) -> list:
            try:
                resp = requests.get(
                    f"{BACKEND_URL}/api/places/search",
                    params={"name": q, "size": size},
                    timeout=5,
                )
                if resp.status_code != 200:
                    return []
                data = resp.json()
                return (data.get("content", data) if isinstance(data, dict) else data) or []
            except Exception:
                return []

        # Strategy 0 — full phrase, best overlap
        results = _search(place_name)
        if results:
            scored = [(c, _overlap(c.get("name", ""), place_name)) for c in results if c.get("id")]
            if scored:
                best, _ = max(scored, key=lambda x: x[1])
                return best

        # Strategy 1 & 2 — token-based fallback
        tokens = [t for t in place_name.split() if len(t) >= 3]
        if not tokens:
            return None

        token_maps = []
        for token in tokens:
            raw = _search(token, size=25)
            if raw:
                token_maps.append({c["id"]: c for c in raw if c.get("id")})

        if not token_maps:
            return None

        # Intersection
        intersected = set(token_maps[0].keys())
        for tm in token_maps[1:]:
            intersected &= set(tm.keys())

        if intersected:
            best_id = max(
                intersected,
                key=lambda pid: _overlap(token_maps[0].get(pid, {}).get("name", ""), place_name),
            )
            return token_maps[0][best_id]

        # Union + score
        all_candidates: dict = {}
        for tm in token_maps:
            all_candidates.update(tm)

        best_id = max(
            all_candidates,
            key=lambda pid: _overlap(all_candidates[pid].get("name", ""), place_name),
        )
        if _overlap(all_candidates[best_id].get("name", ""), place_name) > 0:
            return all_candidates[best_id]

        return None

    # ── deterministic formatter (every DB field, no omissions) ───────────────

    @staticmethod
    def _format_stop(stop_number: int, place_name_query: str, record: dict | None) -> str:
        """
        Formats one route stop as a readable, multi-line data block.
        All known database fields are printed so the LLM can reference them.
        """
        sep = "─" * 55

        if record is None:
            return (
                f"{sep}\n"
                f"Stop {stop_number}: {place_name_query}\n"
                f"  ⚠  Could not retrieve database record for this stop.\n"
                f"{sep}"
            )

        name    = record.get("name", place_name_query)
        types   = record.get("types") or "N/A"
        address = record.get("formattedAddress") or "N/A"
        lat     = record.get("latitude")
        lng     = record.get("longitude")
        coords  = f"{lat:.4f}°N, {lng:.4f}°E" if lat is not None and lng is not None else "N/A"

        rating  = record.get("ratingScore")
        r_count = record.get("ratingCount")
        if rating is not None and r_count is not None:
            rating_str = f"{rating} ⭐  ({r_count:,} reviews)"
        elif rating is not None:
            rating_str = f"{rating} ⭐"
        else:
            rating_str = "N/A"

        price   = record.get("priceLevel")
        price_str = _PRICE_LEVEL_LABELS.get(price, price) if price else "N/A"

        status  = record.get("businessStatus")
        status_str = status.replace("_", " ").capitalize() if status else "N/A"

        visit_min = record.get("plannedVisitMin")
        visit_str = f"{visit_min} min" if visit_min else "N/A"

        return (
            f"{sep}\n"
            f"Stop {stop_number}: {name}\n"
            f"  Type(s)       : {types}\n"
            f"  Address       : {address}\n"
            f"  Coordinates   : {coords}\n"
            f"  Rating        : {rating_str}\n"
            f"  Price level   : {price_str}\n"
            f"  Status        : {status_str}\n"
            f"  Planned visit : {visit_str}\n"
            f"{sep}"
        )

    # ── main entry point ─────────────────────────────────────────────────────

    def __call__(
        self,
        route_stop_names: list,
        route_summary: dict | None = None,
    ) -> str:
        print(f"[SYSTEM] GeneratedRouteExplanationAgent: explaining {len(route_stop_names)} stops")

        if not route_stop_names:
            return "No route stops were provided — I cannot explain an empty route."

        # ── Route-level header ────────────────────────────────────────────────
        rs = route_summary or {}
        duration_min = rs.get("total_duration_min")
        distance_km  = rs.get("total_distance_km")
        travel_mode  = rs.get("travel_mode", "N/A")

        header_lines = ["═" * 55, "ROUTE OVERVIEW", "═" * 55]
        header_lines.append(f"  Stops         : {len(route_stop_names)}")
        if duration_min is not None:
            hrs, mins = divmod(int(duration_min), 60)
            header_lines.append(f"  Est. duration : {f'{hrs}h {mins}m' if hrs else f'{mins} min'}")
        if distance_km is not None:
            header_lines.append(f"  Est. distance : {distance_km:.1f} km")
        header_lines.append(f"  Travel mode   : {travel_mode.capitalize()}")
        header_lines.append("═" * 55)

        # ── Per-stop data blocks ──────────────────────────────────────────────
        stop_blocks = []
        for i, name in enumerate(route_stop_names, start=1):
            print(f"[SYSTEM] GeneratedRouteExplanationAgent: resolving stop {i}: '{name}'")
            record = self._resolve_place(name)
            stop_blocks.append(self._format_stop(i, name, record))

        # ── Assemble full response ────────────────────────────────────────────
        sections = [
            "\n".join(header_lines),
            "\n\n".join(stop_blocks),
            (
                "─" * 55 + "\n"
                "INSTRUCTIONS FOR THE ASSISTANT:\n"
                "1. Print the ROUTE OVERVIEW and every STOP block above VERBATIM — "
                "do not paraphrase or omit any field.\n"
                "2. After the data blocks, write a friendly narrative summary: "
                "describe each stop in 2-3 sentences (what it is, why it is interesting, "
                "practical tips like price and opening hours if known).\n"
                "3. End with an overall verdict on the route (highlights, total time, "
                "any meal or budget considerations).\n"
                + "─" * 55
            ),
        ]

        return "\n\n".join(sections)