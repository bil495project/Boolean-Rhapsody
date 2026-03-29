import axios from 'axios';
import { getStoredToken } from './userService';
import type { MapDestination } from '../data/destinations';
import { mapTypesStringToCategory } from '../utils/placeCategory';
import { getPlaceImage } from '../utils/placeImage';

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

export interface PlaceResponse {
    id: string;
    name: string;
    formattedAddress: string;
    latitude: number;
    longitude: number;
    types: string;
    ratingScore?: number;
    ratingCount?: number;
    priceLevel?: string;
    businessStatus?: string;
}

export interface Page<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
}

const DEFAULT_FETCH_PAGE_SIZE = 1000;

function mapPriceLevelToNumber(priceLevel: string | undefined | null): 1 | 2 | 3 | 4 {
    if (!priceLevel) return 1; // Default
    switch (priceLevel) {
        case 'PRICE_LEVEL_INEXPENSIVE': return 1;
        case 'PRICE_LEVEL_MODERATE': return 2;
        case 'PRICE_LEVEL_EXPENSIVE': return 3;
        case 'PRICE_LEVEL_VERY_EXPENSIVE': return 4;
        default: return 1;
    }
}

export const mapPlaceResponseToDestination = (place: PlaceResponse): MapDestination => {
    const category = mapTypesStringToCategory(place.types);
    const image = getPlaceImage({
        id: place.id,
        name: place.name,
        category,
    });
    
    return {
        id: place.id,
        name: place.name,
        location: place.formattedAddress,
        image,
        rating: place.ratingScore || 4.0, // Default rating if missing
        priceLevel: mapPriceLevelToNumber(place.priceLevel),
        category,
        coordinates: [place.latitude, place.longitude],
        reviewCount: place.ratingCount,
        businessStatus: place.businessStatus,
        types: place.types
            ? place.types.split(',').map((type) => type.trim()).filter(Boolean)
            : [],
    };
};

export const placeService = {
    getAllPlaces: async (page = 0, size = 15000, category?: string): Promise<MapDestination[]> => {
        if (size <= 0) {
            return [];
        }

        const pageSize = Math.min(size, DEFAULT_FETCH_PAGE_SIZE);
        let url = `/places?page=${page}&size=${pageSize}`;
        if (category && category !== 'All') {
            // Note: Our DB might not perfectly match these typed filters, but we can pass 'type' if needed
            // The backend endpoint supports '?type='
        }

        const firstResponse = await api.get<Page<PlaceResponse>>(url);
        const pages: PlaceResponse[] = [...firstResponse.data.content];
        const targetCount = Math.min(size, firstResponse.data.totalElements);

        for (let nextPage = page + 1; nextPage < firstResponse.data.totalPages && pages.length < targetCount; nextPage++) {
            const nextResponse = await api.get<Page<PlaceResponse>>(`/places?page=${nextPage}&size=${pageSize}`);
            pages.push(...nextResponse.data.content);
        }

        return pages
            .slice(0, targetCount)
            .map(mapPlaceResponseToDestination);
    },

    searchPlaces: async (query: string, page = 0, size = 100): Promise<MapDestination[]> => {
        const response = await api.get<Page<PlaceResponse>>(`/places/search?name=${encodeURIComponent(query)}&page=${page}&size=${size}`);
        return response.data.content.map(mapPlaceResponseToDestination);
    },
    
    getPlaceById: async (id: string): Promise<MapDestination> => {
        const response = await api.get<PlaceResponse>(`/places/${id}`);
        return mapPlaceResponseToDestination(response.data);
    },

    getBulkPlaces: async (ids: string[]): Promise<MapDestination[]> => {
        if (!ids.length) return [];
        const response = await api.post<PlaceResponse[]>('/places/bulk', { ids });
        return response.data.map(mapPlaceResponseToDestination);
    }
};
