import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import {
    Box,
    Typography,
    IconButton,
    List,
    ListItem,
    ListItemButton,
    ListItemContent,
    ListItemDecorator,
    Avatar,
    Divider,
    Button,
    Dropdown,
    Menu,
    MenuButton,
    MenuItem,
    ListDivider,
    Modal,
    ModalDialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Input,
    Snackbar,
} from '@mui/joy';
import WarningRoundedIcon from '@mui/icons-material/WarningRounded';
import { useColorScheme } from '@mui/joy/styles';
import ChatBubbleOutlineIcon from '@mui/icons-material/ChatBubbleOutline';
import NavigationIcon from '@mui/icons-material/Navigation';
import ExploreIcon from '@mui/icons-material/Explore';
import BookmarkBorderIcon from '@mui/icons-material/BookmarkBorder';
import BookmarkAddedIcon from '@mui/icons-material/BookmarkAdded';
import AddIcon from '@mui/icons-material/Add';
import MenuIcon from '@mui/icons-material/Menu';
import MoreHorizIcon from '@mui/icons-material/MoreHoriz';
import EditIcon from '@mui/icons-material/Edit';
import PushPinIcon from '@mui/icons-material/PushPin';
import DeleteIcon from '@mui/icons-material/Delete';
import LightModeIcon from '@mui/icons-material/LightMode';
import DarkModeIcon from '@mui/icons-material/DarkMode';
import SettingsIcon from '@mui/icons-material/Settings';
import PersonIcon from '@mui/icons-material/Person';
import LogoutIcon from '@mui/icons-material/Logout';
import MapIcon from '@mui/icons-material/Map';
import RouteIcon from '@mui/icons-material/Route';
import RoadrunnerBirdLogo from '../RoadrunnerBirdLogo';
import { useAppSelector, useAppDispatch } from '../../store/hooks';
import { toggleSidebar, setActiveChat, deleteChatAsync, updateChatTitleAsync, toggleMapFullscreen } from '../../store/chatSlice';
import { logout } from '../../store/authSlice';

interface ChatSidebarProps {
    mobile?: boolean;
    onClose?: () => void;
}

const ChatSidebar = ({ mobile = false, onClose }: ChatSidebarProps) => {
    const navigate = useNavigate();
    const location = useLocation();
    const dispatch = useAppDispatch();
    const { mode, setMode } = useColorScheme();
    const { user } = useAppSelector((state) => state.auth);
    const { chats, activeChat, sidebarOpen, mapFullscreen } = useAppSelector((state) => state.chat);

    const [renameModalOpen, setRenameModalOpen] = useState(false);
    const [chatToRename, setChatToRename] = useState<string | null>(null);
    const [newChatName, setNewChatName] = useState('');

    const [deleteModalOpen, setDeleteModalOpen] = useState(false);
    const [chatToDelete, setChatToDelete] = useState<string | null>(null);
    const [deleteSnackbarOpen, setDeleteSnackbarOpen] = useState(false);
    const [errorSnackbarOpen, setErrorSnackbarOpen] = useState(false);

    const navItems = [
        { label: 'Chats', icon: <ChatBubbleOutlineIcon />, path: '/chat' },
        { label: 'Explore', icon: <ExploreIcon />, path: '/explore' },
        { label: 'Saved', icon: <BookmarkBorderIcon />, path: '/saved' },
        { label: 'Saved Routes', icon: <BookmarkAddedIcon />, path: '/saved-routes' },
        { label: 'Route', icon: <RouteIcon />, path: '/route' },
        { label: 'Navigation', icon: <NavigationIcon />, path: '/navigation' },
    ];

    const handleNewChat = () => {
        // Navigate to new chat view - chat will be created when user sends first message
        navigate('/chat/new', { replace: false });
        onClose?.();
    };

    const handleChatClick = (chatId: string) => {
        console.log('handleChatClick fired for:', chatId);
        dispatch(setActiveChat(chatId));
        navigate(`/chat/${chatId}`);
        onClose?.();
    };

    const handleNavClick = (path: string) => {
        if (path === '/chat') {
            // Go to chat - use existing or new
            if (activeChat) {
                navigate(`/chat/${activeChat.id}`);
            } else if (chats.length > 0) {
                dispatch(setActiveChat(chats[0].id));
                navigate(`/chat/${chats[0].id}`);
            } else {
                handleNewChat();
            }
        } else {
            // Navigate to the route (Explore or Saved)
            navigate(path);
        }
        onClose?.();
    };

    const handleLogoClick = () => {
        navigate('/');
        onClose?.();
    };

    const handleRenameChat = (chatId: string, currentTitle: string) => {
        setChatToRename(chatId);
        setNewChatName(currentTitle);
        setRenameModalOpen(true);
    };

    const handleConfirmRename = () => {
        if (chatToRename && newChatName.trim()) {
            dispatch(updateChatTitleAsync({ chatId: chatToRename, title: newChatName.trim() }));
        }
        setRenameModalOpen(false);
        setChatToRename(null);
        setNewChatName('');
    };

    const handleDeleteChat = (chatId: string) => {
        console.log('handleDeleteChat fired for:', chatId);
        setChatToDelete(chatId);
        setDeleteModalOpen(true);
    };

    const handleConfirmDelete = async () => {
        if (!chatToDelete) return;
        console.log('handleConfirmDelete confirmed for:', chatToDelete);

        // Get remaining chats after deletion
        const remainingChats = chats.filter((c) => c.id !== chatToDelete);

        try {
            const resultAction = await dispatch(deleteChatAsync(chatToDelete));
            
            if (deleteChatAsync.fulfilled.match(resultAction)) {
                // Navigate to another chat or create new one if the active chat was deleted
                if (activeChat?.id === chatToDelete) {
                    if (remainingChats.length > 0) {
                        const nextChat = remainingChats[0];
                        dispatch(setActiveChat(nextChat.id));
                        navigate(`/chat/${nextChat.id}`);
                    } else {
                        handleNewChat();
                    }
                }
                setDeleteSnackbarOpen(true);
            } else {
                console.error('Delete failed:', resultAction.payload);
                setErrorSnackbarOpen(true);
            }
        } catch (error) {
            console.error('Unexpected error during delete:', error);
        } finally {
            setDeleteModalOpen(false);
            setChatToDelete(null);
        }
    };

    const handleLogout = () => {
        dispatch(logout());
        navigate('/');
        onClose?.();
    };

    const handleViewMapToggle = () => {
        dispatch(toggleMapFullscreen());
        onClose?.();
    };

    const getInitials = (name: string) => {
        return name
            .split(' ')
            .map((n) => n[0])
            .join('')
            .toUpperCase()
            .slice(0, 2);
    };

    if (!sidebarOpen && !mobile) {
        return null;
    }

    return (
        <Box
            sx={{
                width: mobile ? '100%' : 260,
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
                bgcolor: 'background.surface',
                borderRight: mobile ? 'none' : '1px solid',
                borderColor: 'divider',
            }}
        >
            {/* Header - Clickable logo */}
            <Box
                onClick={handleLogoClick}
                sx={{
                    p: 2,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 1.5,
                    cursor: 'pointer',
                    transition: 'opacity 0.2s',
                    '&:hover': {
                        opacity: 0.8,
                    },
                }}
            >
                <RoadrunnerBirdLogo size={28} />
                <Typography level="h4" sx={{ fontWeight: 700, flex: 1 }}>
                    TravelPlanner
                </Typography>
                {!mobile && (
                    <IconButton
                        variant="plain"
                        size="sm"
                        onClick={(e) => {
                            e.stopPropagation();
                            dispatch(toggleSidebar());
                        }}
                    >
                        <MenuIcon />
                    </IconButton>
                )}
            </Box>

            {/* Navigation */}
            <List size="sm" sx={{ px: 1 }}>
                {navItems.map((item) => {
                    // Determine if this item is selected based on current URL
                    const isSelected = item.path === '/chat'
                        ? location.pathname.startsWith('/chat')
                        : item.path === '/route'
                            ? location.pathname.startsWith('/route')
                            : item.path === '/saved-routes'
                                ? location.pathname.startsWith('/saved-routes')
                                : location.pathname === item.path;

                    return (
                        <ListItem key={item.path}>
                            <ListItemButton
                                selected={isSelected}
                                onClick={() => handleNavClick(item.path)}
                                sx={{
                                    borderRadius: 'md',
                                    fontWeight: 500,
                                }}
                            >
                                <ListItemDecorator>{item.icon}</ListItemDecorator>
                                <ListItemContent>{item.label}</ListItemContent>
                            </ListItemButton>
                        </ListItem>
                    );
                })}
            </List>

            {/* Mobile: View Map button */}
            {mobile && (
                <>
                    <Divider sx={{ my: 1 }} />
                    <Box sx={{ px: 2, py: 1 }}>
                        <Button
                            variant={mapFullscreen ? 'solid' : 'soft'}
                            color="primary"
                            fullWidth
                            startDecorator={<MapIcon />}
                            onClick={handleViewMapToggle}
                            sx={{ justifyContent: 'flex-start', fontWeight: 500 }}
                        >
                            {mapFullscreen ? 'Hide Map' : 'View Map'}
                        </Button>
                    </Box>
                </>
            )}

            <Divider sx={{ my: 1 }} />

            {/* New Chat Button */}
            <Box sx={{ px: 2, py: 1 }}>
                <Button
                    variant="soft"
                    color="neutral"
                    fullWidth
                    startDecorator={<AddIcon />}
                    onClick={handleNewChat}
                    sx={{
                        justifyContent: 'flex-start',
                        fontWeight: 500,
                    }}
                >
                    New chat
                </Button>
            </Box>

            {/* Chat History */}
            <Box sx={{ flex: 1, overflow: 'auto', px: 1 }}>
                {chats.length > 0 && (
                    <List size="sm">
                        {chats.slice(0, 20).map((chat) => (
                            <ListItem
                                key={chat.id}
                                endAction={
                                    <Dropdown>
                                        <MenuButton
                                            slots={{ root: IconButton }}
                                            slotProps={{
                                                root: {
                                                    variant: 'plain',
                                                    size: 'sm',
                                                    sx: {
                                                        // Always visible on mobile, hover on desktop
                                                        opacity: mobile ? 1 : 0,
                                                        transition: 'opacity 0.2s',
                                                        '.MuiListItem-root:hover &': { opacity: 1 },
                                                        position: 'relative',
                                                        zIndex: 10,
                                                    },
                                                },
                                            }}
                                        >
                                            <MoreHorizIcon sx={{ fontSize: 18 }} />
                                        </MenuButton>
                                        <Menu placement="bottom-end" size="sm" sx={{ zIndex: 9999 }}>
                                            <MenuItem onClick={() => handleRenameChat(chat.id, chat.title)}>
                                                <EditIcon sx={{ mr: 1.5, fontSize: 18 }} />
                                                Rename
                                            </MenuItem>
                                            <MenuItem disabled>
                                                <PushPinIcon sx={{ mr: 1.5, fontSize: 18 }} />
                                                Pin
                                            </MenuItem>
                                            <ListDivider />
                                            <MenuItem color="danger" onClick={() => handleDeleteChat(chat.id)}>
                                                <DeleteIcon sx={{ mr: 1.5, fontSize: 18 }} />
                                                Delete
                                            </MenuItem>
                                        </Menu>
                                    </Dropdown>
                                }
                                sx={{ py: 0.5 }}
                            >
                                <ListItemButton
                                    selected={activeChat?.id === chat.id}
                                    onClick={() => handleChatClick(chat.id)}
                                    sx={{ borderRadius: 'md', pr: 5 }}
                                >
                                    <ListItemContent>
                                        <Typography
                                            level="body-sm"
                                            sx={{
                                                overflow: 'hidden',
                                                textOverflow: 'ellipsis',
                                                whiteSpace: 'nowrap',
                                            }}
                                        >
                                            {chat.title}
                                        </Typography>
                                    </ListItemContent>
                                </ListItemButton>
                            </ListItem>
                        ))}
                    </List>
                )}
            </Box>

            <Divider />

            {/* User Profile - Fixed at bottom */}
            {user && (
                <Box sx={{ p: 1.5, flexShrink: 0 }}>
                    <Dropdown>
                        <MenuButton
                            slots={{ root: Box }}
                            slotProps={{
                                root: {
                                    sx: {
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: 1.5,
                                        p: 1,
                                        borderRadius: 'md',
                                        cursor: 'pointer',
                                        transition: 'background-color 0.2s',
                                        '&:hover': {
                                            bgcolor: 'background.level1',
                                        },
                                    },
                                },
                            }}
                        >
                            <Avatar
                                src={user.avatar}
                                size="sm"
                                sx={{ border: '2px solid', borderColor: 'primary.500' }}
                            >
                                {!user.avatar && getInitials(user.name)}
                            </Avatar>
                            <Box sx={{ flex: 1, minWidth: 0 }}>
                                <Typography level="body-sm" sx={{ fontWeight: 600 }}>
                                    {user.name}
                                </Typography>
                                <Typography
                                    level="body-xs"
                                    sx={{
                                        color: 'text.secondary',
                                        overflow: 'hidden',
                                        textOverflow: 'ellipsis',
                                        whiteSpace: 'nowrap',
                                    }}
                                >
                                    @{user.email.split('@')[0]}
                                </Typography>
                            </Box>
                            <MoreHorizIcon sx={{ color: 'text.tertiary', fontSize: 20 }} />
                        </MenuButton>
                        <Menu placement="top-end" sx={{ minWidth: 200, p: 1, zIndex: 1400 }}>
                            <MenuItem onClick={() => { navigate('/profile'); onClose?.(); }}>
                                <PersonIcon sx={{ mr: 1.5, fontSize: 20 }} />
                                Profile
                            </MenuItem>
                            <MenuItem onClick={() => { navigate('/settings'); onClose?.(); }}>
                                <SettingsIcon sx={{ mr: 1.5, fontSize: 20 }} />
                                Settings
                            </MenuItem>
                            <ListDivider />
                            <MenuItem onClick={() => setMode(mode === 'dark' ? 'light' : 'dark')}>
                                {mode === 'dark' ? (
                                    <LightModeIcon sx={{ mr: 1.5, fontSize: 20 }} />
                                ) : (
                                    <DarkModeIcon sx={{ mr: 1.5, fontSize: 20 }} />
                                )}
                                {mode === 'dark' ? 'Light Mode' : 'Dark Mode'}
                            </MenuItem>
                            <ListDivider />
                            <MenuItem onClick={handleLogout} color="danger">
                                <LogoutIcon sx={{ mr: 1.5, fontSize: 20 }} />
                                Log out
                            </MenuItem>
                        </Menu>
                    </Dropdown>
                </Box>
            )}

            {/* Rename Modal */}
            <Modal open={renameModalOpen} onClose={() => setRenameModalOpen(false)}>
                <ModalDialog size="sm">
                    <DialogTitle>Rename Chat</DialogTitle>
                    <DialogContent>
                        <Input
                            value={newChatName}
                            onChange={(e) => setNewChatName(e.target.value)}
                            placeholder="Enter new name"
                            autoFocus
                            onKeyDown={(e) => e.key === 'Enter' && handleConfirmRename()}
                        />
                    </DialogContent>
                    <DialogActions>
                        <Button variant="plain" color="neutral" onClick={() => setRenameModalOpen(false)}>
                            Cancel
                        </Button>
                        <Button onClick={handleConfirmRename} disabled={!newChatName.trim()}>
                            Rename
                        </Button>
                    </DialogActions>
                </ModalDialog>
            </Modal>

            {/* Delete Confirmation Modal */}
            <Modal open={deleteModalOpen} onClose={() => setDeleteModalOpen(false)}>
                <ModalDialog variant="outlined" role="alertdialog" size="sm">
                    <DialogTitle>
                        <WarningRoundedIcon />
                        Delete Chat
                    </DialogTitle>
                    <Divider />
                    <DialogContent>
                        Are you sure you want to delete this chat? This action cannot be undone.
                    </DialogContent>
                    <DialogActions>
                        <Button variant="solid" color="danger" onClick={handleConfirmDelete}>
                            Delete
                        </Button>
                        <Button variant="plain" color="neutral" onClick={() => setDeleteModalOpen(false)}>
                            Cancel
                        </Button>
                    </DialogActions>
                </ModalDialog>
            </Modal>

            {/* Delete success snackbar */}
            <Snackbar
                open={deleteSnackbarOpen}
                autoHideDuration={3000}
                onClose={() => setDeleteSnackbarOpen(false)}
                variant="soft"
                color="success"
                anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
            >
                Chat deleted successfully.
            </Snackbar>
 
             {/* Delete error snackbar */}
             <Snackbar
                 open={errorSnackbarOpen}
                 autoHideDuration={5000}
                 onClose={() => setErrorSnackbarOpen(false)}
                 variant="soft"
                 color="danger"
                 anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
                 startDecorator={<WarningRoundedIcon />}
             >
                 Failed to delete chat. Please check console for details.
             </Snackbar>
        </Box>
    );
};

export default ChatSidebar;
