package com.platform.smartwastemanager.core.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Listens for Firebase Cloud Messaging (FCM) events.
 *
 * Two main events are handled:
 * 1. onNewToken — called when the device gets a new FCM token. We save this to Firestore
 *    so the Cloud Function can send push notifications to this device.
 *
 * 2. onMessageReceived — called when a push notification arrives while the app is in the
 *    foreground. We display a local notification to the user.
 *
 * STUB — full implementation comes in Phase 6.
 * This stub is registered in AndroidManifest.xml so the app compiles cleanly.
 */
class SmartWasteFCMService : FirebaseMessagingService() {

    /**
     * Called when FCM assigns a new token to this device.
     * This happens on first app launch, or if the token is refreshed.
     *
     * @param token The new FCM registration token.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // TODO (Phase 6): Save the token to Firestore users/{uid}/fcmToken
        // so the Cloud Function can target this device for push notifications.
    }

    /**
     * Called when a push notification is received while the app is in the foreground.
     *
     * @param message The incoming FCM message containing notification data.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // TODO (Phase 6): Parse message.data and show a local notification
        // using NotificationManagerCompat.
    }
}