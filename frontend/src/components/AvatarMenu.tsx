import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    Avatar,
    Box,
    Dropdown,
    Menu,
    MenuButton,
    MenuItem,
    ListDivider,
    Typography,
} from '@mui/joy';
import PersonIcon from '@mui/icons-material/Person';
import SettingsIcon from '@mui/icons-material/Settings';
import LogoutIcon from '@mui/icons-material/Logout';
import { useAppSelector, useAppDispatch } from '../store/hooks';
import { logout } from '../store/authSlice';

const AvatarMenu = () => {
    const [open, setOpen] = useState(false);
    const navigate = useNavigate();
    const dispatch = useAppDispatch();
    const { user } = useAppSelector((state) => state.auth);

    const handleLogout = () => {
        dispatch(logout());
        navigate('/');
        setOpen(false);
    };

    const handleProfileClick = () => {
        navigate('/profile');
        setOpen(false);
    };

    const handleSettingsClick = () => {
        navigate('/settings');
        setOpen(false);
    };

    // Get initials for avatar
    const getInitials = (name: string) => {
        return name
            .split(' ')
            .map((n) => n[0])
            .join('')
            .toUpperCase()
            .slice(0, 2);
    };

    if (!user) return null;

    return (
        <Dropdown open={open} onOpenChange={(_, isOpen) => setOpen(isOpen)}>
            <MenuButton
                slots={{ root: Box }}
                slotProps={{
                    root: {
                        sx: {
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            gap: 1,
                        },
                    },
                }}
            >
                <Avatar
                    src={user.avatar}
                    alt={user.name}
                    size="sm"
                    sx={{
                        border: '2px solid',
                        borderColor: 'primary.500',
                        transition: 'transform 0.2s',
                        '&:hover': {
                            transform: 'scale(1.05)',
                        },
                    }}
                >
                    {!user.avatar && getInitials(user.name)}
                </Avatar>
            </MenuButton>

            <Menu
                placement="bottom-end"
                sx={{
                    minWidth: 200,
                    p: 1,
                    zIndex: 1200, // Higher than header's z-index (1100)
                    mt: 1, // Add margin top to prevent overlap
                }}
            >
                {/* User Info */}
                <Box sx={{ px: 1.5, py: 1, mb: 0.5 }}>
                    <Typography level="title-sm" sx={{ fontWeight: 600 }}>
                        {user.name}
                    </Typography>
                    <Typography level="body-xs" sx={{ color: 'text.secondary' }}>
                        {user.email}
                    </Typography>
                </Box>

                <ListDivider />

                {/* Menu Items */}
                <MenuItem onClick={handleProfileClick}>
                    <PersonIcon sx={{ mr: 1.5, fontSize: 20 }} />
                    Profile
                </MenuItem>
                <MenuItem onClick={handleSettingsClick}>
                    <SettingsIcon sx={{ mr: 1.5, fontSize: 20 }} />
                    Settings
                </MenuItem>

                <ListDivider />

                <MenuItem onClick={handleLogout} color="danger">
                    <LogoutIcon sx={{ mr: 1.5, fontSize: 20 }} />
                    Log out
                </MenuItem>
            </Menu>
        </Dropdown>
    );
};

export default AvatarMenu;
