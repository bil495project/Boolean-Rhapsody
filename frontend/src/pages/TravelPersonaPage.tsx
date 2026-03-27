import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    Box,
    Card,
    Typography,
    Button,
    Stepper,
    Step,
    StepIndicator,
    Checkbox,
    Radio,
    RadioGroup,
    Sheet,
    Avatar,
    IconButton,
} from '@mui/joy';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import CheckIcon from '@mui/icons-material/Check';
import PhotoCameraIcon from '@mui/icons-material/PhotoCamera';
import Header from '../components/Header';
import RoadrunnerBirdLogo from '../components/RoadrunnerBirdLogo';
import { useAppDispatch, useAppSelector } from '../store/hooks';
import { updateTravelPersona, updateUser, clearNewSignup, saveTravelPersona } from '../store/authSlice';
import type { TravelPersona } from '../store/authSlice';

const travelStyles = [
    { id: 'adventure', label: 'Adventure', emoji: '🏔️' },
    { id: 'relaxation', label: 'Relaxation', emoji: '🏖️' },
    { id: 'culture', label: 'Culture', emoji: '🏛️' },
    { id: 'food', label: 'Food & Cuisine', emoji: '🍽️' },
    { id: 'budget', label: 'Budget Travel', emoji: '💰' },
    { id: 'luxury', label: 'Luxury', emoji: '✨' },
];

const interests = [
    { id: 'museums', label: 'Museums', emoji: '🖼️' },
    { id: 'nature', label: 'Nature', emoji: '🌿' },
    { id: 'history', label: 'History', emoji: '📜' },
    { id: 'photography', label: 'Photography', emoji: '📷' },
    { id: 'local-food', label: 'Local Food', emoji: '🥘' },
    { id: 'nightlife', label: 'Nightlife', emoji: '🌙' },
    { id: 'shopping', label: 'Shopping', emoji: '🛍️' },
    { id: 'architecture', label: 'Architecture', emoji: '🏗️' },
];

const travelFrequencies = [
    { id: 'first-timer', label: 'First-time traveler', description: 'This is my first adventure!' },
    { id: 'occasional', label: 'Occasional traveler', description: '1-2 trips per year' },
    { id: 'frequent', label: 'Frequent traveler', description: '3+ trips per year' },
];

const pacePrefences = [
    { id: 'packed', label: 'Packed itinerary', description: 'See as much as possible!' },
    { id: 'balanced', label: 'Balanced', description: 'Mix of activities and downtime' },
    { id: 'relaxed', label: 'Relaxed exploration', description: 'Take it slow and enjoy' },
];

const TravelPersonaPage = () => {
    const [activeStep, setActiveStep] = useState(0);
    const [selectedStyles, setSelectedStyles] = useState<string[]>([]);
    const [selectedInterests, setSelectedInterests] = useState<string[]>([]);
    const [frequency, setFrequency] = useState('');
    const [pace, setPace] = useState('');
    const [avatarPreview, setAvatarPreview] = useState<string | null>(null);

    const navigate = useNavigate();
    const dispatch = useAppDispatch();
    const { user } = useAppSelector((state) => state.auth);

    const steps = ['Travel Style', 'Interests', 'Frequency', 'Pace', 'Photo'];

    const handleStyleToggle = (styleId: string) => {
        setSelectedStyles((prev) =>
            prev.includes(styleId)
                ? prev.filter((id) => id !== styleId)
                : [...prev, styleId]
        );
    };

    const handleInterestToggle = (interestId: string) => {
        setSelectedInterests((prev) =>
            prev.includes(interestId)
                ? prev.filter((id) => id !== interestId)
                : [...prev, interestId]
        );
    };

    const handleAvatarUpload = (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        if (file) {
            const reader = new FileReader();
            reader.onloadend = () => {
                setAvatarPreview(reader.result as string);
            };
            reader.readAsDataURL(file);
        }
    };

    const handleNext = () => {
        if (activeStep < steps.length - 1) {
            setActiveStep((prev) => prev + 1);
        }
    };

    const handleBack = () => {
        if (activeStep > 0) {
            setActiveStep((prev) => prev - 1);
        }
    };

    const handleSkip = () => {
        dispatch(clearNewSignup());
        const chatId = `chat-${Date.now()}`;
        navigate(`/chat/${chatId}`);
    };

    const handleComplete = () => {
        const persona: TravelPersona = {
            travelStyles: selectedStyles,
            interests: selectedInterests,
            travelFrequency: frequency,
            preferredPace: pace,
        };

        dispatch(saveTravelPersona(persona));

        if (avatarPreview) {
            dispatch(updateUser({ avatar: avatarPreview }));
        }

        dispatch(clearNewSignup());
        const chatId = `chat-${Date.now()}`;
        navigate(`/chat/${chatId}`);
    };

    const canProceed = () => {
        switch (activeStep) {
            case 0:
                return selectedStyles.length > 0;
            case 1:
                return selectedInterests.length > 0;
            case 2:
                return frequency !== '';
            case 3:
                return pace !== '';
            case 4:
                return true; // Photo is optional
            default:
                return false;
        }
    };

    const getInitials = (name: string) => {
        return name
            .split(' ')
            .map((n) => n[0])
            .join('')
            .toUpperCase()
            .slice(0, 2);
    };

    return (
        <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', bgcolor: 'background.body' }}>
            <Header />

            <Box
                sx={{
                    flex: 1,
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    pt: { xs: 12, md: 14 },
                    pb: 4,
                    px: 2,
                }}
            >
                {/* Header */}
                <Box sx={{ textAlign: 'center', mb: 4 }}>
                    <RoadrunnerBirdLogo size={48} sx={{ mb: 2 }} />
                    <Typography level="h2" sx={{ mb: 1 }}>
                        Create Your Travel Persona
                    </Typography>
                    <Typography level="body-lg" sx={{ color: 'text.secondary', maxWidth: 500 }}>
                        Help us personalize your travel recommendations
                    </Typography>
                </Box>

                {/* Stepper */}
                <Stepper sx={{ width: '100%', maxWidth: 600, mb: 4 }}>
                    {steps.map((step, index) => (
                        <Step
                            key={step}
                            indicator={
                                <StepIndicator
                                    variant={activeStep >= index ? 'solid' : 'outlined'}
                                    color={activeStep >= index ? 'primary' : 'neutral'}
                                >
                                    {activeStep > index ? <CheckIcon /> : index + 1}
                                </StepIndicator>
                            }
                        >
                            <Typography
                                level="body-xs"
                                sx={{
                                    color: activeStep >= index ? 'primary.500' : 'text.tertiary',
                                    display: { xs: 'none', sm: 'block' },
                                }}
                            >
                                {step}
                            </Typography>
                        </Step>
                    ))}
                </Stepper>

                {/* Content Card */}
                <Card
                    variant="outlined"
                    sx={{
                        width: '100%',
                        maxWidth: 600,
                        p: { xs: 3, sm: 4 },
                    }}
                >
                    {/* Step 0: Travel Style */}
                    {activeStep === 0 && (
                        <Box>
                            <Typography level="h4" sx={{ mb: 1 }}>
                                What's your travel style?
                            </Typography>
                            <Typography level="body-sm" sx={{ color: 'text.secondary', mb: 3 }}>
                                Select all that apply
                            </Typography>
                            <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 1.5 }}>
                                {travelStyles.map((style) => (
                                    <Sheet
                                        key={style.id}
                                        variant={selectedStyles.includes(style.id) ? 'solid' : 'outlined'}
                                        color={selectedStyles.includes(style.id) ? 'primary' : 'neutral'}
                                        sx={{
                                            p: 2,
                                            borderRadius: 'md',
                                            cursor: 'pointer',
                                            transition: 'all 0.2s',
                                            '&:hover': { borderColor: 'primary.500' },
                                        }}
                                        onClick={() => handleStyleToggle(style.id)}
                                    >
                                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                                            <Typography fontSize="xl">{style.emoji}</Typography>
                                            <Typography level="body-md" sx={{ color: 'text.primary', fontWeight: 500 }}>
                                                {style.label}
                                            </Typography>
                                        </Box>
                                    </Sheet>
                                ))}
                            </Box>
                        </Box>
                    )}

                    {/* Step 1: Interests */}
                    {activeStep === 1 && (
                        <Box>
                            <Typography level="h4" sx={{ mb: 1 }}>
                                What interests you?
                            </Typography>
                            <Typography level="body-sm" sx={{ color: 'text.secondary', mb: 3 }}>
                                Choose your favorite activities
                            </Typography>
                            <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 1.5 }}>
                                {interests.map((interest) => (
                                    <Checkbox
                                        key={interest.id}
                                        label={
                                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                                <span>{interest.emoji}</span>
                                                <span>{interest.label}</span>
                                            </Box>
                                        }
                                        checked={selectedInterests.includes(interest.id)}
                                        onChange={() => handleInterestToggle(interest.id)}
                                        sx={{ p: 1.5, borderRadius: 'md' }}
                                    />
                                ))}
                            </Box>
                        </Box>
                    )}

                    {/* Step 2: Frequency */}
                    {activeStep === 2 && (
                        <Box>
                            <Typography level="h4" sx={{ mb: 1 }}>
                                How often do you travel?
                            </Typography>
                            <Typography level="body-sm" sx={{ color: 'text.secondary', mb: 3 }}>
                                This helps us tailor recommendations
                            </Typography>
                            <RadioGroup
                                value={frequency}
                                onChange={(e) => setFrequency(e.target.value)}
                                sx={{ gap: 1.5 }}
                            >
                                {travelFrequencies.map((freq) => (
                                    <Sheet
                                        key={freq.id}
                                        variant="outlined"
                                        sx={{
                                            p: 2,
                                            borderRadius: 'md',
                                            borderColor: frequency === freq.id ? 'primary.500' : undefined,
                                        }}
                                    >
                                        <Radio
                                            value={freq.id}
                                            label={
                                                <Box>
                                                    <Typography level="body-md" sx={{ fontWeight: 500, marginRight: 2 }}>
                                                        {freq.label}
                                                    </Typography>
                                                    <Typography level="body-xs" sx={{ color: 'text.secondary' }}>
                                                        {freq.description}
                                                    </Typography>
                                                </Box>
                                            }
                                        />
                                    </Sheet>
                                ))}
                            </RadioGroup>
                        </Box>
                    )}

                    {/* Step 3: Pace */}
                    {activeStep === 3 && (
                        <Box>
                            <Typography level="h4" sx={{ mb: 1 }}>
                                What's your preferred pace?
                            </Typography>
                            <Typography level="body-sm" sx={{ color: 'text.secondary', mb: 3 }}>
                                How do you like to explore?
                            </Typography>
                            <RadioGroup
                                value={pace}
                                onChange={(e) => setPace(e.target.value)}
                                sx={{ gap: 1.5 }}
                            >
                                {pacePrefences.map((p) => (
                                    <Sheet
                                        key={p.id}
                                        variant="outlined"
                                        sx={{
                                            p: 2,
                                            borderRadius: 'md',
                                            borderColor: pace === p.id ? 'primary.500' : undefined,
                                        }}
                                    >
                                        <Radio
                                            value={p.id}
                                            label={
                                                <Box>
                                                    <Typography level="body-md" sx={{ fontWeight: 500, marginRight: 2 }}>
                                                        {p.label}
                                                    </Typography>
                                                    <Typography level="body-xs" sx={{ color: 'text.secondary' }}>
                                                        {p.description}
                                                    </Typography>
                                                </Box>
                                            }
                                        />
                                    </Sheet>
                                ))}
                            </RadioGroup>
                        </Box>
                    )}

                    {/* Step 4: Photo */}
                    {activeStep === 4 && (
                        <Box sx={{ textAlign: 'center' }}>
                            <Typography level="h4" sx={{ mb: 1 }}>
                                Add a profile photo
                            </Typography>
                            <Typography level="body-sm" sx={{ color: 'text.secondary', mb: 4 }}>
                                Optional - you can add this later
                            </Typography>

                            <Box sx={{ display: 'flex', justifyContent: 'center', mb: 3 }}>
                                <Box sx={{ position: 'relative' }}>
                                    <Avatar
                                        src={avatarPreview || undefined}
                                        sx={{ width: 120, height: 120, fontSize: '2rem' }}
                                    >
                                        {!avatarPreview && user && getInitials(user.name)}
                                    </Avatar>
                                    <IconButton
                                        component="label"
                                        variant="solid"
                                        color="primary"
                                        size="sm"
                                        sx={{
                                            position: 'absolute',
                                            bottom: 0,
                                            right: 0,
                                            borderRadius: '50%',
                                        }}
                                    >
                                        <PhotoCameraIcon />
                                        <input
                                            type="file"
                                            accept="image/*"
                                            hidden
                                            onChange={handleAvatarUpload}
                                        />
                                    </IconButton>
                                </Box>
                            </Box>
                        </Box>
                    )}

                    {/* Navigation Buttons */}
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 4, gap: 2 }}>
                        <Button
                            variant="plain"
                            color="neutral"
                            onClick={handleSkip}
                        >
                            Skip for now
                        </Button>

                        <Box sx={{ display: 'flex', gap: 1.5 }}>
                            {activeStep > 0 && (
                                <Button
                                    variant="outlined"
                                    color="neutral"
                                    startDecorator={<ArrowBackIcon />}
                                    onClick={handleBack}
                                >
                                    Back
                                </Button>
                            )}

                            {activeStep < steps.length - 1 ? (
                                <Button
                                    endDecorator={<ArrowForwardIcon />}
                                    onClick={handleNext}
                                    disabled={!canProceed()}
                                >
                                    Next
                                </Button>
                            ) : (
                                <Button
                                    endDecorator={<CheckIcon />}
                                    onClick={handleComplete}
                                >
                                    Complete
                                </Button>
                            )}
                        </Box>
                    </Box>
                </Card>
            </Box>
        </Box>
    );
};

export default TravelPersonaPage;
