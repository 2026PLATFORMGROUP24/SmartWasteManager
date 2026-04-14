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
 * It is created once inside SmartWasteManagerApp and accessed via
 * (applicationContext as SmartWasteManagerApp).container
 *
 * We now require a Context so that ViewToggleRepository can access DataStore.
 */
class AppContainer(context: Context) {

    // ---- Auth ----
    val authRepository = AuthRepository()

    // ---- Home / Schedules ----
    val scheduleRepository = ScheduleRepository()

    // ---- Driver/User view toggle (persisted via DataStore) ----
    val viewToggleRepository = ViewToggleRepository(context)

    // ---- Waste Reports ----
    val reportRepository = ReportRepository()
    val wasteImageClassifier = WasteImageClassifier(context)

    // ---- Map ----
    val mapRepository = MapRepository()

    // ---- Guides ----
    val guideRepository = GuideRepository()
}