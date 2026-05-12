package com.platform.smartwastemanager.features.map.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.platform.smartwastemanager.features.collectionpoint.domain.CollectionPoint
import com.platform.smartwastemanager.features.map.data.MapRepository
import com.platform.smartwastemanager.features.map.domain.MapPin
import com.platform.smartwastemanager.features.map.domain.Zone
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.*

/** UI state for the map screen. */
sealed class MapUiState {
    object Loading : MapUiState()
    data class Success(val pins: List<MapPin>) : MapUiState()
    data class Error(val message: String) : MapUiState()
}

/** State for the radius-based bulk-collect mode (driver only). */
sealed class RadiusSelectState {
    object Idle : RadiusSelectState()
    data class Active(
        val centerLat: Double,
        val centerLng: Double,
        val radiusMeters: Float,
        val pinsInRadius: List<MapPin>
    ) : RadiusSelectState()
}

class MapViewModel(
    private val mapRepository: MapRepository
) : ViewModel() {

    // ---- Map pins (waste reports) ----
    private val _uiState = MutableStateFlow<MapUiState>(MapUiState.Loading)
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    // ---- Driver zones ----
    private val _driverZones = MutableStateFlow<List<Zone>>(emptyList())
    val driverZones: StateFlow<List<Zone>> = _driverZones.asStateFlow()

    // ---- All collection points (visible to drivers on map) ----
    private val _allCollectionPoints = MutableStateFlow<List<CollectionPoint>>(emptyList())
    val allCollectionPoints: StateFlow<List<CollectionPoint>> = _allCollectionPoints.asStateFlow()

    private val _radiusSelectState = MutableStateFlow<RadiusSelectState>(RadiusSelectState.Idle)
    val radiusSelectState: StateFlow<RadiusSelectState> = _radiusSelectState.asStateFlow()

    private var pinsJob:  Job? = null
    private var zonesJob: Job? = null
    private var cpJob:    Job? = null

    init { loadPins() }

    fun loadPins() {
        pinsJob?.cancel()
        pinsJob = viewModelScope.launch {
            _uiState.value = MapUiState.Loading
            mapRepository.getPendingMapPins().collect { pins ->
                _uiState.value = MapUiState.Success(pins)
                // Keep radius counts live when pins change while mode is active
                val current = _radiusSelectState.value
                if (current is RadiusSelectState.Active) {
                    _radiusSelectState.value = current.copy(
                        pinsInRadius = pinsWithinRadius(pins, current.centerLat, current.centerLng, current.radiusMeters)
                    )
                }
            }
        }
    }

    fun dismissPin(reportId: String) {
        viewModelScope.launch {
            try {
                mapRepository.dismissReport(reportId)
                // The real-time listener in loadPins() will pick up the deletion
            } catch (e: Exception) {
                // Error handling handled by UI via repository if needed
            }
        }
    }

    fun loadDriverZones(driverUid: String) {
        zonesJob?.cancel()
        if (driverUid.isBlank()) { _driverZones.value = emptyList(); return }
        zonesJob = viewModelScope.launch {
            mapRepository.getZonesForDriver(driverUid).collect { zones -> _driverZones.value = zones }
        }
    }

    fun clearDriverZones() {
        zonesJob?.cancel()
        _driverZones.value = emptyList()
    }

    fun loadAllCollectionPoints() {
        cpJob?.cancel()
        cpJob = viewModelScope.launch {
            mapRepository.getAllCollectionPoints().collect { pts -> _allCollectionPoints.value = pts }
        }
    }

    fun clearAllCollectionPoints() {
        cpJob?.cancel()
        _allCollectionPoints.value = emptyList()
    }

    // ---- Radius select ----

    fun enterRadiusMode(lat: Double, lng: Double, initialRadius: Float = 500f) {
        val pins = (_uiState.value as? MapUiState.Success)?.pins ?: emptyList()
        _radiusSelectState.value = RadiusSelectState.Active(
            centerLat    = lat,
            centerLng    = lng,
            radiusMeters = initialRadius,
            pinsInRadius = pinsWithinRadius(pins, lat, lng, initialRadius)
        )
    }

    fun updateRadiusCenter(lat: Double, lng: Double) {
        val current = _radiusSelectState.value as? RadiusSelectState.Active ?: return
        val pins = (_uiState.value as? MapUiState.Success)?.pins ?: emptyList()
        _radiusSelectState.value = current.copy(
            centerLat    = lat,
            centerLng    = lng,
            pinsInRadius = pinsWithinRadius(pins, lat, lng, current.radiusMeters)
        )
    }

    fun updateRadius(meters: Float) {
        val current = _radiusSelectState.value as? RadiusSelectState.Active ?: return
        val pins = (_uiState.value as? MapUiState.Success)?.pins ?: emptyList()
        _radiusSelectState.value = current.copy(
            radiusMeters = meters,
            pinsInRadius = pinsWithinRadius(pins, current.centerLat, current.centerLng, meters)
        )
    }

    fun exitRadiusMode() { _radiusSelectState.value = RadiusSelectState.Idle }

    fun getActiveRadiusPins(): List<MapPin> =
        (_radiusSelectState.value as? RadiusSelectState.Active)?.pinsInRadius ?: emptyList()

    private fun pinsWithinRadius(pins: List<MapPin>, lat: Double, lng: Double, radiusMeters: Float) =
        pins.filter { pin -> haversine(lat, lng, pin.location.latitude, pin.location.longitude) <= radiusMeters }

    private fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r    = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a    = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
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
