package com.platform.smartwastemanager

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.platform.smartwastemanager.core.di.AppContainer
import com.platform.smartwastemanager.core.util.Constants

/**
 * Custom Application class.
 *
 * This is the entry point of the app BEFORE any Activity starts.
 * It is declared in AndroidManifest.xml with android:name=".SmartWasteManagerApp".
 *
 * Responsibilities:
 * - Creates and holds the AppContainer (manual DI) for the app's lifetime.
 * - Sets up the notification channel required for local notifications on Android 8.0+.
 */
class SmartWasteManagerApp : Application() {

    /**
     * The single AppContainer instance.
     * Access it anywhere via: (applicationContext as SmartWasteManagerApp).container
     */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        // Initialise the dependency injection container
        container = AppContainer()

        // Create the notification channel for local notifications (Android 8.0+)
        createNotificationChannel()
    }

    /**
     * Creates the notification channel required for showing local notifications.
     * Notification channels were introduced in Android 8.0 (API 26).
     * Without a channel, notifications will be silently dropped on API 26+.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                Constants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for waste collection reminders and new reports"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}