import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import { routeService, type RouteData, type RouteConstraints } from '../services/routeService';
import type { RootState } from './index';
import type { TravelPersona } from './authSlice';

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

const ROUTE_WEIGHT_KEYS = [
    'weight_parkVeSeyirNoktalari',
    'weight_geceHayati',
    'weight_restoranToleransi',
    'weight_landmark',
    'weight_dogalAlanlar',
    'weight_tarihiAlanlar',
    'weight_kafeTatli',
    'weight_toplamPoiYogunlugu',
    'weight_sparsity',
    'weight_hotelCenterBias',
    'weight_butceSeviyesi',
] as const;

type RouteWeightKey = (typeof ROUTE_WEIGHT_KEYS)[number];
type WeightVector = Record<RouteWeightKey, string>;

function clamp01(value: number): number {
    return Math.max(0, Math.min(1, value));
}

function toVectorString(value: number): string {
    return clamp01(value).toFixed(3);
}

function asClampedNumber(value: unknown): number | null {
    if (typeof value === 'number' && Number.isFinite(value)) {
        return clamp01(value);
    }
    if (typeof value === 'string' && value.trim() !== '') {
        const parsed = Number(value);
        if (Number.isFinite(parsed)) {
            return clamp01(parsed);
        }
    }
    return null;
}

function normalizeExistingVector(rawVector: Record<string, string | number | boolean>): Record<string, string> {
    const normalized: Record<string, string> = {};
    Object.entries(rawVector).forEach(([key, value]) => {
        if (value === null || value === undefined) return;
        if (typeof value === 'number') {
            normalized[key] = Number.isFinite(value) ? String(value) : '0';
            return;
        }
        if (typeof value === 'boolean') {
            normalized[key] = value ? '1' : '0';
            return;
        }
        normalized[key] = value;
    });
    return normalized;
}

function baseWeightVector(): WeightVector {
    return {
        weight_parkVeSeyirNoktalari: toVectorString(0.45),
        weight_geceHayati: toVectorString(0.20),
        weight_restoranToleransi: toVectorString(0.55),
        weight_landmark: toVectorString(0.50),
        weight_dogalAlanlar: toVectorString(0.40),
        weight_tarihiAlanlar: toVectorString(0.45),
        weight_kafeTatli: toVectorString(0.35),
        weight_toplamPoiYogunlugu: toVectorString(0.55),
        weight_sparsity: toVectorString(0.40),
        weight_hotelCenterBias: toVectorString(0.70),
        weight_butceSeviyesi: toVectorString(0.50),
    };
}

function pickDirectWeights(persona: TravelPersona | undefined): Partial<WeightVector> {
    if (!persona) return {};

    const direct: Partial<WeightVector> = {};
    const rawPersona = persona as unknown as Record<string, unknown>;
    ROUTE_WEIGHT_KEYS.forEach((key) => {
        const value = asClampedNumber(rawPersona[key]);
        if (value !== null) {
            direct[key] = toVectorString(value);
        }
    });
    return direct;
}

function deriveLegacyWeights(persona: TravelPersona | undefined): WeightVector {
    const defaults = baseWeightVector();
    if (!persona) return defaults;

    const interests = new Set(persona.interests ?? []);
    const styles = new Set(persona.travelStyles ?? []);

    const hasInterest = (value: string) => interests.has(value) ? 1 : 0;
    const hasStyle = (value: string) => styles.has(value) ? 1 : 0;

    const paceScore = persona.preferredPace === 'packed'
        ? 1
        : persona.preferredPace === 'balanced'
            ? 0.6
            : persona.preferredPace === 'relaxed'
                ? 0.25
                : 0.55;

    const frequencyScore = persona.travelFrequency === 'frequent'
        ? 0.8
        : persona.travelFrequency === 'occasional'
            ? 0.55
            : persona.travelFrequency === 'first-timer'
                ? 0.4
                : 0.5;

    const budgetScore = styles.has('budget')
        ? 0.15
        : styles.has('luxury')
            ? 0.85
            : 0.5;

    const parkVeSeyirNoktalari = clamp01(
        0.18
        + hasInterest('nature') * 0.35
        + hasInterest('photography') * 0.20
        + hasStyle('relaxation') * 0.12
        + hasStyle('adventure') * 0.10,
    );

    const geceHayati = clamp01(
        0.08
        + hasInterest('nightlife') * 0.58
        + hasStyle('luxury') * 0.12
        + hasStyle('food') * 0.06,
    );

    const restoranToleransi = clamp01(
        0.20
        + hasInterest('local-food') * 0.38
        + hasStyle('food') * 0.32
        + hasStyle('luxury') * 0.08,
    );

    const landmark = clamp01(
        0.22
        + hasInterest('photography') * 0.20
        + hasInterest('architecture') * 0.22
        + hasInterest('museums') * 0.18
        + hasStyle('culture') * 0.14,
    );

    const dogalAlanlar = clamp01(
        0.14
        + hasInterest('nature') * 0.48
        + hasStyle('adventure') * 0.18
        + hasStyle('relaxation') * 0.10,
    );

    const tarihiAlanlar = clamp01(
        0.16
        + hasInterest('history') * 0.46
        + hasInterest('museums') * 0.20
        + hasInterest('architecture') * 0.14
        + hasStyle('culture') * 0.16,
    );

    const kafeTatli = clamp01(
        0.15
        + hasInterest('local-food') * 0.24
        + hasInterest('shopping') * 0.10
        + hasStyle('relaxation') * 0.12
        + hasStyle('food') * 0.14,
    );

    const toplamPoiYogunlugu = clamp01(
        0.22
        + paceScore * 0.50
        + frequencyScore * 0.18,
    );

    const sparsity = clamp01(
        0.18
        + (1 - paceScore) * 0.28
        + hasInterest('nature') * 0.14
        + hasStyle('adventure') * 0.12,
    );

    const hotelCenterBias = clamp01(
        0.72
        + hasStyle('relaxation') * 0.08
        + hasInterest('shopping') * 0.05
        - hasStyle('adventure') * 0.16
        - (frequencyScore * 0.08),
    );

    return {
        weight_parkVeSeyirNoktalari: toVectorString(parkVeSeyirNoktalari),
        weight_geceHayati: toVectorString(geceHayati),
        weight_restoranToleransi: toVectorString(restoranToleransi),
        weight_landmark: toVectorString(landmark),
        weight_dogalAlanlar: toVectorString(dogalAlanlar),
        weight_tarihiAlanlar: toVectorString(tarihiAlanlar),
        weight_kafeTatli: toVectorString(kafeTatli),
        weight_toplamPoiYogunlugu: toVectorString(toplamPoiYogunlugu),
        weight_sparsity: toVectorString(sparsity),
        weight_hotelCenterBias: toVectorString(hotelCenterBias),
        weight_butceSeviyesi: toVectorString(budgetScore),
    };
}

// Build a backend-compatible flat userVector for route generation.
function buildUserVector(state: RootState): Record<string, string> {
    const persona = state.auth.user?.travelPersona;
    const fromPersonaVector = persona?.userVector
        ? normalizeExistingVector(persona.userVector)
        : {};

    const directWeights = pickDirectWeights(persona);
    const legacyWeights = deriveLegacyWeights(persona);

    const merged: Record<string, string> = {
        ...legacyWeights,
        ...directWeights,
        ...fromPersonaVector,
    };

    if (!merged.requestId) {
        const personaSeed = state.auth.user?.id ?? 'guest';
        merged.requestId = `${personaSeed}-${Date.now()}`;
    }

    return merged;
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
