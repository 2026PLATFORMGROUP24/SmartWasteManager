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
 * Handles all Firestore CRUD operations for the schedules collection.
 * Now also reads/writes the zoneIds field on each schedule document.
 */
class ScheduleRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val schedulesCollection = firestore.collection(Constants.COLLECTION_SCHEDULES)

    /**
     * Returns a real-time stream of all schedule entries, ordered by day of week.
     * Never calls close(error) — errors emit an empty list instead (Rule 4).
     */
    fun getSchedules(): Flow<List<CollectionDay>> = callbackFlow<List<CollectionDay>> {
        val listenerRegistration = schedulesCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList<CollectionDay>())
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val schedules = snapshot.documents.mapNotNull { doc ->
                        try {
                            CollectionDay(
                                id = doc.id,
                                dayOfWeek = doc.getString("dayOfWeek") ?: "",
                                wasteCategories = (doc.get("wasteCategories") as? List<*>)
                                    ?.filterIsInstance<String>() ?: emptyList(),
                                collectionTimeRange = doc.getString("collectionTimeRange"),
                                linkedGuideId = doc.getString("linkedGuideId"),
                                // Read assigned zone IDs — default to empty list if field missing
                                zoneIds = (doc.get("zoneIds") as? List<*>)
                                    ?.filterIsInstance<String>() ?: emptyList(),
                                createdBy = doc.getString("createdBy") ?: "",
                                updatedAt = doc.getTimestamp("updatedAt") ?: Timestamp.now()
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }.sortedBy { Constants.DAYS_OF_WEEK.indexOf(it.dayOfWeek) }

                    trySend(schedules)
                }
            }

        awaitClose { listenerRegistration.remove() }
    }.catch {
        emit(emptyList())
    }

    /** Creates a new schedule entry including the zoneIds list. */
    suspend fun createSchedule(collectionDay: CollectionDay): Result<Unit> {
        return try {
            val docRef = schedulesCollection.document()
            docRef.set(
                mapOf(
                    "id" to docRef.id,
                    "dayOfWeek" to collectionDay.dayOfWeek,
                    "wasteCategories" to collectionDay.wasteCategories,
                    "collectionTimeRange" to collectionDay.collectionTimeRange,
                    "linkedGuideId" to collectionDay.linkedGuideId,
                    "zoneIds" to collectionDay.zoneIds,
                    "createdBy" to collectionDay.createdBy,
                    "updatedAt" to Timestamp.now()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Updates an existing schedule entry including the zoneIds list. */
    suspend fun updateSchedule(collectionDay: CollectionDay): Result<Unit> {
        return try {
            schedulesCollection.document(collectionDay.id)
                .update(
                    mapOf(
                        "dayOfWeek" to collectionDay.dayOfWeek,
                        "wasteCategories" to collectionDay.wasteCategories,
                        "collectionTimeRange" to collectionDay.collectionTimeRange,
                        "linkedGuideId" to collectionDay.linkedGuideId,
                        "zoneIds" to collectionDay.zoneIds,
                        "updatedAt" to Timestamp.now()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates ONLY the zoneIds field on a schedule document.
     * Used when a driver assigns/unassigns zones without touching other fields.
     */
    suspend fun updateScheduleZoneIds(scheduleId: String, zoneIds: List<String>): Result<Unit> {
        return try {
            schedulesCollection.document(scheduleId)
                .update(
                    mapOf(
                        "zoneIds" to zoneIds,
                        "updatedAt" to Timestamp.now()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Deletes a schedule entry by its Firestore document ID. */
    suspend fun deleteSchedule(scheduleId: String): Result<Unit> {
        return try {
            schedulesCollection.document(scheduleId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}