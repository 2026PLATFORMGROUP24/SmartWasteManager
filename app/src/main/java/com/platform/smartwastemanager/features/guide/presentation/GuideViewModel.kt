package com.platform.smartwastemanager.features.guide.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.platform.smartwastemanager.features.guide.data.GuideRepository
import com.platform.smartwastemanager.features.guide.domain.RecyclingGuide
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ---- UI state for the guide list ----
sealed class GuideListUiState {
    object Loading : GuideListUiState()
    data class Success(val guides: List<RecyclingGuide>) : GuideListUiState()
    data class Error(val message: String) : GuideListUiState()
}

// ---- UI state for a single guide detail ----
sealed class GuideDetailUiState {
    object Idle    : GuideDetailUiState()
    object Loading : GuideDetailUiState()
    data class Success(val guide: RecyclingGuide) : GuideDetailUiState()
    data class Error(val message: String) : GuideDetailUiState()
}

// ---- UI state for create / update operations ----
sealed class GuideSaveUiState {
    object Idle   : GuideSaveUiState()
    object Saving : GuideSaveUiState()
    data class Success(val guideId: String) : GuideSaveUiState()
    data class Error(val message: String) : GuideSaveUiState()
}

/**
 * ViewModel for all recycling guide screens (list, detail, editor).
 *
 * Design notes:
 * - Created ONCE in MainActivity (Rule 3). Never call viewModel() inside a composable.
 * - loadGuides() is called from authViewModel.onAuthSuccess (Rule 5).
 * - [guides] exposes the raw list so ScheduleManagementScreen can use it for the
 *   guide-linking dropdown without its own Firestore listener.
 */
class GuideViewModel(
    private val guideRepository: GuideRepository
) : ViewModel() {

    // ---- Guide list ----
    private val _listUiState = MutableStateFlow<GuideListUiState>(GuideListUiState.Loading)
    val listUiState: StateFlow<GuideListUiState> = _listUiState.asStateFlow()

    // Raw list also consumed by ScheduleManagementScreen for the guide picker dropdown
    private val _guides = MutableStateFlow<List<RecyclingGuide>>(emptyList())
    val guides: StateFlow<List<RecyclingGuide>> = _guides.asStateFlow()

    // ---- Guide detail ----
    private val _detailUiState = MutableStateFlow<GuideDetailUiState>(GuideDetailUiState.Idle)
    val detailUiState: StateFlow<GuideDetailUiState> = _detailUiState.asStateFlow()

    // ---- Save (create / update) ----
    private val _saveUiState = MutableStateFlow<GuideSaveUiState>(GuideSaveUiState.Idle)
    val saveUiState: StateFlow<GuideSaveUiState> = _saveUiState.asStateFlow()

    // ---- Delete ----
    private val _deleteSuccess = MutableStateFlow(false)
    val deleteSuccess: StateFlow<Boolean> = _deleteSuccess.asStateFlow()

    private var listJob: Job? = null

    init { loadGuides() }

    // =========================================================================
    // LIST
    // =========================================================================

    /** Starts (or restarts) the real-time Firestore listener for all guides. */
    fun loadGuides() {
        listJob?.cancel()
        listJob = viewModelScope.launch {
            _listUiState.value = GuideListUiState.Loading
            guideRepository.getGuides().collect { guides ->
                _guides.value = guides
                // Rule 7: if data arrives while state is Error, reset to Success not Error
                _listUiState.value = GuideListUiState.Success(guides)
            }
        }
    }

    // =========================================================================
    // DETAIL
    // =========================================================================

    fun loadGuideById(guideId: String) {
        viewModelScope.launch {
            _detailUiState.value = GuideDetailUiState.Loading
            val result = guideRepository.getGuideById(guideId)
            _detailUiState.value = if (result.isSuccess) {
                GuideDetailUiState.Success(result.getOrThrow())
            } else {
                GuideDetailUiState.Error(
                    result.exceptionOrNull()?.message ?: "Could not load guide"
                )
            }
        }
    }

    // =========================================================================
    // CREATE
    // =========================================================================

    /**
     * Creates a new guide. Images are uploaded AFTER the Firestore document is created
     * so we have a real document ID to use as the Storage folder name.
     *
     * @param guide        Guide to create. [guide.id] is ignored — Firestore auto-assigns one.
     * @param newImageUris Local URIs from the gallery picker, not yet uploaded.
     */
    /**
     * Creates a new guide. Images are uploaded AFTER the Firestore document is created
     * so we have a real document ID to use as the Storage folder name.
     *
     * @param guide        Guide to create. [guide.id] is ignored — Firestore auto-assigns one.
     * @param newImageUris Local URIs from the gallery picker, not yet uploaded.
     */
    fun createGuide(guide: RecyclingGuide, newImageUris: List<Uri>) {
        viewModelScope.launch {
            _saveUiState.value = GuideSaveUiState.Saving

            try {
                // Step 1: Create the guide document first (to get a real Firestore ID)
                val createResult = guideRepository.createGuide(guide)

                if (createResult.isFailure) {
                    _saveUiState.value = GuideSaveUiState.Error(
                        createResult.exceptionOrNull()?.message ?: "Failed to create guide"
                    )
                    return@launch
                }

                val newGuideId = createResult.getOrNull() ?: ""

                // Step 2: Upload images using the new guide ID
                val uploadedUrls = uploadImages(newGuideId, newImageUris)

                // Step 3: Update the guide with the image URLs if any were uploaded
                if (uploadedUrls.isNotEmpty()) {
                    val guideWithImages = guide.copy(
                        id = newGuideId,
                        imageUrls = uploadedUrls
                    )
                    guideRepository.updateGuide(guideWithImages)
                }

                // Step 4: Set success state
                _saveUiState.value = GuideSaveUiState.Success(newGuideId)

            } catch (e: Exception) {
                _saveUiState.value = GuideSaveUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetSaveState() {
        _saveUiState.value = GuideSaveUiState.Idle
    }
    // =========================================================================
    // UPDATE
    // =========================================================================

    /**
     * Updates an existing guide. [newImageUris] are uploaded and appended to
     * the existing [guide.imageUrls] list.
     */
    fun updateGuide(guide: RecyclingGuide, newImageUris: List<Uri> = emptyList()) {
        viewModelScope.launch {
            _saveUiState.value = GuideSaveUiState.Saving
            val uploadedUrls = uploadImages(guide.id, newImageUris)
            val finalGuide   = guide.copy(imageUrls = guide.imageUrls + uploadedUrls)
            val result       = guideRepository.updateGuide(finalGuide)
            _saveUiState.value = if (result.isSuccess) {
                GuideSaveUiState.Success(guide.id)
            } else {
                GuideSaveUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to update guide"
                )
            }
        }
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    fun deleteGuide(guideId: String) {
        viewModelScope.launch {
            guideRepository.deleteGuide(guideId)
            _deleteSuccess.value = true
        }
    }

    // =========================================================================
    // STATE RESETS  (call from DisposableEffect when leaving a screen)
    // =========================================================================


    fun resetDeleteSuccess() { _deleteSuccess.value = false }
    fun resetDetailState()   { _detailUiState.value = GuideDetailUiState.Idle }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /** Uploads all [uris] and returns the download URLs of successful uploads. */
    private suspend fun uploadImages(guideId: String, uris: List<Uri>): List<String> =
        uris.mapNotNull { uri -> guideRepository.uploadImage(guideId, uri).getOrNull() }

    // =========================================================================
    // FACTORY
    // =========================================================================

    companion object {
        fun factory(guideRepository: GuideRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    GuideViewModel(guideRepository) as T
            }
    }
}