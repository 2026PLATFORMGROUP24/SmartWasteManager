package com.platform.smartwastemanager.features.auth.data

import com.platform.smartwastemanager.features.auth.domain.User

/**
 * Handles all Firebase Authentication and user Firestore operations.
 * STUB — full implementation comes in Phase 2.
 */
class AuthRepository {

    /** Sign up a new user with email/password, then save their profile to Firestore. */
    suspend fun signUp(email: String, password: String, username: String, role: String): Result<User> {
        // TODO (Phase 2): Implement Firebase Auth createUserWithEmailAndPassword
        // TODO (Phase 2): Save user document to Firestore users/{uid}
        return Result.failure(NotImplementedError("AuthRepository.signUp not yet implemented"))
    }

    /** Sign in an existing user. */
    suspend fun signIn(email: String, password: String): Result<User> {
        // TODO (Phase 2): Implement Firebase Auth signInWithEmailAndPassword
        return Result.failure(NotImplementedError("AuthRepository.signIn not yet implemented"))
    }

    /** Send a password reset email. */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        // TODO (Phase 2): Implement Firebase Auth sendPasswordResetEmail
        return Result.failure(NotImplementedError("AuthRepository.sendPasswordResetEmail not yet implemented"))
    }

    /** Sign the current user out. */
    fun signOut() {
        // TODO (Phase 2): Implement Firebase Auth signOut
    }

    /** Returns the currently signed-in user's UID, or null if not signed in. */
    fun getCurrentUserUid(): String? {
        // TODO (Phase 2): Return FirebaseAuth.getInstance().currentUser?.uid
        return null
    }
}