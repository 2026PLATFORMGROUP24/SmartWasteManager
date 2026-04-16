package com.platform.smartwastemanager.features.announcement.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.platform.smartwastemanager.core.notifications.NotificationRepository
import com.platform.smartwastemanager.features.announcement.data.AnnouncementRepository
import com.platform.smartwastemanager.features.announcement.domain.Announcement
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class AnnouncementViewModel(
    private val announcementRepository: AnnouncementRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _announcements = MutableStateFlow<List<Announcement>>(emptyList())
    val announcements: StateFlow<List<Announcement>> = _announcements.asStateFlow()

    private val _uiState = MutableStateFlow<AnnouncementUiState>(AnnouncementUiState.Idle)
    val uiState: StateFlow<AnnouncementUiState> = _uiState.asStateFlow()

    private var announcementsJob: Job? = null

    init {
        loadAnnouncements()
    }

    fun loadAnnouncements() {
        announcementsJob?.cancel()
        announcementsJob = viewModelScope.launch {
            try {
                announcementRepository.getAnnouncements()
                    .catch { _ ->
                        if (_announcements.value.isEmpty()) {
                            _uiState.value = AnnouncementUiState.Error(
                                "Could not load announcements. Check your connection."
                            )
                        }
                        emit(emptyList())
                    }
                    .collect { list ->
                        _announcements.value = list
                        if (list.isNotEmpty() && _uiState.value is AnnouncementUiState.Error) {
                            _uiState.value = AnnouncementUiState.Idle
                        }
                    }
            } catch (_: Exception) {
                if (_announcements.value.isEmpty()) {
                    _uiState.value = AnnouncementUiState.Error(
                        "Could not load announcements. Check your connection."
                    )
                }
            }
        }
    }

    /**
     * Creates an announcement AND sends a push notification to all users.
     * This combines persistent storage with immediate push delivery.
     */
    fun createAnnouncement(
        title: String,
        message: String,
        driverUid: String
    ) {
        if (title.isBlank() || message.isBlank()) {
            _uiState.value = AnnouncementUiState.Error("Title and message cannot be empty")
            return
        }
        viewModelScope.launch {
            _uiState.value = AnnouncementUiState.Loading

            // Save to Firestore (persistent)
            val saveResult = announcementRepository.createAnnouncement(
                Announcement(
                    title = title,
                    message = message,
                    createdBy = driverUid
                )
            )

            if (saveResult.isFailure) {
                _uiState.value = AnnouncementUiState.Error(
                    saveResult.exceptionOrNull()?.message ?: "Failed to create announcement"
                )
                return@launch
            }

            // Send push notification to all users (immediate)
            val pushResult = notificationRepository.requestAnnouncementNotification(title, message)

            _uiState.value = if (pushResult.isSuccess) {
                AnnouncementUiState.Success(
                    "📢 Announcement created and sent to all users!"
                )
            } else {
                // Announcement was saved but push failed - still show success
                AnnouncementUiState.Success(
                    "Announcement created (push notification may have failed)"
                )
            }
        }
    }

    fun deleteAnnouncement(announcementId: String) {
        viewModelScope.launch {
            _uiState.value = AnnouncementUiState.Loading
            val result = announcementRepository.deleteAnnouncement(announcementId)
            _uiState.value = if (result.isSuccess)
                AnnouncementUiState.Success("Announcement deleted")
            else
                AnnouncementUiState.Error(result.exceptionOrNull()?.message ?: "Failed to delete announcement")
        }
    }

    fun resetUiState() {
        _uiState.value = AnnouncementUiState.Idle
    }

    companion object {
        fun factory(
            announcementRepository: AnnouncementRepository,
            notificationRepository: NotificationRepository
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AnnouncementViewModel(
                        announcementRepository,
                        notificationRepository
                    ) as T
                }
            }
        }
    }
}

sealed class AnnouncementUiState {
    object Idle : AnnouncementUiState()
    object Loading : AnnouncementUiState()
    data class Success(val message: String) : AnnouncementUiState()
    data class Error(val message: String) : AnnouncementUiState()
}