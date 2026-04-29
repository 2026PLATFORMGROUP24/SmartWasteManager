package com.platform.smartwastemanager.features.auth.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.platform.smartwastemanager.core.util.Constants
import com.platform.smartwastemanager.features.auth.domain.User
import com.platform.smartwastemanager.features.auth.domain.UserRole
import kotlinx.coroutines.tasks.await

/**
 * Handles all Firebase Authentication and Firestore user profile operations.
 *
 * Phase 6 addition: after every sign-in and sign-up, we fetch the device's
 * current FCM token and write it to users/{uid}/fcmToken immediately.
 * This fixes the race condition where onNewToken() fires before the user is
 * authenticated, causing the token to never be saved.
 */
class AuthRepository {

    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore    = FirebaseFirestore.getInstance()

    /** Creates a new Firebase Auth account, saves the Firestore user document,
     *  then immediately saves the FCM token. */
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
                uid      = uid,
                username = username,
                email    = email,
                role     = UserRole.fromString(role),
                fcmToken = ""
            )

            // Save the user document
            firestore.collection(Constants.COLLECTION_USERS).document(uid)
                .set(mapOf(
                    "uid"       to uid,
                    "username"  to username,
                    "email"     to email,
                    "role"      to role.lowercase(),
                    "fcmToken"  to "",
                    "createdAt" to Timestamp.now()
                )).await()

            // Immediately save the FCM token so this device can receive pushes
            saveFcmTokenForUid(uid)

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Signs in, fetches or creates the Firestore profile,
     *  then immediately saves the FCM token. */
    suspend fun signIn(email: String, password: String): Result<User> {
        return try {
            val authResult = firebaseAuth
                .signInWithEmailAndPassword(email, password)
                .await()

            val uid = authResult.user?.uid
                ?: return Result.failure(Exception("Sign in failed: no UID returned"))

            val result = fetchOrCreateUserProfile(uid, email)

            // Immediately save the FCM token so this device can receive pushes
            if (result.isSuccess) {
                saveFcmTokenForUid(uid)
            }

            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Fetches a user's Firestore profile by UID and refreshes the FCM token.
     *  Used to restore the session on app start. */
    suspend fun fetchUserProfile(uid: String): Result<User> {
        return try {
            val result = fetchOrCreateUserProfile(
                uid,
                firebaseAuth.currentUser?.email ?: ""
            )

            // Refresh token on session restore too — token may have rotated since last launch
            if (result.isSuccess) {
                saveFcmTokenForUid(uid)
            }

            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches the FCM registration token for this device and writes it to
     * users/{uid}/fcmToken in Firestore.
     *
     * Called after every sign-in, sign-up, and session restore. This guarantees
     * the token in Firestore is always fresh and belongs to the currently signed-in
     * user, regardless of when FCM originally called onNewToken().
     */
    private suspend fun saveFcmTokenForUid(uid: String) {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            firestore.collection(Constants.COLLECTION_USERS)
                .document(uid)
                .update(Constants.FIELD_FCM_TOKEN, token)
                .await()
        } catch (e: Exception) {
            // Non-fatal — the token will be retried on next sign-in
        }
    }

    /**
     * Fetches the Firestore document for [uid].
     * If it doesn't exist, creates a default "user" role document.
     */
    private suspend fun fetchOrCreateUserProfile(uid: String, email: String): Result<User> {
        val document = firestore
            .collection(Constants.COLLECTION_USERS)
            .document(uid)
            .get()
            .await()

        return if (document.exists()) {
            Result.success(
                User(
                    uid      = uid,
                    username = document.getString("username") ?: "",
                    email    = document.getString("email")    ?: email,
                    role     = UserRole.fromString(document.getString("role") ?: "user"),
                    fcmToken = document.getString("fcmToken") ?: ""
                )
            )
        } else {
            val defaultUser = User(
                uid      = uid,
                username = email.substringBefore("@"),
                email    = email,
                role     = UserRole.USER,
                fcmToken = ""
            )
            firestore.collection(Constants.COLLECTION_USERS).document(uid)
                .set(mapOf(
                    "uid"       to uid,
                    "username"  to defaultUser.username,
                    "email"     to email,
                    "role"      to "user",
                    "fcmToken"  to "",
                    "createdAt" to Timestamp.now()
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

    /** Signs out. */
    fun signOut() { firebaseAuth.signOut() }

    /**
     * Signs in with a Google ID token obtained from the Google Sign-In flow.
     * Firebase exchanges the ID token for a Firebase credential and signs the user in.
     * If the user has no Firestore profile yet, one is created with role "user".
     */
    suspend fun signInWithGoogle(idToken: String): Result<User> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val uid = authResult.user?.uid
                ?: return Result.failure(Exception("Google sign-in failed: no UID"))
            val email = authResult.user?.email ?: ""
            val displayName = authResult.user?.displayName ?: email.substringBefore("@")

            // Fetch or create the Firestore profile
            val doc = firestore.collection(Constants.COLLECTION_USERS).document(uid).get().await()
            val user = if (doc.exists()) {
                User(
                    uid      = uid,
                    username = doc.getString("username") ?: displayName,
                    email    = doc.getString("email")    ?: email,
                    role     = UserRole.fromString(doc.getString("role") ?: "user"),
                    fcmToken = doc.getString("fcmToken") ?: ""
                )
            } else {
                firestore.collection(Constants.COLLECTION_USERS).document(uid)
                    .set(mapOf(
                        "uid"       to uid,
                        "username"  to displayName,
                        "email"     to email,
                        "role"      to "user",
                        "fcmToken"  to "",
                        "createdAt" to Timestamp.now()
                    )).await()
                User(uid = uid, username = displayName, email = email, role = UserRole.USER, fcmToken = "")
            }

            saveFcmTokenForUid(uid)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Returns the UID of the currently signed-in user, or null. */
    fun getCurrentUserUid(): String? = firebaseAuth.currentUser?.uid

    /** Returns true if a user is currently signed in. */
    fun isUserSignedIn(): Boolean = firebaseAuth.currentUser != null
}