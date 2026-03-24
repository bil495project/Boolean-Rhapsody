import math
import json
import requests

class BaseAgent:
    """Base class providing shared utility methods for all AI agents."""
    def _format_response(self, status: str, message: str, data: dict = None):
        return json.dumps({"status": status, "message": message, "data": data})

class calculatorAgent(BaseAgent):
    """
    Agent responsible for evaluating mathematical expressions safely.
    """
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
            # Defined safe environment to prevent arbitrary code execution
            allowed_names = {
                "math": math, 
                "sqrt": math.sqrt, 
                "pow": math.pow, 
                "abs": abs, 
                "round": round
            }
            # Evaluate the expression without access to built-in dangerous functions
            result = eval(expression, {"__builtins__": None}, allowed_names)
            return f"The result of '{expression}' is {result}."
        except Exception as e:
            return f"Calculation Error: {str(e)}"

class weatherAgent(BaseAgent):
    """
    Agent responsible for fetching real-time weather data for a specific location.
    """
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
            # Placeholder for actual API integration (e.g., OpenWeatherMap)
            temp = 22 if unit == "celsius" else 72
            condition = "sunny"
            
            return f"The current weather in {location} is {temp}° {unit.capitalize()} and {condition}."
        except Exception as e:
            return f"Weather Service Error: {str(e)}"
        
class UserProfileAgent_SetInfo(BaseAgent):
    tool_template = {
        "name": "set_user_profile_info",
        "description": "Updates or sets user profile information and travel preferences.",
        "parameters": {
            "type": "object",
            "properties": {
                "user_id": {"type": "string"},
                "user_data_update_info": {"type": "object", "description": "Key-value pairs of profile updates."},
                "travel_id": {"type": "string"}
            },
            "required": ["user_id", "user_data_update_info"]
        }
    }
    def __call__(self, user_id, user_data_update_info, travel_id=None):
        # Logic: Update DB record for user_id
        return f"Successfully updated profile for User {user_id}. Changes: {user_data_update_info}"

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
    def __call__(self, recommendation_id, user_data):
        return f"Justification for {recommendation_id}: This was recommended based on your preference for high-rated historical sites."

class Route_search_agent(BaseAgent):
    tool_template = {
        "name": "search_route",
        "description": "Calculates the optimal route between multiple Points of Interest (POIs).",
        "parameters": {
            "type": "object",
            "properties": {
                "POI_data": {"type": "array", "items": {"type": "string"}},
                "user_data": {"type": "object"},
                "travelPersona": {"type": "string", "enum": ["adventurous", "relaxed", "budget", "luxury"]}
            },
            "required": ["POI_data", "travelPersona"]
        }
    }
    def __call__(self, POI_data, user_data=None, travelPersona="relaxed"):
        return f"Optimized {travelPersona} route generated through: {', '.join(POI_data)}."

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
        # In a real app, this might call a very small LLM or a regex
        title = first_message[:50] + "..." if len(first_message) > 50 else first_message
        return title

# --- NEW AGENT: POI Detail Fetcher (Supports TC-LLM-U-011) ---
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
        """
        Fetches historical/cultural summary from Wikipedia.
        """
        print(f"[SYSTEM] POIDataAgent: Fetching Wikipedia summary for '{poi_name}'")
        
        headers = {
            'User-Agent': 'POIDataAgent/1.0 (tobbbitirmeabdmu584@gmail.com)' 
        }
        
        encoded_name = poi_name.replace(' ', '_')
        
        try:
            # English Wikipedia
            url = f"https://en.wikipedia.org/api/rest_v1/page/summary/{encoded_name}"
            response = requests.get(url, headers=headers, timeout=5)
            
            if response.status_code == 200:
                print("response successful")
                return response.json().get('extract')
            
            # Fallback to Turkish Wikipedia
            url_tr = f"https://tr.wikipedia.org/api/rest_v1/page/summary/{encoded_name}"
            response_tr = requests.get(url_tr, headers=headers, timeout=5)
            
            if response_tr.status_code == 200:
                print("response tr successful")
                return response_tr.json().get('extract')
                
        except Exception as e:
            print(f"[ERROR] POIDataAgent Wikipedia call failed: {str(e)}")

        # Final Hardcoded Fallback
        db = {
            "Anitkabir": "A symbol of modern Turkey, matching your interest in 'history' and 'architecture'.",
            "Kocatepe Mosque": "The largest mosque in Ankara, fitting 'culture' and 'landmarks'."
        }
        return db.get(poi_name, f"A popular landmark matching your travel profile.")

# --- UPDATED EXISTING AGENTS (Aligned with Test Names) ---

class UserProfileAgent_SetInfo(BaseAgent):
    tool_template = {
        "name": "user_profile_agent", # Renamed to match TC-LLM-U-008
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
