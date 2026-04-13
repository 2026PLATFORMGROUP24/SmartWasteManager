package com.platform.smartwastemanager.features.auth.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.platform.smartwastemanager.core.util.Constants
import com.platform.smartwastemanager.features.auth.domain.User
import com.platform.smartwastemanager.features.auth.domain.UserRole
import kotlinx.coroutines.tasks.await

/**
 * Handles all Firebase Authentication and Firestore user profile operations.
 */
class AuthRepository {

    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    /** Creates a new Firebase Auth account and saves the Firestore user document. */
    suspend fun signUp(
        email: String,
        password: String,
        username: String,
        role: String
    ): Result<User> {
        return try {
            val authResult = firebaseAuth
                .createUserWithEmailAndPassword(email, password)
                .await()

            val uid = authResult.user?.uid
                ?: return Result.failure(Exception("Sign up failed: no UID returned"))

            val user = User(uid = uid, username = username, email = email,
                role = UserRole.fromString(role), fcmToken = "")

            firestore.collection(Constants.COLLECTION_USERS).document(uid)
                .set(mapOf(
                    "uid" to uid, "username" to username, "email" to email,
                    "role" to role.lowercase(), "fcmToken" to "",
                    "createdAt" to Timestamp.now()
                )).await()

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs in and fetches the Firestore profile.
     * If no profile document exists, creates a default one.
     */
    suspend fun signIn(email: String, password: String): Result<User> {
        return try {
            val authResult = firebaseAuth
                .signInWithEmailAndPassword(email, password)
                .await()

            val uid = authResult.user?.uid
                ?: return Result.failure(Exception("Sign in failed: no UID returned"))

            fetchOrCreateUserProfile(uid, email)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches a user's Firestore profile by UID.
     * Used to restore the session on app start.
     */
    suspend fun fetchUserProfile(uid: String): Result<User> {
        return try {
            fetchOrCreateUserProfile(uid, firebaseAuth.currentUser?.email ?: "")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches the Firestore document for [uid].
     * If it doesn't exist (account from another project), creates a default document.
     */
    private suspend fun fetchOrCreateUserProfile(uid: String, email: String): Result<User> {
        val document = firestore
            .collection(Constants.COLLECTION_USERS)
            .document(uid)
            .get()
            .await()

        return if (document.exists()) {
            val user = User(
                uid = uid,
                username = document.getString("username") ?: "",
                email = document.getString("email") ?: email,
                role = UserRole.fromString(document.getString("role") ?: "user"),
                fcmToken = document.getString("fcmToken") ?: ""
            )
            Result.success(user)
        } else {
            // No document — create a default one
            val defaultUser = User(
                uid = uid,
                username = email.substringBefore("@"),
                email = email,
                role = UserRole.USER,
                fcmToken = ""
            )
            firestore.collection(Constants.COLLECTION_USERS).document(uid)
                .set(mapOf(
                    "uid" to uid, "username" to defaultUser.username,
                    "email" to email, "role" to "user",
                    "fcmToken" to "", "createdAt" to Timestamp.now()
                )).await()
            Result.success(defaultUser)
        }
    }

    /** Sends a password reset email. */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Signs the current user out. */
    fun signOut() { firebaseAuth.signOut() }

    /** Returns the UID of the currently signed-in user, or null. */
    fun getCurrentUserUid(): String? = firebaseAuth.currentUser?.uid

    /** Returns true if a user is currently signed in. */
    fun isUserSignedIn(): Boolean = firebaseAuth.currentUser != null
}