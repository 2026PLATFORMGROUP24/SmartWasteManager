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

    private var schedulesJob: Job? = null

    init {
        loadSchedules()
        loadToggleState()
    }

    /**
     * Starts (or restarts) the Firestore schedule listener.
     * Called on init AND after successful login (see AuthViewModel.onAuthSuccess).
     */
    fun loadSchedules() {
        schedulesJob?.cancel()
        schedulesJob = viewModelScope.launch {
            try {
                scheduleRepository.getSchedules()
                    .catch { _ ->
                        if (_schedules.value.isEmpty()) {
                            _uiState.value = HomeUiState.Error(
                                "Could not load schedules. Check your connection."
                            )
                        }
                        emit(emptyList())
                    }
                    .collect { list ->
                        _schedules.value = list
                        if (list.isNotEmpty() && _uiState.value is HomeUiState.Error) {
                            _uiState.value = HomeUiState.Idle
                        }
                    }
            } catch (e: Exception) {
                if (_schedules.value.isEmpty()) {
                    _uiState.value = HomeUiState.Error(
                        "Could not load schedules. Check your connection."
                    )
                }
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

    /**
     * Creates a new schedule entry.
     *
     * @param wasteCategories   List of selected waste categories (must not be empty).
     * @param collectionTimeRange Optional display string e.g. "07:00 – 12:00".
     */
    fun createSchedule(
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