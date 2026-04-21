package com.platform.smartwastemanager.features.map.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.platform.smartwastemanager.features.map.data.MapRepository
import com.platform.smartwastemanager.features.map.domain.MapPin
import com.platform.smartwastemanager.features.map.domain.Zone
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI state for the map screen. */
sealed class MapUiState {
    object Loading : MapUiState()
    data class Success(val pins: List<MapPin>) : MapUiState()
    data class Error(val message: String) : MapUiState()
}

data class MapCenterTarget(
    val latitude: Double,
    val longitude: Double,
    val triggerTimestamp: Long = System.currentTimeMillis()
)

/**
 * ViewModel for the Map screen.
 *
 * NEW: Also loads all zones belonging to the signed-in driver so they are
 * displayed as circle overlays on the map when the driver is in driver view.
 */
class MapViewModel(
    private val mapRepository: MapRepository
) : ViewModel() {

    // ---- Map pins (waste reports) ----
    private val _uiState = MutableStateFlow<MapUiState>(MapUiState.Loading)
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    // ---- Driver zones ----
    // Real-time list of all zones created by the currently signed-in driver.
    // Empty list when no driver is signed in or the driver has no zones.
    private val _driverZones = MutableStateFlow<List<Zone>>(emptyList())
    val driverZones: StateFlow<List<Zone>> = _driverZones.asStateFlow()

    // ---- Dismiss mode (driver only) ----
    private val _isDismissMode = MutableStateFlow(false)
    val isDismissMode: StateFlow<Boolean> = _isDismissMode.asStateFlow()

    // ---- Dismiss confirmation ----
    private val _pinToConfirmDismiss = MutableStateFlow<MapPin?>(null)
    val pinToConfirmDismiss: StateFlow<MapPin?> = _pinToConfirmDismiss.asStateFlow()

    private val _centerTarget = MutableStateFlow<MapCenterTarget?>(null)
    val centerTarget: StateFlow<MapCenterTarget?> = _centerTarget.asStateFlow()

    private var pinsJob:  Job? = null
    private var zonesJob: Job? = null

    init {
        loadPins()
    }

    /** Starts/restarts the real-time listener for pending waste report pins. */
    fun loadPins() {
        pinsJob?.cancel()
        pinsJob = viewModelScope.launch {
            _uiState.value = MapUiState.Loading
            mapRepository.getPendingMapPins().collect { pins ->
                _uiState.value = MapUiState.Success(pins)
            }
        }
    }

    /**
     * Loads all zones created by [driverUid] as a real-time stream.
     * Called from MapScreen when the driver enters driver view.
     * Safe to call multiple times — cancels the previous listener first.
     *
     * @param driverUid The UID of the signed-in driver. Pass empty string to clear zones.
     */
    fun loadDriverZones(driverUid: String) {
        zonesJob?.cancel()
        if (driverUid.isBlank()) {
            _driverZones.value = emptyList()
            return
        }
        zonesJob = viewModelScope.launch {
            mapRepository.getZonesForDriver(driverUid)
                .collect { zones -> _driverZones.value = zones }
        }
    }

    /** Clears the zone list (e.g. when driver switches to user view). */
    fun clearDriverZones() {
        zonesJob?.cancel()
        _driverZones.value = emptyList()
    }

    fun toggleDismissMode() {
        _isDismissMode.value = !_isDismissMode.value
        if (!_isDismissMode.value) _pinToConfirmDismiss.value = null
    }

    fun onPinTappedForDismiss(pin: MapPin) { _pinToConfirmDismiss.value = pin }
    fun cancelDismiss()  { _pinToConfirmDismiss.value = null }

    fun confirmDismiss() {
        val pin = _pinToConfirmDismiss.value ?: return
        _pinToConfirmDismiss.value = null
        viewModelScope.launch { mapRepository.dismissReport(pin.reportId) }
    }

    fun centerOnLocation(latitude: Double, longitude: Double) {
        _centerTarget.value = MapCenterTarget(latitude = latitude, longitude = longitude)
    }

    fun clearCenterTarget() {
        _centerTarget.value = null
    }

    companion object {
        fun factory(mapRepository: MapRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MapViewModel(mapRepository) as T
            }
    }
}
