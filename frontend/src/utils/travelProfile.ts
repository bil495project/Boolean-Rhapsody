import type { TravelProfile, TravelProfileAnswers } from '../types/travelProfile';

export const PROFILE_WEIGHT_KEYS = [
    'weight_parkVeSeyirNoktalari',
    'weight_geceHayati',
    'weight_restoranToleransi',
    'weight_landmark',
    'weight_dogalAlanlar',
    'weight_tarihiAlanlar',
    'weight_kafeTatli',
    'weight_toplamPoiYogunlugu',
    'weight_sparsity',
    'weight_hotelCenterBias',
    'weight_butceSeviyesi',
] as const;

export type ProfileWeightKey = (typeof PROFILE_WEIGHT_KEYS)[number];

export function clamp01(value: number): number {
    return Math.max(0, Math.min(1, value));
}

export function toVectorString(value: number): string {
    return clamp01(value).toFixed(3);
}

export function defaultTravelProfileAnswers(): TravelProfileAnswers {
    return {
        tempo: 0.5,
        socialPreference: 0.5,
        naturePreference: 0.5,
        historyPreference: 0.5,
        foodImportance: 0.5,
        alcoholPreference: 0,
        transportStyle: 0.33,
        budgetLevel: 0.5,
        tripLength: 0.5,
        crowdPreference: 0.5,
    };
}

export function defaultTravelProfile(): TravelProfile {
    return {
        name: '',
        isDefault: false,
        ...defaultTravelProfileAnswers(),
        userVector: buildRouteUserVector(defaultTravelProfileAnswers()),
    };
}

export function buildRouteUserVector(answers: TravelProfileAnswers): Record<string, string> {
    const tempo = clamp01(answers.tempo);
    const social = clamp01(answers.socialPreference);
    const nature = clamp01(answers.naturePreference);
    const history = clamp01(answers.historyPreference);
    const food = clamp01(answers.foodImportance);
    const alcohol = clamp01(answers.alcoholPreference);
    const transport = clamp01(answers.transportStyle);
    const budget = clamp01(answers.budgetLevel);
    const tripLength = clamp01(answers.tripLength);
    const crowd = clamp01(answers.crowdPreference);

    const parkVeSeyirNoktalari = clamp01(
        0.10 + (nature * 0.52) + ((1 - crowd) * 0.12) + (tripLength * 0.08),
    );
    const geceHayati = clamp01(
        0.04 + (social * 0.38) + (alcohol * 0.42) + (crowd * 0.10) + (tempo * 0.06),
    );
    const restoranToleransi = clamp01(
        0.12 + (food * 0.66) + (tripLength * 0.08) + (social * 0.06),
    );
    const landmark = clamp01(
        0.18 + (history * 0.24) + (crowd * 0.10) + (tempo * 0.05) + ((1 - nature) * 0.04),
    );
    const dogalAlanlar = clamp01(
        0.08 + (nature * 0.70) + ((1 - crowd) * 0.08),
    );
    const tarihiAlanlar = clamp01(
        0.10 + (history * 0.74) + ((1 - tempo) * 0.06),
    );
    const kafeTatli = clamp01(
        0.08 + (food * 0.38) + (social * 0.14) + ((1 - tempo) * 0.14),
    );
    const toplamPoiYogunlugu = clamp01(
        0.18 + (tempo * 0.42) + (tripLength * 0.32) + (social * 0.06),
    );
    const sparsity = clamp01(
        0.12 + (transport * 0.42) + (tripLength * 0.16) + (nature * 0.08) + ((1 - tempo) * 0.10),
    );
    const hotelCenterBias = clamp01(
        0.88 - (transport * 0.72) + ((1 - crowd) * 0.05) + ((1 - tempo) * 0.03),
    );

    return {
        weight_parkVeSeyirNoktalari: toVectorString(parkVeSeyirNoktalari),
        weight_geceHayati: toVectorString(geceHayati),
        weight_restoranToleransi: toVectorString(restoranToleransi),
        weight_landmark: toVectorString(landmark),
        weight_dogalAlanlar: toVectorString(dogalAlanlar),
        weight_tarihiAlanlar: toVectorString(tarihiAlanlar),
        weight_kafeTatli: toVectorString(kafeTatli),
        weight_toplamPoiYogunlugu: toVectorString(toplamPoiYogunlugu),
        weight_sparsity: toVectorString(sparsity),
        weight_hotelCenterBias: toVectorString(hotelCenterBias),
        weight_butceSeviyesi: toVectorString(budget),
    };
}

export function normalizeUserVector(rawVector: Record<string, string | number | boolean> | undefined): Record<string, string> {
    const normalized: Record<string, string> = {};
    Object.entries(rawVector ?? {}).forEach(([key, value]) => {
        if (value === null || value === undefined) {
            return;
        }
        if (typeof value === 'number') {
            normalized[key] = Number.isFinite(value) ? String(value) : '0';
            return;
        }
        if (typeof value === 'boolean') {
            normalized[key] = value ? '1' : '0';
            return;
        }
        normalized[key] = value;
    });
    return normalized;
}

export function hydrateTravelProfile(profile: Partial<TravelProfile> | undefined): TravelProfile {
    const answers = defaultTravelProfileAnswers();
    const hydrated: TravelProfile = {
        ...answers,
        ...profile,
        name: profile?.name ?? '',
        isDefault: Boolean(profile?.isDefault),
        userVector: profile?.userVector
            ? normalizeUserVector(profile.userVector)
            : buildRouteUserVector({
                tempo: profile?.tempo ?? answers.tempo,
                socialPreference: profile?.socialPreference ?? answers.socialPreference,
                naturePreference: profile?.naturePreference ?? answers.naturePreference,
                historyPreference: profile?.historyPreference ?? answers.historyPreference,
                foodImportance: profile?.foodImportance ?? answers.foodImportance,
                alcoholPreference: profile?.alcoholPreference ?? answers.alcoholPreference,
                transportStyle: profile?.transportStyle ?? answers.transportStyle,
                budgetLevel: profile?.budgetLevel ?? answers.budgetLevel,
                tripLength: profile?.tripLength ?? answers.tripLength,
                crowdPreference: profile?.crowdPreference ?? answers.crowdPreference,
            }),
    };
    return hydrated;
}

export function summarizeTravelProfile(profile: Partial<TravelProfile> | undefined): string {
    if (!profile) {
        return 'Not ready';
    }
    const highlights: string[] = [];
    if ((profile.historyPreference ?? 0) >= 0.65) highlights.push('history');
    if ((profile.naturePreference ?? 0) >= 0.65) highlights.push('nature');
    if ((profile.foodImportance ?? 0) >= 0.65) highlights.push('food');
    if ((profile.socialPreference ?? 0) >= 0.65) highlights.push('social');
    if (highlights.length === 0) {
        return 'Balanced route profile';
    }
    return `${highlights.join(', ')} focused`;
}
