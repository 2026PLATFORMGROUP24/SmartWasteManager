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

    // =====================================================================
    // Map Pins
    // =====================================================================

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

    // =====================================================================
    // Zone CRUD (used by RouteViewModel)
    // =====================================================================

    fun getZonesForDay(scheduleDayId: String): Flow<List<Zone>> = callbackFlow {
        val listener = firestore.collection("route_zones")
            .whereEqualTo("scheduleDayId", scheduleDayId)
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

    /** Shared Firestore → Zone mapping logic. */
    private fun parseZones(
        snapshot: com.google.firebase.firestore.QuerySnapshot?
    ): List<Zone> =
        snapshot?.documents?.mapNotNull { doc ->
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

    suspend fun addZone(zone: Zone): String {
        val data = mapOf(
            "name"          to zone.name,
            "scheduleDayId" to zone.scheduleDayId,
            "centerLat"     to zone.centerLat,
            "centerLng"     to zone.centerLng,
            "radiusMeters"  to zone.radiusMeters,
            "createdBy"     to zone.createdBy
        )
        return firestore.collection("route_zones").add(data).await().id
    }

    suspend fun deleteZone(zoneId: String) {
        firestore.collection("route_zones").document(zoneId).delete().await()
    }

    // =====================================================================
    // Route Calculation
    // =====================================================================

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