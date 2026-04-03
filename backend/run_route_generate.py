import json
import sys
import urllib.error
import urllib.request


INPUT_PATH = r"C:\dev\Boolean-Rhapsody\backend\route_request_input.json"
ENDPOINT = "http://localhost:8080/api/routes/generate"
CATEGORY_ORDER = [
    "Hotels",
    "Historic Places",
    "Landmarks",
    "Parks",
    "Restaurants",
    "Cafes & Desserts",
    "Bars & Nightclubs",
]


def resolve_category(point):
    protection_reason = point.get("protectionReason", "")
    if protection_reason in {"start-anchor:hotel", "end-anchor:hotel"}:
        return "Hotels"

    point_types = point.get("types", [])
    for category in CATEGORY_ORDER:
        if category in point_types:
            return category

    return "Unknown"


def format_routes(routes):
    lines = []
    for route_index, route in enumerate(routes, start=1):
        lines.append(f"Route {route_index}")
        for point_index, point in enumerate(route.get("points", []), start=1):
            category = resolve_category(point)
            name = point.get("poiName", "Unknown Place")
            lines.append(f"{point_index}. {category} - {name}")
        lines.append("")
    return "\n".join(lines).rstrip()


with open(INPUT_PATH, "rb") as input_file:
    request_body = input_file.read()

request = urllib.request.Request(
    ENDPOINT,
    data=request_body,
    headers={"Content-Type": "application/json"},
    method="POST",
)

try:
    with urllib.request.urlopen(request) as response:
        response_body = response.read()
        parsed_response = json.loads(response_body.decode("utf-8"))
        formatted_response = format_routes(parsed_response)
        sys.stdout.buffer.write(formatted_response.encode("utf-8"))
        sys.stdout.buffer.write(b"\n")
except urllib.error.HTTPError as error:
    error_body = error.read()
    try:
        parsed_error = json.loads(error_body.decode("utf-8"))
        formatted_error = json.dumps(parsed_error, indent=2, ensure_ascii=False)
        sys.stdout.buffer.write(formatted_error.encode("utf-8"))
        sys.stdout.buffer.write(b"\n")
    except json.JSONDecodeError:
        sys.stdout.buffer.write(error_body)
        sys.stdout.buffer.write(b"\n")
except urllib.error.URLError as error:
    print(str(error))
