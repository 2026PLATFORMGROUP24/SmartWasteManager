package com.platform.smartwastemanager.features.guide.domain

import com.google.firebase.Timestamp

/**
 * Represents one recycling guide stored in Firestore under recycling_guides/{docId}.
 *
 * @property id              Firestore document ID.
 * @property title           Short title shown in the guide list.
 * @property contentMarkdown Full blog-post content written in Markdown.
 * @property imageUrls       List of Firebase Storage URLs for images in this guide.
 * @property createdBy       UID of the driver who created the guide.
 * @property createdAt       When the guide was first created.
 * @property updatedAt       When the guide was last edited.
 */
data class RecyclingGuide(
    val id: String = "",
    val title: String = "",
    val contentMarkdown: String = "",
    val imageUrls: List<String> = emptyList(),
    val createdBy: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
)