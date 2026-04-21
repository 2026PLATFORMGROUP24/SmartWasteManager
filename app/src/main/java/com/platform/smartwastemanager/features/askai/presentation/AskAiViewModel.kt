package com.platform.smartwastemanager.features.askai.presentation

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.platform.smartwastemanager.features.askai.data.AiRepository
import com.platform.smartwastemanager.features.report.domain.WasteImageClassifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for the Ask AI feature.
 */
data class AskAiUiState(
    val capturedImage: Bitmap? = null,
    val identifiedLabel: String = "",
    val aiResponse: String = "",
    val isLoading: Boolean = false,
    val isClassifying: Boolean = false,
    val error: String? = null
)

class AskAiViewModel(
    private val aiRepository: AiRepository,
    private val classifier: WasteImageClassifier
) : ViewModel() {

    private val _uiState = MutableStateFlow(AskAiUiState())
    val uiState: StateFlow<AskAiUiState> = _uiState.asStateFlow()

    /**
     * Set the captured image and classify it locally using TFLite.
     */
    fun onImageCaptured(bitmap: Bitmap) {
        _uiState.value = _uiState.value.copy(
            capturedImage = bitmap,
            isClassifying = true,
            error = null,
            aiResponse = ""
        )

        viewModelScope.launch {
            try {
                val result = classifier.classify(bitmap)
                // Use the top label from the classification result
                val topLabel = result.topLabels.firstOrNull()?.first ?: "Unknown Item"
                _uiState.value = _uiState.value.copy(
                    identifiedLabel = topLabel,
                    isClassifying = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    identifiedLabel = "Classification Failed",
                    isClassifying = false,
                    error = "Local classification failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Update the label if the user manually changes it.
     */
    fun onLabelChanged(newLabel: String) {
        _uiState.value = _uiState.value.copy(identifiedLabel = newLabel)
    }

    /**
     * Sends the prompt and current label to Gemini.
     */
    fun askAi(prompt: String) {
        if (_uiState.value.identifiedLabel.isBlank()) return

        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                val response = aiRepository.getAiResponse(_uiState.value.identifiedLabel, prompt)
                _uiState.value = _uiState.value.copy(
                    aiResponse = response,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun reset() {
        _uiState.value = AskAiUiState()
    }

    companion object {
        fun factory(aiRepository: AiRepository, classifier: WasteImageClassifier) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AskAiViewModel(aiRepository, classifier) as T
            }
        }
    }
}
