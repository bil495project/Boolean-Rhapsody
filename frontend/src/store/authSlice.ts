import { createAsyncThunk, createSlice, type PayloadAction } from '@reduxjs/toolkit';
import {
    userApi,
    getStoredToken,
    removeToken,
    type UserData,
} from '../services/userService';
import type { TravelProfile } from '../types/travelProfile';
import { hydrateTravelProfile } from '../utils/travelProfile';

export type TravelPersona = TravelProfile;

export interface AuthUser {
    id: string;
    email: string;
    name: string;
    avatar?: string;
    travelPersonas: TravelPersona[];
}

interface AuthState {
    user: AuthUser | null;
    isAuthenticated: boolean;
    isLoading: boolean;
    error: string | null;
    isNewSignup: boolean;
}

const initialState: AuthState = {
    user: null,
    isAuthenticated: false,
    isLoading: false,
    error: null,
    isNewSignup: false,
};

const sortTravelPersonas = (personas: TravelPersona[]): TravelPersona[] =>
    [...personas].sort((left, right) => {
        if (left.isDefault === right.isDefault) {
            return left.name.localeCompare(right.name, 'en');
        }
        return left.isDefault ? -1 : 1;
    });

export const mapUserDataToUser = (user: UserData, _isNewSignup = false): AuthUser => {
    const travelPersonas = sortTravelPersonas(
        (user.travelPersonas ?? []).map((persona) => hydrateTravelProfile(persona)),
    );

    return {
        id: user.id,
        email: user.email,
        name: user.name,
        avatar: user.avatar,
        travelPersonas,
    };
};

export const restoreSession = createAsyncThunk(
    'auth/restoreSession',
    async (_, { rejectWithValue }) => {
        const token = getStoredToken();
        if (!token) {
            return null;
        }

        try {
            const me = await userApi.getMe();
            return mapUserDataToUser(me);
        } catch (error: any) {
            removeToken();
            return rejectWithValue(error?.message || 'Failed to restore session');
        }
    },
);

export const createTravelPersonaAsync = createAsyncThunk(
    'auth/createTravelPersona',
    async (persona: Omit<TravelPersona, 'id'>, { rejectWithValue }) => {
        try {
            const created = await userApi.createPersona(persona);
            return hydrateTravelProfile(created);
        } catch (error: any) {
            return rejectWithValue(error?.message || 'Failed to save profile');
        }
    },
);

export const updateTravelPersonaAsync = createAsyncThunk(
    'auth/updateTravelPersona',
    async (payload: { id: string; persona: Omit<TravelPersona, 'id'> }, { rejectWithValue }) => {
        try {
            const updated = await userApi.updatePersona(payload.id, payload.persona);
            return hydrateTravelProfile(updated);
        } catch (error: any) {
            return rejectWithValue(error?.message || 'Failed to update profile');
        }
    },
);

export const deleteTravelPersonaAsync = createAsyncThunk(
    'auth/deleteTravelPersona',
    async (personaId: string, { rejectWithValue }) => {
        try {
            await userApi.deletePersona(personaId);
            return personaId;
        } catch (error: any) {
            return rejectWithValue(error?.message || 'Failed to delete profile');
        }
    },
);

export const setDefaultTravelPersonaAsync = createAsyncThunk(
    'auth/setDefaultTravelPersona',
    async (personaId: string, { getState, rejectWithValue }) => {
        try {
            const state = getState() as { auth: AuthState };
            const persona = state.auth.user?.travelPersonas.find((item) => item.id === personaId);
            if (!persona?.id) {
                throw new Error('Default profile could not be found');
            }

            const { id: _ignoredId, ...request } = persona;

            const updated = await userApi.updatePersona(persona.id, {
                ...request,
                isDefault: true,
            });
            return hydrateTravelProfile(updated);
        } catch (error: any) {
            return rejectWithValue(error?.message || 'Failed to update the default profile');
        }
    },
);

const authSlice = createSlice({
    name: 'auth',
    initialState,
    reducers: {
        loginSuccess: (state, action: PayloadAction<AuthUser>) => {
            state.user = action.payload;
            state.isAuthenticated = true;
            state.isLoading = false;
            state.error = null;
            state.isNewSignup = false;
        },
        signupSuccess: (state, action: PayloadAction<AuthUser>) => {
            state.user = action.payload;
            state.isAuthenticated = true;
            state.isLoading = false;
            state.error = null;
            state.isNewSignup = true;
        },
        logout: (state) => {
            removeToken();
            state.user = null;
            state.isAuthenticated = false;
            state.isLoading = false;
            state.error = null;
            state.isNewSignup = false;
        },
        setLoading: (state, action: PayloadAction<boolean>) => {
            state.isLoading = action.payload;
        },
        setError: (state, action: PayloadAction<string | null>) => {
            state.error = action.payload;
        },
        deleteAccount: (state) => {
            removeToken();
            state.user = null;
            state.isAuthenticated = false;
            state.isLoading = false;
            state.error = null;
            state.isNewSignup = false;
        },
        updateUser: (state, action: PayloadAction<Partial<AuthUser>>) => {
            if (!state.user) {
                return;
            }
            state.user = {
                ...state.user,
                ...action.payload,
                travelPersonas: action.payload.travelPersonas
                    ? sortTravelPersonas(action.payload.travelPersonas)
                    : state.user.travelPersonas,
            };
        },
        clearNewSignup: (state) => {
            state.isNewSignup = false;
        },
    },
    extraReducers: (builder) => {
        builder
            .addCase(restoreSession.pending, (state) => {
                state.isLoading = true;
            })
            .addCase(restoreSession.fulfilled, (state, action) => {
                state.isLoading = false;
                if (action.payload) {
                    state.user = action.payload;
                    state.isAuthenticated = true;
                    state.error = null;
                } else {
                    state.user = null;
                    state.isAuthenticated = false;
                }
            })
            .addCase(restoreSession.rejected, (state) => {
                state.isLoading = false;
                state.user = null;
                state.isAuthenticated = false;
            })
            .addCase(createTravelPersonaAsync.fulfilled, (state, action) => {
                if (!state.user) {
                    return;
                }

                const nextPersonas = state.user.travelPersonas
                    .filter((persona) => !action.payload.isDefault || !persona.isDefault)
                    .concat(action.payload);
                state.user.travelPersonas = sortTravelPersonas(nextPersonas);
            })
            .addCase(updateTravelPersonaAsync.fulfilled, (state, action) => {
                if (!state.user) {
                    return;
                }

                const nextPersonas = state.user.travelPersonas
                    .filter((persona) => persona.id !== action.payload.id)
                    .map((persona) => ({
                        ...persona,
                        isDefault: action.payload.isDefault ? false : persona.isDefault,
                    }))
                    .concat(action.payload);
                state.user.travelPersonas = sortTravelPersonas(nextPersonas);
            })
            .addCase(setDefaultTravelPersonaAsync.fulfilled, (state, action) => {
                if (!state.user) {
                    return;
                }

                state.user.travelPersonas = sortTravelPersonas(
                    state.user.travelPersonas.map((persona) =>
                        persona.id === action.payload.id
                            ? { ...action.payload, isDefault: true }
                            : { ...persona, isDefault: false },
                    ),
                );
            })
            .addCase(deleteTravelPersonaAsync.fulfilled, (state, action) => {
                if (!state.user) {
                    return;
                }
                state.user.travelPersonas = state.user.travelPersonas.filter(
                    (persona) => persona.id !== action.payload,
                );
            });
    },
});

export const {
    loginSuccess,
    signupSuccess,
    logout,
    setLoading,
    setError,
    deleteAccount,
    updateUser,
    clearNewSignup,
} = authSlice.actions;

export default authSlice.reducer;
