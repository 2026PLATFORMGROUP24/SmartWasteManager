package com.platform.smartwastemanager.features.report.domain

/**
 * All waste categories used across the app:
 * - Schedule cards (what gets collected on which day)
 * - Waste reports (what type of waste was found)
 * - AI classifier output (ML Kit labels are mapped to these)
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