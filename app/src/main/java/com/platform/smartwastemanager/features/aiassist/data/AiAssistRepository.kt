package com.platform.smartwastemanager.features.aiassist.data

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.platform.smartwastemanager.core.util.Constants
import com.platform.smartwastemanager.features.aiassist.domain.AiAssistEntry
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Handles backend operations for legacy Ask AI data:
 *  - Uploading the scanned image to Firebase Storage.
 *  - Returning an unavailable message for AI requests.
 *  - Saving completed entries to Firestore.
 *  - Streaming history entries from Firestore (newest first).
 *
 * The Ask AI feature has been disabled. History persistence remains for
 * backward compatibility with existing stored entries.
 */
class AiAssistRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val storage   = FirebaseStorage.getInstance()
    private val collection = firestore.collection(Constants.COLLECTION_AI_ASSIST_HISTORY)

    // -------------------------------------------------------------------------
    // Image upload
    // -------------------------------------------------------------------------

    /**
     * Compresses [bitmap] to JPEG and uploads it to Firebase Storage.
     * Returns the public download URL, or an empty string on failure.
     */
    suspend fun uploadImage(bitmap: Bitmap, userId: String): String {
        return try {
            val filename = "${UUID.randomUUID()}.jpg"
            val ref = storage.reference
                .child(Constants.STORAGE_AI_ASSIST_IMAGES)
                .child(userId)
                .child(filename)

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            val bytes = baos.toByteArray()

            ref.putBytes(bytes).await()
            ref.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.e("AiAssistRepository", "Image upload failed", e)
            ""
        }
    }

    // -------------------------------------------------------------------------
    // AI generation
    // -------------------------------------------------------------------------

    /**
     * Asks Gemini for recycling advice about the given waste item.
     *
     * The prompt is enriched with the confirmed labels so the model has context
     * even when [userPrompt] is a short question like "How do I recycle this?".
     *
     * @param labels     User-confirmed labels identifying the waste item.
     * @param userPrompt The free-text question the user typed (or a quick-prompt).
     * @return           AI-generated response text, or an error message.
     */
    suspend fun askGemini(@Suppress("UNUSED_PARAMETER") labels: List<String>, @Suppress("UNUSED_PARAMETER") userPrompt: String): String {
        return "AI features are currently unavailable."
    }

    // -------------------------------------------------------------------------
    // Firestore persistence
    // -------------------------------------------------------------------------

    /**
     * Saves a completed AI assist entry to Firestore.
     *
     * @return [Result.success] with the generated document ID, or [Result.failure].
     */
    suspend fun saveEntry(entry: AiAssistEntry): Result<String> {
        return try {
            val docRef = collection.document()
            val data = mapOf(
                "id"        to docRef.id,
                "imageUrl"  to entry.imageUrl,
                "labels"    to entry.labels,
                "prompt"    to entry.prompt,
                "response"  to entry.response,
                "userId"    to entry.userId,
                "timestamp" to entry.timestamp
            )
            docRef.set(data).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Returns a live stream of AI assist entries for [userId], newest first.
     * Firestore's offline persistence provides automatic caching.
     */
    fun getHistory(userId: String): Flow<List<AiAssistEntry>> = callbackFlow {
        val listener = collection
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                val entries = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        @Suppress("UNCHECKED_CAST")
                        AiAssistEntry(
                            id        = doc.getString("id") ?: doc.id,
                            imageUrl  = doc.getString("imageUrl") ?: "",
                            labels    = (doc.get("labels") as? List<String>) ?: emptyList(),
                            prompt    = doc.getString("prompt") ?: "",
                            response  = doc.getString("response") ?: "",
                            userId    = doc.getString("userId") ?: "",
                            timestamp = doc.getTimestamp("timestamp") ?: Timestamp.now()
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
                trySend(entries)
            }
        awaitClose { listener.remove() }
    }.catch { emit(emptyList()) }
}
