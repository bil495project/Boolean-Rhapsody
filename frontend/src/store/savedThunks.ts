import { createAsyncThunk } from '@reduxjs/toolkit';
import type { MapDestination } from '../data/destinations';
import { travelPlanService } from '../services/travelPlanService';
import { placeService } from '../services/placeService';

// Thunk to fetch saved places from backend
export const fetchSavedFromBackend = createAsyncThunk(
    'saved/fetchFromBackend',
    async (_, { rejectWithValue }) => {
        try {
            const plans = await travelPlanService.getAllPlans();
            if (plans.length === 0) return { destinations: [], planId: null };

            // For now, we use the most recent plan as the "Saved Places" plan
            const latestPlan = [...plans].sort((a, b) => b.createdAt - a.createdAt)[0];
            const destinations = await placeService.getBulkPlaces(latestPlan.selectedPlaceIds);
            return { destinations, planId: latestPlan.id };
        } catch (error: any) {
            return rejectWithValue(error.message || 'Failed to fetch saved places');
        }
    }
);

// Thunk to sync local changes to backend
export const syncToggleToBackend = createAsyncThunk(
    'saved/syncToggleToBackend',
    async (_destination: MapDestination, { getState, rejectWithValue }) => {
        const state = getState() as any;
        const isAuthenticated = state.auth.isAuthenticated;
        const savedDestinations = state.saved.destinations;
        const activePlanId = state.saved.activePlanId;

        if (!isAuthenticated) return null;

        try {
            // The local state (savedDestinations) was ALREADY updated by the toggleSaveDestination reducer
            // because the component dispatches it synchronously BEFORE this thunk is called.
            // So we just need to send the current list of IDs from the state.
            const ids = savedDestinations.map((d: MapDestination) => d.id);

            if (activePlanId) {
                await travelPlanService.updatePlan(activePlanId, { selectedPlaceIds: ids });
                return activePlanId;
            } else {
                const newPlan = await travelPlanService.createPlan({ selectedPlaceIds: ids });
                return newPlan.id;
            }
        } catch (error: any) {
            return rejectWithValue(error.message || 'Failed to sync with server');
        }
    }
);
