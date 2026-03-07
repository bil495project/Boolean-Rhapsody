import { useEffect, useMemo, useCallback } from 'react';
import {
    Box,
    Typography,
    Select,
    Option,
    Button,
    Card,
    IconButton,
    Stack,
    CircularProgress,
} from '@mui/joy';
import DirectionsCarIcon from '@mui/icons-material/DirectionsCar';
import DirectionsWalkIcon from '@mui/icons-material/DirectionsWalk';
import NavigationIcon from '@mui/icons-material/Navigation';
import PlaceIcon from '@mui/icons-material/Place';
import MenuIcon from '@mui/icons-material/Menu';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import { useAppSelector, useAppDispatch } from '../../store/hooks';
import { toggleSidebar } from '../../store/chatSlice';
import {
    addStop,
    removeStop,
    updateStop,
    setMode,
    setRouteInfo,
    setRouteCoordinates,
    setLoadingRoute,
} from '../../store/navigationSlice';
import { getRoute } from '../../services/routingService';
import type { MapDestination } from '../../data/destinations';

interface NavigationPanelProps {
    onRouteCalculate: (stops: MapDestination[], mode: 'driving' | 'walking', routeCoords?: [number, number][]) => void;
    onMenuClick?: () => void;
    showMenuButton?: boolean;
    onStopsUpdate?: (stops: MapDestination[]) => void;
}

const NavigationPanel = ({ onRouteCalculate, onMenuClick, showMenuButton = false, onStopsUpdate }: NavigationPanelProps) => {
    const dispatch = useAppDispatch();
    const { destinations: savedDestinations } = useAppSelector((state) => state.saved);

    // Use Redux state for stops and mode
    const { stops, mode, routeInfo, isLoadingRoute } = useAppSelector((state) => state.navigation);

    // Filter out destinations that don't have coordinates
    const availablePoints = useMemo(() => savedDestinations.filter(d => d.coordinates), [savedDestinations]);

    // Notify parent of stops changes
    useEffect(() => {
        if (onStopsUpdate) {
            const validStops = stops
                .map(id => availablePoints.find(p => p.id === id))
                .filter((d): d is MapDestination => !!d);
            onStopsUpdate(validStops);
        }
    }, [stops, availablePoints, onStopsUpdate]);

    const handleAddStop = useCallback(() => {
        dispatch(addStop());
    }, [dispatch]);

    const handleRemoveStop = useCallback((index: number) => {
        dispatch(removeStop(index));
    }, [dispatch]);

    const handleStopChange = useCallback((index: number, value: string | null) => {
        dispatch(updateStop({ index, value }));
    }, [dispatch]);

    const handleModeChange = useCallback((newMode: 'driving' | 'walking') => {
        dispatch(setMode(newMode));
    }, [dispatch]);

    const calculateRoute = useCallback(async () => {
        // Filter out nulls and get full destination objects
        const activeStops = stops
            .map(id => availablePoints.find(p => p.id === id))
            .filter((d): d is MapDestination => !!d);

        if (activeStops.length < 2) return;

        dispatch(setLoadingRoute(true));

        try {
            // Get road-following route from OSRM
            const waypoints = activeStops.map(stop => stop.coordinates);
            const result = await getRoute(waypoints, mode);

            dispatch(setRouteInfo({
                distance: result.distance,
                duration: result.duration
            }));
            dispatch(setRouteCoordinates(result.coordinates));

            // Pass road-following coordinates to parent
            onRouteCalculate(activeStops, mode, result.coordinates);
        } catch (error) {
            console.error('Route calculation error:', error);
            // Fallback: just use waypoints as coordinates
            const waypoints = activeStops.map(stop => stop.coordinates);
            onRouteCalculate(activeStops, mode, waypoints);
        } finally {
            dispatch(setLoadingRoute(false));
        }
    }, [stops, availablePoints, mode, dispatch, onRouteCalculate]);

    const isCalculateDisabled = stops.filter(s => s !== null).length < 2 || stops.some(s => s === null);

    return (
        <Box
            sx={{
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
                bgcolor: 'background.body',
            }}
        >
            {/* Header */}
            <Box sx={{ p: 2, borderBottom: '1px solid', borderColor: 'divider' }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 1 }}>
                    {showMenuButton && (
                        <IconButton variant="plain" size="sm" onClick={onMenuClick || (() => dispatch(toggleSidebar()))}>
                            <MenuIcon />
                        </IconButton>
                    )}
                    <Typography level="h4" sx={{ fontWeight: 600 }}>
                        Navigation
                    </Typography>
                </Box>
                <Typography level="body-sm" sx={{ color: 'text.secondary' }}>
                    Add multiple stops to plan your trip
                </Typography>
            </Box>

            {/* Form */}
            <Box sx={{ p: 3, display: 'flex', flexDirection: 'column', gap: 2, overflow: 'auto', flex: 1 }}>

                {/* Mode Selection */}
                <Box sx={{ display: 'flex', gap: 1, mb: 1 }}>
                    <Button
                        variant={mode === 'driving' ? 'solid' : 'outlined'}
                        color="primary"
                        startDecorator={<DirectionsCarIcon />}
                        onClick={() => handleModeChange('driving')}
                        fullWidth
                    >
                        Driving
                    </Button>
                    <Button
                        variant={mode === 'walking' ? 'solid' : 'outlined'}
                        color="primary"
                        startDecorator={<DirectionsWalkIcon />}
                        onClick={() => handleModeChange('walking')}
                        fullWidth
                    >
                        Walking
                    </Button>
                </Box>

                <Stack spacing={2}>
                    {stops.map((stopId, index) => (
                        <Box key={index} sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                            <Box sx={{ color: 'text.tertiary', display: 'flex', flexDirection: 'column', alignItems: 'center', minWidth: 24 }}>
                                {index === 0 ? (
                                    <PlaceIcon sx={{ color: 'success.500', fontSize: 20 }} />
                                ) : index === stops.length - 1 ? (
                                    <PlaceIcon sx={{ color: 'danger.500', fontSize: 20 }} />
                                ) : (
                                    <Box sx={{ width: 12, height: 12, borderRadius: '50%', border: '2px solid', borderColor: 'text.secondary' }} />
                                )}
                                {index < stops.length - 1 && (
                                    <Box sx={{ width: 2, height: 32, bgcolor: 'divider', my: -1, zIndex: -1, position: 'relative', top: 10 }} />
                                )}
                            </Box>

                            <Select
                                placeholder={index === 0 ? "Start Point" : "Choose destination"}
                                value={stopId}
                                onChange={(_, v) => handleStopChange(index, v)}
                                sx={{ flex: 1 }}
                            >
                                {availablePoints.map(p => (
                                    <Option
                                        key={p.id}
                                        value={p.id}
                                        disabled={stops.includes(p.id) && stopId !== p.id} // Disable already selected except current
                                    >
                                        {p.name}
                                    </Option>
                                ))}
                            </Select>

                            {stops.length > 2 && (
                                <IconButton
                                    size="sm"
                                    variant="plain"
                                    color="danger"
                                    onClick={() => handleRemoveStop(index)}
                                >
                                    <DeleteIcon />
                                </IconButton>
                            )}
                        </Box>
                    ))}
                </Stack>

                <Button
                    variant="outlined"
                    startDecorator={<AddIcon />}
                    onClick={handleAddStop}
                    disabled={stops.length >= 10}
                    sx={{ alignSelf: 'flex-start', ml: 4 }}
                >
                    Add Stop
                </Button>

                <Button
                    size="lg"
                    startDecorator={isLoadingRoute ? <CircularProgress size="sm" /> : <NavigationIcon />}
                    disabled={isCalculateDisabled || isLoadingRoute}
                    onClick={calculateRoute}
                    sx={{ mt: 2 }}
                >
                    {isLoadingRoute ? 'Calculating...' : 'Calculate Route'}
                </Button>

                {/* Results */}
                {routeInfo && (
                    <Card variant="soft" color="primary" sx={{ mt: 2 }}>
                        <Typography level="title-lg" sx={{ mb: 1 }}>
                            Total Trip Details
                        </Typography>
                        <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1 }}>
                            <Typography level="h2" sx={{ color: 'primary.700' }}>
                                {routeInfo.duration}
                            </Typography>
                            <Typography level="title-md" sx={{ color: 'primary.600' }}>
                                min
                            </Typography>
                        </Box>
                        <Typography level="body-sm" sx={{ mt: 1 }}>
                            Total Distance: {routeInfo.distance} km • {activeStopsCount(stops)} stops
                        </Typography>
                    </Card>
                )}

                {savedDestinations.length < 2 && (
                    <Box sx={{ p: 2, textAlign: 'center', bgcolor: 'warning.softBg', borderRadius: 'sm' }}>
                        <Typography level="body-sm" color="warning">
                            You need at least 2 saved places to use navigation.
                        </Typography>
                    </Box>
                )}
            </Box>
        </Box>
    );
};

// Helper
const activeStopsCount = (stops: (string | null)[]) => stops.filter(Boolean).length;

export default NavigationPanel;
