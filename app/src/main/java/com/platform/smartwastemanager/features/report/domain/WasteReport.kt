package com.platform.smartwastemanager.features.report.domain

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint

/**
 * Represents one waste report stored in Firestore under waste_reports/{docId}.
 *
 * @property id          Firestore document ID.
 * @property category    The waste category (e.g., "Plastic", "Organic").
 * @property reportType  Either "Regular Pickup" or "Overflowing Bin".
 * @property location    GPS coordinates as a Firestore GeoPoint.
 * @property streetName  Human-readable street name from reverse geocoding.
 * @property reportedBy  UID of the user who submitted the report.
 * @property timestamp   When the report was created.
 * @property status      Either "pending" (visible on map) or "dismissed" (hidden).
 */
data class WasteReport(
    val id: String = "",
    val category: String = WasteCategory.MIXED_WASTE.displayName,
    val reportType: String = ReportType.REGULAR_PICKUP.displayName,
    val location: GeoPoint = GeoPoint(0.0, 0.0),
    val streetName: String = "",
    val reportedBy: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val status: String = "pending"
)