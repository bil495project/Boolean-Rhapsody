import { useState, useMemo } from 'react';
import {
    Box,
    Typography,
    Input,
    Chip,
    Card,
    CardContent,
    CardCover,
    IconButton,
} from '@mui/joy';
import SearchIcon from '@mui/icons-material/Search';
import StarIcon from '@mui/icons-material/Star';
import BookmarkBorderIcon from '@mui/icons-material/BookmarkBorder';
import BookmarkIcon from '@mui/icons-material/Bookmark';
import PlaceIcon from '@mui/icons-material/Place';
import MenuIcon from '@mui/icons-material/Menu';
import { ankaraDestinations, categories, type MapDestination } from '../../data/destinations';
import { useAppSelector, useAppDispatch } from '../../store/hooks';
import { toggleSaveDestination } from '../../store/savedSlice';
import { toggleSidebar } from '../../store/chatSlice';

interface ExplorePanelProps {
    onDestinationSelect?: (destination: MapDestination) => void;
    onDestinationHover?: (destination: MapDestination | null) => void;
    onMenuClick?: () => void;
    showMenuButton?: boolean;
}

const ExplorePanel = ({
    onDestinationSelect,
    onDestinationHover,
    onMenuClick,
    showMenuButton = false
}: ExplorePanelProps) => {
    const dispatch = useAppDispatch();
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedCategory, setSelectedCategory] = useState('All');
    const { destinations: savedDestinations } = useAppSelector((state) => state.saved);

    const filteredDestinations = useMemo(() => {
        return ankaraDestinations.filter((dest) => {
            const matchesSearch = dest.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                dest.location.toLowerCase().includes(searchQuery.toLowerCase());
            const matchesCategory = selectedCategory === 'All' || dest.category === selectedCategory;
            return matchesSearch && matchesCategory;
        });
    }, [searchQuery, selectedCategory]);

    const isDestinationSaved = (destinationId: string) => {
        return savedDestinations.some(d => d.id === destinationId);
    };

    const handleSaveClick = (destination: MapDestination, e: React.MouseEvent) => {
        e.stopPropagation();
        dispatch(toggleSaveDestination(destination));
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
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
                    {showMenuButton && (
                        <IconButton variant="plain" size="sm" onClick={handleMenuClick}>
                            <MenuIcon />
                        </IconButton>
                    )}
                    <Typography level="h4" sx={{ fontWeight: 600 }}>
                        Explore
                    </Typography>
                </Box>

                {/* Search */}
                <Input
                    placeholder="Search destinations..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    startDecorator={<SearchIcon />}
                    sx={{ mb: 2 }}
                />

                {/* Categories */}
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75 }}>
                    {categories.map((category) => (
                        <Chip
                            key={category}
                            variant={selectedCategory === category ? 'solid' : 'soft'}
                            color={selectedCategory === category ? 'primary' : 'neutral'}
                            onClick={() => setSelectedCategory(category)}
                            size="sm"
                            sx={{ cursor: 'pointer' }}
                        >
                            {category}
                        </Chip>
                    ))}
                </Box>
            </Box>

            {/* Destinations Grid */}
            <Box sx={{ flex: 1, overflow: 'auto', p: 2 }}>
                <Box
                    sx={{
                        display: 'grid',
                        gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
                        gap: 2,
                    }}
                >
                    {filteredDestinations.map((destination) => {
                        const isSaved = isDestinationSaved(destination.id);
                        return (
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
                                                color: isSaved ? '#4dabf5' : '#fff',
                                                minWidth: 'auto',
                                                p: 0.5,
                                                '&:hover': { bgcolor: 'rgba(255,255,255,0.2)' }
                                            }}
                                            onClick={(e) => handleSaveClick(destination, e)}
                                        >
                                            {isSaved ? (
                                                <BookmarkIcon sx={{ fontSize: 18 }} />
                                            ) : (
                                                <BookmarkBorderIcon sx={{ fontSize: 18 }} />
                                            )}
                                        </IconButton>
                                    </Box>
                                </CardContent>
                            </Card>
                        );
                    })}
                </Box>

                {filteredDestinations.length === 0 && (
                    <Box sx={{ textAlign: 'center', py: 4 }}>
                        <Typography level="body-lg" sx={{ color: 'text.secondary' }}>
                            No destinations found
                        </Typography>
                    </Box>
                )}
            </Box>
        </Box>
    );
};

export default ExplorePanel;
