import axios from 'axios';
import { getStoredToken } from './userService';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

const api = axios.create({
    baseURL: BASE_URL,
    headers: { 'Content-Type': 'application/json' },
});

// Attach token to every request if available
api.interceptors.request.use((config) => {
    const token = getStoredToken();
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

// ─── Types (matching backend Route DTOs) ─────────────────────────────────────

export interface RoutePointData {
    index: number;
    poiId: string | null;
    poiName: string | null;
    latitude: number;
    longitude: number;
    formattedAddress: string | null;
    types: string[];
    ratingScore: number;
    ratingCount: number;
    priceLevel: string | null;
    plannedVisitMin: number;
    fixedAnchor?: boolean;
    protectedPoint?: boolean;
    protectionReason?: string | null;
}

export interface RouteSegmentData {
    fromIndex: number;
    toIndex: number;
    durationSec: number;
    distanceM: number;
}

export interface RouteData {
    routeId: string;
    points: RoutePointData[];
    segments: RouteSegmentData[];
    totalDurationSec: number;
    totalDistanceM: number;
    feasible: boolean;
    travelMode: string;
}

// ─── Constraints ─────────────────────────────────────────────────────────────

export interface AnchorFilter {
    minRating?: number;
    minRatingCount?: number;
}

export interface RoutePreferences {
    tempo: number;
    socialPreference: number;
    naturePreference: number;
    historyPreference: number;
    foodImportance: number;
    alcoholPreference: number;
    transportStyle: number;
    budgetLevel: number;
    tripLength: number;
    crowdPreference: number;
}

export interface RouteAnchor {
    kind: 'PLACE' | 'TYPE';
    placeId?: string;
    poiType?: string;
    filters?: AnchorFilter;
}

export interface RoutePoiSlot {
    kind: 'PLACE' | 'TYPE';
    placeId?: string;
    poiType?: string;
    filters?: AnchorFilter;
}

export interface RouteConstraints {
    stayAtHotel: boolean;
    needsBreakfast: boolean;
    needsLunch: boolean;
    needsDinner: boolean;
    startWithPoi?: boolean;
    endWithPoi?: boolean;
    startWithHotel?: boolean;
    endWithHotel?: boolean;
    startAnchor: RouteAnchor | null;
    endAnchor: RouteAnchor | null;
    poiSlots: RoutePoiSlot[] | null;
    requestedVisitCount: number | null;
}

export interface GenerateRoutesPayload {
    userVector: Record<string, string>;
    preferences: RoutePreferences;
    constraints: RouteConstraints;
    centerLat?: number;
    centerLng?: number;
    k: number;
}

// ─── Route API ───────────────────────────────────────────────────────────────

export const routeService = {
    generateRoutes: async (payload: GenerateRoutesPayload): Promise<RouteData[]> => {
        const response = await api.post<RouteData[]>('/routes/generate', payload);
        return response.data;
    },
};
