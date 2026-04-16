package com.platform.smartwastemanager.features.collectionpoint.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.platform.smartwastemanager.features.collectionpoint.domain.CollectionPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await

/**
 * Handles all Firestore CRUD operations for user collection points.
 */
class CollectionPointRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val collection = firestore.collection("collection_points")
    private val auth = FirebaseAuth.getInstance()

    /**
     * Returns a real-time stream of all collection points for the current user.
     */
    fun getCurrentUserPoints(): Flow<List<CollectionPoint>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = collection
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            CollectionPoint(
                                id = doc.id,
                                userId = doc.getString("userId") ?: "",
                                name = doc.getString("name") ?: "",
                                location = doc.getGeoPoint("location") ?: GeoPoint(0.0, 0.0),
                                streetName = doc.getString("streetName") ?: "",
                                zoneId = doc.getString("zoneId") ?: "",
                                markedForCollectionDays = (doc.get("markedForCollectionDays") as? List<*>)
                                    ?.filterIsInstance<String>() ?: emptyList(),
                                createdAt = doc.getTimestamp("createdAt") ?: Timestamp.now()
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                    trySend(list)
                }
            }
        awaitClose { listener.remove() }
    }.catch { emit(emptyList()) }

    /**
     * Returns all collection points within a specific zone that are marked for a specific schedule day.
     * Used by drivers to calculate routes.
     */
    fun getPointsInZoneMarkedForDay(zoneId: String, scheduleDayId: String): Flow<List<CollectionPoint>> = callbackFlow {
        val listener = collection
            .whereEqualTo("zoneId", zoneId)
            .whereArrayContains("markedForCollectionDays", scheduleDayId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            CollectionPoint(
                                id = doc.id,
                                userId = doc.getString("userId") ?: "",
                                name = doc.getString("name") ?: "",
                                location = doc.getGeoPoint("location") ?: GeoPoint(0.0, 0.0),
                                streetName = doc.getString("streetName") ?: "",
                                zoneId = doc.getString("zoneId") ?: "",
                                markedForCollectionDays = (doc.get("markedForCollectionDays") as? List<*>)
                                    ?.filterIsInstance<String>() ?: emptyList(),
                                createdAt = doc.getTimestamp("createdAt") ?: Timestamp.now()
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                    trySend(list)
                }
            }
        awaitClose { listener.remove() }
    }.catch { emit(emptyList()) }

    /**
     * Creates a new collection point for the current user.
     */
    suspend fun createCollectionPoint(point: CollectionPoint): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Not signed in"))
            val docRef = collection.document()
            docRef.set(
                mapOf(
                    "id" to docRef.id,
                    "userId" to uid,
                    "name" to point.name,
                    "location" to point.location,
                    "streetName" to point.streetName,
                    "zoneId" to point.zoneId,
                    "markedForCollectionDays" to point.markedForCollectionDays,
                    "createdAt" to Timestamp.now()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates an existing collection point.
     */
    suspend fun updateCollectionPoint(point: CollectionPoint): Result<Unit> {
        return try {
            val ownerUid = auth.currentUser?.uid ?: return Result.failure(Exception("Not signed in"))
            val doc = collection.document(point.id).get().await()
            if (!doc.exists() || doc.getString("userId") != ownerUid) {
                return Result.failure(Exception("You can only edit your own collection points"))
            }
            collection.document(point.id)
                .update(
                    mapOf(
                        "name" to point.name,
                        "location" to point.location,
                        "streetName" to point.streetName,
                        "zoneId" to point.zoneId,
                        "markedForCollectionDays" to point.markedForCollectionDays
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Marks a collection point for a specific schedule day.
     */
    suspend fun markForCollectionDay(pointId: String, scheduleDayId: String): Result<Unit> {
        return try {
            val ownerUid = auth.currentUser?.uid ?: return Result.failure(Exception("Not signed in"))
            val doc = collection.document(pointId).get().await()
            if (!doc.exists() || doc.getString("userId") != ownerUid) {
                return Result.failure(Exception("You can only update your own collection points"))
            }
            val current = (doc.get("markedForCollectionDays") as? List<*>)
                ?.filterIsInstance<String>() ?: emptyList()
            val updated = (current + scheduleDayId).distinct()

            collection.document(pointId)
                .update("markedForCollectionDays", updated)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Unmarks a collection point from a specific schedule day.
     */
    suspend fun unmarkFromCollectionDay(pointId: String, scheduleDayId: String): Result<Unit> {
        return try {
            val ownerUid = auth.currentUser?.uid ?: return Result.failure(Exception("Not signed in"))
            val doc = collection.document(pointId).get().await()
            if (!doc.exists() || doc.getString("userId") != ownerUid) {
                return Result.failure(Exception("You can only update your own collection points"))
            }
            val current = (doc.get("markedForCollectionDays") as? List<*>)
                ?.filterIsInstance<String>() ?: emptyList()
            val updated = current.filter { it != scheduleDayId }

            collection.document(pointId)
                .update("markedForCollectionDays", updated)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPointById(pointId: String): CollectionPoint? {
        return try {
            val doc = collection.document(pointId).get().await()
            val ownerUid = auth.currentUser?.uid ?: return null
            if (!doc.exists() || doc.getString("userId") != ownerUid) return null
            doc.toCollectionPoint()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Deletes a collection point.
     */
    suspend fun deleteCollectionPoint(pointId: String): Result<Unit> {
        return try {
            val ownerUid = auth.currentUser?.uid ?: return Result.failure(Exception("Not signed in"))
            val doc = collection.document(pointId).get().await()
            if (!doc.exists() || doc.getString("userId") != ownerUid) {
                return Result.failure(Exception("You can only delete your own collection points"))
            }
            collection.document(pointId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun DocumentSnapshot.toCollectionPoint(): CollectionPoint? {
        return try {
            CollectionPoint(
                id = id,
                userId = getString("userId") ?: "",
                name = getString("name") ?: "",
                location = getGeoPoint("location") ?: GeoPoint(0.0, 0.0),
                streetName = getString("streetName") ?: "",
                zoneId = getString("zoneId") ?: "",
                markedForCollectionDays = (get("markedForCollectionDays") as? List<*>)
                    ?.filterIsInstance<String>() ?: emptyList(),
                createdAt = getTimestamp("createdAt") ?: Timestamp.now()
            )
        } catch (_: Exception) {
            null
        }
    }
}
