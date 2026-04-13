package com.platform.smartwastemanager.features.map.data

import com.platform.smartwastemanager.features.map.domain.MapPin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Queries Firestore for pending reports to display as map pins.
 * STUB — full implementation comes in Phase 4.
 */
class MapRepository {

    /** Returns a live stream of MapPin objects for all pending reports. */
    fun getPendingMapPins(): Flow<List<MapPin>> = flow {
        // TODO (Phase 4): Query waste_reports where status == "pending", map to MapPin
        emit(emptyList())
    }
}