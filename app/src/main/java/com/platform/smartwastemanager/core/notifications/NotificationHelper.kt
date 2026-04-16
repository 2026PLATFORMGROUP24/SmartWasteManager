package com.platform.smartwastemanager.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.platform.smartwastemanager.MainActivity
import com.platform.smartwastemanager.R
import com.platform.smartwastemanager.core.util.Constants

/**
 * NotificationHelper — the single place in the app where local notifications are built
 * and displayed. Called from:
 *  - SmartWasteFCMService   (foreground push messages)
 *  - CollectionReminderWorker (WorkManager evening reminder)
 *
 * Notification channels (Android 8+):
 *  CHANNEL_REPORTS      — new waste report alerts (for drivers)
 *  CHANNEL_REMINDERS    — collection day reminders (for all users)
 *  CHANNEL_ANNOUNCEMENTS — general announcements from drivers
 */
object NotificationHelper {

    // ---- Channel IDs ----
    const val CHANNEL_REPORTS       = "channel_reports"
    const val CHANNEL_REMINDERS     = "channel_reminders"
    const val CHANNEL_ANNOUNCEMENTS = "channel_announcements"

    // ---- Notification ID ranges (prevents collisions) ----
    private const val ID_REPORT_BASE       = 1000
    private const val ID_REMINDER          = 2000
    private const val ID_ANNOUNCEMENT_BASE = 3000

    // ---- Running counter for report notifications (each report gets its own notification) ----
    private var reportCounter = 0
    private var announcementCounter = 0

    /**
     * Creates all notification channels. Call this once in Application.onCreate().
     * Safe to call multiple times — Android ignores duplicate channel creation.
     */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REPORTS,
                "New Waste Reports",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when a resident submits a new waste report"
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS,
                "Collection Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Evening reminders about tomorrow's waste collection"
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ANNOUNCEMENTS,
                "Announcements",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General announcements from collection staff"
            }
        )
    }

    /**
     * Posts a "New Waste Report" notification. Shown to drivers when a resident submits
     * a report (delivered via FCM push while the app is in the foreground, or triggered
     * manually from the driver test screen).
     *
     * @param category   The waste category reported (e.g. "Recyclable").
     * @param streetName The street name where the report was made.
     */
    fun showNewReportNotification(context: Context, category: String, streetName: String) {
        val notification = buildNotification(
            context   = context,
            channelId = CHANNEL_REPORTS,
            title     = "🗑️ New Waste Report",
            body      = "$category reported at $streetName — tap to view on map"
        )
        safeNotify(context, ID_REPORT_BASE + (reportCounter++ % 50), notification)
    }

    /**
     * Posts a "Collection Tomorrow" reminder notification. Fired by WorkManager every
     * evening when there is a scheduled collection the next day.
     *
     * @param dayOfWeek  E.g. "Tuesday".
     * @param categories List of waste types being collected (e.g. ["Recyclable", "Glass"]).
     */
    fun showCollectionReminderNotification(
        context: Context,
        dayOfWeek: String,
        categories: List<String>
    ) {
        val categoryText = categories.joinToString(", ").ifBlank { "Waste" }
        val notification = buildNotification(
            context   = context,
            channelId = CHANNEL_REMINDERS,
            title     = "♻️ Collection Tomorrow — $dayOfWeek",
            body      = "Put out your $categoryText bin tonight for collection tomorrow morning."
        )
        safeNotify(context, ID_REMINDER, notification)
    }

    /**
     * Posts a general announcement notification from a driver.
     *
     * @param title   Short headline chosen by the driver.
     * @param message Full announcement body text.
     */
    fun showAnnouncementNotification(context: Context, title: String, message: String) {
        val notification = buildNotification(
            context   = context,
            channelId = CHANNEL_ANNOUNCEMENTS,
            title     = "📢 $title",
            body      = message
        )
        safeNotify(context, ID_ANNOUNCEMENT_BASE + (announcementCounter++ % 20), notification)
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Builds a standard notification with a tap-to-open-app PendingIntent. */
    private fun buildNotification(
        context: Context,
        channelId: String,
        title: String,
        body: String
    ): android.app.Notification {
        // Tapping the notification opens MainActivity
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)   // see step 9 — add this drawable
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    /**
     * Posts the notification safely — checks that the POST_NOTIFICATIONS permission
     * is granted on Android 13+ before calling notify().
     */
    private fun safeNotify(context: Context, id: Int, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS permission not granted — silently skip
        }
    }
}