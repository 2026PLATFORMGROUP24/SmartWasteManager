package com.platform.smartwastemanager.features.notifications.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.platform.smartwastemanager.core.notifications.NotificationHelper
import com.platform.smartwastemanager.core.notifications.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Represents the result of a send operation on the notification screen. */
sealed class NotificationSendState {
    object Idle    : NotificationSendState()
    object Sending : NotificationSendState()
    data class Success(val message: String) : NotificationSendState()
    data class Error(val message: String)   : NotificationSendState()
}

/**
 * ViewModel for the driver-only Notification Centre screen.
 *
 * Two modes per notification type:
 *  - LOCAL  : posts the notification directly on THIS device using NotificationHelper.
 *             Instant, no network needed — great for testing the notification appearance.
 *  - PUSH   : writes a request to Firestore → Cloud Function fans out to all targets.
 *             Tests the real production flow end-to-end.
 */
class NotificationViewModel(
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _sendState = MutableStateFlow<NotificationSendState>(NotificationSendState.Idle)
    val sendState: StateFlow<NotificationSendState> = _sendState.asStateFlow()

    fun resetState() { _sendState.value = NotificationSendState.Idle }

    // =========================================================================
    // LOCAL notifications (this device only)
    // =========================================================================

    /** Shows a "New Waste Report" notification locally on this device. */
    fun testReportNotificationLocal(context: Context, category: String, streetName: String) {
        NotificationHelper.showNewReportNotification(context, category, streetName)
        _sendState.value = NotificationSendState.Success("Report notification shown locally ✅")
    }

    /** Shows a "Collection Reminder" notification locally on this device. */
    fun testReminderNotificationLocal(context: Context, dayOfWeek: String, categories: List<String>) {
        NotificationHelper.showCollectionReminderNotification(context, dayOfWeek, categories)
        _sendState.value = NotificationSendState.Success("Reminder notification shown locally ✅")
    }

    /** Shows an "Announcement" notification locally on this device. */
    fun testAnnouncementNotificationLocal(context: Context, title: String, message: String) {
        NotificationHelper.showAnnouncementNotification(context, title, message)
        _sendState.value = NotificationSendState.Success("Announcement shown locally ✅")
    }

    // =========================================================================
    // PUSH notifications (via Firestore → Cloud Function → FCM → all targets)
    // =========================================================================

    /** Requests a "New Waste Report" push to ALL drivers via the Cloud Function. */
    fun pushReportNotification(category: String, streetName: String) {
        viewModelScope.launch {
            _sendState.value = NotificationSendState.Sending
            val result = notificationRepository.requestNewReportNotification(category, streetName)
            _sendState.value = if (result.isSuccess) {
                NotificationSendState.Success("Push request sent to all drivers ✅")
            } else {
                NotificationSendState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to send push"
                )
            }
        }
    }

    /** Requests a "Collection Reminder" push to ALL users via the Cloud Function. */
    fun pushReminderNotification(dayOfWeek: String, categories: List<String>) {
        viewModelScope.launch {
            _sendState.value = NotificationSendState.Sending
            val result = notificationRepository.requestReminderNotification(dayOfWeek, categories)
            _sendState.value = if (result.isSuccess) {
                NotificationSendState.Success("Reminder push sent to all users ✅")
            } else {
                NotificationSendState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to send push"
                )
            }
        }
    }

    /** Requests an "Announcement" push to ALL users via the Cloud Function. */
    fun pushAnnouncementNotification(title: String, message: String) {
        viewModelScope.launch {
            _sendState.value = NotificationSendState.Sending
            val result = notificationRepository.requestAnnouncementNotification(title, message)
            _sendState.value = if (result.isSuccess) {
                NotificationSendState.Success("Announcement sent to all users ✅")
            } else {
                NotificationSendState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to send push"
                )
            }
        }
    }

    // =========================================================================
    // Factory
    // =========================================================================

    companion object {
        fun factory(notificationRepository: NotificationRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    NotificationViewModel(notificationRepository) as T
            }
    }
}