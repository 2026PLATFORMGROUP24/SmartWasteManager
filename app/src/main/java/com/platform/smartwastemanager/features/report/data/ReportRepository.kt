package com.platform.smartwastemanager.features.report.data

import com.platform.smartwastemanager.features.report.domain.WasteReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Handles waste report operations in Firestore.
 * STUB — full implementation comes in Phase 4.
 */
class ReportRepository {

    /** Saves a new waste report to Firestore. */
    suspend fun submitReport(report: WasteReport): Result<Unit> {
        // TODO (Phase 4): Implement Firestore add() to waste_reports collection
        return Result.failure(NotImplementedError("ReportRepository.submitReport not yet implemented"))
    }

    /** Returns a live stream of all PENDING reports (used by the map). */
    fun getPendingReports(): Flow<List<WasteReport>> = flow {
        // TODO (Phase 4): Listen to waste_reports where status == "pending"
        emit(emptyList())
    }

    /** Updates a report's status to "dismissed" (driver action). */
    suspend fun dismissReport(reportId: String): Result<Unit> {
        // TODO (Phase 4): Implement Firestore update() setting status = "dismissed"
        return Result.failure(NotImplementedError("ReportRepository.dismissReport not yet implemented"))
    }
}