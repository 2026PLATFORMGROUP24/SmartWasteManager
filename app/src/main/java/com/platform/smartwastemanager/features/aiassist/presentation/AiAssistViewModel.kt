package com.platform.smartwastemanager.features.aiassist.presentation

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.platform.smartwastemanager.features.aiassist.data.AiAssistRepository
import com.platform.smartwastemanager.features.aiassist.domain.AiAssistEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI state for an in-progress Ask AI session. */
sealed class AiAssistUiState {
    object Idle    : AiAssistUiState()
    object Loading : AiAssistUiState()
    data class Success(val response: String) : AiAssistUiState()
    data class Error(val message: String) : AiAssistUiState()
}

/**
 * ViewModel for the Ask AI feature.
 *
 * Lifecycle of a session:
 *  1. Labels arrive from the TFLite classifier (via [setInitialLabels]).
 *  2. User edits labels → [addLabel] / [removeLabel].
 *  3. User confirms labels → [confirmLabels].
 *  4. User types / picks a quick prompt → [setPrompt].
 *  5. User taps Send → [sendPrompt] (one-time per session).
 *  6. Response is displayed; entry is saved to Firestore.
 *  7. User can view history via [history] flow.
 */
class AiAssistViewModel(
    private val repository: AiAssistRepository
) : ViewModel() {

    // ---- Session state ----

    /** The bitmap captured during the scan (may be null for manual entry). */
    private val _scannedBitmap = MutableStateFlow<Bitmap?>(null)
    val scannedBitmap: StateFlow<Bitmap?> = _scannedBitmap.asStateFlow()

    /** Labels shown in the editing chip list. */
    private val _editableLabels = MutableStateFlow<List<String>>(emptyList())
    val editableLabels: StateFlow<List<String>> = _editableLabels.asStateFlow()

    /** True once the user has tapped "Looks good — confirm labels". */
    private val _labelsConfirmed = MutableStateFlow(false)
    val labelsConfirmed: StateFlow<Boolean> = _labelsConfirmed.asStateFlow()

    /** The current text in the prompt field. */
    private val _promptText = MutableStateFlow("")
    val promptText: StateFlow<String> = _promptText.asStateFlow()

    /** True after the user has sent one prompt. Prevents further sends. */
    private val _promptSent = MutableStateFlow(false)
    val promptSent: StateFlow<Boolean> = _promptSent.asStateFlow()

    /** UI state for the in-flight Gemini call. */
    private val _uiState = MutableStateFlow<AiAssistUiState>(AiAssistUiState.Idle)
    val uiState: StateFlow<AiAssistUiState> = _uiState.asStateFlow()

    // ---- History state ----

    private val _history = MutableStateFlow<List<AiAssistEntry>>(emptyList())
    val history: StateFlow<List<AiAssistEntry>> = _history.asStateFlow()

    // ---- Session setters ----

    fun setScannedBitmap(bitmap: Bitmap?) {
        _scannedBitmap.value = bitmap
    }

    /** Initialises the editable label list from raw TFLite output. */
    fun setInitialLabels(rawLabels: List<Pair<String, Float>>) {
        _editableLabels.value = rawLabels.map { it.first }
        _labelsConfirmed.value = false
        _promptSent.value = false
        _uiState.value = AiAssistUiState.Idle
    }

    fun addLabel(label: String) {
        val trimmed = label.trim()
        if (trimmed.isNotEmpty() && trimmed !in _editableLabels.value) {
            _editableLabels.value = _editableLabels.value + trimmed
        }
    }

    fun removeLabel(label: String) {
        _editableLabels.value = _editableLabels.value - label
    }

    fun updateLabel(oldLabel: String, newLabel: String) {
        val trimmed = newLabel.trim()
        if (trimmed.isEmpty()) {
            removeLabel(oldLabel)
            return
        }
        _editableLabels.value = _editableLabels.value.map {
            if (it == oldLabel) trimmed else it
        }
    }

    fun confirmLabels() {
        _labelsConfirmed.value = true
    }

    fun setPrompt(text: String) {
        _promptText.value = text
    }

    // ---- Core action ----

    /**
     * Uploads the image (if any), calls Gemini, saves the result to Firestore,
     * and updates [uiState] / [history].
     *
     * Can only be called once per session — [_promptSent] guards repeated taps.
     */
    fun sendPrompt(userId: String) {
        if (_promptSent.value) return
        val prompt = _promptText.value.trim()
        if (prompt.isEmpty()) return

        _promptSent.value = true
        _uiState.value = AiAssistUiState.Loading

        viewModelScope.launch {
            // 1. Upload image if we have one
            val imageUrl = _scannedBitmap.value?.let { bmp ->
                repository.uploadImage(bmp, userId)
            } ?: ""

            // 2. Ask Gemini
            val response = repository.askGemini(_editableLabels.value, prompt)

            // 3. Save to Firestore
            val entry = AiAssistEntry(
                imageUrl  = imageUrl,
                labels    = _editableLabels.value,
                prompt    = prompt,
                response  = response,
                userId    = userId
            )
            repository.saveEntry(entry)

            _uiState.value = AiAssistUiState.Success(response)
        }
    }

    // ---- History ----

    /** Starts listening to this user's history. Call once when the history screen opens. */
    fun loadHistory(userId: String) {
        viewModelScope.launch {
            repository.getHistory(userId).collect { entries ->
                _history.value = entries
            }
        }
    }

    // ---- Reset ----

    /** Resets all session state so the ViewModel can be reused for a new scan. */
    fun resetSession() {
        _scannedBitmap.value = null
        _editableLabels.value = emptyList()
        _labelsConfirmed.value = false
        _promptText.value = ""
        _promptSent.value = false
        _uiState.value = AiAssistUiState.Idle
    }

    // ---- Factory ----

    companion object {
        fun factory(repository: AiAssistRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AiAssistViewModel(repository) as T
            }
    }
}
