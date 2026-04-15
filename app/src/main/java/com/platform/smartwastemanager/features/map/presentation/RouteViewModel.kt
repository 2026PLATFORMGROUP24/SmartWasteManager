package com.platform.smartwastemanager.features.map.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.platform.smartwastemanager.features.map.data.MapRepository
import com.platform.smartwastemanager.features.map.domain.RouteStop
import com.platform.smartwastemanager.features.map.domain.Zone
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ---- UI States for the Zone List screen ----

/** Represents the state of the zone list for a given schedule day. */
sealed class ZoneListUiState {
    object Loading : ZoneListUiState()
    data class Success(val zones: List<Zone>) : ZoneListUiState()
    data class Error(val message: String) : ZoneListUiState()
}

/** Represents a user-triggered action result (add zone, delete zone, load route). */
sealed class RouteActionState {
    object Idle : RouteActionState()
    object Loading : RouteActionState()
    data class Success(val message: String) : RouteActionState()
    data class Error(val message: String) : RouteActionState()
}

// ---- UI States for the Active Route screen ----

/** Represents the state of the active driving route. */
sealed class ActiveRouteUiState {
    object Idle : ActiveRouteUiState()
    object Calculating : ActiveRouteUiState()
    /** Route has been calculated and is ready to start. */
    data class Ready(val stops: List<RouteStop>) : ActiveRouteUiState()
    /** Driver has started the route and is working through stops. */
    data class InProgress(
        val stops: List<RouteStop>,
        val currentStopIndex: Int
    ) : ActiveRouteUiState()
    /** All stops have been collected — route is complete. */
    object Completed : ActiveRouteUiState()
    data class Error(val message: String) : ActiveRouteUiState()
}

/**
 * RouteViewModel manages:
 *   1. Listing / adding / deleting zones for a schedule day.
 *   2. Loading and calculating an optimised route from a zone.
 *   3. Driving the active route: tracking current stop, collecting stops.
 */
class RouteViewModel(
    private val mapRepository: MapRepository
) : ViewModel() {

    // ---- Zone list state ----
    private val _zoneListUiState = MutableStateFlow<ZoneListUiState>(ZoneListUiState.Loading)
    val zoneListUiState: StateFlow<ZoneListUiState> = _zoneListUiState.asStateFlow()

    // ---- Action state (add/delete/route load feedback) ----
    private val _actionState = MutableStateFlow<RouteActionState>(RouteActionState.Idle)
    val actionState: StateFlow<RouteActionState> = _actionState.asStateFlow()

    // ---- Active route state ----
    private val _activeRouteState = MutableStateFlow<ActiveRouteUiState>(ActiveRouteUiState.Idle)
    val activeRouteState: StateFlow<ActiveRouteUiState> = _activeRouteState.asStateFlow()

    // ---- The schedule day ID whose zones we are currently showing ----
    private var currentScheduleDayId: String = ""

    // Zone listener job — cancelled and restarted whenever we switch days
    private var zonesJob: Job? = null

    /**
     * Loads the real-time zone list for the given schedule day.
     * Call this when the ZoneListScreen first opens.
     */
    fun loadZonesForDay(scheduleDayId: String) {
        // Don't reload if already listening for the same day
        if (scheduleDayId == currentScheduleDayId && zonesJob?.isActive == true) return

        currentScheduleDayId = scheduleDayId
        zonesJob?.cancel()
        zonesJob = viewModelScope.launch {
            _zoneListUiState.value = ZoneListUiState.Loading
            mapRepository.getZonesForDay(scheduleDayId)
                .collect { zones ->
                    _zoneListUiState.value = ZoneListUiState.Success(zones)
                }
        }
    }

    /**
     * Adds a new zone for the current schedule day.
     *
     * @param name          The driver-provided name for this zone.
     * @param centerLat     Latitude of the picked zone centre.
     * @param centerLng     Longitude of the picked zone centre.
     * @param radiusMeters  Radius of the zone in metres.
     * @param driverUid     UID of the currently signed-in driver.
     */
    fun addZone(
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
                    name          = name.trim(),
                    scheduleDayId = currentScheduleDayId,
                    centerLat     = centerLat,
                    centerLng     = centerLng,
                    radiusMeters  = radiusMeters,
                    createdBy     = driverUid
                )
                mapRepository.addZone(zone)
                _actionState.value = RouteActionState.Success("Zone '${zone.name}' added.")
            } catch (e: Exception) {
                _actionState.value = RouteActionState.Error("Failed to add zone: ${e.message}")
            }
        }
    }

    /** Deletes a zone by its Firestore document ID. */
    fun deleteZone(zoneId: String) {
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

    /**
     * Calculates the optimised collection route for the given zone.
     * Fetches pending Regular Pickup reports inside the zone, then asks
     * OSRM to order them optimally.
     *
     * After this completes successfully, [activeRouteState] will be [ActiveRouteUiState.Ready].
     * The caller should navigate to the ActiveRouteScreen.
     */
    fun loadRouteForZone(zone: Zone) {
        viewModelScope.launch {
            _activeRouteState.value = ActiveRouteUiState.Calculating
            try {
                val stops = mapRepository.calculateRouteForZone(zone)
                if (stops.isEmpty()) {
                    _activeRouteState.value = ActiveRouteUiState.Error(
                        "No pending Regular Pickup reports found in this zone."
                    )
                } else {
                    _activeRouteState.value = ActiveRouteUiState.Ready(stops)
                }
            } catch (e: Exception) {
                _activeRouteState.value = ActiveRouteUiState.Error(
                    "Could not calculate route: ${e.message}"
                )
            }
        }
    }

    /**
     * Driver taps "Start Route" on the Ready screen.
     * Transitions from Ready → InProgress at stop index 0.
     */
    fun startRoute() {
        val readyState = _activeRouteState.value as? ActiveRouteUiState.Ready ?: return
        _activeRouteState.value = ActiveRouteUiState.InProgress(
            stops            = readyState.stops,
            currentStopIndex = 0
        )
    }

    /**
     * Driver taps "Collect" on the current stop.
     *   - Marks the Firestore report as "dismissed" (removes pin from map).
     *   - Advances to the next stop, or transitions to Completed if this was the last.
     */
    fun collectCurrentStop() {
        val inProgress = _activeRouteState.value as? ActiveRouteUiState.InProgress ?: return
        val currentStop = inProgress.stops[inProgress.currentStopIndex]

        viewModelScope.launch {
            try {
                // Mark report as collected/dismissed in Firestore
                mapRepository.collectStop(currentStop.reportId)

                // Build the updated stop list with this stop marked as collected
                val updatedStops = inProgress.stops.toMutableList().also { list ->
                    list[inProgress.currentStopIndex] = currentStop.copy(isCollected = true)
                }

                val nextIndex = inProgress.currentStopIndex + 1

                _activeRouteState.value = if (nextIndex >= updatedStops.size) {
                    // All stops collected — route complete
                    ActiveRouteUiState.Completed
                } else {
                    // Move to the next stop
                    ActiveRouteUiState.InProgress(
                        stops            = updatedStops,
                        currentStopIndex = nextIndex
                    )
                }
            } catch (e: Exception) {
                // Don't crash the route — show a snackbar-friendly error
                _actionState.value = RouteActionState.Error(
                    "Could not record collection: ${e.message}"
                )
            }
        }
    }

    /** Resets the active route state back to Idle (e.g. driver navigates away). */
    fun resetRoute() {
        _activeRouteState.value = ActiveRouteUiState.Idle
    }

    /** Resets the action state after the UI has consumed it. */
    fun resetActionState() {
        _actionState.value = RouteActionState.Idle
    }

    // ---- Manual DI factory ----
    companion object {
        fun factory(mapRepository: MapRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RouteViewModel(mapRepository) as T
                }
            }
    }
}