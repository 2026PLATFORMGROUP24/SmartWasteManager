package com.platform.smartwastemanager.features.askai.data

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.*
import com.platform.smartwastemanager.core.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for interacting with the Google Generative AI (Gemini).
 */
class AiRepository {

    private val TAG = "AiRepository"

    private val generativeModel = GenerativeModel(
        modelName = Constants.GEMINI_MODEL_NAME,
        apiKey = Constants.GEMINI_API_KEY
    )

    /**
     * Sends a prompt to Gemini and returns the response.
     * Includes the identified waste label for context.
     */
    suspend fun getAiResponse(label: String, userPrompt: String): String = withContext(Dispatchers.IO) {
        try {
            val fullPrompt = "I have identified this waste item as: $label. \nUser question: $userPrompt"
            val response = generativeModel.generateContent(fullPrompt)
            response.text ?: "AI could not generate a response."
        } catch (e: Exception) {
            Log.e(TAG, "AI request failed", e)
            handleAiError(e)
        }
    }

    /**
     * Translates SDK exceptions into user-friendly strings.
     */
    private fun handleAiError(e: Exception): String {
        val msg = e.message?.lowercase() ?: ""
        
        return when (e) {
            is ServerException -> {
                "The AI service is currently overloaded or experiencing high traffic. Please wait a moment and try again."
            }
            is PromptBlockedException -> {
                "The request was blocked by safety filters. Please try a different question."
            }
            is ResponseStoppedException -> {
                "The AI response was cut short. This can happen due to safety settings or length limits."
            }
            else -> {
                // Check for common error signatures in the message
                if (msg.contains("api key") || msg.contains("invalid") || msg.contains("401") || msg.contains("403")) {
                    "Authentication error: Please check if the Gemini API Key is valid and has not expired."
                } else if (msg.contains("traffic") || msg.contains("overloaded") || msg.contains("503") || msg.contains("busy")) {
                    "The AI is currently busy with high traffic. Please try again in a few seconds."
                } else if (msg.contains("internet") || msg.contains("network") || msg.contains("connect")) {
                    "Network error: Please check your internet connection."
                } else {
                    "AI Error: ${e.localizedMessage ?: "An unexpected error occurred. Please try again later."}"
                }
            }
        }
    }
}
