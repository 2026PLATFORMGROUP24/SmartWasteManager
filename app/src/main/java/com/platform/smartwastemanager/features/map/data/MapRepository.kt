package com.platform.smartwastemanager.features.map.data

import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.platform.smartwastemanager.features.map.domain.MapPin
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

/**
 * MapRepository handles all data operations for:
 *   1. Loading pending map pins (waste reports) — existing behaviour.
 *   2. Dismissing a report — existing behaviour.
 *   3. Zone CRUD — driver routes feature.
 *   4. Calculating an optimised collection route using OSRM.
 *   5. Collecting (dismissing) a report during an active route.
 */
class MapRepository {

    private val firestore = Firebase.firestore

    // ---- OSRM Retrofit client (free, no API key) ----
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val osrmService: OsrmApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://router.project-osrm.org/")
            .addConverterFactory(
                json.asConverterFactory("application/json".toMediaType())
            )
            .build()
            .create(OsrmApiService::class.java)
    }

    // =====================================================================
    // EXISTING: Map Pins (pending waste reports)
    // =====================================================================

    /**
     * Returns a real-time Flow of all pending waste reports as [MapPin] objects.
     * Rule 4: never close(error) — use trySend(emptyList()) on failure.
     */
    fun getPendingMapPins(): Flow<List<MapPin>> = callbackFlow {
        val listener = firestore.collection("waste_reports")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
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

    /** Updates a waste report's status to "dismissed". */
    suspend fun dismissReport(reportId: String) {
        firestore.collection("waste_reports")
            .document(reportId)
            .update("status", "dismissed")
            .await()
    }

    // =====================================================================
    // Zone CRUD
    // =====================================================================

    fun getZonesForDay(scheduleDayId: String): Flow<List<Zone>> = callbackFlow {
        val listener = firestore.collection("route_zones")
            .whereEqualTo("scheduleDayId", scheduleDayId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val zones = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        Zone(
                            id            = doc.id,
                            name          = doc.getString("name") ?: "",
                            scheduleDayId = doc.getString("scheduleDayId") ?: "",
                            centerLat     = doc.getDouble("centerLat") ?: 0.0,
                            centerLng     = doc.getDouble("centerLng") ?: 0.0,
                            radiusMeters  = doc.getDouble("radiusMeters") ?: 1000.0,
                            createdBy     = doc.getString("createdBy") ?: ""
                        )
                    } catch (e: Exception) { null }
                } ?: emptyList()
                trySend(zones)
            }
        awaitClose { listener.remove() }
    }.catch { emit(emptyList()) }

    suspend fun addZone(zone: Zone): String {
        val data = mapOf(
            "name"          to zone.name,
            "scheduleDayId" to zone.scheduleDayId,
            "centerLat"     to zone.centerLat,
            "centerLng"     to zone.centerLng,
            "radiusMeters"  to zone.radiusMeters,
            "createdBy"     to zone.createdBy
        )
        val ref = firestore.collection("route_zones").add(data).await()
        return ref.id
    }

    suspend fun deleteZone(zoneId: String) {
        firestore.collection("route_zones").document(zoneId).delete().await()
    }

    // =====================================================================
    // Route Calculation
    // =====================================================================

    /**
     * Fetches ALL pending "Regular Pickup" waste reports in real-time from the
     * Firestore SERVER (bypassing local cache) and filters them to only those
     * inside the given circular zone.
     *
     * KEY FIX: Source.SERVER forces Firestore to go to the network, ensuring
     * waste reports submitted after the app launched (or after a previous route
     * was calculated) are always included. Without this, Firestore's offline
     * persistence cache can serve stale data that misses new reports.
     *
     * @return Optimally ordered list of [RouteStop] objects via OSRM trip API.
     *         Falls back to unordered list if OSRM is unreachable.
     */
    suspend fun calculateRouteForZone(zone: Zone): List<RouteStop> {
        // ---- STEP 1: Force a fresh server read — never use cached data for routes ----
        // Source.SERVER skips the local offline cache entirely.
        // This is the fix for new reports not appearing in loaded routes.
        val snapshot = firestore.collection("waste_reports")
            .whereEqualTo("status", "pending")
            .whereEqualTo("reportType", "Regular Pickup")
            .get(Source.SERVER)   // <-- THE FIX: always fetch from server, not cache
            .await()

        // ---- STEP 2: Map Firestore documents to RouteStop objects ----
        val allRegularPickups = snapshot.documents.mapNotNull { doc ->
            try {
                val geoPoint = doc.getGeoPoint("location") ?: return@mapNotNull null
                RouteStop(
                    reportId    = doc.id,
                    location    = geoPoint,
                    streetName  = doc.getString("streetName") ?: "Unknown Street",
                    category    = doc.getString("category") ?: "Unknown",
                    isCollected = false
                )
            } catch (e: Exception) { null }
        }

        // ---- STEP 3: Filter to stops inside the zone using Haversine distance ----
        val stopsInZone = allRegularPickups.filter { stop ->
            haversineDistanceMeters(
                lat1 = zone.centerLat,
                lng1 = zone.centerLng,
                lat2 = stop.location.latitude,
                lng2 = stop.location.longitude
            ) <= zone.radiusMeters
        }

        // If there's only 1 stop (or none), OSRM trip routing isn't needed
        if (stopsInZone.size < 2) return stopsInZone

        // ---- STEP 4: Build OSRM coordinate string (longitude FIRST, then latitude) ----
        val coordinatesString = stopsInZone.joinToString(";") { stop ->
            "${stop.location.longitude},${stop.location.latitude}"
        }

        // ---- STEP 5: Ask OSRM for the optimal visit order ----
        return try {
            val osrmResponse = osrmService.getOptimisedTrip(coordinatesString)
            if (osrmResponse.code == "Ok" && osrmResponse.waypoints.size == stopsInZone.size) {
                // waypoints[i].waypointIndex = the position this stop should be visited
                // Sort by waypointIndex to get the optimal visit sequence
                val indexed = osrmResponse.waypoints.mapIndexed { inputIndex, waypoint ->
                    waypoint.waypointIndex to stopsInZone[inputIndex]
                }
                indexed.sortedBy { (visitOrder, _) -> visitOrder }.map { (_, stop) -> stop }
            } else {
                stopsInZone // OSRM returned unexpected data — use original order
            }
        } catch (e: Exception) {
            // Network issue contacting OSRM — fall back to unoptimised order
            // The route still works, just not optimally ordered
            stopsInZone
        }
    }

    /**
     * Marks a waste report as "dismissed" during an active route.
     * Identical to dismissReport — removes the pin from the live map.
     */
    suspend fun collectStop(reportId: String) {
        firestore.collection("waste_reports")
            .document(reportId)
            .update("status", "dismissed")
            .await()
    }

    // =====================================================================
    // UTILITY: Haversine distance formula
    // =====================================================================

    /**
     * Calculates the straight-line distance between two GPS points in metres,
     * accounting for Earth's curvature. Used for zone-containment checks.
     */
    private fun haversineDistanceMeters(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2).pow(2)
        return earthRadiusMeters * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}