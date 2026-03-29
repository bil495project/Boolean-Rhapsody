import { fallbackImages } from '../data/fallbackImages';

type PlaceImageInput = {
    id: string;
    name: string;
    category: string;
};

const PLACE_IMAGE_OVERRIDES: Array<{ keywords: string[]; image: string }> = [
    {
        keywords: ['anitkabir', 'anıtkabir'],
        image: 'https://images.unsplash.com/photo-1589561084283-930aa7b1ce50?w=1200',
    },
    {
        keywords: ['atakule'],
        image: 'https://images.adsttc.com/media/images/5d81/4a73/284d/d15e/5d00/0a86/newsletter/Atakule004.jpg?1568754274',
    },
    {
        keywords: ['kocatepe mosque', 'kocatepe camii', 'kocatepe mosque'],
        image: 'https://images.unsplash.com/photo-1564769625905-50e93615e769?w=1200',
    },
    {
        keywords: ['ankara castle', 'ankara kalesi'],
        image: 'https://images.unsplash.com/photo-1590846083693-f23fdede3a7e?w=1200',
    },
    {
        keywords: ['museum of anatolian civilizations', 'anadolu medeniyetleri muzesi', 'anadolu medeniyetleri müzesi'],
        image: 'https://www.baskentankarameclisi.com/cdn/anadolumedeniyetlerimuzesi_1606817182.jpg',
    },
    {
        keywords: ['hamamonu', 'hamamönü'],
        image: 'https://images.unsplash.com/photo-1519677100203-a0e668c92439?w=1200',
    },
    {
        keywords: ['eymir'],
        image: 'https://images.unsplash.com/photo-1439066615861-d1af74d74000?w=1200',
    },
    {
        keywords: ['segmenler', 'seğmenler'],
        image: 'https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=1200',
    },
    {
        keywords: ['genclik parki', 'gençlik parkı', 'genclik park'],
        image: 'https://images.unsplash.com/photo-1568454537842-d933259bb258?w=1200',
    },
    {
        keywords: ['cermodern'],
        image: 'https://images.unsplash.com/photo-1518998053901-5348d3961a04?w=1200',
    },
];

const FALLBACK_CATEGORY_ORDER = [
    'Historic Places',
    'Landmarks',
    'Parks',
    'Cafes & Desserts',
    'Restaurants',
    'Hotels',
    'Bars & Nightclubs',
];

function normalizeText(value: string): string {
    return value
        .toLocaleLowerCase('tr-TR')
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/ı/g, 'i')
        .replace(/[^a-z0-9]+/g, ' ')
        .trim();
}

function getDeterministicIndex(seed: string, length: number): number {
    let hash = 0;
    for (let i = 0; i < seed.length; i++) {
        hash = seed.charCodeAt(i) + ((hash << 5) - hash);
    }
    return Math.abs(hash) % length;
}

function getCategoryImages(category: string): string[] {
    const images = fallbackImages[category];
    if (images?.length) {
        return images;
    }

    for (const fallbackCategory of FALLBACK_CATEGORY_ORDER) {
        const fallback = fallbackImages[fallbackCategory];
        if (fallback?.length) {
            return fallback;
        }
    }

    return [];
}

export function getPlaceImage({ id, name, category }: PlaceImageInput): string {
    const normalizedName = normalizeText(name);

    const override = PLACE_IMAGE_OVERRIDES.find(({ keywords }) =>
        keywords.some((keyword) => normalizedName.includes(normalizeText(keyword))),
    );
    if (override) {
        return override.image;
    }

    const images = getCategoryImages(category);
    if (!images.length) {
        return 'https://images.unsplash.com/photo-1488085061387-422e29b40080?w=1200';
    }

    const index = getDeterministicIndex(`${category}:${normalizedName}:${id}`, images.length);
    return images[index];
}
