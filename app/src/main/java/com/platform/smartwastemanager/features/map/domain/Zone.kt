package com.platform.smartwastemanager.features.map.domain

/**
 * Represents a geographic zone (circular area) that a driver assigns to a schedule day.
 *
 * A zone is stored in Firestore under:
 *   route_zones/{zoneId}
 *
 * @property id            Firestore document ID (auto-generated on creation).
 * @property name          Human-readable name the driver gives the zone, e.g. "North Sector".
 * @property scheduleDayId The Firestore document ID of the CollectionDay this zone belongs to.
 * @property centerLat     Latitude of the zone's centre point.
 * @property centerLng     Longitude of the zone's centre point.
 * @property radiusMeters  Radius of the circular zone in metres.
 * @property createdBy     UID of the driver who created this zone.
 */
data class Zone(
    val id: String = "",
    val name: String = "",
    val scheduleDayId: String = "",
    val centerLat: Double = 0.0,
    val centerLng: Double = 0.0,
    val radiusMeters: Double = 1000.0,
    val createdBy: String = ""
)