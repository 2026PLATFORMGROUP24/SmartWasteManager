package com.platform.smartwastemanager.features.guide.data

import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.platform.smartwastemanager.features.guide.domain.GuideContentType
import com.platform.smartwastemanager.features.guide.domain.RecyclingGuide
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class GuideRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val storage   = FirebaseStorage.getInstance()
    private val auth      = FirebaseAuth.getInstance()

    private val guidesCollection = firestore.collection("recycling_guides")

    // =========================================================================
    // READ
    // =========================================================================

    fun getGuides(): Flow<List<RecyclingGuide>> = callbackFlow {
        val listener = guidesCollection
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
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
                            contentType     = doc.getString("contentType") ?: GuideContentType.MARKDOWN.name,
                            contentMarkdown = doc.getString("contentMarkdown") ?: "",
                            externalUrl     = doc.getString("externalUrl") ?: "",
                            imageUrls       = (doc.get("imageUrls") as? List<*>)
                                ?.filterIsInstance<String>() ?: emptyList(),
                            createdBy       = doc.getString("createdBy") ?: "",
                            createdAt       = doc.getTimestamp("createdAt") ?: Timestamp.now(),
                            updatedAt       = doc.getTimestamp("updatedAt") ?: Timestamp.now()
                        )
                    } catch (_: Exception) {
                        null
                    }
                } ?: emptyList()
                trySend(guides)
            }
        awaitClose { listener.remove() }
    }.catch { emit(emptyList()) }

    suspend fun getGuideById(guideId: String): Result<RecyclingGuide> = try {
        val doc = guidesCollection.document(guideId).get().await()
        if (!doc.exists()) {
            Result.failure(Exception("Guide not found"))
        } else {
            val guide = RecyclingGuide(
                id              = doc.id,
                title           = doc.getString("title") ?: "",
                contentType     = doc.getString("contentType") ?: GuideContentType.MARKDOWN.name,
                contentMarkdown = doc.getString("contentMarkdown") ?: "",
                externalUrl     = doc.getString("externalUrl") ?: "",
                imageUrls       = (doc.get("imageUrls") as? List<*>)
                    ?.filterIsInstance<String>() ?: emptyList(),
                createdBy       = doc.getString("createdBy") ?: "",
                createdAt       = doc.getTimestamp("createdAt") ?: Timestamp.now(),
                updatedAt       = doc.getTimestamp("updatedAt") ?: Timestamp.now()
            )
            Result.success(guide)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    // =========================================================================
    // CREATE
    // =========================================================================

    suspend fun createGuide(guide: RecyclingGuide): Result<String> = try {
        val driverUid = auth.currentUser?.uid
            ?: return Result.failure(Exception("Not signed in"))

        val docRef = guidesCollection.document()
        val now    = Timestamp.now()

        docRef.set(
            mapOf(
                "id"              to docRef.id,
                "title"           to guide.title,
                "contentType"     to guide.contentType,
                "contentMarkdown" to guide.contentMarkdown,
                "externalUrl"     to guide.externalUrl,
                "imageUrls"       to guide.imageUrls,
                "createdBy"       to driverUid,
                "createdAt"       to now,
                "updatedAt"       to now
            )
        ).await()

        Result.success(docRef.id)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // =========================================================================
    // UPDATE
    // =========================================================================

    suspend fun updateGuide(guide: RecyclingGuide): Result<Unit> = try {
        guidesCollection.document(guide.id)
            .update(
                mapOf(
                    "title"           to guide.title,
                    "contentType"     to guide.contentType,
                    "contentMarkdown" to guide.contentMarkdown,
                    "externalUrl"     to guide.externalUrl,
                    "imageUrls"       to guide.imageUrls,
                    "updatedAt"       to Timestamp.now()
                )
            ).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    suspend fun deleteGuide(guideId: String): Result<Unit> = try {
        guidesCollection.document(guideId).delete().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // =========================================================================
    // STORAGE — Images (for Markdown guides)
    // =========================================================================

    suspend fun uploadImage(guideId: String, imageUri: Uri): Result<String> = try {
        val fileName = UUID.randomUUID().toString() + ".jpg"
        val ref      = storage.reference.child("guide_images/$guideId/$fileName")
        ref.putFile(imageUri).await()
        val downloadUrl = ref.downloadUrl.await().toString()
        Result.success(downloadUrl)
    } catch (e: Exception) {
        Result.failure(e)
    }

}
