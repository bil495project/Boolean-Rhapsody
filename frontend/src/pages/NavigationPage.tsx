import { useState, useEffect, useCallback } from 'react';
import { Box, Drawer } from '@mui/joy';
import { useMediaQuery } from '@mui/system';
import ChatSidebar from '../components/chat/ChatSidebar';
import NavigationPanel from '../components/chat/NavigationPanel';
import MapPanel from '../components/chat/MapPanel';
import ResizableDivider from '../components/chat/ResizableDivider';
import { useAppSelector, useAppDispatch } from '../store/hooks';
import { toggleSidebar, setSidebarOpen } from '../store/chatSlice';
import type { MapDestination } from '../data/destinations';

const NavigationPage = () => {
    const dispatch = useAppDispatch();
    const { sidebarOpen, chatPanelWidth, mapFullscreen } = useAppSelector((state) => state.chat);

    // Responsive breakpoints
    const isMobile = useMediaQuery('(max-width: 768px)');
    const [mobileDrawerOpen, setMobileDrawerOpen] = useState(false);

    // Map State
    const [selectedStops, setSelectedStops] = useState<MapDestination[]>([]);
    const [route, setRoute] = useState<[number, number][] | null>(null);

    // Close sidebar on mobile by default
    useEffect(() => {
        if (isMobile) {
            dispatch(setSidebarOpen(false));
        }
    }, [isMobile, dispatch]);

    const handleMobileDrawerClose = () => {
        setMobileDrawerOpen(false);
    };

    const handleMobileMenuClick = () => {
        setMobileDrawerOpen(true);
    };

    const handleToggleSidebar = () => {
        dispatch(toggleSidebar());
    };

    const handleStopsUpdate = useCallback((stops: MapDestination[]) => {
        setSelectedStops(stops);
        // Automatically update route on map to show connection
        if (stops.length > 1) {
            setRoute(stops.map(s => s.coordinates));
        } else {
            setRoute(null);
        }
    }, []);

    const handleRouteCalculate = useCallback((stops: MapDestination[], _mode: 'driving' | 'walking', routeCoords?: [number, number][]) => {
        // Use road-following coordinates if provided, otherwise fallback to direct waypoints
        if (routeCoords && routeCoords.length > 0) {
            setRoute(routeCoords);
        } else {
            const routePoints = stops.map(stop => stop.coordinates);
            setRoute(routePoints);
        }
    }, []);

    return (
        <Box
            sx={{
                height: '100vh',
                display: 'flex',
                overflow: 'hidden',
            }}
        >
            {/* Desktop Sidebar */}
            {!isMobile && sidebarOpen && (
                <ChatSidebar />
            )}

            {/* Mobile Sidebar Drawer */}
            {isMobile && (
                <Drawer
                    open={mobileDrawerOpen}
                    onClose={handleMobileDrawerClose}
                    size="sm"
                    sx={{
                        '--Drawer-horizontalSize': '280px',
                    }}
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
                        {/* Mobile: Show Navigation Panel when not fullscreen, Map when fullscreen */}
                        {!mapFullscreen && (
                            <Box sx={{ flex: 1, overflow: 'hidden' }}>
                                <NavigationPanel
                                    onRouteCalculate={handleRouteCalculate}
                                    onStopsUpdate={handleStopsUpdate}
                                    showMenuButton={true}
                                    onMenuClick={handleMobileMenuClick}
                                />
                            </Box>
                        )}

                        {mapFullscreen && (
                            <Box sx={{ flex: 1, overflow: 'hidden' }}>
                                <MapPanel
                                    destinations={selectedStops}
                                    route={route}
                                    orderedDestinations={selectedStops}
                                    disableClustering
                                    fitCoordinates={route ?? selectedStops.map((stop) => stop.coordinates)}
                                    markerKeyPrefix="navigation-route"
                                />
                            </Box>
                        )}
                    </>
                ) : (
                    /* Desktop: Side-by-side layout */
                    <Box sx={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
                        {/* Navigation Panel - hidden when map is fullscreen */}
                        {!mapFullscreen && (
                            <Box
                                sx={{
                                    width: `${chatPanelWidth}%`,
                                    height: '100%',
                                    flexShrink: 0,
                                    overflow: 'hidden',
                                }}
                            >
                                <NavigationPanel
                                    onRouteCalculate={handleRouteCalculate}
                                    onStopsUpdate={handleStopsUpdate}
                                    showMenuButton={!sidebarOpen}
                                    onMenuClick={handleToggleSidebar}
                                />
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
                                destinations={selectedStops}
                                route={route}
                                orderedDestinations={selectedStops}
                                disableClustering
                                fitCoordinates={route ?? selectedStops.map((stop) => stop.coordinates)}
                                markerKeyPrefix="navigation-route"
                            />
                        </Box>
                    </Box>
                )}
            </Box>
        </Box>
    );
};

export default NavigationPage;
