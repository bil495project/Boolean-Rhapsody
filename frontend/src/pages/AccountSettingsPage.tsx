import { useState, useEffect } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import {
    Box,
    Typography,
    Card,
    Button,
    Divider,
    Sheet,
    Checkbox,
    Slider,
    Modal,
    ModalDialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Select,
    Option,
    Switch,
    Input,
    FormControl,
    FormLabel,
    FormHelperText,
    Alert,
} from '@mui/joy';
import { useColorScheme } from '@mui/joy/styles';
import LightModeIcon from '@mui/icons-material/LightMode';
import DarkModeIcon from '@mui/icons-material/DarkMode';
import SettingsBrightnessIcon from '@mui/icons-material/SettingsBrightness';
import TextFieldsIcon from '@mui/icons-material/TextFields';
import NotificationsIcon from '@mui/icons-material/Notifications';
import SecurityIcon from '@mui/icons-material/Security';
import DeleteIcon from '@mui/icons-material/Delete';
import WarningIcon from '@mui/icons-material/Warning';
import LanguageIcon from '@mui/icons-material/Language';
import Header from '../components/Header';
import Footer from '../components/Footer';
import { useAppDispatch, useAppSelector } from '../store/hooks';
import { deleteAccount } from '../store/authSlice';
import { userApi, extractErrorMessage } from '../services/userService';
import { validatePassword, doPasswordsMatch } from '../utils/validation';

// Get/set font size from localStorage
const getFontSize = () => {
    const saved = localStorage.getItem('travelplanner_fontsize');
    return saved ? parseInt(saved, 10) : 100;
};

const setFontSizeStorage = (size: number) => {
    localStorage.setItem('travelplanner_fontsize', size.toString());
    document.documentElement.style.fontSize = `${size}%`;
};

const SettingsPage = () => {
    const navigate = useNavigate();
    const dispatch = useAppDispatch();
    const { mode, setMode } = useColorScheme();
    const { isAuthenticated, user } = useAppSelector((state) => state.auth);

    const [fontSize, setFontSize] = useState(getFontSize());
    const [emailNotifications, setEmailNotifications] = useState(true);
    const [pushNotifications, setPushNotifications] = useState(false);
    const [language, setLanguage] = useState<string | null>('en');
    const [deleteModalOpen, setDeleteModalOpen] = useState(false);
    const [confirmDelete, setConfirmDelete] = useState(false);
    const [deleteConfirmation, setDeleteConfirmation] = useState('');
    const [deleteLoading, setDeleteLoading] = useState(false);
    const [deleteError, setDeleteError] = useState<string | null>(null);

    // Change password state
    const [showPasswordForm, setShowPasswordForm] = useState(false);
    const [oldPassword, setOldPassword] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmNewPassword, setConfirmNewPassword] = useState('');
    const [passwordChangeLoading, setPasswordChangeLoading] = useState(false);
    const [passwordChangeError, setPasswordChangeError] = useState<string | null>(null);
    const [passwordChangeSuccess, setPasswordChangeSuccess] = useState(false);

    // Apply font size on mount and change
    useEffect(() => {
        setFontSizeStorage(fontSize);
    }, [fontSize]);

    // Redirect to login if not authenticated
    if (!isAuthenticated || !user) {
        return <Navigate to="/login" replace />;
    }

    const accountEmail = user.email;
    const deleteEmailMatches = deleteConfirmation.trim().toLowerCase() === accountEmail.toLowerCase();
    const canDeleteAccount = confirmDelete && deleteEmailMatches && !deleteLoading;

    const closeDeleteModal = () => {
        if (deleteLoading) {
            return;
        }

        setDeleteModalOpen(false);
        setConfirmDelete(false);
        setDeleteConfirmation('');
        setDeleteError(null);
    };

    const handleDeleteAccount = async () => {
        setDeleteError(null);

        if (!canDeleteAccount) {
            setDeleteError('Confirm the deletion and type your account email to continue.');
            return;
        }

        setDeleteLoading(true);
        try {
            await userApi.deleteAccount();
            dispatch(deleteAccount());
            navigate('/', { replace: true });
        } catch (err) {
            setDeleteError(extractErrorMessage(err));
            setDeleteLoading(false);
        }
    };

    const handleChangePassword = async () => {
        setPasswordChangeError(null);

        if (!validatePassword(newPassword).isValid) {
            setPasswordChangeError('New password does not meet requirements.');
            return;
        }
        if (!doPasswordsMatch(newPassword, confirmNewPassword)) {
            setPasswordChangeError('New passwords do not match.');
            return;
        }

        setPasswordChangeLoading(true);
        try {
            await userApi.changePassword(oldPassword, newPassword);
            setPasswordChangeSuccess(true);
            setOldPassword('');
            setNewPassword('');
            setConfirmNewPassword('');
            setShowPasswordForm(false);
            setTimeout(() => setPasswordChangeSuccess(false), 5000);
        } catch (err) {
            setPasswordChangeError(extractErrorMessage(err));
        } finally {
            setPasswordChangeLoading(false);
        }
    };

    const handleFontSizeChange = (_: Event, value: number | number[]) => {
        setFontSize(value as number);
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
                    Settings
                </Typography>
                <Typography level="body-lg" sx={{ color: 'text.secondary', mb: 4 }}>
                    Manage your preferences, security, and account data.
                </Typography>

                {/* Appearance */}
                <Card variant="outlined" sx={{ p: 3, mb: 3 }}>
                    <Typography level="title-lg" sx={{ mb: 3, display: 'flex', alignItems: 'center', gap: 1 }}>
                        <SettingsBrightnessIcon />
                        Appearance
                    </Typography>

                    {/* Theme Selection */}
                    <Box sx={{ mb: 3 }}>
                        <Typography level="body-sm" sx={{ fontWeight: 500, mb: 1.5 }}>
                            Theme
                        </Typography>
                        <Box sx={{ display: 'flex', gap: 2 }}>
                            <Sheet
                                variant={mode === 'light' ? 'solid' : 'outlined'}
                                color={mode === 'light' ? 'primary' : 'neutral'}
                                sx={{
                                    p: 2,
                                    borderRadius: 'md',
                                    cursor: 'pointer',
                                    flex: 1,
                                    textAlign: 'center',
                                    transition: 'all 0.2s',
                                }}
                                onClick={() => setMode('light')}
                            >
                                <LightModeIcon sx={{ fontSize: 28, mb: 0.5 }} />
                                <Typography level="body-sm" sx={{ color: 'text.primary', fontWeight: 500 }}>
                                    Light
                                </Typography>
                            </Sheet>
                            <Sheet
                                variant={mode === 'dark' ? 'solid' : 'outlined'}
                                color={mode === 'dark' ? 'primary' : 'neutral'}
                                sx={{
                                    p: 2,
                                    borderRadius: 'md',
                                    cursor: 'pointer',
                                    flex: 1,
                                    textAlign: 'center',
                                    transition: 'all 0.2s',
                                }}
                                onClick={() => setMode('dark')}
                            >
                                <DarkModeIcon sx={{ fontSize: 28, mb: 0.5 }} />
                                <Typography level="body-sm" sx={{ color: 'text.primary', fontWeight: 500 }}>
                                    Dark
                                </Typography>
                            </Sheet>
                        </Box>
                    </Box>

                    {/* Font Size */}
                    <Box>
                        <Typography level="body-sm" sx={{ fontWeight: 500, mb: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
                            <TextFieldsIcon sx={{ fontSize: 18 }} />
                            Font Size
                        </Typography>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                            <Typography level="body-xs">A</Typography>
                            <Slider
                                value={fontSize}
                                onChange={handleFontSizeChange}
                                min={80}
                                max={120}
                                step={5}
                                marks={[
                                    { value: 80, label: '80%' },
                                    { value: 100, label: '100%' },
                                    { value: 120, label: '120%' },
                                ]}
                                sx={{ flex: 1 }}
                            />
                            <Typography level="body-lg">A</Typography>
                        </Box>
                        <Typography level="body-xs" sx={{ color: 'text.tertiary', mt: 1 }}>
                            Current: {fontSize}%
                        </Typography>
                    </Box>
                </Card>

                {/* Language */}
                <Card variant="outlined" sx={{ p: 3, mb: 3 }}>
                    <Typography level="title-lg" sx={{ mb: 3, display: 'flex', alignItems: 'center', gap: 1 }}>
                        <LanguageIcon />
                        Language & Region
                    </Typography>

                    <Box>
                        <Typography level="body-sm" sx={{ fontWeight: 500, mb: 1 }}>
                            Language
                        </Typography>
                        <Select
                            value={language}
                            onChange={(_, value) => setLanguage(value)}
                            sx={{ maxWidth: 300 }}
                        >
                            <Option value="en">English</Option>
                            <Option value="tr">Türkçe</Option>
                        </Select>
                        <Typography level="body-xs" sx={{ color: 'text.tertiary', mt: 1 }}>
                            Language support coming soon
                        </Typography>
                    </Box>
                </Card>

                {/* Notifications */}
                <Card variant="outlined" sx={{ p: 3, mb: 3 }}>
                    <Typography level="title-lg" sx={{ mb: 3, display: 'flex', alignItems: 'center', gap: 1 }}>
                        <NotificationsIcon />
                        Notifications
                    </Typography>

                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <Box>
                                <Typography level="body-md" sx={{ fontWeight: 500 }}>
                                    Email Notifications
                                </Typography>
                                <Typography level="body-xs" sx={{ color: 'text.secondary' }}>
                                    Receive travel tips and destination updates
                                </Typography>
                            </Box>
                            <Switch
                                checked={emailNotifications}
                                onChange={(e) => setEmailNotifications(e.target.checked)}
                            />
                        </Box>

                        <Divider />

                        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <Box>
                                <Typography level="body-md" sx={{ fontWeight: 500 }}>
                                    Push Notifications
                                </Typography>
                                <Typography level="body-xs" sx={{ color: 'text.secondary' }}>
                                    Get notified about new features
                                </Typography>
                            </Box>
                            <Switch
                                checked={pushNotifications}
                                onChange={(e) => setPushNotifications(e.target.checked)}
                            />
                        </Box>
                    </Box>
                </Card>

                {/* Security */}
                <Card variant="outlined" sx={{ p: 3, mb: 3 }}>
                    <Typography level="title-lg" sx={{ mb: 3, display: 'flex', alignItems: 'center', gap: 1 }}>
                        <SecurityIcon />
                        Security
                    </Typography>

                    {passwordChangeSuccess && (
                        <Alert color="success" sx={{ mb: 2 }}>
                            Password changed successfully!
                        </Alert>
                    )}

                    {passwordChangeError && (
                        <Alert color="danger" sx={{ mb: 2 }}>
                            {passwordChangeError}
                        </Alert>
                    )}

                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                        {!showPasswordForm ? (
                            <Button variant="outlined" color="neutral" onClick={() => setShowPasswordForm(true)}>
                                Change Password
                            </Button>
                        ) : (
                            <>
                                <FormControl>
                                    <FormLabel>Current Password</FormLabel>
                                    <Input
                                        type="password"
                                        value={oldPassword}
                                        onChange={(e) => setOldPassword(e.target.value)}
                                        placeholder="Enter current password"
                                    />
                                </FormControl>

                                <FormControl>
                                    <FormLabel>New Password</FormLabel>
                                    <Input
                                        type="password"
                                        value={newPassword}
                                        onChange={(e) => setNewPassword(e.target.value)}
                                        placeholder="Enter new password"
                                    />
                                    {newPassword && !validatePassword(newPassword).isValid && (
                                        <FormHelperText sx={{ color: 'danger.500' }}>
                                            Password must be 8+ chars with uppercase, lowercase, and number
                                        </FormHelperText>
                                    )}
                                </FormControl>

                                <FormControl>
                                    <FormLabel>Confirm New Password</FormLabel>
                                    <Input
                                        type="password"
                                        value={confirmNewPassword}
                                        onChange={(e) => setConfirmNewPassword(e.target.value)}
                                        placeholder="Confirm new password"
                                    />
                                    {confirmNewPassword && !doPasswordsMatch(newPassword, confirmNewPassword) && (
                                        <FormHelperText sx={{ color: 'danger.500' }}>
                                            Passwords do not match
                                        </FormHelperText>
                                    )}
                                </FormControl>

                                <Box sx={{ display: 'flex', gap: 1.5 }}>
                                    <Button
                                        onClick={handleChangePassword}
                                        loading={passwordChangeLoading}
                                        disabled={!oldPassword || !newPassword || !confirmNewPassword}
                                    >
                                        Save New Password
                                    </Button>
                                    <Button
                                        variant="outlined"
                                        color="neutral"
                                        onClick={() => {
                                            setShowPasswordForm(false);
                                            setOldPassword('');
                                            setNewPassword('');
                                            setConfirmNewPassword('');
                                            setPasswordChangeError(null);
                                        }}
                                    >
                                        Cancel
                                    </Button>
                                </Box>
                            </>
                        )}
                    </Box>
                </Card>

                {/* Privacy & Data */}
                <Card variant="outlined" sx={{ p: 3, borderColor: 'danger.300' }}>
                    <Typography level="title-lg" sx={{ mb: 1, color: 'danger.500', display: 'flex', alignItems: 'center', gap: 1 }}>
                        <DeleteIcon />
                        Privacy & Data
                    </Typography>
                    <Typography level="body-sm" sx={{ color: 'text.secondary', mb: 2 }}>
                        Delete your Roadrunner account when you no longer want it. Your profile,
                        travel profiles, chats, saved places, saved routes, and travel plans will be removed.
                    </Typography>
                    <Typography level="body-xs" sx={{ color: 'text.tertiary', mb: 3 }}>
                        Signed in as {accountEmail}
                    </Typography>

                    <Button
                        variant="outlined"
                        color="danger"
                        startDecorator={<DeleteIcon />}
                        onClick={() => setDeleteModalOpen(true)}
                    >
                        Delete my account
                    </Button>
                </Card>
            </Box>

            <Footer />

            {/* Delete Confirmation Modal */}
            <Modal open={deleteModalOpen} onClose={closeDeleteModal}>
                <ModalDialog variant="outlined" role="alertdialog">
                    <DialogTitle>
                        <WarningIcon sx={{ color: 'danger.500', mr: 1 }} />
                        Delete account and data
                    </DialogTitle>
                    <Divider />
                    <DialogContent>
                        <Typography level="body-md" sx={{ mb: 2 }}>
                            This permanently deletes your Roadrunner account for {accountEmail}. This action cannot be undone.
                        </Typography>
                        <Typography level="body-sm" sx={{ color: 'text.secondary', mb: 2 }}>
                            To continue, type your account email and confirm that you understand the deletion is permanent.
                        </Typography>
                        {deleteError && (
                            <Alert color="danger" sx={{ mb: 2 }}>
                                {deleteError}
                            </Alert>
                        )}
                        <FormControl sx={{ mb: 2 }}>
                            <FormLabel>Account email</FormLabel>
                            <Input
                                value={deleteConfirmation}
                                onChange={(e) => setDeleteConfirmation(e.target.value)}
                                placeholder={accountEmail}
                                autoComplete="off"
                                disabled={deleteLoading}
                            />
                            {deleteConfirmation && !deleteEmailMatches && (
                                <FormHelperText sx={{ color: 'danger.500' }}>
                                    Email must match {accountEmail}
                                </FormHelperText>
                            )}
                        </FormControl>
                        <Checkbox
                            label="I understand this will permanently delete my account and data"
                            checked={confirmDelete}
                            onChange={(e) => setConfirmDelete(e.target.checked)}
                            color="danger"
                            disabled={deleteLoading}
                        />
                    </DialogContent>
                    <DialogActions>
                        <Button
                            variant="plain"
                            color="neutral"
                            onClick={closeDeleteModal}
                            disabled={deleteLoading}
                        >
                            Cancel
                        </Button>
                        <Button
                            variant="solid"
                            color="danger"
                            disabled={!canDeleteAccount}
                            loading={deleteLoading}
                            onClick={handleDeleteAccount}
                        >
                            Delete account
                        </Button>
                    </DialogActions>
                </ModalDialog>
            </Modal>
        </Box>
    );
};

export default SettingsPage;
