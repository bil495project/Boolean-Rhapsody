import { useState, useCallback, useEffect, useRef } from 'react';
import { Navigate, useNavigate, useParams } from 'react-router-dom';
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
} from '@mui/joy';
import { useMediaQuery } from '@mui/system';
import RouteIcon from '@mui/icons-material/Route';
import MenuIcon from '@mui/icons-material/Menu';
import MyLocationIcon from '@mui/icons-material/MyLocation';
import ChatSidebar from '../components/chat/ChatSidebar';
import MapPanel from '../components/chat/MapPanel';
import ResizableDivider from '../components/chat/ResizableDivider';
import EditableRouteCard from '../components/route/EditableRouteCard';
import PlaceSearchAutocomplete from '../components/route/PlaceSearchAutocomplete';
import { useAppSelector, useAppDispatch } from '../store/hooks';
import { toggleSidebar } from '../store/chatSlice';
import { generateRoutesThunk, clearRoutes, hydrateSavedRoute, replaceRouteAtIndex, approveRouteForChat, clearChatApproval } from '../store/routeSlice';
import { addSaveDestination } from '../store/savedSlice';
import { syncToggleToBackend } from '../store/savedThunks';
import { setStops } from '../store/navigationSlice';
import {
    createSavedRouteThunk,
    fetchSavedRouteDetail,
    updateSavedRouteThunk,
} from '../store/savedRoutesSlice';
import {
    createTravelPersonaAsync,
    type TravelPersona,
} from '../store/authSlice';
import type {
    GenerateRoutesPayload,
    RouteConstraints,
    RouteData,
    RoutePointData,
    RouteBoundarySelection,
    RouteBoundarySelectionType,
} from '../services/routeService';
import { placeService } from '../services/placeService';
import { routeService } from '../services/routeService';
import type { MapDestination } from '../data/destinations';
import TravelProfileBuilder from '../components/travel/TravelProfileBuilder';
import { defaultTravelProfile, summarizeTravelProfile } from '../utils/travelProfile';
import { mapTypesArrayToCategory } from '../utils/placeCategory';
import { getPlaceImage } from '../utils/placeImage';

// ─── Helpers ─────────────────────────────────────────────────────────────────

function mapPointToDestination(point: RoutePointData): MapDestination {
    const category = mapTypesToCategory(point.types);
    return {
        id: point.poiId || `point-${point.index}`,
        name: point.poiName || 'Unknown Place',
        location: point.formattedAddress || '',
        image: getPlaceImage({
            id: point.poiId || `point-${point.index}`,
            name: point.poiName || 'Unknown Place',
            category,
        }),
        rating: point.ratingScore || 4.0,
        priceLevel: mapPriceLevel(point.priceLevel),
        category,
        coordinates: [point.latitude, point.longitude] as [number, number],
        reviewCount: point.ratingCount,
    };
}

function mapTypesToCategory(types: string[] | undefined): string {
    return mapTypesArrayToCategory(types);
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

// ─── Route Page ──────────────────────────────────────────────────────────────

const RoutePage = () => {
    const dispatch = useAppDispatch();
    const navigate = useNavigate();
    const { savedRouteId: savedRouteParam } = useParams();

    const { isAuthenticated, user } = useAppSelector((state) => state.auth);
    const { sidebarOpen, mapFullscreen, chatPanelWidth } = useAppSelector((state) => state.chat);
    const { routes, isLoading, error, currentRequest, savedRouteId, savedRouteTitle, pendingChatApproval, returnChatId } = useAppSelector((state) => state.route);
    const { isSaving: isSavedRouteSaving } = useAppSelector((state) => state.savedRoutes);

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
    const [isUpdatingSavedRoute, setIsUpdatingSavedRoute] = useState(false);
    const [mutatingRouteId, setMutatingRouteId] = useState<string | null>(null);

    // ─── Constraint state ────────────────────────────────────────────────────
    const [needsBreakfast, setNeedsBreakfast] = useState(true);
    const [needsLunch, setNeedsLunch] = useState(false);
    const [needsDinner, setNeedsDinner] = useState(true);
    const [startPointType, setStartPointType] = useState<RouteBoundarySelectionType>('HOTEL');
    const [endPointType, setEndPointType] = useState<RouteBoundarySelectionType>('HOTEL');
    const [startSelectedPlace, setStartSelectedPlace] = useState<MapDestination | null>(null);
    const [endSelectedPlace, setEndSelectedPlace] = useState<MapDestination | null>(null);
    const [startPoiType, setStartPoiType] = useState('');
    const [startMinRating, setStartMinRating] = useState<string>('');
    const [startMinRatingCount, setStartMinRatingCount] = useState<string>('');
    const [startPlaceOptions, setStartPlaceOptions] = useState<MapDestination[]>([]);
    const [startPlaceLoading, setStartPlaceLoading] = useState(false);
    const [endPoiType, setEndPoiType] = useState('');
    const [endMinRating, setEndMinRating] = useState<string>('');
    const [endMinRatingCount, setEndMinRatingCount] = useState<string>('');
    const [endPlaceOptions, setEndPlaceOptions] = useState<MapDestination[]>([]);
    const [endPlaceLoading, setEndPlaceLoading] = useState(false);
    const [activeInsertTarget, setActiveInsertTarget] = useState<{ routeIndex: number; insertIndex: number } | null>(null);
    const [insertPlaceOptions, setInsertPlaceOptions] = useState<MapDestination[]>([]);
    const [insertPlaceLoading, setInsertPlaceLoading] = useState(false);
    const [insertSelectedPlace, setInsertSelectedPlace] = useState<MapDestination | null>(null);
    const startSearchTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
    const endSearchTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
    const insertSearchTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

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

    const prepareForRouteRefresh = () => {
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

    const searchPlaces = useCallback(async (query: string, target: 'start' | 'end' | 'insert') => {
        const setLoading = target === 'start'
            ? setStartPlaceLoading
            : target === 'end'
                ? setEndPlaceLoading
                : setInsertPlaceLoading;
        const setOptions = target === 'start'
            ? setStartPlaceOptions
            : target === 'end'
                ? setEndPlaceOptions
                : setInsertPlaceOptions;

        if (query.trim().length < 2) {
            setOptions([]);
            return;
        }

        setLoading(true);
        try {
            const results = await placeService.searchPlaces(query, 0, 10);
            const filtered = target === 'insert' && activeInsertTarget
                ? results.filter((destination) => !routes[activeInsertTarget.routeIndex]?.points.some((point) => point.poiId === destination.id))
                : results;
            setOptions(filtered);
        } catch {
            setOptions([]);
        } finally {
            setLoading(false);
        }
    }, [activeInsertTarget, routes]);

    const handlePlaceInputChange = useCallback((query: string, target: 'start' | 'end' | 'insert') => {
        const timer = target === 'start'
            ? startSearchTimer
            : target === 'end'
                ? endSearchTimer
                : insertSearchTimer;

        if (timer.current) {
            clearTimeout(timer.current);
        }
        timer.current = setTimeout(() => {
            void searchPlaces(query, target);
        }, 300);
    }, [searchPlaces]);

    const closeInsertPicker = () => {
        setActiveInsertTarget(null);
        setInsertSelectedPlace(null);
        setInsertPlaceOptions([]);
        setInsertPlaceLoading(false);
    };

    useEffect(() => () => {
        if (startSearchTimer.current) clearTimeout(startSearchTimer.current);
        if (endSearchTimer.current) clearTimeout(endSearchTimer.current);
        if (insertSearchTimer.current) clearTimeout(insertSearchTimer.current);
    }, []);

    const buildFilters = (minRating: string, minRatingCount: string) => {
        const filters: NonNullable<RouteBoundarySelection['filters']> = {};
        if (minRating) {
            filters.minRating = parseFloat(minRating);
        }
        if (minRatingCount) {
            filters.minRatingCount = parseInt(minRatingCount, 10);
        }
        return Object.keys(filters).length > 0 ? filters : undefined;
    };

    const buildBoundarySelection = (
        type: RouteBoundarySelectionType,
        selectedPlace: MapDestination | null,
        poiType: string,
        minRating: string,
        minRatingCount: string,
    ): RouteBoundarySelection | null => {
        switch (type) {
            case 'HOTEL':
                return { type: 'HOTEL' };
            case 'PLACE':
                return selectedPlace?.id
                    ? { type: 'PLACE', placeId: selectedPlace.id }
                    : { type: 'PLACE' };
            case 'TYPE':
                return {
                    type: 'TYPE',
                    poiType: poiType || undefined,
                    filters: buildFilters(minRating, minRatingCount),
                };
            case 'NONE':
            default:
                return null;
        }
    };

    const buildConstraints = (): RouteConstraints => ({
        needsBreakfast,
        needsLunch,
        needsDinner,
        startPoint: buildBoundarySelection(
            startPointType,
            startSelectedPlace,
            startPoiType,
            startMinRating,
            startMinRatingCount,
        ),
        endPoint: buildBoundarySelection(
            endPointType,
            endSelectedPlace,
            endPoiType,
            endMinRating,
            endMinRatingCount,
        ),
        startAnchor: null,
        endAnchor: null,
        poiSlots: null,
    });

    const findPlaceOption = (placeId?: string | null): MapDestination | null => {
        if (!placeId) {
            return null;
        }
        const knownPlaces = [
            startSelectedPlace,
            endSelectedPlace,
            insertSelectedPlace,
            ...startPlaceOptions,
            ...endPlaceOptions,
            ...insertPlaceOptions,
        ].filter((place): place is MapDestination => Boolean(place));

        return knownPlaces.find((place) => place.id === placeId) ?? null;
    };

    const legacyConstraintToBoundarySelection = (
        constraints: RouteConstraints | null | undefined,
        target: 'start' | 'end',
    ): RouteBoundarySelection | null => {
        if (!constraints) {
            return null;
        }

        const explicit = target === 'start' ? constraints.startPoint : constraints.endPoint;
        if (explicit) {
            return explicit;
        }

        const withPoi = target === 'start' ? constraints.startWithPoi : constraints.endWithPoi;
        const withHotel = target === 'start' ? constraints.startWithHotel : constraints.endWithHotel;
        const anchor = target === 'start' ? constraints.startAnchor : constraints.endAnchor;

        if (withHotel && anchor?.kind === 'PLACE' && anchor.placeId) {
            return { type: 'PLACE', placeId: anchor.placeId };
        }

        if (withHotel) {
            return { type: 'HOTEL' };
        }

        if (withPoi && anchor?.kind === 'PLACE' && anchor.placeId) {
            return { type: 'PLACE', placeId: anchor.placeId };
        }

        if (withPoi && anchor?.kind === 'TYPE' && anchor.poiType) {
            return { type: 'TYPE', poiType: anchor.poiType, filters: anchor.filters };
        }

        if (constraints.stayAtHotel && !withPoi && !anchor) {
            return { type: 'HOTEL' };
        }

        return null;
    };

    const applyBoundarySelectionToState = (
        boundary: RouteBoundarySelection | null,
        target: 'start' | 'end',
    ) => {
        const setType = target === 'start' ? setStartPointType : setEndPointType;
        const setSelectedPlace = target === 'start' ? setStartSelectedPlace : setEndSelectedPlace;
        const setPoiType = target === 'start' ? setStartPoiType : setEndPoiType;
        const setMinRating = target === 'start' ? setStartMinRating : setEndMinRating;
        const setMinRatingCount = target === 'start' ? setStartMinRatingCount : setEndMinRatingCount;

        if (!boundary) {
            setType('NONE');
            setSelectedPlace(null);
            setPoiType('');
            setMinRating('');
            setMinRatingCount('');
            return;
        }

        setType(boundary.type);
        setSelectedPlace(boundary.type === 'PLACE' ? findPlaceOption(boundary.placeId) ?? (boundary.placeId ? {
            id: boundary.placeId,
            name: boundary.placeId,
            location: '',
            image: getPlaceImage({
                id: boundary.placeId,
                name: boundary.placeId,
                category: 'Landmarks',
            }),
            rating: 0,
            priceLevel: 1,
            category: 'Landmarks',
            coordinates: [0, 0],
            reviewCount: 0,
        } : null) : null);
        setPoiType(boundary.type === 'TYPE' ? boundary.poiType || '' : '');
        setMinRating(boundary.type === 'TYPE' && boundary.filters?.minRating !== undefined ? String(boundary.filters.minRating) : '');
        setMinRatingCount(boundary.type === 'TYPE' && boundary.filters?.minRatingCount !== undefined ? String(boundary.filters.minRatingCount) : '');
    };

    const applyGenerateRequestToForm = (payload: GenerateRoutesPayload) => {
        setRouteCount(payload.k ?? 3);
        setCenterPoint(
            typeof payload.centerLat === 'number' && typeof payload.centerLng === 'number'
                ? [payload.centerLat, payload.centerLng]
                : null,
        );

        const constraints = payload.constraints;
        setNeedsBreakfast(constraints?.needsBreakfast ?? false);
        setNeedsLunch(constraints?.needsLunch ?? false);
        setNeedsDinner(constraints?.needsDinner ?? false);
        applyBoundarySelectionToState(legacyConstraintToBoundarySelection(constraints, 'start'), 'start');
        applyBoundarySelectionToState(legacyConstraintToBoundarySelection(constraints, 'end'), 'end');
    };

    const getGenerationValidationError = () => {
        if (startPointType === 'PLACE' && !startSelectedPlace?.id) {
            return 'Please choose a start place.';
        }
        if (endPointType === 'PLACE' && !endSelectedPlace?.id) {
            return 'Please choose an end place.';
        }
        if (startPointType === 'TYPE' && !startPoiType) {
            return 'Please choose a start type.';
        }
        if (endPointType === 'TYPE' && !endPoiType) {
            return 'Please choose an end type.';
        }
        return null;
    };

    const handleGenerate = () => {
        const validationError = getGenerationValidationError();
        if (validationError) {
            setSnackbar({
                open: true,
                message: validationError,
                color: 'danger',
            });
            return;
        }

        prepareForRouteRefresh();
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
        if (isLoading || routes.length === 0) {
            setActiveRouteIdx(null);
            return;
        }
        if (activeRouteIdx === null || activeRouteIdx >= routes.length) {
            setActiveRouteIdx(0);
        }
    }, [activeRouteIdx, isLoading, routes.length]);

    useEffect(() => {
        if (!isAuthenticated || !savedRouteParam) {
            return;
        }

        let cancelled = false;
        dispatch(fetchSavedRouteDetail(savedRouteParam))
            .unwrap()
            .then((detail) => {
                if (cancelled) {
                    return;
                }
                dispatch(hydrateSavedRoute({
                    route: detail.route,
                    generateRequest: detail.generateRequest,
                    savedRouteId: detail.id,
                    title: detail.title,
                }));
                applyGenerateRequestToForm(detail.generateRequest);
                setRouteProfileMode('none');
                setSessionProfile(null);
                setSnackbar({
                    open: true,
                    message: `Loaded saved route: ${detail.title}`,
                    color: 'success',
                });
            })
            .catch(() => {
                if (!cancelled) {
                    setSnackbar({
                        open: true,
                        message: 'Saved route could not be loaded.',
                        color: 'danger',
                    });
                }
            });

        return () => {
            cancelled = true;
        };
    }, [dispatch, isAuthenticated, savedRouteParam]);

    const persistRouteSnapshot = async (routeToPersist: RouteData) => {
        if (!currentRequest) {
            return null;
        }

        if (savedRouteId) {
            const detail = await dispatch(updateSavedRouteThunk({
                savedRouteId,
                payload: {
                    title: savedRouteTitle || undefined,
                    route: routeToPersist,
                    generateRequest: currentRequest,
                },
            })).unwrap();

            dispatch(hydrateSavedRoute({
                route: detail.route,
                generateRequest: detail.generateRequest,
                savedRouteId: detail.id,
                title: detail.title,
            }));

            return {
                detail,
                message: `Route approved and updated in "${detail.title}".`,
            };
        }

        const detail = await dispatch(createSavedRouteThunk({
            route: routeToPersist,
            generateRequest: currentRequest,
        })).unwrap();

        return {
            detail,
            message: `Route approved and saved as "${detail.title}".`,
        };
    };

    const handleUpdateSavedRoute = async () => {
        const routeToPersist = activeRoute ?? routes[0];
        if (!savedRouteId || !currentRequest || !routeToPersist) {
            return;
        }

        setIsUpdatingSavedRoute(true);
        try {
            await persistRouteSnapshot(routeToPersist);
            setSnackbar({
                open: true,
                message: 'Saved route updated.',
                color: 'success',
            });
        } catch {
            setSnackbar({
                open: true,
                message: 'Saved route could not be updated.',
                color: 'danger',
            });
        } finally {
            setIsUpdatingSavedRoute(false);
        }
    };

    const handleApprove = async (route: RouteData) => {
        // ── Chat approval flow: save to DB, then return to the chat agent ──
        if (pendingChatApproval) {
            setApprovingRouteId(route.routeId);
            try {
                // Save route to database (Saved Routes)
                if (currentRequest) {
                    await dispatch(createSavedRouteThunk({
                        route,
                        generateRequest: currentRequest,
                    })).unwrap();
                }

                dispatch(approveRouteForChat(route));
                setSnackbar({
                    open: true,
                    message: 'Route approved and saved! Returning to chat...',
                    color: 'success',
                });

                // Brief delay so the user sees the success message
                const targetChat = returnChatId ? `/chat/${returnChatId}` : '/chat';
                setTimeout(() => navigate(targetChat), 600);
            } catch (err) {
                // Even if save fails, still approve and navigate back
                console.error('Failed to save route, approving anyway:', err);
                dispatch(approveRouteForChat(route));
                const targetChat = returnChatId ? `/chat/${returnChatId}` : '/chat';
                navigate(targetChat);
            } finally {
                setApprovingRouteId(null);
            }
            return;
        }

        // ── Standard approval flow: save and navigate to navigation ──
        setApprovingRouteId(route.routeId);
        try {
            let savedRouteMessage = 'Route approved!';
            if (currentRequest) {
                const persisted = await persistRouteSnapshot(route);
                if (persisted?.message) {
                    savedRouteMessage = persisted.message;
                }
            }

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
                message: `${savedRouteMessage} Navigating with ${validPoints.length} stops...`,
                color: 'success',
            });

            // Brief delay so the user sees the success message before navigating
            setTimeout(() => {
                navigate('/navigation');
            }, 800);
        } catch (err) {
            setSnackbar({
                open: true,
                message: 'Failed to approve and save the route. Please try again.',
                color: 'danger',
            });
        } finally {
            setApprovingRouteId(null);
        }
    };

    const performRouteMutation = async (
        routeIndex: number,
        run: (route: RouteData, originalUserVector: Record<string, string>) => Promise<RouteData>,
        successMessage: string,
    ) => {
        const route = routes[routeIndex];
        const originalUserVector = currentRequest?.userVector;

        if (!route || !originalUserVector) {
            setSnackbar({
                open: true,
                message: 'Route state is incomplete. Please generate the route again.',
                color: 'danger',
            });
            return;
        }

        setMutatingRouteId(route.routeId);
        try {
            const updatedRoute = await run(route, originalUserVector);
            dispatch(replaceRouteAtIndex({ index: routeIndex, route: updatedRoute }));
            setActiveRouteIdx(routeIndex);
            closeInsertPicker();
            setSnackbar({
                open: true,
                message: successMessage,
                color: 'success',
            });
        } catch (mutationError: any) {
            setSnackbar({
                open: true,
                message: mutationError?.response?.data?.error || mutationError?.message || 'Route could not be updated.',
                color: 'danger',
            });
        } finally {
            setMutatingRouteId(null);
        }
    };

    const handleInsertStop = (routeIndex: number, insertIndex: number) => {
        setActiveInsertTarget({ routeIndex, insertIndex });
        setInsertSelectedPlace(null);
        setInsertPlaceOptions([]);
    };

    const handleRemoveStop = (routeIndex: number, pointIndex: number) => {
        void performRouteMutation(
            routeIndex,
            (route, originalUserVector) => routeService.removePoint({
                currentRoute: route,
                index: pointIndex,
                originalUserVector,
            }),
            'Stop removed from the route.',
        );
    };

    const handleRerollStop = (routeIndex: number, pointIndex: number) => {
        void performRouteMutation(
            routeIndex,
            (route, originalUserVector) => routeService.rerollPoint({
                currentRoute: route,
                index: pointIndex,
                originalUserVector,
            }),
            'Stop rerolled with a new candidate.',
        );
    };

    const handleReorderStops = (routeIndex: number, newOrder: number[]) => {
        void performRouteMutation(
            routeIndex,
            (route, originalUserVector) => routeService.reorderPoints({
                currentRoute: route,
                newOrder,
                originalUserVector,
            }),
            'Route order updated.',
        );
    };

    const handleInsertPlaceSelect = (destination: MapDestination | null) => {
        if (!destination || !activeInsertTarget) {
            return;
        }

        const { routeIndex, insertIndex } = activeInsertTarget;
        setInsertSelectedPlace(destination);
        closeInsertPicker();
        void performRouteMutation(
            routeIndex,
            (route, originalUserVector) => routeService.insertPoint({
                currentRoute: route,
                index: insertIndex,
                poiId: destination.id,
                originalUserVector,
            }),
            `Inserted ${destination.name} into the route.`,
        );
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

                <Typography level="h4" sx={{ fontWeight: 700, flex: 1 }}>
                    Route Generation
                </Typography>
            </Box>

            {/* Scrollable Content */}
            <Box sx={{ flex: 1, overflow: 'auto', p: { xs: 2, md: 3 } }}>
                {/* Chat approval review mode banner */}
                {pendingChatApproval && routes.length > 0 && (
                    <Card
                        variant="soft"
                        color="primary"
                        sx={{
                            mb: 3,
                            p: 3,
                            background: 'linear-gradient(135deg, var(--joy-palette-primary-softBg), var(--joy-palette-primary-100))',
                        }}
                    >
                        <Typography level="title-lg" sx={{ fontWeight: 700, mb: 0.5 }}>
                            📍 Review Your Routes
                        </Typography>
                        <Typography level="body-sm" sx={{ color: 'text.secondary', mb: 1.5 }}>
                            Your AI assistant generated {routes.length} route alternative{routes.length !== 1 ? 's' : ''}.
                            Review, edit, and approve your preferred route to continue the conversation.
                        </Typography>
                        <Button
                            variant="plain"
                            color="neutral"
                            size="sm"
                            onClick={() => {
                                dispatch(clearChatApproval());
                                dispatch(clearRoutes());
                                const targetChat = returnChatId ? `/chat/${returnChatId}` : '/chat';
                                navigate(targetChat);
                            }}
                        >
                            Cancel and return to chat
                        </Button>
                    </Card>
                )}

                {routeProfileMode === null && !pendingChatApproval && (
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

                {routeProfileMode === 'builder' && !pendingChatApproval && (
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

                {routeProfileMode === 'saved-picker' && !pendingChatApproval && (
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

                {routeProfileMode !== null && routeProfileMode !== 'saved-picker' && !pendingChatApproval && (
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

                {savedRouteId && !pendingChatApproval && (
                    <Card variant="soft" color="primary" sx={{ mb: 3, p: 2.5 }}>
                        <Typography level="title-sm" sx={{ fontWeight: 700 }}>
                            Saved route mode
                        </Typography>
                        <Typography level="body-sm" sx={{ color: 'text.secondary', mt: 0.5 }}>
                            {savedRouteTitle || 'Saved route'} is loaded from the database as an exact snapshot.
                            Use Update Saved Route when you want to overwrite that record explicitly.
                        </Typography>
                        <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap', mt: 2 }}>
                            <Button
                                onClick={handleUpdateSavedRoute}
                                loading={isUpdatingSavedRoute || isSavedRouteSaving}
                                disabled={!currentRequest || routes.length === 0}
                            >
                                Update Saved Route
                            </Button>
                            <Button
                                variant="plain"
                                color="neutral"
                                onClick={() => navigate('/saved-routes')}
                            >
                                Back to Saved Routes
                            </Button>
                        </Box>
                    </Card>
                )}

                {/* Generate Controls */}
                {canGenerateRoutes && !pendingChatApproval && (
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

                        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mb: 2 }}>
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

                        <Box sx={{ display: 'grid', gap: 2, mb: 2 }}>
                            <Card variant="soft" color="neutral" sx={{ p: 2 }}>
                                <Typography level="title-sm" sx={{ fontWeight: 700, mb: 1.5 }}>
                                    Start Anchor
                                </Typography>
                                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'flex-end' }}>
                                    <FormControl size="sm" sx={{ minWidth: 180 }}>
                                        <FormLabel>Kind</FormLabel>
                                        <Select
                                            value={startPointType}
                                            onChange={(_e, value) => {
                                                const nextValue = (value as RouteBoundarySelectionType) || 'NONE';
                                                setStartPointType(nextValue);
                                                if (nextValue !== 'PLACE') {
                                                    setStartSelectedPlace(null);
                                                }
                                                if (nextValue !== 'TYPE') {
                                                    setStartPoiType('');
                                                    setStartMinRating('');
                                                    setStartMinRatingCount('');
                                                }
                                            }}
                                        >
                                            <Option value="HOTEL">Hotel</Option>
                                            <Option value="PLACE">Specific place</Option>
                                            <Option value="TYPE">Recommended type</Option>
                                            <Option value="NONE">No fixed start</Option>
                                        </Select>
                                    </FormControl>

                                    {startPointType === 'PLACE' && (
                                        <PlaceSearchAutocomplete
                                            label="Search Place"
                                            value={startSelectedPlace}
                                            options={startPlaceOptions}
                                            loading={startPlaceLoading}
                                            onInputChange={(query) => handlePlaceInputChange(query, 'start')}
                                            onChange={(value) => setStartSelectedPlace(value)}
                                        />
                                    )}

                                    {startPointType === 'TYPE' && (
                                        <>
                                            <FormControl size="sm" sx={{ flex: 1, minWidth: 180 }}>
                                                <FormLabel>POI Type</FormLabel>
                                                <Select
                                                    value={startPoiType || null}
                                                    placeholder="Select type..."
                                                    onChange={(_e, value) => setStartPoiType(value || '')}
                                                >
                                                    {POI_TYPE_OPTIONS.map((opt) => (
                                                        <Option key={opt.value} value={opt.value}>
                                                            {opt.label}
                                                        </Option>
                                                    ))}
                                                </Select>
                                            </FormControl>
                                            <FormControl size="sm" sx={{ minWidth: 120 }}>
                                                <FormLabel>Min Rating</FormLabel>
                                                <Input
                                                    type="number"
                                                    slotProps={{ input: { step: 0.1, min: 0, max: 5 } }}
                                                    placeholder="4.5"
                                                    value={startMinRating}
                                                    onChange={(e) => setStartMinRating(e.target.value)}
                                                />
                                            </FormControl>
                                            <FormControl size="sm" sx={{ minWidth: 150 }}>
                                                <FormLabel>Min Review Count</FormLabel>
                                                <Input
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
                            </Card>

                            <Card variant="soft" color="neutral" sx={{ p: 2 }}>
                                <Typography level="title-sm" sx={{ fontWeight: 700, mb: 1.5 }}>
                                    End Anchor
                                </Typography>
                                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'flex-end' }}>
                                    <FormControl size="sm" sx={{ minWidth: 180 }}>
                                        <FormLabel>Kind</FormLabel>
                                        <Select
                                            value={endPointType}
                                            onChange={(_e, value) => {
                                                const nextValue = (value as RouteBoundarySelectionType) || 'NONE';
                                                setEndPointType(nextValue);
                                                if (nextValue !== 'PLACE') {
                                                    setEndSelectedPlace(null);
                                                }
                                                if (nextValue !== 'TYPE') {
                                                    setEndPoiType('');
                                                    setEndMinRating('');
                                                    setEndMinRatingCount('');
                                                }
                                            }}
                                        >
                                            <Option value="HOTEL">Hotel</Option>
                                            <Option value="PLACE">Specific place</Option>
                                            <Option value="TYPE">Recommended type</Option>
                                            <Option value="NONE">No fixed end</Option>
                                        </Select>
                                    </FormControl>

                                    {endPointType === 'PLACE' && (
                                        <PlaceSearchAutocomplete
                                            label="Search Place"
                                            value={endSelectedPlace}
                                            options={endPlaceOptions}
                                            loading={endPlaceLoading}
                                            onInputChange={(query) => handlePlaceInputChange(query, 'end')}
                                            onChange={(value) => setEndSelectedPlace(value)}
                                        />
                                    )}

                                    {endPointType === 'TYPE' && (
                                        <>
                                            <FormControl size="sm" sx={{ flex: 1, minWidth: 180 }}>
                                                <FormLabel>POI Type</FormLabel>
                                                <Select
                                                    value={endPoiType || null}
                                                    placeholder="Select type..."
                                                    onChange={(_e, value) => setEndPoiType(value || '')}
                                                >
                                                    {POI_TYPE_OPTIONS.map((opt) => (
                                                        <Option key={opt.value} value={opt.value}>
                                                            {opt.label}
                                                        </Option>
                                                    ))}
                                                </Select>
                                            </FormControl>
                                            <FormControl size="sm" sx={{ minWidth: 120 }}>
                                                <FormLabel>Min Rating</FormLabel>
                                                <Input
                                                    type="number"
                                                    slotProps={{ input: { step: 0.1, min: 0, max: 5 } }}
                                                    placeholder="4.5"
                                                    value={endMinRating}
                                                    onChange={(e) => setEndMinRating(e.target.value)}
                                                />
                                            </FormControl>
                                            <FormControl size="sm" sx={{ minWidth: 150 }}>
                                                <FormLabel>Min Review Count</FormLabel>
                                                <Input
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
                            </Card>
                        </Box>

                        {getGenerationValidationError() && (
                            <Alert color="warning" sx={{ mb: 2 }}>
                                {getGenerationValidationError()}
                            </Alert>
                        )}

                        <Button
                            id="generate-routes-btn"
                            variant="solid"
                            color="primary"
                            size="lg"
                            startDecorator={isLoading ? <CircularProgress size="sm" /> : <RouteIcon />}
                            onClick={handleGenerate}
                            disabled={isLoading || Boolean(getGenerationValidationError())}
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
                                <EditableRouteCard
                                    route={route}
                                    index={idx}
                                    onApprove={handleApprove}
                                    isApproving={approvingRouteId === route.routeId}
                                    isApproved={approvedRouteIds.has(route.routeId)}
                                    isMutating={mutatingRouteId === route.routeId}
                                    onInsertBetween={(insertIndex) => handleInsertStop(idx, insertIndex)}
                                    onRemovePoint={(pointIndex) => handleRemoveStop(idx, pointIndex)}
                                    onRerollPoint={(pointIndex) => handleRerollStop(idx, pointIndex)}
                                    onReorderPoints={(newOrder) => handleReorderStops(idx, newOrder)}
                                    activeInsertIndex={activeInsertTarget?.routeIndex === idx ? activeInsertTarget.insertIndex : null}
                                    insertPlaceOptions={activeInsertTarget?.routeIndex === idx ? insertPlaceOptions : []}
                                    insertPlaceValue={activeInsertTarget?.routeIndex === idx ? insertSelectedPlace : null}
                                    insertPlaceLoading={activeInsertTarget?.routeIndex === idx ? insertPlaceLoading : false}
                                    onInsertPlaceInputChange={(query) => handlePlaceInputChange(query, 'insert')}
                                    onInsertPlaceSelect={handleInsertPlaceSelect}
                                    onInsertCancel={closeInsertPicker}
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
