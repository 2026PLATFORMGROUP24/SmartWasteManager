package com.platform.smartwastemanager.features.report.domain

/**
 * The two types of waste report a user can submit.
 * - REGULAR_PICKUP: Routine waste that needs to be collected.
 * - OVERFLOWING_BIN: An urgent report — a bin is full and overflowing.
 */
enum class ReportType(val displayName: String) {
    REGULAR_PICKUP("Regular Pickup"),
    OVERFLOWING_BIN("Overflowing Bin");

    companion object {
        fun fromDisplayName(name: String): ReportType {
            return entries.firstOrNull {
                it.displayName.equals(name, ignoreCase = true)
            } ?: REGULAR_PICKUP
        }
    }
}