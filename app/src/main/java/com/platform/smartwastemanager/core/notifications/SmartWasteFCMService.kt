package com.platform.smartwastemanager.core.notifications

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.platform.smartwastemanager.core.util.Constants

/**
 * Firebase Cloud Messaging service.
 *
 * Registered in AndroidManifest.xml. Handles two events:
 *
 * 1. onNewToken — FCM assigns a fresh device token (first launch, token rotation, reinstall).
 *    We save the token to the signed-in user's Firestore document so the Cloud Function
 *    can target this device for push notifications.
 *
 * 2. onMessageReceived — a push message arrives while the app is in the FOREGROUND.
 *    (When the app is in the background / killed, FCM delivers the notification directly
 *    to the system tray without calling this method.)
 *    We parse the message's data payload and call NotificationHelper to show a local
 *    notification inside the app.
 *
 * Expected FCM data payload keys (set by the Cloud Function):
 *   type        : "new_report" | "announcement"
 *   title       : notification headline
 *   body        : notification body text
 *   category    : waste category  (new_report only)
 *   streetName  : street name     (new_report only)
 */
class SmartWasteFCMService : FirebaseMessagingService() {

    // =========================================================================
    // Token refresh
    // =========================================================================

    /**
     * Called when FCM issues a new registration token for this device.
     * Saves the token to Firestore so the Cloud Function can push to this device.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        saveTokenToFirestore(token)
    }

    /**
     * Writes the FCM token to users/{uid}/fcmToken in Firestore.
     * If no user is signed in yet the token will be saved on the next sign-in
     * (AuthRepository.signIn also refreshes the token via updateFcmToken).
     */
    private fun saveTokenToFirestore(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        Firebase.firestore
            .collection(Constants.COLLECTION_USERS)
            .document(uid)
            .update(Constants.FIELD_FCM_TOKEN, token)
        // Ignore failures — the token will be refreshed next time the user signs in
    }

    // =========================================================================
    // Foreground message handling
    // =========================================================================

    /**
     * Called when a push message arrives while the app is open in the foreground.
     * Parses the data payload and delegates to NotificationHelper.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data  = message.data
        val type  = data["type"] ?: "announcement"
        val title = data["title"] ?: message.notification?.title ?: "Smart Waste Manager"
        val body  = data["body"]  ?: message.notification?.body  ?: ""

        when (type) {
            "new_report" -> {
                val category   = data["category"]   ?: "Unknown"
                val streetName = data["streetName"] ?: "Unknown location"
                NotificationHelper.showNewReportNotification(this, category, streetName)
            }
            "announcement" -> {
                NotificationHelper.showAnnouncementNotification(this, title, body)
            }
            "reminder" -> {
                val day        = data["dayOfWeek"]  ?: "tomorrow"
                val categories = data["categories"]
                    ?.split(",")
                    ?.map { it.trim() }
                    ?: emptyList()
                NotificationHelper.showCollectionReminderNotification(this, day, categories)
            }
            else -> {
                // Unknown type — show as a generic announcement
                if (title.isNotBlank() || body.isNotBlank()) {
                    NotificationHelper.showAnnouncementNotification(this, title, body)
                }
            }
        }
    }
}