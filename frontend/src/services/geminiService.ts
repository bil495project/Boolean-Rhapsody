import { GoogleGenerativeAI, SchemaType, type FunctionDeclaration } from '@google/generative-ai';
import { type MapDestination } from '../data/destinations';
import { placeService } from './placeService';

const API_KEY = "AIzaSyDFy90_WVDQAHDMXm1lnjPsOTGKXgK0KXY";
const MODEL_NAME = import.meta.env.VITE_GEMINI_MODEL;

if (!API_KEY) {
    console.error('VITE_GEMINI_API_KEY is not set in .env');
}

const genAI = new GoogleGenerativeAI(API_KEY || '');

const SYSTEM_PROMPT = `You are a helpful travel planning assistant. Your role is:
- Find and recommend destinations, cafes, museums, parks, and attractions using the search_destinations tool
- Provide travel tips and recommendations
- Save places to their map using the save_destination tool

When a user asks for recommendations (like "recommend a cafe" or "find a museum"), you MUST use the search_destinations tool to find matching places from the actual database.
When the user wants to save a place, use the save_destination tool with the EXACT destination ID returned from your search.

IMPORTANT - Destination IDs:
When saving a destination, ALWAYS use the precise ID format provided by the search results (which might be UUID strings). DO NOT make up generic IDs.

Keep your responses friendly and concise. Always include relevant details like ratings when recommending places.`;

// Tool definitions for Gemini
const searchDestinationsDeclaration: FunctionDeclaration = {
    name: 'search_destinations',
    description: 'Search for destinations, cafes, museums, parks, or attractions in Ankara. Use this when user asks for recommendations or wants to find places.',
    parameters: {
        type: SchemaType.OBJECT,
        properties: {
            query: {
                type: SchemaType.STRING,
                description: 'Search query - name or keyword to search for',
            },
            category: {
                type: SchemaType.STRING,
                description: 'Category filter: Historical, Museum, Park, Culture, Nature, Landmark, or Cafe',
            },
        },
    },
};

const saveDestinationDeclaration: FunctionDeclaration = {
    name: 'save_destination',
    description: 'Save a destination to the user\'s map. Use this when user wants to save or bookmark a place.',
    parameters: {
        type: SchemaType.OBJECT,
        properties: {
            destinationId: {
                type: SchemaType.STRING,
                description: 'The ID of the destination to save',
            },
        },
        required: ['destinationId'],
    },
};

const tools = [
    {
        functionDeclarations: [searchDestinationsDeclaration, saveDestinationDeclaration],
    },
];

export interface ChatMessage {
    role: 'user' | 'model';
    content: string;
}

export interface ToolCallResult {
    type: 'destination_recommendation' | 'destination_saved' | 'text';
    destinations?: MapDestination[];
    savedDestination?: MapDestination;
    message: string;
}

// Execute tool calls asynchronously
async function executeToolCall(name: string, args: Record<string, string>): Promise<{ result: unknown; destinations?: MapDestination[] }> {
    switch (name) {
        case 'search_destinations': {
            try {
                // Ignore category filter if there is a query for simpler UI, or we can use it both
                const destinations = await placeService.searchPlaces(args.query || args.category || '');
                return {
                    result: destinations.length > 0
                        ? `Found ${destinations.length} places: ${destinations.map(d => `${d.name} (ID: ${d.id}) - ${d.category} - ${'$'.repeat(d.priceLevel)}`).join(', ')}`
                        : 'No destinations found matching your criteria.',
                    destinations: destinations.slice(0, 5), // Return top 5 to avoid overloading context
                };
            } catch (err) {
                console.error("Tool execution failed: search", err);
                return { result: "Failed to search places." };
            }
        }
        case 'save_destination': {
            try {
                const destination = await placeService.getPlaceById(args.destinationId);
                if (destination) {
                    return {
                        result: `Successfully saved ${destination.name} to your map!`,
                        destinations: [destination],
                    };
                }
                return { result: 'Destination not found.' };
            } catch (err) {
                console.error("Tool execution failed: save", err);
                return { result: 'Destination not found or failed to load.' };
            }
        }
        default:
            return { result: 'Unknown tool' };
    }
}

export async function sendMessage(
    message: string,
    history: ChatMessage[] = []
): Promise<ToolCallResult> {
    try {
        const model = genAI.getGenerativeModel({
            model: MODEL_NAME || 'gemini-2.0-flash',
            tools,
        });

        const chat = model.startChat({
            history: [
                {
                    role: 'user',
                    parts: [{ text: SYSTEM_PROMPT }],
                },
                {
                    role: 'model',
                    parts: [{ text: 'I understand! I\'m ready to help you explore Ankara. I can recommend cafes, museums, historical sites, and more. What would you like to discover?' }],
                },
                ...history.map((msg) => ({
                    role: msg.role as 'user' | 'model',
                    parts: [{ text: msg.content }],
                })),
            ],
        });

        const result = await chat.sendMessage(message);
        const response = result.response;

        // Check for function calls
        const functionCalls = response.functionCalls();

        if (functionCalls && functionCalls.length > 0) {
            const call = functionCalls[0];
            console.log('Tool call received:', call.name, call.args);

            const { result: toolResult, destinations } = await executeToolCall(
                call.name,
                call.args as Record<string, string>
            );

            console.log('Tool result:', toolResult);

            // Send tool result back to model for natural language response
            const followUp = await chat.sendMessage([
                {
                    functionResponse: {
                        name: call.name,
                        response: { result: toolResult },
                    },
                },
            ]);

            // Get text from follow-up, handling potential function calls
            let finalText = '';
            try {
                const followUpFunctionCalls = followUp.response.functionCalls();
                if (followUpFunctionCalls && followUpFunctionCalls.length > 0) {
                    // Model wants to call another function, just use the tool result
                    finalText = String(toolResult);
                } else {
                    finalText = followUp.response.text() || String(toolResult);
                }
            } catch {
                finalText = String(toolResult);
            }

            if (call.name === 'search_destinations' && destinations && destinations.length > 0) {
                return {
                    type: 'destination_recommendation',
                    destinations,
                    message: finalText,
                };
            } else if (call.name === 'save_destination' && destinations && destinations.length > 0) {
                return {
                    type: 'destination_saved',
                    savedDestination: destinations[0],
                    message: finalText,
                };
            }

            // Tool was called but no destinations - return text anyway
            return {
                type: 'text',
                message: finalText || 'The operation was completed.',
            };
        }

        // No function call, return plain text
        return {
            type: 'text',
            message: response.text(),
        };
    } catch (error) {
        console.error('Gemini API error:', error);
        throw new Error(`Failed to get response from AI: ${error instanceof Error ? error.message : 'Unknown error'}`);
    }
}

// Legacy function for simple text response (backwards compatibility)
export async function sendMessageSimple(
    message: string,
    history: ChatMessage[] = []
): Promise<string> {
    const result = await sendMessage(message, history);
    return result.message;
}

export function generateTripTitle(query: string): string {
    const cleanQuery = query.trim();
    const stopWords = ['a', 'an', 'the', 'to', 'in', 'for', 'of', 'and', 'or', 'is', 'are', 'i', 'want', 'would', 'like', 'please', 'can', 'you', 'me', 'my', 'trip', 'plan', 'visit', 'bir', 'bana', 'öner', 'bul'];
    const words = cleanQuery
        .toLowerCase()
        .split(/\s+/)
        .filter(word => !stopWords.includes(word) && word.length > 2);

    const titleWords = words.slice(0, 3).map(word =>
        word.charAt(0).toUpperCase() + word.slice(1)
    );

    return titleWords.length > 0 ? titleWords.join(' ') : 'New Trip';
}
