import axios from 'axios';
import { getStoredToken } from './userService';
import type { MapDestination } from '../data/destinations';

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

const FALLBACK_IMAGES: Record<string, string> = {
    'Historic Places': 'https://images.unsplash.com/photo-1589561084283-930aa7b1ce50?w=800',
    'Cafes & Desserts': 'https://images.unsplash.com/photo-1554118811-1e0d58224f24?w=800',
    'Parks': 'https://images.unsplash.com/photo-1568454537842-d933259bb258?w=800',
    'Landmarks': 'https://images.unsplash.com/photo-1590846083693-f23fdede3a7e?w=800',
    'Bars & Nightclubs': 'https://images.unsplash.com/photo-1514525253361-bee8718a3427?w=800',
    'Restaurants': 'https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=800',
    'Hotels': 'https://images.unsplash.com/photo-1566073771259-6a8506099945?w=800',
    'Default': 'https://images.unsplash.com/photo-1488085061387-422e29b40080?w=800',
};

function mapTypeToCategory(typesString: string | undefined | null): string {
    if (!typesString) return 'Landmarks';
    const lowerTypes = typesString.toLowerCase();
    
    // 1. Check for explicit category markers (prepended by loader)
    if (lowerTypes.startsWith('historic places')) return 'Historic Places';
    if (lowerTypes.startsWith('cafes & desserts')) return 'Cafes & Desserts';
    if (lowerTypes.startsWith('restaurants')) return 'Restaurants';
    if (lowerTypes.startsWith('parks')) return 'Parks';
    if (lowerTypes.startsWith('landmarks')) return 'Landmarks';
    if (lowerTypes.startsWith('bars & nightclubs')) return 'Bars & Nightclubs';
    if (lowerTypes.startsWith('hotels')) return 'Hotels';

    // 2. Fallback to keyword matching
    if (lowerTypes.includes('cafe') || lowerTypes.includes('dessert')) return 'Cafes & Desserts';
    if (lowerTypes.includes('restaurant') || lowerTypes.includes('food')) return 'Restaurants';
    if (lowerTypes.includes('park')) return 'Parks';
    if (lowerTypes.includes('museum') || lowerTypes.includes('art')) return 'Landmarks';
    if (lowerTypes.includes('history') || lowerTypes.includes('historical') || lowerTypes.includes('mosque')) return 'Historic Places';
    if (lowerTypes.includes('bar') || lowerTypes.includes('nightclub') || lowerTypes.includes('club')) return 'Bars & Nightclubs';
    if (lowerTypes.includes('hotel') || lowerTypes.includes('lodging')) return 'Hotels';
    
    return 'Landmarks';
}

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
    const category = mapTypeToCategory(place.types);
    const image = FALLBACK_IMAGES[category] || FALLBACK_IMAGES['Default'];
    
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
    };
};

export const placeService = {
    getAllPlaces: async (page = 0, size = 15000, category?: string): Promise<MapDestination[]> => {
        let url = `/places?page=${page}&size=${size}`;
        if (category && category !== 'All') {
            // Note: Our DB might not perfectly match these typed filters, but we can pass 'type' if needed
            // The backend endpoint supports '?type='
        }
        const response = await api.get<Page<PlaceResponse>>(url);
        return response.data.content.map(mapPlaceResponseToDestination);
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
