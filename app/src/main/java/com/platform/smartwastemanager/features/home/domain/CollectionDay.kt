package com.platform.smartwastemanager.features.home.domain

import com.google.firebase.Timestamp

/**
 * Represents one schedule entry stored in Firestore under schedules/{docId}.
 *
 * @property id                 Firestore document ID.
 * @property dayOfWeek          E.g., "Monday", "Tuesday", ..., "Sunday".
 * @property wasteCategories    List of waste categories collected on this day.
 * @property collectionTimeRange Optional time range string, e.g. "07:00 – 12:00".
 * @property linkedGuideId      Optional Firestore ID of a recycling guide for this day.
 * @property zoneIds            List of global zone IDs assigned to this schedule day.
 *                              Drivers pick from all global zones and assign them here.
 * @property createdBy          UID of the driver who created this entry.
 * @property updatedAt          Timestamp of the most recent update.
 */
data class CollectionDay(
    val id: String = "",
    val dayOfWeek: String = "",
    val wasteCategories: List<String> = emptyList(),
    val collectionTimeRange: String? = null,
    val linkedGuideId: String? = null,
    val zoneIds: List<String> = emptyList(),
    val createdBy: String = "",
    val updatedAt: Timestamp = Timestamp.now()
)