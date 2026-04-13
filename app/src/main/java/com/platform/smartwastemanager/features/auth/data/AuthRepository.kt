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

    /**
     * Creates a new Firebase Auth account, then saves the user profile to Firestore.
     */
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

            val user = User(
                uid = uid,
                username = username,
                email = email,
                role = UserRole.fromString(role),
                fcmToken = ""
            )

            // Save user profile document to Firestore
            firestore
                .collection(Constants.COLLECTION_USERS)
                .document(uid)
                .set(
                    mapOf(
                        "uid" to uid,
                        "username" to username,
                        "email" to email,
                        "role" to role.lowercase(),
                        "fcmToken" to "",
                        "createdAt" to Timestamp.now()
                    )
                )
                .await()

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs in with email/password, then fetches or creates the Firestore user document.
     *
     * If the document doesn't exist (e.g. an account from a previous project),
     * we create a default one so the app works rather than blocking the user.
     */
    suspend fun signIn(email: String, password: String): Result<User> {
        return try {
            val authResult = firebaseAuth
                .signInWithEmailAndPassword(email, password)
                .await()

            val uid = authResult.user?.uid
                ?: return Result.failure(Exception("Sign in failed: no UID returned"))

            // Try to fetch the existing Firestore document
            val document = firestore
                .collection(Constants.COLLECTION_USERS)
                .document(uid)
                .get()
                .await()

            val user: User

            if (document.exists()) {
                // Document found — map it to our User model
                user = User(
                    uid = uid,
                    username = document.getString("username") ?: "",
                    email = document.getString("email") ?: email,
                    role = UserRole.fromString(document.getString("role") ?: "user"),
                    fcmToken = document.getString("fcmToken") ?: ""
                )
            } else {
                // No Firestore document exists for this account (e.g. imported from another project)
                // Create a default document so the rest of the app works correctly
                val defaultUser = User(
                    uid = uid,
                    username = email.substringBefore("@"), // use email prefix as fallback username
                    email = email,
                    role = UserRole.USER,
                    fcmToken = ""
                )

                firestore
                    .collection(Constants.COLLECTION_USERS)
                    .document(uid)
                    .set(
                        mapOf(
                            "uid" to uid,
                            "username" to defaultUser.username,
                            "email" to email,
                            "role" to "user",
                            "fcmToken" to "",
                            "createdAt" to Timestamp.now()
                        )
                    )
                    .await()

                user = defaultUser
            }

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends a password reset email.
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs the current user out.
     */
    fun signOut() {
        firebaseAuth.signOut()
    }

    /**
     * Returns the UID of the currently signed-in user, or null if not signed in.
     */
    fun getCurrentUserUid(): String? {
        return firebaseAuth.currentUser?.uid
    }

    /**
     * Returns true if a user is currently signed in.
     */
    fun isUserSignedIn(): Boolean {
        return firebaseAuth.currentUser != null
    }
}