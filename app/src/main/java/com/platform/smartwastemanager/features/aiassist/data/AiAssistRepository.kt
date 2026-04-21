package com.platform.smartwastemanager.features.aiassist.data

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.platform.smartwastemanager.BuildConfig
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
 * Handles all backend operations for the Ask AI feature:
 *  - Uploading the scanned image to Firebase Storage.
 *  - Sending the labels + prompt to Gemini and returning the response.
 *  - Saving completed entries to Firestore.
 *  - Streaming history entries from Firestore (newest first).
 *
 * AI model: Gemini 1.5 Flash — 100% FREE via Google AI Studio.
 *   Get your free API key (no credit card required) at:
 *   https://aistudio.google.com/app/apikey
 *   Free tier limits: 1,500 requests/day, 15 requests/minute.
 *   Add your key to local.properties:  GEMINI_API_KEY=your_key_here
 */
class AiAssistRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val storage   = FirebaseStorage.getInstance()
    private val collection = firestore.collection(Constants.COLLECTION_AI_ASSIST_HISTORY)

    /** Generative model — lazily initialised so we don't crash if the key is blank.
     *  Uses Gemini 1.5 Flash which is FREE on the Google AI Studio free tier.
     *  Get your free key (no credit card) at https://aistudio.google.com/app/apikey */
    private val gemini: GenerativeModel? by lazy {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank()) null
        else GenerativeModel(modelName = "gemini-1.5-flash", apiKey = key)
    }

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
    suspend fun askGemini(labels: List<String>, userPrompt: String): String {
        val model = gemini
            ?: return "Gemini API key is not configured.\n\n" +
                "The AI feature uses the FREE Gemini 1.5 Flash API (no credit card required).\n\n" +
                "To set it up:\n" +
                "1. Visit https://aistudio.google.com/app/apikey\n" +
                "2. Sign in with your Google account and create a free API key.\n" +
                "3. Add the following line to your local.properties file:\n" +
                "   GEMINI_API_KEY=your_key_here\n" +
                "4. Rebuild the app."

        val labelString = labels.joinToString(", ")
        val fullPrompt = """
            You are a helpful waste management and recycling assistant.
            The user has scanned a waste item identified as: $labelString.
            User question: $userPrompt
            Please provide clear, practical recycling or disposal advice for this specific item.
            Keep the response concise and actionable.
        """.trimIndent()

        return try {
            val result = model.generateContent(fullPrompt)
            result.text ?: "No response received from AI."
        } catch (e: Exception) {
            "Error contacting AI: ${e.message}"
        }
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
