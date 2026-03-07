import { createSlice, type PayloadAction } from '@reduxjs/toolkit';
import type { AppDispatch } from './index';
import { getStoredToken, removeToken, userApi } from '../services/userService';
import type { UserData } from '../services/userService';

// Travel persona interface
export interface TravelPersona {
    travelStyles: string[];
    interests: string[];
    travelFrequency: string;
    preferredPace: string;
}

// User interface
export interface User {
    id: string;
    email: string;
    name: string;
    avatar?: string;
    travelPersona?: TravelPersona;
    hasCompletedOnboarding: boolean;
}

// Auth state interface
interface AuthState {
    user: User | null;
    isAuthenticated: boolean;
    isLoading: boolean;
    error: string | null;
    isNewSignup: boolean;
}

// Load initial state from localStorage
const loadAuthState = (): AuthState => {
    try {
        const storedUser = localStorage.getItem('travelplanner_user');
        if (storedUser) {
            const user = JSON.parse(storedUser) as User;
            return {
                user,
                isAuthenticated: true,
                isLoading: false,
                error: null,
                isNewSignup: false,
            };
        }
    } catch (error) {
        console.error('Error loading auth state:', error);
    }

    return {
        user: null,
        isAuthenticated: false,
        isLoading: false,
        error: null,
        isNewSignup: false,
    };
};

const initialState: AuthState = loadAuthState();

// Auth slice
const authSlice = createSlice({
    name: 'auth',
    initialState,
    reducers: {
        setLoading: (state, action: PayloadAction<boolean>) => {
            state.isLoading = action.payload;
        },
        setError: (state, action: PayloadAction<string | null>) => {
            state.error = action.payload;
        },
        loginSuccess: (state, action: PayloadAction<User>) => {
            state.user = action.payload;
            state.isAuthenticated = true;
            state.isLoading = false;
            state.error = null;
            state.isNewSignup = false;
            localStorage.setItem('travelplanner_user', JSON.stringify(action.payload));
        },
        logout: (state) => {
            state.user = null;
            state.isAuthenticated = false;
            state.isLoading = false;
            state.error = null;
            state.isNewSignup = false;
            localStorage.removeItem('travelplanner_user');
        },
        signupSuccess: (state, action: PayloadAction<User>) => {
            state.user = action.payload;
            state.isAuthenticated = true;
            state.isLoading = false;
            state.error = null;
            state.isNewSignup = true;
            localStorage.setItem('travelplanner_user', JSON.stringify(action.payload));
        },
        clearNewSignup: (state) => {
            state.isNewSignup = false;
        },
        updateUser: (state, action: PayloadAction<Partial<User>>) => {
            if (state.user) {
                state.user = { ...state.user, ...action.payload };
                localStorage.setItem('travelplanner_user', JSON.stringify(state.user));
            }
        },
        updateTravelPersona: (state, action: PayloadAction<TravelPersona>) => {
            if (state.user) {
                state.user.travelPersona = action.payload;
                state.user.hasCompletedOnboarding = true;
                localStorage.setItem('travelplanner_user', JSON.stringify(state.user));
            }
        },
        deleteAccount: (state) => {
            state.user = null;
            state.isAuthenticated = false;
            state.isLoading = false;
            state.error = null;
            state.isNewSignup = false;
            localStorage.removeItem('travelplanner_user');
        },
    },
});

export const {
    setLoading,
    setError,
    loginSuccess,
    logout,
    signupSuccess,
    clearNewSignup,
    updateUser,
    updateTravelPersona,
    deleteAccount,
} = authSlice.actions;

// ─── Helper: map backend UserData to frontend User ───────────────────────────

export const mapUserDataToUser = (data: UserData, hasCompletedOnboarding = true): User => {
    const firstPersona = data.travelPersonas?.[0];
    return {
        id: data.id,
        email: data.email,
        name: data.name,
        avatar: data.avatar,
        hasCompletedOnboarding,
        travelPersona: firstPersona
            ? {
                travelStyles: firstPersona.travelStyles,
                interests: firstPersona.interests,
                travelFrequency: firstPersona.travelFrequency,
                preferredPace: firstPersona.preferredPace,
            }
            : undefined,
    };
};

// ─── Thunks ──────────────────────────────────────────────────────────────────

/** Restores user session from stored JWT token on app load. */
export const restoreSession = () => async (dispatch: AppDispatch) => {
    try {
        const token = getStoredToken();
        if (!token) return;

        const userData = await userApi.getMe();
        const user = mapUserDataToUser(userData);
        dispatch(loginSuccess(user));
    } catch {
        // Token is invalid or expired — clean up silently
        removeToken();
        localStorage.removeItem('travelplanner_user');
    }
};

export default authSlice.reducer;
