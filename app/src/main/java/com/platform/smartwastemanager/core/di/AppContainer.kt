package com.platform.smartwastemanager.core.di

import com.platform.smartwastemanager.features.auth.data.AuthRepository
import com.platform.smartwastemanager.features.guide.data.GuideRepository
import com.platform.smartwastemanager.features.home.data.ScheduleRepository
import com.platform.smartwastemanager.features.map.data.MapRepository
import com.platform.smartwastemanager.features.report.data.ReportRepository
import com.platform.smartwastemanager.features.report.domain.WasteImageClassifier

/**
 * AppContainer is the heart of our manual dependency injection system.
 *
 * Instead of using Hilt or Dagger, we create ONE instance of each repository here.
 * This instance lives for the entire lifetime of the app (as long as the Application is alive).
 *
 * ViewModels get their repositories through a ViewModelProvider.Factory that
 * reads from this container — see each feature's ViewModel for examples.
 *
 * To add a new repository:
 * 1. Create the repository class in its feature/data/ package.
 * 2. Add a val property here.
 * 3. Use it in the corresponding ViewModel factory.
 */
class AppContainer {

    // ---- Auth ----
    val authRepository = AuthRepository()

    // ---- Home / Schedules ----
    val scheduleRepository = ScheduleRepository()

    // ---- Waste Reports ----
    val reportRepository = ReportRepository()
    val wasteImageClassifier = WasteImageClassifier()

    // ---- Map ----
    val mapRepository = MapRepository()

    // ---- Guides ----
    val guideRepository = GuideRepository()
}