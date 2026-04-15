import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { savedRouteService, type SavedRouteDetail, type SavedRouteSummary, type SavedRouteWritePayload } from '../services/savedRouteService';

interface SavedRoutesState {
    summaries: SavedRouteSummary[];
    activeRoute: SavedRouteDetail | null;
    isLoading: boolean;
    isSaving: boolean;
    error: string | null;
}

const initialState: SavedRoutesState = {
    summaries: [],
    activeRoute: null,
    isLoading: false,
    isSaving: false,
    error: null,
};

const clearSavedRoutesState = (state: SavedRoutesState) => {
    state.summaries = [];
    state.activeRoute = null;
    state.isLoading = false;
    state.isSaving = false;
    state.error = null;
};

const toAsyncErrorMessage = (error: unknown, fallback: string) => {
    if (typeof error === 'object' && error !== null) {
        const candidate = error as {
            message?: string;
            response?: { data?: { error?: string } };
        };

        return candidate.response?.data?.error || candidate.message || fallback;
    }

    return fallback;
};

export const fetchSavedRoutes = createAsyncThunk(
    'savedRoutes/fetchAll',
    async (_, { rejectWithValue }) => {
        try {
            return await savedRouteService.getAllSavedRoutes();
        } catch (error: unknown) {
            return rejectWithValue(toAsyncErrorMessage(error, 'Failed to load saved routes'));
        }
    },
);

export const fetchSavedRouteDetail = createAsyncThunk(
    'savedRoutes/fetchDetail',
    async (savedRouteId: string, { rejectWithValue }) => {
        try {
            return await savedRouteService.getSavedRouteById(savedRouteId);
        } catch (error: unknown) {
            return rejectWithValue(toAsyncErrorMessage(error, 'Failed to load saved route'));
        }
    },
);

export const createSavedRouteThunk = createAsyncThunk(
    'savedRoutes/create',
    async (payload: SavedRouteWritePayload, { rejectWithValue }) => {
        try {
            return await savedRouteService.createSavedRoute(payload);
        } catch (error: unknown) {
            return rejectWithValue(toAsyncErrorMessage(error, 'Failed to save route'));
        }
    },
);

export const updateSavedRouteThunk = createAsyncThunk(
    'savedRoutes/update',
    async (
        params: { savedRouteId: string; payload: SavedRouteWritePayload },
        { rejectWithValue },
    ) => {
        try {
            return await savedRouteService.updateSavedRoute(params.savedRouteId, params.payload);
        } catch (error: unknown) {
            return rejectWithValue(toAsyncErrorMessage(error, 'Failed to update saved route'));
        }
    },
);

export const renameSavedRouteThunk = createAsyncThunk(
    'savedRoutes/rename',
    async (
        params: { savedRouteId: string; title: string },
        { rejectWithValue },
    ) => {
        try {
            return await savedRouteService.renameSavedRoute(params.savedRouteId, params.title);
        } catch (error: unknown) {
            return rejectWithValue(toAsyncErrorMessage(error, 'Failed to rename saved route'));
        }
    },
);

export const deleteSavedRouteThunk = createAsyncThunk(
    'savedRoutes/delete',
    async (savedRouteId: string, { rejectWithValue }) => {
        try {
            await savedRouteService.deleteSavedRoute(savedRouteId);
            return savedRouteId;
        } catch (error: unknown) {
            return rejectWithValue(toAsyncErrorMessage(error, 'Failed to delete saved route'));
        }
    },
);

function upsertSummary(summaries: SavedRouteSummary[], summary: SavedRouteSummary): SavedRouteSummary[] {
    const next = summaries.filter((item) => item.id !== summary.id);
    next.unshift(summary);
    return next;
}

function toSummary(detail: SavedRouteDetail): SavedRouteSummary {
    return {
        id: detail.id,
        title: detail.title,
        orderedPlaceIds: detail.orderedPlaceIds,
        stopCount: detail.stopCount,
        travelMode: detail.travelMode,
        totalDurationSec: detail.totalDurationSec,
        totalDistanceM: detail.totalDistanceM,
        feasible: detail.feasible,
        createdAt: detail.createdAt,
        updatedAt: detail.updatedAt,
    };
}

const savedRoutesSlice = createSlice({
    name: 'savedRoutes',
    initialState,
    reducers: {
        clearActiveSavedRoute: (state) => {
            state.activeRoute = null;
        },
    },
    extraReducers: (builder) => {
        builder
            .addCase(fetchSavedRoutes.pending, (state) => {
                state.isLoading = true;
                state.error = null;
            })
            .addCase(fetchSavedRoutes.fulfilled, (state, action) => {
                state.isLoading = false;
                state.summaries = action.payload;
            })
            .addCase(fetchSavedRoutes.rejected, (state, action) => {
                state.isLoading = false;
                state.error = action.payload as string;
            })
            .addCase(fetchSavedRouteDetail.pending, (state) => {
                state.isLoading = true;
                state.error = null;
            })
            .addCase(fetchSavedRouteDetail.fulfilled, (state, action) => {
                state.isLoading = false;
                state.activeRoute = action.payload;
                state.summaries = upsertSummary(state.summaries, toSummary(action.payload));
            })
            .addCase(fetchSavedRouteDetail.rejected, (state, action) => {
                state.isLoading = false;
                state.error = action.payload as string;
            })
            .addCase(createSavedRouteThunk.pending, (state) => {
                state.isSaving = true;
                state.error = null;
            })
            .addCase(createSavedRouteThunk.fulfilled, (state, action) => {
                state.isSaving = false;
                state.activeRoute = action.payload;
                state.summaries = upsertSummary(state.summaries, toSummary(action.payload));
            })
            .addCase(createSavedRouteThunk.rejected, (state, action) => {
                state.isSaving = false;
                state.error = action.payload as string;
            })
            .addCase(updateSavedRouteThunk.pending, (state) => {
                state.isSaving = true;
                state.error = null;
            })
            .addCase(updateSavedRouteThunk.fulfilled, (state, action) => {
                state.isSaving = false;
                state.activeRoute = action.payload;
                state.summaries = upsertSummary(state.summaries, toSummary(action.payload));
            })
            .addCase(updateSavedRouteThunk.rejected, (state, action) => {
                state.isSaving = false;
                state.error = action.payload as string;
            })
            .addCase(renameSavedRouteThunk.fulfilled, (state, action) => {
                state.summaries = upsertSummary(state.summaries, action.payload);
                if (state.activeRoute?.id === action.payload.id) {
                    state.activeRoute = {
                        ...state.activeRoute,
                        ...action.payload,
                    };
                }
            })
            .addCase(renameSavedRouteThunk.rejected, (state, action) => {
                state.error = action.payload as string;
            })
            .addCase(deleteSavedRouteThunk.fulfilled, (state, action) => {
                state.summaries = state.summaries.filter((item) => item.id !== action.payload);
                if (state.activeRoute?.id === action.payload) {
                    state.activeRoute = null;
                }
            })
            .addCase(deleteSavedRouteThunk.rejected, (state, action) => {
                state.error = action.payload as string;
            })
            .addCase('auth/logout', (state) => {
                clearSavedRoutesState(state);
            })
            .addCase('auth/deleteAccount', (state) => {
                clearSavedRoutesState(state);
            });
    },
});

export const { clearActiveSavedRoute } = savedRoutesSlice.actions;
export default savedRoutesSlice.reducer;
