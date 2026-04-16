package com.platform.smartwastemanager.core.notifications

import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.platform.smartwastemanager.core.util.Constants
import kotlinx.coroutines.tasks.await

/**
 * NotificationRepository — handles the server-side part of push notifications.
 *
 * ARCHITECTURE NOTE — Why no Cloud Function here?
 * ─────────────────────────────────────────────────
 * Sending FCM messages from a device requires the FCM Server Key, which must
 * NEVER be embedded in an APK (it would be visible to anyone who decompiles it).
 *
 * The correct architecture is:
 *   Device  →  writes to Firestore  →  Cloud Function reads it  →  calls FCM API
 *
 * This repository writes "notification_requests" documents to Firestore.
 * A Cloud Function (index.js, provided below) watches that collection and
 * calls the FCM HTTP v1 API on behalf of the app.
 *
 * For the DRIVER TEST SCREEN, we use the same Firestore-trigger approach so the
 * security model is identical to production use.
 *
 * Collections used:
 *  notification_requests/{docId}  — written by this repo, read by Cloud Function
 *  users/{uid}                    — read to collect FCM tokens
 */
class NotificationRepository {

    private val firestore = Firebase.firestore

    // =========================================================================
    // FCM Token Management
    // =========================================================================

    /**
     * Saves the current device's FCM token to users/{uid}/fcmToken.
     * Called from SmartWasteFCMService.onNewToken() and after sign-in.
     */
    suspend fun saveFcmToken(uid: String, token: String): Result<Unit> {
        return try {
            firestore.collection(Constants.COLLECTION_USERS)
                .document(uid)
                .update(Constants.FIELD_FCM_TOKEN, token)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // Push via Firestore → Cloud Function trigger
    // =========================================================================

    /**
     * Requests a "new waste report" push notification to be sent to ALL drivers.
     *
     * Writes a document to notification_requests. The Cloud Function picks it up
     * and fans out FCM messages to every driver's fcmToken.
     *
     * @param category   Waste category (e.g. "Recyclable").
     * @param streetName Street where the report was made.
     * @param reportId   Firestore document ID of the waste report (for deep-linking).
     */
    suspend fun requestNewReportNotification(
        category: String,
        streetName: String,
        reportId: String = ""
    ): Result<Unit> {
        return writeNotificationRequest(
            mapOf(
                "type"       to "new_report",
                "target"     to "drivers",          // Cloud Function fans out to all drivers
                "title"      to "🗑️ New Waste Report",
                "body"       to "$category reported at $streetName",
                "category"   to category,
                "streetName" to streetName,
                "reportId"   to reportId
            )
        )
    }

    /**
     * Requests an announcement push notification to be sent to ALL users (drivers + residents).
     *
     * @param title   Short headline.
     * @param message Full announcement body.
     */
    suspend fun requestAnnouncementNotification(
        title: String,
        message: String
    ): Result<Unit> {
        return writeNotificationRequest(
            mapOf(
                "type"    to "announcement",
                "target"  to "all",                 // Cloud Function fans out to everyone
                "title"   to title,
                "body"    to message
            )
        )
    }

    /**
     * Requests a collection reminder notification.
     * Used by the driver test screen to force-push a reminder to ALL users.
     *
     * @param dayOfWeek  Day name, e.g. "Tuesday".
     * @param categories List of categories being collected, e.g. ["Recyclable", "Glass"].
     */
    suspend fun requestReminderNotification(
        dayOfWeek: String,
        categories: List<String>
    ): Result<Unit> {
        return writeNotificationRequest(
            mapOf(
                "type"       to "reminder",
                "target"     to "all",
                "title"      to "♻️ Collection Tomorrow — $dayOfWeek",
                "body"       to "Put out your ${categories.joinToString(", ")} bin tonight.",
                "dayOfWeek"  to dayOfWeek,
                "categories" to categories.joinToString(",")
            )
        )
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Writes a notification request document to Firestore.
     * The Cloud Function watches this collection and calls the FCM API.
     *
     * A "processed: false" flag is included so the Cloud Function can mark it
     * "processed: true" once the FCM call is complete (idempotency guard).
     */
    private suspend fun writeNotificationRequest(data: Map<String, Any>): Result<Unit> {
        return try {
            val payload = data.toMutableMap()
            payload["processed"]  = false
            payload["createdAt"]  = com.google.firebase.Timestamp.now()
            firestore.collection("notification_requests").add(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}