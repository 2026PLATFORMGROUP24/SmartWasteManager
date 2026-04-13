package com.platform.smartwastemanager.features.auth.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.platform.smartwastemanager.core.util.Constants
import com.platform.smartwastemanager.features.auth.domain.User
import com.platform.smartwastemanager.features.auth.domain.UserRole
import kotlinx.coroutines.tasks.await

/**
 * Handles all Firebase Authentication and Firestore user profile operations.
 *
 * Every function returns a Result<T> so the ViewModel can easily handle
 * success and failure without try/catch blocks in the UI layer.
 */
class AuthRepository {

    // Firebase Auth instance — manages sign up, sign in, sign out
    private val firebaseAuth = FirebaseAuth.getInstance()

    // Firestore instance — stores the user profile document
    private val firestore = FirebaseFirestore.getInstance()

    /**
     * Creates a new account with Firebase Auth, then saves the user's
     * profile (username, email, role) to Firestore under users/{uid}.
     */
    suspend fun signUp(
        email: String,
        password: String,
        username: String,
        role: String
    ): Result<User> {
        return try {
            // Step 1: Create the Firebase Auth account
            val authResult = firebaseAuth
                .createUserWithEmailAndPassword(email, password)
                .await()

            val uid = authResult.user?.uid
                ?: return Result.failure(Exception("Sign up failed: no UID returned"))

            // Step 2: Build the user object to save to Firestore
            val user = User(
                uid = uid,
                username = username,
                email = email,
                role = UserRole.fromString(role),
                fcmToken = "",
            )

            // Step 3: Save the user document to Firestore users/{uid}
            firestore
                .collection(Constants.COLLECTION_USERS)
                .document(uid)
                .set(mapOf(
                    "uid" to user.uid,
                    "username" to user.username,
                    "email" to user.email,
                    "role" to role.lowercase(),   // store as "user" or "driver"
                    "fcmToken" to "",
                    "createdAt" to com.google.firebase.Timestamp.now()
                ))
                .await()

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs in with email and password, then fetches the user's profile
     * from Firestore so we know their role.
     */
    suspend fun signIn(email: String, password: String): Result<User> {
        return try {
            // Step 1: Sign in with Firebase Auth
            val authResult = firebaseAuth
                .signInWithEmailAndPassword(email, password)
                .await()

            val uid = authResult.user?.uid
                ?: return Result.failure(Exception("Sign in failed: no UID returned"))

            // Step 2: Fetch the user document from Firestore to get the role
            val document = firestore
                .collection(Constants.COLLECTION_USERS)
                .document(uid)
                .get()
                .await()

            if (!document.exists()) {
                return Result.failure(Exception("User profile not found in database"))
            }

            // Step 3: Map the Firestore document fields to our User model
            val user = User(
                uid = uid,
                username = document.getString("username") ?: "",
                email = document.getString("email") ?: email,
                role = UserRole.fromString(document.getString("role") ?: "user"),
                fcmToken = document.getString("fcmToken") ?: ""
            )

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends a password reset email to the given address.
     * Firebase handles the email delivery — no extra setup needed.
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
     * Signs the current user out of Firebase Auth.
     * After this, getCurrentUserUid() will return null.
     */
    fun signOut() {
        firebaseAuth.signOut()
    }

    /**
     * Returns the UID of the currently signed-in user, or null if nobody is signed in.
     * This is used at app start to decide whether to show the login screen.
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