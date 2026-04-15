package com.platform.smartwastemanager.core.di

import android.content.Context
import com.platform.smartwastemanager.core.util.ViewToggleRepository
import com.platform.smartwastemanager.features.auth.data.AuthRepository
import com.platform.smartwastemanager.features.guide.data.GuideRepository
import com.platform.smartwastemanager.features.home.data.ScheduleRepository
import com.platform.smartwastemanager.features.map.data.MapRepository
import com.platform.smartwastemanager.features.report.data.ReportRepository
import com.platform.smartwastemanager.features.report.domain.WasteImageClassifier

/**
 * AppContainer holds every repository for the lifetime of the app.
 * Created once in SmartWasteManagerApp.onCreate() and accessed via
 * (applicationContext as SmartWasteManagerApp).container
 *
 * Context is required for ViewToggleRepository (DataStore) and WasteImageClassifier.
 *
 * NOTE: mapRepository and scheduleRepository are both passed to RouteViewModel —
 * RouteViewModel now needs scheduleRepository to update zoneIds on schedule docs.
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
}