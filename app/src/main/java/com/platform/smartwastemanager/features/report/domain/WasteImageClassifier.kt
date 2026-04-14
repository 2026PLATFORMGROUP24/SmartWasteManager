package com.platform.smartwastemanager.features.report.domain

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.tasks.await

/**
 * Uses ML Kit Image Labeling (on-device) to classify waste from a camera photo.
 *
 * The ML Kit model returns generic labels like "Bottle", "Plastic bag", "Food".
 * Each label is mapped to one of the app's WasteCategory enum values.
 * The label with the highest confidence score wins; ties default to MIXED_WASTE.
 */
class WasteImageClassifier {

    // ML Kit labeler with a minimum confidence threshold of 0.65 (65%)
    // Only labels the model is fairly confident about are considered
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.65f)
            .build()
    )

    /**
     * Classifies the given bitmap and returns the most likely WasteCategory.
     *
     * Flow:
     * 1. Convert bitmap to an ML Kit InputImage.
     * 2. Run the on-device labeler (suspend — awaits the Task).
     * 3. Map each returned label text to a WasteCategory.
     * 4. Return the first non-MIXED_WASTE result, or MIXED_WASTE if nothing matched.
     *
     * @param bitmap  The camera frame captured by CameraX.
     * @return        The best-matching WasteCategory.
     */
    suspend fun classify(bitmap: Bitmap): WasteCategory {
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val labels = labeler.process(inputImage).await()

            // Labels are already sorted by confidence (highest first)
            // Find the first label that maps to something specific (not MIXED_WASTE)
            val bestCategory = labels
                .map { labelToCategory(it.text) }
                .firstOrNull { it != WasteCategory.MIXED_WASTE }

            bestCategory ?: WasteCategory.MIXED_WASTE
        } catch (e: Exception) {
            // If ML Kit fails for any reason, fall back to MIXED_WASTE
            WasteCategory.MIXED_WASTE
        }
    }

    /**
     * Maps an ML Kit label string to a WasteCategory.
     * Matching is done case-insensitively using keyword checks.
     * Unrecognised labels return MIXED_WASTE.
     */
    private fun labelToCategory(label: String): WasteCategory {
        val lower = label.lowercase()
        return when {
            // ---- Plastic ----
            lower.contains("plastic") || lower.contains("bottle") ||
                    lower.contains("bag") || lower.contains("container") ||
                    lower.contains("cup") || lower.contains("straw")
                -> WasteCategory.PLASTIC

            // ---- Paper ----
            lower.contains("paper") || lower.contains("cardboard") ||
                    lower.contains("newspaper") || lower.contains("book") ||
                    lower.contains("magazine") || lower.contains("envelope")
                -> WasteCategory.PAPER

            // ---- Glass ----
            lower.contains("glass") || lower.contains("jar") ||
                    lower.contains("window") || lower.contains("mirror")
                -> WasteCategory.GLASS

            // ---- Metal ----
            lower.contains("metal") || lower.contains("tin") ||
                    lower.contains("aluminum") || lower.contains("aluminium") ||
                    lower.contains("can") || lower.contains("steel") ||
                    lower.contains("iron") || lower.contains("copper")
                -> WasteCategory.METAL

            // ---- Organic ----
            lower.contains("food") || lower.contains("fruit") ||
                    lower.contains("vegetable") || lower.contains("plant") ||
                    lower.contains("leaf") || lower.contains("grass") ||
                    lower.contains("wood") || lower.contains("compost")
                -> WasteCategory.ORGANIC

            // ---- Hazardous ----
            lower.contains("battery") || lower.contains("chemical") ||
                    lower.contains("paint") || lower.contains("oil") ||
                    lower.contains("solvent") || lower.contains("bleach")
                -> WasteCategory.HAZARDOUS

            // ---- Recyclable (general) ----
            lower.contains("recycle") || lower.contains("recyclable") ||
                    lower.contains("carton") || lower.contains("tetra")
                -> WasteCategory.RECYCLABLE

            else -> WasteCategory.MIXED_WASTE
        }
    }
}