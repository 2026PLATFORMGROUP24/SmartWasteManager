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
     * Fetches all pending Regular Pickup reports inside [zone], asks OSRM to
     * optimise the visit order, and returns a [RouteResult] containing:
     *   - [RouteResult.stops]        — stops in optimised visit order
     *   - [RouteResult.roadPolyline] — LatLng points that trace the actual roads
     *                                  between all stops (from the OSRM geometry).
     *
     * If the OSRM call fails the stops are still returned in unoptimised order
     * and roadPolyline will be empty (the screen falls back to straight lines).
     */
    suspend fun calculateRouteForZone(zone: Zone): RouteResult {
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

        val stopsInZone = allPickups.filter { stop ->
            haversineDistanceMeters(
                zone.centerLat, zone.centerLng,
                stop.location.latitude, stop.location.longitude
            ) <= zone.radiusMeters
        }

        // Only one stop — no need to call OSRM, no polyline
        if (stopsInZone.size < 2) return RouteResult(stops = stopsInZone, roadPolyline = emptyList())

        val coords = stopsInZone.joinToString(";") {
            "${it.location.longitude},${it.location.latitude}"
        }

        return try {
            val resp = osrmService.getOptimisedTrip(coords)

            if (resp.code == "Ok" && resp.waypoints.size == stopsInZone.size) {

                // Re-order stops using the waypoint_index OSRM returns
                val orderedStops = stopsInZone
                    .mapIndexed { i, stop -> resp.waypoints[i].waypointIndex to stop }
                    .sortedBy { it.first }
                    .map { it.second }

                // Convert the OSRM GeoJSON geometry coordinates [lng, lat] → LatLng
                // This is the road-following polyline for the entire route
                val roadPolyline = resp.trips.firstOrNull()
                    ?.geometry
                    ?.coordinates
                    ?.mapNotNull { coord ->
                        // OSRM returns [longitude, latitude]
                        if (coord.size >= 2) LatLng(coord[1], coord[0]) else null
                    } ?: emptyList()

                RouteResult(stops = orderedStops, roadPolyline = roadPolyline)

            } else {
                // OSRM responded but not "Ok" — return unordered stops, no polyline
                RouteResult(stops = stopsInZone, roadPolyline = emptyList())
            }
        } catch (e: Exception) {
            // Network/parse failure — return unordered stops, no polyline
            RouteResult(stops = stopsInZone, roadPolyline = emptyList())
        }
    }

    /**
     * Fetches turn-by-turn driving directions from the driver to a single stop.
     * Uses the OSRM /route endpoint (not /trip) which returns step instructions.
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