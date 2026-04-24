package com.platform.smartwastemanager.features.askai.data

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.vertexai.vertexAI
import com.google.firebase.vertexai.type.content
import com.google.firebase.vertexai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Repository that streams recycling-guide responses using Firebase Vertex AI (Gemini).
 *
 * NOTE: The original on-device MediaPipe / GGUF approach was abandoned because
 * MediaPipe's TFLite engine only supports its own proprietary model bundle format —
 * standard HuggingFace GGUF files are NOT compatible (causes SIGSEGV or
 * "model identifier ''" errors). No working Maven-published llama.cpp Android
 * binding was found that resolves from Maven Central or JitPack.
 *
 * Lifecycle:
 *  1. [initialize] is a no-op — retained for AskAiViewModel API compatibility.
 *  2. Call [getAiResponseStream] to stream tokens for each user prompt.
 *  3. Call [close] from ViewModel.onCleared() — also a no-op.
 */
class SmolLmRepository {

    private val TAG = "SmolLmRepository"

    private val generativeModel by lazy {
        Firebase.vertexAI.generativeModel(
            // gemini-2.5-flash — recommended for new projects as of March 2026
            // (gemini-2.0-flash-001 restricted to existing customers from March 6 2026)
            modelName = "gemini-2.5-flash",
            generationConfig = generationConfig {
                temperature = 0.8f
                maxOutputTokens = 1024
            },
            systemInstruction = content {
                text(
                    """
                    You are a concise and practical recycling and waste disposal guide assistant.
                    Only answer questions related to waste disposal, recycling methods, environmental impact,
                    and upcycling ideas. If asked something unrelated, politely redirect to waste topics.
                    Keep answers brief, clear, and helpful. Use bullet points when listing steps.
                    """.trimIndent()
                )
            }
        )
    }

    /** Always ready — Vertex AI requires no local model or initialisation. */
    val isReady: Boolean get() = true

    /** No-op. Retained for AskAiViewModel API compatibility. */
    suspend fun initialize(context: Context, modelPath: String) {
        Log.d(TAG, "Vertex AI — no initialisation required (cloud inference)")
    }

    /**
     * Streams a recycling-guide response using Gemini via Firebase Vertex AI.
     * Each emitted string is a partial token chunk enabling a live typewriter effect.
     */
    fun getAiResponseStream(label: String, userPrompt: String): Flow<String> = flow {
        val userMessage = "I have identified this waste item as: $label.\n$userPrompt"
        Log.d(TAG, "Streaming response for label='$label'")
        val inputContent = content { text(userMessage) }
        val stream = generativeModel.generateContentStream(inputContent)
        stream.collect { response ->
            response.text?.let { token ->
                if (token.isNotEmpty()) emit(token)
            }
        }
    }

    /** No-op. Retained for ViewModel.onCleared() compatibility. */
    fun close() {
        Log.d(TAG, "Vertex AI — nothing to close")
    }
}
