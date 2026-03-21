import { useEffect, useState, useRef } from 'react';
import { Box, IconButton, Typography, Card, CardCover, CardContent } from '@mui/joy';
import FullscreenIcon from '@mui/icons-material/Fullscreen';
import FullscreenExitIcon from '@mui/icons-material/FullscreenExit';
import StarIcon from '@mui/icons-material/Star';
import PlaceIcon from '@mui/icons-material/Place';
import LocalOfferIcon from '@mui/icons-material/LocalOffer';
import BookmarkBorderIcon from '@mui/icons-material/BookmarkBorder';
import BookmarkIcon from '@mui/icons-material/Bookmark';
import { MapContainer, TileLayer, Marker, useMap, useMapEvents, Polyline } from 'react-leaflet';
import MarkerClusterGroup from 'react-leaflet-cluster';
import L from 'leaflet';
import 'leaflet.markercluster/dist/MarkerCluster.css';
import 'leaflet.markercluster/dist/MarkerCluster.Default.css';
import 'leaflet.markercluster';
import 'leaflet/dist/leaflet.css';
import { useAppSelector, useAppDispatch } from '../../store/hooks';
import { toggleMapFullscreen } from '../../store/chatSlice';
import { toggleSaveDestination, syncToggleToBackend } from '../../store/savedSlice';
import { type MapDestination } from '../../data/destinations';

// Fix for default marker icons in React-Leaflet
delete (L.Icon.Default.prototype as unknown as { _getIconUrl?: () => string })._getIconUrl;
L.Icon.Default.mergeOptions({
    iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
    iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
    shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});

// Custom marker icon
const createCustomIcon = (color: string = '#00BFA6', number?: number) => {
    return L.divIcon({
        className: 'custom-marker',
        html: `
      <div style="
        width: 32px;
        height: 32px;
        background: ${color};
        border-radius: 50% 50% 50% 0;
        transform: rotate(-45deg);
        border: 3px solid white;
        box-shadow: 0 2px 8px rgba(0,0,0,0.3);
        display: flex;
        align-items: center;
        justify-content: center;
      ">
        <div style="
          width: ${number ? '20px' : '6px'};
          height: ${number ? '20px' : '6px'};
          background: ${number ? 'transparent' : 'white'};
          border-radius: 50%;
          transform: rotate(45deg);
          display: flex;
          align-items: center;
          justify-content: center;
          color: white;
          font-weight: bold;
          font-size: 14px;
          font-family: sans-serif;
        ">${number || ''}</div>
      </div>
    `,
        iconSize: [32, 32],
        iconAnchor: [16, 32],
        popupAnchor: [0, -32],
    });
};

// Component to handle map resize on fullscreen toggle
const MapResizeHandler = ({ fullscreen }: { fullscreen: boolean }) => {
    const map = useMap();

    useEffect(() => {
        setTimeout(() => {
            map.invalidateSize();
        }, 100);
    }, [fullscreen, map]);

    return null;
};

// Component to handle map click to close popup
const MapClickHandler = ({
    onMapClick,
    onMapClickCoords
}: {
    onMapClick: () => void;
    onMapClickCoords?: (latlng: { lat: number; lng: number }) => void;
}) => {
    useMapEvents({
        click: (e) => {
            onMapClick();
            if (onMapClickCoords) {
                onMapClickCoords({ lat: e.latlng.lat, lng: e.latlng.lng });
            }
        },
    });
    return null;
};

interface MapPanelProps {
    destinations?: MapDestination[];
    highlightedDestination?: MapDestination | null;
    onDestinationSelect?: (destination: MapDestination) => void;
    route?: [number, number][] | null;
    orderedDestinations?: MapDestination[];
    onMapClick?: (latlng: { lat: number; lng: number }) => void;
}

const MapPanel = ({
    destinations = [], // Default to empty array
    highlightedDestination,
    onDestinationSelect,
    route,
    orderedDestinations = [],
    onMapClick: onMapClickProp
}: MapPanelProps) => {
    const dispatch = useAppDispatch();
    const { mapFullscreen } = useAppSelector((state) => state.chat);

    // Hover-based popup
    const [hoveredDestination, setHoveredDestination] = useState<MapDestination | null>(null);
    const [popupPosition, setPopupPosition] = useState<{ x: number; y: number } | null>(null);
    const [isHoveringPopup, setIsHoveringPopup] = useState(false);
    const mapContainerRef = useRef<HTMLDivElement>(null);
    const hoverTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    // Default center (Ankara)
    const defaultCenter: [number, number] = [39.9334, 32.8597];

    const isDestinationSaved = (destinationId: string) => {
        return savedDestinations.some(d => d.id === destinationId);
    };

    const handleMarkerHover = (destination: MapDestination, event: L.LeafletMouseEvent) => {
        // Clear any pending close
        if (hoverTimeoutRef.current) {
            clearTimeout(hoverTimeoutRef.current);
            hoverTimeoutRef.current = null;
        }

        setHoveredDestination(destination);
        const containerPoint = event.containerPoint;

        // Calculate position, keeping popup within bounds
        const containerWidth = mapContainerRef.current?.clientWidth || window.innerWidth;
        const x = Math.min(containerPoint.x + 10, containerWidth - 280);
        const y = Math.max(containerPoint.y - 120, 10);

        setPopupPosition({ x, y });
    };

    const handleMarkerLeave = () => {
        // Delay closing to allow mouse to enter popup
        hoverTimeoutRef.current = setTimeout(() => {
            if (!isHoveringPopup) {
                setHoveredDestination(null);
                setPopupPosition(null);
            }
        }, 150);
    };

    const handlePopupMouseEnter = () => {
        setIsHoveringPopup(true);
        if (hoverTimeoutRef.current) {
            clearTimeout(hoverTimeoutRef.current);
            hoverTimeoutRef.current = null;
        }
    };

    const handlePopupMouseLeave = () => {
        setIsHoveringPopup(false);
        setHoveredDestination(null);
        setPopupPosition(null);
    };

    const handleMapClick = () => {
        setHoveredDestination(null);
        setPopupPosition(null);
    };
    const { destinations: savedDestinations } = useAppSelector(state => state.saved);

    const handleSaveClick = (e: React.MouseEvent) => {
        e.stopPropagation();
        if (hoveredDestination) {
            dispatch(toggleSaveDestination(hoveredDestination));
            dispatch(syncToggleToBackend(hoveredDestination));
        }
    };

    const handleDestinationClick = () => {
        if (hoveredDestination && onDestinationSelect) {
            onDestinationSelect(hoveredDestination);
            setHoveredDestination(null);
            setPopupPosition(null);
        }
    };

    // Cleanup timeout on unmount
    useEffect(() => {
        return () => {
            if (hoverTimeoutRef.current) {
                clearTimeout(hoverTimeoutRef.current);
            }
        };
    }, []);

    return (
        <Box
            ref={mapContainerRef}
            sx={{
                height: '100%',
                width: '100%',
                position: 'relative',
                bgcolor: 'background.level1',
            }}
        >
            <MapContainer
                center={defaultCenter}
                zoom={12}
                style={{ height: '100%', width: '100%' }}
            >
                <TileLayer
                    attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>'
                    url="https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png"
                />
                <MapResizeHandler fullscreen={mapFullscreen} />
                <MapClickHandler onMapClick={handleMapClick} onMapClickCoords={onMapClickProp} />

                {/* Route Polyline */}
                {route && route.length > 1 && (
                    <Polyline
                        positions={route}
                        pathOptions={{ color: '#000000', weight: 4, opacity: 0.8, dashArray: '10, 10' }}
                    />
                )}

                {/* Destination markers */}
                <MarkerClusterGroup chunkedLoading>
                    {destinations.map((destination) => {
                        const orderIndex = orderedDestinations.findIndex(d => d.id === destination.id);
                        const orderNumber = orderIndex !== -1 ? orderIndex + 1 : undefined;

                        return (
                            <Marker
                                key={destination.id}
                                position={destination.coordinates}
                                icon={createCustomIcon(
                                    highlightedDestination?.id === destination.id || hoveredDestination?.id === destination.id
                                        ? '#FF6B6B'
                                        : '#00BFA6',
                                    orderNumber
                                )}
                                eventHandlers={{
                                    mouseover: (e) => handleMarkerHover(destination, e),
                                    mouseout: handleMarkerLeave,
                                }}
                            />
                        );
                    })}
                </MarkerClusterGroup>
            </MapContainer>

            {/* Hover Popup Card */}
            {hoveredDestination && popupPosition && (
                <Card
                    variant="outlined"
                    onMouseEnter={handlePopupMouseEnter}
                    onMouseLeave={handlePopupMouseLeave}
                    sx={{
                        position: 'absolute',
                        left: popupPosition.x,
                        top: popupPosition.y,
                        zIndex: 1000,
                        width: 260,
                        p: 0,
                        bgcolor: 'background.surface',
                        borderRadius: 'lg',
                        boxShadow: 'lg',
                        overflow: 'hidden',
                        pointerEvents: 'auto',
                    }}
                >
                    {/* Image section - clickable to open chat */}
                    <Box
                        sx={{ position: 'relative', height: 90, cursor: 'pointer' }}
                        onClick={handleDestinationClick}
                    >
                        <CardCover>
                            <img
                                src={hoveredDestination.image}
                                alt={hoveredDestination.name}
                                style={{ objectFit: 'cover' }}
                            />
                        </CardCover>
                        <CardCover
                            sx={{
                                background: 'linear-gradient(to bottom, transparent 50%, rgba(0,0,0,0.4) 100%)',
                            }}
                        />
                        {/* Rating badge */}
                        <Box
                            sx={{
                                position: 'absolute',
                                top: 8,
                                left: 8,
                                display: 'flex',
                                alignItems: 'center',
                                gap: 0.25,
                                bgcolor: 'background.surface',
                                px: 0.75,
                                py: 0.25,
                                borderRadius: 'sm',
                                boxShadow: 'sm',
                            }}
                        >
                            <StarIcon sx={{ fontSize: 14, color: '#FFD700' }} />
                            <Typography level="body-xs" sx={{ fontWeight: 600 }}>
                                {hoveredDestination.rating}
                            </Typography>
                        </Box>
                        {/* Save button */}
                        <IconButton
                            size="sm"
                            variant="solid"
                            sx={{
                                position: 'absolute',
                                top: 8,
                                right: 8,
                                bgcolor: 'background.surface',
                                color: isDestinationSaved(hoveredDestination.id) ? 'primary.500' : 'text.primary',
                                minWidth: 'auto',
                                '&:hover': {
                                    bgcolor: 'background.level1',
                                },
                            }}
                            onClick={handleSaveClick}
                        >
                            {isDestinationSaved(hoveredDestination.id) ? (
                                <BookmarkIcon sx={{ fontSize: 16 }} />
                            ) : (
                                <BookmarkBorderIcon sx={{ fontSize: 16 }} />
                            )}
                        </IconButton>
                    </Box>
                    {/* Content section - clickable to open chat */}
                    <CardContent
                        sx={{ p: 1.5, cursor: 'pointer' }}
                        onClick={handleDestinationClick}
                    >
                        <Typography level="title-md" sx={{ fontWeight: 600, mb: 0.25 }}>
                            {hoveredDestination.name}
                        </Typography>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0.5 }}>
                            <LocalOfferIcon sx={{ fontSize: 14, color: 'primary.500' }} />
                            <Typography level="body-xs" sx={{ color: 'text.secondary' }}>
                                {hoveredDestination.category}
                            </Typography>
                        </Box>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0.5 }}>
                            <PlaceIcon sx={{ fontSize: 14, color: 'text.tertiary' }} />
                            <Typography level="body-xs" sx={{ color: 'text.secondary' }}>
                                {hoveredDestination.location}
                            </Typography>
                        </Box>
                        <Typography level="body-xs" sx={{ fontWeight: 500 }}>
                            {'$'.repeat(hoveredDestination.priceLevel)}
                        </Typography>
                    </CardContent>
                </Card>
            )}

            {/* Map Controls */}
            <Box
                sx={{
                    position: 'absolute',
                    top: 12,
                    right: 12,
                    zIndex: 1000,
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 1,
                }}
            >
                <IconButton
                    variant="solid"
                    size="sm"
                    onClick={() => dispatch(toggleMapFullscreen())}
                    sx={{
                        bgcolor: 'background.surface',
                        color: 'text.primary',
                        boxShadow: 'md',
                        '&:hover': {
                            bgcolor: 'background.level1',
                        },
                    }}
                >
                    {mapFullscreen ? <FullscreenExitIcon /> : <FullscreenIcon />}
                </IconButton>
            </Box>
        </Box>
    );
};

export default MapPanel;
