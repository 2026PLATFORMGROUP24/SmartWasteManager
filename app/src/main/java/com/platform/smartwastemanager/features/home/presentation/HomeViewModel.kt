package com.platform.smartwastemanager.features.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.platform.smartwastemanager.core.util.ViewToggleRepository
import com.platform.smartwastemanager.features.collectionpoint.data.CollectionPointRepository
import com.platform.smartwastemanager.features.collectionpoint.domain.CollectionPoint
import com.platform.smartwastemanager.features.home.data.ScheduleRepository
import com.platform.smartwastemanager.features.home.domain.CollectionDay
import com.platform.smartwastemanager.features.map.data.MapRepository
import com.platform.smartwastemanager.features.map.domain.Zone
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val viewToggleRepository: ViewToggleRepository,
    private val collectionPointRepository: CollectionPointRepository,
    private val mapRepository: MapRepository
) : ViewModel() {

    // User's collection points
    private val _collectionPoints = MutableStateFlow<List<CollectionPoint>>(emptyList())
    val collectionPoints: StateFlow<List<CollectionPoint>> = _collectionPoints.asStateFlow()

    // Currently selected collection point
    private val _selectedPoint = MutableStateFlow<CollectionPoint?>(null)
    val selectedPoint: StateFlow<CollectionPoint?> = _selectedPoint.asStateFlow()

    // Schedules for the selected zone (user) or all zones (driver)
    private val _schedules = MutableStateFlow<List<CollectionDay>>(emptyList())
    val schedules: StateFlow<List<CollectionDay>> = _schedules.asStateFlow()

    // All zones (for driver view)
    private val _zones = MutableStateFlow<List<Zone>>(emptyList())
    val zones: StateFlow<List<Zone>> = _zones.asStateFlow()

    // Currently selected zone (driver view)
    private val _selectedZone = MutableStateFlow<Zone?>(null)
    val selectedZone: StateFlow<Zone?> = _selectedZone.asStateFlow()

    private val _isDriverViewActive = MutableStateFlow(true)
    val isDriverViewActive: StateFlow<Boolean> = _isDriverViewActive.asStateFlow()

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var collectionPointsJob: Job? = null
    private var schedulesJob: Job? = null
    private var zonesJob: Job? = null

    init {
        loadToggleState()
        loadCollectionPoints()
        loadZones()
    }

    // =========================================================================
    // User Flow: Collection Points
    // =========================================================================

    fun loadCollectionPoints() {
        collectionPointsJob?.cancel()
        collectionPointsJob = viewModelScope.launch {
            try {
                collectionPointRepository.getCurrentUserPoints()
                    .collect { list ->
                        _collectionPoints.value = list

                        // Auto-select first point if none selected
                        if (_selectedPoint.value == null && list.isNotEmpty()) {
                            selectCollectionPoint(list.first())
                        }

                        if (list.isNotEmpty() && _uiState.value is HomeUiState.Error) {
                            _uiState.value = HomeUiState.Idle
                        }
                    }
            } catch (_: Exception) {
                if (_collectionPoints.value.isEmpty()) {
                    _uiState.value = HomeUiState.Error(
                        "Could not load collection points. Check your connection."
                    )
                }
            }
        }
    }

    fun selectCollectionPoint(point: CollectionPoint) {
        _selectedPoint.value = point

        // Load schedules for this point's zone
        if (point.zoneId.isNotBlank()) {
            loadSchedulesForZone(point.zoneId)
        } else {
            _schedules.value = emptyList()
            _uiState.value = HomeUiState.Error(
                "This collection point is not assigned to a zone yet."
            )
        }
    }

    fun markPointForCollection(pointId: String, scheduleDayId: String) {
        viewModelScope.launch {
            val result = collectionPointRepository.markForCollectionDay(pointId, scheduleDayId)
            _uiState.value = if (result.isSuccess)
                HomeUiState.Success("✅ Marked for collection")
            else
                HomeUiState.Error(result.exceptionOrNull()?.message ?: "Failed to mark for collection")
        }
    }

    fun unmarkPointFromCollection(pointId: String, scheduleDayId: String) {
        viewModelScope.launch {
            val result = collectionPointRepository.unmarkFromCollectionDay(pointId, scheduleDayId)
            _uiState.value = if (result.isSuccess)
                HomeUiState.Success("Unmarked from collection")
            else
                HomeUiState.Error(result.exceptionOrNull()?.message ?: "Failed to unmark")
        }
    }

    // =========================================================================
    // Driver Flow: Zones
    // =========================================================================

    fun loadZones() {
        zonesJob?.cancel()
        zonesJob = viewModelScope.launch {
            try {
                mapRepository.getZones()
                    .collect { list ->
                        _zones.value = list

                        // Auto-select first zone if none selected
                        if (_selectedZone.value == null && list.isNotEmpty()) {
                            selectZone(list.first())
                        }
                    }
            } catch (_: Exception) {
                _zones.value = emptyList()
            }
        }
    }

    fun selectZone(zone: Zone) {
        _selectedZone.value = zone
        loadSchedulesForZone(zone.id)
    }

    // =========================================================================
    // Schedules
    // =========================================================================

    private fun loadSchedulesForZone(zoneId: String) {
        schedulesJob?.cancel()
        schedulesJob = viewModelScope.launch {
            try {
                scheduleRepository.getSchedulesForZone(zoneId)
                    .collect { list ->
                        _schedules.value = list
                        if (list.isNotEmpty() && _uiState.value is HomeUiState.Error) {
                            _uiState.value = HomeUiState.Idle
                        }
                    }
            } catch (_: Exception) {
                if (_schedules.value.isEmpty()) {
                    _uiState.value = HomeUiState.Error(
                        "Could not load schedules. Check your connection."
                    )
                }
            }
        }
    }

    fun createSchedule(
        zoneId: String,
        dayOfWeek: String,
        wasteCategories: List<String>,
        collectionTimeRange: String?,
        linkedGuideId: String?,
        driverUid: String
    ) {
        if (dayOfWeek.isBlank() || wasteCategories.isEmpty()) {
            _uiState.value = HomeUiState.Error("Please select a day and at least one waste category")
            return
        }
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            val result = scheduleRepository.createSchedule(
                CollectionDay(
                    zoneId = zoneId,
                    dayOfWeek = dayOfWeek,
                    wasteCategories = wasteCategories,
                    collectionTimeRange = collectionTimeRange?.ifBlank { null },
                    linkedGuideId = linkedGuideId?.ifBlank { null },
                    createdBy = driverUid
                )
            )
            _uiState.value = if (result.isSuccess)
                HomeUiState.Success("Schedule created successfully")
            else
                HomeUiState.Error(result.exceptionOrNull()?.message ?: "Failed to create schedule")
        }
    }

    fun updateSchedule(collectionDay: CollectionDay) {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            val result = scheduleRepository.updateSchedule(
                collectionDay.copy(
                    linkedGuideId = collectionDay.linkedGuideId?.ifBlank { null },
                    collectionTimeRange = collectionDay.collectionTimeRange?.ifBlank { null }
                )
            )
            _uiState.value = if (result.isSuccess)
                HomeUiState.Success("Schedule updated successfully")
            else
                HomeUiState.Error(result.exceptionOrNull()?.message ?: "Failed to update schedule")
        }
    }

    fun deleteSchedule(scheduleId: String) {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            val result = scheduleRepository.deleteSchedule(scheduleId)
            _uiState.value = if (result.isSuccess)
                HomeUiState.Success("Schedule deleted")
            else
                HomeUiState.Error(result.exceptionOrNull()?.message ?: "Failed to delete schedule")
        }
    }

    // =========================================================================
    // Driver View Toggle
    // =========================================================================

    private fun loadToggleState() {
        viewModelScope.launch {
            try {
                viewToggleRepository.isDriverViewActive.collect { isActive ->
                    _isDriverViewActive.value = isActive
                }
            } catch (_: Exception) {
                _isDriverViewActive.value = true
            }
        }
    }

    fun toggleDriverView() {
        viewModelScope.launch {
            viewToggleRepository.setDriverViewActive(!_isDriverViewActive.value)
        }
    }

    fun resetUiState() {
        _uiState.value = HomeUiState.Idle
    }

    companion object {
        fun factory(
            scheduleRepository: ScheduleRepository,
            viewToggleRepository: ViewToggleRepository,
            collectionPointRepository: CollectionPointRepository,
            mapRepository: MapRepository
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(
                        scheduleRepository,
                        viewToggleRepository,
                        collectionPointRepository,
                        mapRepository
                    ) as T
                }
            }
        }
    }
}

sealed class HomeUiState {
    object Idle : HomeUiState()
    object Loading : HomeUiState()
    data class Success(val message: String) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}