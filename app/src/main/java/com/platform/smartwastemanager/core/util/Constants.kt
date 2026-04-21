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

    // ---- AI Settings ----
    // TODO: Replace with your actual Gemini API Key from Google AI Studio
    const val GEMINI_API_KEY = "AIzaSyBCFGHdMaB56M4-7CPAs0bLQtBpsfcD_9g"
    const val GEMINI_MODEL_NAME = "gemini-2.5-flash"
}
