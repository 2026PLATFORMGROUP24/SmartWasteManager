package com.platform.smartwastemanager.features.map.domain

import com.google.firebase.firestore.GeoPoint

/**
 * A single stop on an active collection route.
 *
 * Each stop corresponds to one "Regular Pickup" waste report that falls inside
 * the selected zone. Stops are ordered by OSRM's trip optimisation.
 *
 * @property reportId       Firestore document ID of the waste_reports document.
 * @property location       GPS coordinates of this stop.
 * @property streetName     Human-readable street name shown to the driver.
 * @property category       Waste category of this report.
 * @property isCollected    True once the driver taps "Collect" on this stop.
 */
data class RouteStop(
    val reportId: String = "",
    val location: GeoPoint = GeoPoint(0.0, 0.0),
    val streetName: String = "",
    val category: String = "",
    val isCollected: Boolean = false
)