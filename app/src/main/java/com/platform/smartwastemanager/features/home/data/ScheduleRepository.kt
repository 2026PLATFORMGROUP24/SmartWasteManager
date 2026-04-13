package com.platform.smartwastemanager.features.home.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.platform.smartwastemanager.core.util.Constants
import com.platform.smartwastemanager.features.home.domain.CollectionDay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Handles all Firestore CRUD operations for the schedules collection.
 *
 * Firestore offline persistence is enabled by default in the Firebase SDK —
 * schedule data will be available even without an internet connection and
 * will sync automatically when connectivity is restored.
 */
class ScheduleRepository {

    private val firestore = FirebaseFirestore.getInstance()

    // Reference to the schedules collection
    private val schedulesCollection = firestore.collection(Constants.COLLECTION_SCHEDULES)

    /**
     * Returns a real-time stream of all schedule entries ordered by day of week.
     *
     * callbackFlow converts Firestore's snapshot listener (callback-based) into
     * a Kotlin Flow that the ViewModel can collect.
     *
     * The flow stays active and emits a new list every time Firestore data changes.
     */
    fun getSchedules(): Flow<List<CollectionDay>> = callbackFlow {
        // Add a real-time listener to the schedules collection
        val listenerRegistration = schedulesCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Close the flow with the error if something goes wrong
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    // Map each Firestore document to a CollectionDay object
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
                            null // Skip any malformed documents
                        }
                    }
                    // Sort by day of week order (Mon → Sun)
                    val sorted = schedules.sortedBy { day ->
                        Constants.DAYS_OF_WEEK.indexOf(day.dayOfWeek)
                    }
                    trySend(sorted)
                }
            }

        // When the flow is cancelled (e.g. the screen leaves composition),
        // remove the Firestore listener to prevent memory leaks
        awaitClose { listenerRegistration.remove() }
    }

    /**
     * Creates a new schedule entry in Firestore.
     * Firestore auto-generates the document ID.
     */
    suspend fun createSchedule(collectionDay: CollectionDay): Result<Unit> {
        return try {
            val docRef = schedulesCollection.document() // auto-generate ID

            val data = mapOf(
                "id" to docRef.id,
                "dayOfWeek" to collectionDay.dayOfWeek,
                "wasteCategory" to collectionDay.wasteCategory,
                "linkedGuideId" to collectionDay.linkedGuideId,
                "createdBy" to collectionDay.createdBy,
                "updatedAt" to Timestamp.now()
            )

            docRef.set(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates an existing schedule entry.
     * Uses the CollectionDay's id to find the correct Firestore document.
     */
    suspend fun updateSchedule(collectionDay: CollectionDay): Result<Unit> {
        return try {
            val data = mapOf(
                "dayOfWeek" to collectionDay.dayOfWeek,
                "wasteCategory" to collectionDay.wasteCategory,
                "linkedGuideId" to collectionDay.linkedGuideId,
                "updatedAt" to Timestamp.now()
            )

            schedulesCollection
                .document(collectionDay.id)
                .update(data)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes a schedule entry by its Firestore document ID.
     */
    suspend fun deleteSchedule(scheduleId: String): Result<Unit> {
        return try {
            schedulesCollection
                .document(scheduleId)
                .delete()
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}