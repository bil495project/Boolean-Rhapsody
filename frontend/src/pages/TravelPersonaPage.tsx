import { useState } from 'react';
import { Alert, Box, Button, Card, Typography } from '@mui/joy';
import { Navigate, useNavigate } from 'react-router-dom';
import Header from '../components/Header';
import RoadrunnerBirdLogo from '../components/RoadrunnerBirdLogo';
import TravelProfileBuilder from '../components/travel/TravelProfileBuilder';
import { useAppDispatch, useAppSelector } from '../store/hooks';
import {
    clearNewSignup,
    createTravelPersonaAsync,
    type TravelPersona,
} from '../store/authSlice';
import { extractErrorMessage } from '../services/userService';

const toErrorMessage = (error: unknown) =>
    typeof error === 'string' ? error : extractErrorMessage(error);

const TravelPersonaPage = () => {
    const navigate = useNavigate();
    const dispatch = useAppDispatch();
    const { isAuthenticated, isNewSignup } = useAppSelector((state) => state.auth);
    const [error, setError] = useState<string | null>(null);

    if (!isAuthenticated) {
        return <Navigate to="/login" replace />;
    }

    const handleSkip = () => {
        dispatch(clearNewSignup());
        navigate('/chat/new', { replace: true });
    };

    const handleComplete = async (profile: TravelPersona) => {
        try {
            setError(null);
            await dispatch(createTravelPersonaAsync({
                ...profile,
                name: profile.name.trim(),
            })).unwrap();
            dispatch(clearNewSignup());
            navigate('/chat/new', { replace: true });
        } catch (err) {
            setError(toErrorMessage(err));
        }
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
                <Box sx={{ textAlign: 'center', mb: 4, maxWidth: 680 }}>
                    <RoadrunnerBirdLogo size={52} sx={{ mb: 2 }} />
                    <Typography level="h2" sx={{ mb: 1 }}>
                        Create your travel profile
                    </Typography>
                    <Typography level="body-lg" sx={{ color: 'text.secondary' }}>
                        {isNewSignup
                            ? 'Welcome. Let’s create a quick profile to make your first route suggestions more accurate.'
                            : 'Create a profile you can save and reuse later from the route screen or your profile page.'}
                    </Typography>
                </Box>

                <Card
                    variant="outlined"
                    sx={{
                        width: '100%',
                        maxWidth: 760,
                        p: { xs: 2, sm: 3 },
                    }}
                >
                    {error && (
                        <Alert color="danger" sx={{ mb: 2 }}>
                            {error}
                        </Alert>
                    )}

                    <TravelProfileBuilder
                        title="Build your route style"
                        description="Once you answer these questions, the system converts your preferences into route weights for future suggestions."
                        confirmLabel="Save profile and continue"
                        cancelLabel="Skip for now"
                        onConfirm={handleComplete}
                        onCancel={handleSkip}
                    />

                    <Box sx={{ display: 'flex', justifyContent: 'center', mt: 2 }}>
                        <Button variant="plain" color="neutral" onClick={handleSkip}>
                            Continue without a profile
                        </Button>
                    </Box>
                </Card>
            </Box>
        </Box>
    );
};

export default TravelPersonaPage;
