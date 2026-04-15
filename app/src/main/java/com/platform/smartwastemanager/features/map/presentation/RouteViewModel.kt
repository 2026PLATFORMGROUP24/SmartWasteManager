package com.platform.smartwastemanager.features.map.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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

// ─────────────────────────────────────────────────────────────────────────────
// UI State — Zone List screen
// ─────────────────────────────────────────────────────────────────────────────

/** State for the list of zones assigned to a schedule day. */
sealed class ZoneListUiState {
    object Loading : ZoneListUiState()
    data class Success(val zones: List<Zone>) : ZoneListUiState()
    data class Error(val message: String) : ZoneListUiState()
}

/** State for the global zone management screen (all zones). */
sealed class AllZonesUiState {
    object Loading : AllZonesUiState()
    data class Success(val zones: List<Zone>) : AllZonesUiState()
    data class Error(val message: String) : AllZonesUiState()
}

/** Feedback state for any user-triggered action (add/delete/assign). */
sealed class RouteActionState {
    object Idle : RouteActionState()
    object Loading : RouteActionState()
    data class Success(val message: String) : RouteActionState()
    data class Error(val message: String) : RouteActionState()
}

// ─────────────────────────────────────────────────────────────────────────────
// UI State — Active Route screen
// ─────────────────────────────────────────────────────────────────────────────

sealed class ActiveRouteUiState {
    object Idle : ActiveRouteUiState()
    object Calculating : ActiveRouteUiState()

    /** Route calculated — ready for the driver to start. */
    data class Ready(val stops: List<RouteStop>) : ActiveRouteUiState()

    /**
     * Driver is actively working through the route.
     *
     * @param stops              Full ordered stop list.
     * @param currentStopIndex   Index of the stop the driver must visit next.
     * @param directions         Turn-by-turn instruction strings to the current stop.
     *                           Empty list = directions not yet loaded or unavailable.
     * @param directionsLoading  True while the directions network request is in-flight.
     */
    data class InProgress(
        val stops: List<RouteStop>,
        val currentStopIndex: Int,
        val directions: List<String> = emptyList(),
        val directionsLoading: Boolean = false
    ) : ActiveRouteUiState()

    object Completed : ActiveRouteUiState()
    data class Error(val message: String) : ActiveRouteUiState()
}

// ─────────────────────────────────────────────────────────────────────────────
// RouteViewModel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * RouteViewModel manages:
 *   1. Listing all global zones (for zone management + picker screens).
 *   2. Loading zones assigned to a specific schedule day.
 *   3. Assigning/unassigning zones to a schedule day.
 *   4. Creating/deleting global zones.
 *   5. Calculating and driving the active collection route.
 *   6. Fetching turn-by-turn directions to each stop as the driver progresses.
 *
 * NOTE: Requires both MapRepository (zone/route data) and ScheduleRepository
 * (to update zoneIds on a schedule document).
 */
class RouteViewModel(
    private val mapRepository: MapRepository,
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {

    // ---- All global zones (zone management screen + picker) ----
    private val _allZonesState = MutableStateFlow<AllZonesUiState>(AllZonesUiState.Loading)
    val allZonesState: StateFlow<AllZonesUiState> = _allZonesState.asStateFlow()

    // ---- Zones assigned to the currently viewed schedule day ----
    private val _zoneListUiState = MutableStateFlow<ZoneListUiState>(ZoneListUiState.Loading)
    val zoneListUiState: StateFlow<ZoneListUiState> = _zoneListUiState.asStateFlow()

    // ---- Action feedback ----
    private val _actionState = MutableStateFlow<RouteActionState>(RouteActionState.Idle)
    val actionState: StateFlow<RouteActionState> = _actionState.asStateFlow()

    // ---- Active route ----
    private val _activeRouteState = MutableStateFlow<ActiveRouteUiState>(ActiveRouteUiState.Idle)
    val activeRouteState: StateFlow<ActiveRouteUiState> = _activeRouteState.asStateFlow()

    // The schedule currently being viewed in ZoneListScreen — kept in memory so
    // assign/unassign functions can update it without extra parameters.
    private var currentSchedule: CollectionDay? = null

    // Job for the all-zones real-time listener
    private var allZonesJob: Job? = null

    // =========================================================================
    // All-Zones (Global Zone Management)
    // =========================================================================

    /**
     * Starts a real-time listener for ALL zones in the route_zones collection.
     * Call this when the Global Zone Management screen opens.
     */
    fun loadAllZones() {
        allZonesJob?.cancel()
        allZonesJob = viewModelScope.launch {
            _allZonesState.value = AllZonesUiState.Loading
            mapRepository.getAllZones().collect { zones ->
                _allZonesState.value = AllZonesUiState.Success(zones)
            }
        }
    }

    /**
     * Creates a new global zone. Does NOT assign it to any schedule day.
     * The driver assigns zones to schedule days separately via the Zone Picker.
     */
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
                val zone = Zone(
                    name         = name.trim(),
                    centerLat    = centerLat,
                    centerLng    = centerLng,
                    radiusMeters = radiusMeters,
                    createdBy    = driverUid
                )
                mapRepository.addZone(zone)
                _actionState.value = RouteActionState.Success("Zone '${zone.name}' created.")
            } catch (e: Exception) {
                _actionState.value = RouteActionState.Error("Failed to create zone: ${e.message}")
            }
        }
    }

    /**
     * Deletes a global zone permanently from Firestore.
     * Note: does NOT automatically remove the zoneId from any schedule documents.
     * Orphaned IDs in schedules are safely ignored when loading (getZonesById returns only found docs).
     */
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

    /**
     * Loads the zones currently assigned to [schedule].
     * Fetches the actual Zone objects from Firestore by their IDs.
     * Call this when ZoneListScreen opens.
     */
    fun loadZonesForSchedule(schedule: CollectionDay) {
        currentSchedule = schedule
        viewModelScope.launch {
            _zoneListUiState.value = ZoneListUiState.Loading
            val zones = mapRepository.getZonesById(schedule.zoneIds)
            _zoneListUiState.value = ZoneListUiState.Success(zones)
        }
    }

    /**
     * Assigns a zone to the current schedule day by adding its ID to the schedule's zoneIds list.
     * Saves the updated list to Firestore immediately.
     */
    fun assignZoneToSchedule(zone: Zone) {
        val schedule = currentSchedule ?: return
        // Don't add duplicates
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
                // Refresh the assigned zones list to show the newly added zone
                val zones = mapRepository.getZonesById(updatedIds)
                _zoneListUiState.value = ZoneListUiState.Success(zones)
                _actionState.value = RouteActionState.Success("'${zone.name}' assigned to ${schedule.dayOfWeek}.")
            } else {
                // Roll back in-memory change if Firestore write fails
                currentSchedule = schedule
                _actionState.value = RouteActionState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to assign zone."
                )
            }
        }
    }

    /**
     * Removes a zone from the current schedule day's zoneIds list.
     * Does NOT delete the global zone — just unlinks it from this day.
     */
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
     * Calculates the optimised collection route for [zone].
     * After success, [activeRouteState] becomes [ActiveRouteUiState.Ready].
     */
    fun loadRouteForZone(zone: Zone) {
        viewModelScope.launch {
            _activeRouteState.value = ActiveRouteUiState.Calculating
            try {
                val stops = mapRepository.calculateRouteForZone(zone)
                _activeRouteState.value = if (stops.isEmpty()) {
                    ActiveRouteUiState.Error(
                        "No pending Regular Pickup reports found in this zone."
                    )
                } else {
                    ActiveRouteUiState.Ready(stops)
                }
            } catch (e: Exception) {
                _activeRouteState.value = ActiveRouteUiState.Error(
                    "Could not calculate route: ${e.message}"
                )
            }
        }
    }

    /**
     * Transitions from Ready → InProgress at stop index 0 and immediately
     * fetches directions to the first stop.
     *
     * @param driverLat  Current GPS latitude of the driver.
     * @param driverLng  Current GPS longitude of the driver.
     */
    fun startRoute(driverLat: Double, driverLng: Double) {
        val readyState = _activeRouteState.value as? ActiveRouteUiState.Ready ?: return
        _activeRouteState.value = ActiveRouteUiState.InProgress(
            stops            = readyState.stops,
            currentStopIndex = 0,
            directionsLoading = true
        )
        fetchDirectionsToStop(driverLat, driverLng, readyState.stops[0])
    }

    /**
     * Marks the current stop as collected, advances to the next stop,
     * and fetches directions to it.
     *
     * @param driverLat  Current GPS latitude of the driver (used for directions to the next stop).
     * @param driverLng  Current GPS longitude of the driver.
     */
    fun collectCurrentStop(driverLat: Double, driverLng: Double) {
        val inProgress = _activeRouteState.value as? ActiveRouteUiState.InProgress ?: return
        val currentStop = inProgress.stops[inProgress.currentStopIndex]

        viewModelScope.launch {
            try {
                // Mark report as dismissed in Firestore
                mapRepository.collectStop(currentStop.reportId)

                // Mark this stop as collected in the list
                val updatedStops = inProgress.stops.toMutableList().also {
                    it[inProgress.currentStopIndex] = currentStop.copy(isCollected = true)
                }

                val nextIndex = inProgress.currentStopIndex + 1

                if (nextIndex >= updatedStops.size) {
                    // All stops done
                    _activeRouteState.value = ActiveRouteUiState.Completed
                } else {
                    // Advance to next stop and start fetching directions
                    _activeRouteState.value = ActiveRouteUiState.InProgress(
                        stops             = updatedStops,
                        currentStopIndex  = nextIndex,
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
     * Fetches turn-by-turn directions from the driver's current position to [stop].
     * Updates the InProgress state with the result.
     * Silently ignores failures — the UI shows an empty directions panel in that case.
     */
    private fun fetchDirectionsToStop(driverLat: Double, driverLng: Double, stop: RouteStop) {
        viewModelScope.launch {
            val directions = mapRepository.getDirectionsToStop(
                fromLat = driverLat,
                fromLng = driverLng,
                toLat   = stop.location.latitude,
                toLng   = stop.location.longitude
            )
            // Apply directions to the current InProgress state
            val current = _activeRouteState.value as? ActiveRouteUiState.InProgress ?: return@launch
            _activeRouteState.value = current.copy(
                directions        = directions,
                directionsLoading = false
            )
        }
    }

    /** Resets the active route back to Idle. */
    fun resetRoute() {
        _activeRouteState.value = ActiveRouteUiState.Idle
    }

    /** Resets the action state after the UI has consumed it. */
    fun resetActionState() {
        _actionState.value = RouteActionState.Idle
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Manual DI factory
    // ─────────────────────────────────────────────────────────────────────────

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