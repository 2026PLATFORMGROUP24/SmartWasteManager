package com.platform.smartwastemanager.features.map.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.platform.smartwastemanager.features.home.data.ScheduleRepository
import com.platform.smartwastemanager.features.home.domain.CollectionDay
import com.platform.smartwastemanager.features.map.data.MapRepository
import com.platform.smartwastemanager.features.map.domain.RouteStop
import com.platform.smartwastemanager.features.map.domain.Zone
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// =============================================================================
// UI States — Zone screens
// =============================================================================

sealed class ZoneListUiState {
    object Loading : ZoneListUiState()
    data class Success(val zones: List<Zone>) : ZoneListUiState()
    data class Error(val message: String) : ZoneListUiState()
}

sealed class AllZonesUiState {
    object Loading : AllZonesUiState()
    data class Success(val zones: List<Zone>) : AllZonesUiState()
    data class Error(val message: String) : AllZonesUiState()
}

sealed class RouteActionState {
    object Idle : RouteActionState()
    object Loading : RouteActionState()
    data class Success(val message: String) : RouteActionState()
    data class Error(val message: String) : RouteActionState()
}

// =============================================================================
// UI States — Active Route screen
// =============================================================================

sealed class ActiveRouteUiState {
    object Idle : ActiveRouteUiState()
    object Calculating : ActiveRouteUiState()

    /**
     * Route calculated and ready to start.
     *
     * @param stops        Stops in optimised visit order.
     * @param roadPolyline LatLng points tracing the actual roads between all stops.
     *                     Empty if OSRM geometry was unavailable (falls back to straight lines).
     */
    data class Ready(
        val stops: List<RouteStop>,
        val roadPolyline: List<LatLng> = emptyList()
    ) : ActiveRouteUiState()

    /**
     * Driver is actively working through the route.
     *
     * @param stops              Full ordered stop list.
     * @param currentStopIndex   Index of the stop the driver must visit next.
     * @param roadPolyline       Full road-following polyline for the entire route.
     * @param directions         Turn-by-turn instruction strings to the current stop.
     * @param directionsLoading  True while the directions network request is in-flight.
     */
    data class InProgress(
        val stops: List<RouteStop>,
        val currentStopIndex: Int,
        val roadPolyline: List<LatLng> = emptyList(),
        val directions: List<String> = emptyList(),
        val directionsLoading: Boolean = false
    ) : ActiveRouteUiState()

    object Completed : ActiveRouteUiState()
    data class Error(val message: String) : ActiveRouteUiState()
}

// =============================================================================
// RouteViewModel
// =============================================================================

/**
 * Manages:
 *   1. All global zones (zone management + picker screens).
 *   2. Zones assigned to a specific schedule day.
 *   3. Assigning / unassigning zones to a schedule day.
 *   4. Creating / deleting global zones.
 *   5. Calculating the optimised collection route (stops + road polyline).
 *   6. Driving the active route: collecting stops + fetching directions.
 */
class RouteViewModel(
    private val mapRepository: MapRepository,
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {

    // ---- All global zones ----
    private val _allZonesState = MutableStateFlow<AllZonesUiState>(AllZonesUiState.Loading)
    val allZonesState: StateFlow<AllZonesUiState> = _allZonesState.asStateFlow()

    // ---- Zones assigned to current schedule day ----
    private val _zoneListUiState = MutableStateFlow<ZoneListUiState>(ZoneListUiState.Loading)
    val zoneListUiState: StateFlow<ZoneListUiState> = _zoneListUiState.asStateFlow()

    // ---- Action feedback (add/delete/assign/unassign) ----
    private val _actionState = MutableStateFlow<RouteActionState>(RouteActionState.Idle)
    val actionState: StateFlow<RouteActionState> = _actionState.asStateFlow()

    // ---- Active route ----
    private val _activeRouteState = MutableStateFlow<ActiveRouteUiState>(ActiveRouteUiState.Idle)
    val activeRouteState: StateFlow<ActiveRouteUiState> = _activeRouteState.asStateFlow()

    // The schedule currently open in ZoneListScreen
    private var currentSchedule: CollectionDay? = null

    private var allZonesJob: Job? = null

    // =========================================================================
    // All-Zones (Global Zone Management)
    // =========================================================================

    fun loadAllZones() {
        allZonesJob?.cancel()
        allZonesJob = viewModelScope.launch {
            _allZonesState.value = AllZonesUiState.Loading
            mapRepository.getAllZones().collect { zones ->
                _allZonesState.value = AllZonesUiState.Success(zones)
            }
        }
    }

    fun addGlobalZone(
        name: String,
        centerLat: Double,
        centerLng: Double,
        radiusMeters: Double,
        driverUid: String
    ) {
        if (name.isBlank()) {
            _actionState.value = RouteActionState.Error("Zone name cannot be empty.")
            return
        }
        if (centerLat == 0.0 && centerLng == 0.0) {
            _actionState.value = RouteActionState.Error("Please pick a location on the map.")
            return
        }
        viewModelScope.launch {
            _actionState.value = RouteActionState.Loading
            try {
                mapRepository.addZone(
                    Zone(
                        name         = name.trim(),
                        centerLat    = centerLat,
                        centerLng    = centerLng,
                        radiusMeters = radiusMeters,
                        createdBy    = driverUid
                    )
                )
                _actionState.value = RouteActionState.Success("Zone '$name' created.")
            } catch (e: Exception) {
                _actionState.value = RouteActionState.Error("Failed to create zone: ${e.message}")
            }
        }
    }

    fun deleteGlobalZone(zoneId: String) {
        viewModelScope.launch {
            _actionState.value = RouteActionState.Loading
            try {
                mapRepository.deleteZone(zoneId)
                _actionState.value = RouteActionState.Success("Zone deleted.")
            } catch (e: Exception) {
                _actionState.value = RouteActionState.Error("Failed to delete zone: ${e.message}")
            }
        }
    }

    // =========================================================================
    // Zone Assignment to a Schedule Day
    // =========================================================================

    fun loadZonesForSchedule(schedule: CollectionDay) {
        currentSchedule = schedule
        viewModelScope.launch {
            _zoneListUiState.value = ZoneListUiState.Loading
            val zones = mapRepository.getZonesById(schedule.zoneIds)
            _zoneListUiState.value = ZoneListUiState.Success(zones)
        }
    }

    fun assignZoneToSchedule(zone: Zone) {
        val schedule = currentSchedule ?: return
        if (zone.id in schedule.zoneIds) {
            _actionState.value = RouteActionState.Error("'${zone.name}' is already assigned to this day.")
            return
        }
        val updatedIds = schedule.zoneIds + zone.id
        currentSchedule = schedule.copy(zoneIds = updatedIds)
        viewModelScope.launch {
            _actionState.value = RouteActionState.Loading
            val result = scheduleRepository.updateScheduleZoneIds(schedule.id, updatedIds)
            if (result.isSuccess) {
                val zones = mapRepository.getZonesById(updatedIds)
                _zoneListUiState.value = ZoneListUiState.Success(zones)
                _actionState.value = RouteActionState.Success("'${zone.name}' assigned to ${schedule.dayOfWeek}.")
            } else {
                currentSchedule = schedule
                _actionState.value = RouteActionState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to assign zone."
                )
            }
        }
    }

    fun unassignZoneFromSchedule(zoneId: String) {
        val schedule = currentSchedule ?: return
        val updatedIds = schedule.zoneIds - zoneId
        currentSchedule = schedule.copy(zoneIds = updatedIds)
        viewModelScope.launch {
            _actionState.value = RouteActionState.Loading
            val result = scheduleRepository.updateScheduleZoneIds(schedule.id, updatedIds)
            if (result.isSuccess) {
                val zones = mapRepository.getZonesById(updatedIds)
                _zoneListUiState.value = ZoneListUiState.Success(zones)
                _actionState.value = RouteActionState.Success("Zone removed from ${schedule.dayOfWeek}.")
            } else {
                currentSchedule = schedule
                _actionState.value = RouteActionState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to remove zone."
                )
            }
        }
    }

    // =========================================================================
    // Active Route
    // =========================================================================

    /**
     * Calls OSRM to calculate the optimised route for [zone].
     * On success: state → Ready with stops AND road polyline.
     */
    fun loadRouteForZone(zone: Zone) {
        viewModelScope.launch {
            _activeRouteState.value = ActiveRouteUiState.Calculating
            try {
                val result = mapRepository.calculateRouteForZone(zone)
                _activeRouteState.value = if (result.stops.isEmpty()) {
                    ActiveRouteUiState.Error(
                        "No pending Regular Pickup reports found in this zone."
                    )
                } else {
                    ActiveRouteUiState.Ready(
                        stops        = result.stops,
                        roadPolyline = result.roadPolyline
                    )
                }
            } catch (e: Exception) {
                _activeRouteState.value = ActiveRouteUiState.Error(
                    "Could not calculate route: ${e.message}"
                )
            }
        }
    }

    /**
     * Transitions Ready → InProgress at stop 0, carrying the road polyline
     * forward into InProgress so the map can keep drawing the full road path.
     */
    fun startRoute(driverLat: Double, driverLng: Double) {
        val readyState = _activeRouteState.value as? ActiveRouteUiState.Ready ?: return
        _activeRouteState.value = ActiveRouteUiState.InProgress(
            stops             = readyState.stops,
            currentStopIndex  = 0,
            roadPolyline      = readyState.roadPolyline,
            directionsLoading = true
        )
        fetchDirectionsToStop(driverLat, driverLng, readyState.stops[0])
    }

    /**
     * Marks the current stop as collected, advances to the next stop,
     * keeps the road polyline, and fetches directions to the next stop.
     */
    fun collectCurrentStop(driverLat: Double, driverLng: Double) {
        val inProgress  = _activeRouteState.value as? ActiveRouteUiState.InProgress ?: return
        val currentStop = inProgress.stops[inProgress.currentStopIndex]

        viewModelScope.launch {
            try {
                mapRepository.collectStop(currentStop.reportId)

                val updatedStops = inProgress.stops.toMutableList().also {
                    it[inProgress.currentStopIndex] = currentStop.copy(isCollected = true)
                }

                val nextIndex = inProgress.currentStopIndex + 1

                if (nextIndex >= updatedStops.size) {
                    _activeRouteState.value = ActiveRouteUiState.Completed
                } else {
                    _activeRouteState.value = ActiveRouteUiState.InProgress(
                        stops             = updatedStops,
                        currentStopIndex  = nextIndex,
                        roadPolyline      = inProgress.roadPolyline, // keep the same road polyline
                        directionsLoading = true
                    )
                    fetchDirectionsToStop(driverLat, driverLng, updatedStops[nextIndex])
                }
            } catch (e: Exception) {
                _actionState.value = RouteActionState.Error(
                    "Could not record collection: ${e.message}"
                )
            }
        }
    }

    /**
     * Fetches turn-by-turn text directions from the driver's position to [stop].
     * Silently ignores failures — the UI shows an empty panel in that case.
     */
    private fun fetchDirectionsToStop(driverLat: Double, driverLng: Double, stop: RouteStop) {
        viewModelScope.launch {
            val directions = mapRepository.getDirectionsToStop(
                fromLat = driverLat, fromLng = driverLng,
                toLat   = stop.location.latitude, toLng = stop.location.longitude
            )
            val current = _activeRouteState.value as? ActiveRouteUiState.InProgress
                ?: return@launch
            _activeRouteState.value = current.copy(
                directions        = directions,
                directionsLoading = false
            )
        }
    }

    fun resetRoute() {
        _activeRouteState.value = ActiveRouteUiState.Idle
    }

    fun resetActionState() {
        _actionState.value = RouteActionState.Idle
    }

    companion object {
        fun factory(
            mapRepository: MapRepository,
            scheduleRepository: ScheduleRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    RouteViewModel(mapRepository, scheduleRepository) as T
            }
    }
}