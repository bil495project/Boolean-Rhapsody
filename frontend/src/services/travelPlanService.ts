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

export interface TravelPlanResponse {
    id: string;
    selectedPlaceIds: string[];
    createdAt: number;
}

export interface CreateTravelPlanRequest {
    selectedPlaceIds: string[];
}

export const travelPlanService = {
    getAllPlans: async (): Promise<TravelPlanResponse[]> => {
        const response = await api.get<TravelPlanResponse[]>('/users/me/plans');
        return response.data;
    },

    createPlan: async (request: CreateTravelPlanRequest): Promise<TravelPlanResponse> => {
        const response = await api.post<TravelPlanResponse>('/users/me/plans/new', request);
        return response.data;
    },

    updatePlan: async (planId: string, request: CreateTravelPlanRequest): Promise<TravelPlanResponse> => {
        const response = await api.put<TravelPlanResponse>(`/users/me/plans/${planId}`, request);
        return response.data;
    },

    deletePlan: async (planId: string): Promise<void> => {
        await api.delete(`/users/me/plans/${planId}`);
    }
};
