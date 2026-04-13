package com.platform.smartwastemanager.features.home.domain

import com.google.firebase.Timestamp

/**
 * Represents one schedule entry stored in Firestore under schedules/{docId}.
 *
 * @property id                 Firestore document ID.
 * @property dayOfWeek          E.g., "Monday", "Tuesday", ..., "Sunday".
 * @property wasteCategories    List of waste categories collected on this day.
 *                              Replaces the old single wasteCategory field.
 *                              E.g. ["Recyclable", "Glass", "Paper"]
 * @property collectionTimeRange Optional time range string, e.g. "07:00 – 12:00".
 *                              Null means no time range has been set.
 * @property linkedGuideId      Optional Firestore ID of a recycling guide for this day.
 * @property createdBy          UID of the driver who created this entry.
 * @property updatedAt          Timestamp of the most recent update.
 */
data class CollectionDay(
    val id: String = "",
    val dayOfWeek: String = "",
    val wasteCategories: List<String> = emptyList(),   // NEW — replaces wasteCategory
    val collectionTimeRange: String? = null,            // NEW — optional time range
    val linkedGuideId: String? = null,
    val createdBy: String = "",
    val updatedAt: Timestamp = Timestamp.now()
)