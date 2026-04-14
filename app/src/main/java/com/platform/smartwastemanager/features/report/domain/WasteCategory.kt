package com.platform.smartwastemanager.features.report.domain

/**
 * All 8 waste categories used in the app.
 *
 * These match the Firestore schema and the schedule management checkboxes.
 * The TFLite classifier maps ImageNet labels into these categories.
 */
enum class WasteCategory(val displayName: String) {
    RECYCLABLE("Recyclable"),
    ORGANIC("Organic"),
    PAPER("Paper"),
    GLASS("Glass"),
    PLASTIC("Plastic"),
    METAL("Metal"),
    HAZARDOUS("Hazardous"),
    MIXED_WASTE("Mixed Waste");

    companion object {
        /** Returns the enum from a display name string, or MIXED_WASTE as fallback. */
        fun fromDisplayName(name: String): WasteCategory {
            return entries.firstOrNull {
                it.displayName.equals(name, ignoreCase = true)
            } ?: MIXED_WASTE
        }
    }
}