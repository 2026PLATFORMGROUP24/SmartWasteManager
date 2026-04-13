package com.platform.smartwastemanager

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.platform.smartwastemanager.core.di.AppContainer
import com.platform.smartwastemanager.core.util.Constants

/**
 * Custom Application class — the first thing that runs when the app starts.
 * Declared in AndroidManifest.xml with android:name=".SmartWasteManagerApp".
 *
 * Responsibilities:
 * - Creates the AppContainer (manual DI) for the app's lifetime.
 * - Sets up the notification channel for local notifications on Android 8.0+.
 */
class SmartWasteManagerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        // Pass 'this' (the Application context) so AppContainer can initialise DataStore
        container = AppContainer(this)

        createNotificationChannel()
    }

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