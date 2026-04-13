package com.platform.smartwastemanager.features.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.platform.smartwastemanager.core.util.ViewToggleRepository
import com.platform.smartwastemanager.features.home.data.ScheduleRepository
import com.platform.smartwastemanager.features.home.domain.CollectionDay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Manages UI state for the Home screen and Schedule Management screen.
 */
class HomeViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val viewToggleRepository: ViewToggleRepository
) : ViewModel() {

    private val _schedules = MutableStateFlow<List<CollectionDay>>(emptyList())
    val schedules: StateFlow<List<CollectionDay>> = _schedules.asStateFlow()

    private val _isDriverViewActive = MutableStateFlow(true)
    val isDriverViewActive: StateFlow<Boolean> = _isDriverViewActive.asStateFlow()

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Tracks the current schedule-loading job so we can cancel and restart it
    private var schedulesJob: Job? = null

    init {
        loadSchedules()
        loadToggleState()
    }

    /**
     * Starts (or restarts) the real-time Firestore schedule listener.
     *
     * Cancelling the old job first ensures we never have two simultaneous
     * listeners running, which would cause duplicate or stale data.
     *
     * Call this:
     * - Automatically on init (covers the "already signed in" case)
     * - Explicitly after a successful login (covers the "just logged in" case)
     */
    fun loadSchedules() {
        // Cancel any previous listener before starting a new one
        schedulesJob?.cancel()

        schedulesJob = viewModelScope.launch {
            try {
                scheduleRepository.getSchedules()
                    .catch { e ->
                        _uiState.value = HomeUiState.Error(
                            "Could not load schedules. Check your connection."
                        )
                        emit(emptyList())
                    }
                    .collect { list ->
                        _schedules.value = list
                    }
            } catch (e: Exception) {
                _schedules.value = emptyList()
                _uiState.value = HomeUiState.Error(
                    "Could not load schedules. Check your connection."
                )
            }
        }
    }

    private fun loadToggleState() {
        viewModelScope.launch {
            try {
                viewToggleRepository.isDriverViewActive.collect { isActive ->
                    _isDriverViewActive.value = isActive
                }
            } catch (e: Exception) {
                _isDriverViewActive.value = true
            }
        }
    }

    fun toggleDriverView() {
        viewModelScope.launch {
            viewToggleRepository.setDriverViewActive(!_isDriverViewActive.value)
        }
    }

    fun createSchedule(
        dayOfWeek: String,
        wasteCategory: String,
        linkedGuideId: String?,
        driverUid: String
    ) {
        if (dayOfWeek.isBlank() || wasteCategory.isBlank()) {
            _uiState.value = HomeUiState.Error("Please select a day and waste category")
            return
        }
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            val result = scheduleRepository.createSchedule(
                CollectionDay(
                    dayOfWeek = dayOfWeek,
                    wasteCategory = wasteCategory,
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
                collectionDay.copy(linkedGuideId = collectionDay.linkedGuideId?.ifBlank { null })
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

    fun resetUiState() {
        _uiState.value = HomeUiState.Idle
    }

    companion object {
        fun factory(
            scheduleRepository: ScheduleRepository,
            viewToggleRepository: ViewToggleRepository
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(scheduleRepository, viewToggleRepository) as T
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