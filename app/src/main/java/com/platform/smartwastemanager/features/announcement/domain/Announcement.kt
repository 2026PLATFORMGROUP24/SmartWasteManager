package com.platform.smartwastemanager.features.announcement.domain

import com.google.firebase.Timestamp

/**
 * Domain model for announcements.
 *
 * Firestore schema: announcements/{docId}
 * {
 *   id: String,
 *   title: String,
 *   message: String,
 *   createdBy: String,           // Driver UID
 *   createdAt: Timestamp
 * }
 */
data class Announcement(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val createdBy: String = "",
    val createdAt: Timestamp = Timestamp.now()
)