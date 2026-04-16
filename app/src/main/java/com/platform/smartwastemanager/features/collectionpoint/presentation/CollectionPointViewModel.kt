package com.platform.smartwastemanager.features.collectionpoint.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.platform.smartwastemanager.features.collectionpoint.data.CollectionPointRepository
import com.platform.smartwastemanager.features.collectionpoint.domain.CollectionPoint
import com.platform.smartwastemanager.features.map.data.MapRepository
import com.platform.smartwastemanager.features.map.domain.Zone
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class CollectionPointViewModel(
    private val repository: CollectionPointRepository,
    private val mapRepository: MapRepository
) : ViewModel() {

    private val _collectionPoints = MutableStateFlow<List<CollectionPoint>>(emptyList())
    val collectionPoints: StateFlow<List<CollectionPoint>> = _collectionPoints.asStateFlow()

    private val _selectedPoint = MutableStateFlow<CollectionPoint?>(null)
    val selectedPoint: StateFlow<CollectionPoint?> = _selectedPoint.asStateFlow()

    private val _allZones = MutableStateFlow<List<Zone>>(emptyList())
    val allZones: StateFlow<List<Zone>> = _allZones.asStateFlow()

    private val _uiState = MutableStateFlow<CollectionPointUiState>(CollectionPointUiState.Idle)
    val uiState: StateFlow<CollectionPointUiState> = _uiState.asStateFlow()

    private var pointsJob: Job? = null

    init {
        loadCurrentUserPoints()
    }

    fun loadAllZones() {
        viewModelScope.launch {
            mapRepository.getAllZones().collect { zones ->
                _allZones.value = zones
            }
        }
    }

    fun loadCurrentUserPoints() {
        pointsJob?.cancel()
        pointsJob = viewModelScope.launch {
            try {
                repository.getCurrentUserPoints()
                    .catch { _ ->
                        if (_collectionPoints.value.isEmpty()) {
                            _uiState.value = CollectionPointUiState.Error(
                                "Could not load collection points."
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
                        "Could not load collection points."
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
            if (_collectionPoints.value.size >= MAX_COLLECTION_POINTS) {
                _uiState.value = CollectionPointUiState.Error(
                    "Maximum limit of $MAX_COLLECTION_POINTS collection points reached. Delete a point to add more."
                )
                return@launch
            }

            _uiState.value = CollectionPointUiState.Loading
            val result = repository.createCollectionPoint(point)
            _uiState.value = if (result.isSuccess)
                CollectionPointUiState.Success("Collection point created successfully")
            else
                CollectionPointUiState.Error(result.exceptionOrNull()?.message ?: "Failed to create collection point")
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
        fun factory(
            repository: CollectionPointRepository,
            mapRepository: MapRepository
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CollectionPointViewModel(repository, mapRepository) as T
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
