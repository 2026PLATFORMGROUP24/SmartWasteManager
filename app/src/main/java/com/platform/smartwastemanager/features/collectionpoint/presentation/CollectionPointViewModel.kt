package com.platform.smartwastemanager.features.collectionpoint.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.platform.smartwastemanager.features.collectionpoint.data.CollectionPointRepository
import com.platform.smartwastemanager.features.collectionpoint.domain.CollectionPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class CollectionPointViewModel(
    private val repository: CollectionPointRepository
) : ViewModel() {

    private val _collectionPoints = MutableStateFlow<List<CollectionPoint>>(emptyList())
    val collectionPoints: StateFlow<List<CollectionPoint>> = _collectionPoints.asStateFlow()

    private val _selectedPoint = MutableStateFlow<CollectionPoint?>(null)
    val selectedPoint: StateFlow<CollectionPoint?> = _selectedPoint.asStateFlow()

    private val _uiState = MutableStateFlow<CollectionPointUiState>(CollectionPointUiState.Idle)
    val uiState: StateFlow<CollectionPointUiState> = _uiState.asStateFlow()

    private var pointsJob: Job? = null

    init {
        loadCurrentUserPoints()
    }

    fun loadCurrentUserPoints() {
        pointsJob?.cancel()
        pointsJob = viewModelScope.launch {
            try {
                repository.getCurrentUserPoints()
                    .catch { _ ->
                        if (_collectionPoints.value.isEmpty()) {
                            _uiState.value = CollectionPointUiState.Error(
                                "Could not load collection points. Check your connection."
                            )
                        }
                        emit(emptyList())
                    }
                    .collect { list ->
                        _collectionPoints.value = list
                        if (list.isNotEmpty() && _uiState.value is CollectionPointUiState.Error) {
                            _uiState.value = CollectionPointUiState.Idle
                        }
                    }
            } catch (_: Exception) {
                if (_collectionPoints.value.isEmpty()) {
                    _uiState.value = CollectionPointUiState.Error(
                        "Could not load collection points. Check your connection."
                    )
                }
            }
        }
    }

    fun selectPoint(point: CollectionPoint) {
        _selectedPoint.value = point
    }

    fun createCollectionPoint(point: CollectionPoint) {
        viewModelScope.launch {
            _uiState.value = CollectionPointUiState.Loading
            val result = repository.createCollectionPoint(point)
            _uiState.value = if (result.isSuccess)
                CollectionPointUiState.Success("Collection point created successfully")
            else
                CollectionPointUiState.Error(result.exceptionOrNull()?.message ?: "Failed to create collection point")
        }
    }

    fun updateCollectionPoint(point: CollectionPoint) {
        viewModelScope.launch {
            _uiState.value = CollectionPointUiState.Loading
            val result = repository.updateCollectionPoint(point)
            _uiState.value = if (result.isSuccess)
                CollectionPointUiState.Success("Collection point updated successfully")
            else
                CollectionPointUiState.Error(result.exceptionOrNull()?.message ?: "Failed to update collection point")
        }
    }

    fun markForCollectionDay(pointId: String, scheduleDayId: String) {
        viewModelScope.launch {
            val result = repository.markForCollectionDay(pointId, scheduleDayId)
            _uiState.value = if (result.isSuccess)
                CollectionPointUiState.Success("Marked for collection")
            else
                CollectionPointUiState.Error(result.exceptionOrNull()?.message ?: "Failed to mark for collection")
        }
    }

    fun unmarkFromCollectionDay(pointId: String, scheduleDayId: String) {
        viewModelScope.launch {
            val result = repository.unmarkFromCollectionDay(pointId, scheduleDayId)
            _uiState.value = if (result.isSuccess)
                CollectionPointUiState.Success("Unmarked from collection")
            else
                CollectionPointUiState.Error(result.exceptionOrNull()?.message ?: "Failed to unmark from collection")
        }
    }

    fun deleteCollectionPoint(pointId: String) {
        viewModelScope.launch {
            _uiState.value = CollectionPointUiState.Loading
            val result = repository.deleteCollectionPoint(pointId)
            _uiState.value = if (result.isSuccess)
                CollectionPointUiState.Success("Collection point deleted")
            else
                CollectionPointUiState.Error(result.exceptionOrNull()?.message ?: "Failed to delete collection point")
        }
    }

    fun resetUiState() {
        _uiState.value = CollectionPointUiState.Idle
    }

    companion object {
        fun factory(repository: CollectionPointRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CollectionPointViewModel(repository) as T
                }
            }
        }
    }
}

sealed class CollectionPointUiState {
    object Idle : CollectionPointUiState()
    object Loading : CollectionPointUiState()
    data class Success(val message: String) : CollectionPointUiState()
    data class Error(val message: String) : CollectionPointUiState()
}