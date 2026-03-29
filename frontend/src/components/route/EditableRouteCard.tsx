import { useMemo, useState } from 'react';
import {
    Box,
    Button,
    Card,
    Chip,
    CircularProgress,
    Divider,
    IconButton,
    Tooltip,
    Typography,
} from '@mui/joy';
import RouteIcon from '@mui/icons-material/Route';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import LocationOnIcon from '@mui/icons-material/LocationOn';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import StraightenIcon from '@mui/icons-material/Straighten';
import DirectionsWalkIcon from '@mui/icons-material/DirectionsWalk';
import DirectionsCarIcon from '@mui/icons-material/DirectionsCar';
import StarIcon from '@mui/icons-material/Star';
import DragIndicatorIcon from '@mui/icons-material/DragIndicator';
import ShuffleIcon from '@mui/icons-material/Shuffle';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import AddCircleOutlineIcon from '@mui/icons-material/AddCircleOutline';
import type { RouteData, RoutePointData } from '../../services/routeService';
import type { ColorPaletteProp } from '@mui/joy/styles';
import type { MapDestination } from '../../data/destinations';
import { mapTypesArrayToCategory } from '../../utils/placeCategory';
import PlaceSearchAutocomplete from './PlaceSearchAutocomplete';

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

function getTravelModeIcon(mode: string | undefined) {
    if (mode?.toLowerCase().includes('walk')) return <DirectionsWalkIcon sx={{ fontSize: 18 }} />;
    return <DirectionsCarIcon sx={{ fontSize: 18 }} />;
}

function getCategoryBadge(types: string[] | undefined): { label: string; color: ColorPaletteProp } {
    const category = mapTypesArrayToCategory(types);

    switch (category) {
        case 'Cafes & Desserts':
            return { label: 'Cafe', color: 'warning' };
        case 'Restaurants':
            return { label: 'Restaurant', color: 'danger' };
        case 'Parks':
            return { label: 'Park', color: 'success' };
        case 'Historic Places':
            return { label: 'Historic', color: 'neutral' };
        case 'Bars & Nightclubs':
            return { label: 'Nightlife', color: 'neutral' };
        case 'Hotels':
            return { label: 'Hotel', color: 'neutral' };
        default:
            return { label: 'Landmark', color: 'primary' };
    }
}

function isMutablePoint(point: RoutePointData): boolean {
    return Boolean(point.poiId);
}

function getPointLabel(point: RoutePointData, pointPosition: number, totalPoints: number): string {
    if (point.poiName?.trim()) {
        return point.poiName;
    }
    if (point.fixedAnchor && pointPosition === 0) {
        return 'Start anchor';
    }
    if (point.fixedAnchor && pointPosition === totalPoints - 1) {
        return 'End anchor';
    }
    return 'Route point';
}

function buildFullRouteOrder(route: RouteData, draggedIndex: number, targetIndex: number): number[] {
    const mutableIndices = route.points.filter(isMutablePoint).map((point) => point.index);
    const fromMutableIndex = mutableIndices.indexOf(draggedIndex);
    const targetMutableIndex = mutableIndices.indexOf(targetIndex);

    if (fromMutableIndex === -1 || targetMutableIndex === -1 || fromMutableIndex === targetMutableIndex) {
        return route.points.map((point) => point.index);
    }

    const reorderedMutable = [...mutableIndices];
    [reorderedMutable[fromMutableIndex], reorderedMutable[targetMutableIndex]] = [
        reorderedMutable[targetMutableIndex],
        reorderedMutable[fromMutableIndex],
    ];

    const mutableIndexSet = new Set(mutableIndices);
    let cursor = 0;
    return route.points.map((point) => {
        if (!mutableIndexSet.has(point.index)) {
            return point.index;
        }
        const next = reorderedMutable[cursor];
        cursor += 1;
        return next;
    });
}

interface EditableRouteCardProps {
    route: RouteData;
    index: number;
    onApprove: (route: RouteData) => void;
    isApproving: boolean;
    isApproved: boolean;
    isMutating?: boolean;
    onInsertBetween: (insertIndex: number) => void;
    onRemovePoint: (pointIndex: number) => void;
    onRerollPoint: (pointIndex: number) => void;
    onReorderPoints: (newOrder: number[]) => void;
    activeInsertIndex?: number | null;
    insertPlaceOptions?: MapDestination[];
    insertPlaceValue?: MapDestination | null;
    insertPlaceLoading?: boolean;
    onInsertPlaceInputChange?: (query: string) => void;
    onInsertPlaceSelect?: (value: MapDestination | null) => void;
    onInsertCancel?: () => void;
}

const EditableRouteCard = ({
    route,
    index,
    onApprove,
    isApproving,
    isApproved,
    isMutating = false,
    onInsertBetween,
    onRemovePoint,
    onRerollPoint,
    onReorderPoints,
    activeInsertIndex = null,
    insertPlaceOptions = [],
    insertPlaceValue = null,
    insertPlaceLoading = false,
    onInsertPlaceInputChange,
    onInsertPlaceSelect,
    onInsertCancel,
}: EditableRouteCardProps) => {
    const [draggedPointIndex, setDraggedPointIndex] = useState<number | null>(null);
    const [dragOverPointIndex, setDragOverPointIndex] = useState<number | null>(null);

    const visiblePoints = useMemo(
        () => route.points.filter((point) => point.poiId || point.fixedAnchor),
        [route.points],
    );

    return (
        <Card
            id={`route-card-${index}`}
            variant="outlined"
            sx={{
                overflow: 'hidden',
                transition: 'all 0.3s ease-in-out',
                position: 'relative',
                '&:hover': {
                    boxShadow: 'lg',
                    borderColor: 'primary.400',
                },
            }}
        >
            {isMutating && (
                <Box
                    sx={{
                        position: 'absolute',
                        inset: 0,
                        zIndex: 2,
                        bgcolor: 'rgba(6,16,28,0.18)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        backdropFilter: 'blur(2px)',
                    }}
                >
                    <CircularProgress size="sm" />
                </Box>
            )}

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

            <Box sx={{ p: 2 }}>
                <Typography level="body-sm" sx={{ fontWeight: 600, mb: 1.5, color: 'text.secondary' }}>
                    {route.points.filter((point) => point.poiId).length} stops
                </Typography>

                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                    {visiblePoints.map((point, pointPosition) => {
                        const categoryBadge = getCategoryBadge(point.types);
                        const mutable = isMutablePoint(point);
                        const isDragOver = dragOverPointIndex === point.index;

                        return (
                            <Box key={`${point.poiId || 'anchor'}-${point.index}`}>
                                <Box
                                    draggable={mutable}
                                    onDragStart={() => {
                                        if (!mutable) return;
                                        setDraggedPointIndex(point.index);
                                    }}
                                    onDragEnd={() => {
                                        setDraggedPointIndex(null);
                                        setDragOverPointIndex(null);
                                    }}
                                    onDragOver={(event) => {
                                        if (!mutable || draggedPointIndex === null) return;
                                        event.preventDefault();
                                        setDragOverPointIndex(point.index);
                                    }}
                                    onDrop={(event) => {
                                        if (!mutable || draggedPointIndex === null) return;
                                        event.preventDefault();
                                        const newOrder = buildFullRouteOrder(route, draggedPointIndex, point.index);
                                        setDraggedPointIndex(null);
                                        setDragOverPointIndex(null);
                                        onReorderPoints(newOrder);
                                    }}
                                    sx={{
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: 1.25,
                                        p: 1.5,
                                        borderRadius: 'lg',
                                        bgcolor: isDragOver ? 'primary.softBg' : 'background.level1',
                                        border: '1px solid',
                                        borderColor: isDragOver ? 'primary.300' : 'rgba(142, 161, 186, 0.16)',
                                        transition: 'all 0.2s',
                                        '&:hover': {
                                            bgcolor: isDragOver ? 'primary.softBg' : 'background.level2',
                                        },
                                    }}
                                >
                                    <Box
                                        sx={{
                                            width: 30,
                                            height: 30,
                                            borderRadius: '50%',
                                            bgcolor: point.poiId ? 'primary.softBg' : 'neutral.softBg',
                                            color: point.poiId ? 'primary.600' : 'neutral.700',
                                            display: 'flex',
                                            alignItems: 'center',
                                            justifyContent: 'center',
                                            fontWeight: 700,
                                            fontSize: 13,
                                            flexShrink: 0,
                                        }}
                                    >
                                        {pointPosition + 1}
                                    </Box>

                                    <Box sx={{ display: 'flex', alignItems: 'center', color: 'text.secondary' }}>
                                        <DragIndicatorIcon sx={{ opacity: mutable ? 1 : 0.35 }} />
                                    </Box>

                                    <Box sx={{ flex: 1, minWidth: 0 }}>
                                        <Typography
                                            level="body-sm"
                                            sx={{
                                                fontWeight: 700,
                                                overflow: 'hidden',
                                                textOverflow: 'ellipsis',
                                                whiteSpace: 'nowrap',
                                            }}
                                        >
                                            {getPointLabel(point, pointPosition, visiblePoints.length)}
                                        </Typography>
                                        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75, mt: 0.75 }}>
                                            <Chip size="sm" variant="soft" color={categoryBadge.color} sx={{ fontWeight: 700 }}>
                                                {point.poiId ? categoryBadge.label : 'Custom anchor'}
                                            </Chip>
                                            {point.plannedVisitMin > 0 && (
                                                <Chip size="sm" variant="outlined" color="neutral">
                                                    {point.plannedVisitMin} min
                                                </Chip>
                                            )}
                                        </Box>
                                        {point.formattedAddress && (
                                            <Typography
                                                level="body-xs"
                                                sx={{
                                                    color: 'text.secondary',
                                                    mt: 0.75,
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

                                    {point.ratingScore > 0 && (
                                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.3, flexShrink: 0 }}>
                                            <StarIcon sx={{ fontSize: 14, color: '#FFB800' }} />
                                            <Typography level="body-xs" sx={{ fontWeight: 700 }}>
                                                {point.ratingScore.toFixed(1)}
                                            </Typography>
                                        </Box>
                                    )}

                                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.25, flexShrink: 0 }}>
                                        {mutable && (
                                            <>
                                                <Tooltip title="Reroll this stop">
                                                    <IconButton size="sm" variant="plain" color="neutral" onClick={() => onRerollPoint(point.index)}>
                                                        <ShuffleIcon />
                                                    </IconButton>
                                                </Tooltip>
                                                <Tooltip title="Remove this stop">
                                                    <IconButton size="sm" variant="plain" color="danger" onClick={() => onRemovePoint(point.index)}>
                                                        <DeleteOutlineIcon />
                                                    </IconButton>
                                                </Tooltip>
                                            </>
                                        )}
                                    </Box>
                                </Box>

                                {pointPosition < visiblePoints.length - 1 && (
                                    activeInsertIndex === visiblePoints[pointPosition + 1].index ? (
                                        <Box
                                            sx={{
                                                display: 'flex',
                                                alignItems: 'flex-end',
                                                gap: 1,
                                                py: 1,
                                                px: 1,
                                                flexWrap: 'wrap',
                                            }}
                                        >
                                            <PlaceSearchAutocomplete
                                                label="Search Place"
                                                value={insertPlaceValue}
                                                options={insertPlaceOptions}
                                                loading={insertPlaceLoading}
                                                onInputChange={(query) => onInsertPlaceInputChange?.(query)}
                                                onChange={(value) => onInsertPlaceSelect?.(value)}
                                            />
                                            <Button size="sm" variant="plain" color="neutral" onClick={onInsertCancel}>
                                                Cancel
                                            </Button>
                                        </Box>
                                    ) : (
                                        <Box sx={{ display: 'flex', justifyContent: 'center', py: 0.75 }}>
                                            <Button
                                                size="sm"
                                                variant="plain"
                                                color="neutral"
                                                startDecorator={<AddCircleOutlineIcon />}
                                                onClick={() => onInsertBetween(visiblePoints[pointPosition + 1].index)}
                                                sx={{ borderRadius: 'xl' }}
                                            >
                                                Insert here
                                            </Button>
                                        </Box>
                                    )
                                )}
                            </Box>
                        );
                    })}
                </Box>
            </Box>

            <Divider />

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
                    disabled={isApproving || isApproved || isMutating}
                    onClick={() => onApprove(route)}
                    sx={{ fontWeight: 600, transition: 'all 0.2s' }}
                >
                    {isApproving ? 'Saving...' : isApproved ? 'Approved' : 'Approve'}
                </Button>
            </Box>
        </Card>
    );
};

export default EditableRouteCard;
