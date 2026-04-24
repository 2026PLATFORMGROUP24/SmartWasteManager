package com.platform.smartwastemanager.features.askai.data

import com.google.firebase.Timestamp

/**
 * Represents a single message in an AI chat session.
 * role = "user" | "ai"
 */
data class AiChatMessage(
    val role: String = "",
    val text: String = "",
    val timestamp: Timestamp = Timestamp.now()
)

/**
 * Represents one AI chat session for a specific waste item scan.
 *
 * promptCount tracks TOTAL user prompts sent (including the initial classification prompt).
 * When promptCount >= MAX_PROMPTS (5) the chat is closed — no more prompts allowed.
 *
 * imageUrl is the Firebase Storage download URL for the captured item image.
 */
data class AiChat(
    val chatId: String = "",
    val userId: String = "",
    val imageUrl: String = "",
    val wasteLabel: String = "",
    val messages: List<AiChatMessage> = emptyList(),
    val promptCount: Int = 0,
    val createdAt: Timestamp = Timestamp.now()
) {
    companion object {
        const val MAX_PROMPTS = 5
    }

    val isClosed: Boolean get() = promptCount >= MAX_PROMPTS
}

