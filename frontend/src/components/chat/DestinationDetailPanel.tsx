import { useState, useEffect, useRef, useCallback } from 'react';
import { Box, Card, Typography, IconButton, Button, AspectRatio, Divider } from '@mui/joy';
import CloseIcon from '@mui/icons-material/Close';
import BookmarkIcon from '@mui/icons-material/Bookmark';
import BookmarkBorderIcon from '@mui/icons-material/BookmarkBorder';
import StarIcon from '@mui/icons-material/Star';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import PhoneIcon from '@mui/icons-material/Phone';
import LanguageIcon from '@mui/icons-material/Language';
import type { MapDestination } from '../../data/destinations';
import { useAppSelector, useAppDispatch } from '../../store/hooks';
import { toggleSaveDestination, syncToggleToBackend } from '../../store/savedSlice';

interface DestinationDetailPanelProps {
    destination: MapDestination;
    onClose: () => void;
    onAskAbout?: (destination: MapDestination) => void;
}

const MIN_WIDTH = 300;
const MAX_WIDTH = 800;

const DestinationDetailPanel = ({ destination, onClose }: DestinationDetailPanelProps) => {
    const dispatch = useAppDispatch();
    const { destinations: savedDestinations } = useAppSelector((state) => state.saved);
    const isSaved = savedDestinations.some(d => d.id === destination.id);

    // Resize state
    const [width, setWidth] = useState(380);
    const isResizingRef = useRef(false);

    const handleToggleSave = () => {
        dispatch(toggleSaveDestination(destination));
        dispatch(syncToggleToBackend(destination));
    };

    // Resize handlers
    const startResize = useCallback((e: React.MouseEvent) => {
        e.preventDefault();
        isResizingRef.current = true;
        document.body.style.cursor = 'col-resize';
        document.body.style.userSelect = 'none'; // Prevent text selection while dragging
    }, []);

    const stopResize = useCallback(() => {
        isResizingRef.current = false;
        document.body.style.cursor = '';
        document.body.style.userSelect = '';
    }, []);

    const resize = useCallback((e: MouseEvent) => {
        if (isResizingRef.current) {
            // Calculate new width based on mouse position from right edge of screen
            const newWidth = window.innerWidth - e.clientX;

            // Clamp width between min and max
            if (newWidth >= MIN_WIDTH && newWidth <= MAX_WIDTH) {
                setWidth(newWidth);
            }
        }
    }, []);

    useEffect(() => {
        // Add global event listeners for drag
        window.addEventListener('mousemove', resize);
        window.addEventListener('mouseup', stopResize);

        return () => {
            window.removeEventListener('mousemove', resize);
            window.removeEventListener('mouseup', stopResize);
        };
    }, [resize, stopResize]);

    return (
        <Card
            sx={{
                position: 'absolute',
                top: 0,
                right: 0,
                width: width,
                height: '100%',
                borderRadius: 0,
                borderLeft: '1px solid',
                borderColor: 'divider',
                display: 'flex',
                flexDirection: 'column',
                overflow: 'visible', // Allow resize handle to be visible if needed
                zIndex: 1001,
                bgcolor: 'background.surface',
                transition: isResizingRef.current ? 'none' : 'width 0.1s ease-out', // Smooth transition only when not dragging
                boxShadow: '-4px 0 12px rgba(0,0,0,0.1)',
            }}
        >
            {/* Resize Handle Area */}
            <Box
                onMouseDown={startResize}
                sx={{
                    position: 'absolute',
                    left: -8, // Extend slightly outside to make it easier to grab
                    top: 0,
                    bottom: 0,
                    width: 16, // Wider hit area
                    cursor: 'col-resize',
                    zIndex: 1002,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    '&:hover .resize-indicator': {
                        opacity: 1,
                    },
                    '&:active .resize-indicator': {
                        opacity: 1,
                        bgcolor: 'primary.solidBg',
                    }
                }}
            >
                {/* Visual indicator line */}
                <Box
                    className="resize-indicator"
                    sx={{
                        width: 4,
                        height: 48,
                        bgcolor: 'neutral.outlinedBorder',
                        borderRadius: 'xs',
                        opacity: 0, // Hidden by default, shown on hover
                        transition: 'opacity 0.2s',
                    }}
                />
            </Box>

            {/* Header Image with Close Button */}
            <Box sx={{ position: 'relative' }}>
                <AspectRatio ratio="16/9">
                    <img
                        src={destination.image}
                        alt={destination.name}
                        loading="lazy"
                        style={{ objectFit: 'cover' }}
                        onError={(e) => {
                            e.currentTarget.style.display = 'none';
                        }}
                    />
                </AspectRatio>

                {/* Close Button */}
                <IconButton
                    variant="solid"
                    size="sm"
                    onClick={onClose}
                    sx={{
                        position: 'absolute',
                        top: 12,
                        right: 12,
                        bgcolor: 'background.surface',
                        color: 'text.primary',
                        '&:hover': { bgcolor: 'background.level1' },
                    }}
                >
                    <CloseIcon />
                </IconButton>

                {/* Save Button */}
                <IconButton
                    variant="solid"
                    size="sm"
                    onClick={handleToggleSave}
                    sx={{
                        position: 'absolute',
                        top: 12,
                        right: 52,
                        bgcolor: 'background.surface',
                        color: isSaved ? 'primary.500' : 'text.primary',
                        '&:hover': { bgcolor: 'background.level1' },
                    }}
                >
                    {isSaved ? <BookmarkIcon /> : <BookmarkBorderIcon />}
                </IconButton>
            </Box>

            {/* Content */}
            <Box sx={{ flex: 1, overflow: 'auto', p: 2.5 }}>
                {/* Title */}
                <Typography level="h3" sx={{ fontWeight: 700, mb: 0.5 }}>
                    {destination.name}
                </Typography>

                {/* Category */}
                <Typography level="body-sm" sx={{ color: 'text.secondary', mb: 2 }}>
                    {destination.category}
                </Typography>

                {/* Rating & Price Row */}
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 3 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                        <StarIcon sx={{ fontSize: 20, color: '#FFB800' }} />
                        <Typography level="body-md" fontWeight={600}>
                            {destination.rating}
                        </Typography>
                        {destination.reviewCount && (
                            <Typography level="body-sm" sx={{ color: 'primary.500' }}>
                                ({destination.reviewCount.toLocaleString()})
                            </Typography>
                        )}
                    </Box>
                    <Typography level="body-md" sx={{ color: 'text.secondary' }}>
                        {'$'.repeat(destination.priceLevel)} {'$'.repeat(4 - destination.priceLevel).split('').map(() => '').join('')}
                        <Typography component="span" sx={{ color: 'text.tertiary' }}>
                            {'$'.repeat(4 - destination.priceLevel)}
                        </Typography>
                    </Typography>
                </Box>

                {/* About Section */}
                {destination.description && (
                    <>
                        <Typography level="title-md" sx={{ fontWeight: 600, mb: 1 }}>
                            Hakkında
                        </Typography>
                        <Typography level="body-sm" sx={{ color: 'text.secondary', mb: 3, lineHeight: 1.7 }}>
                            {destination.description}
                        </Typography>
                    </>
                )}

                <Divider sx={{ my: 2 }} />

                {/* Opening Hours */}
                {destination.openingHours && (
                    <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 2, mb: 2 }}>
                        <Box sx={{
                            p: 1,
                            bgcolor: 'background.level1',
                            borderRadius: 'md',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center'
                        }}>
                            <AccessTimeIcon sx={{ fontSize: 20, color: 'text.secondary' }} />
                        </Box>
                        <Box>
                            <Typography level="body-xs" sx={{ color: 'text.tertiary', mb: 0.25 }}>
                                Çalışma Saatleri
                            </Typography>
                            <Typography level="body-md" fontWeight={500}>
                                {destination.openingHours}
                            </Typography>
                        </Box>
                    </Box>
                )}

                {/* Phone */}
                {destination.phone && (
                    <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 2, mb: 2 }}>
                        <Box sx={{
                            p: 1,
                            bgcolor: 'background.level1',
                            borderRadius: 'md',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center'
                        }}>
                            <PhoneIcon sx={{ fontSize: 20, color: 'text.secondary' }} />
                        </Box>
                        <Box>
                            <Typography level="body-xs" sx={{ color: 'text.tertiary', mb: 0.25 }}>
                                Telefon
                            </Typography>
                            <Typography level="body-md" fontWeight={500}>
                                {destination.phone}
                            </Typography>
                        </Box>
                    </Box>
                )}

                {/* Website */}
                {destination.website && (
                    <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 2, mb: 2 }}>
                        <Box sx={{
                            p: 1,
                            bgcolor: 'background.level1',
                            borderRadius: 'md',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center'
                        }}>
                            <LanguageIcon sx={{ fontSize: 20, color: 'text.secondary' }} />
                        </Box>
                        <Box>
                            <Typography level="body-xs" sx={{ color: 'text.tertiary', mb: 0.25 }}>
                                Website
                            </Typography>
                            <Typography
                                level="body-md"
                                fontWeight={500}
                                sx={{
                                    color: 'primary.500',
                                    cursor: 'pointer',
                                    '&:hover': { textDecoration: 'underline' }
                                }}
                                onClick={() => window.open(`https://${destination.website}`, '_blank')}
                            >
                                {destination.website}
                            </Typography>
                        </Box>
                    </Box>
                )}
            </Box>

            {/* Bottom Actions */}
            <Box sx={{ p: 2, borderTop: '1px solid', borderColor: 'divider' }}>
                <Button
                    variant={isSaved ? 'solid' : 'outlined'}
                    color={isSaved ? 'primary' : 'neutral'}
                    startDecorator={isSaved ? <BookmarkIcon /> : <BookmarkBorderIcon />}
                    onClick={handleToggleSave}
                    fullWidth
                    size="lg"
                >
                    {isSaved ? 'Saved' : 'Save to Map'}
                </Button>
            </Box>
        </Card>
    );
};

export default DestinationDetailPanel;
