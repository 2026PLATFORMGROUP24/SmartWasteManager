package com.platform.smartwastemanager.features.map.data

import com.google.firebase.firestore.FieldPath
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
    // Zone CRUD — Zones are now GLOBAL (no scheduleDayId)
    // =========================================================================

    /**
     * Returns a real-time stream of ALL zones in the route_zones collection.
     * Used by ZonePickerScreen so drivers can see all available zones to assign.
     */
    fun getAllZones(): Flow<List<Zone>> = callbackFlow {
        val listener = firestore.collection("route_zones")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                trySend(parseZones(snapshot))
            }
        awaitClose { listener.remove() }
    }.catch { emit(emptyList()) }

    /**
     * Returns a real-time stream of ALL zones created by [driverUid].
     * Used by MapViewModel to show zone overlays on the main map screen.
     */
    fun getZonesForDriver(driverUid: String): Flow<List<Zone>> = callbackFlow {
        val listener = firestore.collection("route_zones")
            .whereEqualTo("createdBy", driverUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                trySend(parseZones(snapshot))
            }
        awaitClose { listener.remove() }
    }.catch { emit(emptyList()) }

    /**
     * Fetches a specific set of zones by their Firestore document IDs.
     * Used when loading the zones assigned to a particular schedule day.
     * Firestore's whereIn supports up to 30 items per query.
     */
    suspend fun getZonesById(zoneIds: List<String>): List<Zone> {
        if (zoneIds.isEmpty()) return emptyList()
        return try {
            // whereIn only supports up to 30 values; chunk if needed
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

    /** Shared Firestore → Zone mapping logic. */
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

    /**
     * Adds a new global zone. No scheduleDayId is stored — assignment to a
     * schedule day happens separately via ScheduleRepository.updateScheduleZoneIds().
     */
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

    /** Deletes a zone document. Note: does NOT remove the zoneId from any schedules. */
    suspend fun deleteZone(zoneId: String) {
        firestore.collection("route_zones").document(zoneId).delete().await()
    }

    // =========================================================================
    // Route Calculation
    // =========================================================================

    /**
     * Fetches all pending Regular Pickup reports inside [zone], then uses OSRM
     * to sort them into the most efficient visit order.
     */
    suspend fun calculateRouteForZone(zone: Zone): List<RouteStop> {
        val snapshot = firestore.collection("waste_reports")
            .whereEqualTo("status", "pending")
            .whereEqualTo("reportType", "Regular Pickup")
            .get(Source.SERVER)
            .await()

        val allPickups = snapshot.documents.mapNotNull { doc ->
            try {
                val gp = doc.getGeoPoint("location") ?: return@mapNotNull null
                RouteStop(
                    reportId   = doc.id,
                    location   = gp,
                    streetName = doc.getString("streetName") ?: "Unknown Street",
                    category   = doc.getString("category") ?: "Unknown",
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

        if (stopsInZone.size < 2) return stopsInZone

        val coords = stopsInZone.joinToString(";") {
            "${it.location.longitude},${it.location.latitude}"
        }

        return try {
            val resp = osrmService.getOptimisedTrip(coords)
            if (resp.code == "Ok" && resp.waypoints.size == stopsInZone.size) {
                stopsInZone.mapIndexed { i, stop -> resp.waypoints[i].waypointIndex to stop }
                    .sortedBy { it.first }.map { it.second }
            } else stopsInZone
        } catch (e: Exception) { stopsInZone }
    }

    /**
     * Fetches turn-by-turn driving directions from [fromLat]/[fromLng] to [toLat]/[toLng].
     * Returns a list of human-readable instruction strings, one per maneuver step.
     * Returns an empty list if the request fails — the UI handles this gracefully.
     */
    suspend fun getDirectionsToStop(
        fromLat: Double, fromLng: Double,
        toLat: Double,   toLng: Double
    ): List<String> {
        return try {
            // OSRM expects longitude,latitude order
            val coords = "$fromLng,$fromLat;$toLng,$toLat"
            val response = osrmService.getRoute(coords, steps = true)

            if (response.code != "Ok") return emptyList()

            val steps = response.routes.firstOrNull()?.legs?.firstOrNull()?.steps
                ?: return emptyList()

            steps.mapNotNull { step ->
                buildDirectionString(step)
            }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Converts an [OsrmStep] into a human-readable direction string.
     * Example output: "Turn right onto Oak Avenue (250 m)"
     */
    private fun buildDirectionString(step: OsrmStep): String? {
        val type     = step.maneuver.type
        val modifier = step.maneuver.modifier
        val road     = step.name
        val distM    = step.distance.toInt()
        val distStr  = if (distM >= 1000) "${"%.1f".format(distM / 1000.0)} km"
        else "$distM m"

        // Build the action phrase
        val action = when (type) {
            "depart"     -> "Head ${modifier.ifBlank { "forward" }}"
            "arrive"     -> return "🏁 Arrive at destination"
            "turn"       -> when (modifier) {
                "left"        -> "Turn left"
                "right"       -> "Turn right"
                "slight left" -> "Turn slight left"
                "slight right"-> "Turn slight right"
                "sharp left"  -> "Turn sharp left"
                "sharp right" -> "Turn sharp right"
                "uturn"       -> "Make a U-turn"
                else          -> "Continue"
            }
            "new name"   -> "Continue"
            "continue"   -> "Continue straight"
            "merge"      -> "Merge ${modifier.ifBlank { "" }}"
            "roundabout" -> "Enter the roundabout"
            "exit roundabout" -> "Exit the roundabout"
            "fork"       -> "Keep ${modifier.ifBlank { "straight" }} at the fork"
            else         -> "Continue"
        }

        return if (road.isNotBlank()) {
            "$action onto $road ($distStr)"
        } else {
            "$action ($distStr)"
        }
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