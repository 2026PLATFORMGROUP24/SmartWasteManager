package com.platform.smartwastemanager.features.map.data

import com.google.firebase.firestore.FirebaseFirestore
import com.platform.smartwastemanager.core.util.Constants
import com.platform.smartwastemanager.features.map.domain.MapPin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await

/**
 * Queries Firestore for pending reports to display as map pins.
 * Also handles driver dismiss operations.
 */
class MapRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val collection = firestore.collection(Constants.COLLECTION_WASTE_REPORTS)

    /**
     * Returns a live stream of [MapPin] objects for all "pending" reports.
     * Each Firestore document is mapped to a MapPin.
     *
     * IMPORTANT (Rule 4): Never call close(error) in callbackFlow.
     */
    fun getPendingMapPins(): Flow<List<MapPin>> = callbackFlow {
        val listener = collection
            .whereEqualTo(Constants.FIELD_STATUS, Constants.FIELD_STATUS_PENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val pins = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        MapPin(
                            reportId   = doc.getString("id") ?: doc.id,
                            location   = doc.getGeoPoint("location")
                                ?: com.google.firebase.firestore.GeoPoint(0.0, 0.0),
                            category   = doc.getString("category") ?: "",
                            streetName = doc.getString("streetName") ?: "",
                            timestamp  = doc.getTimestamp("timestamp")
                                ?: com.google.firebase.Timestamp.now()
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()

                trySend(pins)
            }

        awaitClose { listener.remove() }
    }.catch {
        emit(emptyList())
    }

    /**
     * Sets a report's status to "dismissed".
     * The Firestore snapshot listener will automatically remove it from
     * the pending pins list without any extra refresh needed.
     */
    suspend fun dismissReport(reportId: String): Result<Unit> {
        return try {
            collection.document(reportId)
                .update(Constants.FIELD_STATUS, Constants.FIELD_STATUS_DISMISSED)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}