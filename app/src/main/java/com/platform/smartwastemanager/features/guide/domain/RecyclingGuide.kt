package com.platform.smartwastemanager.features.guide.domain

import com.google.firebase.Timestamp

/**
 * Content type for the recycling guide.
 */
enum class GuideContentType {
    MARKDOWN,        // Rich text formatted with Markdown
    YOUTUBE,         // YouTube video ID (embedded player)
    PDF              // PDF file URL from Firebase Storage
}

fun isValidYoutubeVideoId(videoId: String): Boolean =
    videoId.matches(Regex("^[A-Za-z0-9_-]{11}$"))

/**
 * Represents one recycling guide stored in Firestore under recycling_guides/{docId}.
 *
 * @property id              Firestore document ID.
 * @property title           Short title shown in the guide list.
 * @property contentType     Type of content (markdown, youtube, pdf).
 * @property contentMarkdown Full blog-post content written in Markdown (used if contentType = MARKDOWN).
 * @property externalUrl     External resource URL:
 *                           - YouTube: video ID (e.g. "dQw4w9WgXcQ")
 *                           - PDF: Firebase Storage download URL
 * @property imageUrls       List of Firebase Storage URLs for images (only used with Markdown content).
 * @property createdBy       UID of the driver who created the guide.
 * @property createdAt       When the guide was first created.
 * @property updatedAt       When the guide was last edited.
 */
data class RecyclingGuide(
    val id: String = "",
    val title: String = "",
    val contentType: String = GuideContentType.MARKDOWN.name,  // Store as String for Firestore compatibility
    val contentMarkdown: String = "",
    val externalUrl: String = "",
    val imageUrls: List<String> = emptyList(),
    val createdBy: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
) {
    /**
     * Helper to get the enum value safely.
     */
    fun getContentType(): GuideContentType =
        try {
            GuideContentType.valueOf(contentType)
        } catch (_: Exception) {
            GuideContentType.MARKDOWN
        }
}
