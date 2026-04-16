package com.platform.smartwastemanager

import android.app.Application
import com.platform.smartwastemanager.core.di.AppContainer
import com.platform.smartwastemanager.core.notifications.NotificationHelper
import com.platform.smartwastemanager.core.notifications.NotificationScheduler

/**
 * Custom Application class — first thing that runs when the app starts.
 *
 * Phase 6 additions:
 *  - NotificationHelper.createChannels() replaces the old inline channel setup.
 *  - NotificationScheduler.scheduleDailyReminder() starts the 7 PM WorkManager job.
 */
class SmartWasteManagerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        // Manual DI — single AppContainer for the app's lifetime (Rule 2)
        container = AppContainer(this)

        // Create all three notification channels (reports, reminders, announcements)
        NotificationHelper.createChannels(this)

        // Schedule the daily 7 PM collection reminder via WorkManager
        // Uses KEEP policy — safe to call on every app start
        NotificationScheduler.scheduleDailyReminder(this)
    }
}