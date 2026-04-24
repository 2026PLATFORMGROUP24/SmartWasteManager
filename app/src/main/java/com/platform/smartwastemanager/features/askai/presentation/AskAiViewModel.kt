package com.platform.smartwastemanager.features.askai.presentation

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.platform.smartwastemanager.features.askai.data.AiChat
import com.platform.smartwastemanager.features.askai.data.AiChatRepository
import com.platform.smartwastemanager.features.askai.data.ModelDownloadManager
import com.platform.smartwastemanager.features.askai.data.ModelDownloadState
import com.platform.smartwastemanager.features.askai.data.SmolLmRepository
import com.platform.smartwastemanager.features.report.domain.WasteImageClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Represents the main screen the user is viewing inside Ask AI */
enum class AskAiScreenMode {
    LANDING,
    CAMERA,
    CHAT,
    HISTORY_LIST,
    HISTORY_CHAT
}

data class AskAiUiState(
    // ── Image / classification ──────────────────────────────────────────────
    val capturedImage: Bitmap? = null,
    val identifiedLabel: String = "",
    val isClassifying: Boolean = false,

    // ── AI response ─────────────────────────────────────────────────────────
    /** Tokens arriving in real-time; shown as the typewriter bubble. */
    val streamingResponse: String = "",
    val isStreaming: Boolean = false,
    /** Full assembled response stored after streaming completes. */
    val latestAiResponse: String = "",
    val isLoading: Boolean = false,

    // ── Chat / Firestore ────────────────────────────────────────────────────
    val currentChat: AiChat? = null,
    val chatHistory: List<AiChat> = emptyList(),
    val selectedHistoryChat: AiChat? = null,
    val isCreatingChat: Boolean = false,

    // ── Screen navigation ───────────────────────────────────────────────────
    val screenMode: AskAiScreenMode = AskAiScreenMode.LANDING,

    // ── Model state ─────────────────────────────────────────────────────────
    /** False when the device doesn't meet minimum requirements (API 29 + 4 GB RAM). */
    val isDeviceSupported: Boolean = true,
    val isModelReady: Boolean = false,
    /** True while LlmInference.createFromOptions() is running in the background. */
    val isModelInitializing: Boolean = false,
    val isModelDownloading: Boolean = false,
    val modelDownloadProgress: Int = 0,
    /** True once the user has tapped "Download" — suppresses the consent card. */
    val downloadStarted: Boolean = false,

    // ── Errors ───────────────────────────────────────────────────────────────
    val error: String? = null,
    val modelError: String? = null
)

class AskAiViewModel(
    application: Application,
    private val aiChatRepository: AiChatRepository,
    private val classifier: WasteImageClassifier,
    private val smolLmRepository: SmolLmRepository,
    private val modelDownloadManager: ModelDownloadManager
) : AndroidViewModel(application) {

    private val TAG = "AskAiViewModel"

    private val _uiState = MutableStateFlow(AskAiUiState())
    val uiState: StateFlow<AskAiUiState> = _uiState.asStateFlow()

    private var currentUserId: String = ""

    /**
     * True once the user has manually corrected the label via [onLabelChanged].
     * Prevents the ML classification result (which may arrive asynchronously AFTER
     * the user's edit) from overwriting the corrected label.
     * Reset to false each time a new image is captured.
     */
    private var hasUserEditedLabel: Boolean = false

    // ──────────────────────────────────────────────────────────────────────────
    // Init
    // ──────────────────────────────────────────────────────────────────────────

    fun init(userId: String) {
        if (currentUserId == userId) return
        currentUserId = userId

        val supported = checkDeviceSupport()
        // Vertex AI works on any device — mark supported + ready immediately,
        // suppress the old "download model" consent card by setting downloadStarted = true.
        _uiState.value = _uiState.value.copy(
            isDeviceSupported = supported,
            isModelReady = true,
            downloadStarted = true
        )

        if (!supported) return

        loadChatHistory()
    }

    /** Checks that the device runs Android 10+ and has at least 4 GB RAM. */
    private fun checkDeviceSupport(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val am = getApplication<Application>()
            .getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        return totalRamGb >= 4.0
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Model download & initialisation
    // ──────────────────────────────────────────────────────────────────────────

    /** Called when the user taps the "Download AI Model" consent button. */
    fun startDownload() {
        _uiState.value = _uiState.value.copy(downloadStarted = true)
        viewModelScope.launch {
            modelDownloadManager.downloadModel().collect { state ->
                when (state) {
                    is ModelDownloadState.Idle -> Unit
                    is ModelDownloadState.Downloading -> {
                        _uiState.value = _uiState.value.copy(
                            isModelDownloading = true,
                            modelDownloadProgress = state.progress,
                            modelError = null
                        )
                    }
                    is ModelDownloadState.Ready -> {
                        _uiState.value = _uiState.value.copy(
                            isModelDownloading = false,
                            modelDownloadProgress = 100
                        )
                        initializeModel(state.path)
                    }
                    is ModelDownloadState.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isModelDownloading = false,
                            downloadStarted = false, // allow retry
                            modelError = state.message
                        )
                    }
                }
            }
        }
    }

    private fun initializeModel(modelPath: String) {
        _uiState.value = _uiState.value.copy(isModelInitializing = true)
        viewModelScope.launch(Dispatchers.Default) {
            try {
                smolLmRepository.initialize(getApplication(), modelPath)
                _uiState.value = _uiState.value.copy(
                    isModelInitializing = false,
                    isModelReady = true
                )
                Log.d(TAG, "Model ready")
            } catch (e: Exception) {
                Log.e(TAG, "Model initialisation failed", e)
                _uiState.value = _uiState.value.copy(
                    isModelInitializing = false,
                    modelError = "Failed to load AI model: ${e.message}"
                )
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Chat history
    // ──────────────────────────────────────────────────────────────────────────

    private fun loadChatHistory() {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            aiChatRepository.getChatsForUser(currentUserId).collect { chats ->
                _uiState.value = _uiState.value.copy(chatHistory = chats)

                val selectedId = _uiState.value.selectedHistoryChat?.chatId
                if (selectedId != null) {
                    _uiState.value = _uiState.value.copy(
                        selectedHistoryChat = chats.firstOrNull { it.chatId == selectedId }
                    )
                }
                val activeChatId = _uiState.value.currentChat?.chatId
                if (activeChatId != null) {
                    chats.firstOrNull { it.chatId == activeChatId }?.let { refreshed ->
                        // Always refresh the chat document itself (messages, promptCount, etc.)
                        // but only sync identifiedLabel from wasteLabel if the user hasn't
                        // manually edited it — preventing the Firestore refresh from resetting
                        // a user correction.
                        val syncedLabel = if (hasUserEditedLabel)
                            _uiState.value.identifiedLabel
                        else
                            refreshed.wasteLabel.ifBlank { _uiState.value.identifiedLabel }
                        _uiState.value = _uiState.value.copy(
                            currentChat = refreshed,
                            identifiedLabel = syncedLabel
                        )
                    }
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Image capture & classification
    // ──────────────────────────────────────────────────────────────────────────

    fun onImageCaptured(bitmap: Bitmap) {
        // Reset the user-edit guard whenever a brand-new image is captured
        hasUserEditedLabel = false
        _uiState.value = _uiState.value.copy(
            capturedImage = bitmap,
            isClassifying = true,
            error = null,
            latestAiResponse = "",
            streamingResponse = "",
            screenMode = AskAiScreenMode.CHAT
        )
        viewModelScope.launch {
            try {
                val result = classifier.classify(bitmap)
                val topLabel = result.topLabels.firstOrNull()?.first ?: "Unknown Item"
                // Only apply the ML result if the user hasn't already corrected the label
                if (!hasUserEditedLabel) {
                    _uiState.value = _uiState.value.copy(
                        identifiedLabel = topLabel,
                        isClassifying = false
                    )
                } else {
                    // Classification finished but user already edited — just clear the spinner
                    _uiState.value = _uiState.value.copy(isClassifying = false)
                }
                createChatSession(bitmap, topLabel)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    identifiedLabel = if (!hasUserEditedLabel) "Classification Failed"
                                      else _uiState.value.identifiedLabel,
                    isClassifying = false,
                    error = "Classification failed: ${e.message}"
                )
            }
        }
    }

    private suspend fun createChatSession(bitmap: Bitmap, label: String) {
        if (currentUserId.isBlank()) return
        _uiState.value = _uiState.value.copy(isCreatingChat = true)
        try {
            val tempId = System.currentTimeMillis().toString()
            val imageUrl = try {
                aiChatRepository.uploadImage(currentUserId, tempId, bitmap)
            } catch (e: Exception) { "" }
            val result = aiChatRepository.createChat(currentUserId, imageUrl, label)
            if (result.isSuccess) {
                val chatId = result.getOrNull()!!

                // If the user corrected the label while the image was uploading,
                // the chat was created in Firestore with the ML label. Update it now.
                val effectiveLabel = if (hasUserEditedLabel) {
                    val corrected = _uiState.value.identifiedLabel
                    if (corrected != label) {
                        aiChatRepository.updateWasteLabel(chatId, corrected)
                    }
                    corrected
                } else {
                    label
                }

                val newChat = AiChat(
                    chatId = chatId,
                    userId = currentUserId,
                    imageUrl = imageUrl,
                    wasteLabel = effectiveLabel,
                    messages = emptyList(),
                    promptCount = 0
                )
                _uiState.value = _uiState.value.copy(currentChat = newChat, isCreatingChat = false)
            } else {
                _uiState.value = _uiState.value.copy(isCreatingChat = false)
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isCreatingChat = false)
        }
    }

    fun onLabelChanged(newLabel: String) {
        hasUserEditedLabel = true
        _uiState.value = _uiState.value.copy(identifiedLabel = newLabel)
        // Persist immediately if the chat already exists in Firestore.
        // If createChatSession hasn't finished yet (chatId is null), the update
        // will be applied inside createChatSession once it completes.
        val chatId = _uiState.value.currentChat?.chatId
        if (!chatId.isNullOrBlank()) {
            viewModelScope.launch {
                aiChatRepository.updateWasteLabel(chatId, newLabel)
            }
        }
        // If chatId is null here, hasUserEditedLabel=true ensures createChatSession
        // will pick up the corrected label and update Firestore when it finishes.
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Ask AI (streaming)
    // ──────────────────────────────────────────────────────────────────────────

    fun askAi(prompt: String) {
        val chat = _uiState.value.currentChat
        if (chat != null && chat.isClosed) return
        if (_uiState.value.identifiedLabel.isBlank()) return
        if (!_uiState.value.isModelReady) return

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            isStreaming = true,
            streamingResponse = "",
            error = null
        )

        viewModelScope.launch {
            // 1. Persist user message
            if (chat != null) {
                aiChatRepository.addMessage(
                    chat.chatId, "user", prompt, incrementPromptCount = true
                )
            }

            // 2. Stream tokens from on-device model
            val fullResponse = StringBuilder()
            try {
                smolLmRepository
                    .getAiResponseStream(_uiState.value.identifiedLabel, prompt)
                    .collect { token ->
                        fullResponse.append(token)
                        _uiState.value = _uiState.value.copy(
                            streamingResponse = fullResponse.toString()
                        )
                    }

                val finalText = fullResponse.toString().trim()

                // 3. Persist AI response & update fallback
                if (chat != null) {
                    aiChatRepository.addMessage(
                        chat.chatId, "ai", finalText, incrementPromptCount = false
                    )
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isStreaming = false,
                    streamingResponse = "",
                    latestAiResponse = finalText
                )
            } catch (e: Exception) {
                Log.e(TAG, "Streaming failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isStreaming = false,
                    streamingResponse = "",
                    error = "AI Error: ${e.localizedMessage ?: "An unexpected error occurred."}"
                )
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Navigation helpers
    // ──────────────────────────────────────────────────────────────────────────

    fun showHistory() {
        _uiState.value = _uiState.value.copy(screenMode = AskAiScreenMode.HISTORY_LIST)
    }

    fun showCamera() {
        _uiState.value = _uiState.value.copy(screenMode = AskAiScreenMode.CAMERA)
    }

    fun openHistoryChat(chat: AiChat) {
        _uiState.value = _uiState.value.copy(
            selectedHistoryChat = chat,
            screenMode = AskAiScreenMode.HISTORY_CHAT
        )
    }

    fun continueHistoryChat(chat: AiChat) {
        _uiState.value = _uiState.value.copy(
            currentChat = chat,
            identifiedLabel = chat.wasteLabel,
            capturedImage = null,
            screenMode = AskAiScreenMode.CHAT
        )
    }

    fun backFromHistoryChat() {
        _uiState.value = _uiState.value.copy(
            screenMode = AskAiScreenMode.HISTORY_LIST,
            selectedHistoryChat = null
        )
    }

    fun backFromHistoryList() {
        _uiState.value = _uiState.value.copy(screenMode = AskAiScreenMode.LANDING)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, modelError = null)
    }

    fun reset() {
        hasUserEditedLabel = false
        _uiState.value = _uiState.value.copy(
            capturedImage = null,
            identifiedLabel = "",
            isClassifying = false,
            isLoading = false,
            isStreaming = false,
            streamingResponse = "",
            latestAiResponse = "",
            error = null,
            currentChat = null,
            selectedHistoryChat = null,
            screenMode = AskAiScreenMode.LANDING,
            isCreatingChat = false
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cleanup
    // ──────────────────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        smolLmRepository.close()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Factory
    // ──────────────────────────────────────────────────────────────────────────

    companion object {
        fun factory(
            application: Application,
            aiChatRepository: AiChatRepository,
            classifier: WasteImageClassifier
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                AskAiViewModel(
                    application        = application,
                    aiChatRepository   = aiChatRepository,
                    classifier         = classifier,
                    smolLmRepository   = SmolLmRepository(),
                    modelDownloadManager = ModelDownloadManager(application)
                ) as T
        }
    }
}
