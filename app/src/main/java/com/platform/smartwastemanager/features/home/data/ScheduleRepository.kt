package com.platform.smartwastemanager.features.home.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.platform.smartwastemanager.core.util.Constants
import com.platform.smartwastemanager.features.home.domain.CollectionDay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await

/**
 * Handles all Firestore CRUD operations for zone-based collection schedules.
 */
class ScheduleRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val schedulesCollection = firestore.collection(Constants.COLLECTION_SCHEDULES)

    /**
     * Returns a real-time stream of ALL schedules for a specific zone.
     */
    fun getSchedulesForZone(zoneId: String): Flow<List<CollectionDay>> = callbackFlow {
        val listener = schedulesCollection
            .whereEqualTo("zoneId", zoneId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val schedules = snapshot.documents.mapNotNull { doc ->
                        try {
                            CollectionDay(
                                id = doc.id,
                                zoneId = doc.getString("zoneId") ?: "",
                                dayOfWeek = doc.getString("dayOfWeek") ?: "",
                                wasteCategories = (doc.get("wasteCategories") as? List<*>)
                                    ?.filterIsInstance<String>() ?: emptyList(),
                                collectionTimeRange = doc.getString("collectionTimeRange"),
                                linkedGuideId = doc.getString("linkedGuideId"),
                                createdBy = doc.getString("createdBy") ?: "",
                                updatedAt = doc.getTimestamp("updatedAt") ?: Timestamp.now()
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }.sortedBy { Constants.DAYS_OF_WEEK.indexOf(it.dayOfWeek) }

                    trySend(schedules)
                }
            }

        awaitClose { listener.remove() }
    }.catch { emit(emptyList()) }

    /**
     * Creates a new schedule entry for a specific zone.
     */
    suspend fun createSchedule(collectionDay: CollectionDay): Result<Unit> {
        return try {
            val docRef = schedulesCollection.document()
            docRef.set(
                mapOf(
                    "id" to docRef.id,
                    "zoneId" to collectionDay.zoneId,
                    "dayOfWeek" to collectionDay.dayOfWeek,
                    "wasteCategories" to collectionDay.wasteCategories,
                    "collectionTimeRange" to collectionDay.collectionTimeRange,
                    "linkedGuideId" to collectionDay.linkedGuideId,
                    "createdBy" to collectionDay.createdBy,
                    "updatedAt" to Timestamp.now()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates an existing schedule entry.
     */
    suspend fun updateSchedule(collectionDay: CollectionDay): Result<Unit> {
        return try {
            schedulesCollection.document(collectionDay.id)
                .update(
                    mapOf(
                        "zoneId" to collectionDay.zoneId,
                        "dayOfWeek" to collectionDay.dayOfWeek,
                        "wasteCategories" to collectionDay.wasteCategories,
                        "collectionTimeRange" to collectionDay.collectionTimeRange,
                        "linkedGuideId" to collectionDay.linkedGuideId,
                        "updatedAt" to Timestamp.now()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes a schedule entry.
     */
    suspend fun deleteSchedule(scheduleId: String): Result<Unit> {
        return try {
            schedulesCollection.document(scheduleId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}