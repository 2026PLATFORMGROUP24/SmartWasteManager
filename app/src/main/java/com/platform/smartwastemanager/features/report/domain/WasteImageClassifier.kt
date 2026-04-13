package com.platform.smartwastemanager.features.report.domain

import android.graphics.Bitmap

/**
 * Uses ML Kit Image Labeling (on-device) to classify waste from a camera photo.
 *
 * The ML Kit model returns generic labels like "Bottle", "Plastic bag", "Food".
 * This class maps those labels to the app's WasteCategory enum.
 *
 * STUB — full implementation comes in Phase 4.
 */
class WasteImageClassifier {

    /**
     * Classifies the given bitmap and returns the most likely WasteCategory.
     *
     * @param bitmap The image captured by the camera.
     * @return The detected WasteCategory, or MIXED_WASTE if nothing matches.
     */
    suspend fun classify(bitmap: Bitmap): WasteCategory {
        // TODO (Phase 4): Run ML Kit ImageLabeler on the bitmap
        // TODO (Phase 4): Map returned labels using labelToCategory()
        return WasteCategory.MIXED_WASTE
    }

    /**
     * Maps an ML Kit label string to a WasteCategory.
     * Unrecognised labels default to MIXED_WASTE.
     *
     * Full mapping table will be implemented in Phase 4.
     */
    private fun labelToCategory(label: String): WasteCategory {
        return when (label.lowercase()) {
            "bottle", "plastic bottle", "plastic", "plastic bag" -> WasteCategory.PLASTIC
            "paper", "cardboard", "newspaper", "book" -> WasteCategory.PAPER
            "glass", "glass bottle", "window" -> WasteCategory.GLASS
            "metal", "tin", "aluminium", "can", "steel" -> WasteCategory.METAL
            "food", "fruit", "vegetable", "plant", "leaf" -> WasteCategory.ORGANIC
            "battery", "chemical", "paint" -> WasteCategory.HAZARDOUS
            else -> WasteCategory.MIXED_WASTE
        }
    }
}