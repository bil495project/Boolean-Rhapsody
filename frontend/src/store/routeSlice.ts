import { createAsyncThunk, createSlice, type PayloadAction } from '@reduxjs/toolkit';
import {
    routeService,
    type GenerateRoutesPayload,
    type RouteConstraints,
    type RouteData,
    type RoutePreferences,
} from '../services/routeService';
import type { RootState } from './index';
import {
    buildRouteUserVector,
    clamp01,
    defaultTravelProfileAnswers,
    normalizeUserVector,
} from '../utils/travelProfile';
import type { TravelProfileAnswers } from '../types/travelProfile';

interface RouteState {
    routes: RouteData[];
    isLoading: boolean;
    error: string | null;
    currentRequest: GenerateRoutesPayload | null;
    savedRouteId: string | null;
    savedRouteTitle: string | null;
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
};

function buildUserVector(
    state: RootState,
    override?: Record<string, string | number | boolean>,
): Record<string, string> {
    const baseVector = override
        ? normalizeUserVector(override)
        : buildRouteUserVector(defaultTravelProfileAnswers());

    if (!baseVector.requestId) {
        baseVector.requestId = `${state.auth.user?.id ?? 'guest'}-${Date.now()}`;
    }

    return baseVector;
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
    state: RootState,
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
        userVector: buildUserVector(state, params?.userVectorOverride),
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
            requestedVisitCount: null,
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
        { getState, rejectWithValue },
    ) => {
        try {
            const state = getState() as RootState;
            const payload = buildGenerateRoutesPayload(state, params);
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

export const { clearRoutes, hydrateSavedRoute, replaceRouteAtIndex } = routeSlice.actions;
export default routeSlice.reducer;
