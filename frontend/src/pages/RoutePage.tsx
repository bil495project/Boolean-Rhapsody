import { useState, useCallback, useRef, useEffect } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import {
    Box,
    Typography,
    Button,
    Card,
    Chip,
    Divider,
    Drawer,
    CircularProgress,
    Snackbar,
    Alert,
    IconButton,
    Slider,
    Checkbox,
    Input,
    FormControl,
    FormLabel,
    Select,
    Option,
    Autocomplete,
    AutocompleteOption,
    ListItemContent,
} from '@mui/joy';
import SearchIcon from '@mui/icons-material/Search';
import { useMediaQuery } from '@mui/system';
import RouteIcon from '@mui/icons-material/Route';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import LocationOnIcon from '@mui/icons-material/LocationOn';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import StraightenIcon from '@mui/icons-material/Straighten';
import DirectionsWalkIcon from '@mui/icons-material/DirectionsWalk';
import DirectionsCarIcon from '@mui/icons-material/DirectionsCar';
import StarIcon from '@mui/icons-material/Star';
import MenuIcon from '@mui/icons-material/Menu';
import MyLocationIcon from '@mui/icons-material/MyLocation';
import ChatSidebar from '../components/chat/ChatSidebar';
import MapPanel from '../components/chat/MapPanel';
import ResizableDivider from '../components/chat/ResizableDivider';
import { useAppSelector, useAppDispatch } from '../store/hooks';
import { toggleSidebar } from '../store/chatSlice';
import { generateRoutesThunk, clearRoutes } from '../store/routeSlice';
import { addSaveDestination } from '../store/savedSlice';
import { syncToggleToBackend } from '../store/savedThunks';
import { setStops } from '../store/navigationSlice';
import {
    createTravelPersonaAsync,
    type TravelPersona,
} from '../store/authSlice';
import { placeService } from '../services/placeService';
import type { RouteData, RoutePointData, RouteConstraints } from '../services/routeService';
import type { MapDestination } from '../data/destinations';
import TravelProfileBuilder from '../components/travel/TravelProfileBuilder';
import { defaultTravelProfile, summarizeTravelProfile } from '../utils/travelProfile';

// ─── Helpers ─────────────────────────────────────────────────────────────────

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

function mapPointToDestination(point: RoutePointData): MapDestination {
    const category = mapTypesToCategory(point.types);
    return {
        id: point.poiId || `point-${point.index}`,
        name: point.poiName || 'Unknown Place',
        location: point.formattedAddress || '',
        image: 'https://images.unsplash.com/photo-1488085061387-422e29b40080?w=800',
        rating: point.ratingScore || 4.0,
        priceLevel: mapPriceLevel(point.priceLevel),
        category,
        coordinates: [point.latitude, point.longitude] as [number, number],
        reviewCount: point.ratingCount,
    };
}

function mapTypesToCategory(types: string[] | undefined): string {
    if (!types || types.length === 0) return 'Landmarks';
    const joined = types.join(' ').toLowerCase();
    if (joined.includes('cafe') || joined.includes('dessert')) return 'Cafes & Desserts';
    if (joined.includes('restaurant') || joined.includes('food')) return 'Restaurants';
    if (joined.includes('park')) return 'Parks';
    if (joined.includes('museum') || joined.includes('art')) return 'Landmarks';
    if (joined.includes('history') || joined.includes('mosque')) return 'Historic Places';
    if (joined.includes('bar') || joined.includes('nightclub')) return 'Bars & Nightclubs';
    if (joined.includes('hotel') || joined.includes('lodging')) return 'Hotels';
    return 'Landmarks';
}

function mapPriceLevel(priceLevel: string | null | undefined): 1 | 2 | 3 | 4 {
    if (!priceLevel) return 1;
    switch (priceLevel) {
        case 'PRICE_LEVEL_INEXPENSIVE': return 1;
        case 'PRICE_LEVEL_MODERATE': return 2;
        case 'PRICE_LEVEL_EXPENSIVE': return 3;
        case 'PRICE_LEVEL_VERY_EXPENSIVE': return 4;
        default: return 1;
    }
}

function getTravelModeIcon(mode: string | undefined) {
    if (mode?.toLowerCase().includes('walk')) return <DirectionsWalkIcon sx={{ fontSize: 18 }} />;
    return <DirectionsCarIcon sx={{ fontSize: 18 }} />;
}

// ─── POI Type Options ────────────────────────────────────────────────────────

const POI_TYPE_OPTIONS = [
    { value: 'HOTEL', label: 'Hotel' },
    { value: 'RESTAURANT', label: 'Restaurant' },
    { value: 'CAFE', label: 'Cafe & Desserts' },
    { value: 'PARK', label: 'Park' },
    { value: 'LANDMARK', label: 'Landmark' },
    { value: 'HISTORIC_PLACE', label: 'Historic Place' },
    { value: 'BAR_NIGHTCLUB', label: 'Bar & Nightclub' },
];

// ─── Place Search Option ─────────────────────────────────────────────────────

interface PlaceOption {
    id: string;
    name: string;
    address: string;
}

// ─── Route Card Component ────────────────────────────────────────────────────

interface RouteCardProps {
    route: RouteData;
    index: number;
    onApprove: (route: RouteData) => void;
    isApproving: boolean;
    isApproved: boolean;
}

const RouteCard = ({ route, index, onApprove, isApproving, isApproved }: RouteCardProps) => {
    const validPoints = route.points.filter((p) => p.poiId);

    return (
        <Card
            id={`route-card-${index}`}
            variant="outlined"
            sx={{
                overflow: 'hidden',
                transition: 'all 0.3s ease-in-out',
                '&:hover': {
                    boxShadow: 'lg',
                    borderColor: 'primary.400',
                },
            }}
        >
            {/* Card Header */}
            <Box
                sx={{
                    p: 2,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    flexWrap: 'wrap',
                    gap: 1,
                    background: 'linear-gradient(135deg, var(--joy-palette-primary-softBg), var(--joy-palette-primary-softBg))',
                    borderBottom: '1px solid',
                    borderColor: 'divider',
                }}
            >
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                    <RouteIcon sx={{ color: 'primary.500', fontSize: 24 }} />
                    <Typography level="title-lg" sx={{ fontWeight: 700 }}>
                        Route {index + 1}
                    </Typography>
                    {route.feasible ? (
                        <Chip size="sm" variant="soft" color="success">Feasible</Chip>
                    ) : (
                        <Chip size="sm" variant="soft" color="warning">Long Route</Chip>
                    )}
                </Box>

                {/* Stats */}
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                        <AccessTimeIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                        <Typography level="body-sm" sx={{ color: 'text.secondary' }}>
                            ~{formatDuration(route.totalDurationSec)}
                        </Typography>
                    </Box>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                        <StraightenIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                        <Typography level="body-sm" sx={{ color: 'text.secondary' }}>
                            ~{formatDistance(route.totalDistanceM)}
                        </Typography>
                    </Box>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                        {getTravelModeIcon(route.travelMode)}
                        <Typography level="body-sm" sx={{ color: 'text.secondary', textTransform: 'capitalize' }}>
                            {route.travelMode || 'driving'}
                        </Typography>
                    </Box>
                </Box>
            </Box>

            {/* Points List */}
            <Box sx={{ p: 2 }}>
                <Typography level="body-sm" sx={{ fontWeight: 600, mb: 1.5, color: 'text.secondary' }}>
                    {validPoints.length} stops
                </Typography>

                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                    {validPoints.map((point, idx) => (
                        <Box
                            key={point.poiId || idx}
                            sx={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: 1.5,
                                p: 1.5,
                                borderRadius: 'md',
                                bgcolor: 'background.level1',
                                transition: 'background-color 0.2s',
                                '&:hover': {
                                    bgcolor: 'background.level2',
                                },
                            }}
                        >
                            {/* Stop number */}
                            <Box
                                sx={{
                                    width: 28,
                                    height: 28,
                                    borderRadius: '50%',
                                    bgcolor: 'primary.softBg',
                                    color: 'primary.600',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    fontWeight: 700,
                                    fontSize: 13,
                                    flexShrink: 0,
                                }}
                            >
                                {idx + 1}
                            </Box>

                            {/* POI info */}
                            <Box sx={{ flex: 1, minWidth: 0 }}>
                                <Typography
                                    level="body-sm"
                                    sx={{
                                        fontWeight: 600,
                                        overflow: 'hidden',
                                        textOverflow: 'ellipsis',
                                        whiteSpace: 'nowrap',
                                    }}
                                >
                                    {point.poiName || 'Unknown'}
                                </Typography>
                                {point.formattedAddress && (
                                    <Typography
                                        level="body-xs"
                                        sx={{
                                            color: 'text.secondary',
                                            overflow: 'hidden',
                                            textOverflow: 'ellipsis',
                                            whiteSpace: 'nowrap',
                                        }}
                                    >
                                        <LocationOnIcon sx={{ fontSize: 12, mr: 0.3, verticalAlign: 'middle' }} />
                                        {point.formattedAddress}
                                    </Typography>
                                )}
                            </Box>

                            {/* Rating */}
                            {point.ratingScore > 0 && (
                                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.3, flexShrink: 0 }}>
                                    <StarIcon sx={{ fontSize: 14, color: '#FFB800' }} />
                                    <Typography level="body-xs" sx={{ fontWeight: 600 }}>
                                        {point.ratingScore.toFixed(1)}
                                    </Typography>
                                </Box>
                            )}

                            {/* Visit duration */}
                            {point.plannedVisitMin > 0 && (
                                <Chip size="sm" variant="outlined" color="neutral" sx={{ flexShrink: 0 }}>
                                    {point.plannedVisitMin} min
                                </Chip>
                            )}
                        </Box>
                    ))}
                </Box>
            </Box>

            <Divider />

            {/* Approve Button */}
            <Box sx={{ p: 2, display: 'flex', justifyContent: 'flex-end' }}>
                <Button
                    id={`approve-route-${index}`}
                    variant={isApproved ? 'soft' : 'solid'}
                    color={isApproved ? 'success' : 'primary'}
                    startDecorator={
                        isApproving ? (
                            <CircularProgress size="sm" />
                        ) : (
                            <CheckCircleIcon />
                        )
                    }
                    disabled={isApproving || isApproved}
                    onClick={() => onApprove(route)}
                    sx={{
                        fontWeight: 600,
                        transition: 'all 0.2s',
                    }}
                >
                    {isApproving ? 'Saving...' : isApproved ? 'Approved' : 'Approve'}
                </Button>
            </Box>
        </Card>
    );
};

// ─── Route Page ──────────────────────────────────────────────────────────────

const RoutePage = () => {
    const dispatch = useAppDispatch();
    const navigate = useNavigate();

    const { isAuthenticated, user } = useAppSelector((state) => state.auth);
    const { sidebarOpen, mapFullscreen, chatPanelWidth } = useAppSelector((state) => state.chat);
    const { routes, isLoading, error } = useAppSelector((state) => state.route);

    const isMobile = useMediaQuery('(max-width: 768px)');
    const [mobileDrawerOpen, setMobileDrawerOpen] = useState(false);
    const [approvingRouteId, setApprovingRouteId] = useState<string | null>(null);
    const [approvedRouteIds, setApprovedRouteIds] = useState<Set<string>>(new Set());
    const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; color: 'success' | 'danger' }>({
        open: false,
        message: '',
        color: 'success',
    });
    const [routeCount, setRouteCount] = useState<number>(3);
    const [centerPoint, setCenterPoint] = useState<[number, number] | null>(null);
    const [activeRouteIdx, setActiveRouteIdx] = useState<number | null>(null);
    const [routeProfileMode, setRouteProfileMode] = useState<'builder' | 'saved-picker' | 'saved' | 'default' | 'none' | null>(null);
    const [sessionProfile, setSessionProfile] = useState<TravelPersona | null>(null);
    const [draftProfile, setDraftProfile] = useState<TravelPersona>(defaultTravelProfile());
    const [routeProfileBuilderKey, setRouteProfileBuilderKey] = useState(0);
    const [isSavingProfile, setIsSavingProfile] = useState(false);

    // ─── Constraint state ────────────────────────────────────────────────────
    const [stayAtHotel, setStayAtHotel] = useState(true);
    const [needsBreakfast, setNeedsBreakfast] = useState(true);
    const [needsLunch, setNeedsLunch] = useState(false);
    const [needsDinner, setNeedsDinner] = useState(true);

    // Start anchor
    const [startAnchorKind, setStartAnchorKind] = useState<'PLACE' | 'TYPE' | ''>('');
    const [startPlaceId, setStartPlaceId] = useState('');
    const [startPlaceOption, setStartPlaceOption] = useState<PlaceOption | null>(null);
    const [startPlaceOptions, setStartPlaceOptions] = useState<PlaceOption[]>([]);
    const [startPlaceLoading, setStartPlaceLoading] = useState(false);
    const [startPoiType, setStartPoiType] = useState('');
    const [startMinRating, setStartMinRating] = useState<string>('');
    const [startMinRatingCount, setStartMinRatingCount] = useState<string>('');

    // End anchor
    const [endAnchorKind, setEndAnchorKind] = useState<'PLACE' | 'TYPE' | ''>('');
    const [endPlaceId, setEndPlaceId] = useState('');
    const [endPlaceOption, setEndPlaceOption] = useState<PlaceOption | null>(null);
    const [endPlaceOptions, setEndPlaceOptions] = useState<PlaceOption[]>([]);
    const [endPlaceLoading, setEndPlaceLoading] = useState(false);
    const [endPoiType, setEndPoiType] = useState('');
    const [endMinRating, setEndMinRating] = useState<string>('');
    const [endMinRatingCount, setEndMinRatingCount] = useState<string>('');

    // Debounced place search
    const startSearchTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
    const endSearchTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

    const searchPlaces = useCallback(async (query: string, target: 'start' | 'end') => {
        if (query.length < 2) {
            if (target === 'start') setStartPlaceOptions([]);
            else setEndPlaceOptions([]);
            return;
        }
        const setLoading = target === 'start' ? setStartPlaceLoading : setEndPlaceLoading;
        const setOptions = target === 'start' ? setStartPlaceOptions : setEndPlaceOptions;
        setLoading(true);
        try {
            const results = await placeService.searchPlaces(query, 0, 10);
            setOptions(results.map((p) => ({ id: p.id, name: p.name, address: p.location })));
        } catch {
            setOptions([]);
        } finally {
            setLoading(false);
        }
    }, []);

    const handlePlaceInputChange = useCallback((query: string, target: 'start' | 'end') => {
        const timer = target === 'start' ? startSearchTimer : endSearchTimer;
        if (timer.current) clearTimeout(timer.current);
        timer.current = setTimeout(() => searchPlaces(query, target), 300);
    }, [searchPlaces]);

    // Cleanup timers
    useEffect(() => {
        return () => {
            if (startSearchTimer.current) clearTimeout(startSearchTimer.current);
            if (endSearchTimer.current) clearTimeout(endSearchTimer.current);
        };
    }, []);

    // Show only the hovered route's points on the map
    const activeRoute = activeRouteIdx !== null && activeRouteIdx < routes.length
        ? routes[activeRouteIdx]
        : null;
    const routeDestinations: MapDestination[] = activeRoute
        ? activeRoute.points.filter((p) => p.poiId).map(mapPointToDestination)
        : [];
    const routeLineCoords: [number, number][] | null = activeRoute
        ? activeRoute.points.map(p => [p.latitude, p.longitude] as [number, number])
        : null;
    const activeRouteMapKey = activeRoute?.routeId || 'no-route';
    const savedProfiles = user?.travelPersonas ?? [];
    const defaultProfile = savedProfiles.find((profile) => profile.isDefault) ?? null;
    const canGenerateRoutes =
        routeProfileMode === 'none'
        || routeProfileMode === 'default'
        || routeProfileMode === 'saved'
        || (routeProfileMode === 'builder' && sessionProfile !== null);

    if (!isAuthenticated) {
        return <Navigate to="/login" replace />;
    }

    const handleMobileDrawerClose = () => setMobileDrawerOpen(false);
    const handleMobileMenuClick = () => setMobileDrawerOpen(true);
    const handleToggleSidebar = () => dispatch(toggleSidebar());

    const resetRouteSession = () => {
        dispatch(clearRoutes());
        setActiveRouteIdx(null);
        setApprovedRouteIds(new Set());
    };

    const handleStartProfileBuilder = () => {
        setRouteProfileMode('builder');
        setDraftProfile(defaultTravelProfile());
        setSessionProfile(null);
        setRouteProfileBuilderKey((prev) => prev + 1);
        resetRouteSession();
    };

    const handleUseDefaultProfile = () => {
        if (!defaultProfile) {
            return;
        }
        setRouteProfileMode('default');
        setSessionProfile(defaultProfile);
        setDraftProfile(defaultProfile);
        resetRouteSession();
    };

    const handleUseNoProfile = () => {
        setRouteProfileMode('none');
        setSessionProfile(null);
        resetRouteSession();
    };

    const handleOpenSavedProfiles = () => {
        setRouteProfileMode('saved-picker');
        resetRouteSession();
    };

    const handleSelectSavedProfile = (profile: TravelPersona) => {
        setRouteProfileMode('saved');
        setSessionProfile(profile);
        setDraftProfile(profile);
        resetRouteSession();
    };

    const handleDraftConfirm = (profile: TravelPersona) => {
        setDraftProfile(profile);
        setSessionProfile(profile);
        setRouteProfileMode('builder');
        setSnackbar({
            open: true,
            message: 'The profile is ready for this route session.',
            color: 'success',
        });
    };

    const handleSaveDraftProfile = async () => {
        if (!sessionProfile || sessionProfile.id) {
            return;
        }

        setIsSavingProfile(true);
        try {
            const payload = {
                ...sessionProfile,
                name: sessionProfile.name.trim() || `Profile ${savedProfiles.length + 1}`,
            };
            const created = await dispatch(createTravelPersonaAsync(payload)).unwrap();
            setSessionProfile(created);
            setDraftProfile(created);
            setSnackbar({
                open: true,
                message: 'The profile was saved to your account.',
                color: 'success',
            });
        } catch {
            setSnackbar({
                open: true,
                message: 'There was a problem while saving the profile.',
                color: 'danger',
            });
        } finally {
            setIsSavingProfile(false);
        }
    };

    const buildConstraints = (): RouteConstraints => {
        const constraints: RouteConstraints = {
            stayAtHotel,
            needsBreakfast,
            needsLunch,
            needsDinner,
            startAnchor: null,
            endAnchor: null,
            poiSlots: null,
            requestedVisitCount: null,
        };

        if (startAnchorKind === 'PLACE' && startPlaceId) {
            constraints.startAnchor = { kind: 'PLACE', placeId: startPlaceId };
        } else if (startAnchorKind === 'TYPE' && startPoiType) {
            constraints.startAnchor = {
                kind: 'TYPE',
                poiType: startPoiType,
                filters: {
                    ...(startMinRating ? { minRating: parseFloat(startMinRating) } : {}),
                    ...(startMinRatingCount ? { minRatingCount: parseInt(startMinRatingCount, 10) } : {}),
                },
            };
        }

        if (endAnchorKind === 'PLACE' && endPlaceId) {
            constraints.endAnchor = { kind: 'PLACE', placeId: endPlaceId };
        } else if (endAnchorKind === 'TYPE' && endPoiType) {
            constraints.endAnchor = {
                kind: 'TYPE',
                poiType: endPoiType,
                filters: {
                    ...(endMinRating ? { minRating: parseFloat(endMinRating) } : {}),
                    ...(endMinRatingCount ? { minRatingCount: parseInt(endMinRatingCount, 10) } : {}),
                },
            };
        }

        return constraints;
    };

    const handleGenerate = () => {
        resetRouteSession();
        dispatch(generateRoutesThunk({
            k: routeCount,
            centerLat: centerPoint?.[0],
            centerLng: centerPoint?.[1],
            constraints: buildConstraints(),
            userVectorOverride: sessionProfile?.userVector,
            preferencesOverride: sessionProfile ?? undefined,
        }));
    };

    useEffect(() => {
        if (isLoading || routes.length === 0 || (activeRouteIdx !== null && activeRouteIdx >= routes.length)) {
            setActiveRouteIdx(null);
        }
    }, [activeRouteIdx, isLoading, routes.length]);

    const handleApprove = async (route: RouteData) => {
        setApprovingRouteId(route.routeId);
        try {
            const validPoints = route.points.filter((p) => p.poiId);
            // Save all POIs to the user's saved places (addSaveDestination only adds, never removes)
            for (const point of validPoints) {
                const destination: MapDestination = mapPointToDestination(point);
                dispatch(addSaveDestination(destination));
                await dispatch(syncToggleToBackend(destination));
            }
            // Set the navigation stops in order and navigate to the Navigation page
            const stopIds = validPoints.map((p) => p.poiId);
            dispatch(setStops(stopIds));

            setApprovedRouteIds((prev) => new Set(prev).add(route.routeId));
            setSnackbar({
                open: true,
                message: `Route approved! Navigating with ${validPoints.length} stops...`,
                color: 'success',
            });

            // Brief delay so the user sees the success message before navigating
            setTimeout(() => {
                navigate('/navigation');
            }, 800);
        } catch (err) {
            setSnackbar({
                open: true,
                message: 'Failed to save places. Please try again.',
                color: 'danger',
            });
        } finally {
            setApprovingRouteId(null);
        }
    };

    // Handle map click to set center point
    const handleMapClickForCenter = useCallback((_dest: MapDestination) => {
        // This is currently not used for destination selection, the map click handler below handles center
    }, []);

    // Main route panel content
    const renderRoutePanel = () => (
        <Box
            sx={{
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
                overflow: 'hidden',
            }}
        >
            {/* Top Bar */}
            <Box
                sx={{
                    p: 2,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 2,
                    borderBottom: '1px solid',
                    borderColor: 'divider',
                    flexShrink: 0,
                    flexWrap: 'wrap',
                }}
            >
                {(isMobile || !sidebarOpen) && (
                    <IconButton
                        variant="plain"
                        size="sm"
                        onClick={isMobile ? handleMobileMenuClick : handleToggleSidebar}
                    >
                        <MenuIcon />
                    </IconButton>
                )}

                <RouteIcon sx={{ color: 'primary.500', fontSize: 28 }} />
                <Typography level="h4" sx={{ fontWeight: 700, flex: 1 }}>
                    Route Generation
                </Typography>
            </Box>

            {/* Scrollable Content */}
            <Box sx={{ flex: 1, overflow: 'auto', p: { xs: 2, md: 3 } }}>
                {routeProfileMode === null && (
                    <Card variant="soft" color="primary" sx={{ mb: 3, p: 3 }}>
                        <Typography level="title-lg" sx={{ fontWeight: 700, mb: 0.5 }}>
                            Let's shape your route together
                        </Typography>
                        <Typography level="body-sm" sx={{ color: 'text.secondary', mb: 2.5 }}>
                            For better route suggestions, you can first create a travel profile, use one of your saved profiles, or continue without one.
                        </Typography>
                        <Box sx={{ display: 'grid', gap: 1.5 }}>
                            <Button onClick={handleStartProfileBuilder}>
                                Create a new travel profile
                            </Button>
                            <Button
                                variant="outlined"
                                onClick={handleUseDefaultProfile}
                                disabled={!defaultProfile}
                            >
                                Use my default profile
                            </Button>
                            <Button
                                variant="outlined"
                                color="neutral"
                                onClick={handleOpenSavedProfiles}
                                disabled={savedProfiles.length === 0}
                            >
                                Choose from my saved profiles
                            </Button>
                            <Button variant="plain" color="neutral" onClick={handleUseNoProfile}>
                                Continue without a profile
                            </Button>
                        </Box>
                    </Card>
                )}

                {routeProfileMode === 'builder' && (
                    <Box sx={{ display: 'grid', gap: 2, mb: 3 }}>
                        <TravelProfileBuilder
                            key={`route-profile-builder-${routeProfileBuilderKey}`}
                            initialValue={draftProfile}
                            title="Your route profile"
                            description="These answers are only used to tune the kinds of stops you prefer and the overall route density."
                            confirmLabel="Continue with this profile"
                            cancelLabel="Go back"
                            requireName={false}
                            isSaving={false}
                            onConfirm={handleDraftConfirm}
                            onCancel={() => {
                                setRouteProfileMode(null);
                                setSessionProfile(null);
                            }}
                        />

                        {sessionProfile && (
                            <Card variant="outlined" sx={{ p: 2.5 }}>
                                <Typography level="title-sm" sx={{ fontWeight: 700, mb: 0.5 }}>
                                    Profile used for this session
                                </Typography>
                                <Typography level="body-sm" sx={{ color: 'text.secondary', mb: 1.5 }}>
                                    {sessionProfile.name?.trim() || 'Untitled profile'} - {summarizeTravelProfile(sessionProfile)}
                                </Typography>
                                {!sessionProfile.id && (
                                    <Alert color="neutral" sx={{ mb: 2 }}>
                                        This profile has not been saved to your account yet. You can save it from this screen whenever you want.
                                    </Alert>
                                )}
                                <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap' }}>
                                    {!sessionProfile.id && (
                                        <Button
                                            variant="outlined"
                                            onClick={handleSaveDraftProfile}
                                            loading={isSavingProfile}
                                        >
                                            Save this profile to my account
                                        </Button>
                                    )}
                                    <Button variant="plain" color="neutral" onClick={() => setRouteProfileMode(null)}>
                                        Back to profile options
                                    </Button>
                                </Box>
                            </Card>
                        )}
                    </Box>
                )}

                {routeProfileMode === 'saved-picker' && (
                    <Card variant="outlined" sx={{ mb: 3, p: 3 }}>
                        <Typography level="title-md" sx={{ fontWeight: 700, mb: 0.5 }}>
                            Your saved profiles
                        </Typography>
                        <Typography level="body-sm" sx={{ color: 'text.secondary', mb: 2 }}>
                            Pick one of your saved profiles to start route generation faster.
                        </Typography>
                        <Box sx={{ display: 'grid', gap: 1.5 }}>
                            {savedProfiles.map((profile) => (
                                <Card key={profile.id || profile.name} variant="soft" sx={{ p: 2 }}>
                                    <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                                        <Box>
                                            <Typography level="title-sm" sx={{ fontWeight: 700 }}>
                                                {profile.name}
                                            </Typography>
                                            <Typography level="body-xs" sx={{ color: 'text.secondary' }}>
                                                {summarizeTravelProfile(profile)}
                                            </Typography>
                                        </Box>
                                        <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                                            {profile.isDefault && (
                                                <Chip size="sm" color="primary" variant="soft">
                                                    Default
                                                </Chip>
                                            )}
                                            <Button size="sm" onClick={() => handleSelectSavedProfile(profile)}>
                                                Use this profile
                                            </Button>
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Box>
                        <Button
                            variant="plain"
                            color="neutral"
                            sx={{ mt: 2 }}
                            onClick={() => setRouteProfileMode(null)}
                        >
                            Go back
                        </Button>
                    </Card>
                )}

                {routeProfileMode !== null && routeProfileMode !== 'saved-picker' && (
                    <Card variant="soft" color="neutral" sx={{ mb: 3, p: 2.5 }}>
                        <Typography level="body-sm" sx={{ fontWeight: 700 }}>
                            {routeProfileMode === 'none'
                                ? 'Profile-free mode is active'
                                : `Active profile: ${sessionProfile?.name?.trim() || 'Temporary profile'}`}
                        </Typography>
                        <Typography level="body-xs" sx={{ color: 'text.secondary', mt: 0.5 }}>
                            {routeProfileMode === 'none'
                                ? 'Routes will be generated with the default fallback weights.'
                                : summarizeTravelProfile(sessionProfile ?? undefined)}
                        </Typography>
                        <Button
                            variant="plain"
                            color="neutral"
                            size="sm"
                            sx={{ mt: 1 }}
                            onClick={() => {
                                setRouteProfileMode(null);
                                setSessionProfile(null);
                            }}
                        >
                            Change selection
                        </Button>
                    </Card>
                )}

                {/* Generate Controls */}
                {canGenerateRoutes && (
                    <Card
                        variant="outlined"
                        sx={{
                            mb: 3,
                            p: 3,
                            background: 'linear-gradient(135deg, var(--joy-palette-primary-softBg), transparent)',
                        }}
                    >
                    <Typography level="title-md" sx={{ fontWeight: 600, mb: 1 }}>
                        Generate Personalized Routes
                    </Typography>
                    <Typography level="body-sm" sx={{ color: 'text.secondary', mb: 2 }}>
                        Create AI-powered route suggestions based on your travel preferences.
                        Click on the map to set a center point for nearby routes.
                    </Typography>

                    {/* Center point indicator */}
                    {centerPoint && (
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                            <MyLocationIcon sx={{ color: 'primary.500', fontSize: 18 }} />
                            <Typography level="body-sm" sx={{ color: 'primary.600', fontWeight: 600 }}>
                                Center: {centerPoint[0].toFixed(4)}, {centerPoint[1].toFixed(4)}
                            </Typography>
                            <Button
                                size="sm"
                                variant="plain"
                                color="neutral"
                                onClick={() => setCenterPoint(null)}
                                sx={{ ml: 1, minHeight: 'auto', py: 0 }}
                            >
                                Clear
                            </Button>
                        </Box>
                    )}

                    <Box sx={{ mb: 2, maxWidth: 400 }}>
                        <Typography level="body-sm" sx={{ fontWeight: 600, mb: 1 }}>
                            Number of routes: {routeCount}
                        </Typography>
                        <Slider
                            id="route-count-slider"
                            value={routeCount}
                            onChange={(_e, val) => setRouteCount(val as number)}
                            min={1}
                            max={10}
                            step={1}
                            marks={[
                                { value: 1, label: '1' },
                                { value: 5, label: '5' },
                                { value: 10, label: '10' },
                            ]}
                            sx={{ py: 1 }}
                        />
                    </Box>

                    {/* ─── Constraint Controls ─────────────────────────── */}
                    <Divider sx={{ my: 2 }} />
                    <Typography level="title-sm" sx={{ fontWeight: 700, mb: 1.5 }}>
                        Route Constraints
                    </Typography>

                    {/* Meal & Hotel checkboxes */}
                    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mb: 2 }}>
                        <Checkbox
                            id="constraint-stay-at-hotel"
                            label="Stay at Hotel"
                            checked={stayAtHotel}
                            onChange={(e) => setStayAtHotel(e.target.checked)}
                        />
                        <Checkbox
                            id="constraint-needs-breakfast"
                            label="Needs Breakfast"
                            checked={needsBreakfast}
                            onChange={(e) => setNeedsBreakfast(e.target.checked)}
                        />
                        <Checkbox
                            id="constraint-needs-lunch"
                            label="Needs Lunch"
                            checked={needsLunch}
                            onChange={(e) => setNeedsLunch(e.target.checked)}
                        />
                        <Checkbox
                            id="constraint-needs-dinner"
                            label="Needs Dinner"
                            checked={needsDinner}
                            onChange={(e) => setNeedsDinner(e.target.checked)}
                        />
                    </Box>

                    {/* ─── Start Anchor ────────────────────────────────── */}
                    <Typography level="body-sm" sx={{ fontWeight: 600, mb: 1 }}>
                        Start Anchor
                    </Typography>
                    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mb: 2, alignItems: 'flex-end' }}>
                        <FormControl size="sm" sx={{ minWidth: 120 }}>
                            <FormLabel>Kind</FormLabel>
                            <Select
                                id="start-anchor-kind"
                                placeholder="None"
                                value={startAnchorKind || null}
                                onChange={(_e, val) => setStartAnchorKind((val as 'PLACE' | 'TYPE' | '') || '')}
                            >
                                <Option value="">None</Option>
                                <Option value="PLACE">Place</Option>
                                <Option value="TYPE">Type</Option>
                            </Select>
                        </FormControl>

                        {startAnchorKind === 'PLACE' && (
                            <FormControl size="sm" sx={{ flex: 1, minWidth: 250 }}>
                                <FormLabel>Search Place</FormLabel>
                                <Autocomplete
                                    id="start-place-search"
                                    placeholder="Type to search places..."
                                    options={startPlaceOptions}
                                    getOptionLabel={(opt) => opt.name}
                                    isOptionEqualToValue={(opt, val) => opt.id === val.id}
                                    value={startPlaceOption}
                                    loading={startPlaceLoading}
                                    onInputChange={(_e, value) => handlePlaceInputChange(value, 'start')}
                                    onChange={(_e, value) => {
                                        setStartPlaceOption(value);
                                        setStartPlaceId(value?.id || '');
                                    }}
                                    startDecorator={<SearchIcon sx={{ fontSize: 18 }} />}
                                    renderOption={(props, option) => (
                                        <AutocompleteOption {...props} key={option.id}>
                                            <ListItemContent>
                                                <Typography level="body-sm" sx={{ fontWeight: 600 }}>
                                                    {option.name}
                                                </Typography>
                                                <Typography level="body-xs" sx={{ color: 'text.secondary' }}>
                                                    {option.address}
                                                </Typography>
                                            </ListItemContent>
                                        </AutocompleteOption>
                                    )}
                                />
                            </FormControl>
                        )}

                        {startAnchorKind === 'TYPE' && (
                            <>
                                <FormControl size="sm" sx={{ flex: 1, minWidth: 160 }}>
                                    <FormLabel>POI Type</FormLabel>
                                    <Select
                                        id="start-poi-type"
                                        placeholder="Select type..."
                                        value={startPoiType || null}
                                        onChange={(_e, val) => setStartPoiType(val || '')}
                                    >
                                        {POI_TYPE_OPTIONS.map((opt) => (
                                            <Option key={opt.value} value={opt.value}>
                                                {opt.label}
                                            </Option>
                                        ))}
                                    </Select>
                                </FormControl>
                                <FormControl size="sm" sx={{ minWidth: 100 }}>
                                    <FormLabel>Min Rating</FormLabel>
                                    <Input
                                        id="start-min-rating"
                                        type="number"
                                        slotProps={{ input: { step: 0.1, min: 0, max: 5 } }}
                                        placeholder="4.5"
                                        value={startMinRating}
                                        onChange={(e) => setStartMinRating(e.target.value)}
                                    />
                                </FormControl>
                                <FormControl size="sm" sx={{ minWidth: 130 }}>
                                    <FormLabel>Min Rating Count</FormLabel>
                                    <Input
                                        id="start-min-rating-count"
                                        type="number"
                                        slotProps={{ input: { step: 100, min: 0 } }}
                                        placeholder="2000"
                                        value={startMinRatingCount}
                                        onChange={(e) => setStartMinRatingCount(e.target.value)}
                                    />
                                </FormControl>
                            </>
                        )}
                    </Box>

                    {/* ─── End Anchor ──────────────────────────────────── */}
                    <Typography level="body-sm" sx={{ fontWeight: 600, mb: 1 }}>
                        End Anchor
                    </Typography>
                    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mb: 2, alignItems: 'flex-end' }}>
                        <FormControl size="sm" sx={{ minWidth: 120 }}>
                            <FormLabel>Kind</FormLabel>
                            <Select
                                id="end-anchor-kind"
                                placeholder="None"
                                value={endAnchorKind || null}
                                onChange={(_e, val) => setEndAnchorKind((val as 'PLACE' | 'TYPE' | '') || '')}
                            >
                                <Option value="">None</Option>
                                <Option value="PLACE">Place</Option>
                                <Option value="TYPE">Type</Option>
                            </Select>
                        </FormControl>

                        {endAnchorKind === 'PLACE' && (
                            <FormControl size="sm" sx={{ flex: 1, minWidth: 250 }}>
                                <FormLabel>Search Place</FormLabel>
                                <Autocomplete
                                    id="end-place-search"
                                    placeholder="Type to search places..."
                                    options={endPlaceOptions}
                                    getOptionLabel={(opt) => opt.name}
                                    isOptionEqualToValue={(opt, val) => opt.id === val.id}
                                    value={endPlaceOption}
                                    loading={endPlaceLoading}
                                    onInputChange={(_e, value) => handlePlaceInputChange(value, 'end')}
                                    onChange={(_e, value) => {
                                        setEndPlaceOption(value);
                                        setEndPlaceId(value?.id || '');
                                    }}
                                    startDecorator={<SearchIcon sx={{ fontSize: 18 }} />}
                                    renderOption={(props, option) => (
                                        <AutocompleteOption {...props} key={option.id}>
                                            <ListItemContent>
                                                <Typography level="body-sm" sx={{ fontWeight: 600 }}>
                                                    {option.name}
                                                </Typography>
                                                <Typography level="body-xs" sx={{ color: 'text.secondary' }}>
                                                    {option.address}
                                                </Typography>
                                            </ListItemContent>
                                        </AutocompleteOption>
                                    )}
                                />
                            </FormControl>
                        )}

                        {endAnchorKind === 'TYPE' && (
                            <>
                                <FormControl size="sm" sx={{ flex: 1, minWidth: 160 }}>
                                    <FormLabel>POI Type</FormLabel>
                                    <Select
                                        id="end-poi-type"
                                        placeholder="Select type..."
                                        value={endPoiType || null}
                                        onChange={(_e, val) => setEndPoiType(val || '')}
                                    >
                                        {POI_TYPE_OPTIONS.map((opt) => (
                                            <Option key={opt.value} value={opt.value}>
                                                {opt.label}
                                            </Option>
                                        ))}
                                    </Select>
                                </FormControl>
                                <FormControl size="sm" sx={{ minWidth: 100 }}>
                                    <FormLabel>Min Rating</FormLabel>
                                    <Input
                                        id="end-min-rating"
                                        type="number"
                                        slotProps={{ input: { step: 0.1, min: 0, max: 5 } }}
                                        placeholder="4.5"
                                        value={endMinRating}
                                        onChange={(e) => setEndMinRating(e.target.value)}
                                    />
                                </FormControl>
                                <FormControl size="sm" sx={{ minWidth: 130 }}>
                                    <FormLabel>Min Rating Count</FormLabel>
                                    <Input
                                        id="end-min-rating-count"
                                        type="number"
                                        slotProps={{ input: { step: 100, min: 0 } }}
                                        placeholder="2000"
                                        value={endMinRatingCount}
                                        onChange={(e) => setEndMinRatingCount(e.target.value)}
                                    />
                                </FormControl>
                            </>
                        )}
                    </Box>

                    <Button
                        id="generate-routes-btn"
                        variant="solid"
                        color="primary"
                        size="lg"
                        startDecorator={isLoading ? <CircularProgress size="sm" /> : <RouteIcon />}
                        onClick={handleGenerate}
                        disabled={isLoading}
                        sx={{
                            fontWeight: 600,
                            px: 4,
                            alignSelf: 'flex-start',
                            transition: 'all 0.2s',
                        }}
                    >
                        {isLoading ? 'Generating...' : 'Generate Routes'}
                    </Button>
                    </Card>
                )}

                {/* Error */}
                {error && (
                    <Card variant="soft" color="danger" sx={{ mb: 3, p: 2 }}>
                        <Typography level="body-sm" sx={{ color: 'danger.700' }}>
                            {error}
                        </Typography>
                    </Card>
                )}

                {/* Route Cards */}
                {routes.length > 0 && (
                    <Box
                        sx={{
                            display: 'flex',
                            flexDirection: 'column',
                            gap: 3,
                        }}
                    >
                        {routes.map((route, idx) => (
                            <Box
                                key={route.routeId || idx}
                                onMouseEnter={() => setActiveRouteIdx(idx)}
                            >
                                <RouteCard
                                    route={route}
                                    index={idx}
                                    onApprove={handleApprove}
                                    isApproving={approvingRouteId === route.routeId}
                                    isApproved={approvedRouteIds.has(route.routeId)}
                                />
                            </Box>
                        ))}
                    </Box>
                )}

                {/* Empty state */}
                {!isLoading && routes.length === 0 && !error && canGenerateRoutes && (
                    <Box
                        sx={{
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: 'center',
                            justifyContent: 'center',
                            py: 8,
                            opacity: 0.6,
                        }}
                    >
                        <RouteIcon sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
                        <Typography level="title-md" sx={{ color: 'text.secondary' }}>
                            No routes generated yet
                        </Typography>
                        <Typography level="body-sm" sx={{ color: 'text.tertiary' }}>
                            Click on the map to pick a center, then generate routes.
                        </Typography>
                    </Box>
                )}
            </Box>
        </Box>
    );

    return (
        <Box sx={{ height: '100vh', display: 'flex', overflow: 'hidden' }}>
            {/* Desktop Sidebar */}
            {!isMobile && sidebarOpen && <ChatSidebar />}

            {/* Mobile Sidebar Drawer */}
            {isMobile && (
                <Drawer
                    open={mobileDrawerOpen}
                    onClose={handleMobileDrawerClose}
                    size="sm"
                    sx={{ '--Drawer-horizontalSize': '280px' }}
                >
                    <ChatSidebar mobile onClose={handleMobileDrawerClose} />
                </Drawer>
            )}

            {/* Main Content Area */}
            <Box
                sx={{
                    flex: 1,
                    display: 'flex',
                    flexDirection: 'column',
                    overflow: 'hidden',
                    position: 'relative',
                }}
            >
                {/* Mobile Layout */}
                {isMobile ? (
                    <>
                        {!mapFullscreen && (
                            <Box sx={{ flex: 1, overflow: 'hidden' }}>
                                {renderRoutePanel()}
                            </Box>
                        )}

                        {mapFullscreen && (
                            <Box sx={{ flex: 1, overflow: 'hidden' }}>
                                <MapPanel
                                    destinations={routeDestinations}
                                    route={routeLineCoords}
                                    orderedDestinations={routeDestinations}
                                    onMapClick={(latlng) => setCenterPoint([latlng.lat, latlng.lng])}
                                    disableClustering
                                    fitCoordinates={routeLineCoords}
                                    markerKeyPrefix={activeRouteMapKey}
                                />
                            </Box>
                        )}
                    </>
                ) : (
                    /* Desktop: Side-by-side layout */
                    <Box sx={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
                        {/* Route Panel */}
                        {!mapFullscreen && (
                            <Box
                                sx={{
                                    width: `${chatPanelWidth}%`,
                                    height: '100%',
                                    flexShrink: 0,
                                    overflow: 'hidden',
                                }}
                            >
                                {renderRoutePanel()}
                            </Box>
                        )}

                        {/* Resizable Divider */}
                        {!mapFullscreen && (
                            <ResizableDivider orientation="vertical" />
                        )}

                        {/* Map Panel */}
                        <Box
                            sx={{
                                flex: mapFullscreen ? 1 : undefined,
                                width: mapFullscreen ? '100%' : `${100 - chatPanelWidth}%`,
                                height: '100%',
                                flexShrink: 0,
                                overflow: 'hidden',
                            }}
                        >
                            <MapPanel
                                destinations={routeDestinations}
                                route={routeLineCoords}
                                orderedDestinations={routeDestinations}
                                onDestinationSelect={handleMapClickForCenter}
                                onMapClick={(latlng) => setCenterPoint([latlng.lat, latlng.lng])}
                                disableClustering
                                fitCoordinates={routeLineCoords}
                                markerKeyPrefix={activeRouteMapKey}
                            />
                        </Box>
                    </Box>
                )}
            </Box>

            {/* Snackbar */}
            <Snackbar
                open={snackbar.open}
                autoHideDuration={4000}
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

export default RoutePage;
