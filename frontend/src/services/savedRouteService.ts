import axios from 'axios';
import { getStoredToken } from './userService';
import type { GenerateRoutesPayload, RouteData } from './routeService';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

const api = axios.create({
    baseURL: BASE_URL,
    headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
    const token = getStoredToken();
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

export interface SavedRouteSummary {
    id: string;
    title: string;
    orderedPlaceIds: string[];
    stopCount: number;
    travelMode: string;
    totalDurationSec: number;
    totalDistanceM: number;
    feasible: boolean;
    createdAt: number;
    updatedAt: number;
}

export interface SavedRouteDetail extends SavedRouteSummary {
    route: RouteData;
    generateRequest: GenerateRoutesPayload;
}

export interface SavedRouteWritePayload {
    title?: string;
    route: RouteData;
    generateRequest: GenerateRoutesPayload;
}

export const savedRouteService = {
    getAllSavedRoutes: async (): Promise<SavedRouteSummary[]> => {
        const response = await api.get<SavedRouteSummary[]>('/users/me/saved-routes');
        return response.data;
    },

    getSavedRouteById: async (savedRouteId: string): Promise<SavedRouteDetail> => {
        const response = await api.get<SavedRouteDetail>(`/users/me/saved-routes/${savedRouteId}`);
        return response.data;
    },

    createSavedRoute: async (payload: SavedRouteWritePayload): Promise<SavedRouteDetail> => {
        const response = await api.post<SavedRouteDetail>('/users/me/saved-routes', payload);
        return response.data;
    },

    updateSavedRoute: async (savedRouteId: string, payload: SavedRouteWritePayload): Promise<SavedRouteDetail> => {
        const response = await api.put<SavedRouteDetail>(`/users/me/saved-routes/${savedRouteId}`, payload);
        return response.data;
    },

    renameSavedRoute: async (savedRouteId: string, title: string): Promise<SavedRouteSummary> => {
        const response = await api.patch<SavedRouteSummary>(
            `/users/me/saved-routes/${savedRouteId}/title`,
            { title },
        );
        return response.data;
    },

    deleteSavedRoute: async (savedRouteId: string): Promise<void> => {
        await api.delete(`/users/me/saved-routes/${savedRouteId}`);
    },
};
