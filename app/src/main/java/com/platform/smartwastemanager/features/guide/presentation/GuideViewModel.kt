package com.platform.smartwastemanager.features.guide.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.platform.smartwastemanager.features.guide.data.GuideRepository
import com.platform.smartwastemanager.features.guide.domain.GuideContentType
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

class GuideViewModel(
    private val guideRepository: GuideRepository
) : ViewModel() {

    // ---- Guide list ----
    private val _listUiState = MutableStateFlow<GuideListUiState>(GuideListUiState.Loading)
    val listUiState: StateFlow<GuideListUiState> = _listUiState.asStateFlow()

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

    fun loadGuides() {
        listJob?.cancel()
        listJob = viewModelScope.launch {
            _listUiState.value = GuideListUiState.Loading
            guideRepository.getGuides().collect { guides ->
                _guides.value = guides
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

    fun createGuide(
        guide: RecyclingGuide,
        newImageUris: List<Uri> = emptyList()
    ) {
        viewModelScope.launch {
            _saveUiState.value = GuideSaveUiState.Saving

            try {
                // Step 1: Create the guide document first
                val createResult = guideRepository.createGuide(guide)

                if (createResult.isFailure) {
                    _saveUiState.value = GuideSaveUiState.Error(
                        createResult.exceptionOrNull()?.message ?: "Failed to create guide"
                    )
                    return@launch
                }

                val newGuideId = createResult.getOrNull() ?: ""

                // Step 2: Handle content type-specific uploads
                val finalGuide = when (guide.getContentType()) {
                    GuideContentType.MARKDOWN -> {
                        if (newImageUris.isNotEmpty()) {
                            val uploadedUrls = uploadImages(newGuideId, newImageUris)
                            guide.copy(id = newGuideId, imageUrls = uploadedUrls)
                        } else {
                            guide.copy(id = newGuideId)
                        }
                    }
                    GuideContentType.YOUTUBE -> guide.copy(id = newGuideId)
                }

                // Step 3: Update the document with uploaded URLs if needed
                if (finalGuide != guide.copy(id = newGuideId)) {
                    guideRepository.updateGuide(finalGuide)
                }

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

    fun updateGuide(
        guide: RecyclingGuide,
        newImageUris: List<Uri> = emptyList()
    ) {
        viewModelScope.launch {
            _saveUiState.value = GuideSaveUiState.Saving

            val finalGuide = when (guide.getContentType()) {
                GuideContentType.MARKDOWN -> {
                    val uploadedUrls = uploadImages(guide.id, newImageUris)
                    guide.copy(imageUrls = guide.imageUrls + uploadedUrls)
                }
                GuideContentType.YOUTUBE -> guide
            }

            val result = guideRepository.updateGuide(finalGuide)
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
    // STATE RESETS
    // =========================================================================

    fun resetDeleteSuccess() { _deleteSuccess.value = false }
    fun resetDetailState()   { _detailUiState.value = GuideDetailUiState.Idle }

    // =========================================================================
    // HELPERS
    // =========================================================================

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
