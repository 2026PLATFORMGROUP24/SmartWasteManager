package com.platform.smartwastemanager.features.map.data

import com.google.firebase.firestore.GeoPoint
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
 *   3. Zone CRUD — new for driver routes feature.
 *   4. Calculating an optimised collection route using OSRM — new.
 *   5. Collecting (dismissing) a report during an active route — new.
 */
class MapRepository {

    private val firestore = Firebase.firestore

    // ---- OSRM Retrofit client (free, no API key) ----
    private val json = Json {
        ignoreUnknownKeys = true  // Ignore any OSRM fields we don't model
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
                    // Rule 4: do NOT close with error
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
    // NEW: Zone CRUD
    // =====================================================================

    /**
     * Returns a real-time Flow of all zones associated with a specific schedule day.
     *
     * @param scheduleDayId The Firestore document ID of the CollectionDay.
     */
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

    /**
     * Saves a new zone to Firestore.
     * Returns the generated document ID on success, or throws on failure.
     */
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

    /**
     * Deletes a zone from Firestore by its document ID.
     */
    suspend fun deleteZone(zoneId: String) {
        firestore.collection("route_zones").document(zoneId).delete().await()
    }

    // =====================================================================
    // NEW: Route Calculation
    // =====================================================================

    /**
     * Loads all PENDING "Regular Pickup" waste reports that fall within the
     * given zone (circular area defined by centre + radius).
     *
     * We fetch all pending Regular Pickup reports from Firestore, then
     * filter client-side using the Haversine formula to check if each
     * report's location is within the zone radius.
     *
     * Firestore does not natively support radius queries on GeoPoints, so
     * client-side filtering is the correct approach here.
     *
     * @return A list of [RouteStop] objects ordered by OSRM's optimised trip route.
     *         Returns an empty list if no reports are found in the zone.
     * @throws Exception if the OSRM API call fails.
     */
    suspend fun calculateRouteForZone(zone: Zone): List<RouteStop> {
        // Step 1: Fetch all pending Regular Pickup reports from Firestore
        val snapshot = firestore.collection("waste_reports")
            .whereEqualTo("status", "pending")
            .whereEqualTo("reportType", "Regular Pickup")
            .get()
            .await()

        // Step 2: Map Firestore documents to RouteStop objects
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

        // Step 3: Filter to only those inside the zone radius using Haversine distance
        val stopsInZone = allRegularPickups.filter { stop ->
            haversineDistanceMeters(
                lat1 = zone.centerLat,
                lng1 = zone.centerLng,
                lat2 = stop.location.latitude,
                lng2 = stop.location.longitude
            ) <= zone.radiusMeters
        }

        // If fewer than 2 stops, no routing needed — return as-is
        if (stopsInZone.size < 2) return stopsInZone

        // Step 4: Build OSRM coordinates string — format is "lng,lat;lng,lat;..."
        // OSRM expects LONGITUDE first, then LATITUDE (the opposite of what you might expect)
        val coordinatesString = stopsInZone.joinToString(";") { stop ->
            "${stop.location.longitude},${stop.location.latitude}"
        }

        // Step 5: Call OSRM trip API to get the optimised visit order
        val osrmResponse = osrmService.getOptimisedTrip(coordinatesString)

        // Step 6: Re-order stops according to OSRM's optimised waypoint order
        return if (osrmResponse.code == "Ok" && osrmResponse.waypoints.size == stopsInZone.size) {
            // waypoints[i].waypointIndex tells us: "input stop i should be visited at position X"
            // We need to invert this: build a list sorted by waypointIndex
            val indexed = osrmResponse.waypoints.mapIndexed { inputIndex, waypoint ->
                waypoint.waypointIndex to stopsInZone[inputIndex]
            }
            indexed.sortedBy { (visitOrder, _) -> visitOrder }.map { (_, stop) -> stop }
        } else {
            // OSRM failed — return stops in original order as fallback
            stopsInZone
        }
    }

    /**
     * Marks a waste report as "dismissed" during an active route.
     * This is the same underlying operation as dismissReport — it removes
     * the pin from the map and marks it as collected.
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
     * Calculates the great-circle distance between two GPS coordinates in metres.
     *
     * The Haversine formula accounts for the curvature of the Earth, giving
     * accurate distance measurements for our zone-containment checks.
     *
     * @return Distance in metres between the two points.
     */
    private fun haversineDistanceMeters(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Double {
        val earthRadiusMeters = 6_371_000.0

        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) *
                cos(Math.toRadians(lat2)) *
                sin(dLng / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadiusMeters * c
    }
}