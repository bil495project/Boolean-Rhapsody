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

export interface RouteAnchor {
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
    startAnchor?: RouteAnchor;
    endAnchor?: RouteAnchor;
}

// ─── Route API ───────────────────────────────────────────────────────────────

export const routeService = {
    generateRoutes: async (
        userVector: Record<string, string>,
        k: number = 3,
        constraints?: RouteConstraints,
    ): Promise<RouteData[]> => {
        const response = await api.post<RouteData[]>('/routes/generate', {
            userVector,
            k,
            constraints,
        });
        return response.data;
    },
};
