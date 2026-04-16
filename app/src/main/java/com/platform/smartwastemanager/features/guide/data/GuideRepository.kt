package com.platform.smartwastemanager.features.guide.data

import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.platform.smartwastemanager.features.guide.domain.RecyclingGuide
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Handles all CRUD operations for recycling guides in Firestore,
 * and image uploads / deletions in Firebase Storage.
 *
 * Firestore collection : recycling_guides/{docId}
 * Storage path         : guide_images/{guideId}/{uuid}.jpg
 */
class GuideRepository {

    private val firestore = Firebase.firestore
    private val storage   = Firebase.storage

    // =========================================================================
    // READ
    // =========================================================================

    /**
     * Returns a real-time stream of all guides, ordered newest-first.
     * Rule 4: errors send an empty list rather than crashing the app.
     */
    fun getGuides(): Flow<List<RecyclingGuide>> = callbackFlow {
        val listener = firestore.collection("recycling_guides")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val guides = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        RecyclingGuide(
                            id              = doc.id,
                            title           = doc.getString("title") ?: "",
                            contentMarkdown = doc.getString("contentMarkdown") ?: "",
                            imageUrls       = (doc.get("imageUrls") as? List<*>)
                                ?.filterIsInstance<String>() ?: emptyList(),
                            createdBy       = doc.getString("createdBy") ?: "",
                            createdAt       = doc.getTimestamp("createdAt") ?: Timestamp.now(),
                            updatedAt       = doc.getTimestamp("updatedAt") ?: Timestamp.now()
                        )
                    } catch (e: Exception) { null }
                } ?: emptyList()
                trySend(guides)
            }
        awaitClose { listener.remove() }
    }.catch { emit(emptyList()) }

    /** Fetches a single guide by its Firestore document ID. */
    suspend fun getGuideById(guideId: String): Result<RecyclingGuide> {
        return try {
            val doc = firestore.collection("recycling_guides").document(guideId).get().await()
            if (!doc.exists()) return Result.failure(Exception("Guide not found"))
            Result.success(
                RecyclingGuide(
                    id              = doc.id,
                    title           = doc.getString("title") ?: "",
                    contentMarkdown = doc.getString("contentMarkdown") ?: "",
                    imageUrls       = (doc.get("imageUrls") as? List<*>)
                        ?.filterIsInstance<String>() ?: emptyList(),
                    createdBy       = doc.getString("createdBy") ?: "",
                    createdAt       = doc.getTimestamp("createdAt") ?: Timestamp.now(),
                    updatedAt       = doc.getTimestamp("updatedAt") ?: Timestamp.now()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // WRITE
    // =========================================================================

    /**
     * Creates a new guide in Firestore.
     * Returns a Result containing the new auto-generated document ID on success.
     */
    suspend fun createGuide(guide: RecyclingGuide): Result<String> {
        return try {
            val data = hashMapOf(
                "title"           to guide.title,
                "contentMarkdown" to guide.contentMarkdown,
                "imageUrls"       to guide.imageUrls,
                "createdBy"       to guide.createdBy,
                "createdAt"       to Timestamp.now(),
                "updatedAt"       to Timestamp.now()
            )
            val docRef = firestore.collection("recycling_guides").add(data).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates an existing guide's mutable fields.
     * createdAt and createdBy are intentionally never changed.
     */
    suspend fun updateGuide(guide: RecyclingGuide): Result<Unit> {
        return try {
            val data = hashMapOf(
                "title"           to guide.title,
                "contentMarkdown" to guide.contentMarkdown,
                "imageUrls"       to guide.imageUrls,
                "updatedAt"       to Timestamp.now()
            )
            firestore.collection("recycling_guides").document(guide.id).update(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes a guide from Firestore AND all its images from Firebase Storage.
     * Storage errors are caught separately — a missing Storage folder will not
     * prevent the Firestore document from being deleted.
     */
    suspend fun deleteGuide(guideId: String): Result<Unit> {
        return try {
            // Step 1 — delete Storage images
            try {
                val folderRef = storage.reference.child("guide_images/$guideId")
                val items = folderRef.listAll().await()
                items.items.forEach { fileRef -> fileRef.delete().await() }
            } catch (storageError: Exception) {
                // Storage folder may not exist — that is fine, continue to Firestore deletion
            }

            // Step 2 — delete Firestore document
            firestore.collection("recycling_guides").document(guideId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // IMAGE UPLOAD
    // =========================================================================

    /**
     * Uploads one image [uri] to Firebase Storage at:
     *   guide_images/{guideId}/{uuid}.jpg
     *
     * Returns the public HTTPS download URL on success.
     */
    suspend fun uploadImage(guideId: String, uri: Uri): Result<String> {
        return try {
            val fileName = "${UUID.randomUUID()}.jpg"
            val ref = storage.reference.child("guide_images/$guideId/$fileName")
            ref.putFile(uri).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}