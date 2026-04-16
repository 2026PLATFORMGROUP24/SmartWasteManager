package com.platform.smartwastemanager.features.announcement.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.platform.smartwastemanager.features.announcement.domain.Announcement
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await

/**
 * Handles all Firestore CRUD operations for the announcements collection.
 */
class AnnouncementRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val collection = firestore.collection("announcements")

    /**
     * Returns a real-time stream of all announcements, ordered by newest first.
     */
    fun getAnnouncements(): Flow<List<Announcement>> = callbackFlow {
        val listener = collection
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            Announcement(
                                id = doc.id,
                                title = doc.getString("title") ?: "",
                                message = doc.getString("message") ?: "",
                                createdBy = doc.getString("createdBy") ?: "",
                                createdAt = doc.getTimestamp("createdAt") ?: Timestamp.now()
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                    trySend(list)
                }
            }
        awaitClose { listener.remove() }
    }.catch { emit(emptyList()) }

    /**
     * Creates a new announcement.
     */
    suspend fun createAnnouncement(announcement: Announcement): Result<Unit> {
        return try {
            val docRef = collection.document()
            docRef.set(
                mapOf(
                    "id" to docRef.id,
                    "title" to announcement.title,
                    "message" to announcement.message,
                    "createdBy" to announcement.createdBy,
                    "createdAt" to Timestamp.now()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes an announcement by its Firestore document ID.
     */
    suspend fun deleteAnnouncement(announcementId: String): Result<Unit> {
        return try {
            collection.document(announcementId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}