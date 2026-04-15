package com.platform.smartwastemanager.features.map.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Top-level response from OSRM /trip endpoint.
 * Example URL: http://router.project-osrm.org/trip/v1/driving/28.0473,-26.2041;28.05,-26.21
 *
 * We only extract the waypoint order — the actual navigation is handled
 * by drawing a polyline between stops in sequence.
 */
@Serializable
data class OsrmTripResponse(
    @SerialName("code")      val code: String = "",
    @SerialName("waypoints") val waypoints: List<OsrmWaypoint> = emptyList(),
    @SerialName("trips")     val trips: List<OsrmTrip> = emptyList()
)

/**
 * One waypoint in the OSRM response.
 * [waypointIndex] tells us the optimised visit order for this input coordinate.
 * [tripIndex] is always 0 for single-trip responses.
 */
@Serializable
data class OsrmWaypoint(
    @SerialName("waypoint_index") val waypointIndex: Int = 0,
    @SerialName("trip_index")     val tripIndex: Int = 0,
    @SerialName("location")       val location: List<Double> = emptyList(), // [lng, lat]
    @SerialName("name")           val name: String = ""
)

/**
 * A single trip returned by OSRM.
 * We use the geometry (encoded polyline) to draw the route on the map.
 */
@Serializable
data class OsrmTrip(
    @SerialName("geometry")  val geometry: OsrmGeometry = OsrmGeometry(),
    @SerialName("distance")  val distance: Double = 0.0,
    @SerialName("duration")  val duration: Double = 0.0
)

/**
 * Geometry from OSRM — encoded as a polyline string when overview=simplified is used,
 * or as a GeoJSON object. We request overview=full&geometries=polyline.
 */
@Serializable
data class OsrmGeometry(
    // This is a polyline-encoded string (Google Polyline Format)
    @SerialName("coordinates") val coordinates: List<List<Double>> = emptyList()
)