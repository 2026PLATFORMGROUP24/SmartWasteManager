package com.platform.smartwastemanager.core.di

import android.content.Context
import com.platform.smartwastemanager.features.aiassist.data.AiAssistRepository
import com.platform.smartwastemanager.core.util.ViewToggleRepository
import com.platform.smartwastemanager.features.auth.data.AuthRepository
import com.platform.smartwastemanager.features.guide.data.GuideRepository
import com.platform.smartwastemanager.features.home.data.ScheduleRepository
import com.platform.smartwastemanager.features.map.data.MapRepository
import com.platform.smartwastemanager.features.report.data.ReportRepository
import com.platform.smartwastemanager.features.report.domain.WasteImageClassifier
import com.platform.smartwastemanager.core.notifications.NotificationRepository
import com.platform.smartwastemanager.features.announcement.data.AnnouncementRepository
import com.platform.smartwastemanager.features.collectionpoint.data.CollectionPointRepository

/**
 * AppContainer holds every repository for the lifetime of the app.
 * Created once in SmartWasteManagerApp.onCreate() and accessed via
 * (applicationContext as SmartWasteManagerApp).container
 */
class AppContainer(context: Context) {

    // ---- Auth ----
    val authRepository = AuthRepository()

    // ---- Home / Schedules ----
    val scheduleRepository = ScheduleRepository()

    // ---- Driver/User view toggle (persisted via DataStore) ----
    val viewToggleRepository = ViewToggleRepository(context)

    // ---- Waste Reports ----
    val reportRepository     = ReportRepository()
    val wasteImageClassifier = WasteImageClassifier(context)

    // ---- Map + Routes (shared MapRepository — one Firestore connection) ----
    val mapRepository = MapRepository()

    // ---- Guides ----
    val guideRepository = GuideRepository()

    // ---- Notifications ----
    val notificationRepository = NotificationRepository()

    // ---- Announcements ----
    val announcementRepository = AnnouncementRepository()

    // ---- Collection Points (zone-based system) ----
    val collectionPointRepository = CollectionPointRepository()

    // ---- AI Assist (Ask AI feature — free Gemini 1.5 Flash API) ----
    val aiAssistRepository = AiAssistRepository()
}