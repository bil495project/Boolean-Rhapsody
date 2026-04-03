import { useEffect, useState, useRef, useMemo } from 'react';
import { useParams, useSearchParams, Navigate, useNavigate } from 'react-router-dom';
import { Box, Drawer } from '@mui/joy';
import { useMediaQuery } from '@mui/system';
import ChatSidebar from '../components/chat/ChatSidebar';
import ChatPanel from '../components/chat/ChatPanel';
import MapPanel from '../components/chat/MapPanel';
import ResizableDivider from '../components/chat/ResizableDivider';
import DestinationDetailPanel from '../components/chat/DestinationDetailPanel';
import { useAppSelector, useAppDispatch } from '../store/hooks';
import {
    createChatAsync,
    addMessageAsync,
    setActiveChat,
    addMessageLocal,
    setSidebarOpen,
    setLoading,
    toggleSidebar,
} from '../store/chatSlice';
import { sendMessage, generateTripTitle, explainRoute, type ToolCallResult } from '../services/llmService';
import type { MapDestination } from '../data/destinations';
import { fetchAllPlaces } from '../store/placesSlice';
import { clearChatApproval, clearPendingRouteExplain } from '../store/routeSlice';
import { mapTypesArrayToCategory } from '../utils/placeCategory';
import { getPlaceImage } from '../utils/placeImage';

const ChatPage = () => {
    const { chatId } = useParams<{ chatId: string }>();
    const [searchParams, setSearchParams] = useSearchParams();
    const navigate = useNavigate();
    const initialQuery = searchParams.get('q');
    const dispatch = useAppDispatch();

    const { user, isAuthenticated } = useAppSelector((state) => state.auth);
    const { chats, activeChat, sidebarOpen, mapFullscreen, chatPanelWidth } = useAppSelector(
        (state) => state.chat
    );
    const { destinations } = useAppSelector((state) => state.places);
    const { pendingChatApproval, approvedRoute, returnChatId, selectedChatRoute, pendingRouteExplain } = useAppSelector(
        (state) => state.route
    );

    // ─── Derive map data from the selected route (if any) ─────────────────
    // These are memoized so the MapPanel's React.memo can skip re-renders when
    // nothing about the route actually changed.
    const routeLineCoords = useMemo<[number, number][] | null>(() => {
        if (!selectedChatRoute) return null;
        return selectedChatRoute.points.map(
            (p) => [p.latitude, p.longitude] as [number, number]
        );
    }, [selectedChatRoute]);

    const routeDestinations = useMemo<MapDestination[]>(() => {
        if (!selectedChatRoute) return [];
        return selectedChatRoute.points
            .filter((p) => p.poiId)
            .map((p) => {
                const category = mapTypesArrayToCategory(p.types);
                return {
                    id: p.poiId!,
                    name: p.poiName || 'Unknown Place',
                    location: p.formattedAddress || '',
                    image: getPlaceImage({ id: p.poiId!, name: p.poiName || 'Unknown Place', category }),
                    rating: p.ratingScore || 4.0,
                    priceLevel: (() => {
                        switch (p.priceLevel) {
                            case 'PRICE_LEVEL_INEXPENSIVE': return 1;
                            case 'PRICE_LEVEL_MODERATE':    return 2;
                            case 'PRICE_LEVEL_EXPENSIVE':   return 3;
                            case 'PRICE_LEVEL_VERY_EXPENSIVE': return 4;
                            default: return 1;
                        }
                    })() as 1 | 2 | 3 | 4,
                    category,
                    coordinates: [p.latitude, p.longitude] as [number, number],
                    reviewCount: p.ratingCount,
                };
            });
    }, [selectedChatRoute]);

    // Fit the map viewport to the route bounds whenever the selected route changes.
    const routeFitCoords = routeLineCoords ?? undefined;

    // Fetch destinations from backend if not already loaded
    useEffect(() => {
        if (destinations.length === 0) {
            dispatch(fetchAllPlaces());
        }
    }, [dispatch, destinations.length]);

    // Check if we're in "new chat" mode (no chat created yet)
    const isNewChatMode = chatId === 'new' || !chatId;

    // Responsive breakpoints
    const isMobile = useMediaQuery('(max-width: 768px)');

    const [mobileDrawerOpen, setMobileDrawerOpen] = useState(false);
    const processedQueryRef = useRef<string | null>(null);
    const [highlightedDestination] = useState<MapDestination | null>(null);
    const [selectedDestination, setSelectedDestination] = useState<MapDestination | null>(null);

    // Redirect to login if not authenticated
    if (!isAuthenticated) {
        return <Navigate to="/login" replace />;
    }

    // Handle initial query from landing page - creates chat and sends message
    useEffect(() => {
        const processInitialQuery = async () => {
            if (initialQuery && chatId && chatId !== 'new' && processedQueryRef.current !== `${chatId}-${initialQuery}`) {
                processedQueryRef.current = `${chatId}-${initialQuery}`;

                const existingChat = chats.find((c) => c.id === chatId);
                if (existingChat) {
                    dispatch(setActiveChat(chatId));
                    return;
                }

                // Create chat via backend
                const title = await generateTripTitle(initialQuery);
                const result = await dispatch(createChatAsync({ title })).unwrap();
                const newId = result.id;
                navigate(`/chat/${newId}`, { replace: true });
                setSearchParams({}, { replace: true });

                // Add user message
                await dispatch(addMessageAsync({ chatId: newId, role: 'user', content: initialQuery }));

                // Get AI response
                dispatch(setLoading(true));
                try {
                    const response: ToolCallResult = await sendMessage(initialQuery, [], user?.id);
                    await dispatch(addMessageAsync({
                        chatId: newId,
                        role: 'assistant',
                        content: response.message,
                    }));
                } catch {
                    await dispatch(addMessageAsync({
                        chatId: newId,
                        role: 'assistant',
                        content: 'Welcome! I\'m excited to help you plan your trip. What would you like to explore?',
                    }));
                } finally {
                    dispatch(setLoading(false));
                }
            }
        };

        processInitialQuery();
    }, [initialQuery, chatId, chats, dispatch, setSearchParams, navigate]);

    // Set active chat based on URL (only for existing chats, not "new")
    useEffect(() => {
        if (chatId && chatId !== 'new' && !initialQuery && chatId !== activeChat?.id) {
            const existingChat = chats.find((c) => c.id === chatId);
            if (existingChat) {
                dispatch(setActiveChat(chatId));
            } else {
                // Chat doesn't exist, redirect to new
                navigate('/chat/new', { replace: true });
            }
        }
    }, [chatId, chats, activeChat, dispatch, initialQuery, navigate]);

    // Close sidebar on mobile by default
    useEffect(() => {
        if (isMobile) {
            dispatch(setSidebarOpen(false));
        }
    }, [isMobile, dispatch]);

    // ─── Handle approved route from Route Page ─────────────────────────
    // When the user returns from the Route Page after approving a route,
    // send the selected route to the LLM for a final conversational response.
    const approvalProcessedRef = useRef(false);
    useEffect(() => {
        if (
            !pendingChatApproval ||
            !approvedRoute ||
            !activeChat ||
            approvalProcessedRef.current
        ) {
            return;
        }

        // Guard: only process if we're on the correct chat
        if (returnChatId && activeChat.id !== returnChatId) {
            return;
        }

        approvalProcessedRef.current = true;

        const processApprovedRoute = async () => {
            dispatch(setLoading(true));
            try {
                // Build a concise summary of the approved route for the LLM
                const routeSummary = JSON.stringify(approvedRoute);
                const approvalQuery =
                    `[SYSTEM] The user has reviewed the generated route alternatives and approved the following route. ` +
                    `Please provide a brief, friendly summary of this approved route — mention the key stops, ` +
                    `estimated duration, and travel mode. Do NOT mention other route alternatives. ` +
                    `Approved route data: ${routeSummary}`;

                // Build history from existing chat messages
                const history = activeChat.messages.map((msg) => ({
                    role: msg.role === 'user' ? 'user' as const : 'assistant' as const,
                    content: msg.content,
                }));

                const response: ToolCallResult = await sendMessage(
                    approvalQuery,
                    history,
                    user?.id,
                );

                await dispatch(addMessageAsync({
                    chatId: activeChat.id,
                    role: 'assistant',
                    content: response.message || 'Your route has been approved! You can find it in your saved routes.',
                }));
            } catch (error) {
                console.error('Error processing approved route:', error);
                await dispatch(addMessageAsync({
                    chatId: activeChat.id,
                    role: 'assistant',
                    content: 'Your route has been approved! \u2705',
                }));
            } finally {
                dispatch(setLoading(false));
                dispatch(clearChatApproval());
                approvalProcessedRef.current = false;
            }
        };

        processApprovedRoute();
    }, [pendingChatApproval, approvedRoute, activeChat, returnChatId, dispatch, user?.id]);

    // ─── Handle "Ask LLM about Route" navigation from RoutePage ─────────────────
    // When the user clicks the button on a route card, RoutePage stores the
    // selected route in pendingRouteExplain and navigates here (/chat/new).
    // This effect picks it up, auto-creates a chat, and auto-sends the
    // explain_generated_route query so the LLM explains every stop.
    const explainProcessedRef = useRef(false);
    useEffect(() => {
        if (!pendingRouteExplain || !isNewChatMode || explainProcessedRef.current) return;

        explainProcessedRef.current = true;

        const fireRouteExplanation = async () => {
            dispatch(setLoading(true));
            try {
                // Build the stop name list from the route's points
                const stopNames = pendingRouteExplain.points
                    .filter((p) => p.poiName)
                    .map((p) => p.poiName!);

                if (stopNames.length === 0) {
                    dispatch(clearPendingRouteExplain());
                    explainProcessedRef.current = false;
                    return;
                }

                const durationMin = Math.round(pendingRouteExplain.totalDurationSec / 60);
                const distanceKm  = (pendingRouteExplain.totalDistanceM / 1000).toFixed(1);
                const mode        = pendingRouteExplain.travelMode || 'walking';

                // Human-readable stop list for the LLM query
                const stopList = stopNames.map((n, i) => `${i + 1}. ${n}`).join(', ');

                // This prompt drives the LLM to call explain_generated_route
                const query =
                    `I have a generated route with ${stopNames.length} stops: ${stopList}. ` +
                    `The total estimated duration is ~${durationMin} minutes, ` +
                    `distance is ~${distanceKm} km, and the travel mode is ${mode}. ` +
                    `Please use the explain_generated_route tool to fetch and explain every stop in detail, ` +
                    `then give me a friendly summary of the whole route.`;

                // Create a new chat for this conversation
                const title = `Route Explanation: ${stopNames.slice(0, 2).join(' → ')}${stopNames.length > 2 ? '…' : ''}`;
                const result = await dispatch(createChatAsync({ title })).unwrap();
                const newChatId = result.id;

                navigate(`/chat/${newChatId}`, { replace: true });

                // Persist the user-side question
                await dispatch(addMessageAsync({ chatId: newChatId, role: 'user', content: query }));

                // Get the LLM response via the dedicated explain-route endpoint
                // (avoids the LLM tool-call JSON parser which breaks on Turkish chars)
                const response: ToolCallResult = await explainRoute(
                    stopNames,
                    {
                        total_duration_min: durationMin,
                        total_distance_km:  parseFloat(distanceKm),
                        travel_mode:        mode,
                    }
                );

                await dispatch(addMessageAsync({
                    chatId: newChatId,
                    role: 'assistant',
                    content: response.message ||
                        'Here is the explanation for your route stops.',
                    toolUsed:   response.toolUsed,
                    toolParams: response.toolParams,
                }));
            } catch (error) {
                console.error('Error firing route explanation:', error);
            } finally {
                dispatch(setLoading(false));
                dispatch(clearPendingRouteExplain());
                explainProcessedRef.current = false;
            }
        };

        fireRouteExplanation();
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [pendingRouteExplain, isNewChatMode]);

    const handleMobileDrawerClose = () => {
        setMobileDrawerOpen(false);
    };

    const handleMobileMenuClick = () => {
        setMobileDrawerOpen(true);
    };

    const handleToggleSidebar = () => {
        dispatch(toggleSidebar());
    };

    const handleDestinationSelect = (destination: MapDestination) => {
        // Open the detail panel
        setSelectedDestination(destination);
    };

    const handleCloseDetailPanel = () => {
        setSelectedDestination(null);
    };

    const handleAskAboutDestination = async (destination: MapDestination) => {
        const query = `Tell me more about ${destination.name} in Ankara. What can I do there and what should I know before visiting?`;

        if (activeChat) {
            // Add user message optimistically
            dispatch(addMessageLocal({
                chatId: activeChat.id,
                message: {
                    id: `temp-${Date.now()}`,
                    role: 'user',
                    content: query,
                    timestamp: Date.now(),
                },
            }));

            // Persist user message
            await dispatch(addMessageAsync({ chatId: activeChat.id, role: 'user', content: query }));

            dispatch(setLoading(true));
            try {
                const response: ToolCallResult = await sendMessage(query, [], user?.id);
                await dispatch(addMessageAsync({
                    chatId: activeChat.id,
                    role: 'assistant',
                    content: response.message,
                }));
            } catch {
                // ignore
            } finally {
                dispatch(setLoading(false));
            }
        } else if (isNewChatMode) {
             // Let ChatPanel handle it by putting it in input state? 
             // Or we just ignore it for 'new' mode until they send first message.
             // For now, in new mode, we just ignore handleAskAboutDestination or would need to create chat.
        }
        
        // Close detail panel
        setSelectedDestination(null);
    };

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
                        {!mapFullscreen && (
                            <Box sx={{ flex: 1, overflow: 'hidden' }}>
                                <ChatPanel
                                    userName={user?.name || 'Traveler'}
                                    onMenuClick={handleMobileMenuClick}
                                    showMenuButton={true}
                                    isNewChatMode={isNewChatMode}
                                />
                            </Box>
                        )}

                        {mapFullscreen && (
                            <Box sx={{ flex: 1, overflow: 'hidden' }}>
                                <MapPanel
                                    destinations={routeDestinations.length > 0 ? routeDestinations : destinations}
                                    highlightedDestination={highlightedDestination}
                                    onDestinationSelect={handleDestinationSelect}
                                    route={routeLineCoords}
                                    orderedDestinations={routeDestinations}
                                    disableClustering={routeDestinations.length > 0}
                                    fitCoordinates={routeFitCoords}
                                    markerKeyPrefix={selectedChatRoute?.routeId ?? 'chat'}
                                />
                            </Box>
                        )}
                    </>
                ) : (
                    /* Desktop: Side-by-side layout */
                    <Box sx={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
                        {/* Chat Panel */}
                        {!mapFullscreen && (
                            <Box
                                sx={{
                                    width: `${chatPanelWidth}%`,
                                    height: '100%',
                                    flexShrink: 0,
                                    overflow: 'hidden',
                                }}
                            >
                                <ChatPanel
                                    userName={user?.name || 'Traveler'}
                                    isNewChatMode={isNewChatMode}
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
                                destinations={routeDestinations.length > 0 ? routeDestinations : destinations}
                                highlightedDestination={highlightedDestination}
                                onDestinationSelect={handleDestinationSelect}
                                route={routeLineCoords}
                                orderedDestinations={routeDestinations}
                                disableClustering={routeDestinations.length > 0}
                                fitCoordinates={routeFitCoords}
                                markerKeyPrefix={selectedChatRoute?.routeId ?? 'chat'}
                            />
                        </Box>

                        {/* Destination Detail Panel */}
                        {selectedDestination && (
                            <DestinationDetailPanel
                                destination={selectedDestination}
                                onClose={handleCloseDetailPanel}
                                onAskAbout={handleAskAboutDestination}
                            />
                        )}
                    </Box>
                )}
            </Box>
        </Box>
    );
};

export default ChatPage;
