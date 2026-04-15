package com.platform.smartwastemanager.features.report.presentation

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
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
 * - Manual map-based location picking (new)
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

    private val _selectedCategory = MutableStateFlow(WasteCategory.MIXED_WASTE.displayName)
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _aiLabels = MutableStateFlow<List<Pair<String, Float>>>(emptyList())
    val aiLabels: StateFlow<List<Pair<String, Float>>> = _aiLabels.asStateFlow()

    private val _aiDebugInfo = MutableStateFlow("")
    val aiDebugInfo: StateFlow<String> = _aiDebugInfo.asStateFlow()

    private val _isLowConfidence = MutableStateFlow(false)
    val isLowConfidence: StateFlow<Boolean> = _isLowConfidence.asStateFlow()

    private val _selectedReportType = MutableStateFlow(ReportType.REGULAR_PICKUP.displayName)
    val selectedReportType: StateFlow<String> = _selectedReportType.asStateFlow()

    // ---- Location state ----

    /**
     * The GPS GeoPoint that will actually be saved to Firestore.
     * Updated by fetchLocation() (GPS auto) or setManualLocation() (map pick).
     */
    private val _location = MutableStateFlow(GeoPoint(0.0, 0.0))
    val location: StateFlow<GeoPoint> = _location.asStateFlow()

    /**
     * The human-readable street name shown in the form and saved to Firestore.
     * Auto-resolved via Geocoder from whichever [_location] was set.
     * The user can also type/edit it directly.
     */
    private val _streetName = MutableStateFlow("")
    val streetName: StateFlow<String> = _streetName.asStateFlow()

    /** True while a GPS fix or reverse-geocode operation is in progress. */
    private val _isLocating = MutableStateFlow(false)
    val isLocating: StateFlow<Boolean> = _isLocating.asStateFlow()

    /**
     * True when the user has manually pinned a location on the map
     * instead of relying on auto-GPS.
     * The form uses this to show an appropriate status label.
     */
    private val _isManualLocation = MutableStateFlow(false)
    val isManualLocation: StateFlow<Boolean> = _isManualLocation.asStateFlow()

    // ---- Setters ----

    fun setCategory(category: String)  { _selectedCategory.value = category }
    fun setReportType(type: String)    { _selectedReportType.value = type }
    fun setStreetName(name: String)    { _streetName.value = name }

    /**
     * Called when the user confirms a location on the LocationPickerMapScreen.
     *
     * Stores the picked [LatLng] as a [GeoPoint], marks the location as manually
     * chosen, and reverse-geocodes the coordinates to fill the street name field.
     *
     * @param context  Needed for the Geocoder reverse-lookup.
     * @param latLng   The coordinates the user pinned on the map.
     */
    fun setManualLocation(context: Context, latLng: LatLng) {
        viewModelScope.launch {
            _isLocating.value = true
            val geoPoint = GeoPoint(latLng.latitude, latLng.longitude)
            _location.value = geoPoint
            _isManualLocation.value = true
            // Reverse-geocode so the street name field auto-fills from the pin
            _streetName.value = LocationHelper.getStreetName(context, geoPoint)
            _isLocating.value = false
        }
    }

    /**
     * Fetches the device's current GPS location and reverse-geocodes it.
     * Clears the manual-location flag — reverts to auto-GPS mode.
     */
    fun fetchLocation(context: Context) {
        viewModelScope.launch {
            _isLocating.value = true
            _isManualLocation.value = false   // back to GPS mode
            val geoPoint = LocationHelper.getCurrentLocation(context)
            _location.value = geoPoint
            _streetName.value = LocationHelper.getStreetName(context, geoPoint)
            _isLocating.value = false
        }
    }

    /**
     * Classifies a bitmap using the TFLite waste classifier and pre-fills
     * the category dropdown with the result.
     */
    fun classifyImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.value = ReportUiState.Loading
            val result = wasteImageClassifier.classify(bitmap)
            _selectedCategory.value  = result.category.displayName
            _aiLabels.value          = result.topLabels
            _aiDebugInfo.value       = result.debugInfo
            _isLowConfidence.value   = result.lowConfidence
            _uiState.value = ReportUiState.Idle
        }
    }

    /**
     * Builds a WasteReport from current form state and submits it to Firestore.
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

    /** Resets all form fields back to their defaults after a successful submission. */
    fun resetForm() {
        _selectedCategory.value   = WasteCategory.MIXED_WASTE.displayName
        _aiLabels.value           = emptyList()
        _aiDebugInfo.value        = ""
        _isLowConfidence.value    = false
        _selectedReportType.value = ReportType.REGULAR_PICKUP.displayName
        _streetName.value         = ""
        _location.value           = GeoPoint(0.0, 0.0)
        _isManualLocation.value   = false
        _uiState.value            = ReportUiState.Idle
    }

    // ---- Manual DI factory ----
    companion object {
        fun factory(
            reportRepository: ReportRepository,
            wasteImageClassifier: WasteImageClassifier
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ReportViewModel(reportRepository, wasteImageClassifier) as T
        }
    }
}