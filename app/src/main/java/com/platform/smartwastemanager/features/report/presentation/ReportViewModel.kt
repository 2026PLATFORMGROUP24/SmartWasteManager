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
 * - ML Kit classification from a camera bitmap
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

    // The selected report type
    private val _selectedReportType = MutableStateFlow(ReportType.REGULAR_PICKUP.displayName)
    val selectedReportType: StateFlow<String> = _selectedReportType.asStateFlow()

    // GPS location — auto-fetched, not shown to user directly
    private val _location = MutableStateFlow(GeoPoint(0.0, 0.0))

    // Street name — auto-fetched by GPS + geocoding, but user can edit it
    private val _streetName = MutableStateFlow("")
    val streetName: StateFlow<String> = _streetName.asStateFlow()

    // True while we're fetching the GPS location
    private val _isLocating = MutableStateFlow(false)
    val isLocating: StateFlow<Boolean> = _isLocating.asStateFlow()

    // ---- Setters called by the UI ----

    fun setCategory(category: String) { _selectedCategory.value = category }
    fun setReportType(type: String)    { _selectedReportType.value = type }
    fun setStreetName(name: String)    { _streetName.value = name }

    /**
     * Takes a bitmap from the camera, runs ML Kit on it, and pre-fills
     * the category field with the detected WasteCategory.
     * Called from ScanScreen after the user taps "Use This Photo".
     */
    fun classifyImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.value = ReportUiState.Loading
            val category = wasteImageClassifier.classify(bitmap)
            _selectedCategory.value = category.displayName
            _uiState.value = ReportUiState.Idle
        }
    }

    /**
     * Fetches the device's current GPS location and reverse-geocodes it
     * to a street name. Populates _location and _streetName.
     * Called automatically when the ReportFormScreen is first opened.
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
     * @param reportedByUid  The UID of the currently signed-in user.
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

    /** Resets the form back to defaults. Called after a successful submission. */
    fun resetForm() {
        _selectedCategory.value = WasteCategory.MIXED_WASTE.displayName
        _selectedReportType.value = ReportType.REGULAR_PICKUP.displayName
        _streetName.value = ""
        _location.value = GeoPoint(0.0, 0.0)
        _uiState.value = ReportUiState.Idle
    }

    // ---- Manual DI factory (Rule 6 — no Hilt) ----
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