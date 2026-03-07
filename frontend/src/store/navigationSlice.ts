import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

interface NavigationState {
    stops: (string | null)[]; // Array of destination IDs  
    mode: 'driving' | 'walking';
    routeInfo: { duration: number; distance: number } | null;
    routeCoordinates: [number, number][] | null; // Road-following coordinates from OSRM
    isLoadingRoute: boolean;
}

const initialState: NavigationState = {
    stops: [null, null], // Start with two empty slots
    mode: 'driving',
    routeInfo: null,
    routeCoordinates: null,
    isLoadingRoute: false,
};

const navigationSlice = createSlice({
    name: 'navigation',
    initialState,
    reducers: {
        setStops: (state, action: PayloadAction<(string | null)[]>) => {
            state.stops = action.payload;
            state.routeInfo = null;
            state.routeCoordinates = null;
        },

        addStop: (state) => {
            if (state.stops.length < 10) {
                state.stops.push(null);
                state.routeInfo = null;
                state.routeCoordinates = null;
            }
        },

        removeStop: (state, action: PayloadAction<number>) => {
            if (state.stops.length > 2) {
                state.stops = state.stops.filter((_, i) => i !== action.payload);
                state.routeInfo = null;
                state.routeCoordinates = null;
            }
        },

        updateStop: (state, action: PayloadAction<{ index: number; value: string | null }>) => {
            const { index, value } = action.payload;
            if (index >= 0 && index < state.stops.length) {
                state.stops[index] = value;
                state.routeInfo = null;
                state.routeCoordinates = null;
            }
        },

        setMode: (state, action: PayloadAction<'driving' | 'walking'>) => {
            state.mode = action.payload;
            state.routeInfo = null;
            state.routeCoordinates = null;
        },

        setRouteInfo: (state, action: PayloadAction<{ duration: number; distance: number } | null>) => {
            state.routeInfo = action.payload;
        },

        setRouteCoordinates: (state, action: PayloadAction<[number, number][] | null>) => {
            state.routeCoordinates = action.payload;
        },

        setLoadingRoute: (state, action: PayloadAction<boolean>) => {
            state.isLoadingRoute = action.payload;
        },

        clearRoute: (state) => {
            state.routeInfo = null;
            state.routeCoordinates = null;
        },
    },
});

export const {
    setStops,
    addStop,
    removeStop,
    updateStop,
    setMode,
    setRouteInfo,
    setRouteCoordinates,
    setLoadingRoute,
    clearRoute,
} = navigationSlice.actions;

export default navigationSlice.reducer;
