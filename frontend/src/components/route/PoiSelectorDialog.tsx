import { useMemo, useState } from 'react';
import {
    Box,
    Button,
    Card,
    CardContent,
    CardCover,
    Chip,
    Input,
    Modal,
    ModalClose,
    ModalDialog,
    Option,
    Select,
    Typography,
} from '@mui/joy';
import SearchIcon from '@mui/icons-material/Search';
import StarIcon from '@mui/icons-material/Star';
import PlaceIcon from '@mui/icons-material/Place';
import GroupsIcon from '@mui/icons-material/Groups';
import type { MapDestination } from '../../data/destinations';
import { categories } from '../../data/destinations';
import { normalizeForSearch } from '../../utils/stringUtils';

type SortMode = 'rating' | 'reviewCount' | 'name';

interface PoiSelectorDialogProps {
    open: boolean;
    title: string;
    description?: string;
    destinations: MapDestination[];
    loading?: boolean;
    excludedIds?: string[];
    onClose: () => void;
    onSelect: (destination: MapDestination) => void;
}

const PoiSelectorDialog = ({
    open,
    title,
    description,
    destinations,
    loading = false,
    excludedIds = [],
    onClose,
    onSelect,
}: PoiSelectorDialogProps) => {
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedCategory, setSelectedCategory] = useState('All');
    const [sortMode, setSortMode] = useState<SortMode>('rating');

    const excludedSet = useMemo(() => new Set(excludedIds), [excludedIds]);

    const filteredDestinations = useMemo(() => {
        const normalizedQuery = normalizeForSearch(searchQuery);
        const base = destinations.filter((destination) => {
            if (excludedSet.has(destination.id)) {
                return false;
            }

            const matchesCategory =
                selectedCategory === 'All' || destination.category === selectedCategory;
            const matchesSearch =
                !normalizedQuery
                || normalizeForSearch(destination.name).includes(normalizedQuery)
                || normalizeForSearch(destination.location).includes(normalizedQuery);

            return matchesCategory && matchesSearch;
        });

        return [...base].sort((left, right) => {
            if (sortMode === 'name') {
                return left.name.localeCompare(right.name, 'tr');
            }
            if (sortMode === 'reviewCount') {
                return (right.reviewCount ?? 0) - (left.reviewCount ?? 0)
                    || right.rating - left.rating;
            }
            return right.rating - left.rating
                || (right.reviewCount ?? 0) - (left.reviewCount ?? 0);
        });
    }, [destinations, excludedSet, searchQuery, selectedCategory, sortMode]);

    return (
        <Modal open={open} onClose={onClose}>
            <ModalDialog
                layout="fullscreen"
                sx={{
                    maxWidth: 1120,
                    width: 'min(1120px, calc(100vw - 32px))',
                    height: 'min(86vh, 920px)',
                    p: 0,
                    overflow: 'hidden',
                    borderRadius: 'xl',
                    bgcolor: 'background.body',
                }}
            >
                <ModalClose />

                <Box sx={{ p: 2.5, borderBottom: '1px solid', borderColor: 'divider' }}>
                    <Typography level="h4" sx={{ fontWeight: 700, mb: 0.5 }}>
                        {title}
                    </Typography>
                    {description && (
                        <Typography level="body-sm" sx={{ color: 'text.secondary', maxWidth: 720 }}>
                            {description}
                        </Typography>
                    )}

                    <Box
                        sx={{
                            display: 'grid',
                            gridTemplateColumns: { xs: '1fr', md: 'minmax(0, 1fr) 180px' },
                            gap: 1.5,
                            mt: 2,
                        }}
                    >
                        <Input
                            placeholder="Search by name or address..."
                            value={searchQuery}
                            onChange={(event) => setSearchQuery(event.target.value)}
                            startDecorator={<SearchIcon />}
                        />
                        <Select
                            value={sortMode}
                            onChange={(_event, value) => setSortMode((value as SortMode) || 'rating')}
                        >
                            <Option value="rating">Sort by rating</Option>
                            <Option value="reviewCount">Sort by popularity</Option>
                            <Option value="name">Sort by name</Option>
                        </Select>
                    </Box>

                    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75, mt: 1.5 }}>
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

                <Box sx={{ flex: 1, overflow: 'auto', p: 2.5 }}>
                    {loading ? (
                        <Box sx={{ py: 8, textAlign: 'center' }}>
                            <Typography level="body-md" sx={{ color: 'text.secondary' }}>
                                Loading places...
                            </Typography>
                        </Box>
                    ) : filteredDestinations.length === 0 ? (
                        <Box sx={{ py: 8, textAlign: 'center' }}>
                            <Typography level="title-sm" sx={{ fontWeight: 700, mb: 0.5 }}>
                                No matching places
                            </Typography>
                            <Typography level="body-sm" sx={{ color: 'text.secondary' }}>
                                Try another search or category.
                            </Typography>
                        </Box>
                    ) : (
                        <>
                            <Typography level="body-sm" sx={{ color: 'text.secondary', mb: 1.5 }}>
                                {filteredDestinations.length} candidate places
                            </Typography>
                            <Box
                                sx={{
                                    display: 'grid',
                                    gridTemplateColumns: 'repeat(auto-fill, minmax(210px, 1fr))',
                                    gap: 2,
                                }}
                            >
                                {filteredDestinations.map((destination) => (
                                    <Card
                                        key={destination.id}
                                        variant="plain"
                                        sx={{
                                            cursor: 'pointer',
                                            minHeight: 230,
                                            borderRadius: 'xl',
                                            overflow: 'hidden',
                                            bgcolor: 'background.surface',
                                            transition: 'all 0.2s ease',
                                            '&:hover': {
                                                transform: 'translateY(-2px)',
                                                boxShadow: 'lg',
                                            },
                                        }}
                                        onClick={() => onSelect(destination)}
                                    >
                                        <CardCover>
                                            <img
                                                src={destination.image}
                                                alt={destination.name}
                                                loading="lazy"
                                                style={{ objectFit: 'cover' }}
                                                onError={(event) => {
                                                    event.currentTarget.style.display = 'none';
                                                }}
                                            />
                                        </CardCover>
                                        <CardCover
                                            sx={{
                                                background:
                                                    'linear-gradient(to top, rgba(3,10,18,0.98) 0%, rgba(6,16,28,0.88) 36%, rgba(7,18,31,0.58) 62%, rgba(7,18,31,0.22) 82%, rgba(7,18,31,0.08) 100%)',
                                            }}
                                        />
                                        <CardContent sx={{ justifyContent: 'flex-end', minHeight: 230, p: 1.5 }}>
                                            <Chip
                                                size="sm"
                                                variant="soft"
                                                color="neutral"
                                                sx={{
                                                    alignSelf: 'flex-start',
                                                    mb: 1,
                                                    bgcolor: 'rgba(7,18,31,0.65)',
                                                    color: '#fff',
                                                }}
                                            >
                                                {destination.category}
                                            </Chip>
                                            <Typography
                                                level="title-md"
                                                sx={{ color: '#fff', fontWeight: 700, mb: 0.35, textShadow: '0 2px 6px rgba(0,0,0,0.85)' }}
                                            >
                                                {destination.name}
                                            </Typography>
                                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0.75 }}>
                                                <PlaceIcon sx={{ fontSize: 13, color: 'rgba(255,255,255,0.92)' }} />
                                                <Typography level="body-xs" sx={{ color: 'rgba(255,255,255,0.92)' }}>
                                                    {destination.location}
                                                </Typography>
                                            </Box>
                                            <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1 }}>
                                                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.35 }}>
                                                    <StarIcon sx={{ fontSize: 14, color: '#FFD700' }} />
                                                    <Typography level="body-xs" sx={{ color: '#fff', fontWeight: 600 }}>
                                                        {destination.rating.toFixed(1)}
                                                    </Typography>
                                                </Box>
                                                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.35 }}>
                                                    <GroupsIcon sx={{ fontSize: 14, color: 'rgba(255,255,255,0.88)' }} />
                                                    <Typography level="body-xs" sx={{ color: 'rgba(255,255,255,0.92)' }}>
                                                        {(destination.reviewCount ?? 0).toLocaleString('tr-TR')}
                                                    </Typography>
                                                </Box>
                                            </Box>
                                        </CardContent>
                                    </Card>
                                ))}
                            </Box>
                        </>
                    )}
                </Box>

                <Box sx={{ p: 2, borderTop: '1px solid', borderColor: 'divider', display: 'flex', justifyContent: 'flex-end' }}>
                    <Button variant="plain" color="neutral" onClick={onClose}>
                        Close
                    </Button>
                </Box>
            </ModalDialog>
        </Modal>
    );
};

export default PoiSelectorDialog;
