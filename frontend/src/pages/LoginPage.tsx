import { useState } from 'react';
import { useNavigate, Link as RouterLink } from 'react-router-dom';
import { useGoogleReCaptcha } from 'react-google-recaptcha-v3';
import {
    Box,
    Card,
    Typography,
    Input,
    Button,
    Checkbox,
    Divider,
    Link,
    FormControl,
    FormLabel,
    FormHelperText,
    IconButton,
    CircularProgress,
    Alert,
} from '@mui/joy';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import GoogleIcon from '@mui/icons-material/Google';
import AppleIcon from '@mui/icons-material/Apple';
import EmailIcon from '@mui/icons-material/Email';
import LockIcon from '@mui/icons-material/Lock';
import { useAppDispatch, useAppSelector } from '../store/hooks';
import { loginSuccess, setLoading, setError } from '../store/authSlice';
import { isValidEmail, getEmailError, getPasswordError } from '../utils/validation';
import { RECAPTCHA_ACTIONS, executeRecaptchaToken } from '../utils/recaptcha';
import { authApi, extractErrorMessage } from '../services/userService';
import { mapUserDataToUser } from '../store/authSlice';
import beachBg from '../assets/beach-bg.png';
import RoadrunnerBirdLogo from '../components/RoadrunnerBirdLogo';

const LoginPage = () => {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [rememberMe, setRememberMe] = useState(false);
    const [touched, setTouched] = useState({ email: false, password: false });

    const navigate = useNavigate();
    const dispatch = useAppDispatch();
    const { isLoading, error } = useAppSelector((state) => state.auth);
    const { executeRecaptcha } = useGoogleReCaptcha();

    const emailError = touched.email ? getEmailError(email) : null;
    const passwordError = touched.password ? getPasswordError(password) : null;

    const handleLogin = async (e: React.FormEvent) => {
        e.preventDefault();
        setTouched({ email: true, password: true });

        // Validate form
        if (!isValidEmail(email) || !password) {
            return;
        }

        dispatch(setLoading(true));
        dispatch(setError(null));

        try {
            const token = await executeRecaptchaToken(executeRecaptcha, RECAPTCHA_ACTIONS.LOGIN);
            if (!token) {
                dispatch(setError('reCAPTCHA failed. Please try again.'));
                dispatch(setLoading(false));
                return;
            }

            const response = await authApi.login(email, password, token);
            const user = mapUserDataToUser(response.user);
            dispatch(loginSuccess(user));

            navigate('/explore');
        } catch (error) {
            dispatch(setError(extractErrorMessage(error)));
        } finally {
            dispatch(setLoading(false));
        }
    };

    return (
        <Box
            sx={{
                minHeight: '100vh',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                position: 'relative',
                py: { xs: 8, md: 4 },
                px: 2,
            }}
        >
            {/* Background */}
            <Box
                sx={{
                    position: 'absolute',
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
                    backgroundImage: `url(${beachBg})`,
                    backgroundSize: 'cover',
                    backgroundPosition: 'center',
                    filter: 'blur(8px)',
                    transform: 'scale(1.1)',
                    '&::after': {
                        content: '""',
                        position: 'absolute',
                        top: 0,
                        left: 0,
                        right: 0,
                        bottom: 0,
                        background: 'rgba(0,0,0,0.5)',
                    },
                }}
            />

            {/* Login Card */}
            <Card
                variant="outlined"
                sx={{
                    position: 'relative',
                    zIndex: 1,
                    width: '100%',
                    maxWidth: 440,
                    p: { xs: 3, sm: 4 },
                    backgroundColor: 'background.surface',
                    backdropFilter: 'blur(20px)',
                    boxShadow: '0 16px 64px rgba(0,0,0,0.3)',
                }}
            >
                {/* Logo */}
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 3 }}>
                    <RoadrunnerBirdLogo size={28} />
                    <Typography level="h4" sx={{ fontWeight: 700 }}>
                        TravelPlanner AI
                    </Typography>
                </Box>

                <Typography level="h3" sx={{ mb: 0.5 }}>
                    Welcome back
                </Typography>
                <Typography level="body-md" sx={{ color: 'text.secondary', mb: 3 }}>
                    Sign in to continue planning your trips
                </Typography>

                {/* Error Alert */}
                {error && (
                    <Alert color="danger" sx={{ mb: 2 }}>
                        {error}
                    </Alert>
                )}

                {/* Form */}
                <form onSubmit={handleLogin}>
                    <FormControl error={!!emailError} sx={{ mb: 2 }}>
                        <FormLabel>Email</FormLabel>
                        <Input
                            type="email"
                            placeholder="you@example.com"
                            value={email}
                            onChange={(e) => setEmail(e.target.value)}
                            onBlur={() => setTouched((prev) => ({ ...prev, email: true }))}
                            startDecorator={<EmailIcon />}
                            disabled={isLoading}
                        />
                        {emailError && <FormHelperText>{emailError}</FormHelperText>}
                    </FormControl>

                    <FormControl error={!!passwordError} sx={{ mb: 2 }}>
                        <FormLabel>Password</FormLabel>
                        <Input
                            type={showPassword ? 'text' : 'password'}
                            placeholder="Enter your password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            onBlur={() => setTouched((prev) => ({ ...prev, password: true }))}
                            startDecorator={<LockIcon />}
                            endDecorator={
                                <IconButton
                                    variant="plain"
                                    onClick={() => setShowPassword(!showPassword)}
                                    disabled={isLoading}
                                >
                                    {showPassword ? <VisibilityOffIcon /> : <VisibilityIcon />}
                                </IconButton>
                            }
                            disabled={isLoading}
                        />
                        {passwordError && <FormHelperText>{passwordError}</FormHelperText>}
                    </FormControl>

                    <Box
                        sx={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                            mb: 3,
                        }}
                    >
                        <Checkbox
                            label="Remember me"
                            checked={rememberMe}
                            onChange={(e) => setRememberMe(e.target.checked)}
                            disabled={isLoading}
                            size="sm"
                        />
                        <Link level="body-sm" href="#" sx={{ color: 'primary.500' }}>
                            Forgot password?
                        </Link>
                    </Box>

                    <Button
                        type="submit"
                        fullWidth
                        size="lg"
                        loading={isLoading}
                        loadingIndicator={<CircularProgress size="sm" />}
                        sx={{ mb: 2 }}
                    >
                        Sign In
                    </Button>
                </form>

                <Divider sx={{ my: 2 }}>or continue with</Divider>

                {/* Social Login */}
                <Box sx={{ display: 'flex', gap: 1.5, mb: 3 }}>
                    <Button
                        variant="outlined"
                        color="neutral"
                        fullWidth
                        startDecorator={<GoogleIcon />}
                        disabled={isLoading}
                    >
                        Google
                    </Button>
                    <Button
                        variant="outlined"
                        color="neutral"
                        fullWidth
                        startDecorator={<AppleIcon />}
                        disabled={isLoading}
                    >
                        Apple
                    </Button>
                </Box>

                {/* Sign Up Link */}
                <Typography level="body-sm" sx={{ textAlign: 'center' }}>
                    Don't have an account?{' '}
                    <Link component={RouterLink} to="/signup" sx={{ fontWeight: 600 }}>
                        Sign up
                    </Link>
                </Typography>

                {/* reCAPTCHA Notice */}
                <Typography
                    level="body-xs"
                    sx={{ textAlign: 'center', color: 'text.tertiary', mt: 2 }}
                >
                    Protected by reCAPTCHA v3
                </Typography>
            </Card>
        </Box>
    );
};

export default LoginPage;
