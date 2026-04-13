package com.platform.smartwastemanager.features.auth.domain

import com.google.firebase.Timestamp

/**
 * Represents a user stored in Firestore under users/{uid}.
 *
 * @property uid        Firebase Auth UID — unique identifier for this user.
 * @property username   Display name chosen at sign-up.
 * @property email      Email address used to log in.
 * @property role       Either USER or DRIVER (see UserRole enum).
 * @property fcmToken   Firebase Cloud Messaging token for push notifications.
 * @property createdAt  Timestamp of when the account was created.
 */
data class User(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val role: UserRole = UserRole.USER,
    val fcmToken: String = "",
    val createdAt: Timestamp = Timestamp.now()
)