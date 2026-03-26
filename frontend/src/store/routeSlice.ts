import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import { routeService, type RouteData, type RouteConstraints } from '../services/routeService';
import type { RootState } from './index';

interface RouteState {
    routes: RouteData[];
    isLoading: boolean;
    error: string | null;
}

const initialState: RouteState = {
    routes: [],
    isLoading: false,
    error: null,
};

// Build a userVector map from the user's travel persona interests
function buildUserVector(state: RootState): Record<string, string> {
    const persona = state.auth.user?.travelPersona;
    if (persona && persona.interests && persona.interests.length > 0) {
        const vector: Record<string, string> = {};
        persona.interests.forEach((interest) => {
            vector[interest] = 'true';
        });
        if (persona.preferredPace) {
            vector['pace'] = persona.preferredPace;
        }
        return vector;
    }
    // Sensible default when no persona exists
    return { sightseeing: 'true', culture: 'true', food: 'true' };
}

export const generateRoutesThunk = createAsyncThunk(
    'route/generateRoutes',
    async (params: { k?: number; centerLat?: number; centerLng?: number; radiusKm?: number; constraints?: RouteConstraints } | undefined, { getState, rejectWithValue }) => {
        try {
            const state = getState() as RootState;
            const userVector = buildUserVector(state);
            const k = params?.k ?? 3;
            // Add center coordinates if provided
            if (params?.centerLat !== undefined && params?.centerLng !== undefined) {
                userVector['centerLat'] = String(params.centerLat);
                userVector['centerLng'] = String(params.centerLng);
            }
            if (params?.radiusKm !== undefined) {
                userVector['radiusKm'] = String(params.radiusKm);
            }
            return await routeService.generateRoutes(userVector, k, params?.constraints);
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
                state.routes = action.payload;
            })
            .addCase(generateRoutesThunk.rejected, (state, action) => {
                state.isLoading = false;
                state.error = action.payload as string;
            });
    },
});

export const { clearRoutes } = routeSlice.actions;
export default routeSlice.reducer;
