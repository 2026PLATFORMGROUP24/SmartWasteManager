package com.platform.smartwastemanager.features.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.platform.smartwastemanager.core.util.ViewToggleRepository
import com.platform.smartwastemanager.features.home.data.ScheduleRepository
import com.platform.smartwastemanager.features.home.domain.CollectionDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages the UI state for the Home screen and Schedule Management screen.
 *
 * Exposes:
 * - [schedules]         — live list of schedule entries from Firestore
 * - [isDriverViewActive] — whether the driver is currently in driver view or user view
 * - [uiState]           — loading / error / success feedback for CRUD operations
 */
class HomeViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val viewToggleRepository: ViewToggleRepository
) : ViewModel() {

    // ---- Schedules ----
    private val _schedules = MutableStateFlow<List<CollectionDay>>(emptyList())
    val schedules: StateFlow<List<CollectionDay>> = _schedules.asStateFlow()

    // ---- Driver/User view toggle ----
    private val _isDriverViewActive = MutableStateFlow(true)
    val isDriverViewActive: StateFlow<Boolean> = _isDriverViewActive.asStateFlow()

    // ---- UI feedback for CRUD operations ----
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadSchedules()
        loadToggleState()
    }

    /** Starts collecting the real-time Firestore schedule stream. */
    private fun loadSchedules() {
        viewModelScope.launch {
            scheduleRepository.getSchedules().collect { list ->
                _schedules.value = list
            }
        }
    }

    /** Loads the persisted toggle state from DataStore. */
    private fun loadToggleState() {
        viewModelScope.launch {
            viewToggleRepository.isDriverViewActive.collect { isActive ->
                _isDriverViewActive.value = isActive
            }
        }
    }

    /**
     * Flips the driver/user view toggle and persists the new value.
     * Called when the driver taps the toggle button in the top bar.
     */
    fun toggleDriverView() {
        viewModelScope.launch {
            val newValue = !_isDriverViewActive.value
            viewToggleRepository.setDriverViewActive(newValue)
            // _isDriverViewActive will update automatically via the DataStore flow above
        }
    }

    /**
     * Creates a new schedule entry in Firestore.
     * @param driverUid The UID of the driver creating the schedule.
     */
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

            val newEntry = CollectionDay(
                dayOfWeek = dayOfWeek,
                wasteCategory = wasteCategory,
                linkedGuideId = linkedGuideId?.ifBlank { null },
                createdBy = driverUid
            )

            val result = scheduleRepository.createSchedule(newEntry)

            _uiState.value = if (result.isSuccess) {
                HomeUiState.Success("Schedule created successfully")
            } else {
                HomeUiState.Error(result.exceptionOrNull()?.message ?: "Failed to create schedule")
            }
        }
    }

    /**
     * Updates an existing schedule entry in Firestore.
     */
    fun updateSchedule(collectionDay: CollectionDay) {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading

            val result = scheduleRepository.updateSchedule(
                collectionDay.copy(
                    linkedGuideId = collectionDay.linkedGuideId?.ifBlank { null }
                )
            )

            _uiState.value = if (result.isSuccess) {
                HomeUiState.Success("Schedule updated successfully")
            } else {
                HomeUiState.Error(result.exceptionOrNull()?.message ?: "Failed to update schedule")
            }
        }
    }

    /**
     * Deletes a schedule entry from Firestore.
     */
    fun deleteSchedule(scheduleId: String) {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading

            val result = scheduleRepository.deleteSchedule(scheduleId)

            _uiState.value = if (result.isSuccess) {
                HomeUiState.Success("Schedule deleted")
            } else {
                HomeUiState.Error(result.exceptionOrNull()?.message ?: "Failed to delete schedule")
            }
        }
    }

    /** Resets the UI state back to Idle. Call after showing a snackbar. */
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

/** All possible UI feedback states for the Home screen. */
sealed class HomeUiState {
    object Idle : HomeUiState()
    object Loading : HomeUiState()
    data class Success(val message: String) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}