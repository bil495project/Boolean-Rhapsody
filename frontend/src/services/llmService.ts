/**
 * LLM Service Layer
 *
 * Client-side adapter that communicates with the Spring Boot backend,
 * which in turn proxies requests to Utku's Flask LLM Server.
 *
 * Architecture:
 *   Frontend (this service) → Spring Boot (:8080) /api/llm/** → Flask LLM Server (:5000) /chatbot
 *
 * This service is responsible for:
 *   1) Formatting user messages into the request structure for the backend
 *   2) Sending requests over HTTP to the Spring Boot backend
 *   3) Parsing backend responses back into client-side data models
 *   4) Transforming tool-call results into structured UI-friendly data
 *   5) Generating concise trip titles from user queries
 */

import type { MapDestination } from '../data/destinations';
import { placeService } from './placeService';

// ─── Configuration ────────────────────────────────────────────────────────────

/**
 * Base URL for the Spring Boot backend API.
 * The backend proxies LLM requests to the Flask server internally.
 */
const API_BASE_URL: string =
    import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

// ─── Types ────────────────────────────────────────────────────────────────────

/** Message format used in chat history. */
export interface ChatMessage {
    role: 'user' | 'assistant';
    content: string;
}

/**
 * Structured result returned to the UI after parsing the backend response.
 * - 'text': plain conversational response
 * - 'destination_recommendation': tool call returned POI search results
 * - 'destination_saved': tool call saved a destination
 * - 'route_generated': tool call generated a route
 * - 'itinerary_modified': tool call modified an itinerary
 * - 'weather_info': tool call fetched weather data
 * - 'tool_result': generic tool result that doesn't fit specific categories
 */
export interface ToolCallResult {
    type:
        | 'text'
        | 'destination_recommendation'
        | 'destination_saved'
        | 'route_generated'
        | 'route_approval_required'
        | 'itinerary_modified'
        | 'weather_info'
        | 'profile_updated'
        | 'tool_result';
    destinations?: MapDestination[];
    savedDestination?: MapDestination;
    /** Raw route data array for the route approval flow. */
    routeData?: any[];
    message: string;
    toolUsed?: string;
    toolParams?: any;
}

/**
 * Response shape from the Spring Boot backend's /api/llm/chat endpoint.
 * This mirrors the Flask LLM Server response, proxied through Spring Boot.
 *
 * Success with tool: { status: "success", toolUsed: "<name>", response: "<text>" }
 * Success no tool:   { status: "success", response: "<text>" }
 * Error:             { status: "error", message: "<error_text>" }
 */
interface LLMBackendResponse {
    status: 'success' | 'error';
    response?: string;
    toolUsed?: string;  // camelCase from Spring Boot's Jackson serialization
    toolParams?: any;
    /** Structured route data when toolUsed === 'generate_route_format'. */
    routeData?: any;
    message?: string;
}

/**
 * LocationCard compatible with the chat UI (chatSlice.ts).
 */
export interface LocationCard {
    name: string;
    type: string;
    rating: number;
    priceLevel: string;
    image?: string;
    coordinates?: { lat: number; lng: number };
}

// ─── Tool name → response type mapping ────────────────────────────────────────

/**
 * Maps backend tool names (from TOOL_REGISTRY in Flask server.py) to ToolCallResult types.
 */
const TOOL_TYPE_MAP: Record<string, ToolCallResult['type']> = {
    'suggest_poi': 'destination_recommendation',
    'search_route': 'route_generated',
    'modify_itinerary': 'itinerary_modified',
    'weather_agent': 'weather_info',
    'get_poi_details': 'destination_recommendation',
    'calculator_agent': 'tool_result',
    'update_user_profile': 'profile_updated',
    'submit_trip_feedback': 'tool_result',
    'get_xai_justification': 'tool_result',
    'generate_chat_title': 'tool_result',
    'generate_route_format': 'route_approval_required',
};

// ─── Private Helpers ──────────────────────────────────────────────────────────

/**
 * Attempts to extract structured destination data from the LLM response text.
 * Agents return plain text mentioning POI names; we search those via placeService.
 */
async function extract_destinations_from_text(
    response_text: string
): Promise<MapDestination[]> {
    try {
        const poi_patterns = [
            /Suggested POIs.*?:\s*(.+)/i,
            /recommend.*?:\s*(.+)/i,
            /found.*?places?:\s*(.+)/i,
            /route.*?through:\s*(.+)/i,
        ];

        let names_text = '';
        for (const pattern of poi_patterns) {
            const match = response_text.match(pattern);
            if (match) {
                names_text = match[1];
                break;
            }
        }

        if (!names_text) return [];

        const poi_names = names_text
            .split(/,\s*|\s+and\s+/)
            .map((n) => n.replace(/\(.*?\)/g, '').trim())
            .filter((n) => n.length > 2);

        if (poi_names.length === 0) return [];

        const results: MapDestination[] = [];
        for (const name of poi_names.slice(0, 5)) {
            try {
                const found = await placeService.searchPlaces(name, 0, 1);
                if (found.length > 0) {
                    results.push(found[0]);
                }
            } catch {
                // Silently skip if search fails for a specific name
            }
        }

        return results;
    } catch {
        return [];
    }
}

/**
 * Parses the backend response into a structured ToolCallResult.
 */
async function parse_response(
    backend_response: LLMBackendResponse
): Promise<ToolCallResult> {
    // 1. Handle error responses
    if (backend_response.status === 'error') {
        return {
            type: 'text',
            message:
                backend_response.message ||
                'Bir hata oluştu. Lütfen tekrar deneyin.',
        };
    }

    const response_text = backend_response.response || '';
    const tool_used = backend_response.toolUsed;

    // 2. No tool was used → plain text
    if (!tool_used) {
        return {
            type: 'text',
            message: response_text,
        };
    }

    // 3. Tool was used → determine type and extract data
    const result_type = TOOL_TYPE_MAP[tool_used] || 'tool_result';

    // 3a. Route approval flow — extract structured route data
    if (result_type === 'route_approval_required') {
        let routes: any[] = [];
        // routeData is set by Spring Boot after parsing the agent's JSON output
        if (backend_response.routeData) {
            const rd = backend_response.routeData;
            if (Array.isArray(rd)) {
                routes = rd;
            } else if (rd && typeof rd === 'object' && Array.isArray((rd as any).routes)) {
                routes = (rd as any).routes;
            }
        }
        // Fallback: try parsing response_text if routeData wasn't populated
        if (routes.length === 0 && response_text) {
            try {
                const parsed = JSON.parse(response_text);
                if (Array.isArray(parsed)) {
                    routes = parsed;
                } else if (parsed && Array.isArray(parsed.routes)) {
                    routes = parsed.routes;
                }
            } catch {
                // Not parseable — fall through
            }
        }

        return {
            type: 'route_approval_required',
            routeData: routes,
            message: '',
            toolUsed: tool_used,
            toolParams: backend_response.toolParams,
        };
    }

    // 3b. Destination recommendations and route search results
    if (
        result_type === 'destination_recommendation' ||
        result_type === 'route_generated'
    ) {
        const destinations =
            await extract_destinations_from_text(response_text);
        return {
            type: result_type,
            destinations: destinations.length > 0 ? destinations : undefined,
            message: response_text,
            toolUsed: tool_used,
            toolParams: backend_response.toolParams,
        };
    }

    return {
        type: result_type,
        message: response_text,
        toolUsed: tool_used,
        toolParams: backend_response.toolParams,
    };
}

/**
 * Converts a MapDestination to a LocationCard for the chat UI.
 */
export function destination_to_location_card(
    dest: MapDestination
): LocationCard {
    return {
        name: dest.name,
        type: dest.category,
        rating: dest.rating,
        priceLevel: '$'.repeat(dest.priceLevel),
        image: dest.image,
        coordinates: dest.coordinates
            ? { lat: dest.coordinates[0], lng: dest.coordinates[1] }
            : undefined,
    };
}

/**
 * Gets the auth token from localStorage for authenticated requests.
 */
function get_auth_headers(): Record<string, string> {
    const headers: Record<string, string> = {
        'Content-Type': 'application/json',
    };
    const token = localStorage.getItem('roadrunner_token');
    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }
    return headers;
}

// ─── Public API ───────────────────────────────────────────────────────────────

/**
 * Sends a message to the LLM via the Spring Boot backend.
 *
 * Frontend → POST /api/llm/chat { query, chatId? } → Spring Boot → Flask LLM Server
 *
 * @param message - The user's message
 * @param history - Previous chat messages for context (reserved for future use)
 * @returns Structured result with type, message, and optional destinations
 */
export async function send_message(
    message: string,
    history: ChatMessage[] = [],
    userId?: string
): Promise<ToolCallResult> {
    try {
        // Take only the last 10 messages for context
        const recentHistory = history.slice(-10);

        const response = await fetch(`${API_BASE_URL}/llm/chat`, {
            method: 'POST',
            headers: get_auth_headers(),
            body: JSON.stringify({
                query: message,
                user_id: userId ?? null, // Pass user ID directly so Flask agents can fetch personas
                history: recentHistory.map((msg) => ({
                    role: msg.role,
                    content: msg.content,
                })),
            }),
        });

        if (!response.ok) {
            const error_body = await response.text();
            throw new Error(
                `Backend returned ${response.status}: ${error_body}`
            );
        }

        const backend_response: LLMBackendResponse = await response.json();
        return await parse_response(backend_response);
    } catch (error) {
        if (error instanceof TypeError && error.message.includes('fetch')) {
            throw new Error(
                'Backend sunucusuna bağlanılamadı. Sunucunun çalıştığından emin olun (port 8080).'
            );
        }
        throw error instanceof Error
            ? error
            : new Error(`LLM hatası: ${String(error)}`);
    }
}

/**
 * Sends a message and returns only the text response.
 */
export async function send_message_simple(
    message: string,
    history: ChatMessage[] = []
): Promise<string> {
    const result = await send_message(message, history);
    return result.message;
}

/**
 * Generates a concise trip title via the backend.
 *
 * Frontend → POST /api/llm/title { query } → Spring Boot → Flask LLM Server
 *
 * @param query - The user's first message in the chat
 * @returns A short descriptive title (max ~60 chars)
 */
export async function generate_trip_title(query: string): Promise<string> {
    try {
        const response = await fetch(`${API_BASE_URL}/llm/title`, {
            method: 'POST',
            headers: get_auth_headers(),
            body: JSON.stringify({ query }),
        });

        if (response.ok) {
            const data: { title: string } = await response.json();
            if (data.title && data.title.length > 0) {
                return data.title;
            }
        }
    } catch {
        // Fall through to local heuristic
    }

    return generate_title_local(query);
}

/**
 * Local heuristic for generating a trip title without LLM.
 */
function generate_title_local(query: string): string {
    const clean_query = query.trim();
    const stop_words = [
        'a', 'an', 'the', 'to', 'in', 'for', 'of', 'and', 'or',
        'is', 'are', 'i', 'want', 'would', 'like', 'please', 'can',
        'you', 'me', 'my', 'trip', 'plan', 'visit',
        'bir', 'bana', 'öner', 'bul', 'ben', 'istiyorum', 'için',
        'ne', 'nerede', 'nasıl', 'var', 'mı', 'mi',
    ];

    const words = clean_query
        .toLowerCase()
        .split(/\s+/)
        .filter((word) => !stop_words.includes(word) && word.length > 2);

    const title_words = words
        .slice(0, 3)
        .map((word) => word.charAt(0).toUpperCase() + word.slice(1));

    return title_words.length > 0 ? title_words.join(' ') : 'New Trip';
}

// ─── Re-exports for backwards compatibility with geminiService API ────────────

export const sendMessage = send_message;
export const sendMessageSimple = send_message_simple;
export const generateTripTitle = generate_trip_title;
