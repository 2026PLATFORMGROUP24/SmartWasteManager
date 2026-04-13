package com.platform.smartwastemanager.features.guide.data

import com.platform.smartwastemanager.features.guide.domain.RecyclingGuide
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Handles CRUD for recycling guides in Firestore and image uploads to Firebase Storage.
 * STUB — full implementation comes in Phase 5.
 */
class GuideRepository {

    /** Returns a live stream of all recycling guides. */
    fun getGuides(): Flow<List<RecyclingGuide>> = flow {
        // TODO (Phase 5): Listen to recycling_guides collection
        emit(emptyList())
    }

    /** Returns a single guide by its Firestore document ID. */
    suspend fun getGuideById(guideId: String): Result<RecyclingGuide> {
        // TODO (Phase 5): Fetch single document from recycling_guides/{guideId}
        return Result.failure(NotImplementedError("GuideRepository.getGuideById not yet implemented"))
    }

    /** Creates a new guide in Firestore. */
    suspend fun createGuide(guide: RecyclingGuide): Result<Unit> {
        // TODO (Phase 5): Implement Firestore add()
        return Result.failure(NotImplementedError("GuideRepository.createGuide not yet implemented"))
    }

    /** Updates an existing guide. */
    suspend fun updateGuide(guide: RecyclingGuide): Result<Unit> {
        // TODO (Phase 5): Implement Firestore set()
        return Result.failure(NotImplementedError("GuideRepository.updateGuide not yet implemented"))
    }

    /** Deletes a guide and its images from Storage. */
    suspend fun deleteGuide(guideId: String): Result<Unit> {
        // TODO (Phase 5): Delete Firestore doc + all Storage images
        return Result.failure(NotImplementedError("GuideRepository.deleteGuide not yet implemented"))
    }
}