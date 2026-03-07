import { useState, useRef } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import {
    Box,
    Typography,
    Card,
    Input,
    Button,
    Avatar,
    IconButton,
    Sheet,
    Alert,
} from '@mui/joy';
import PersonIcon from '@mui/icons-material/Person';
import EmailIcon from '@mui/icons-material/Email';
import PhotoCameraIcon from '@mui/icons-material/PhotoCamera';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import Header from '../components/Header';
import Footer from '../components/Footer';
import { useAppDispatch, useAppSelector } from '../store/hooks';
import { updateUser } from '../store/authSlice';
import { userApi, extractErrorMessage } from '../services/userService';

const travelStyleLabels: Record<string, string> = {
    adventure: '🏔️ Adventure',
    relaxation: '🏖️ Relaxation',
    culture: '🏛️ Culture',
    food: '🍽️ Food & Cuisine',
    budget: '💰 Budget Travel',
    luxury: '✨ Luxury',
};

const interestLabels: Record<string, string> = {
    museums: '🖼️ Museums',
    nature: '🌿 Nature',
    history: '📜 History',
    photography: '📷 Photography',
    'local-food': '🥘 Local Food',
    nightlife: '🌙 Nightlife',
    shopping: '🛍️ Shopping',
    architecture: '🏗️ Architecture',
};

const frequencyLabels: Record<string, string> = {
    'first-timer': 'First-time traveler',
    occasional: 'Occasional traveler',
    frequent: 'Frequent traveler',
};

const paceLabels: Record<string, string> = {
    packed: 'Packed itinerary',
    balanced: 'Balanced',
    relaxed: 'Relaxed exploration',
};

const ProfilePage = () => {
    const navigate = useNavigate();
    const dispatch = useAppDispatch();
    const { user, isAuthenticated } = useAppSelector((state) => state.auth);
    const fileInputRef = useRef<HTMLInputElement>(null);

    const [name, setName] = useState(user?.name || '');
    const [email] = useState(user?.email || '');
    const [avatarPreview, setAvatarPreview] = useState<string | null>(user?.avatar || null);
    const [isEditing, setIsEditing] = useState(false);
    const [saved, setSaved] = useState(false);

    const [profileError, setProfileError] = useState<string | null>(null);
    const [isSaving, setIsSaving] = useState(false);

    // Redirect to login if not authenticated
    if (!isAuthenticated) {
        return <Navigate to="/login" replace />;
    }

    const getInitials = (name: string) => {
        return name
            .split(' ')
            .map((n) => n[0])
            .join('')
            .toUpperCase()
            .slice(0, 2);
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

    const handleSaveProfile = async () => {
        setProfileError(null);
        setIsSaving(true);
        try {
            await userApi.updateProfile(name, avatarPreview);
            dispatch(updateUser({
                name,
                avatar: avatarPreview || undefined,
            }));
            setIsEditing(false);
            setSaved(true);
            setTimeout(() => setSaved(false), 3000);
        } catch (err) {
            setProfileError(extractErrorMessage(err));
        } finally {
            setIsSaving(false);
        }
    };

    const handleCancelEdit = () => {
        setName(user?.name || '');
        setAvatarPreview(user?.avatar || null);
        setIsEditing(false);
    };

    return (
        <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
            <Header />

            <Box
                component="main"
                sx={{
                    flex: 1,
                    pt: { xs: 10, md: 12 },
                    pb: 6,
                    px: { xs: 2, md: 4 },
                    maxWidth: 800,
                    mx: 'auto',
                    width: '100%',
                }}
            >
                <Typography level="h2" sx={{ mb: 1 }}>
                    Profile
                </Typography>
                <Typography level="body-lg" sx={{ color: 'text.secondary', mb: 4 }}>
                    Manage your personal information
                </Typography>

                {saved && (
                    <Alert color="success" sx={{ mb: 3 }}>
                        Your profile has been updated!
                    </Alert>
                )}

                {profileError && (
                    <Alert color="danger" sx={{ mb: 3 }}>
                        {profileError}
                    </Alert>
                )}

                {/* Profile Card */}
                <Card variant="outlined" sx={{ p: 3, mb: 3 }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 3 }}>
                        <Typography level="title-lg">
                            Personal Information
                        </Typography>
                        {!isEditing && (
                            <Button
                                variant="outlined"
                                size="sm"
                                startDecorator={<EditIcon />}
                                onClick={() => setIsEditing(true)}
                            >
                                Edit
                            </Button>
                        )}
                    </Box>

                    {/* Avatar */}
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 3, mb: 3 }}>
                        <Box sx={{ position: 'relative' }}>
                            <Avatar
                                src={avatarPreview || undefined}
                                sx={{ width: 100, height: 100, fontSize: '2rem' }}
                            >
                                {!avatarPreview && name && getInitials(name)}
                            </Avatar>
                            {isEditing && (
                                <Box
                                    sx={{
                                        position: 'absolute',
                                        bottom: -8,
                                        left: '50%',
                                        transform: 'translateX(-50%)',
                                        display: 'flex',
                                        gap: 0.5,
                                    }}
                                >
                                    <IconButton
                                        component="label"
                                        variant="solid"
                                        color="primary"
                                        size="sm"
                                        sx={{ borderRadius: '50%' }}
                                    >
                                        <PhotoCameraIcon sx={{ fontSize: 16 }} />
                                        <input
                                            ref={fileInputRef}
                                            type="file"
                                            accept="image/*"
                                            hidden
                                            onChange={handleAvatarUpload}
                                        />
                                    </IconButton>
                                    {avatarPreview && (
                                        <IconButton
                                            variant="solid"
                                            color="danger"
                                            size="sm"
                                            sx={{ borderRadius: '50%' }}
                                            onClick={() => setAvatarPreview(null)}
                                        >
                                            <DeleteIcon sx={{ fontSize: 16 }} />
                                        </IconButton>
                                    )}
                                </Box>
                            )}
                        </Box>
                        <Box>
                            <Typography level="title-lg" sx={{ fontWeight: 600 }}>
                                {user?.name}
                            </Typography>
                            <Typography level="body-sm" sx={{ color: 'text.secondary' }}>
                                {user?.email}
                            </Typography>
                            {user?.travelPersona && (
                                <Typography level="body-xs" sx={{ color: 'primary.500', mt: 0.5 }}>
                                    Travel persona completed ✓
                                </Typography>
                            )}
                        </Box>
                    </Box>

                    {isEditing ? (
                        <>
                            {/* Editable Fields */}
                            <Box sx={{ mb: 2 }}>
                                <Typography level="body-sm" sx={{ mb: 0.5, fontWeight: 500 }}>
                                    Full Name
                                </Typography>
                                <Input
                                    value={name}
                                    onChange={(e) => setName(e.target.value)}
                                    startDecorator={<PersonIcon />}
                                    sx={{ maxWidth: 400 }}
                                />
                            </Box>

                            <Box sx={{ mb: 3 }}>
                                <Typography level="body-sm" sx={{ mb: 0.5, fontWeight: 500 }}>
                                    Email
                                </Typography>
                                <Input
                                    value={email}
                                    readOnly
                                    startDecorator={<EmailIcon />}
                                    sx={{ maxWidth: 400, bgcolor: 'background.level1' }}
                                />
                                <Typography level="body-xs" sx={{ color: 'text.tertiary', mt: 0.5 }}>
                                    Email cannot be changed
                                </Typography>
                            </Box>

                            <Box sx={{ display: 'flex', gap: 1.5 }}>
                                <Button onClick={handleSaveProfile} loading={isSaving}>
                                    Save Changes
                                </Button>
                                <Button variant="outlined" color="neutral" onClick={handleCancelEdit}>
                                    Cancel
                                </Button>
                            </Box>
                        </>
                    ) : (
                        <Box sx={{ display: 'grid', gap: 2 }}>
                            <Box>
                                <Typography level="body-xs" sx={{ color: 'text.secondary' }}>
                                    Full Name
                                </Typography>
                                <Typography level="body-md">
                                    {user?.name}
                                </Typography>
                            </Box>
                            <Box>
                                <Typography level="body-xs" sx={{ color: 'text.secondary' }}>
                                    Email
                                </Typography>
                                <Typography level="body-md">
                                    {user?.email}
                                </Typography>
                            </Box>
                        </Box>
                    )}
                </Card>

                {/* Travel Preferences */}
                <Card variant="outlined" sx={{ p: 3 }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
                        <Typography level="title-lg">
                            Travel Preferences
                        </Typography>
                        <Button
                            variant="outlined"
                            size="sm"
                            onClick={() => navigate('/onboarding')}
                        >
                            {user?.travelPersona ? 'Update' : 'Create'}
                        </Button>
                    </Box>

                    {user?.travelPersona ? (
                        <Box sx={{ display: 'grid', gap: 3 }}>
                            {/* Travel Styles */}
                            {user.travelPersona.travelStyles.length > 0 && (
                                <Box>
                                    <Typography level="body-sm" sx={{ fontWeight: 500, mb: 1, color: 'text.secondary' }}>
                                        Travel Styles
                                    </Typography>
                                    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                                        {user.travelPersona.travelStyles.map((style) => (
                                            <Sheet
                                                key={style}
                                                variant="soft"
                                                color="primary"
                                                sx={{ px: 1.5, py: 0.5, borderRadius: 'md' }}
                                            >
                                                <Typography level="body-sm">
                                                    {travelStyleLabels[style] || style}
                                                </Typography>
                                            </Sheet>
                                        ))}
                                    </Box>
                                </Box>
                            )}

                            {/* Interests */}
                            {user.travelPersona.interests.length > 0 && (
                                <Box>
                                    <Typography level="body-sm" sx={{ fontWeight: 500, mb: 1, color: 'text.secondary' }}>
                                        Interests
                                    </Typography>
                                    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                                        {user.travelPersona.interests.map((interest) => (
                                            <Sheet
                                                key={interest}
                                                variant="soft"
                                                color="neutral"
                                                sx={{ px: 1.5, py: 0.5, borderRadius: 'md' }}
                                            >
                                                <Typography level="body-sm">
                                                    {interestLabels[interest] || interest}
                                                </Typography>
                                            </Sheet>
                                        ))}
                                    </Box>
                                </Box>
                            )}

                            {/* Frequency & Pace */}
                            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
                                {user.travelPersona.travelFrequency && (
                                    <Box>
                                        <Typography level="body-sm" sx={{ fontWeight: 500, mb: 0.5, color: 'text.secondary' }}>
                                            Travel Frequency
                                        </Typography>
                                        <Typography level="body-md">
                                            {frequencyLabels[user.travelPersona.travelFrequency] || user.travelPersona.travelFrequency}
                                        </Typography>
                                    </Box>
                                )}
                                {user.travelPersona.preferredPace && (
                                    <Box>
                                        <Typography level="body-sm" sx={{ fontWeight: 500, mb: 0.5, color: 'text.secondary' }}>
                                            Preferred Pace
                                        </Typography>
                                        <Typography level="body-md">
                                            {paceLabels[user.travelPersona.preferredPace] || user.travelPersona.preferredPace}
                                        </Typography>
                                    </Box>
                                )}
                            </Box>
                        </Box>
                    ) : (
                        <Box sx={{ textAlign: 'center', py: 4 }}>
                            <Typography level="body-md" sx={{ color: 'text.secondary', mb: 2 }}>
                                You haven't created your travel persona yet.
                            </Typography>
                            <Button onClick={() => navigate('/onboarding')}>
                                Create Travel Persona
                            </Button>
                        </Box>
                    )}
                </Card>
            </Box>

            <Footer />
        </Box >
    );
};

export default ProfilePage;
