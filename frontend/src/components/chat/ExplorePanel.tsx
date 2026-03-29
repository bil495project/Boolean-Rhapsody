import { useState, useMemo, useEffect } from 'react';
import {
    Box,
    Typography,
    Input,
    Chip,
    Card,
    CardContent,
    CardCover,
    IconButton,
    Button,
    Select,
    Option,
    FormControl,
    FormLabel,
    Divider,
} from '@mui/joy';
import SearchIcon from '@mui/icons-material/Search';
import StarIcon from '@mui/icons-material/Star';
import BookmarkBorderIcon from '@mui/icons-material/BookmarkBorder';
import BookmarkIcon from '@mui/icons-material/Bookmark';
import PlaceIcon from '@mui/icons-material/Place';
import MenuIcon from '@mui/icons-material/Menu';
import FilterListIcon from '@mui/icons-material/FilterList';
import { categories, type MapDestination } from '../../data/destinations';
import { useAppSelector, useAppDispatch } from '../../store/hooks';
import { toggleSaveDestination, syncToggleToBackend } from '../../store/savedSlice';
import { toggleSidebar } from '../../store/chatSlice';
import { normalizeForSearch } from '../../utils/stringUtils';

interface ExplorePanelProps {
    onDestinationSelect?: (destination: MapDestination) => void;
    onDestinationHover?: (destination: MapDestination | null) => void;
    onMenuClick?: () => void;
    showMenuButton?: boolean;
    destinations?: MapDestination[];
}

const ExplorePanel = ({
    onDestinationSelect,
    onDestinationHover,
    onMenuClick,
    showMenuButton = false,
    destinations = []
}: ExplorePanelProps) => {
    const dispatch = useAppDispatch();
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedCategory, setSelectedCategory] = useState('All');
    const [sortMode, setSortMode] = useState<'rating' | 'reviewCount' | 'priceLowHigh' | 'priceHighLow' | 'name'>('rating');
    const [minRating, setMinRating] = useState<number>(0);
    const [minReviewCount, setMinReviewCount] = useState<number>(0);
    const [selectedPriceLevel, setSelectedPriceLevel] = useState<string>('All');
    const [businessStatus, setBusinessStatus] = useState<string>('All');
    const [typeQuery, setTypeQuery] = useState('');
    const [showAdvancedFilters, setShowAdvancedFilters] = useState(false);
    const { destinations: savedDestinations } = useAppSelector((state) => state.saved);

    const [page, setPage] = useState(1);
    const itemsPerPage = 20;

    const filteredDestinations = useMemo(() => {
        const normalizedQuery = normalizeForSearch(searchQuery);
        const normalizedTypeQuery = normalizeForSearch(typeQuery);

        const filtered = destinations.filter((dest) => {
            const nameMatch = normalizeForSearch(dest.name || '').includes(normalizedQuery);
            const locMatch = normalizeForSearch(dest.location || '').includes(normalizedQuery);
            const matchesSearch = nameMatch || locMatch;
            const matchesCategory = selectedCategory === 'All' || dest.category === selectedCategory;
            const matchesRating = dest.rating >= minRating;
            const matchesReviewCount = (dest.reviewCount ?? 0) >= minReviewCount;
            const matchesPrice = selectedPriceLevel === 'All' || String(dest.priceLevel) === selectedPriceLevel;
            const normalizedStatus = (dest.businessStatus || 'UNKNOWN').toUpperCase();
            const matchesBusinessStatus = businessStatus === 'All' || normalizedStatus === businessStatus;
            const matchesType = !normalizedTypeQuery || (dest.types ?? []).some((type) =>
                normalizeForSearch(type).includes(normalizedTypeQuery),
            );

            return matchesSearch
                && matchesCategory
                && matchesRating
                && matchesReviewCount
                && matchesPrice
                && matchesBusinessStatus
                && matchesType;
        });

        return [...filtered].sort((left, right) => {
            switch (sortMode) {
                case 'reviewCount':
                    return (right.reviewCount ?? 0) - (left.reviewCount ?? 0) || right.rating - left.rating;
                case 'priceLowHigh':
                    return left.priceLevel - right.priceLevel || right.rating - left.rating;
                case 'priceHighLow':
                    return right.priceLevel - left.priceLevel || right.rating - left.rating;
                case 'name':
                    return left.name.localeCompare(right.name, 'tr');
                case 'rating':
                default:
                    return right.rating - left.rating || (right.reviewCount ?? 0) - (left.reviewCount ?? 0);
            }
        });
    }, [
        businessStatus,
        destinations,
        minRating,
        minReviewCount,
        searchQuery,
        selectedCategory,
        selectedPriceLevel,
        sortMode,
        typeQuery,
    ]);

    // Reset to page 1 when filters change
    useEffect(() => {
        setPage(1);
    }, [businessStatus, minRating, minReviewCount, searchQuery, selectedCategory, selectedPriceLevel, sortMode, typeQuery]);

    const totalPages = Math.ceil(filteredDestinations.length / itemsPerPage);
    
    const paginatedDestinations = useMemo(() => {
        const start = (page - 1) * itemsPerPage;
        return filteredDestinations.slice(start, start + itemsPerPage);
    }, [filteredDestinations, page]);

    const isDestinationSaved = (destinationId: string) => {
        return savedDestinations.some(d => d.id === destinationId);
    };

    const handleSaveClick = (destination: MapDestination, e: React.MouseEvent) => {
        e.stopPropagation();
        dispatch(toggleSaveDestination(destination));
        dispatch(syncToggleToBackend(destination));
    };

    const handleMenuClick = () => {
        if (onMenuClick) {
            onMenuClick();
        } else {
            dispatch(toggleSidebar());
        }
    };

    const resetFilters = () => {
        setSearchQuery('');
        setSelectedCategory('All');
        setSortMode('rating');
        setMinRating(0);
        setMinReviewCount(0);
        setSelectedPriceLevel('All');
        setBusinessStatus('All');
        setTypeQuery('');
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

                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mt: 2, gap: 1 }}>
                    <Button
                        size="sm"
                        variant="soft"
                        color="neutral"
                        startDecorator={<FilterListIcon />}
                        onClick={() => setShowAdvancedFilters((prev) => !prev)}
                    >
                        {showAdvancedFilters ? 'Hide filters' : 'More filters'}
                    </Button>
                    <Button size="sm" variant="plain" color="neutral" onClick={resetFilters}>
                        Clear all
                    </Button>
                </Box>

                {showAdvancedFilters && (
                    <>
                        <Divider sx={{ my: 2 }} />
                        <Box
                            sx={{
                                display: 'grid',
                                gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))' },
                                gap: 1.5,
                            }}
                        >
                            <FormControl size="sm">
                                <FormLabel>Sort</FormLabel>
                                <Select
                                    value={sortMode}
                                    onChange={(_event, value) => setSortMode((value as typeof sortMode) || 'rating')}
                                >
                                    <Option value="rating">Highest rating</Option>
                                    <Option value="reviewCount">Most popular</Option>
                                    <Option value="priceLowHigh">Price low to high</Option>
                                    <Option value="priceHighLow">Price high to low</Option>
                                    <Option value="name">Name A-Z</Option>
                                </Select>
                            </FormControl>

                            <FormControl size="sm">
                                <FormLabel>Minimum Rating</FormLabel>
                                <Select
                                    value={String(minRating)}
                                    onChange={(_event, value) => setMinRating(Number(value ?? 0))}
                                >
                                    <Option value="0">Any</Option>
                                    <Option value="3.5">3.5+</Option>
                                    <Option value="4">4.0+</Option>
                                    <Option value="4.5">4.5+</Option>
                                </Select>
                            </FormControl>

                            <FormControl size="sm">
                                <FormLabel>Minimum Review Count</FormLabel>
                                <Select
                                    value={String(minReviewCount)}
                                    onChange={(_event, value) => setMinReviewCount(Number(value ?? 0))}
                                >
                                    <Option value="0">Any</Option>
                                    <Option value="100">100+</Option>
                                    <Option value="500">500+</Option>
                                    <Option value="1000">1,000+</Option>
                                    <Option value="5000">5,000+</Option>
                                </Select>
                            </FormControl>

                            <FormControl size="sm">
                                <FormLabel>Price Level</FormLabel>
                                <Select
                                    value={selectedPriceLevel}
                                    onChange={(_event, value) => setSelectedPriceLevel(value || 'All')}
                                >
                                    <Option value="All">Any</Option>
                                    <Option value="1">$</Option>
                                    <Option value="2">$$</Option>
                                    <Option value="3">$$$</Option>
                                    <Option value="4">$$$$</Option>
                                </Select>
                            </FormControl>

                            <FormControl size="sm">
                                <FormLabel>Business Status</FormLabel>
                                <Select
                                    value={businessStatus}
                                    onChange={(_event, value) => setBusinessStatus(value || 'All')}
                                >
                                    <Option value="All">Any</Option>
                                    <Option value="OPERATIONAL">Operational</Option>
                                    <Option value="CLOSED_TEMPORARILY">Closed Temporarily</Option>
                                    <Option value="CLOSED_PERMANENTLY">Closed Permanently</Option>
                                    <Option value="UNKNOWN">Unknown</Option>
                                </Select>
                            </FormControl>

                            <FormControl size="sm">
                                <FormLabel>Type Tag</FormLabel>
                                <Input
                                    placeholder="restaurant, museum, park..."
                                    value={typeQuery}
                                    onChange={(event) => setTypeQuery(event.target.value)}
                                />
                            </FormControl>
                        </Box>
                    </>
                )}
            </Box>

            {/* Destinations Grid */}
            <Box sx={{ flex: 1, overflow: 'auto', p: 2 }}>
                <Typography level="body-sm" sx={{ color: 'text.secondary', mb: 1.5 }}>
                    {filteredDestinations.length} places match the current filters
                </Typography>
                <Box
                    sx={{
                        display: 'grid',
                        gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
                        gap: 2,
                    }}
                >
                    {paginatedDestinations.map((destination) => {
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
                                        onError={(e) => {
                                            e.currentTarget.style.display = 'none';
                                        }}
                                    />
                                </CardCover>
                                {/* Darker gradient overlay for better text readability */}
                                <CardCover
                                    sx={{
                                        background:
                                            'linear-gradient(to top, rgba(3,10,18,0.98) 0%, rgba(6,16,28,0.88) 36%, rgba(7,18,31,0.58) 62%, rgba(7,18,31,0.22) 82%, rgba(7,18,31,0.08) 100%)',
                                    }}
                                />
                                <CardContent
                                    sx={{
                                        justifyContent: 'flex-end',
                                        minHeight: 160,
                                        p: 1.5,
                                        background: 'linear-gradient(to top, rgba(5,12,22,0.32) 0%, rgba(5,12,22,0.12) 55%, transparent 100%)',
                                    }}
                                >
                                    <Typography
                                        level="title-md"
                                        sx={{ color: '#fff', fontWeight: 700, mb: 0.25, textShadow: '0 2px 6px rgba(0,0,0,0.85)' }}
                                    >
                                        {destination.name}
                                    </Typography>
                                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0.5 }}>
                                        <PlaceIcon sx={{ fontSize: 12, color: 'rgba(255,255,255,0.92)' }} />
                                        <Typography level="body-xs" sx={{ color: 'rgba(255,255,255,0.92)', textShadow: '0 1px 4px rgba(0,0,0,0.75)' }}>
                                            {destination.location}
                                        </Typography>
                                    </Box>
                                    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.25 }}>
                                                <StarIcon sx={{ fontSize: 14, color: '#FFD700' }} />
                                                <Typography level="body-xs" sx={{ color: '#fff', fontWeight: 600, textShadow: '0 1px 4px rgba(0,0,0,0.75)' }}>
                                                    {destination.rating}
                                                </Typography>
                                            </Box>
                                            <Typography level="body-xs" sx={{ color: 'rgba(255,255,255,0.92)', textShadow: '0 1px 4px rgba(0,0,0,0.75)' }}>
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

                {/* Pagination Controls */}
                {totalPages > 1 && (
                    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 2, mt: 3, pb: 2 }}>
                        <Button
                            variant="outlined"
                            color="neutral"
                            size="sm"
                            disabled={page === 1}
                            onClick={() => setPage(p => p - 1)}
                        >
                            Previous
                        </Button>
                        <Typography level="body-sm" sx={{ fontWeight: 500 }}>
                            Page {page} of {totalPages}
                        </Typography>
                        <Button
                            variant="outlined"
                            color="neutral"
                            size="sm"
                            disabled={page === totalPages}
                            onClick={() => setPage(p => p + 1)}
                        >
                            Next
                        </Button>
                    </Box>
                )}
            </Box>
        </Box>
    );
};

export default ExplorePanel;
