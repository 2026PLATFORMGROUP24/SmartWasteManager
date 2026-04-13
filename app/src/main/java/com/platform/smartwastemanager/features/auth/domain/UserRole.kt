package com.platform.smartwastemanager.features.auth.domain

/**
 * The two roles a user can have in the app.
 * - USER: A regular resident. Can view schedules, report waste, view map and guides.
 * - DRIVER: Collection staff. Has all USER capabilities + CRUD for schedules and guides,
 *           plus the ability to dismiss map pins.
 */
enum class UserRole {
    USER,
    DRIVER;

    companion object {
        /**
         * Converts the string stored in Firestore ("user" or "driver") to a UserRole enum.
         * Defaults to USER if the string is unrecognised.
         */
        fun fromString(value: String): UserRole {
            return when (value.lowercase()) {
                "driver" -> DRIVER
                else -> USER
            }
        }
    }
}