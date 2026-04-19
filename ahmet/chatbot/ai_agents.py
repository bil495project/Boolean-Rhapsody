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


# ---------------------------------------------------------------------------
# Sentiment & place-to-persona mapping helpers used by TripFeedbackAgent
# ---------------------------------------------------------------------------

# Keyword sets for lightweight, dependency-free sentiment analysis.
# Words the user uses when they *liked* something.
_POSITIVE_SIGNALS = {
    "loved", "love", "great", "amazing", "fantastic", "wonderful", "excellent",
    "perfect", "beautiful", "awesome", "enjoyed", "enjoy", "liked", "like",
    "good", "nice", "impressive", "stunning", "brilliant", "splendid",
    "incredible", "superb", "best", "favourite", "favourite", "fun",
    "happy", "glad", "pleased", "satisfied", "recommend", "definitely",
    "must", "worth", "100", "5/5", "5 stars", "five stars",
    # Explicit star ratings (numeric) — e.g. "I give this trip 5 stars"
    "5", "four", "4",
    # Turkish
    "güzel", "harika", "mükemmel", "sevdim", "beğendim", "çok iyi", "süper",
}

# Words the user uses when they *didn't like* something.
_NEGATIVE_SIGNALS = {
    "hated", "hate", "terrible", "awful", "horrible", "bad", "boring", "dull",
    "disappointing", "disappointed", "waste", "wasted", "didn't like",
    "did not like", "not good", "not great", "not worth", "overcrowded",
    "overpriced", "expensive", "skip", "avoid", "never again", "worst",
    "mediocre", "poor", "unpleasant", "disliked", "wasn't", "wasn't good",
    "not impressive", "too crowded", "too loud", "too dark", "too long",
    # Explicit star ratings (numeric) — e.g. "I give this trip 1 star"
    "1", "2", "one", "two",
    # Turkish
    "kötü", "berbat", "sıkıcı", "sevmedim", "beğenmedim", "pahalı",
    "hayal kırıklığı", "yazık", "olmamış",
}

# Negation words that flip the sentiment of the word that follows.
_NEGATIONS = {
    "not", "no", "never", "didn't", "did not", "wasn't", "was not",
    "couldn't", "could not", "don't", "do not", "doesn't", "does not",
    "won't", "will not", "hardly", "barely", "değil",
}


def _analyse_sentiment(text: str) -> float:
    """
    Rule-based, dependency-free sentiment scorer.

    Returns a score in the range [-1.0, +1.0]:
        +1.0  → strongly positive ("I loved it!")
        0.0   → neutral / mixed
        -1.0  → strongly negative ("I hated it")

    Strategy
    --------
    1. Tokenise the text into lowercase words.
    2. Slide a context window of size 3: if a negation appears in the
       three words *before* a sentiment word, flip its polarity.
    3. Accumulate ±1 for every signal word found, then normalise by the
       total number of signal words so a single strong word gives 1.0 or
       -1.0 while mixed feedback averages out.
    """
    words = text.lower().split()
    score = 0
    hits  = 0

    for i, word in enumerate(words):
        # Strip common punctuation to improve matching
        clean = word.strip(".,!?;:'\"()")

        polarity = 0
        if clean in _POSITIVE_SIGNALS:
            polarity = +1
        elif clean in _NEGATIVE_SIGNALS:
            polarity = -1

        if polarity != 0:
            # Look back up to 3 words for a negation
            window = words[max(0, i - 3):i]
            if any(w.strip(".,!?;:'\"()") in _NEGATIONS for w in window):
                polarity *= -1  # flip: "didn't like" → negative

            score += polarity
            hits  += 1

    if hits == 0:
        return 0.0  # no sentiment words found → neutral

    return max(-1.0, min(1.0, score / hits))


# ---------------------------------------------------------------------------
# Maps place types (from the DB 'types' string) to the persona weight keys
# that should be adjusted when the user gives feedback on that place.
#
# Logic: if the user loved a Historic Place, we nudge historyPreference up;
#        if they hated a Park, we nudge naturePreference down.
# ---------------------------------------------------------------------------
_PLACE_TYPE_TO_PERSONA_KEYS: dict[str, list[str]] = {
    # Historic & cultural sites
    "museum":           ["historyPreference"],
    "historic":         ["historyPreference"],
    "historical":       ["historyPreference"],
    "church":           ["historyPreference", "socialPreference"],
    "mosque":           ["historyPreference", "socialPreference"],
    "monument":         ["historyPreference"],
    "archaeological":   ["historyPreference"],

    # Nature & outdoors
    "park":             ["naturePreference"],
    "garden":           ["naturePreference"],
    "lake":             ["naturePreference"],
    "forest":           ["naturePreference"],
    "trail":            ["naturePreference", "tempo"],
    "nature":           ["naturePreference"],

    # Food & drink
    "restaurant":       ["foodImportance", "budgetLevel"],
    "cafe":             ["foodImportance", "socialPreference"],
    "coffee":           ["foodImportance", "socialPreference"],
    "bakery":           ["foodImportance"],
    "food":             ["foodImportance"],
    "bar":              ["socialPreference", "alcoholPreference"],
    "pub":              ["socialPreference", "alcoholPreference"],
    "nightclub":        ["socialPreference", "alcoholPreference"],

    # Social & entertainment
    "landmark":         ["historyPreference", "socialPreference"],
    "stadium":          ["socialPreference"],
    "theater":          ["socialPreference", "historyPreference"],
    "shopping":         ["socialPreference", "budgetLevel"],
    "mall":             ["socialPreference", "budgetLevel"],

    # Accommodation
    "hotel":            ["budgetLevel"],
    "accommodation":    ["budgetLevel"],
}


def _place_types_to_persona_keys(place_types_str: str) -> list[str]:
    """
    Given a raw 'types' string from the backend (e.g.
    "point_of_interest, museum, tourist_attraction"), returns the
    deduplicated list of persona preference keys that are relevant.
    """
    key_set: set[str] = set()
    lowered = place_types_str.lower()
    for type_keyword, persona_keys in _PLACE_TYPE_TO_PERSONA_KEYS.items():
        if type_keyword in lowered:
            key_set.update(persona_keys)
    return list(key_set)


def _apply_feedback_delta(
    current_weight: float,
    sentiment_score: float,
    learning_rate: float = 0.08,
) -> float:
    """
    Nudges a single persona weight towards +1.0 or 0.0 depending on
    the sentiment score.

    Parameters
    ----------
    current_weight   : The current value of the persona preference (0.0–1.0).
    sentiment_score  : A value in [-1.0, +1.0] from _analyse_sentiment.
    learning_rate    : How large a step to take per piece of feedback.
                       Default 0.08 means a strong opinion shifts the weight
                       by at most ~8 percentage points per feedback message.

    Returns
    -------
    The new clamped weight value (still 0.0–1.0).
    """
    delta = sentiment_score * learning_rate
    new_weight = current_weight + delta
    return round(max(0.0, min(1.0, new_weight)), 4)


# ---------------------------------------------------------------------------
# TripFeedbackAgent
# ---------------------------------------------------------------------------

class TripFeedbackAgent(BaseAgent):
    """
    Records user feedback about a place or trip experience and automatically
    updates the active travel persona weights to reflect what the user did
    or didn't enjoy.

    How it works
    ------------
    1. **Sentiment Analysis** — The feedback comment is scored on a
       [-1.0, +1.0] scale using a keyword-based analyser (_analyse_sentiment).
       A score of +1 means "strongly liked"; -1 means "strongly disliked".

    2. **Place Lookup** — If a ``place_name`` is provided, the agent fetches
       the place record from the backend (GET /api/places/search) to read its
       ``types`` field (e.g. "museum, historic_place").

    3. **Persona Key Mapping** — The place's type keywords are mapped to the
       relevant persona weight keys (e.g. "museum" → historyPreference).

    4. **Weight Adjustment** — Each mapped persona key is nudged by
       ``sentiment_score × learning_rate`` (default: ±0.08 per feedback).
       Extreme values are clamped to [0.0, 1.0].

    5. **Persistence** — The updated persona is saved via ``_set_persona``
       which calls PUT /api/users/{user_id}/personas/{persona_id}.
    """

    # How much a single piece of feedback can shift a weight.
    # Increase (e.g. 0.15) for faster learning; decrease (e.g. 0.04) for
    # more conservative adjustments.
    LEARNING_RATE = 0.08

    tool_template = {
        "name": "submit_trip_feedback",
        "description": (
            "Saves the user's feedback about a completed trip or a specific visited place, "
            "and automatically adjusts their active travel persona weights based on what "
            "they liked or disliked. "
            "Use this whenever the user rates their experience, comments on a visited place, "
            "mentions what they enjoyed or didn't enjoy during a trip, or says things like "
            "'I loved the park', 'the museum was boring', 'great restaurant', etc. "
            "IMPORTANT: If the user mentions a specific place they liked/disliked, set "
            "'place_name' to that place's name so the feedback can be precisely attributed. "
            "Do NOT use this for changing general profile preferences without trip context — "
            "use 'update_user_profile' for that."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "user_feedback": {
                    "type": "string",
                    "description": (
                        "The user's feedback comment in free text. Should capture what they "
                        "liked or disliked about the place or trip. "
                        "Example: 'The museum was amazing, I could have stayed all day.' "
                        "or 'The park was a bit boring honestly.'"
                    )
                },
                "place_name": {
                    "type": "string",
                    "description": (
                        "Optional. The name of the specific place the user is commenting on "
                        "(e.g. 'Anıtkabir', 'Eymir Gölü'). When provided, the agent looks up "
                        "the place's category to determine which persona weights to adjust. "
                        "Omit if the feedback is about the whole trip rather than a single place."
                    )
                },
                "trip_id": {
                    "type": "string",
                    "description": (
                        "Optional. The unique identifier of the completed trip this feedback "
                        "belongs to. Useful for logging but not required for persona adjustment."
                    )
                }
            },
            "required": ["user_feedback"]
        }
    }

    # ------------------------------------------------------------------ #
    # Private: fetch a place record by name (same endpoint as other agents)
    # ------------------------------------------------------------------ #
    def _fetch_place(self, place_name: str) -> dict | None:
        """
        Searches the backend for a place by name and returns the best-matching
        record, or None if nothing is found.
        """
        try:
            url  = f"{BACKEND_URL}/api/places/search"
            resp = requests.get(url, params={"name": place_name, "size": 5}, timeout=5)
            if resp.status_code != 200:
                print(f"[WARN] TripFeedbackAgent: place search returned HTTP {resp.status_code}")
                return None

            data    = resp.json()
            results = data.get("content", data) if isinstance(data, dict) else data
            if not results:
                return None

            # Simple best-match: pick the result whose name most closely
            # matches what the user mentioned (case-insensitive prefix match
            # first, then fall back to the first result).
            lowered = place_name.lower()
            for r in results:
                if r.get("name", "").lower().startswith(lowered):
                    return r
            return results[0]

        except Exception as e:
            print(f"[WARN] TripFeedbackAgent: error fetching place '{place_name}': {e}")
            return None

    # ------------------------------------------------------------------ #
    # __call__
    # ------------------------------------------------------------------ #
    def __call__(
        self,
        user_feedback: str,
        place_name: str = None,
        trip_id: str = None,
        user_id: str = None,  # injected by server.py — NOT in tool_template
    ) -> str:
        print(
            f"[SYSTEM] TripFeedbackAgent: user_id={user_id}, "
            f"place='{place_name}', trip='{trip_id}'"
        )

        # ── Guard: must be logged in to update persona ───────────────────────
        if not user_id:
            return (
                "I've noted your feedback, but I couldn't identify your account so "
                "I wasn't able to update your travel preferences. Please make sure "
                "you are logged in."
            )

        # ── Step 1: Sentiment analysis ───────────────────────────────────────
        sentiment_score = _analyse_sentiment(user_feedback)
        sentiment_label = (
            "positive" if sentiment_score > 0.1
            else "negative" if sentiment_score < -0.1
            else "neutral"
        )
        print(
            f"[SYSTEM] TripFeedbackAgent: sentiment_score={sentiment_score:.3f} "
            f"({sentiment_label})"
        )

        # If the sentiment is neutral we still record it, but there's nothing
        # meaningful to adjust in the persona.
        if sentiment_label == "neutral":
            return (
                f"Thanks for your feedback{' about ' + place_name if place_name else ''}! "
                "Your comment sounded fairly neutral, so I didn't make any changes to "
                "your travel preferences this time. Feel free to give more specific "
                "feedback (e.g. 'I loved the park' or 'the museum was boring') and I'll "
                "fine-tune your profile accordingly."
            )

        # ── Step 2: Determine which persona keys to adjust ───────────────────
        place_record = None
        persona_keys_to_adjust: list[str] = []

        if place_name:
            place_record = self._fetch_place(place_name)

        if place_record:
            place_types_str = place_record.get("types", "")
            persona_keys_to_adjust = _place_types_to_persona_keys(place_types_str)
            print(
                f"[SYSTEM] TripFeedbackAgent: place types='{place_types_str}' → "
                f"persona keys={persona_keys_to_adjust}"
            )

        # Fallback: if no place was found (or no types were mappable), apply a
        # general adjustment across the most common preference dimensions so the
        # feedback is never silently dropped.
        if not persona_keys_to_adjust:
            persona_keys_to_adjust = [
                "historyPreference", "naturePreference",
                "foodImportance", "socialPreference",
            ]
            print(
                "[SYSTEM] TripFeedbackAgent: no specific place types "
                "→ falling back to general preference adjustment"
            )

        # ── Step 3: Fetch the user's personas ────────────────────────────────
        personas = _fetch_personas(user_id)
        if not personas:
            return (
                f"Thanks for your feedback! "
                "Unfortunately I couldn't find any travel personas linked to your "
                "account to update. You can create one in your profile settings."
            )

        # Select the active / default persona (same logic as other agents)
        active_persona = next(
            (p for p in personas if p.get("isDefault")), personas[0]
        )
        persona_id = active_persona.get("id")

        if not persona_id:
            return "I found your personas but couldn't read an ID for the active one — update skipped."

        print(
            f"[SYSTEM] TripFeedbackAgent: updating persona "
            f"'{active_persona.get('name')}' (id={persona_id})"
        )

        # ── Step 4: Compute and apply the weight deltas ──────────────────────
        updated_persona = active_persona.copy()
        changes: list[str] = []  # human-readable change log

        for key in persona_keys_to_adjust:
            old_value = active_persona.get(key)
            if old_value is None:
                # Key not present in this persona — skip silently
                continue

            new_value = _apply_feedback_delta(
                current_weight=float(old_value),
                sentiment_score=sentiment_score,
                learning_rate=self.LEARNING_RATE,
            )
            updated_persona[key] = new_value

            direction = "↑" if new_value > float(old_value) else "↓"
            changes.append(
                f"{key}: {float(old_value):.2f} {direction} {new_value:.2f}"
            )

        if not changes:
            return (
                f"Thanks for your {'positive' if sentiment_score > 0 else 'negative'} feedback! "
                "I wasn't able to map it to any preference weights in your active persona "
                "(the relevant weights may already be at their limits)."
            )

        # ── Step 5: Persist the updated persona ──────────────────────────────
        result = _set_persona(user_id, persona_id, updated_persona)

        if not result.get("success"):
            print(f"[ERROR] TripFeedbackAgent: _set_persona failed — {result.get('message')}")
            return (
                "I understood your feedback but ran into an issue saving the preference "
                f"updates: {result.get('message', 'Unknown error')}. Please try again later."
            )

        # ── Step 6: Build a clear, friendly confirmation message ─────────────
        place_part  = f" about **{place_record.get('name', place_name)}**" if place_record else ""
        trip_part   = f" (trip {trip_id})" if trip_id else ""
        sign        = "+" if sentiment_score > 0 else ""
        change_list = ", ".join(changes)

        return (
            f"Got it! Based on your {sentiment_label} feedback{place_part}{trip_part} "
            f"(sentiment score: {sign}{sentiment_score:.2f}), I've updated your "
            f"**{active_persona.get('name', 'active')}** persona:\n\n"
            f"{chr(10).join('  • ' + c for c in changes)}\n\n"
            "These small adjustments help me recommend places that better match what "
            "you actually enjoy. Keep sharing your thoughts after visits!"
        )
        
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
            params = {"name": poi_name, "size": 20}   # fetch up to 20 matches
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

class GeneratedRouteExplanationAgent(BaseAgent):
    """
    Explains an already-generated route to the user.

    Workflow
    --------
    1.  Receives an ordered list of place names that make up the route.
    2.  For each name, fetches the full database record from the backend
        using the same multi-strategy resolver as RouteGenerationFormatAgent
        (full-phrase search → token intersection → token union).
    3.  Returns a compact JSON string containing:
          • route_overview  — total stops, duration, distance, travel mode
          • stops           — list of dicts with only narration-relevant
                              fields (name, types, rating, price_level,
                              planned_visit_min, address)
    4.  The /explain_route endpoint renders the route overview as Markdown
        and feeds the stops JSON to a second LLM call that writes creative
        2–3 sentence verdicts per stop.  The LLM is NOT asked to reproduce
        the data verbatim.
    """

    tool_template = {
        "name": "explain_generated_route",
        "description": (
            "Fetches database records for every stop in a route and returns "
            "a compact JSON object with route overview metadata and per-stop "
            "fields (name, types, rating, price level, address, planned visit). "
            "ALWAYS USE THIS TOOL when the user has an already-generated route "
            "(a list of stops) and asks the assistant to explain, summarise, or "
            "describe that route."
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

    # ── compact stop builder (all display fields) ─────────────────────────────

    @staticmethod
    def _build_stop_dict(stop_number: int, place_name_query: str, record: dict | None) -> dict:
        """
        Returns a dict with all display-relevant fields for the stop.
        The server uses this to render a deterministic data block
        and to feed narration-relevant fields to the LLM.
        """
        if record is None:
            return {
                "stop": stop_number,
                "name": place_name_query,
                "error": "Could not retrieve database record for this stop.",
            }

        name    = record.get("name", place_name_query)
        types   = record.get("types") or "N/A"
        address = record.get("formattedAddress") or "N/A"

        lat = record.get("latitude")
        lng = record.get("longitude")
        coords = f"{lat:.4f}°N, {lng:.4f}°E" if lat is not None and lng is not None else "N/A"

        rating  = record.get("ratingScore")
        r_count = record.get("ratingCount")
        if rating is not None and r_count is not None:
            rating_str = f"{rating} ({r_count:,} reviews)"
        elif rating is not None:
            rating_str = str(rating)
        else:
            rating_str = "N/A"

        price     = record.get("priceLevel")
        price_str = _PRICE_LEVEL_LABELS.get(price, price) if price else "N/A"

        status  = record.get("businessStatus")
        status_str = status.replace("_", " ").capitalize() if status else "N/A"

        visit_min = record.get("plannedVisitMin")
        visit_str = f"{visit_min} min" if visit_min else "N/A"

        return {
            "stop":          stop_number,
            "name":          name,
            "types":         types,
            "address":       address,
            "coordinates":   coords,
            "rating":        rating_str,
            "price_level":   price_str,
            "status":        status_str,
            "planned_visit": visit_str,
        }

    # ── main entry point ─────────────────────────────────────────────────────

    def __call__(
        self,
        route_stop_names: list,
        route_summary: dict | None = None,
    ) -> str:
        print("[SYSTEM]:\t",route_stop_names, route_summary)
        print(f"[SYSTEM] GeneratedRouteExplanationAgent: explaining {len(route_stop_names)} stops")

        if not route_stop_names:
            return json.dumps({"error": "No route stops were provided."})

        # ── Route overview ────────────────────────────────────────────────────
        rs = route_summary or {}
        duration_min = rs.get("total_duration_min")
        distance_km  = rs.get("total_distance_km")
        travel_mode  = rs.get("travel_mode")

        route_overview = {"total_stops": len(route_stop_names)}
        if duration_min is not None:
            route_overview["total_duration_min"] = duration_min
        if distance_km is not None:
            route_overview["total_distance_km"] = distance_km
        if travel_mode:
            route_overview["travel_mode"] = travel_mode

        # ── Per-stop compact dicts ────────────────────────────────────────────
        stops = []
        for i, name in enumerate(route_stop_names, start=1):
            print(f"[SYSTEM] GeneratedRouteExplanationAgent: resolving stop {i}: '{name}'")
            record = self._resolve_place(name)
            stops.append(self._build_stop_dict(i, name, record))

        result = {"route_overview": route_overview, "stops": stops}
        print()
        print("[SYSTEM] GeneratedRouteExplanationAgent: returned JSON payload")
        return json.dumps(result, ensure_ascii=False)