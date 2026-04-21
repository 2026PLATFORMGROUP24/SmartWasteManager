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
    const val COLLECTION_AI_ASSIST_HISTORY = "ai_assist_history"

    // ---- Firestore Field Names ----
    const val FIELD_ROLE = "role"
    const val FIELD_FCM_TOKEN = "fcmToken"
    const val FIELD_STATUS = "status"
    const val FIELD_STATUS_PENDING = "pending"
    const val FIELD_STATUS_DISMISSED = "dismissed"

    // ---- Firebase Storage Paths ----
    const val STORAGE_GUIDE_IMAGES = "guide_images"
    const val STORAGE_AI_ASSIST_IMAGES = "ai_assist_images"

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
}