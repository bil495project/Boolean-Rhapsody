import { createSlice, type PayloadAction } from '@reduxjs/toolkit';
import type { MapDestination } from '../data/destinations';
import { fetchSavedFromBackend, syncToggleToBackend } from './savedThunks';

interface SavedState {
    destinations: MapDestination[];
    activePlanId: string | null;
    isLoading: boolean;
    error: string | null;
}

// Load saved items from localStorage
const loadSaved = (): MapDestination[] => {
    try {
        const saved = localStorage.getItem('travelplanner_saved');
        if (saved) {
            return JSON.parse(saved) as MapDestination[];
        }
    } catch (error) {
        console.error('Error loading saved items:', error);
    }
    return [];
};

const saveLocally = (destinations: MapDestination[]) => {
    localStorage.setItem('travelplanner_saved', JSON.stringify(destinations));
};

const clearSavedState = (state: SavedState) => {
    state.destinations = [];
    state.activePlanId = null;
    state.isLoading = false;
    state.error = null;
    localStorage.removeItem('travelplanner_saved');
};

const initialState: SavedState = {
    destinations: loadSaved(),
    activePlanId: null,
    isLoading: false,
    error: null,
};

const savedSlice = createSlice({
    name: 'saved',
    initialState,
    reducers: {
        toggleSaveDestination: (state, action: PayloadAction<MapDestination>) => {
            const index = state.destinations.findIndex(d => d.id === action.payload.id);
            if (index >= 0) {
                state.destinations.splice(index, 1);
            } else {
                state.destinations.unshift(action.payload);
            }
            saveLocally(state.destinations);
        },
        addSaveDestination: (state, action: PayloadAction<MapDestination>) => {
            const exists = state.destinations.some(d => d.id === action.payload.id);
            if (!exists) {
                state.destinations.unshift(action.payload);
                saveLocally(state.destinations);
            }
        },
        clearSaved: (state) => {
            state.destinations = [];
            state.activePlanId = null;
            saveLocally([]);
        }
    },
    extraReducers: (builder) => {
        builder
            .addCase(fetchSavedFromBackend.pending, (state) => {
                state.isLoading = true;
            })
            .addCase(fetchSavedFromBackend.fulfilled, (state, action) => {
                state.isLoading = false;
                state.activePlanId = action.payload.planId;
                state.destinations = action.payload.destinations;
                saveLocally(state.destinations);
            })
            .addCase(fetchSavedFromBackend.rejected, (state, action) => {
                state.isLoading = false;
                state.error = action.payload as string;
            })
            .addCase(syncToggleToBackend.fulfilled, (state, action) => {
                if (action.payload) {
                    state.activePlanId = action.payload;
                }
            })
            .addCase('auth/logout', (state) => {
                clearSavedState(state);
            })
            .addCase('auth/deleteAccount', (state) => {
                clearSavedState(state);
            });
    }
});

export const { toggleSaveDestination, addSaveDestination, clearSaved } = savedSlice.actions;
export default savedSlice.reducer;
export * from './savedThunks';
