export interface TravelProfileAnswers {
    tempo: number;
    socialPreference: number;
    naturePreference: number;
    historyPreference: number;
    foodImportance: number;
    alcoholPreference: number;
    transportStyle: number;
    budgetLevel: number;
    tripLength: number;
    crowdPreference: number;
}

export interface TravelProfile extends TravelProfileAnswers {
    id?: string;
    name: string;
    isDefault: boolean;
    userVector?: Record<string, string | number | boolean>;
}
