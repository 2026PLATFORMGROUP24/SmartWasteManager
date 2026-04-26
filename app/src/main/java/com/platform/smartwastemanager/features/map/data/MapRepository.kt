package com.platform.smartwastemanager.features.map.data

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.google.android.gms.maps.model.LatLng
import com.platform.smartwastemanager.core.util.Constants
import com.platform.smartwastemanager.features.collectionpoint.domain.CollectionPoint
import com.platform.smartwastemanager.features.map.domain.MapPin
import com.platform.smartwastemanager.features.map.domain.RouteResult
import com.platform.smartwastemanager.features.map.domain.RouteStop
import com.platform.smartwastemanager.features.map.domain.RouteStopType
import com.platform.smartwastemanager.features.map.domain.TurnByTurnNavigation
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

    /** Real-time stream of ALL collection points (all users) — used by driver map views. */
    fun getAllCollectionPoints(): Flow<List<CollectionPoint>> = callbackFlow {
        val listener = firestore.collection("collection_points")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                val points = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        CollectionPoint(
                            id         = doc.id,
                            userId     = doc.getString("userId") ?: "",
                            name       = doc.getString("name") ?: "",
                            location   = doc.getGeoPoint("location") ?: GeoPoint(0.0, 0.0),
                            streetName = doc.getString("streetName") ?: "",
                            zoneId     = doc.getString("zoneId") ?: "",
                            markedForCollectionDays = (doc.get("markedForCollectionDays") as? List<*>)
                                ?.filterIsInstance<String>() ?: emptyList(),
                            createdAt  = doc.getTimestamp("createdAt")
                                ?: com.google.firebase.Timestamp.now()
                        )
                    } catch (_: Exception) { null }
                } ?: emptyList()
                trySend(points)
            }
        awaitClose { listener.remove() }
    }.catch { emit(emptyList()) }

    suspend fun dismissReport(reportId: String) {
        firestore.collection("waste_reports").document(reportId)
            .delete().await()
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
        val scheduleDocs = firestore.collection(Constants.COLLECTION_SCHEDULES)
            .whereEqualTo("zoneId", zoneId)
            .get()
            .await()
            .documents
        scheduleDocs.forEach { it.reference.delete().await() }
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
     *   1. Fetch all pending Regular Pickup reports inside the zone radius whose
     *      [category] matches one of the [scheduleCategories] for the day.
     *      If [scheduleCategories] is empty, ALL categories are included (fallback).
     *   2. Pre-sort stops by straight-line (haversine) distance from the driver so
     *      stop[0] is ALWAYS the geographically nearest — this is the reliable
     *      nearest-first guarantee that does not depend on OSRM.
     *   3. Prepend the driver's location as coordinate index 0 in the OSRM /trip
     *      call with source=first, so OSRM routes driver → all stops in optimal order.
     *   4. Pass approaches=curb for every waypoint so OSRM always routes to the
     *      kerb/left side of the road — prevents the "wrong side of the road" problem.
     *   5. Re-order stops using the waypoint_index values OSRM returns.
     *   6. Decode the road-following GeoJSON geometry polyline.
     *
     * Falls back to the haversine-sorted stop list with no polyline if OSRM is
     * unreachable or returns a non-Ok response.
     *
     * @param scheduleDayId       Schedule day document id used to fetch category filters and
     *                            collection points marked ready for this schedule day.
     * @param driverLat           Driver's current latitude  (0.0 = unavailable).
     * @param driverLng           Driver's current longitude (0.0 = unavailable).
     */
    suspend fun calculateRouteForZone(
        zone: Zone,
        scheduleDayId: String = "",
        driverLat: Double = 0.0,
        driverLng: Double = 0.0
    ): RouteResult {
        val scheduleCategories = if (scheduleDayId.isBlank()) {
            emptyList()
        } else {
            (firestore.collection(Constants.COLLECTION_SCHEDULES).document(scheduleDayId).get(Source.SERVER).await()
                .get("wasteCategories") as? List<*>)
                ?.filterIsInstance<String>()
                ?: emptyList()
        }

        // --- 1. Fetch pending Regular Pickup reports ---
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
                    type        = RouteStopType.WASTE_REPORT,
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

        // Filter by the schedule day's waste categories.
        // If scheduleCategories is empty (safety fallback), keep all stops.
        val filteredStops = if (scheduleCategories.isEmpty()) {
            stopsInZone
        } else {
            stopsInZone.filter { stop ->
                // Case-insensitive match so minor formatting differences don't break routing
                scheduleCategories.any { cat ->
                    cat.equals(stop.category, ignoreCase = true)
                }
            }
        }

        val collectionPointStops = if (scheduleDayId.isBlank()) {
            emptyList()
        } else {
            firestore.collection("collection_points")
                .whereEqualTo("zoneId", zone.id)
                .whereArrayContains("markedForCollectionDays", scheduleDayId)
                .get(Source.SERVER)
                .await()
                .documents
                .mapNotNull { doc ->
                    try {
                        val gp = doc.getGeoPoint("location") ?: return@mapNotNull null
                        RouteStop(
                            reportId   = doc.id,
                            location   = gp,
                            // Show only the street address — never the user-assigned name
                            // (names like "HOME" must not be visible to drivers for privacy)
                            streetName = doc.getString("streetName")
                                ?.takeIf { it.isNotBlank() }
                                ?: "Unknown Street",
                            category   = "COLLECTION POINT",
                            type       = RouteStopType.COLLECTION_POINT,
                            isCollected = false
                        )
                    } catch (e: Exception) { null }
                }
        }

        val combinedStops = filteredStops + collectionPointStops

        if (combinedStops.isEmpty()) return RouteResult(stops = emptyList(), roadPolyline = emptyList())
        if (combinedStops.size == 1) return RouteResult(stops = combinedStops, roadPolyline = emptyList())

        val hasDriverLocation = driverLat != 0.0 || driverLng != 0.0

        // --- 2. Pre-sort stops by haversine distance from the driver (or zone centre).
        //        This is the reliable nearest-first guarantee that does not depend on OSRM.
        val originLat = if (hasDriverLocation) driverLat else zone.centerLat
        val originLng = if (hasDriverLocation) driverLng else zone.centerLng
        val nearestFirstStops = combinedStops.sortedBy { stop ->
            haversineDistanceMeters(
                originLat, originLng,
                stop.location.latitude, stop.location.longitude
            )
        }

        // --- 3. Build OSRM coordinate string.
        //        Prepend driver location as coordinate[0] if available (source=first).
        val coordsList = buildList {
            if (hasDriverLocation) add("$driverLng,$driverLat")   // index 0 = driver
            addAll(nearestFirstStops.map { "${it.location.longitude},${it.location.latitude}" })
        }
        val coords = coordsList.joinToString(";")

        // --- 4. Build the approaches parameter.
        //        "curb" tells OSRM to always approach from the left-hand side of the road
        //        (the kerb), preventing the "wrong side of the road" routing bug.
        //        One "curb" entry is needed per coordinate, including the driver origin.
        val approaches = coordsList.indices.joinToString(";") { "curb" }

        return try {
            val resp = osrmService.getOptimisedTrip(
                coordinates = coords,
                roundtrip   = false,
                source      = "first",
                destination = "last",
                approaches  = approaches
            )

            if (resp.code == "Ok" && resp.waypoints.isNotEmpty()) {

                // OSRM returns waypoints in INPUT order.
                // waypointIndex = optimised visit position in the trip (0-based).
                // With hasDriverLocation=true the driver gets waypointIndex 0 (source=first).
                // We drop the driver waypoint then sort stops by waypointIndex.
                val driverOffset  = if (hasDriverLocation) 1 else 0
                val stopWaypoints = resp.waypoints.drop(driverOffset)

                val orderedStops = nearestFirstStops
                    .mapIndexed { i, stop ->
                        // waypointIndex is 1-based for stops when driverOffset=1.
                        // Subtract driverOffset to make it 0-based for sorting.
                        val visitPosition = (stopWaypoints.getOrNull(i)?.waypointIndex
                            ?: (i + driverOffset)) - driverOffset
                        visitPosition to stop
                    }
                    .sortedBy { it.first }
                    .map { it.second }

                // Decode the road-following GeoJSON polyline (coordinates = [lng, lat])
                val roadPolyline = resp.trips.firstOrNull()
                    ?.geometry
                    ?.coordinates
                    ?.mapNotNull { coord ->
                        if (coord.size >= 2) LatLng(coord[1], coord[0]) else null
                    } ?: emptyList()

                RouteResult(stops = orderedStops, roadPolyline = roadPolyline)

            } else {
                // OSRM gave a non-Ok response — use our haversine nearest-first list.
                RouteResult(stops = nearestFirstStops, roadPolyline = emptyList())
            }

        } catch (e: Exception) {
            // Network/parse failure — haversine nearest-first is the safe fallback.
            RouteResult(stops = nearestFirstStops, roadPolyline = emptyList())
        }
    }

    /**
     * Fetches turn-by-turn driving directions from the driver to a single stop.
     * Returns an empty list on any failure — the UI handles this gracefully.
     */
    suspend fun getDirectionsToStop(
        fromLat: Double, fromLng: Double,
        toLat: Double,   toLng: Double
    ): TurnByTurnNavigation {
        return try {
            val coords   = "$fromLng,$fromLat;$toLng,$toLat"
            // approaches=curb;curb — approach both the origin and destination from the kerb side
            val response = osrmService.getRoute(
                coordinates = coords,
                steps       = true,
                approaches  = "curb;curb"
            )
            if (response.code != "Ok") return TurnByTurnNavigation()
            val route = response.routes.firstOrNull() ?: return TurnByTurnNavigation()
            val steps = route.legs.firstOrNull()?.steps
                ?.mapNotNull { buildDirectionString(it) }
                ?.filter { it.isNotBlank() }
                .orEmpty()
            TurnByTurnNavigation(
                steps = steps,
                distanceMeters = route.distance.toInt(),
                durationSeconds = route.duration.toInt()
            )
        } catch (e: Exception) {
            TurnByTurnNavigation()
        }
    }

    suspend fun getRoadPolylineForOrderedStops(
        stops: List<RouteStop>,
        driverLat: Double? = null,
        driverLng: Double? = null
    ): List<LatLng> {
        val hasDriverLocation = driverLat != null && driverLng != null
        if (stops.isEmpty() || (stops.size == 1 && !hasDriverLocation)) return emptyList()

        val coordsList = buildList {
            if (hasDriverLocation) add("${driverLng},${driverLat}")
            addAll(stops.map { "${it.location.longitude},${it.location.latitude}" })
        }
        val coords = coordsList.joinToString(";")
        val approaches = coordsList.indices.joinToString(";") { "curb" }

        return try {
            val response = osrmService.getRoute(
                coordinates = coords,
                steps = false,
                approaches = approaches
            )
            if (response.code != "Ok") return emptyList()

            response.routes.firstOrNull()
                ?.geometry
                ?.coordinates
                ?.mapNotNull { coord ->
                    // OSRM returns [longitude, latitude], but Google LatLng expects [latitude, longitude].
                    if (coord.size >= 2) LatLng(coord[1], coord[0]) else null
                }.orEmpty()
        } catch (_: Exception) {
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

    /**
     * Calculates an optimised collection route for a pre-filtered list of [MapPin]s.
     * Mirrors the logic in calculateRouteForZone but skips all zone/schedule filtering.
     */
    suspend fun calculateRouteForPins(
        pins: List<MapPin>,
        driverLat: Double = 0.0,
        driverLng: Double = 0.0
    ): RouteResult {
        val stops = pins.map { pin ->
            RouteStop(
                reportId   = pin.reportId,
                location   = pin.location,
                streetName = pin.streetName,
                category   = pin.category,
                type       = RouteStopType.WASTE_REPORT,
                isCollected = false
            )
        }
        if (stops.isEmpty()) return RouteResult(emptyList(), emptyList())
        if (stops.size == 1) return RouteResult(stops, emptyList())

        val hasDriverLocation = driverLat != 0.0 || driverLng != 0.0
        val originLat = if (hasDriverLocation) driverLat else stops[0].location.latitude
        val originLng = if (hasDriverLocation) driverLng else stops[0].location.longitude

        val nearestFirstStops = stops.sortedBy { stop ->
            haversineDistanceMeters(originLat, originLng, stop.location.latitude, stop.location.longitude)
        }

        val coordsList = buildList {
            if (hasDriverLocation) add("$driverLng,$driverLat")
            addAll(nearestFirstStops.map { "${it.location.longitude},${it.location.latitude}" })
        }
        val coords    = coordsList.joinToString(";")
        val approaches = coordsList.indices.joinToString(";") { "curb" }

        return try {
            val resp = osrmService.getOptimisedTrip(
                coordinates = coords,
                roundtrip   = false,
                source      = "first",
                destination = "last",
                approaches  = approaches
            )
            if (resp.code == "Ok" && resp.waypoints.isNotEmpty()) {
                val driverOffset  = if (hasDriverLocation) 1 else 0
                val stopWaypoints = resp.waypoints.drop(driverOffset)
                val orderedStops  = nearestFirstStops
                    .mapIndexed { i, stop ->
                        val pos = (stopWaypoints.getOrNull(i)?.waypointIndex
                            ?: (i + driverOffset)) - driverOffset
                        pos to stop
                    }
                    .sortedBy { it.first }
                    .map { it.second }
                val roadPolyline  = resp.trips.firstOrNull()
                    ?.geometry?.coordinates
                    ?.mapNotNull { coord -> if (coord.size >= 2) LatLng(coord[1], coord[0]) else null }
                    ?: emptyList()
                RouteResult(stops = orderedStops, roadPolyline = roadPolyline)
            } else {
                RouteResult(stops = nearestFirstStops, roadPolyline = emptyList())
            }
        } catch (_: Exception) {
            RouteResult(stops = nearestFirstStops, roadPolyline = emptyList())
        }
    }

    suspend fun collectStop(reportId: String) {
        firestore.collection("waste_reports").document(reportId)
            .delete().await()
    }

    suspend fun unmarkCollectionPointFromDay(pointId: String, scheduleDayId: String) {
        if (scheduleDayId.isBlank()) return
        firestore.collection("collection_points").document(pointId)
            .update("markedForCollectionDays", FieldValue.arrayRemove(scheduleDayId))
            .await()
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
    // REPLACE lines 380-416 with this corrected version:
    fun getZones(): Flow<List<Zone>> = callbackFlow {
        val listener = firestore.collection("route_zones")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val zones = snapshot.documents.mapNotNull { doc ->
                        try {
                            Zone(
                                id = doc.id,
                                name = doc.getString("name") ?: "",
                                centerLat = doc.getDouble("centerLat") ?: 0.0,
                                centerLng = doc.getDouble("centerLng") ?: 0.0,
                                radiusMeters = doc.getDouble("radiusMeters") ?: 1000.0,
                                createdBy = doc.getString("createdBy") ?: ""
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                    trySend(zones)
                }
            }

        awaitClose { listener.remove() }
    }.catch { emit(emptyList()) }
}
