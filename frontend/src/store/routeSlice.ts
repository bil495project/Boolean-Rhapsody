import { createAsyncThunk, createSlice, type PayloadAction } from '@reduxjs/toolkit';
import {
    routeService,
    type GenerateRoutesPayload,
    type RouteConstraints,
    type RouteData,
    type RoutePreferences,
} from '../services/routeService';
import {
    buildRouteUserVector,
    clamp01,
    defaultTravelProfileAnswers,
    normalizeUserVector,
} from '../utils/travelProfile';
import type { TravelProfileAnswers } from '../types/travelProfile';

// ─── Chat-approval flow types ─────────────────────────────────────────────────

interface SetChatRoutesPayload {
    routes: RouteData[];
    chatId: string;
}

// ─── State ────────────────────────────────────────────────────────────────────

interface RouteState {
    routes: RouteData[];
    isLoading: boolean;
    error: string | null;
    currentRequest: GenerateRoutesPayload | null;
    savedRouteId: string | null;
    savedRouteTitle: string | null;

    /** True when routes were loaded from a chat flow and require user approval. */
    pendingChatApproval: boolean;
    /** The chat ID to navigate back to after approval. */
    returnChatId: string | null;
    /** The single route the user selected in the approval flow. */
    approvedRoute: RouteData | null;
    /**
     * The route the user selected from the route page during a chat-approval flow.
     * Unlike `approvedRoute`, this field is NOT cleared by `clearChatApproval` so
     * the ChatPage map can keep displaying the selected route after returning from
     * the RoutePage.
     */
    selectedChatRoute: RouteData | null;
    /**
     * A route the user asked to explain via the "Ask LLM about Route" button.
     * ChatPage reads this on mount, fires the query automatically, then clears it.
     */
    pendingRouteExplain: RouteData | null;
}

interface HydrateSavedRoutePayload {
    route: RouteData;
    generateRequest: GenerateRoutesPayload;
    savedRouteId: string;
    title: string;
}

interface ReplaceRoutePayload {
    index: number;
    route: RouteData;
}

const initialState: RouteState = {
    routes: [],
    isLoading: false,
    error: null,
    currentRequest: null,
    savedRouteId: null,
    savedRouteTitle: null,
    pendingChatApproval: false,
    returnChatId: null,
    approvedRoute: null,
    selectedChatRoute: null,
    pendingRouteExplain: null,
};

function buildUserVector(
    override?: Record<string, string | number | boolean>,
): Record<string, string> {
    return override
        ? normalizeUserVector(override)
        : buildRouteUserVector(defaultTravelProfileAnswers());
}

function buildPreferences(
    override?: Partial<TravelProfileAnswers>,
): RoutePreferences {
    const defaults = defaultTravelProfileAnswers();
    return {
        tempo: clamp01(override?.tempo ?? defaults.tempo),
        socialPreference: clamp01(override?.socialPreference ?? defaults.socialPreference),
        naturePreference: clamp01(override?.naturePreference ?? defaults.naturePreference),
        historyPreference: clamp01(override?.historyPreference ?? defaults.historyPreference),
        foodImportance: clamp01(override?.foodImportance ?? defaults.foodImportance),
        alcoholPreference: clamp01(override?.alcoholPreference ?? defaults.alcoholPreference),
        transportStyle: clamp01(override?.transportStyle ?? defaults.transportStyle),
        budgetLevel: clamp01(override?.budgetLevel ?? defaults.budgetLevel),
        tripLength: clamp01(override?.tripLength ?? defaults.tripLength),
        crowdPreference: clamp01(override?.crowdPreference ?? defaults.crowdPreference),
    };
}

function buildGenerateRoutesPayload(
    params: {
        k?: number;
        centerLat?: number;
        centerLng?: number;
        constraints?: RouteConstraints;
        userVectorOverride?: Record<string, string | number | boolean>;
        preferencesOverride?: Partial<TravelProfileAnswers>;
    } | undefined,
): GenerateRoutesPayload {
    return {
        userVector: buildUserVector(params?.userVectorOverride),
        preferences: buildPreferences(params?.preferencesOverride),
        constraints: params?.constraints ?? {
            needsBreakfast: false,
            needsLunch: false,
            needsDinner: false,
            startPoint: { type: 'HOTEL' },
            endPoint: { type: 'HOTEL' },
            startAnchor: null,
            endAnchor: null,
            poiSlots: null,
        },
        centerLat: params?.centerLat,
        centerLng: params?.centerLng,
        k: params?.k ?? 3,
    };
}

export const generateRoutesThunk = createAsyncThunk(
    'route/generateRoutes',
    async (
        params: {
            k?: number;
            centerLat?: number;
            centerLng?: number;
            constraints?: RouteConstraints;
            userVectorOverride?: Record<string, string | number | boolean>;
            preferencesOverride?: Partial<TravelProfileAnswers>;
        } | undefined,
        { rejectWithValue },
    ) => {
        try {
            const payload = buildGenerateRoutesPayload(params);
            const routes = await routeService.generateRoutes(payload);
            return { routes, payload };
        } catch (error: any) {
            return rejectWithValue(
                error?.response?.data?.error || error.message || 'Failed to generate routes',
            );
        }
    },
);

const routeSlice = createSlice({
    name: 'route',
    initialState,
    reducers: {
        clearRoutes: (state) => {
            state.routes = [];
            state.error = null;
            state.currentRequest = null;
            state.savedRouteId = null;
            state.savedRouteTitle = null;
        },
        hydrateSavedRoute: (state, action: PayloadAction<HydrateSavedRoutePayload>) => {
            state.routes = [action.payload.route];
            state.error = null;
            state.isLoading = false;
            state.currentRequest = action.payload.generateRequest;
            state.savedRouteId = action.payload.savedRouteId;
            state.savedRouteTitle = action.payload.title;
        },
        replaceRouteAtIndex: (state, action: PayloadAction<ReplaceRoutePayload>) => {
            const { index, route } = action.payload;
            if (index >= 0 && index < state.routes.length) {
                state.routes[index] = route;
            }
        },

        // ─── Chat approval flow reducers ──────────────────────────────────

        /** Store routes received from the chat LLM tool call for user review. */
        setChatRoutes: (state, action: PayloadAction<SetChatRoutesPayload>) => {
            state.routes = action.payload.routes;
            state.pendingChatApproval = true;
            state.returnChatId = action.payload.chatId;
            state.approvedRoute = null;
            // Clear any previously selected route so the map starts fresh for
            // this new round of route alternatives.
            state.selectedChatRoute = null;
            state.error = null;
            state.isLoading = false;
            // Build a default request so route mutations (reroll/insert/remove/reorder)
            // have the required userVector and preferences to operate.
            state.currentRequest = buildGenerateRoutesPayload(undefined);
            state.savedRouteId = null;
            state.savedRouteTitle = null;
        },

        /** Mark a route as approved by the user from the Route Page. */
        approveRouteForChat: (state, action: PayloadAction<RouteData>) => {
            state.approvedRoute = action.payload;
            // Persist the selected route so the ChatPage map keeps displaying it
            // even after the approval flow is cleared.
            state.selectedChatRoute = action.payload;
            // Keep pendingChatApproval true so ChatPage can detect it
        },

        /** Reset all chat-approval state after the flow completes. */
        clearChatApproval: (state) => {
            state.pendingChatApproval = false;
            state.returnChatId = null;
            state.approvedRoute = null;
            // NOTE: selectedChatRoute is intentionally NOT cleared here so the
            // map in ChatPage continues to display the approved route.
        },

        /** Clear the route shown on the ChatPage map (e.g. when a new chat starts). */
        clearSelectedChatRoute: (state) => {
            state.selectedChatRoute = null;
        },

        /** Signal ChatPage to auto-fire a route explanation query for this route. */
        setPendingRouteExplain: (state, action: PayloadAction<RouteData>) => {
            state.pendingRouteExplain = action.payload;
        },

        /** Clear after ChatPage has consumed the pending explanation request. */
        clearPendingRouteExplain: (state) => {
            state.pendingRouteExplain = null;
        },
    },
    extraReducers: (builder) => {
        builder
            .addCase(generateRoutesThunk.pending, (state) => {
                state.isLoading = true;
                state.error = null;
                state.routes = [];
            })
            .addCase(generateRoutesThunk.fulfilled, (state, action) => {
                state.isLoading = false;
                state.routes = action.payload.routes;
                state.currentRequest = action.payload.payload;
            })
            .addCase(generateRoutesThunk.rejected, (state, action) => {
                state.isLoading = false;
                state.error = action.payload as string;
            });
    },
});

export const {
    clearRoutes,
    hydrateSavedRoute,
    replaceRouteAtIndex,
    setChatRoutes,
    approveRouteForChat,
    clearChatApproval,
    clearSelectedChatRoute,
    setPendingRouteExplain,
    clearPendingRouteExplain,
} = routeSlice.actions;

export default routeSlice.reducer;

