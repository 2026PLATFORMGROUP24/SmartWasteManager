package com.platform.smartwastemanager.features.report.domain

/**
 * Refined waste categories to better handle sub-types and the "Mixed Waste" priority.
 *
 * Hierarchical Logic (Internal):
 * - RECYCLABLE includes: Paper, Plastic, Glass, Metal.
 * - ORGANIC
 * - HAZARDOUS
 * - MIXED_WASTE (Default for anything that doesn't fit or has conflicting high-confidence signals)
 */
enum class WasteCategory(val displayName: String) {
    RECYCLABLE("Recyclable"),
    ORGANIC("Organic"),
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