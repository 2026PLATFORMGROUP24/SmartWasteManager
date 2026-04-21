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
 * All reports are always "Regular Pickup" — there is no report type selection.
 * The reportType field is hardcoded and never exposed to the UI.
 *
 * Manages:
 * - Form field values (category, streetName, location)
 * - TFLite classification from a camera bitmap
 * - GPS location fetching and reverse geocoding
 * - Manual map-based location picking
 * - Firestore submission via ReportRepository
 *
 * Manual location guard:
 *   [setManualLocation] sets [_isManualLocation] = true.
 *   [fetchLocation] is guarded by the form's LaunchedEffect checks on [isManualLocation],
 *   so GPS never overwrites a manually chosen pin on recompose.
 *   [clearManualAndFetchGps] resets the flag and fetches fresh GPS — called by the
 *   GPS refresh button in ReportFormScreen when the user explicitly wants to revert.
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

    /**
     * The last bitmap classified by [classifyImage].
     * Exposed so the Ask AI tab can display and upload the image without
     * the ViewModel needing to know about the AI assist feature.
     */
    private val _lastClassifiedBitmap = MutableStateFlow<Bitmap?>(null)
    val lastClassifiedBitmap: StateFlow<Bitmap?> = _lastClassifiedBitmap.asStateFlow()

    // Report type is always "Regular Pickup" — not user-selectable.
    // Stored as a private constant; never exposed as a StateFlow.
    private val reportType = "Regular Pickup"

    // ---- Location state ----

    /**
     * The GeoPoint that will actually be saved to Firestore.
     * Updated by [fetchLocation] (GPS auto) or [setManualLocation] (map pick).
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
     *
     * When true the form's LaunchedEffect blocks skip [fetchLocation],
     * preventing the manually chosen pin from being overwritten on recompose.
     * Reset to false by [resetForm] and [clearManualAndFetchGps].
     */
    private val _isManualLocation = MutableStateFlow(false)
    val isManualLocation: StateFlow<Boolean> = _isManualLocation.asStateFlow()

    // ---- Setters ----

    fun setCategory(category: String) { _selectedCategory.value = category }
    fun setStreetName(name: String)   { _streetName.value = name }

    /**
     * Called when the user confirms a location on LocationPickerMapScreen.
     *
     * Stores the picked [LatLng] as a [GeoPoint], marks the location as manually
     * chosen, and reverse-geocodes the coordinates to fill the street name field.
     * After this call the form's LaunchedEffect blocks will NOT call [fetchLocation],
     * preserving these coordinates until [resetForm] or [clearManualAndFetchGps].
     *
     * @param context  Needed for the Geocoder reverse-lookup.
     * @param latLng   The coordinates the user confirmed on the map.
     */
    fun setManualLocation(context: Context, latLng: LatLng) {
        viewModelScope.launch {
            _isLocating.value = true
            val geoPoint = GeoPoint(latLng.latitude, latLng.longitude)
            _location.value = geoPoint
            // Set the flag BEFORE the geocode so it is true even during the async wait
            _isManualLocation.value = true
            _streetName.value = LocationHelper.getStreetName(context, geoPoint)
            _isLocating.value = false
        }
    }

    /**
     * Fetches the device's current GPS location and reverse-geocodes it.
     *
     * Always resets [_isManualLocation] to false so a fresh GPS position
     * replaces any previous manual pin.
     */
    fun fetchLocation(context: Context) {
        viewModelScope.launch {
            _isLocating.value = true
            _isManualLocation.value = false   // revert to GPS mode
            val geoPoint = LocationHelper.getCurrentLocation(context)
            _location.value = geoPoint
            _streetName.value = LocationHelper.getStreetName(context, geoPoint)
            _isLocating.value = false
        }
    }

    /**
     * Explicitly clears the manual-location flag and fetches a fresh GPS location.
     *
     * Called by the GPS refresh button (↺) in ReportFormScreen when the user
     * deliberately wants to revert from a manually picked pin back to their
     * device's current location. Unlike the guarded LaunchedEffect blocks,
     * this always runs regardless of [_isManualLocation].
     *
     * Delegates to [fetchLocation] which already resets [_isManualLocation].
     */
    fun clearManualAndFetchGps(context: Context) {
        fetchLocation(context)
    }

    /**
     * Classifies a bitmap using the TFLite waste classifier and pre-fills
     * the category dropdown with the result.
     */
    fun classifyImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.value = ReportUiState.Loading
            _lastClassifiedBitmap.value = bitmap
            val result = wasteImageClassifier.classify(bitmap)
            _selectedCategory.value = result.category.displayName
            _aiLabels.value         = result.topLabels
            _aiDebugInfo.value      = result.debugInfo
            _isLowConfidence.value  = result.lowConfidence
            _uiState.value = ReportUiState.Idle
        }
    }

    /**
     * Builds a WasteReport from current form state and submits it to Firestore.
     * reportType is always "Regular Pickup" — never user-supplied.
     */
    fun submitReport(reportedByUid: String) {
        viewModelScope.launch {
            _uiState.value = ReportUiState.Loading
            val report = WasteReport(
                category   = _selectedCategory.value,
                reportType = reportType,           // always "Regular Pickup"
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
        _selectedCategory.value = WasteCategory.MIXED_WASTE.displayName
        _aiLabels.value         = emptyList()
        _aiDebugInfo.value      = ""
        _isLowConfidence.value  = false
        _lastClassifiedBitmap.value = null
        // reportType needs no reset — it is a fixed constant
        _streetName.value       = ""
        _location.value         = GeoPoint(0.0, 0.0)
        _isManualLocation.value = false
        _uiState.value          = ReportUiState.Idle
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