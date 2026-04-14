package com.platform.smartwastemanager.features.report.presentation

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.GeoPoint
import com.platform.smartwastemanager.core.util.LocationHelper
import com.platform.smartwastemanager.features.report.data.ReportRepository
import com.platform.smartwastemanager.features.report.domain.ReportType
import com.platform.smartwastemanager.features.report.domain.WasteCategory
import com.platform.smartwastemanager.features.report.domain.WasteImageClassifier
import com.platform.smartwastemanager.features.report.domain.WasteReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI state for the report submission process. */
sealed class ReportUiState {
    object Idle    : ReportUiState()
    object Loading : ReportUiState()
    object Success : ReportUiState()
    data class Error(val message: String) : ReportUiState()
}

/**
 * ViewModel for the Waste Reporting feature.
 *
 * Manages:
 * - Form field values (category, reportType, streetName, location)
 * - TFLite classification from a camera bitmap
 * - GPS location fetching and reverse geocoding
 * - Firestore submission via ReportRepository
 */
class ReportViewModel(
    private val reportRepository: ReportRepository,
    private val wasteImageClassifier: WasteImageClassifier
) : ViewModel() {

    // ---- UI state ----
    private val _uiState = MutableStateFlow<ReportUiState>(ReportUiState.Idle)
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    // ---- Form fields ----

    // The selected waste category (editable by user after AI pre-fill)
    private val _selectedCategory = MutableStateFlow(WasteCategory.MIXED_WASTE.displayName)
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    // Top-5 AI label names and confidence scores shown in the scan results card
    private val _aiLabels = MutableStateFlow<List<Pair<String, Float>>>(emptyList())
    val aiLabels: StateFlow<List<Pair<String, Float>>> = _aiLabels.asStateFlow()

    // Human-readable debug string e.g. "• water bottle → 42%\n• plastic bag → 18%"
    // Displayed in the AI summary card so the user can see what the model detected.
    private val _aiDebugInfo = MutableStateFlow("")
    val aiDebugInfo: StateFlow<String> = _aiDebugInfo.asStateFlow()

    // True when the model's best score was below the confidence threshold.
    // Drives the orange warning card in ReportFormScreen that prompts a re-scan.
    private val _isLowConfidence = MutableStateFlow(false)
    val isLowConfidence: StateFlow<Boolean> = _isLowConfidence.asStateFlow()

    // The selected report type
    private val _selectedReportType = MutableStateFlow(ReportType.REGULAR_PICKUP.displayName)
    val selectedReportType: StateFlow<String> = _selectedReportType.asStateFlow()

    // GPS location — auto-fetched, not exposed directly to the UI
    private val _location = MutableStateFlow(GeoPoint(0.0, 0.0))

    // Street name — auto-fetched via GPS + Geocoder, but the user can edit it
    private val _streetName = MutableStateFlow("")
    val streetName: StateFlow<String> = _streetName.asStateFlow()

    // True while we are waiting for the GPS fix
    private val _isLocating = MutableStateFlow(false)
    val isLocating: StateFlow<Boolean> = _isLocating.asStateFlow()

    // ---- Setters called by the UI ----

    fun setCategory(category: String) { _selectedCategory.value = category }
    fun setReportType(type: String)    { _selectedReportType.value = type }
    fun setStreetName(name: String)    { _streetName.value = name }

    /**
     * Takes a bitmap from the camera or gallery, runs the TFLite multi-crop
     * classifier, and pre-fills the category dropdown with the result.
     *
     * All five StateFlows (_selectedCategory, _aiLabels, _aiDebugInfo,
     * _isLowConfidence) are updated INSIDE the coroutine, AFTER `result`
     * is assigned. Never reference `result` before the `val result = ...` line.
     */
    fun classifyImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.value = ReportUiState.Loading

            // Runs on Dispatchers.Default inside WasteImageClassifier
            val result = wasteImageClassifier.classify(bitmap)

            // Pre-fill category dropdown with AI's best guess
            _selectedCategory.value = result.category.displayName

            // Store top-5 labels for the AI summary card progress bars
            _aiLabels.value = result.topLabels

            // Store the formatted debug string for display
            _aiDebugInfo.value = result.debugInfo

            // Flag low confidence — triggers the warning card + re-scan prompt
            _isLowConfidence.value = result.lowConfidence

            _uiState.value = ReportUiState.Idle
        }
    }

    /**
     * Fetches the device's current GPS location and reverse-geocodes it to a
     * street name. Populates _location and _streetName.
     * Called automatically when ReportFormScreen first opens.
     */
    fun fetchLocation(context: Context) {
        viewModelScope.launch {
            _isLocating.value = true
            val geoPoint = LocationHelper.getCurrentLocation(context)
            _location.value = geoPoint
            _streetName.value = LocationHelper.getStreetName(context, geoPoint)
            _isLocating.value = false
        }
    }

    /**
     * Builds a WasteReport from the current form state and submits it to Firestore.
     *
     * @param reportedByUid The UID of the currently signed-in user.
     */
    fun submitReport(reportedByUid: String) {
        viewModelScope.launch {
            _uiState.value = ReportUiState.Loading

            val report = WasteReport(
                category   = _selectedCategory.value,
                reportType = _selectedReportType.value,
                location   = _location.value,
                streetName = _streetName.value,
                reportedBy = reportedByUid,
                status     = "pending"
            )

            val result = reportRepository.submitReport(report)

            _uiState.value = if (result.isSuccess) {
                ReportUiState.Success
            } else {
                ReportUiState.Error(result.exceptionOrNull()?.message ?: "Submission failed")
            }
        }
    }

    /** Resets all form fields back to defaults after a successful submission. */
    fun resetForm() {
        _selectedCategory.value  = WasteCategory.MIXED_WASTE.displayName
        _aiLabels.value          = emptyList()
        _aiDebugInfo.value       = ""
        _isLowConfidence.value   = false
        _selectedReportType.value = ReportType.REGULAR_PICKUP.displayName
        _streetName.value        = ""
        _location.value          = GeoPoint(0.0, 0.0)
        _uiState.value           = ReportUiState.Idle
    }

    // ---- Manual DI factory (no Hilt / Dagger — Rule 6) ----
    companion object {
        fun factory(
            reportRepository: ReportRepository,
            wasteImageClassifier: WasteImageClassifier
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReportViewModel(reportRepository, wasteImageClassifier) as T
            }
        }
    }
}