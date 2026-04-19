package com.platform.smartwastemanager.features.guide.domain

import com.google.firebase.Timestamp

/**
 * Content type for the recycling guide.
 */
enum class GuideContentType {
    MARKDOWN,        // Rich text formatted with Markdown
    YOUTUBE          // YouTube video ID (opens in app/browser)
}

data class RecyclingGuide(
    val id: String = "",
    val title: String = "",
    val contentType: String = GuideContentType.MARKDOWN.name,
    val contentMarkdown: String = "",
    val externalUrl: String = "",  // YouTube video ID
    val imageUrls: List<String> = emptyList(),
    val createdBy: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
) {
    fun getContentType(): GuideContentType =
        try {
            GuideContentType.valueOf(contentType)
        } catch (_: Exception) {
            GuideContentType.MARKDOWN
        }
}