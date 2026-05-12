package com.platform.smartwastemanager.features.map.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.platform.smartwastemanager.features.collectionpoint.domain.CollectionPoint
import com.platform.smartwastemanager.features.map.data.MapRepository
import com.platform.smartwastemanager.features.map.domain.MapPin
import com.platform.smartwastemanager.features.map.domain.RouteStop
import com.platform.smartwastemanager.features.map.domain.RouteStopType
import com.platform.smartwastemanager.features.map.domain.Zone
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

// =============================================================================
// UI States — Zone screens
// =============================================================================

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

    data class Ready(
        val stops: List<RouteStop>,
        val roadPolyline: List<LatLng> = emptyList()
    ) : ActiveRouteUiState()

    data class InProgress(
        val stops: List<RouteStop>,
        val currentStopIndex: Int,
        val roadPolyline: List<LatLng> = emptyList(),
        val directions: List<String> = emptyList(),
        val distanceMeters: Int? = null,
        val etaMinutes: Int? = null,
        val directionsLoading: Boolean = false
    ) : ActiveRouteUiState()

    object Completed : ActiveRouteUiState()
    data class Error(val message: String) : ActiveRouteUiState()
}

// =============================================================================
// RouteViewModel
// =============================================================================

class RouteViewModel(
    private val mapRepository: MapRepository
) : ViewModel() {

    // ---- All global zones ----
    private val _allZonesState = MutableStateFlow<AllZonesUiState>(AllZonesUiState.Loading)
    val allZonesState: StateFlow<AllZonesUiState> = _allZonesState.asStateFlow()

    // ---- Action feedback (add/delete) ----
    private val _actionState = MutableStateFlow<RouteActionState>(RouteActionState.Idle)
    val actionState: StateFlow<RouteActionState> = _actionState.asStateFlow()

    // ---- Active route ----
    private val _activeRouteState = MutableStateFlow<ActiveRouteUiState>(ActiveRouteUiState.Idle)
    val activeRouteState: StateFlow<ActiveRouteUiState> = _activeRouteState.asStateFlow()

    private var allZonesJob: Job? = null
    private var routeJob: Job? = null
    private var cpJob: Job? = null
    private var currentScheduleDayId: String = ""

    // ---- All collection points (shown on driver maps) ----
    private val _allCollectionPoints = MutableStateFlow<List<CollectionPoint>>(emptyList())
    val allCollectionPoints: StateFlow<List<CollectionPoint>> = _allCollectionPoints.asStateFlow()

    fun loadAllCollectionPoints() {
        cpJob?.cancel()
        cpJob = viewModelScope.launch {
            mapRepository.getAllCollectionPoints().collect { pts -> _allCollectionPoints.value = pts }
        }
    }

    fun loadAllZones() {
        allZonesJob?.cancel()
        allZonesJob = viewModelScope.launch {
            _allZonesState.value = AllZonesUiState.Loading
            try {
                mapRepository.getAllZones().collect { zones ->
                    _allZonesState.value = AllZonesUiState.Success(zones)
                }
            } catch (e: Exception) {
                _allZonesState.value = AllZonesUiState.Error(
                    e.message ?: "Could not load zones."
                )
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

    // =========================================================================
    // Active Route
    // =========================================================================

    /**
     * Builds an optimised collection route directly from a list of [MapPin]s — used by
     * the Map screen's radius-select mode so drivers can collect all reports in an area
     * without going through the schedule/zone flow.
     */
    fun loadRouteForPins(
        pins: List<MapPin>,
        driverLat: Double = 0.0,
        driverLng: Double = 0.0
    ) {
        currentScheduleDayId = ""
        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            _activeRouteState.value = ActiveRouteUiState.Calculating
            try {
                val result = withTimeout(20_000L) {
                    mapRepository.calculateRouteForPins(
                        pins      = pins,
                        driverLat = driverLat,
                        driverLng = driverLng
                    )
                }
                _activeRouteState.value = if (result.stops.isEmpty()) {
                    ActiveRouteUiState.Error("No stops found in the selected area.")
                } else {
                    ActiveRouteUiState.Ready(stops = result.stops, roadPolyline = result.roadPolyline)
                }
            } catch (_: TimeoutCancellationException) {
                _activeRouteState.value = ActiveRouteUiState.Error("Route calculation timed out. Please try again.")
            } catch (e: Exception) {
                _activeRouteState.value = ActiveRouteUiState.Error("Could not calculate route: ${e.message}")
            }
        }
    }

    fun loadRouteForZone(
        zone: Zone,
        scheduleDayId: String = "",
        driverLat: Double = 0.0,
        driverLng: Double = 0.0
    ) {
        currentScheduleDayId = scheduleDayId
        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            _activeRouteState.value = ActiveRouteUiState.Calculating
            try {
                val result = withTimeout(20_000L) {
                    mapRepository.calculateRouteForZone(
                        zone          = zone,
                        scheduleDayId = scheduleDayId,
                        driverLat     = driverLat,
                        driverLng     = driverLng
                    )
                }
                _activeRouteState.value = if (result.stops.isEmpty()) {
                    ActiveRouteUiState.Error(
                        "No pending stops found in this zone for this schedule day."
                    )
                } else {
                    ActiveRouteUiState.Ready(
                        stops        = result.stops,
                        roadPolyline = result.roadPolyline
                    )
                }
            } catch (_: TimeoutCancellationException) {
                _activeRouteState.value = ActiveRouteUiState.Error(
                    "Route calculation is taking too long. Please try again."
                )
            } catch (e: Exception) {
                _activeRouteState.value = ActiveRouteUiState.Error(
                    "Could not calculate route: ${e.message}"
                )
            }
        }
    }

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

    fun collectCurrentStop(driverLat: Double, driverLng: Double) {
        val inProgress  = _activeRouteState.value as? ActiveRouteUiState.InProgress ?: return
        val currentStop = inProgress.stops[inProgress.currentStopIndex]

        viewModelScope.launch {
            try {
                when (currentStop.type) {
                    RouteStopType.WASTE_REPORT -> mapRepository.collectStop(currentStop.reportId)
                    RouteStopType.COLLECTION_POINT -> mapRepository.unmarkCollectionPointFromDay(
                        pointId = currentStop.reportId,
                        scheduleDayId = currentScheduleDayId
                    )
                }

                val updatedStops = inProgress.stops.toMutableList().also {
                    it[inProgress.currentStopIndex] = currentStop.copy(isCollected = true)
                }

                val nextIndex = inProgress.currentStopIndex + 1

                if (nextIndex >= updatedStops.size) {
                    mapRepository.markScheduleDayAsCompleted(currentScheduleDayId)
                    _activeRouteState.value = ActiveRouteUiState.Completed
                } else {
                    val remainingStops = updatedStops.drop(nextIndex).filterNot { it.isCollected }
                    val remainingPolyline = mapRepository.getRoadPolylineForOrderedStops(
                        stops = remainingStops,
                        driverLat = driverLat,
                        driverLng = driverLng
                    ).ifEmpty {
                        buildRemainingPolyline(
                            driverLat = driverLat,
                            driverLng = driverLng,
                            remainingStops = remainingStops
                        )
                    }
                    _activeRouteState.value = ActiveRouteUiState.InProgress(
                        stops             = updatedStops,
                        currentStopIndex  = nextIndex,
                        roadPolyline      = remainingPolyline,
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

    private fun buildRemainingPolyline(
        driverLat: Double,
        driverLng: Double,
        remainingStops: List<RouteStop>
    ): List<LatLng> {
        val stopPoints = remainingStops.map { LatLng(it.location.latitude, it.location.longitude) }
        if (stopPoints.isEmpty()) return emptyList()
        val hasDriverLocation = driverLat != 0.0 || driverLng != 0.0
        return if (hasDriverLocation) {
            listOf(LatLng(driverLat, driverLng)) + stopPoints
        } else {
            stopPoints
        }
    }

    private fun fetchDirectionsToStop(driverLat: Double, driverLng: Double, stop: RouteStop) {
        viewModelScope.launch {
            try {
                val navigation = withTimeout(15_000L) {
                    mapRepository.getDirectionsToStop(
                        fromLat = driverLat, fromLng = driverLng,
                        toLat   = stop.location.latitude, toLng = stop.location.longitude
                    )
                }
                val current = _activeRouteState.value as? ActiveRouteUiState.InProgress
                    ?: return@launch
                _activeRouteState.value = current.copy(
                    directions        = navigation.steps,
                    distanceMeters    = navigation.distanceMeters,
                    etaMinutes        = navigation.durationSeconds?.let { (it / 60.0).toInt() },
                    directionsLoading = false
                )
            } catch (_: Exception) {
                val current = _activeRouteState.value as? ActiveRouteUiState.InProgress
                    ?: return@launch
                _activeRouteState.value = current.copy(
                    directions        = emptyList(),
                    distanceMeters    = null,
                    etaMinutes        = null,
                    directionsLoading = false
                )
            }
        }
    }

    fun refreshNavigationToCurrentStop(driverLat: Double, driverLng: Double) {
        val current = _activeRouteState.value as? ActiveRouteUiState.InProgress ?: return
        val activeStop = current.stops.getOrNull(current.currentStopIndex) ?: return
        viewModelScope.launch {
            val remainingStops = current.stops
                .drop(current.currentStopIndex)
                .filterNot { it.isCollected }
            val updatedPolyline = mapRepository.getRoadPolylineForOrderedStops(
                stops = remainingStops,
                driverLat = driverLat,
                driverLng = driverLng
            ).ifEmpty {
                buildRemainingPolyline(driverLat, driverLng, remainingStops)
            }
            _activeRouteState.value = current.copy(
                roadPolyline = updatedPolyline,
                directionsLoading = true
            )
            fetchDirectionsToStop(driverLat, driverLng, activeStop)
        }
    }

    fun setActiveRouteError(message: String) {
        _activeRouteState.value = ActiveRouteUiState.Error(message)
    }

    fun resetRoute() {
        _activeRouteState.value = ActiveRouteUiState.Idle
    }

    fun resetActionState() {
        _actionState.value = RouteActionState.Idle
    }

    companion object {
        fun factory(
            mapRepository: MapRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    RouteViewModel(mapRepository) as T
            }
    }
}
