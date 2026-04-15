package com.platform.smartwastemanager.features.map.data

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.google.android.gms.maps.model.LatLng
import com.platform.smartwastemanager.features.map.domain.MapPin
import com.platform.smartwastemanager.features.map.domain.RouteResult
import com.platform.smartwastemanager.features.map.domain.RouteStop
import com.platform.smartwastemanager.features.map.domain.Zone
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import kotlin.math.*

class MapRepository {

    private val firestore = Firebase.firestore

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val osrmService: OsrmApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://router.project-osrm.org/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OsrmApiService::class.java)
    }

    // =========================================================================
    // Map Pins (waste reports)
    // =========================================================================

    fun getPendingMapPins(): Flow<List<MapPin>> = callbackFlow {
        val listener = firestore.collection("waste_reports")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                val pins = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        MapPin(
                            reportId   = doc.id,
                            location   = doc.getGeoPoint("location") ?: GeoPoint(0.0, 0.0),
                            category   = doc.getString("category") ?: "",
                            reportType = doc.getString("reportType") ?: "",
                            streetName = doc.getString("streetName") ?: "",
                            timestamp  = doc.getTimestamp("timestamp")
                                ?: com.google.firebase.Timestamp.now()
                        )
                    } catch (e: Exception) { null }
                } ?: emptyList()
                trySend(pins)
            }
        awaitClose { listener.remove() }
    }.catch { emit(emptyList()) }

    suspend fun dismissReport(reportId: String) {
        firestore.collection("waste_reports").document(reportId)
            .update("status", "dismissed").await()
    }

    // =========================================================================
    // Zone CRUD — Global zones (no scheduleDayId)
    // =========================================================================

    fun getAllZones(): Flow<List<Zone>> = callbackFlow {
        val listener = firestore.collection("route_zones")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                trySend(parseZones(snapshot))
            }
        awaitClose { listener.remove() }
    }.catch { emit(emptyList()) }

    fun getZonesForDriver(driverUid: String): Flow<List<Zone>> = callbackFlow {
        val listener = firestore.collection("route_zones")
            .whereEqualTo("createdBy", driverUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                trySend(parseZones(snapshot))
            }
        awaitClose { listener.remove() }
    }.catch { emit(emptyList()) }

    suspend fun getZonesById(zoneIds: List<String>): List<Zone> {
        if (zoneIds.isEmpty()) return emptyList()
        return try {
            zoneIds.chunked(30).flatMap { chunk ->
                firestore.collection("route_zones")
                    .whereIn(FieldPath.documentId(), chunk)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { doc ->
                        try {
                            Zone(
                                id           = doc.id,
                                name         = doc.getString("name") ?: "",
                                centerLat    = doc.getDouble("centerLat") ?: 0.0,
                                centerLng    = doc.getDouble("centerLng") ?: 0.0,
                                radiusMeters = doc.getDouble("radiusMeters") ?: 1000.0,
                                createdBy    = doc.getString("createdBy") ?: ""
                            )
                        } catch (e: Exception) { null }
                    }
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun parseZones(
        snapshot: com.google.firebase.firestore.QuerySnapshot?
    ): List<Zone> =
        snapshot?.documents?.mapNotNull { doc ->
            try {
                Zone(
                    id           = doc.id,
                    name         = doc.getString("name") ?: "",
                    centerLat    = doc.getDouble("centerLat") ?: 0.0,
                    centerLng    = doc.getDouble("centerLng") ?: 0.0,
                    radiusMeters = doc.getDouble("radiusMeters") ?: 1000.0,
                    createdBy    = doc.getString("createdBy") ?: ""
                )
            } catch (e: Exception) { null }
        } ?: emptyList()

    suspend fun addZone(zone: Zone): String {
        val data = mapOf(
            "name"         to zone.name,
            "centerLat"    to zone.centerLat,
            "centerLng"    to zone.centerLng,
            "radiusMeters" to zone.radiusMeters,
            "createdBy"    to zone.createdBy
        )
        return firestore.collection("route_zones").add(data).await().id
    }

    suspend fun deleteZone(zoneId: String) {
        firestore.collection("route_zones").document(zoneId).delete().await()
    }

    // =========================================================================
    // Route Calculation
    // =========================================================================

    /**
     * Calculates the optimised collection route for [zone], starting from the
     * driver's current position ([driverLat], [driverLng]).
     *
     * How it works:
     *   1. Fetch all pending Regular Pickup reports inside the zone radius.
     *   2. Prepend the driver's location as coordinate index 0 in the OSRM /trip call.
     *      OSRM will then route: driver → nearest stop → all remaining stops in best order.
     *   3. Strip the driver waypoint from the returned waypoints (index 0) — it is not a
     *      real collection stop.
     *   4. Re-order the stops using the waypoint_index values from OSRM.
     *   5. Decode the road-following geometry polyline from the OSRM response.
     *
     * Falls back to an unordered stop list with no polyline if OSRM is unreachable.
     *
     * @param driverLat  Driver's current latitude  (0.0 = location unavailable, ignored).
     * @param driverLng  Driver's current longitude (0.0 = location unavailable, ignored).
     */
    suspend fun calculateRouteForZone(
        zone: Zone,
        driverLat: Double = 0.0,
        driverLng: Double = 0.0
    ): RouteResult {

        val snapshot = firestore.collection("waste_reports")
            .whereEqualTo("status", "pending")
            .whereEqualTo("reportType", "Regular Pickup")
            .get(Source.SERVER)
            .await()

        val allPickups = snapshot.documents.mapNotNull { doc ->
            try {
                val gp = doc.getGeoPoint("location") ?: return@mapNotNull null
                RouteStop(
                    reportId    = doc.id,
                    location    = gp,
                    streetName  = doc.getString("streetName") ?: "Unknown Street",
                    category    = doc.getString("category") ?: "Unknown",
                    isCollected = false
                )
            } catch (e: Exception) { null }
        }

        // Keep only stops that fall within the zone's radius
        val stopsInZone = allPickups.filter { stop ->
            haversineDistanceMeters(
                zone.centerLat, zone.centerLng,
                stop.location.latitude, stop.location.longitude
            ) <= zone.radiusMeters
        }

        if (stopsInZone.isEmpty()) return RouteResult(stops = emptyList(), roadPolyline = emptyList())

        // Only one stop — no routing needed, but still sort nearest-first
        if (stopsInZone.size == 1) return RouteResult(stops = stopsInZone, roadPolyline = emptyList())

        // Decide whether we have a usable driver location
        val hasDriverLocation = driverLat != 0.0 || driverLng != 0.0

        // Build the coordinate string for OSRM.
        // If we have the driver's location, prepend it as the fixed "source" waypoint.
        // OSRM source=first means it starts from coordinate[0] — the driver.
        val coordsList = buildList {
            if (hasDriverLocation) add("$driverLng,$driverLat")  // index 0 = driver
            addAll(stopsInZone.map { "${it.location.longitude},${it.location.latitude}" })
        }
        val coords = coordsList.joinToString(";")

        return try {
            val resp = osrmService.getOptimisedTrip(
                coordinates = coords,
                roundtrip   = false,
                source      = "first",
                destination = "last"
            )

            if (resp.code == "Ok" && resp.waypoints.isNotEmpty()) {

                // The waypoints list has (stopsInZone.size + 1) entries when driver location
                // was included, or stopsInZone.size entries when it was not.
                // waypointIndex tells us what position OSRM put each input coordinate at.
                val driverOffset = if (hasDriverLocation) 1 else 0
                val stopWaypoints = resp.waypoints.drop(driverOffset) // remove driver entry

                // Re-order stops using waypoint_index.
                // waypointIndex for the stop waypoints starts at driverOffset in OSRM's
                // ordering, so we subtract the offset so they sort as 0, 1, 2…
                val orderedStops = stopsInZone
                    .mapIndexed { i, stop ->
                        val waypointIndex = stopWaypoints.getOrNull(i)?.waypointIndex ?: i
                        (waypointIndex - driverOffset) to stop
                    }
                    .sortedBy { it.first }
                    .map { it.second }

                // Decode the OSRM GeoJSON geometry → road-following LatLng polyline.
                // Coordinates are [longitude, latitude] per GeoJSON spec.
                val roadPolyline = resp.trips.firstOrNull()
                    ?.geometry
                    ?.coordinates
                    ?.mapNotNull { coord ->
                        if (coord.size >= 2) LatLng(coord[1], coord[0]) else null
                    } ?: emptyList()

                RouteResult(stops = orderedStops, roadPolyline = roadPolyline)

            } else {
                // OSRM gave a non-Ok response — fall back to distance-sorted stops, no polyline.
                // If we have the driver's location, sort stops nearest-first manually.
                val fallbackStops = if (hasDriverLocation) {
                    stopsInZone.sortedBy { stop ->
                        haversineDistanceMeters(
                            driverLat, driverLng,
                            stop.location.latitude, stop.location.longitude
                        )
                    }
                } else stopsInZone

                RouteResult(stops = fallbackStops, roadPolyline = emptyList())
            }
        } catch (e: Exception) {
            // Network/parse failure — nearest-first fallback, no polyline
            val fallbackStops = if (hasDriverLocation) {
                stopsInZone.sortedBy { stop ->
                    haversineDistanceMeters(
                        driverLat, driverLng,
                        stop.location.latitude, stop.location.longitude
                    )
                }
            } else stopsInZone

            RouteResult(stops = fallbackStops, roadPolyline = emptyList())
        }
    }

    /**
     * Fetches turn-by-turn driving directions from the driver to a single stop.
     * Returns an empty list on any failure — the UI handles this gracefully.
     */
    suspend fun getDirectionsToStop(
        fromLat: Double, fromLng: Double,
        toLat: Double,   toLng: Double
    ): List<String> {
        return try {
            val coords   = "$fromLng,$fromLat;$toLng,$toLat"
            val response = osrmService.getRoute(coords, steps = true)
            if (response.code != "Ok") return emptyList()
            val steps = response.routes.firstOrNull()?.legs?.firstOrNull()?.steps
                ?: return emptyList()
            steps.mapNotNull { buildDirectionString(it) }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun buildDirectionString(step: OsrmStep): String? {
        val type     = step.maneuver.type
        val modifier = step.maneuver.modifier
        val road     = step.name
        val distM    = step.distance.toInt()
        val distStr  = if (distM >= 1000) "${"%.1f".format(distM / 1000.0)} km" else "$distM m"

        val action = when (type) {
            "depart"          -> "Head ${modifier.ifBlank { "forward" }}"
            "arrive"          -> return "🏁 Arrive at destination"
            "turn"            -> when (modifier) {
                "left"         -> "Turn left"
                "right"        -> "Turn right"
                "slight left"  -> "Turn slight left"
                "slight right" -> "Turn slight right"
                "sharp left"   -> "Turn sharp left"
                "sharp right"  -> "Turn sharp right"
                "uturn"        -> "Make a U-turn"
                else           -> "Continue"
            }
            "new name"        -> "Continue"
            "continue"        -> "Continue straight"
            "merge"           -> "Merge ${modifier.ifBlank { "" }}"
            "roundabout"      -> "Enter the roundabout"
            "exit roundabout" -> "Exit the roundabout"
            "fork"            -> "Keep ${modifier.ifBlank { "straight" }} at the fork"
            else              -> "Continue"
        }

        return if (road.isNotBlank()) "$action onto $road ($distStr)" else "$action ($distStr)"
    }

    suspend fun collectStop(reportId: String) {
        firestore.collection("waste_reports").document(reportId)
            .update("status", "dismissed").await()
    }

    private fun haversineDistanceMeters(
        lat1: Double, lng1: Double, lat2: Double, lng2: Double
    ): Double {
        val r    = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a    = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}