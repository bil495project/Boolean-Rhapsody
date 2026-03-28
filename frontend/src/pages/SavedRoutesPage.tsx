import { useEffect, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import {
    Alert,
    Box,
    Button,
    Card,
    DialogActions,
    DialogContent,
    DialogTitle,
    Divider,
    Drawer,
    IconButton,
    Input,
    Modal,
    ModalDialog,
    Snackbar,
    Typography,
} from '@mui/joy';
import { useMediaQuery } from '@mui/system';
import MenuIcon from '@mui/icons-material/Menu';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import StraightenIcon from '@mui/icons-material/Straighten';
import RouteIcon from '@mui/icons-material/Route';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import ChatSidebar from '../components/chat/ChatSidebar';
import { useAppDispatch, useAppSelector } from '../store/hooks';
import { fetchSavedRoutes, renameSavedRouteThunk, deleteSavedRouteThunk } from '../store/savedRoutesSlice';
import { toggleSidebar } from '../store/chatSlice';

function formatDuration(seconds: number): string {
    if (seconds < 60) return `${seconds}s`;
    const mins = Math.round(seconds / 60);
    if (mins < 60) return `${mins} min`;
    const hrs = Math.floor(mins / 60);
    const remainMins = mins % 60;
    return remainMins > 0 ? `${hrs}h ${remainMins}m` : `${hrs}h`;
}

function formatDistance(meters: number): string {
    if (meters < 1000) return `${Math.round(meters)} m`;
    return `${(meters / 1000).toFixed(1)} km`;
}

function formatTimestamp(epochMs: number): string {
    return new Date(epochMs).toLocaleString();
}

const SavedRoutesPage = () => {
    const dispatch = useAppDispatch();
    const navigate = useNavigate();
    const isMobile = useMediaQuery('(max-width: 768px)');

    const { isAuthenticated } = useAppSelector((state) => state.auth);
    const { sidebarOpen } = useAppSelector((state) => state.chat);
    const { summaries, isLoading, error } = useAppSelector((state) => state.savedRoutes);

    const [mobileDrawerOpen, setMobileDrawerOpen] = useState(false);
    const [renameModalOpen, setRenameModalOpen] = useState(false);
    const [deleteModalOpen, setDeleteModalOpen] = useState(false);
    const [targetSavedRouteId, setTargetSavedRouteId] = useState<string | null>(null);
    const [targetTitle, setTargetTitle] = useState('');
    const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; color: 'success' | 'danger' }>({
        open: false,
        message: '',
        color: 'success',
    });

    useEffect(() => {
        if (isAuthenticated) {
            dispatch(fetchSavedRoutes());
        }
    }, [dispatch, isAuthenticated]);

    if (!isAuthenticated) {
        return <Navigate to="/login" replace />;
    }

    const openRenameModal = (savedRouteId: string, currentTitle: string) => {
        setTargetSavedRouteId(savedRouteId);
        setTargetTitle(currentTitle);
        setRenameModalOpen(true);
    };

    const openDeleteModal = (savedRouteId: string, currentTitle: string) => {
        setTargetSavedRouteId(savedRouteId);
        setTargetTitle(currentTitle);
        setDeleteModalOpen(true);
    };

    const handleRename = async () => {
        if (!targetSavedRouteId || !targetTitle.trim()) {
            return;
        }
        try {
            await dispatch(renameSavedRouteThunk({
                savedRouteId: targetSavedRouteId,
                title: targetTitle.trim(),
            })).unwrap();
            setSnackbar({ open: true, message: 'Saved route renamed.', color: 'success' });
        } catch {
            setSnackbar({ open: true, message: 'Saved route could not be renamed.', color: 'danger' });
        } finally {
            setRenameModalOpen(false);
        }
    };

    const handleDelete = async () => {
        if (!targetSavedRouteId) {
            return;
        }
        try {
            await dispatch(deleteSavedRouteThunk(targetSavedRouteId)).unwrap();
            setSnackbar({ open: true, message: 'Saved route deleted.', color: 'success' });
        } catch {
            setSnackbar({ open: true, message: 'Saved route could not be deleted.', color: 'danger' });
        } finally {
            setDeleteModalOpen(false);
            setTargetSavedRouteId(null);
            setTargetTitle('');
        }
    };

    const content = (
        <Box sx={{ flex: 1, overflow: 'auto', p: { xs: 2, md: 3 } }}>
            <Box
                sx={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 2,
                    mb: 3,
                    flexWrap: 'wrap',
                }}
            >
                {(isMobile || !sidebarOpen) && (
                    <IconButton
                        variant="plain"
                        size="sm"
                        onClick={isMobile ? () => setMobileDrawerOpen(true) : () => dispatch(toggleSidebar())}
                    >
                        <MenuIcon />
                    </IconButton>
                )}
                <RouteIcon sx={{ color: 'primary.500', fontSize: 28 }} />
                <Box sx={{ flex: 1 }}>
                    <Typography level="h4" sx={{ fontWeight: 700 }}>
                        Saved Routes
                    </Typography>
                    <Typography level="body-sm" sx={{ color: 'text.secondary' }}>
                        Approved routes are persisted here as exact snapshots.
                    </Typography>
                </Box>
            </Box>

            {error && (
                <Alert color="danger" sx={{ mb: 2 }}>
                    {error}
                </Alert>
            )}

            {!isLoading && summaries.length === 0 && (
                <Card variant="soft" color="neutral" sx={{ p: 3 }}>
                    <Typography level="title-md" sx={{ fontWeight: 700, mb: 0.5 }}>
                        No saved routes yet
                    </Typography>
                    <Typography level="body-sm" sx={{ color: 'text.secondary' }}>
                        Generate a route and use Approve to store it here.
                    </Typography>
                </Card>
            )}

            <Box sx={{ display: 'grid', gap: 2 }}>
                {summaries.map((savedRoute) => (
                    <Card key={savedRoute.id} variant="outlined" sx={{ p: 2.5 }}>
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap' }}>
                            <Box sx={{ minWidth: 0, flex: 1 }}>
                                <Typography level="title-lg" sx={{ fontWeight: 700 }}>
                                    {savedRoute.title}
                                </Typography>
                                <Typography level="body-xs" sx={{ color: 'text.secondary', mt: 0.5 }}>
                                    Created {formatTimestamp(savedRoute.createdAt)}
                                </Typography>
                                <Typography level="body-xs" sx={{ color: 'text.secondary' }}>
                                    Updated {formatTimestamp(savedRoute.updatedAt)}
                                </Typography>
                            </Box>
                            <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                                <Button
                                    size="sm"
                                    startDecorator={<OpenInNewIcon />}
                                    onClick={() => navigate(`/route/saved/${savedRoute.id}`)}
                                >
                                    Open
                                </Button>
                                <Button
                                    size="sm"
                                    variant="outlined"
                                    color="neutral"
                                    startDecorator={<EditIcon />}
                                    onClick={() => openRenameModal(savedRoute.id, savedRoute.title)}
                                >
                                    Rename
                                </Button>
                                <Button
                                    size="sm"
                                    variant="outlined"
                                    color="danger"
                                    startDecorator={<DeleteIcon />}
                                    onClick={() => openDeleteModal(savedRoute.id, savedRoute.title)}
                                >
                                    Delete
                                </Button>
                            </Box>
                        </Box>

                        <Divider sx={{ my: 2 }} />

                        <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
                            <Typography level="body-sm">
                                {savedRoute.stopCount} stops
                            </Typography>
                            <Typography level="body-sm" sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                                <AccessTimeIcon sx={{ fontSize: 16 }} />
                                {formatDuration(savedRoute.totalDurationSec)}
                            </Typography>
                            <Typography level="body-sm" sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                                <StraightenIcon sx={{ fontSize: 16 }} />
                                {formatDistance(savedRoute.totalDistanceM)}
                            </Typography>
                            <Typography level="body-sm" sx={{ textTransform: 'capitalize' }}>
                                {savedRoute.travelMode || 'driving'}
                            </Typography>
                        </Box>
                    </Card>
                ))}
            </Box>
        </Box>
    );

    return (
        <Box sx={{ height: '100vh', display: 'flex', overflow: 'hidden' }}>
            {!isMobile && sidebarOpen && <ChatSidebar />}

            {isMobile && (
                <Drawer
                    open={mobileDrawerOpen}
                    onClose={() => setMobileDrawerOpen(false)}
                    size="sm"
                    sx={{ '--Drawer-horizontalSize': '280px' }}
                >
                    <ChatSidebar mobile onClose={() => setMobileDrawerOpen(false)} />
                </Drawer>
            )}

            <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                {content}
            </Box>

            <Modal open={renameModalOpen} onClose={() => setRenameModalOpen(false)}>
                <ModalDialog size="sm">
                    <DialogTitle>Rename Saved Route</DialogTitle>
                    <DialogContent>
                        <Input
                            value={targetTitle}
                            onChange={(event) => setTargetTitle(event.target.value)}
                            placeholder="Enter a title"
                            autoFocus
                            onKeyDown={(event) => {
                                if (event.key === 'Enter') {
                                    handleRename();
                                }
                            }}
                        />
                    </DialogContent>
                    <DialogActions>
                        <Button variant="plain" color="neutral" onClick={() => setRenameModalOpen(false)}>
                            Cancel
                        </Button>
                        <Button onClick={handleRename} disabled={!targetTitle.trim()}>
                            Rename
                        </Button>
                    </DialogActions>
                </ModalDialog>
            </Modal>

            <Modal open={deleteModalOpen} onClose={() => setDeleteModalOpen(false)}>
                <ModalDialog size="sm">
                    <DialogTitle>Delete Saved Route</DialogTitle>
                    <DialogContent>
                        <Typography level="body-sm">
                            Delete <strong>{targetTitle}</strong>? This cannot be undone.
                        </Typography>
                    </DialogContent>
                    <DialogActions>
                        <Button variant="plain" color="neutral" onClick={() => setDeleteModalOpen(false)}>
                            Cancel
                        </Button>
                        <Button color="danger" onClick={handleDelete}>
                            Delete
                        </Button>
                    </DialogActions>
                </ModalDialog>
            </Modal>

            <Snackbar
                open={snackbar.open}
                autoHideDuration={3500}
                onClose={() => setSnackbar((prev) => ({ ...prev, open: false }))}
                variant="soft"
                color={snackbar.color}
                anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
            >
                {snackbar.message}
            </Snackbar>
        </Box>
    );
};

export default SavedRoutesPage;
