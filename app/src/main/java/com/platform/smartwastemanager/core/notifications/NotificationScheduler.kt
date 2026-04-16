package com.platform.smartwastemanager.core.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * NotificationScheduler — schedules and cancels the daily collection reminder.
 *
 * How the timing works:
 *  - We want the reminder to fire every day at 19:00 (7 PM).
 *  - We calculate the delay from NOW to the next 19:00 and pass it as
 *    setInitialDelay() on the builder. After that first fire, the worker
 *    repeats every 24 hours.
 *  - The KEEP policy means if the worker is already scheduled (e.g. app
 *    restarted) we do not reset the timer.
 */
object NotificationScheduler {

    private const val WORK_NAME    = "collection_reminder_daily"
    private const val REMINDER_HOUR = 19   // 7 PM local time

    /**
     * Schedules the daily 7 PM collection reminder.
     * Safe to call multiple times — KEEP policy prevents duplicate work.
     */
    fun scheduleDailyReminder(context: Context) {
        val initialDelayMs = calculateDelayToNextReminder()

        val workRequest = PeriodicWorkRequestBuilder<CollectionReminderWorker>(
            repeatInterval     = 24L,
            repeatIntervalTimeUnit = TimeUnit.HOURS        // correct parameter name
        )
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    /** Cancels the daily reminder. */
    fun cancelDailyReminder(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /**
     * Calculates milliseconds from now until the next 19:00 local time.
     * If 19:00 has already passed today, targets tomorrow's 19:00.
     */
    private fun calculateDelayToNextReminder(): Long {
        val now    = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, REMINDER_HOUR)
            set(Calendar.MINUTE,      0)
            set(Calendar.SECOND,      0)
            set(Calendar.MILLISECOND, 0)
        }

        // If 19:00 has already passed today, move target to tomorrow
        if (now.after(target)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        return target.timeInMillis - now.timeInMillis
    }
}