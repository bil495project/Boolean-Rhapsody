const CATEGORY_LABELS: Record<string, string> = {
    'historic places': 'Historic Places',
    'cafes & desserts': 'Cafes & Desserts',
    'restaurants': 'Restaurants',
    'parks': 'Parks',
    'landmarks': 'Landmarks',
    'bars & nightclubs': 'Bars & Nightclubs',
    'hotels': 'Hotels',
};

function normalizePrimaryType(primaryType: string | undefined | null): string {
    return primaryType?.trim().toLowerCase() || '';
}

export function mapPrimaryTypeToCategory(primaryType: string | undefined | null): string {
    const normalizedType = normalizePrimaryType(primaryType);

    if (!normalizedType) return 'Landmarks';

    const explicitCategory = CATEGORY_LABELS[normalizedType];
    if (explicitCategory) {
        return explicitCategory;
    }

    if (
        normalizedType.includes('cafe') ||
        normalizedType.includes('coffee') ||
        normalizedType.includes('dessert') ||
        normalizedType.includes('bakery') ||
        normalizedType.includes('confectionery') ||
        normalizedType.includes('bagel')
    ) {
        return 'Cafes & Desserts';
    }

    if (
        normalizedType.includes('bar') ||
        normalizedType.includes('nightclub') ||
        normalizedType.includes('night_club') ||
        normalizedType === 'pub' ||
        normalizedType.includes('club')
    ) {
        return 'Bars & Nightclubs';
    }

    if (
        normalizedType.includes('hotel') ||
        normalizedType.includes('lodging') ||
        normalizedType.includes('guest_house')
    ) {
        return 'Hotels';
    }

    if (
        normalizedType.includes('park') ||
        normalizedType.includes('natural_feature') ||
        normalizedType.includes('campground') ||
        normalizedType.includes('botanical_garden')
    ) {
        return 'Parks';
    }

    if (
        normalizedType.includes('museum') ||
        normalizedType.includes('church') ||
        normalizedType.includes('mosque') ||
        normalizedType.includes('historical') ||
        normalizedType.includes('history') ||
        normalizedType.includes('tourist_attraction') ||
        normalizedType.includes('synagogue') ||
        normalizedType.includes('ruins')
    ) {
        return 'Historic Places';
    }

    if (
        normalizedType.includes('landmark') ||
        normalizedType.includes('city_hall') ||
        normalizedType.includes('stadium') ||
        normalizedType.includes('amusement_park') ||
        normalizedType.includes('aquarium') ||
        normalizedType.includes('zoo') ||
        normalizedType.includes('art')
    ) {
        return 'Landmarks';
    }

    if (
        normalizedType.includes('restaurant') ||
        normalizedType.includes('food') ||
        normalizedType.includes('meal_takeaway') ||
        normalizedType.includes('meal_delivery')
    ) {
        return 'Restaurants';
    }

    return 'Landmarks';
}

export function mapTypesStringToCategory(typesString: string | undefined | null): string {
    const primaryType = typesString
        ?.split(',')
        .map((type) => type.trim())
        .find(Boolean);

    return mapPrimaryTypeToCategory(primaryType);
}

export function mapTypesArrayToCategory(types: string[] | undefined): string {
    const primaryType = types?.find((type) => type?.trim()) || '';
    return mapPrimaryTypeToCategory(primaryType);
}
