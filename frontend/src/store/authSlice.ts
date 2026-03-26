import { createSlice, type PayloadAction } from '@reduxjs/toolkit';
import type { AppDispatch } from './index';
import { getStoredToken, removeToken, userApi } from '../services/userService';
import type { UserData, TravelPersonaData } from '../services/userService';

// Travel persona interface
export interface TravelPersona {
    travelStyles: string[];
    interests: string[];
    travelFrequency: string;
    preferredPace: string;
    userVector?: Record<string, string | number | boolean>;
    weight_parkVeSeyirNoktalari?: number | string;
    weight_geceHayati?: number | string;
    weight_restoranToleransi?: number | string;
    weight_landmark?: number | string;
    weight_dogalAlanlar?: number | string;
    weight_tarihiAlanlar?: number | string;
    weight_kafeTatli?: number | string;
    weight_toplamPoiYogunlugu?: number | string;
    weight_sparsity?: number | string;
    weight_hotelCenterBias?: number | string;
    weight_butceSeviyesi?: number | string;
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
                userVector: firstPersona.userVector,
                weight_parkVeSeyirNoktalari: firstPersona.weight_parkVeSeyirNoktalari,
                weight_geceHayati: firstPersona.weight_geceHayati,
                weight_restoranToleransi: firstPersona.weight_restoranToleransi,
                weight_landmark: firstPersona.weight_landmark,
                weight_dogalAlanlar: firstPersona.weight_dogalAlanlar,
                weight_tarihiAlanlar: firstPersona.weight_tarihiAlanlar,
                weight_kafeTatli: firstPersona.weight_kafeTatli,
                weight_toplamPoiYogunlugu: firstPersona.weight_toplamPoiYogunlugu,
                weight_sparsity: firstPersona.weight_sparsity,
                weight_hotelCenterBias: firstPersona.weight_hotelCenterBias,
                weight_butceSeviyesi: firstPersona.weight_butceSeviyesi,
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
        const user = mapUserDataToUser(userData, userData.travelPersonas && userData.travelPersonas.length > 0);
        dispatch(loginSuccess(user));
    } catch {
        // Token is invalid or expired — clean up silently
        removeToken();
        localStorage.removeItem('travelplanner_user');
    }
};

/** Saves the travel persona to the backend (creates if none exists, updates if one does). */
export const saveTravelPersona = (personaData: TravelPersona) => async (dispatch: AppDispatch) => {
    try {
        dispatch(setLoading(true));
        
        // Check for existing personas
        const existingPersonas = await userApi.getPersonas();
        
        let savedPersona: TravelPersonaData;
        if (existingPersonas.length > 0) {
            // Update the first one
            savedPersona = await userApi.updatePersona(existingPersonas[0].id!, personaData);
        } else {
            // Create new
            savedPersona = await userApi.createPersona(personaData);
        }
        
        // Update local state
        dispatch(updateTravelPersona({
            travelStyles: savedPersona.travelStyles,
            interests: savedPersona.interests,
            travelFrequency: savedPersona.travelFrequency,
            preferredPace: savedPersona.preferredPace,
            userVector: savedPersona.userVector,
            weight_parkVeSeyirNoktalari: savedPersona.weight_parkVeSeyirNoktalari,
            weight_geceHayati: savedPersona.weight_geceHayati,
            weight_restoranToleransi: savedPersona.weight_restoranToleransi,
            weight_landmark: savedPersona.weight_landmark,
            weight_dogalAlanlar: savedPersona.weight_dogalAlanlar,
            weight_tarihiAlanlar: savedPersona.weight_tarihiAlanlar,
            weight_kafeTatli: savedPersona.weight_kafeTatli,
            weight_toplamPoiYogunlugu: savedPersona.weight_toplamPoiYogunlugu,
            weight_sparsity: savedPersona.weight_sparsity,
            weight_hotelCenterBias: savedPersona.weight_hotelCenterBias,
            weight_butceSeviyesi: savedPersona.weight_butceSeviyesi,
        }));
        
        dispatch(setError(null));
    } catch (error) {
        console.error('Error saving travel persona:', error);
        dispatch(setError('Failed to save travel persona. Please try again.'));
    } finally {
        dispatch(setLoading(false));
    }
};

export default authSlice.reducer;
