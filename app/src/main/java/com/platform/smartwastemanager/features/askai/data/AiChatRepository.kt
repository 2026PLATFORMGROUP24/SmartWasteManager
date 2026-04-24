package com.platform.smartwastemanager.features.askai.data

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

/**
 * Handles all Firestore + Firebase Storage operations for the ai_chats collection.
 *
 * Storage path: ai_chat_images/{userId}/{chatId}.jpg
 * Firestore:    ai_chats/{chatId}
 */
class AiChatRepository {

    private val TAG = "AiChatRepository"
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val collection = firestore.collection("ai_chats")

    /**
     * Uploads the bitmap to Firebase Storage and returns its public download URL.
     */
    suspend fun uploadImage(userId: String, chatId: String, bitmap: Bitmap): String {
        val ref = storage.reference.child("ai_chat_images/$userId/$chatId.jpg")
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        val bytes = baos.toByteArray()
        ref.putBytes(bytes).await()
        return ref.downloadUrl.await().toString()
    }

    /**
     * Creates a new chat document in Firestore.
     * Returns the generated chatId.
     */
    suspend fun createChat(
        userId: String,
        imageUrl: String,
        wasteLabel: String
    ): Result<String> {
        return try {
            val docRef = collection.document()
            val data = mapOf(
                "chatId"      to docRef.id,
                "userId"      to userId,
                "imageUrl"    to imageUrl,
                "wasteLabel"  to wasteLabel,
                "messages"    to emptyList<Map<String, Any>>(),
                "promptCount" to 0,
                "createdAt"   to Timestamp.now()
            )
            docRef.set(data).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "createChat failed", e)
            Result.failure(e)
        }
    }

    /**
     * Appends a message to the chat and increments promptCount (only for user messages).
     */
    suspend fun addMessage(
        chatId: String,
        role: String,
        text: String,
        incrementPromptCount: Boolean
    ): Result<Unit> {
        return try {
            val chatRef = collection.document(chatId)
            firestore.runTransaction { tx ->
                val snap = tx.get(chatRef)
                @Suppress("UNCHECKED_CAST")
                val existing = snap.get("messages") as? List<Map<String, Any>> ?: emptyList()
                val newMsg = mapOf(
                    "role"      to role,
                    "text"      to text,
                    "timestamp" to Timestamp.now()
                )
                val updatedMessages = existing + newMsg
                val updates = mutableMapOf<String, Any>(
                    "messages" to updatedMessages
                )
                if (incrementPromptCount) {
                    val current = (snap.getLong("promptCount") ?: 0L).toInt()
                    updates["promptCount"] = current + 1
                }
                tx.update(chatRef, updates)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "addMessage failed", e)
            Result.failure(e)
        }
    }

    /**
     * Updates the wasteLabel field of an existing chat document.
     * Called when the user manually corrects the identified item name.
     */
    suspend fun updateWasteLabel(chatId: String, newLabel: String): Result<Unit> {
        return try {
            collection.document(chatId)
                .update("wasteLabel", newLabel)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateWasteLabel failed", e)
            Result.failure(e)
        }
    }

    /**
     */
    fun getChatsForUser(userId: String): Flow<List<AiChat>> = callbackFlow {
        val listener = collection
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "getChatsForUser error", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val chats = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val rawMsgs = doc.get("messages") as? List<Map<String, Any>> ?: emptyList()
                        val messages = rawMsgs.map { m ->
                            AiChatMessage(
                                role      = m["role"] as? String ?: "",
                                text      = m["text"] as? String ?: "",
                                timestamp = m["timestamp"] as? Timestamp ?: Timestamp.now()
                            )
                        }
                        AiChat(
                            chatId       = doc.getString("chatId") ?: doc.id,
                            userId       = doc.getString("userId") ?: "",
                            imageUrl     = doc.getString("imageUrl") ?: "",
                            wasteLabel   = doc.getString("wasteLabel") ?: "",
                            messages     = messages,
                            promptCount  = (doc.getLong("promptCount") ?: 0L).toInt(),
                            createdAt    = doc.getTimestamp("createdAt") ?: Timestamp.now()
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
                trySend(chats)
            }
        awaitClose { listener.remove() }
    }.catch { emit(emptyList()) }

    /**
     * Returns a single chat by ID as a one-shot Flow.
     */
    fun getChatById(chatId: String): Flow<AiChat?> = callbackFlow {
        val listener = collection.document(chatId)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                try {
                    @Suppress("UNCHECKED_CAST")
                    val rawMsgs = snap.get("messages") as? List<Map<String, Any>> ?: emptyList()
                    val messages = rawMsgs.map { m ->
                        AiChatMessage(
                            role      = m["role"] as? String ?: "",
                            text      = m["text"] as? String ?: "",
                            timestamp = m["timestamp"] as? Timestamp ?: Timestamp.now()
                        )
                    }
                    trySend(
                        AiChat(
                            chatId      = snap.getString("chatId") ?: snap.id,
                            userId      = snap.getString("userId") ?: "",
                            imageUrl    = snap.getString("imageUrl") ?: "",
                            wasteLabel  = snap.getString("wasteLabel") ?: "",
                            messages    = messages,
                            promptCount = (snap.getLong("promptCount") ?: 0L).toInt(),
                            createdAt   = snap.getTimestamp("createdAt") ?: Timestamp.now()
                        )
                    )
                } catch (e: Exception) {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }.catch { emit(null) }
}

