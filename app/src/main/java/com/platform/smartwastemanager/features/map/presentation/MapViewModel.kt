package com.platform.smartwastemanager.features.map.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.platform.smartwastemanager.features.map.data.MapRepository
import com.platform.smartwastemanager.features.map.domain.MapPin
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

/**
 * ViewModel for the Map screen.
 *
 * Manages:
 * - Loading the live list of pending map pins from Firestore
 * - Driver dismiss mode toggle
 * - Dismissing a single report (driver only)
 */
class MapViewModel(
    private val mapRepository: MapRepository
) : ViewModel() {

    // ---- UI state ----
    private val _uiState = MutableStateFlow<MapUiState>(MapUiState.Loading)
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    // ---- Dismiss mode (driver only) ----
    // When true, tapping a map pin will prompt the driver to dismiss it
    private val _isDismissMode = MutableStateFlow(false)
    val isDismissMode: StateFlow<Boolean> = _isDismissMode.asStateFlow()

    // ---- Dismiss confirmation dialog state ----
    // Holds the MapPin that was tapped while in dismiss mode
    private val _pinToConfirmDismiss = MutableStateFlow<MapPin?>(null)
    val pinToConfirmDismiss: StateFlow<MapPin?> = _pinToConfirmDismiss.asStateFlow()

    // Job reference so we can cancel the previous listener on reload
    private var pinsJob: Job? = null

    init {
        loadPins()
    }

    /**
     * Starts a Firestore real-time listener for pending map pins.
     * Cancels any existing listener first (avoids duplicates).
     */
    fun loadPins() {
        pinsJob?.cancel()
        pinsJob = viewModelScope.launch {
            _uiState.value = MapUiState.Loading
            mapRepository.getPendingMapPins().collect { pins ->
                _uiState.value = MapUiState.Success(pins)
            }
        }
    }

    /** Toggles the driver's dismiss mode on/off. */
    fun toggleDismissMode() {
        _isDismissMode.value = !_isDismissMode.value
        // Clear any pending confirmation when toggling off
        if (!_isDismissMode.value) _pinToConfirmDismiss.value = null
    }

    /**
     * Called when a driver taps a map pin while in dismiss mode.
     * Sets the pin on [pinToConfirmDismiss] so the UI can show a confirmation dialog.
     */
    fun onPinTappedForDismiss(pin: MapPin) {
        _pinToConfirmDismiss.value = pin
    }

    /** Called when the driver cancels the dismiss confirmation dialog. */
    fun cancelDismiss() {
        _pinToConfirmDismiss.value = null
    }

    /**
     * Confirms the dismiss — updates Firestore status to "dismissed".
     * The Firestore listener automatically removes the pin from the map.
     */
    fun confirmDismiss() {
        val pin = _pinToConfirmDismiss.value ?: return
        _pinToConfirmDismiss.value = null
        viewModelScope.launch {
            mapRepository.dismissReport(pin.reportId)
            // No need to manually update state — the snapshot listener handles it
        }
    }

    // ---- Manual DI factory ----
    companion object {
        fun factory(mapRepository: MapRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MapViewModel(mapRepository) as T
                }
            }
    }
}