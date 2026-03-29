import {
    Box,
    Typography,
    Card,
    CardContent,
    CardCover,
    IconButton,
    Button,
} from '@mui/joy';
import StarIcon from '@mui/icons-material/Star';
import BookmarkIcon from '@mui/icons-material/Bookmark';
import PlaceIcon from '@mui/icons-material/Place';
import MenuIcon from '@mui/icons-material/Menu';
import BookmarkRemoveIcon from '@mui/icons-material/BookmarkRemove';
import { useAppSelector, useAppDispatch } from '../../store/hooks';
import { toggleSaveDestination, syncToggleToBackend, clearSaved } from '../../store/savedSlice';
import { toggleSidebar } from '../../store/chatSlice';
import type { MapDestination } from '../../data/destinations';

interface SavedPanelProps {
    onDestinationSelect?: (destination: MapDestination) => void;
    onDestinationHover?: (destination: MapDestination | null) => void;
    onMenuClick?: () => void;
    showMenuButton?: boolean;
}

const SavedPanel = ({
    onDestinationSelect,
    onDestinationHover,
    onMenuClick,
    showMenuButton = false
}: SavedPanelProps) => {
    const dispatch = useAppDispatch();
    const { destinations: savedDestinations } = useAppSelector((state) => state.saved);

    const handleUnsave = (destination: MapDestination, e: React.MouseEvent) => {
        e.stopPropagation();
        dispatch(toggleSaveDestination(destination));
        dispatch(syncToggleToBackend(destination));
    };

    const handleClearAll = () => {
        if (window.confirm('Are you sure you want to clear all saved places?')) {
            dispatch(clearSaved());
            dispatch(syncToggleToBackend(null as any));
        }
    };

    const handleMenuClick = () => {
        if (onMenuClick) {
            onMenuClick();
        } else {
            dispatch(toggleSidebar());
        }
    };

    return (
        <Box
            sx={{
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
                bgcolor: 'background.body',
                overflow: 'hidden',
            }}
        >
            {/* Header */}
            <Box sx={{ p: 2, borderBottom: '1px solid', borderColor: 'divider' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                        {showMenuButton && (
                            <IconButton variant="plain" size="sm" onClick={handleMenuClick}>
                                <MenuIcon />
                            </IconButton>
                        )}
                        <Typography level="h4" sx={{ fontWeight: 600 }}>
                            Saved Places
                        </Typography>
                    </Box>
                    {savedDestinations.length > 0 && (
                        <Button
                            variant="plain"
                            color="danger"
                            size="sm"
                            onClick={handleClearAll}
                        >
                            Clear All
                        </Button>
                    )}
                </Box>
                <Typography level="body-sm" sx={{ color: 'text.secondary' }}>
                    {savedDestinations.length} {savedDestinations.length === 1 ? 'place' : 'places'} saved
                </Typography>
            </Box>

            {/* Saved Destinations Grid */}
            <Box sx={{ flex: 1, overflow: 'auto', p: 2 }}>
                {savedDestinations.length === 0 ? (
                    <Box sx={{ textAlign: 'center', py: 8 }}>
                        <Box
                            sx={{
                                width: 80,
                                height: 80,
                                borderRadius: '50%',
                                bgcolor: 'background.level2',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                mx: 'auto',
                                mb: 3,
                            }}
                        >
                            <BookmarkIcon sx={{ fontSize: 40, color: 'text.tertiary' }} />
                        </Box>
                        <Typography level="title-lg" sx={{ mb: 1 }}>
                            No saved places yet
                        </Typography>
                        <Typography level="body-md" sx={{ color: 'text.secondary' }}>
                            Tap the bookmark icon on any destination to save it here
                        </Typography>
                    </Box>
                ) : (
                    <Box
                        sx={{
                            display: 'grid',
                            gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
                            gap: 2,
                        }}
                    >
                        {savedDestinations.map((destination) => (
                            <Card
                                key={destination.id}
                                variant="plain"
                                sx={{
                                    cursor: 'pointer',
                                    transition: 'all 0.2s',
                                    bgcolor: 'background.surface',
                                    borderRadius: 'lg',
                                    overflow: 'hidden',
                                    '&:hover': {
                                        transform: 'translateY(-2px)',
                                        boxShadow: 'lg',
                                    },
                                }}
                                onClick={() => onDestinationSelect?.(destination)}
                                onMouseEnter={() => onDestinationHover?.(destination)}
                                onMouseLeave={() => onDestinationHover?.(null)}
                            >
                                <CardCover>
                                    <img
                                        src={destination.image}
                                        alt={destination.name}
                                        loading="lazy"
                                        style={{ objectFit: 'cover' }}
                                        onError={(e) => {
                                            e.currentTarget.style.display = 'none';
                                        }}
                                    />
                                </CardCover>
                                {/* Darker gradient overlay for better text readability */}
                                <CardCover
                                    sx={{
                                        background:
                                            'linear-gradient(to top, rgba(0,0,0,0.95) 0%, rgba(0,0,0,0.6) 40%, rgba(0,0,0,0.2) 70%, transparent 100%)',
                                    }}
                                />
                                <CardContent sx={{ justifyContent: 'flex-end', minHeight: 160, p: 1.5 }}>
                                    <Typography
                                        level="title-md"
                                        sx={{ color: '#fff', fontWeight: 600, mb: 0.25, textShadow: '0 1px 2px rgba(0,0,0,0.5)' }}
                                    >
                                        {destination.name}
                                    </Typography>
                                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0.5 }}>
                                        <PlaceIcon sx={{ fontSize: 12, color: 'rgba(255,255,255,0.85)' }} />
                                        <Typography level="body-xs" sx={{ color: 'rgba(255,255,255,0.85)' }}>
                                            {destination.location}
                                        </Typography>
                                    </Box>
                                    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.25 }}>
                                                <StarIcon sx={{ fontSize: 14, color: '#FFD700' }} />
                                                <Typography level="body-xs" sx={{ color: '#fff', fontWeight: 500 }}>
                                                    {destination.rating}
                                                </Typography>
                                            </Box>
                                            <Typography level="body-xs" sx={{ color: 'rgba(255,255,255,0.85)' }}>
                                                {'$'.repeat(destination.priceLevel)}
                                            </Typography>
                                        </Box>
                                        <IconButton
                                            size="sm"
                                            variant="plain"
                                            sx={{
                                                color: '#ff6b6b',
                                                minWidth: 'auto',
                                                p: 0.5,
                                                '&:hover': { bgcolor: 'rgba(255,255,255,0.2)' }
                                            }}
                                            onClick={(e) => handleUnsave(destination, e)}
                                        >
                                            <BookmarkRemoveIcon sx={{ fontSize: 18 }} />
                                        </IconButton>
                                    </Box>
                                </CardContent>
                            </Card>
                        ))}
                    </Box>
                )}
            </Box>
        </Box>
    );
};

export default SavedPanel;
