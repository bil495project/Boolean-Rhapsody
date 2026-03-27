import { useRef, useState } from 'react';
import { Navigate } from 'react-router-dom';
import {
    Alert,
    Avatar,
    Box,
    Button,
    Card,
    Chip,
    IconButton,
    Input,
    Typography,
} from '@mui/joy';
import PersonIcon from '@mui/icons-material/Person';
import EmailIcon from '@mui/icons-material/Email';
import PhotoCameraIcon from '@mui/icons-material/PhotoCamera';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import Header from '../components/Header';
import Footer from '../components/Footer';
import TravelProfileBuilder from '../components/travel/TravelProfileBuilder';
import { useAppDispatch, useAppSelector } from '../store/hooks';
import {
    createTravelPersonaAsync,
    deleteTravelPersonaAsync,
    setDefaultTravelPersonaAsync,
    updateTravelPersonaAsync,
    updateUser,
    type TravelPersona,
} from '../store/authSlice';
import { userApi, extractErrorMessage } from '../services/userService';
import { summarizeTravelProfile } from '../utils/travelProfile';

const toErrorMessage = (error: unknown) =>
    typeof error === 'string' ? error : extractErrorMessage(error);

const ProfilePage = () => {
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
    const [travelProfileNotice, setTravelProfileNotice] = useState<string | null>(null);
    const [travelProfileError, setTravelProfileError] = useState<string | null>(null);
    const [isSavingPersona, setIsSavingPersona] = useState(false);
    const [editorMode, setEditorMode] = useState<'create' | 'edit' | null>(null);
    const [editingPersona, setEditingPersona] = useState<TravelPersona | null>(null);

    if (!isAuthenticated) {
        return <Navigate to="/login" replace />;
    }

    const getInitials = (fullName: string) =>
        fullName
            .split(' ')
            .map((part) => part[0])
            .join('')
            .toUpperCase()
            .slice(0, 2);

    const handleAvatarUpload = (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        if (!file) {
            return;
        }

        const reader = new FileReader();
        reader.onloadend = () => {
            setAvatarPreview(reader.result as string);
        };
        reader.readAsDataURL(file);
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

    const openCreatePersona = () => {
        setEditorMode('create');
        setEditingPersona(null);
        setTravelProfileError(null);
        setTravelProfileNotice(null);
    };

    const openEditPersona = (persona: TravelPersona) => {
        setEditorMode('edit');
        setEditingPersona(persona);
        setTravelProfileError(null);
        setTravelProfileNotice(null);
    };

    const closePersonaEditor = () => {
        setEditorMode(null);
        setEditingPersona(null);
    };

    const handleSavePersona = async (persona: TravelPersona) => {
        setIsSavingPersona(true);
        setTravelProfileError(null);
        setTravelProfileNotice(null);

        try {
            if (editorMode === 'edit' && editingPersona?.id) {
                await dispatch(updateTravelPersonaAsync({
                    id: editingPersona.id,
                    persona: {
                        ...persona,
                        name: persona.name.trim(),
                    },
                })).unwrap();
                setTravelProfileNotice('Travel profile updated.');
            } else {
                await dispatch(createTravelPersonaAsync({
                    ...persona,
                    name: persona.name.trim(),
                })).unwrap();
                setTravelProfileNotice('New travel profile saved.');
            }
            closePersonaEditor();
        } catch (error) {
            setTravelProfileError(toErrorMessage(error));
        } finally {
            setIsSavingPersona(false);
        }
    };

    const handleDeletePersona = async (personaId: string) => {
        setTravelProfileError(null);
        setTravelProfileNotice(null);
        try {
            await dispatch(deleteTravelPersonaAsync(personaId)).unwrap();
            setTravelProfileNotice('Profile deleted.');
            if (editingPersona?.id === personaId) {
                closePersonaEditor();
            }
        } catch (error) {
            setTravelProfileError(toErrorMessage(error));
        }
    };

    const handleSetDefault = async (personaId: string) => {
        setTravelProfileError(null);
        setTravelProfileNotice(null);
        try {
            await dispatch(setDefaultTravelPersonaAsync(personaId)).unwrap();
            setTravelProfileNotice('Default profile updated.');
        } catch (error) {
            setTravelProfileError(toErrorMessage(error));
        }
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
                    maxWidth: 940,
                    mx: 'auto',
                    width: '100%',
                }}
            >
                <Typography level="h2" sx={{ mb: 1 }}>
                    My Profile
                </Typography>
                <Typography level="body-lg" sx={{ color: 'text.secondary', mb: 4 }}>
                    Manage your account details and travel profiles here.
                </Typography>

                {saved && (
                    <Alert color="success" sx={{ mb: 3 }}>
                        Your profile details have been updated.
                    </Alert>
                )}

                {profileError && (
                    <Alert color="danger" sx={{ mb: 3 }}>
                        {profileError}
                    </Alert>
                )}

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

                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 3, mb: 3, flexWrap: 'wrap' }}>
                        <Box sx={{ position: 'relative' }}>
                            <Avatar
                                src={avatarPreview || undefined}
                                sx={{ width: 100, height: 100, fontSize: '2rem' }}
                            >
                                {!avatarPreview && user?.name && getInitials(user.name)}
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
                            <Typography level="body-xs" sx={{ color: 'primary.500', mt: 0.5 }}>
                                {user?.travelPersonas.length ?? 0} travel profiles saved
                            </Typography>
                        </Box>
                    </Box>

                    {isEditing ? (
                        <>
                            <Box sx={{ mb: 2 }}>
                                <Typography level="body-sm" sx={{ mb: 0.5, fontWeight: 500 }}>
                                    Full Name
                                </Typography>
                                <Input
                                    value={name}
                                    onChange={(event) => setName(event.target.value)}
                                    startDecorator={<PersonIcon />}
                                    sx={{ maxWidth: 420 }}
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
                                    sx={{ maxWidth: 420, bgcolor: 'background.level1' }}
                                />
                            </Box>

                            <Box sx={{ display: 'flex', gap: 1.5 }}>
                                <Button onClick={handleSaveProfile} loading={isSaving}>
                                    Save changes
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
                                <Typography level="body-md">{user?.name}</Typography>
                            </Box>
                            <Box>
                                <Typography level="body-xs" sx={{ color: 'text.secondary' }}>
                                    Email
                                </Typography>
                                <Typography level="body-md">{user?.email}</Typography>
                            </Box>
                        </Box>
                    )}
                </Card>

                <Card variant="outlined" sx={{ p: 3 }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 2, mb: 3, flexWrap: 'wrap' }}>
                        <Box>
                            <Typography level="title-lg">
                                Travel Profiles
                            </Typography>
                            <Typography level="body-sm" sx={{ color: 'text.secondary', mt: 0.5 }}>
                                Save multiple profiles for different trip styles and choose whichever one should be your default.
                            </Typography>
                        </Box>
                        <Button onClick={openCreatePersona}>
                            Create new profile
                        </Button>
                    </Box>

                    {travelProfileNotice && (
                        <Alert color="success" sx={{ mb: 2 }}>
                            {travelProfileNotice}
                        </Alert>
                    )}

                    {travelProfileError && (
                        <Alert color="danger" sx={{ mb: 2 }}>
                            {travelProfileError}
                        </Alert>
                    )}

                    {editorMode && (
                        <Box sx={{ mb: 3 }}>
                            <TravelProfileBuilder
                                key={`${editorMode ?? 'closed'}-${editingPersona?.id ?? 'new'}`}
                                initialValue={editingPersona ?? undefined}
                                title={editorMode === 'edit' ? 'Edit profile' : 'New travel profile'}
                                description="This profile automatically prepares the route weights used during route generation."
                                confirmLabel={editorMode === 'edit' ? 'Save changes' : 'Save profile'}
                                isSaving={isSavingPersona}
                                onConfirm={handleSavePersona}
                                onCancel={closePersonaEditor}
                            />
                        </Box>
                    )}

                    {user?.travelPersonas.length ? (
                        <Box sx={{ display: 'grid', gap: 1.5 }}>
                            {user.travelPersonas.map((persona) => (
                                <Card key={persona.id || persona.name} variant="soft" sx={{ p: 2.5 }}>
                                    <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap' }}>
                                        <Box sx={{ minWidth: 0 }}>
                                            <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap', mb: 0.5 }}>
                                                <Typography level="title-sm" sx={{ fontWeight: 700 }}>
                                                    {persona.name}
                                                </Typography>
                                                {persona.isDefault && (
                                                    <Chip size="sm" color="primary" variant="soft">
                                                        Default
                                                    </Chip>
                                                )}
                                            </Box>
                                            <Typography level="body-sm" sx={{ color: 'text.secondary' }}>
                                                {summarizeTravelProfile(persona)}
                                            </Typography>
                                        </Box>

                                        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                                            {!persona.isDefault && persona.id && (
                                                <Button size="sm" variant="outlined" onClick={() => handleSetDefault(persona.id!)}>
                                                    Set as default
                                                </Button>
                                            )}
                                            <Button size="sm" variant="outlined" color="neutral" onClick={() => openEditPersona(persona)}>
                                                Edit
                                            </Button>
                                            {persona.id && (
                                                <Button size="sm" color="danger" variant="soft" onClick={() => handleDeletePersona(persona.id!)}>
                                                    Delete
                                                </Button>
                                            )}
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Box>
                    ) : (
                        <Box sx={{ textAlign: 'center', py: 4 }}>
                            <Typography level="body-md" sx={{ color: 'text.secondary', mb: 2 }}>
                                You do not have any saved travel profiles yet.
                            </Typography>
                            <Button onClick={openCreatePersona}>
                                Create your first profile
                            </Button>
                        </Box>
                    )}
                </Card>
            </Box>

            <Footer />
        </Box>
    );
};

export default ProfilePage;
