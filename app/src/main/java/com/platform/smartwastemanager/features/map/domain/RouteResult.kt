package com.platform.smartwastemanager.features.map.domain

import com.google.android.gms.maps.model.LatLng

/**
 * Returned by [MapRepository.calculateRouteForZone].
 *
 * Bundles the two things the UI needs after an OSRM optimisation call:
 *
 * @property stops        Stops in optimised visit order.
 * @property roadPolyline LatLng points decoded from the OSRM GeoJSON geometry —
 *                        these trace the actual road path between all stops.
 *                        Empty list if OSRM failed (UI falls back to straight lines).
 */
data class RouteResult(
    val stops: List<RouteStop>,
    val roadPolyline: List<LatLng>
)