import { createSlice, createAsyncThunk, type PayloadAction } from '@reduxjs/toolkit';
import { placeService } from '../services/placeService';
import type { MapDestination } from '../data/destinations';

interface PlacesState {
    destinations: MapDestination[];
    isLoading: boolean;
    error: string | null;
    lastFetched: number | null;
}

const initialState: PlacesState = {
    destinations: [],
    isLoading: false,
    error: null,
    lastFetched: null,
};

export const fetchAllPlaces = createAsyncThunk(
    'places/fetchAllPlaces',
    async (_, { rejectWithValue }) => {
        try {
            // Fetching a large number of places as done in ExplorePage
            const data = await placeService.getAllPlaces(0, 15000);
            return data;
        } catch (error: any) {
            return rejectWithValue(error.message || 'Failed to fetch places');
        }
    }
);

const placesSlice = createSlice({
    name: 'places',
    initialState,
    reducers: {
        clearPlaces: (state) => {
            state.destinations = [];
            state.lastFetched = null;
        },
    },
    extraReducers: (builder) => {
        builder
            .addCase(fetchAllPlaces.pending, (state) => {
                state.isLoading = true;
                state.error = null;
            })
            .addCase(fetchAllPlaces.fulfilled, (state, action: PayloadAction<MapDestination[]>) => {
                state.isLoading = false;
                state.destinations = action.payload;
                state.lastFetched = Date.now();
            })
            .addCase(fetchAllPlaces.rejected, (state, action) => {
                state.isLoading = false;
                state.error = action.payload as string;
            });
    },
});

export const { clearPlaces } = placesSlice.actions;
export default placesSlice.reducer;
