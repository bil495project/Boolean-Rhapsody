import math
class calculatorAgent:

    tool_template = {
            "name": "calculator_agent",
            "description": "A tool that performs mathematical calculations. It accepts a mathematical expression as a string and returns the numerical result.",
            "input_schema": {
                "type": "object",
                "properties": {
                "expression": {
                    "type": "string",
                    "description": "The mathematical expression to evaluate (e.g., '2 + 2', 'sqrt(16) * 5', or '15% of 200')."
                }
                },
                "required": ["expression"]
            }
        }
    def __call__(self, expression: str):
        try:
            # Note: Using a safe eval environment
            allowed_names = {"math": math, "sqrt": math.sqrt, "pow": math.pow}
            return str(eval(expression, {"__builtins__": None}, allowed_names))
        except Exception as e:
            return f"Math Error: {str(e)}"


class weatherAgent:
    tool_template = {
        "name": "weather_agent",
        "description": "Retrieves current weather information, including temperature and conditions, for a specified location.",
        "input_schema": {
            "type": "object",
            "properties": {
            "location": {
                "type": "string",
                "description": "The city and state/country (e.g., 'San Francisco, CA' or 'Tokyo, Japan')."
            },
            "unit": {
                "type": "string",
                "enum": ["celsius", "fahrenheit"],
                "description": "The temperature unit to return. Defaults to celsius."
            }
            },
            "required": ["location"]
        }
        }
    def __call__(self, location: str, unit: str = "celsius"):
        # Placeholder for actual API call logic
        temp = 22 if unit == "celsius" else 72
        return f"The weather in {location} is {temp}° {unit} and sunny."

