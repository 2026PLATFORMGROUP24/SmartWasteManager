package com.platform.smartwastemanager.features.collectionpoint.domain

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint

/**
 * Represents a user-defined collection point.
 *
 * Firestore schema: collection_points/{docId}
 * {
 *   id: String,
 *   userId: String,              // Owner of this collection point
 *   name: String,                // User-friendly label e.g. "Home", "Office"
 *   location: GeoPoint,
 *   streetName: String,
 *   zoneId: String,              // The zone this point belongs to (assigned by system based on location)
 *   markedForCollectionDays: List<String>,  // ScheduleDay IDs where this point is marked for collection
 *   createdAt: Timestamp
 * }
 */
data class CollectionPoint(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val location: GeoPoint = GeoPoint(0.0, 0.0),
    val streetName: String = "",
    val zoneId: String = "",                          // Which zone this point is in
    val markedForCollectionDays: List<String> = emptyList(),  // Days marked for collection
    val createdAt: Timestamp = Timestamp.now()
)