import { useState, useEffect, useCallback } from 'react';
import { Navigate } from 'react-router-dom';
import { Box, Drawer } from '@mui/joy';
import { useMediaQuery } from '@mui/system';
import ChatSidebar from '../components/chat/ChatSidebar';
import ExplorePanel from '../components/chat/ExplorePanel';
import MapPanel from '../components/chat/MapPanel';
import ResizableDivider from '../components/chat/ResizableDivider';
import { useAppSelector, useAppDispatch } from '../store/hooks';
import { toggleSidebar } from '../store/chatSlice';
import type { MapDestination } from '../data/destinations';
import { fetchAllPlaces } from '../store/placesSlice';

const ExplorePage = () => {
  const dispatch = useAppDispatch();

  const { isAuthenticated } = useAppSelector((state) => state.auth);
  const { sidebarOpen, mapFullscreen, chatPanelWidth } = useAppSelector((state) => state.chat);
  const { destinations } = useAppSelector((state) => state.places);

  const isMobile = useMediaQuery('(max-width: 768px)');
  const [mobileDrawerOpen, setMobileDrawerOpen] = useState(false);
  const [highlightedDestination, setHighlightedDestination] = useState<MapDestination | null>(null);
  const [selectedDestination, setSelectedDestination] = useState<MapDestination | null>(null);

  // Fetch destinations from backend on component mount
  useEffect(() => {
    if (destinations.length === 0) {
      dispatch(fetchAllPlaces());
    }
  }, [dispatch, destinations.length]);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  const handleMobileDrawerClose = () => {
    setMobileDrawerOpen(false);
  };

  const handleMobileMenuClick = () => {
    setMobileDrawerOpen(true);
  };

  const handleToggleSidebar = () => {
    dispatch(toggleSidebar());
  };

  // When user clicks a destination card, highlight its marker and open a popup on the map
  const handleDestinationSelect = useCallback((destination: MapDestination) => {
    setSelectedDestination(destination);
    setHighlightedDestination(destination);
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
            {!mapFullscreen && (
              <Box sx={{ flex: 1, overflow: 'hidden' }}>
                <ExplorePanel
                  destinations={destinations}
                  onDestinationSelect={handleDestinationSelect}
                  onDestinationHover={setHighlightedDestination}
                  onMenuClick={handleMobileMenuClick}
                  showMenuButton={true}
                />
              </Box>
            )}

            {mapFullscreen && (
              <Box sx={{ flex: 1, overflow: 'hidden' }}>
                <MapPanel
                  destinations={destinations}
                  highlightedDestination={highlightedDestination}
                  selectedDestination={selectedDestination}
                  onSelectedDestinationClear={() => setSelectedDestination(null)}
                />
              </Box>
            )}
          </>
        ) : (
          /* Desktop: Side-by-side layout */
          <Box sx={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
            {/* Explore Panel */}
            {!mapFullscreen && (
              <Box
                sx={{
                  width: `${chatPanelWidth}%`,
                  height: '100%',
                  flexShrink: 0,
                  overflow: 'hidden',
                }}
              >
                <ExplorePanel
                  destinations={destinations}
                  onDestinationSelect={handleDestinationSelect}
                  onDestinationHover={setHighlightedDestination}
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
                destinations={destinations}
                highlightedDestination={highlightedDestination}
                selectedDestination={selectedDestination}
                onSelectedDestinationClear={() => setSelectedDestination(null)}
              />
            </Box>
          </Box>
        )}
      </Box>
    </Box>
  );
};

export default ExplorePage;

