import { createSlice, type PayloadAction } from '@reduxjs/toolkit';
import type { MapDestination } from '../data/destinations';

interface SavedState {
    destinations: MapDestination[];
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

const saveSaved = (destinations: MapDestination[]) => {
    // TODO: When backend is ready, save per-user
    localStorage.setItem('travelplanner_saved', JSON.stringify(destinations));
};

const initialState: SavedState = {
    destinations: loadSaved(),
};

const savedSlice = createSlice({
    name: 'saved',
    initialState,
    reducers: {
        saveDestination: (state, action: PayloadAction<MapDestination>) => {
            // Check if already saved
            const exists = state.destinations.some(d => d.id === action.payload.id);
            if (!exists) {
                state.destinations.unshift(action.payload);
                saveSaved(state.destinations);
            }
        },

        unsaveDestination: (state, action: PayloadAction<string>) => {
            state.destinations = state.destinations.filter(d => d.id !== action.payload);
            saveSaved(state.destinations);
        },

        toggleSaveDestination: (state, action: PayloadAction<MapDestination>) => {
            const index = state.destinations.findIndex(d => d.id === action.payload.id);
            if (index >= 0) {
                state.destinations.splice(index, 1);
            } else {
                state.destinations.unshift(action.payload);
            }
            saveSaved(state.destinations);
        },
    },
});

export const { saveDestination, unsaveDestination, toggleSaveDestination } = savedSlice.actions;
export default savedSlice.reducer;
