import { useState, useEffect } from 'react';
import { useNavigate, Link as RouterLink, useSearchParams } from 'react-router-dom';
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
    LinearProgress,
} from '@mui/joy';
import { useColorScheme } from '@mui/joy/styles';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import GoogleIcon from '@mui/icons-material/Google';
import AppleIcon from '@mui/icons-material/Apple';
import EmailIcon from '@mui/icons-material/Email';
import LockIcon from '@mui/icons-material/Lock';
import PersonIcon from '@mui/icons-material/Person';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import FlightIcon from '@mui/icons-material/Flight';
import PublicIcon from '@mui/icons-material/Public';
import LocationOnIcon from '@mui/icons-material/LocationOn';
import ExploreIcon from '@mui/icons-material/Explore';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { useAppDispatch, useAppSelector } from '../store/hooks';
import { loginSuccess, signupSuccess, setLoading, setError } from '../store/authSlice';
import {
    isValidEmail,
    validatePassword,
    doPasswordsMatch,
    getEmailError,
    getNameError,
    getPasswordError,
    getConfirmPasswordError,
} from '../utils/validation';
import { RECAPTCHA_ACTIONS, executeRecaptchaToken } from '../utils/recaptcha';
import { authApi, extractErrorMessage } from '../services/userService';
import { mapUserDataToUser } from '../store/authSlice';
import beachBg from '../assets/beach-bg.png';
import RoadrunnerBirdLogo from '../components/RoadrunnerBirdLogo';

// Toggle Switch Component
const AuthToggle = ({ isSignUp, onToggle }: { isSignUp: boolean; onToggle: () => void }) => {
    return (
        <Box
            onClick={onToggle}
            sx={{
                display: 'flex',
                alignItems: 'center',
                backgroundColor: 'rgba(0, 191, 166, 0.15)',
                borderRadius: '30px',
                padding: '4px',
                cursor: 'pointer',
                position: 'relative',
                width: '220px',
                height: '44px',
                border: '2px solid',
                borderColor: 'primary.500',
                transition: 'all 0.3s ease',
                '&:hover': {
                    backgroundColor: 'rgba(0, 191, 166, 0.25)',
                },
            }}
        >
            {/* Sliding pill indicator */}
            <Box
                sx={{
                    position: 'absolute',
                    width: '50%',
                    height: '36px',
                    backgroundColor: 'primary.500',
                    borderRadius: '24px',
                    transition: 'transform 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
                    transform: isSignUp ? 'translateX(100%)' : 'translateX(0)',
                    boxShadow: '0 2px 8px rgba(0, 191, 166, 0.4)',
                }}
            />
            {/* Sign In label */}
            <Typography
                level="body-sm"
                sx={{
                    flex: 1,
                    textAlign: 'center',
                    fontWeight: 600,
                    color: !isSignUp ? '#fff' : 'text.primary',
                    zIndex: 1,
                    transition: 'color 0.3s ease',
                    userSelect: 'none',
                }}
            >
                Sign In
            </Typography>
            {/* Sign Up label */}
            <Typography
                level="body-sm"
                sx={{
                    flex: 1,
                    textAlign: 'center',
                    fontWeight: 600,
                    color: isSignUp ? '#fff' : 'text.primary',
                    zIndex: 1,
                    transition: 'color 0.3s ease',
                    userSelect: 'none',
                }}
            >
                Sign Up
            </Typography>
        </Box>
    );
};

// Floating animated icon component
const FloatingIcon = ({ icon, delay, duration, top, left }: {
    icon: React.ReactNode;
    delay: number;
    duration: number;
    top: string;
    left: string;
}) => (
    <Box
        sx={{
            position: 'absolute',
            top,
            left,
            opacity: 0.6,
            animation: `float ${duration}s ease-in-out ${delay}s infinite`,
            '@keyframes float': {
                '0%, 100%': { transform: 'translateY(0) rotate(0deg)' },
                '50%': { transform: 'translateY(-20px) rotate(10deg)' },
            },
        }}
    >
        {icon}
    </Box>
);

// Overlay component for inactive side
const InactiveOverlay = ({
    isVisible,
    isSignUp,
    onSwitch
}: {
    isVisible: boolean;
    isSignUp: boolean;
    onSwitch: () => void;
}) => {
    const { mode } = useColorScheme();

    return (
        <Box
            sx={{
                position: 'absolute',
                top: 0,
                left: 0,
                right: 0,
                bottom: 0,
                background: mode === 'dark'
                    ? 'linear-gradient(135deg, rgba(0, 191, 166, 0.92) 0%, rgba(0, 145, 124, 0.95) 50%, rgba(0, 83, 70, 0.97) 100%)'
                    : 'linear-gradient(135deg, rgba(0, 191, 166, 0.92) 0%, rgba(77, 212, 195, 0.9) 50%, rgba(0, 145, 124, 0.95) 100%)',
                backdropFilter: 'blur(8px)',
                borderRadius: 'inherit',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                opacity: isVisible ? 1 : 0,
                pointerEvents: isVisible ? 'auto' : 'none',
                transition: 'opacity 0.4s ease-in-out',
                overflow: 'hidden',
                zIndex: 10,
            }}
        >
            {/* Floating travel icons */}
            <FloatingIcon
                icon={<FlightIcon sx={{ fontSize: 32, color: 'rgba(255,255,255,0.4)' }} />}
                delay={0}
                duration={4}
                top="15%"
                left="20%"
            />
            <FloatingIcon
                icon={<PublicIcon sx={{ fontSize: 40, color: 'rgba(255,255,255,0.3)' }} />}
                delay={0.5}
                duration={5}
                top="60%"
                left="75%"
            />
            <FloatingIcon
                icon={<LocationOnIcon sx={{ fontSize: 28, color: 'rgba(255,255,255,0.35)' }} />}
                delay={1}
                duration={4.5}
                top="75%"
                left="15%"
            />
            <FloatingIcon
                icon={<ExploreIcon sx={{ fontSize: 36, color: 'rgba(255,255,255,0.3)' }} />}
                delay={1.5}
                duration={5.5}
                top="25%"
                left="80%"
            />

            {/* Content */}
            <Box sx={{ textAlign: 'center', px: 4, zIndex: 2 }}>
                <RoadrunnerBirdLogo size={48} sx={{ mb: 2, opacity: 0.9 }} />
                <Typography
                    level="h3"
                    sx={{
                        color: '#fff',
                        fontWeight: 700,
                        mb: 1,
                        textShadow: '0 2px 8px rgba(0,0,0,0.2)',
                    }}
                >
                    {isSignUp ? 'Already have an account?' : 'New here?'}
                </Typography>
                <Typography
                    level="body-md"
                    sx={{
                        color: 'rgba(255,255,255,0.9)',
                        mb: 3,
                        maxWidth: 280,
                    }}
                >
                    {isSignUp
                        ? 'Sign in to continue planning your dream adventures!'
                        : 'Create an account and start planning your perfect trip today!'}
                </Typography>
                <Button
                    variant="outlined"
                    onClick={onSwitch}
                    sx={{
                        borderColor: '#fff',
                        color: '#fff',
                        fontWeight: 600,
                        px: 4,
                        py: 1,
                        borderRadius: '24px',
                        transition: 'all 0.3s ease',
                        '&:hover': {
                            backgroundColor: 'rgba(255,255,255,0.15)',
                            borderColor: '#fff',
                            transform: 'scale(1.05)',
                        },
                    }}
                >
                    {isSignUp ? 'Sign In' : 'Sign Up'}
                </Button>
            </Box>
        </Box>
    );
};

// Sign In Form Component
const SignInForm = ({ isLoading }: { isLoading: boolean }) => {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [rememberMe, setRememberMe] = useState(false);
    const [touched, setTouched] = useState({ email: false, password: false });

    const navigate = useNavigate();
    const dispatch = useAppDispatch();
    const { error } = useAppSelector((state) => state.auth);
    const { executeRecaptcha } = useGoogleReCaptcha();

    const emailError = touched.email ? getEmailError(email) : null;
    const passwordError = touched.password ? getPasswordError(password) : null;

    const handleLogin = async (e: React.FormEvent) => {
        e.preventDefault();
        setTouched({ email: true, password: true });

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
        <Box sx={{ width: '100%' }}>
            <Typography level="h3" sx={{ mb: 0.5 }}>
                Welcome back
            </Typography>
            <Typography level="body-md" sx={{ color: 'text.secondary', mb: 3 }}>
                Sign in to continue planning your trips
            </Typography>

            {error && (
                <Alert color="danger" sx={{ mb: 2 }}>
                    {error}
                </Alert>
            )}

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

            <Box sx={{ display: 'flex', gap: 1.5, mb: 2 }}>
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

            <Typography
                level="body-xs"
                sx={{ textAlign: 'center', color: 'text.tertiary', mt: 2 }}
            >
                Protected by reCAPTCHA v3
            </Typography>
        </Box>
    );
};

// Sign Up Form Component
const SignUpForm = ({ isLoading }: { isLoading: boolean }) => {
    const [name, setName] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [showConfirmPassword, setShowConfirmPassword] = useState(false);
    const [agreeTerms, setAgreeTerms] = useState(false);
    const [touched, setTouched] = useState({
        name: false,
        email: false,
        password: false,
        confirmPassword: false,
    });

    const navigate = useNavigate();
    const dispatch = useAppDispatch();
    const { error } = useAppSelector((state) => state.auth);
    const { executeRecaptcha } = useGoogleReCaptcha();

    const passwordValidation = validatePassword(password);
    const nameError = touched.name ? getNameError(name) : null;
    const emailError = touched.email ? getEmailError(email) : null;
    const passwordErr = touched.password ? getPasswordError(password) : null;
    const confirmPasswordError = touched.confirmPassword
        ? getConfirmPasswordError(password, confirmPassword)
        : null;

    const getPasswordStrengthColor = () => {
        switch (passwordValidation.strength) {
            case 'strong':
                return 'success';
            case 'medium':
                return 'warning';
            default:
                return 'danger';
        }
    };

    const getPasswordStrengthValue = () => {
        switch (passwordValidation.strength) {
            case 'strong':
                return 100;
            case 'medium':
                return 66;
            default:
                return 33;
        }
    };

    const handleSignUp = async (e: React.FormEvent) => {
        e.preventDefault();
        setTouched({ name: true, email: true, password: true, confirmPassword: true });

        if (
            !name.trim() ||
            !isValidEmail(email) ||
            !passwordValidation.isValid ||
            !doPasswordsMatch(password, confirmPassword) ||
            !agreeTerms
        ) {
            if (!agreeTerms) {
                dispatch(setError('Please agree to the terms and conditions'));
            }
            return;
        }

        dispatch(setLoading(true));
        dispatch(setError(null));

        try {
            const token = await executeRecaptchaToken(executeRecaptcha, RECAPTCHA_ACTIONS.SIGNUP);
            if (!token) {
                dispatch(setError('reCAPTCHA failed. Please try again.'));
                dispatch(setLoading(false));
                return;
            }

            const response = await authApi.register(name, email, password, token);
            const user = mapUserDataToUser(response.user, false);
            dispatch(signupSuccess(user));

            navigate('/onboarding');
        } catch (error) {
            dispatch(setError(extractErrorMessage(error)));
        } finally {
            dispatch(setLoading(false));
        }
    };

    return (
        <Box sx={{ width: '100%' }}>
            <Typography level="h3" sx={{ mb: 0.5 }}>
                Create account
            </Typography>
            <Typography level="body-md" sx={{ color: 'text.secondary', mb: 3 }}>
                Start planning your dream trips today
            </Typography>

            {error && (
                <Alert color="danger" sx={{ mb: 2 }}>
                    {error}
                </Alert>
            )}

            <form onSubmit={handleSignUp}>
                <FormControl error={!!nameError} sx={{ mb: 2 }}>
                    <FormLabel>Full Name</FormLabel>
                    <Input
                        placeholder="John Doe"
                        value={name}
                        onChange={(e) => setName(e.target.value)}
                        onBlur={() => setTouched((prev) => ({ ...prev, name: true }))}
                        startDecorator={<PersonIcon />}
                        disabled={isLoading}
                    />
                    {nameError && <FormHelperText>{nameError}</FormHelperText>}
                </FormControl>

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

                <FormControl error={!!passwordErr} sx={{ mb: 1 }}>
                    <FormLabel>Password</FormLabel>
                    <Input
                        type={showPassword ? 'text' : 'password'}
                        placeholder="Create a strong password"
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
                    {passwordErr && <FormHelperText>{passwordErr}</FormHelperText>}
                </FormControl>

                {password && (
                    <Box sx={{ mb: 2 }}>
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                            <Typography level="body-xs" sx={{ color: 'text.secondary' }}>
                                Password strength
                            </Typography>
                            <Typography
                                level="body-xs"
                                sx={{
                                    color: `${getPasswordStrengthColor()}.500`,
                                    fontWeight: 600,
                                    textTransform: 'capitalize',
                                }}
                            >
                                {passwordValidation.strength}
                            </Typography>
                        </Box>
                        <LinearProgress
                            determinate
                            value={getPasswordStrengthValue()}
                            color={getPasswordStrengthColor()}
                            sx={{ height: 4, borderRadius: 2 }}
                        />
                        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mt: 1 }}>
                            {[
                                { check: passwordValidation.hasMinLength, label: '8+ chars' },
                                { check: passwordValidation.hasUppercase, label: 'Uppercase' },
                                { check: passwordValidation.hasLowercase, label: 'Lowercase' },
                                { check: passwordValidation.hasNumber, label: 'Number' },
                            ].map((item) => (
                                <Typography
                                    key={item.label}
                                    level="body-xs"
                                    startDecorator={
                                        item.check ? (
                                            <CheckCircleIcon sx={{ fontSize: 14, color: 'success.500' }} />
                                        ) : null
                                    }
                                    sx={{
                                        color: item.check ? 'success.600' : 'text.tertiary',
                                    }}
                                >
                                    {item.label}
                                </Typography>
                            ))}
                        </Box>
                    </Box>
                )}

                <FormControl error={!!confirmPasswordError} sx={{ mb: 2 }}>
                    <FormLabel>Confirm Password</FormLabel>
                    <Input
                        type={showConfirmPassword ? 'text' : 'password'}
                        placeholder="Confirm your password"
                        value={confirmPassword}
                        onChange={(e) => setConfirmPassword(e.target.value)}
                        onBlur={() => setTouched((prev) => ({ ...prev, confirmPassword: true }))}
                        startDecorator={<LockIcon />}
                        endDecorator={
                            <IconButton
                                variant="plain"
                                onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                                disabled={isLoading}
                            >
                                {showConfirmPassword ? <VisibilityOffIcon /> : <VisibilityIcon />}
                            </IconButton>
                        }
                        disabled={isLoading}
                    />
                    {confirmPasswordError && <FormHelperText>{confirmPasswordError}</FormHelperText>}
                </FormControl>

                <Checkbox
                    label={
                        <Typography level="body-sm">
                            I agree to the{' '}
                            <Link href="#" sx={{ fontWeight: 600 }}>
                                Terms of Service
                            </Link>{' '}
                            and{' '}
                            <Link href="#" sx={{ fontWeight: 600 }}>
                                Privacy Policy
                            </Link>
                        </Typography>
                    }
                    checked={agreeTerms}
                    onChange={(e) => setAgreeTerms(e.target.checked)}
                    disabled={isLoading}
                    size="sm"
                    sx={{ mb: 3 }}
                />

                <Button
                    type="submit"
                    fullWidth
                    size="lg"
                    loading={isLoading}
                    loadingIndicator={<CircularProgress size="sm" />}
                    sx={{ mb: 2 }}
                >
                    Create Account
                </Button>
            </form>

            <Divider sx={{ my: 1.5 }}>or sign up with</Divider>

            <Box sx={{ display: 'flex', gap: 1.5, mb: 1 }}>
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

            <Typography
                level="body-xs"
                sx={{ textAlign: 'center', color: 'text.tertiary', mt: 1 }}
            >
                Protected by reCAPTCHA v3
            </Typography>
        </Box>
    );
};

// Main AuthPage Component
const AuthPage = () => {
    const [searchParams] = useSearchParams();
    const mode = searchParams.get('mode');
    const [isSignUp, setIsSignUp] = useState(mode === 'signup');
    const { isLoading } = useAppSelector((state) => state.auth);

    // Update isSignUp when URL params change
    useEffect(() => {
        setIsSignUp(mode === 'signup');
    }, [mode]);

    const toggleAuth = () => {
        setIsSignUp(!isSignUp);
    };

    return (
        <Box
            sx={{
                minHeight: '100vh',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                position: 'relative',
                py: { xs: 10, md: 4 },
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

            {/* Back to Home Button - Fixed to viewport */}
            <Box
                component={RouterLink}
                to="/"
                sx={{
                    position: 'fixed',
                    top: 20,
                    left: 20,
                    zIndex: 1200,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 0.75,
                    textDecoration: 'none',
                    color: '#fff',
                    backgroundColor: 'primary.500',
                    borderRadius: '12px',
                    px: 2,
                    py: 1,
                    boxShadow: '0 4px 12px rgba(0, 191, 166, 0.4)',
                    transition: 'all 0.2s ease',
                    '&:hover': {
                        backgroundColor: 'primary.600',
                        transform: 'translateX(-2px)',
                        boxShadow: '0 6px 16px rgba(0, 191, 166, 0.5)',
                    },
                }}
            >
                <ArrowBackIcon sx={{ fontSize: 18, color: 'inherit' }} />
                <Typography
                    level="body-sm"
                    sx={{
                        color: 'inherit',
                        fontWeight: 600,
                    }}
                >
                    Back to Home
                </Typography>
            </Box>

            {/* Desktop Layout - Side by Side */}
            <Box
                sx={{
                    display: { xs: 'none', lg: 'flex' },
                    position: 'relative',
                    zIndex: 1,
                    width: '100%',
                    maxWidth: 1000,
                    gap: 0,
                }}
            >
                {/* Sign In Card */}
                <Card
                    variant="outlined"
                    sx={{
                        position: 'relative',
                        flex: 1,
                        p: 4,
                        pb: 12,
                        backgroundColor: 'background.surface',
                        backdropFilter: 'blur(20px)',
                        boxShadow: '0 16px 64px rgba(0,0,0,0.3)',
                        borderTopRightRadius: 0,
                        borderBottomRightRadius: 0,
                        borderRight: 'none',
                        overflow: 'hidden',
                        maxHeight: '90vh',
                        overflowY: 'auto',
                    }}
                >
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 3 }}>
                        <RoadrunnerBirdLogo size={28} />
                        <Typography level="h4" sx={{ fontWeight: 700 }}>
                            Roadrunner
                        </Typography>
                    </Box>
                    <SignInForm isLoading={isLoading} />
                    <InactiveOverlay isVisible={isSignUp} isSignUp={true} onSwitch={toggleAuth} />
                </Card>

                {/* Center Divider with Toggle */}
                <Box
                    sx={{
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        justifyContent: 'center',
                        position: 'relative',
                        zIndex: 20,
                        px: 0,
                    }}
                >
                    {/* Vertical line */}
                    <Box
                        sx={{
                            position: 'absolute',
                            top: 0,
                            bottom: 0,
                            width: '2px',
                            background: 'linear-gradient(180deg, transparent 0%, rgba(0,191,166,0.5) 20%, rgba(0,191,166,0.8) 50%, rgba(0,191,166,0.5) 80%, transparent 100%)',
                        }}
                    />
                    {/* Toggle positioned at bottom of divider */}
                    <Box
                        sx={{
                            position: 'absolute',
                            bottom: '30px',
                            transform: 'translateX(-50%)',
                            left: '50%',
                        }}
                    >
                        <AuthToggle isSignUp={isSignUp} onToggle={toggleAuth} />
                    </Box>
                </Box>

                {/* Sign Up Card */}
                <Card
                    variant="outlined"
                    sx={{
                        position: 'relative',
                        flex: 1,
                        p: 4,
                        pb: 12,
                        backgroundColor: 'background.surface',
                        backdropFilter: 'blur(20px)',
                        boxShadow: '0 16px 64px rgba(0,0,0,0.3)',
                        borderTopLeftRadius: 0,
                        borderBottomLeftRadius: 0,
                        borderLeft: 'none',
                        overflow: 'hidden',
                        maxHeight: '90vh',
                        overflowY: 'auto',
                    }}
                >
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 3 }}>
                        <RoadrunnerBirdLogo size={28} />
                        <Typography level="h4" sx={{ fontWeight: 700 }}>
                            Roadrunner
                        </Typography>
                    </Box>
                    <SignUpForm isLoading={isLoading} />
                    <InactiveOverlay isVisible={!isSignUp} isSignUp={false} onSwitch={toggleAuth} />
                </Card>
            </Box>

            {/* Mobile/Tablet Layout - Stacked */}
            <Card
                variant="outlined"
                sx={{
                    display: { xs: 'block', lg: 'none' },
                    position: 'relative',
                    zIndex: 1,
                    width: '100%',
                    maxWidth: 480,
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
                        Roadrunner
                    </Typography>
                </Box>

                {/* Toggle at top for mobile */}
                <Box sx={{ display: 'flex', justifyContent: 'center', mb: 3 }}>
                    <AuthToggle isSignUp={isSignUp} onToggle={toggleAuth} />
                </Box>

                {/* Forms with animation */}
                <Box sx={{ position: 'relative', overflow: 'hidden' }}>
                    <Box
                        sx={{
                            display: isSignUp ? 'none' : 'block',
                            animation: !isSignUp ? 'fadeIn 0.3s ease' : 'none',
                            '@keyframes fadeIn': {
                                from: { opacity: 0, transform: 'translateX(-20px)' },
                                to: { opacity: 1, transform: 'translateX(0)' },
                            },
                        }}
                    >
                        <SignInForm isLoading={isLoading} />
                    </Box>
                    <Box
                        sx={{
                            display: isSignUp ? 'block' : 'none',
                            animation: isSignUp ? 'fadeIn 0.3s ease' : 'none',
                        }}
                    >
                        <SignUpForm isLoading={isLoading} />
                    </Box>
                </Box>

                {/* Switch link for mobile */}
                <Typography level="body-sm" sx={{ textAlign: 'center', mt: 2 }}>
                    {isSignUp ? 'Already have an account? ' : "Don't have an account? "}
                    <Link
                        component="button"
                        onClick={toggleAuth}
                        sx={{ fontWeight: 600, cursor: 'pointer' }}
                    >
                        {isSignUp ? 'Sign In' : 'Sign Up'}
                    </Link>
                </Typography>
            </Card>
        </Box>
    );
};

export default AuthPage;
