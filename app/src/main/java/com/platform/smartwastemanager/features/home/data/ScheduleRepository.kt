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
 */
class ScheduleRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val schedulesCollection = firestore.collection(Constants.COLLECTION_SCHEDULES)

    /**
     * Returns a real-time stream of schedule entries.
     *
     * IMPORTANT: Never calls close(error) — doing so propagates the exception
     * to the coroutine collector and crashes the app. Instead, errors are
     * swallowed here and an empty list is emitted. The ViewModel's .catch{}
     * provides an additional safety layer.
     */
    fun getSchedules(): Flow<List<CollectionDay>> = callbackFlow<List<CollectionDay>> {
        val listenerRegistration = schedulesCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // DO NOT call close(error) — that kills the coroutine with an exception.
                    // Emit empty list and let the ViewModel show an error state instead.
                    trySend(emptyList<CollectionDay>())
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val schedules = snapshot.documents.mapNotNull { doc ->
                        try {
                            CollectionDay(
                                id = doc.id,
                                dayOfWeek = doc.getString("dayOfWeek") ?: "",
                                wasteCategory = doc.getString("wasteCategory") ?: "",
                                linkedGuideId = doc.getString("linkedGuideId"),
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
        // Final safety net — if the flow itself throws, emit empty list
        emit(emptyList())
    }

    suspend fun createSchedule(collectionDay: CollectionDay): Result<Unit> {
        return try {
            val docRef = schedulesCollection.document()
            docRef.set(mapOf(
                "id" to docRef.id,
                "dayOfWeek" to collectionDay.dayOfWeek,
                "wasteCategory" to collectionDay.wasteCategory,
                "linkedGuideId" to collectionDay.linkedGuideId,
                "createdBy" to collectionDay.createdBy,
                "updatedAt" to Timestamp.now()
            )).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateSchedule(collectionDay: CollectionDay): Result<Unit> {
        return try {
            schedulesCollection.document(collectionDay.id)
                .update(mapOf(
                    "dayOfWeek" to collectionDay.dayOfWeek,
                    "wasteCategory" to collectionDay.wasteCategory,
                    "linkedGuideId" to collectionDay.linkedGuideId,
                    "updatedAt" to Timestamp.now()
                )).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteSchedule(scheduleId: String): Result<Unit> {
        return try {
            schedulesCollection.document(scheduleId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}