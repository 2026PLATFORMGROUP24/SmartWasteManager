package com.platform.smartwastemanager.features.home.domain

import com.google.firebase.Timestamp

/**
 * Represents one schedule entry stored in Firestore under schedules/{docId}.
 *
 * @property id            Firestore document ID.
 * @property dayOfWeek     E.g., "Monday", "Tuesday", ..., "Sunday".
 * @property wasteCategory The type of waste collected on this day.
 * @property linkedGuideId Optional — the Firestore ID of a recycling guide linked to this day.
 *                         If null, tapping the card shows a "No guide available" message.
 * @property createdBy     UID of the driver who created this entry.
 * @property updatedAt     Timestamp of the most recent update.
 */
data class CollectionDay(
    val id: String = "",
    val dayOfWeek: String = "",
    val wasteCategory: String = "",
    val linkedGuideId: String? = null,
    val createdBy: String = "",
    val updatedAt: Timestamp = Timestamp.now()
)