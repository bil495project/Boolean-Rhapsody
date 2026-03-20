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

const FALLBACK_IMAGES: Record<string, string[]> = {
    'Historic Places': [
        'https://images.unsplash.com/photo-1548013146-72479768bbaa?w=800',
        'https://images.unsplash.com/photo-1589561084283-930aa7b1ce50?w=800',
        'https://images.unsplash.com/photo-1542667593-979921245e54?w=800',
        'https://images.unsplash.com/photo-1527838832700-5059252407fa?w=800',
        'https://images.unsplash.com/photo-1564507595952-46ee5707f502?w=800',
        'https://images.unsplash.com/photo-1570168007204-dfb528c6958f?w=800',
        'https://images.unsplash.com/photo-1599424423916-76a63d91796a?w=800',
        'https://images.unsplash.com/photo-1533154683836-84ea7a0bc310?w=800',
        'https://images.unsplash.com/photo-1512100356956-c1ebef634020?w=800',
        'https://images.unsplash.com/photo-1565011523534-747a8601f10a?w=800',
        'https://images.unsplash.com/photo-1598330106281-04d44ca68ad9?w=800',
        'https://images.unsplash.com/photo-1620332372374-f108c53d2e03?w=800',
        'https://images.unsplash.com/photo-1546412414-e188cd80ae55?w=800',
        'https://images.unsplash.com/photo-1582266327725-efd5581446fe?w=800',
        'https://images.unsplash.com/photo-1518709766631-a6a7f459ba1c?w=800',
        'https://images.unsplash.com/photo-1449156059431-1f9538c82305?w=800',
        'https://images.unsplash.com/photo-1541339907198-e08759dfc12f?w=800',
        'https://images.unsplash.com/photo-1470770841072-f978cf4d019e?w=800',
        'https://images.unsplash.com/photo-1477959858617-67f85cf4f1df?w=800',
        'https://images.unsplash.com/photo-1495562547156-230d23588501?w=800',
    ],
    'Cafes & Desserts': [
        'https://images.unsplash.com/photo-1554118811-1e0d58224f24?w=800',
        'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?w=800',
        'https://images.unsplash.com/photo-1551024601-bec78aea704b?w=800',
        'https://images.unsplash.com/photo-1509440159596-0249088772ff?w=800',
        'https://images.unsplash.com/photo-1541167760496-162955ed8521?w=800',
        'https://images.unsplash.com/photo-1525610553991-2bede1a233e9?w=800',
        'https://images.unsplash.com/photo-1488477181946-6428a0291777?w=800',
        'https://images.unsplash.com/photo-1544787210-2213d44ad53e?w=800',
        'https://images.unsplash.com/photo-1517433670267-24bb3360d517?w=800',
        'https://images.unsplash.com/photo-1521017432531-fbd92d744264?w=800',
        'https://images.unsplash.com/photo-1507133750040-4a8f571c08af?w=800',
        'https://images.unsplash.com/photo-1511920170033-f8396924c34b?w=800',
        'https://images.unsplash.com/photo-1442512595331-e89e73853f31?w=800',
        'https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=800',
        'https://images.unsplash.com/photo-1506372023823-741c83b836fe?w=800',
        'https://images.unsplash.com/photo-1497933321188-941f9ad36b17?w=800',
        'https://images.unsplash.com/photo-1524350300060-da8217bbba69?w=800',
        'https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?w=800',
        'https://images.unsplash.com/photo-1508215885820-4585e56135c8?w=800',
        'https://images.unsplash.com/photo-1494346480775-936a9f0d0877?w=800',
    ],
    'Parks': [
        'https://images.unsplash.com/photo-1568454537842-d933259bb258?w=800',
        'https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=800',
        'https://images.unsplash.com/photo-1470058869958-2a77da4460b2?w=800',
        'https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?w=800',
        'https://images.unsplash.com/photo-1558239027-d296612a7224?w=800',
        'https://images.unsplash.com/photo-1519331379826-f10be5486c6f?w=800',
        'https://images.unsplash.com/photo-1433086177607-6c35c246f40c?w=800',
        'https://images.unsplash.com/photo-1507502707541-f369a3b18502?w=800',
        'https://images.unsplash.com/photo-1490750967868-886a502c5f10?w=800',
        'https://images.unsplash.com/photo-1502082553048-f009c37129b9?w=800',
        'https://images.unsplash.com/photo-1549444143-233777f98d40?w=800',
        'https://images.unsplash.com/photo-1585829319231-8bc6218fdba9?w=800',
        'https://images.unsplash.com/photo-1536746803623-cee8709ee3b2?w=800',
        'https://images.unsplash.com/photo-1496442226666-8d4d0e62e6e9?w=800',
        'https://images.unsplash.com/photo-1523712999610-f77fbcfc3843?w=800',
        'https://images.unsplash.com/photo-1518115276850-2dca1c0e3576?w=800',
        'https://images.unsplash.com/photo-1470252649378-9c29740c9fa8?w=800',
        'https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=800',
        'https://images.unsplash.com/photo-1431324155629-1a6eda1eedbc?w=800',
        'https://images.unsplash.com/photo-1414441002220-304bc5753164?w=800',
    ],
    'Landmarks': [
        'https://images.unsplash.com/photo-1518391846015-55a9cc003b25?w=800',
        'https://images.unsplash.com/photo-1503917988258-f87a78e3c995?w=800',
        'https://images.unsplash.com/photo-1541339907198-e08759dfc12f?w=800',
        'https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?w=800',
        'https://images.unsplash.com/photo-1470770841072-f978cf4d019e?w=800',
        'https://images.unsplash.com/photo-1477959858617-67f85cf4f1df?w=800',
        'https://images.unsplash.com/photo-1449156059431-1f9538c82305?w=800',
        'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=800',
        'https://images.unsplash.com/photo-1519681393784-d120267933ba?w=800',
        'https://images.unsplash.com/photo-1533105079780-92b9be482077?w=800',
        'https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=800',
        'https://images.unsplash.com/photo-1518709766631-a6a7f459ba1c?w=800',
        'https://images.unsplash.com/photo-1444723121867-7a241cacace9?w=800',
        'https://images.unsplash.com/photo-1464303254921-2679234b6b19?w=800',
        'https://images.unsplash.com/photo-1514565131-fce0801e5785?w=800',
        'https://images.unsplash.com/photo-1502602898657-3e91760cbb34?w=800',
        'https://images.unsplash.com/photo-1547633044-8393c896987f?w=800',
        'https://images.unsplash.com/photo-1534067783941-51c9c23eccfd?w=800',
        'https://images.unsplash.com/photo-1512413316925-fd4b93f31521?w=800',
        'https://images.unsplash.com/photo-1495562547156-230d23588501?w=800',
    ],
    'Bars & Nightclubs': [
        'https://images.unsplash.com/photo-1514525253361-bee8718a3427?w=800',
        'https://images.unsplash.com/photo-1514362545857-3bc16c4c7d1b?w=800',
        'https://images.unsplash.com/photo-1574096079513-d8259312b785?w=800',
        'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800',
        'https://images.unsplash.com/photo-1566417713040-08b5ca4a0e91?w=800',
        'https://images.unsplash.com/photo-1516280440614-37939bbacd81?w=800',
        'https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=800',
        'https://images.unsplash.com/photo-1551024506-0bccd828d307?w=800',
        'https://images.unsplash.com/photo-1572116469696-31de0f17cc34?w=800',
        'https://images.unsplash.com/photo-1538481199705-c710c4e965fc?w=800',
        'https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=800',
        'https://images.unsplash.com/photo-1510812431401-41d2bd2722f3?w=800',
        'https://images.unsplash.com/photo-1543007630-9710e4a00a20?w=800',
        'https://images.unsplash.com/photo-1481833761820-0509d3217039?w=800',
        'https://images.unsplash.com/photo-1541535881962-3bb39e557e04?w=800',
        'https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=800',
        'https://images.unsplash.com/photo-1543007631-2f3ce3463ea1?w=800',
        'https://images.unsplash.com/photo-1511795409834-ef04bbd61622?w=800',
        'https://images.unsplash.com/photo-1514361044390-50267e7168fc?w=800',
        'https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?w=800',
    ],
    'Restaurants': [
        'https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=800',
        'https://images.unsplash.com/photo-1513104890138-7c749659a591?w=800',
        'https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=800',
        'https://images.unsplash.com/photo-1553621042-f6e147245754?w=800',
        'https://images.unsplash.com/photo-1414235077428-338989a2e8c0?w=800',
        'https://images.unsplash.com/photo-1473093226795-af9932fe5856?w=800',
        'https://images.unsplash.com/photo-1544025162-d76694265947?w=800',
        'https://images.unsplash.com/photo-1490645935967-10de6ba17061?w=800',
        'https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=800',
        'https://images.unsplash.com/photo-1556910103-1c02745aae4d?w=800',
        'https://images.unsplash.com/photo-1528605105345-5627bade0022?w=800',
        'https://images.unsplash.com/photo-1552566626-52f8b828add9?w=800',
        'https://images.unsplash.com/photo-1555396273-367ea474fb73?w=800',
        'https://images.unsplash.com/photo-1559339352-11d035aa65de?w=800',
        'https://images.unsplash.com/photo-1467003909585-2f8a72700288?w=800',
        'https://images.unsplash.com/photo-1514933651103-005eec06c04b?w=800',
        'https://images.unsplash.com/photo-1515003197210-e0cd71810b5f?w=800',
        'https://images.unsplash.com/photo-1443675217430-671e3532f641?w=800',
        'https://images.unsplash.com/photo-1504754524776-8f4f37790ca0?w=800',
        'https://images.unsplash.com/photo-1511690656952-34342bb7c2f2?w=800',
    ],
    'Hotels': [
        'https://images.unsplash.com/photo-1566073771259-6a8506099945?w=800',
        'https://images.unsplash.com/photo-1582719508461-905c673771fd?w=800',
        'https://images.unsplash.com/photo-1540518614846-7eded433c457?w=800',
        'https://images.unsplash.com/photo-1517840901100-8179e982ad41?w=800',
        'https://images.unsplash.com/photo-1522771739844-6a9f6d5f14af?w=800',
        'https://images.unsplash.com/photo-1445019980597-93fa8acb246c?w=800',
        'https://images.unsplash.com/photo-1571003123894-1f0594d2b5d9?w=800',
        'https://images.unsplash.com/photo-1542314831-068cd1dbfeeb?w=800',
        'https://images.unsplash.com/photo-1564501049412-61c2a3083791?w=800',
        'https://images.unsplash.com/photo-1520250497591-112f2f40a3f4?w=800',
        'https://images.unsplash.com/photo-1566665797739-1674de7a421a?w=800',
        'https://images.unsplash.com/photo-1551882547-ff43c61f3c3a?w=800',
        'https://images.unsplash.com/photo-1596394516093-501ba68a0ba6?w=800',
        'https://images.unsplash.com/photo-1578683062331-624344d7f8c2?w=800',
        'https://images.unsplash.com/photo-1590073844006-33379778ae09?w=800',
        'https://images.unsplash.com/photo-1582719478250-c89cae4df85b?w=800',
        'https://images.unsplash.com/photo-1512918728675-ed5a9ecdebfd?w=800',
        'https://images.unsplash.com/photo-1551632436-cbf8dd35adfa?w=800',
        'https://images.unsplash.com/photo-1561501900-3701fa6a0f64?w=800',
        'https://images.unsplash.com/photo-1590490360182-c33d57733427?w=800',
    ],
    'Default': [
        'https://images.unsplash.com/photo-1488085061387-422e29b40080?w=800',
    ],
};

function getPlaceImage(placeId: string, category: string): string {
    const images = FALLBACK_IMAGES[category] || FALLBACK_IMAGES['Default'];
    // Deterministic selection based on placeId
    let hash = 0;
    for (let i = 0; i < placeId.length; i++) {
        hash = placeId.charCodeAt(i) + ((hash << 5) - hash);
    }
    const index = Math.abs(hash) % images.length;
    return images[index];
}

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
    const image = getPlaceImage(place.id, category);
    
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
