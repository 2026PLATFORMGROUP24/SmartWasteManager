package com.platform.smartwastemanager.features.map.domain

/**
 * Represents a global geographic zone (circular area).
 *
 * Zones are created globally — not tied to a schedule day at creation time.
 * A CollectionDay stores a list of zone IDs (zoneIds) referencing whichever
 * global zones have been assigned to that day.
 *
 * Stored in Firestore under: route_zones/{zoneId}
 *
 * @property id            Firestore document ID (auto-generated on creation).
 * @property name          Human-readable name, e.g. "North Sector".
 * @property centerLat     Latitude of the zone centre.
 * @property centerLng     Longitude of the zone centre.
 * @property radiusMeters  Radius of the circular zone in metres.
 * @property createdBy     UID of the driver who created this zone.
 */
data class Zone(
    val id: String = "",
    val name: String = "",
    val centerLat: Double = 0.0,
    val centerLng: Double = 0.0,
    val radiusMeters: Double = 1000.0,
    val createdBy: String = ""
)