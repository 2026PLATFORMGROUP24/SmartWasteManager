package com.platform.smartwastemanager.features.aiassist.domain

import com.google.firebase.Timestamp

/**
 * A single Ask AI session entry stored in Firestore.
 *
 * @property id        Auto-generated Firestore document ID.
 * @property imageUrl  Firebase Storage download URL of the scanned image (empty if no image).
 * @property labels    The user-confirmed labels for the scanned waste item.
 * @property prompt    The question the user asked about recycling.
 * @property response  The Gemini-generated recycling advice.
 * @property userId    UID of the user who created this entry.
 * @property timestamp When this entry was created.
 */
data class AiAssistEntry(
    val id: String = "",
    val imageUrl: String = "",
    val labels: List<String> = emptyList(),
    val prompt: String = "",
    val response: String = "",
    val userId: String = "",
    val timestamp: Timestamp = Timestamp.now()
)
