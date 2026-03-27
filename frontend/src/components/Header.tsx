import { useState } from 'react';
import { useNavigate, useLocation, Link as RouterLink } from 'react-router-dom';
import {
    Box,
    IconButton,
    Button,
    Typography,
    Sheet,
    Drawer,
    List,
    ListItem,
    ListItemButton,
    Divider,
    Avatar,
} from '@mui/joy';
import { useColorScheme } from '@mui/joy/styles';
import MenuIcon from '@mui/icons-material/Menu';
import CloseIcon from '@mui/icons-material/Close';
import ExploreIcon from '@mui/icons-material/Explore';
import roadrunnerLogo from '../assets/roadrunner.jpg';
import LightModeIcon from '@mui/icons-material/LightMode';
import DarkModeIcon from '@mui/icons-material/DarkMode';
import PersonIcon from '@mui/icons-material/Person';
import SettingsIcon from '@mui/icons-material/Settings';
import LogoutIcon from '@mui/icons-material/Logout';
import AvatarMenu from './AvatarMenu';
import RoadrunnerBirdLogo from './RoadrunnerBirdLogo';
import { useAppSelector, useAppDispatch } from '../store/hooks';
import { logout } from '../store/authSlice';

const Header = () => {
    const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
    const navigate = useNavigate();
    const location = useLocation();
    const { mode, setMode } = useColorScheme();
    const { isAuthenticated, user } = useAppSelector((state) => state.auth);
    const dispatch = useAppDispatch();

    const isLandingPage = location.pathname === '/';

    const toggleTheme = () => {
        setMode(mode === 'light' ? 'dark' : 'light');
    };

    const handleLogout = () => {
        dispatch(logout());
        navigate('/');
        setMobileMenuOpen(false);
    };

    const getInitials = (name: string) => {
        return name
            .split(' ')
            .map((n) => n[0])
            .join('')
            .toUpperCase()
            .slice(0, 2);
    };

    const navLinks = [
        { label: 'Start Planning', path: '/chat', icon: <RoadrunnerBirdLogo size={20} /> },
        { label: 'Explore', path: '/explore', icon: <ExploreIcon /> },
    ];

    return (
        <>
            <Sheet
                component="header"
                sx={{
                    position: 'fixed',
                    top: 0,
                    left: 0,
                    right: 0,
                    zIndex: 1100,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    px: { xs: 2, md: 4 },
                    py: 1.5,
                    backgroundColor: isLandingPage
                        ? 'transparent'
                        : mode === 'dark'
                            ? 'rgba(13, 27, 42, 0.95)'
                            : 'rgba(255, 255, 255, 0.95)',
                    backdropFilter: isLandingPage ? 'none' : 'blur(10px)',
                    borderBottom: isLandingPage ? 'none' : '1px solid',
                    borderColor: mode === 'dark' ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.1)',
                    transition: 'all 0.3s ease',
                }}
            >
                <Box
                    component={RouterLink}
                    to="/"
                    sx={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 1,
                        textDecoration: 'none',
                        color: isLandingPage ? '#fff' : 'text.primary',
                    }}
                >
                    <img src={roadrunnerLogo} alt="Roadrunner logo" style={{ width: 28, height: 28, borderRadius: '50%', objectFit: 'cover' }} />
                    <Typography
                        level="h4"
                        sx={{
                            fontWeight: 700,
                            color: 'inherit',
                            textShadow: isLandingPage ? '0 2px 4px rgba(0,0,0,0.3)' : 'none',
                        }}
                    >
                        Roadrunner
                    </Typography>
                </Box>

                {/* Desktop Navigation */}
                <Box
                    sx={{
                        display: { xs: 'none', md: 'flex' },
                        alignItems: 'center',
                        gap: 3,
                    }}
                >
                    {navLinks.map((link) => (
                        <Typography
                            key={link.path}
                            component={RouterLink}
                            to={link.path}
                            level="body-md"
                            sx={{
                                color: isLandingPage ? '#fff' : 'text.primary',
                                textDecoration: 'none',
                                fontWeight: 500,
                                textShadow: isLandingPage ? '0 2px 4px rgba(0,0,0,0.3)' : 'none',
                                transition: 'color 0.2s',
                                '&:hover': {
                                    color: 'primary.500',
                                },
                            }}
                        >
                            {link.label}
                        </Typography>
                    ))}

                    {/* Theme Toggle */}
                    <IconButton
                        variant="plain"
                        onClick={toggleTheme}
                        sx={{
                            color: isLandingPage ? '#fff' : 'text.primary',
                        }}
                    >
                        {mode === 'light' ? <DarkModeIcon /> : <LightModeIcon />}
                    </IconButton>

                    {/* Auth - Avatar or Login/Signup */}
                    {isAuthenticated ? (
                        <AvatarMenu />
                    ) : (
                        <Box sx={{ display: 'flex', gap: 1.5 }}>
                            <Button
                                variant="outlined"
                                color="neutral"
                                onClick={() => navigate('/auth?mode=signin')}
                                sx={{
                                    borderColor: isLandingPage ? '#fff' : undefined,
                                    color: isLandingPage ? '#fff' : undefined,
                                    '&:hover': {
                                        borderColor: 'primary.500',
                                        color: 'primary.500',
                                        backgroundColor: isLandingPage ? 'rgba(255,255,255,0.1)' : undefined,
                                    },
                                }}
                            >
                                Login
                            </Button>
                            <Button
                                variant="solid"
                                color="primary"
                                onClick={() => navigate('/auth?mode=signup')}
                            >
                                Sign Up
                            </Button>
                        </Box>
                    )}
                </Box>

                {/* Mobile Menu Button */}
                <Box sx={{ display: { xs: 'flex', md: 'none' }, alignItems: 'center', gap: 1 }}>
                    <IconButton
                        variant="plain"
                        onClick={toggleTheme}
                        sx={{
                            color: isLandingPage ? '#fff' : 'text.primary',
                            textShadow: isLandingPage ? '0 1px 4px rgba(0,0,0,0.5)' : 'none',
                            '&:hover': {
                                backgroundColor: isLandingPage ? 'rgba(255,255,255,0.1)' : undefined,
                            },
                        }}
                    >
                        {mode === 'light' ? <DarkModeIcon /> : <LightModeIcon />}
                    </IconButton>
                    <IconButton
                        variant="plain"
                        onClick={() => setMobileMenuOpen(true)}
                        sx={{
                            color: isLandingPage ? '#fff' : 'text.primary',
                            textShadow: isLandingPage ? '0 1px 4px rgba(0,0,0,0.5)' : 'none',
                            '&:hover': {
                                backgroundColor: isLandingPage ? 'rgba(255,255,255,0.1)' : undefined,
                            },
                        }}
                    >
                        <MenuIcon />
                    </IconButton>
                </Box>
            </Sheet>

            {/* Mobile Drawer */}
            <Drawer
                open={mobileMenuOpen}
                onClose={() => setMobileMenuOpen(false)}
                anchor="right"
                size="sm"
                slotProps={{
                    content: {
                        sx: {
                            display: 'flex',
                            flexDirection: 'column',
                        },
                    },
                }}
            >
                <Box sx={{ p: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <Typography level="h4" sx={{ fontWeight: 700 }}>
                        Menu
                    </Typography>
                    <IconButton variant="plain" onClick={() => setMobileMenuOpen(false)}>
                        <CloseIcon />
                    </IconButton>
                </Box>
                <Divider />

                {/* User Info (Mobile) */}
                {isAuthenticated && user && (
                    <>
                        <Box sx={{ p: 2, display: 'flex', alignItems: 'center', gap: 2 }}>
                            <Avatar
                                src={user.avatar}
                                size="lg"
                                sx={{ border: '2px solid', borderColor: 'primary.500' }}
                            >
                                {!user.avatar && getInitials(user.name)}
                            </Avatar>
                            <Box>
                                <Typography level="title-md">{user.name}</Typography>
                                <Typography level="body-sm" sx={{ color: 'text.secondary' }}>
                                    {user.email}
                                </Typography>
                            </Box>
                        </Box>
                        <Divider />
                    </>
                )}

                {/* All navigation items in one list */}
                <List>
                    {navLinks.map((link) => (
                        <ListItem key={link.path}>
                            <ListItemButton
                                onClick={() => {
                                    if (link.path === '/chat') {
                                        const chatId = `chat-${Date.now()}`;
                                        navigate(`/chat/${chatId}`);
                                    } else {
                                        navigate(link.path);
                                    }
                                    setMobileMenuOpen(false);
                                }}
                                sx={{ gap: 2 }}
                            >
                                {link.icon}
                                {link.label}
                            </ListItemButton>
                        </ListItem>
                    ))}

                    {/* Profile and Settings - only when authenticated */}
                    {isAuthenticated && (
                        <>
                            <Divider sx={{ my: 1 }} />
                            <ListItem>
                                <ListItemButton
                                    onClick={() => {
                                        navigate('/profile');
                                        setMobileMenuOpen(false);
                                    }}
                                    sx={{ gap: 2 }}
                                >
                                    <PersonIcon />
                                    Profile
                                </ListItemButton>
                            </ListItem>
                            <ListItem>
                                <ListItemButton
                                    onClick={() => {
                                        navigate('/settings');
                                        setMobileMenuOpen(false);
                                    }}
                                    sx={{ gap: 2 }}
                                >
                                    <SettingsIcon />
                                    Settings
                                </ListItemButton>
                            </ListItem>
                            <Divider sx={{ my: 1 }} />
                            <ListItem>
                                <ListItemButton
                                    onClick={handleLogout}
                                    sx={{ gap: 2, color: 'danger.500' }}
                                >
                                    <LogoutIcon />
                                    Log out
                                </ListItemButton>
                            </ListItem>
                        </>
                    )}
                </List>

                {/* Login/Signup buttons for unauthenticated users */}
                {!isAuthenticated && (
                    <Box sx={{ p: 2, display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                        <Button
                            variant="outlined"
                            color="neutral"
                            onClick={() => {
                                navigate('/auth?mode=signin');
                                setMobileMenuOpen(false);
                            }}
                        >
                            Login
                        </Button>
                        <Button
                            variant="solid"
                            color="primary"
                            onClick={() => {
                                navigate('/auth?mode=signup');
                                setMobileMenuOpen(false);
                            }}
                        >
                            Sign Up
                        </Button>
                    </Box>
                )}
            </Drawer>
        </>
    );
};

export default Header;
