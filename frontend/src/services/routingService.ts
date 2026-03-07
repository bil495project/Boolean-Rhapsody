/**
 * Routing Service using OSRM (Open Source Routing Machine) public API
 * Provides road-following routes instead of straight lines
 */

export interface RouteResult {
    coordinates: [number, number][]; // Array of [lat, lng] coordinates following roads
    distance: number; // Total distance in km
    duration: number; // Total duration in minutes
}

/**
 * Get a road-following route from OSRM
 * @param waypoints Array of [lat, lng] coordinates
 * @param mode 'driving' or 'walking'
 * @returns RouteResult with road-following coordinates and distance/duration
 */
export async function getRoute(
    waypoints: [number, number][],
    mode: 'driving' | 'walking'
): Promise<RouteResult> {
    if (waypoints.length < 2) {
        throw new Error('At least 2 waypoints required');
    }

    // OSRM uses lng,lat format (opposite of Leaflet's lat,lng)
    const coordinateString = waypoints
        .map(([lat, lng]) => `${lng},${lat}`)
        .join(';');

    // OSRM profile: 'driving' for car, 'foot' for walking
    const profile = mode === 'driving' ? 'driving' : 'foot';

    const url = `https://router.project-osrm.org/route/v1/${profile}/${coordinateString}?overview=full&geometries=geojson`;

    try {
        const response = await fetch(url);

        if (!response.ok) {
            throw new Error(`OSRM API error: ${response.status}`);
        }

        const data = await response.json();

        if (data.code !== 'Ok' || !data.routes || data.routes.length === 0) {
            throw new Error('No route found');
        }

        const route = data.routes[0];
        const geometry = route.geometry;

        // Convert GeoJSON coordinates [lng, lat] to Leaflet format [lat, lng]
        const coordinates: [number, number][] = geometry.coordinates.map(
            ([lng, lat]: [number, number]) => [lat, lng] as [number, number]
        );

        return {
            coordinates,
            distance: parseFloat((route.distance / 1000).toFixed(1)), // Convert meters to km
            // OSRM demo server only has driving profile, so for walking we calculate manually
            // Walking speed: ~5 km/h, Driving uses OSRM's returned duration
            duration: mode === 'walking'
                ? Math.ceil((route.distance / 1000) / 5 * 60) // 5 km/h walking speed
                : Math.ceil(route.duration / 60), // OSRM returns seconds for driving
        };
    } catch (error) {
        console.error('OSRM routing error:', error);
        // Fallback to straight line if OSRM fails
        return {
            coordinates: waypoints,
            distance: calculateStraightLineDistance(waypoints),
            duration: estimateDuration(calculateStraightLineDistance(waypoints), mode),
        };
    }
}

/**
 * Calculate straight-line distance between waypoints (fallback)
 */
function calculateStraightLineDistance(waypoints: [number, number][]): number {
    let totalDistance = 0;
    const R = 6371; // Earth radius in km

    for (let i = 0; i < waypoints.length - 1; i++) {
        const [lat1, lng1] = waypoints[i];
        const [lat2, lng2] = waypoints[i + 1];

        const dLat = ((lat2 - lat1) * Math.PI) / 180;
        const dLng = ((lng2 - lng1) * Math.PI) / 180;

        const a =
            Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos((lat1 * Math.PI) / 180) *
            Math.cos((lat2 * Math.PI) / 180) *
            Math.sin(dLng / 2) *
            Math.sin(dLng / 2);

        const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        totalDistance += R * c;
    }

    // Multiply by 1.4 to approximate road distance
    return parseFloat((totalDistance * 1.4).toFixed(1));
}

/**
 * Estimate duration based on distance (fallback)
 */
function estimateDuration(distanceKm: number, mode: 'driving' | 'walking'): number {
    const speed = mode === 'driving' ? 30 : 5; // km/h (city traffic average)
    return Math.ceil((distanceKm / speed) * 60);
}
