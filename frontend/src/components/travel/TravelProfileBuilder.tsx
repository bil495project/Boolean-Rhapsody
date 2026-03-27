import { useMemo, useState } from 'react';
import {
    Box,
    Button,
    Card,
    Checkbox,
    FormControl,
    FormLabel,
    Slider,
    Typography,
} from '@mui/joy';
import type { TravelProfile, TravelProfileAnswers } from '../../types/travelProfile';
import { buildRouteUserVector, hydrateTravelProfile } from '../../utils/travelProfile';

interface TravelProfileBuilderProps {
    initialValue?: Partial<TravelProfile>;
    title?: string;
    description?: string;
    confirmLabel?: string;
    cancelLabel?: string;
    showActions?: boolean;
    showNameFields?: boolean;
    requireName?: boolean;
    isSaving?: boolean;
    onConfirm?: (profile: TravelProfile) => void;
    onCancel?: () => void;
}

const alcoholOptions = [
    { value: 0, label: '🚫 No, not necessary' },
    { value: 1, label: '🍷 Yes, that works' },
];

const transportOptions = [
    { value: 0, label: '🚶 I usually walk' },
    { value: 0.33, label: '🚌 I prefer public transport' },
    { value: 0.66, label: '🔀 I mix different options' },
    { value: 1, label: '🚕 I use a car or taxi' },
];

const budgetOptions = [
    { value: 0, label: '💸 Budget-conscious' },
    { value: 0.5, label: '💼 Balanced' },
    { value: 1, label: '✨ Flexible' },
];

const tripLengthOptions = [
    { value: 0, label: '🌤️ Short getaway' },
    { value: 0.5, label: '🗓️ Mid-length trip' },
    { value: 1, label: '🧳 Long trip' },
];

const questionMeta: Array<{
    key: keyof TravelProfileAnswers;
    label: string;
    description: string;
    marks: Array<{ value: number; label: string }>;
}> = [
    {
        key: 'tempo',
        label: '⚡ How active should your route feel?',
        description: 'This controls whether the day feels relaxed or packed.',
        marks: [
            { value: 0, label: '🐢 Relaxed' },
            { value: 0.5, label: '⚖️ Balanced' },
            { value: 1, label: '🔥 Packed' },
        ],
    },
    {
        key: 'socialPreference',
        label: '🎉 How much do you enjoy social settings?',
        description: 'This influences lively streets, popular stops, and busy areas.',
        marks: [
            { value: 0, label: '🌙 Quiet' },
            { value: 0.5, label: '👋 Mixed' },
            { value: 1, label: '🎊 Lively' },
        ],
    },
    {
        key: 'naturePreference',
        label: '🌿 How much do you enjoy nature?',
        description: 'This shapes the mix of parks, viewpoints, and outdoor stops.',
        marks: [
            { value: 0, label: '🏙️ Urban' },
            { value: 0.5, label: '🌤️ Mixed' },
            { value: 1, label: '🌲 Green' },
        ],
    },
    {
        key: 'historyPreference',
        label: '🏛️ How appealing are historic places to you?',
        description: 'This increases the weight of museums, monuments, and heritage stops.',
        marks: [
            { value: 0, label: '📍 Light' },
            { value: 0.5, label: '📚 Some' },
            { value: 1, label: '🏺 Deep' },
        ],
    },
    {
        key: 'foodImportance',
        label: '🍽️ How important is the food experience?',
        description: 'This affects how much the route leans toward cafes and restaurants.',
        marks: [
            { value: 0, label: '⚡ Quick' },
            { value: 0.5, label: '🍜 Balanced' },
            { value: 1, label: '🍴 Main' },
        ],
    },
    {
        key: 'crowdPreference',
        label: '👥 How comfortable are you with crowds?',
        description: 'This helps shape whether the route feels busier or more relaxed.',
        marks: [
            { value: 0, label: '🌿 Calm' },
            { value: 0.5, label: '⚖️ Flexible' },
            { value: 1, label: '🎆 Buzz' },
        ],
    },
];

const TravelProfileBuilder = ({
    initialValue,
    title = 'Travel Profile',
    description = "Let's tailor route suggestions to your style.",
    confirmLabel = 'Use This Profile',
    cancelLabel = 'Cancel',
    showActions = true,
    showNameFields = true,
    requireName = showNameFields,
    isSaving = false,
    onConfirm,
    onCancel,
}: TravelProfileBuilderProps) => {
    const [profile, setProfile] = useState<TravelProfile>(() => hydrateTravelProfile(initialValue));

    const profileWithVector = useMemo(
        () => ({
            ...profile,
            userVector: buildRouteUserVector(profile),
        }),
        [profile],
    );

    const updateAnswer = <K extends keyof TravelProfileAnswers>(key: K, value: TravelProfileAnswers[K]) => {
        setProfile((prev) => ({
            ...prev,
            [key]: value,
        }));
    };

    const renderChoiceGrid = (
        options: Array<{ value: number; label: string }>,
        value: number,
        onSelect: (nextValue: number) => void,
        columns: any,
    ) => (
        <Box
            sx={{
                display: 'grid',
                gridTemplateColumns: columns,
                gap: 1,
            }}
        >
            {options.map((option) => (
                <Button
                    key={option.label}
                    variant={value === option.value ? 'solid' : 'outlined'}
                    color={value === option.value ? 'primary' : 'neutral'}
                    onClick={() => onSelect(option.value)}
                    sx={{
                        justifyContent: 'flex-start',
                        textAlign: 'left',
                        minHeight: 48,
                        whiteSpace: 'normal',
                    }}
                >
                    {option.label}
                </Button>
            ))}
        </Box>
    );

    return (
        <Card variant="outlined" sx={{ p: 3 }}>
            <Typography level="title-lg" sx={{ fontWeight: 700, mb: 0.5 }}>
                {title}
            </Typography>
            <Typography level="body-sm" sx={{ color: 'text.secondary', mb: 2.5 }}>
                {description}
            </Typography>

            {showNameFields && (
                <Box sx={{ display: 'grid', gap: 2, mb: 3 }}>
                    <FormControl>
                        <FormLabel>Profile name</FormLabel>
                        <input
                            value={profile.name}
                            onChange={(e) => setProfile((prev) => ({ ...prev, name: e.target.value }))}
                            style={{
                                width: '100%',
                                padding: '10px 12px',
                                borderRadius: '8px',
                                border: '1px solid var(--joy-palette-neutral-outlinedBorder)',
                                background: 'var(--joy-palette-background-surface)',
                                color: 'inherit',
                            }}
                        />
                    </FormControl>
                    <Checkbox
                        label="Set as default profile"
                        checked={profile.isDefault}
                        onChange={(e) => setProfile((prev) => ({ ...prev, isDefault: e.target.checked }))}
                    />
                </Box>
            )}

            <Box sx={{ display: 'grid', gap: 2.5 }}>
                {questionMeta.map((question) => (
                    <FormControl key={question.key} sx={{ mb: 0.5 }}>
                        <FormLabel>{question.label}</FormLabel>
                        <Typography level="body-xs" sx={{ color: 'text.tertiary', mb: 1 }}>
                            {question.description}
                        </Typography>
                        <Box
                            sx={{
                                width: '100%',
                                maxWidth: { xs: 260, sm: 320, md: 380 },
                                mx: 'auto',
                                pt: 0.5,
                            }}
                        >
                            <Slider
                                value={profile[question.key]}
                                min={0}
                                max={1}
                                step={0.25}
                                marks={question.marks}
                                onChange={(_event, value) => updateAnswer(question.key, value as number)}
                                sx={{
                                    '& .MuiSlider-markLabel': {
                                        fontSize: { xs: '0.68rem', sm: '0.74rem' },
                                        whiteSpace: 'nowrap',
                                    },
                                    '& .MuiSlider-rail, & .MuiSlider-track': {
                                        height: 4,
                                    },
                                    '& .MuiSlider-thumb': {
                                        width: 20,
                                        height: 20,
                                    },
                                }}
                            />
                        </Box>
                    </FormControl>
                ))}

                <FormControl>
                    <Box sx={{ height: 8 }} />
                    <FormLabel>🍷 Are you open to stops that include alcohol?</FormLabel>
                    {renderChoiceGrid(
                        alcoholOptions,
                        profile.alcoholPreference,
                        (nextValue) => updateAnswer('alcoholPreference', nextValue),
                        { xs: '1fr', sm: '1fr 1fr' },
                    )}
                </FormControl>

                <FormControl>
                    <FormLabel>🧭 How do you usually get around the city?</FormLabel>
                    {renderChoiceGrid(
                        transportOptions,
                        profile.transportStyle,
                        (nextValue) => updateAnswer('transportStyle', nextValue),
                        { xs: '1fr', sm: '1fr 1fr' },
                    )}
                </FormControl>

                <FormControl>
                    <FormLabel>💰 What is your budget style?</FormLabel>
                    {renderChoiceGrid(
                        budgetOptions,
                        profile.budgetLevel,
                        (nextValue) => updateAnswer('budgetLevel', nextValue),
                        { xs: '1fr', sm: '1fr 1fr 1fr' },
                    )}
                </FormControl>

                <FormControl>
                    <FormLabel>🗓️ How long is this trip?</FormLabel>
                    {renderChoiceGrid(
                        tripLengthOptions,
                        profile.tripLength,
                        (nextValue) => updateAnswer('tripLength', nextValue),
                        { xs: '1fr', sm: '1fr 1fr 1fr' },
                    )}
                </FormControl>
            </Box>

            {showActions && (
                <Box sx={{ display: 'flex', gap: 1.5, justifyContent: 'flex-end', mt: 3 }}>
                    {onCancel && (
                        <Button variant="outlined" color="neutral" onClick={onCancel}>
                            {cancelLabel}
                        </Button>
                    )}
                    {onConfirm && (
                        <Button
                            onClick={() => onConfirm(profileWithVector)}
                            loading={isSaving}
                            disabled={requireName && !profile.name.trim()}
                        >
                            {confirmLabel}
                        </Button>
                    )}
                </Box>
            )}
        </Card>
    );
};

export default TravelProfileBuilder;
