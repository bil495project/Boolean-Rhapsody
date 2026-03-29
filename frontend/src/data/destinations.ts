import type { Destination } from '../components/DestinationCard';

// Extended destination with coordinates for map display
export interface MapDestination extends Destination {
    coordinates: [number, number]; // [lat, lng]
}

// Ankara, Türkiye destination data with coordinates
export const ankaraDestinations: MapDestination[] = [
    {
        id: '1',
        name: 'Anıtkabir',
        location: 'Çankaya, Ankara',
        image: 'https://images.unsplash.com/photo-1589561084283-930aa7b1ce50?w=800',
        rating: 4.9,
        priceLevel: 1,
        category: 'Historic Places',
        coordinates: [39.9254, 32.8369],
        description: 'Türkiye Cumhuriyeti\'nin kurucusu Mustafa Kemal Atatürk\'ün anıt mezarı. 1944-1953 yılları arasında inşa edilen bu muhteşem yapı, Türk mimarisinin en önemli eserlerinden biridir.',
        openingHours: '09:00 - 17:00',
        phone: '+90 312 231 7975',
        website: 'www.anitkabir.tsk.tr',
        reviewCount: 48520,
    },
    {
        id: '2',
        name: 'Kocatepe Mosque',
        location: 'Kocatepe, Ankara',
        image: 'https://images.unsplash.com/photo-1564769625905-50e93615e769?w=800',
        rating: 4.8,
        priceLevel: 1,
        category: 'Historic Places',
        coordinates: [39.9182, 32.8599],
    },
    {
        id: '3',
        name: 'Ankara Castle',
        location: 'Altındağ, Ankara',
        image: 'https://images.unsplash.com/photo-1590846083693-f23fdede3a7e?w=800',
        rating: 4.7,
        priceLevel: 1,
        category: 'Historic Places',
        coordinates: [39.9411, 32.8642],
    },
    {
        id: '4',
        name: 'Museum of Anatolian Civilizations',
        location: 'Ulus, Ankara',
        image: 'https://www.baskentankarameclisi.com/cdn/anadolumedeniyetlerimuzesi_1606817182.jpg',
        rating: 4.9,
        priceLevel: 2,
        category: 'Historic Places',
        coordinates: [39.9388, 32.8600],
    },
    {
        id: '5',
        name: 'Gençlik Parkı',
        location: 'Altındağ, Ankara',
        image: 'https://images.unsplash.com/photo-1568454537842-d933259bb258?w=800',
        rating: 4.5,
        priceLevel: 1,
        category: 'Parks',
        coordinates: [39.9369, 32.8524],
    },
    {
        id: '6',
        name: 'Atakule Tower',
        location: 'Çankaya, Ankara',
        image: 'https://images.adsttc.com/media/images/5d81/4a73/284d/d15e/5d00/0a86/newsletter/Atakule004.jpg?1568754274',
        rating: 4.6,
        priceLevel: 2,
        category: 'Landmark',
        coordinates: [39.8880, 32.8594],
    },
    {
        id: '7',
        name: 'Erimtan Archaeology Museum',
        location: 'Altındağ, Ankara',
        image: 'https://images.unsplash.com/photo-1554907984-15263bfd63bd?w=800',
        rating: 4.7,
        priceLevel: 2,
        category: 'Historic Places',
        coordinates: [39.9398, 32.8608],
    },
    {
        id: '8',
        name: 'Hamamönü',
        location: 'Altındağ, Ankara',
        image: 'https://images.unsplash.com/photo-1519677100203-a0e668c92439?w=800',
        rating: 4.6,
        priceLevel: 2,
        category: 'Historic Places',
        coordinates: [39.9362, 32.8670],
    },
    {
        id: '9',
        name: 'Beypazarı',
        location: 'Beypazarı, Ankara',
        image: 'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=800',
        rating: 4.8,
        priceLevel: 2,
        category: 'Culture',
        coordinates: [40.1673, 31.9215],
    },
    {
        id: '10',
        name: 'Eymir Lake',
        location: 'Gölbaşı, Ankara',
        image: 'https://images.unsplash.com/photo-1439066615861-d1af74d74000?w=800',
        rating: 4.5,
        priceLevel: 1,
        category: 'Parks',
        coordinates: [39.8205, 32.8042],
    },
    {
        id: '11',
        name: 'Seğmenler Park',
        location: 'Çankaya, Ankara',
        image: 'https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=800',
        rating: 4.4,
        priceLevel: 1,
        category: 'Park',
        coordinates: [39.8967, 32.8539],
    },
    {
        id: '12',
        name: 'CerModern',
        location: 'Altındağ, Ankara',
        image: 'https://images.unsplash.com/photo-1518998053901-5348d3961a04?w=800',
        rating: 4.6,
        priceLevel: 2,
        category: 'Museum',
        coordinates: [39.9325, 32.8575],
    },
];

// Cafe destinations in Ankara
export const cafeDestinations: MapDestination[] = [
    {
        id: 'cafe-1',
        name: 'Cafe Nero Tunalı',
        location: 'Tunalı Hilmi, Çankaya, Ankara',
        image: 'https://images.unsplash.com/photo-1554118811-1e0d58224f24?w=800',
        rating: 4.5,
        priceLevel: 2,
        category: 'Cafes & Desserts',
        coordinates: [39.9000, 32.8600],
    },
    {
        id: 'cafe-2',
        name: 'Starbucks Kızılay',
        location: 'Kızılay, Çankaya, Ankara',
        image: 'https://images.unsplash.com/photo-1453614512568-c4024d13c247?w=800',
        rating: 4.3,
        priceLevel: 2,
        category: 'Cafes & Desserts',
        coordinates: [39.9208, 32.8541],
    },
    {
        id: 'cafe-3',
        name: 'Kahve Dünyası Bahçelievler',
        location: 'Bahçelievler, Çankaya, Ankara',
        image: 'https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=800',
        rating: 4.4,
        priceLevel: 2,
        category: 'Cafes & Desserts',
        coordinates: [39.9167, 32.8333],
    },
    {
        id: 'cafe-4',
        name: 'MOC Tunalı',
        location: 'Tunalı Hilmi, Çankaya, Ankara',
        image: 'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?w=800',
        rating: 4.6,
        priceLevel: 3,
        category: 'Cafes & Desserts',
        coordinates: [39.8989, 32.8611],
    },
    {
        id: 'cafe-5',
        name: 'Espresso Lab',
        location: 'Gaziosmanpaşa, Çankaya, Ankara',
        image: 'https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=800',
        rating: 4.7,
        priceLevel: 3,
        category: 'Cafes & Desserts',
        coordinates: [39.9050, 32.8550],
    },
];

// All destinations combined
export const allDestinations: MapDestination[] = [...ankaraDestinations, ...cafeDestinations];

// Search destinations by query or category
export function searchDestinations(query?: string, category?: string): MapDestination[] {
    let results = allDestinations;

    if (category && category !== 'All') {
        results = results.filter(d => d.category.toLowerCase() === category.toLowerCase());
    }

    if (query) {
        const lowerQuery = query.toLowerCase();
        results = results.filter(d =>
            d.name.toLowerCase().includes(lowerQuery) ||
            d.location.toLowerCase().includes(lowerQuery) ||
            d.category.toLowerCase().includes(lowerQuery)
        );
    }

    return results;
}

// Get destination by ID
export function getDestinationById(id: string): MapDestination | undefined {
    return allDestinations.find(d => d.id === id);
}

export const categories = [
    'All',
    'Bars & Nightclubs',
    'Cafes & Desserts',
    'Historic Places',
    'Hotels',
    'Landmarks',
    'Parks',
    'Restaurants'
];
