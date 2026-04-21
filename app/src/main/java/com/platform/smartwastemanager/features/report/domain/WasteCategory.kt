package com.platform.smartwastemanager.features.report.domain

/**
 * Waste categories used in the app.
 *
 * - Paper  + Plastic → RECYCLABLE
 * - Metal  + Glass   → MIXED_WASTE
 */
enum class WasteCategory(val displayName: String) {
    RECYCLABLE("Recyclable"),
    ORGANIC("Organic"),
    HAZARDOUS("Hazardous"),
    MIXED_WASTE("Mixed Waste");

    companion object {
        /**
         * Returns the enum from a display name string, or MIXED_WASTE as fallback.
         * Legacy names: Paper/Plastic → RECYCLABLE; Glass/Metal → MIXED_WASTE.
         */
        fun fromDisplayName(name: String): WasteCategory {
            return when {
                name.equals("Paper",   ignoreCase = true) -> RECYCLABLE
                name.equals("Plastic", ignoreCase = true) -> RECYCLABLE
                name.equals("Glass",   ignoreCase = true) -> MIXED_WASTE
                name.equals("Metal",   ignoreCase = true) -> MIXED_WASTE
                else -> entries.firstOrNull {
                    it.displayName.equals(name, ignoreCase = true)
                } ?: MIXED_WASTE
            }
        }
    }
}