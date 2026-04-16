package com.platform.smartwastemanager.core.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.util.Calendar

/**
 * WorkManager worker — runs once every evening (scheduled by NotificationScheduler).
 *
 * What it does:
 *  1. Determines what day of the week tomorrow is.
 *  2. Queries Firestore for any schedule entries on that day.
 *  3. If entries exist → posts a "Collection Tomorrow" local notification.
 *  4. If no entries  → does nothing (no redundant notifications).
 *
 * This worker runs entirely on-device and does NOT require a Cloud Function.
 */
class CollectionReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val tomorrowDay = getTomorrowDayName()

            // Query Firestore for schedules that match tomorrow's day of week
            val snapshot = Firebase.firestore
                .collection("schedules")
                .whereEqualTo("dayOfWeek", tomorrowDay)
                .get()
                .await()

            if (snapshot.documents.isNotEmpty()) {
                // Collect all waste categories across all matching schedule entries
                val allCategories = snapshot.documents.flatMap { doc ->
                    @Suppress("UNCHECKED_CAST")
                    (doc.get("wasteCategories") as? List<*>)
                        ?.filterIsInstance<String>()
                        ?: emptyList()
                }.distinct()

                NotificationHelper.showCollectionReminderNotification(
                    context    = context,
                    dayOfWeek  = tomorrowDay,
                    categories = allCategories
                )
            }

            Result.success()
        } catch (e: Exception) {
            // Retry once on failure (e.g. no network)
            Result.retry()
        }
    }

    /**
     * Returns the full English name of tomorrow's day (e.g. "Tuesday").
     * Uses the device calendar — no timezone library needed for a simple day name.
     */
    private fun getTomorrowDayName(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY    -> "Monday"
            Calendar.TUESDAY   -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY  -> "Thursday"
            Calendar.FRIDAY    -> "Friday"
            Calendar.SATURDAY  -> "Saturday"
            Calendar.SUNDAY    -> "Sunday"
            else               -> "Monday"
        }
    }
}