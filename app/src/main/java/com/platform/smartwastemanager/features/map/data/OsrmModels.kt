package com.platform.smartwastemanager.features.map.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────────────
// TRIP endpoint models (used for stop order optimisation)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Top-level response from OSRM /trip endpoint.
 */
@Serializable
data class OsrmTripResponse(
    @SerialName("code")      val code: String = "",
    @SerialName("waypoints") val waypoints: List<OsrmWaypoint> = emptyList(),
    @SerialName("trips")     val trips: List<OsrmTrip> = emptyList()
)

/**
 * One waypoint in the OSRM trip response.
 * [waypointIndex] is the optimised visit order for this coordinate.
 */
@Serializable
data class OsrmWaypoint(
    @SerialName("waypoint_index") val waypointIndex: Int = 0,
    @SerialName("trip_index")     val tripIndex: Int = 0,
    @SerialName("location")       val location: List<Double> = emptyList(),
    @SerialName("name")           val name: String = ""
)

/**
 * A single trip returned by OSRM /trip.
 */
@Serializable
data class OsrmTrip(
    @SerialName("geometry")  val geometry: OsrmGeometry = OsrmGeometry(),
    @SerialName("distance")  val distance: Double = 0.0,
    @SerialName("duration")  val duration: Double = 0.0
)

/**
 * GeoJSON geometry from OSRM (used when geometries=geojson).
 */
@Serializable
data class OsrmGeometry(
    @SerialName("coordinates") val coordinates: List<List<Double>> = emptyList()
)

// ─────────────────────────────────────────────────────────────────────────────
// ROUTE endpoint models (used for turn-by-turn directions to next stop)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Top-level response from OSRM /route endpoint.
 */
@Serializable
data class OsrmRouteResponse(
    @SerialName("code")   val code: String = "",
    @SerialName("routes") val routes: List<OsrmRoute> = emptyList()
)

/**
 * A single route from the OSRM /route response.
 */
@Serializable
data class OsrmRoute(
    @SerialName("distance") val distance: Double = 0.0,
    @SerialName("duration") val duration: Double = 0.0,
    @SerialName("legs")     val legs: List<OsrmLeg> = emptyList()
)

/**
 * One leg of the route (origin → destination segment).
 * Since we always request origin → single destination, there is one leg.
 */
@Serializable
data class OsrmLeg(
    @SerialName("distance") val distance: Double = 0.0,
    @SerialName("duration") val duration: Double = 0.0,
    @SerialName("steps")    val steps: List<OsrmStep> = emptyList()
)

/**
 * One navigation step (e.g. "Turn right onto Oak Avenue").
 */
@Serializable
data class OsrmStep(
    @SerialName("distance") val distance: Double = 0.0,
    @SerialName("duration") val duration: Double = 0.0,
    @SerialName("name")     val name: String = "",
    @SerialName("maneuver") val maneuver: OsrmManeuver = OsrmManeuver()
)

/**
 * The maneuver instruction for a step.
 * [type] examples: "depart", "turn", "arrive", "roundabout", etc.
 * [modifier] examples: "left", "right", "straight", "slight left", etc.
 */
@Serializable
data class OsrmManeuver(
    @SerialName("type")     val type: String = "",
    @SerialName("modifier") val modifier: String = ""
)