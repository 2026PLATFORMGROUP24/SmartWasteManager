package com.platform.smartwastemanager.features.home.domain

import com.google.firebase.Timestamp

/**
 * Represents a collection schedule for a specific day within a specific ZONE.
 *
 * NEW ZONE-BASED MODEL:
 * - Each CollectionDay belongs to ONE zone
 * - Schedules are zone-specific (different zones have different schedules)
 * - When a driver taps a day, routes are calculated from collection points
 *   marked for this day within this zone
 *
 * Firestore schema: schedules/{docId}
 * {
 *   id: String,
 *   zoneId: String,                      // NEW: which zone this schedule belongs to
 *   dayOfWeek: String,                   // "Monday" … "Sunday"
 *   wasteCategories: List<String>,       // ["Recyclable", "Glass"]
 *   collectionTimeRange: String?,        // "07:00 – 12:00" or null
 *   linkedGuideId: String?,
 *   createdBy: String,
 *   updatedAt: Timestamp,
 *   isManuallyEnabled: Boolean,          // NEW: Driver override to enable user button
 *   isRouteCompleted: Boolean            // NEW: True if the driver completed the route for this day
 * }
 */
data class CollectionDay(
    val id: String = "",
    val zoneId: String = "",                           // NEW: zone association
    val dayOfWeek: String = "",
    val wasteCategories: List<String> = emptyList(),
    val collectionTimeRange: String? = null,
    val linkedGuideId: String? = null,
    val createdBy: String = "",
    val updatedAt: Timestamp = Timestamp.now(),
    val isManuallyEnabled: Boolean = false,            // NEW
    val isRouteCompleted: Boolean = false              // NEW
)