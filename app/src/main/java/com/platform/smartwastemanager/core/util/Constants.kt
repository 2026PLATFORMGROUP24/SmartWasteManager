package com.platform.smartwastemanager.core.util

/**
 * App-wide constants. Put any "magic strings" here so they're easy to find and update.
 */
object Constants {

    // ---- Firestore Collection Names ----
    const val COLLECTION_USERS = "users"
    const val COLLECTION_SCHEDULES = "schedules"
    const val COLLECTION_WASTE_REPORTS = "waste_reports"
    const val COLLECTION_RECYCLING_GUIDES = "recycling_guides"

    // ---- Firestore Field Names ----
    const val FIELD_ROLE = "role"
    const val FIELD_FCM_TOKEN = "fcmToken"
    const val FIELD_STATUS = "status"
    const val FIELD_STATUS_PENDING = "pending"
    const val FIELD_STATUS_DISMISSED = "dismissed"

    // ---- Firebase Storage Paths ----
    const val STORAGE_GUIDE_IMAGES = "guide_images"

    // ---- DataStore Keys ----
    const val DATASTORE_NAME = "smart_waste_prefs"
    const val KEY_DRIVER_VIEW_ACTIVE = "driver_view_active"

    // ---- Notification Channel ----
    const val NOTIFICATION_CHANNEL_ID = "smart_waste_channel"
    const val NOTIFICATION_CHANNEL_NAME = "Smart Waste Notifications"

    // ---- Days of the Week ----
    val DAYS_OF_WEEK = listOf(
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    )

    // ---- AI Model (SmoLLM2 on-device) ----
    /** Firebase Storage path where the GGUF model is hosted.
     *  NOTE: MediaPipe LlmInference only supports Q4_0, Q4_K_M, and Q6_K quantizations.
     *  Q8_0 is NOT supported and causes a native SIGSEGV crash. */
    const val AI_MODEL_STORAGE_PATH = "ai_models/smollm2-360m-instruct-q4_k_m.gguf"
    /** Local file name written to filesDir/models/ after download. */
    const val AI_MODEL_FILE_NAME    = "smollm2-360m-q4km.gguf"
    /** Minimum file size (bytes) to consider a local model valid (~200 MB for Q4_K_M). */
    const val AI_MODEL_MIN_SIZE_BYTES = 150_000_000L
    /** Legacy file names that should be deleted if found (old/incompatible downloads). */
    val AI_MODEL_LEGACY_FILE_NAMES = listOf("smollm2-360m.gguf")
}
