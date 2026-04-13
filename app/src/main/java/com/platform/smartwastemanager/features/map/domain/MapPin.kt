package com.platform.smartwastemanager.features.map.domain

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint

/**
 * A lightweight model used by the map screen.
 * Each MapPin represents a pending waste report shown as a red marker.
 *
 * @property reportId    The Firestore document ID of the original waste report.
 * @property location    GPS coordinates for placing the marker on the map.
 * @property category    Waste category shown in the info window.
 * @property streetName  Street name shown in the info window.
 * @property timestamp   Report time shown in the info window.
 */
data class MapPin(
    val reportId: String = "",
    val location: GeoPoint = GeoPoint(0.0, 0.0),
    val category: String = "",
    val streetName: String = "",
    val timestamp: Timestamp = Timestamp.now()
)