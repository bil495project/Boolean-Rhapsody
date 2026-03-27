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
    LinearProgress,
} from '@mui/joy';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import GoogleIcon from '@mui/icons-material/Google';
import AppleIcon from '@mui/icons-material/Apple';
import EmailIcon from '@mui/icons-material/Email';
import LockIcon from '@mui/icons-material/Lock';
import PersonIcon from '@mui/icons-material/Person';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import { useAppDispatch, useAppSelector } from '../store/hooks';
import { signupSuccess, setLoading, setError } from '../store/authSlice';
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

const SignUpPage = () => {
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
    const { isLoading, error } = useAppSelector((state) => state.auth);
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

        // Validate form
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

            {/* Sign Up Card */}
            <Card
                variant="outlined"
                sx={{
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
                        TravelPlanner AI
                    </Typography>
                </Box>

                <Typography level="h3" sx={{ mb: 0.5 }}>
                    Create account
                </Typography>
                <Typography level="body-md" sx={{ color: 'text.secondary', mb: 3 }}>
                    Start planning your dream trips today
                </Typography>

                {/* Error Alert */}
                {error && (
                    <Alert color="danger" sx={{ mb: 2 }}>
                        {error}
                    </Alert>
                )}

                {/* Form */}
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

                    {/* Password Strength Indicator */}
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

                <Divider sx={{ my: 2 }}>or sign up with</Divider>

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

                {/* Login Link */}
                <Typography level="body-sm" sx={{ textAlign: 'center' }}>
                    Already have an account?{' '}
                    <Link component={RouterLink} to="/login" sx={{ fontWeight: 600 }}>
                        Sign in
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

export default SignUpPage;
