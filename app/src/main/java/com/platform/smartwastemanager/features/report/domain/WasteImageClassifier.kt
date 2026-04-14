package com.platform.smartwastemanager.features.report.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.tasks.await

/**
 * Result of the AI classification process.
 * 
 * @property category The mapped waste category.
 * @property topLabels List of top detected labels with their confidence scores.
 */
data class ClassificationResult(
    val category: WasteCategory,
    val topLabels: List<Pair<String, Float>>
)

/**
 * Uses ML Kit Image Labeling (on-device) to classify waste from a camera photo.
 *
 * This version uses:
 * 1. Center-cropping to focus on the object.
 * 2. Weighted scoring (summing confidence across multiple related labels).
 */
class WasteImageClassifier {

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.35f) 
            .build()
    )

    /**
     * Classifies the given bitmap and returns a [ClassificationResult].
     */
    suspend fun classify(bitmap: Bitmap): ClassificationResult {
        return try {
            // 1. Center Crop: Remove 15% from each edge to focus on the center object
            val focusedBitmap = centerCrop(bitmap)
            val inputImage = InputImage.fromBitmap(focusedBitmap, 0)
            
            val labels = labeler.process(inputImage).await()
            val topLabels = labels.take(5).map { it.text to it.confidence }

            // 2. Weighted Scoring: Sum confidence scores for each category
            val categoryScores = mutableMapOf<WasteCategory, Float>()
            
            labels.forEach { label ->
                val category = labelToCategory(label.text)
                if (category != WasteCategory.MIXED_WASTE) {
                    val currentScore = categoryScores.getOrDefault(category, 0f)
                    categoryScores[category] = currentScore + label.confidence
                }
            }

            // Find the best category by score
            val sortedScores = categoryScores.toList().sortedByDescending { it.second }
            val bestCategoryEntry = sortedScores.firstOrNull()
            
            val finalCategory = if (bestCategoryEntry == null) {
                WasteCategory.MIXED_WASTE
            } else {
                // MIXED_WASTE BIAS:
                // If the top category's score is too low, or if the second best is very close,
                // it's likely a mix of different waste types.
                val topScore = bestCategoryEntry.second
                val secondScore = sortedScores.getOrNull(1)?.second ?: 0f
                
                if (topScore < 0.5f || (topScore - secondScore) < 0.15f) {
                    WasteCategory.MIXED_WASTE
                } else {
                    bestCategoryEntry.first
                }
            }

            ClassificationResult(
                category = finalCategory,
                topLabels = topLabels
            )
        } catch (e: Exception) {
            ClassificationResult(WasteCategory.MIXED_WASTE, emptyList())
        }
    }

    /**
     * Crops the center 70% of the bitmap to focus on the waste item.
     */
    private fun centerCrop(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val cropWidth = (width * 0.7f).toInt()
        val cropHeight = (height * 0.7f).toInt()
        
        val left = (width - cropWidth) / 2
        val top = (height - cropHeight) / 2
        
        val croppedBitmap = Bitmap.createBitmap(cropWidth, cropHeight, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(croppedBitmap)
        val srcRect = Rect(left, top, left + cropWidth, top + cropHeight)
        val dstRect = Rect(0, 0, cropWidth, cropHeight)
        canvas.drawBitmap(src, srcRect, dstRect, null)
        return croppedBitmap
    }

    /**
     * Maps an ML Kit label string to a WasteCategory.
     * All sub-types (Paper, Plastic, Glass, Metal) are now mapped to RECYCLABLE.
     */
    private fun labelToCategory(label: String): WasteCategory {
        val lower = label.lowercase()
        return when {
            // ---- Hazardous ----
            lower.contains("battery") || lower.contains("chemical") ||
                    lower.contains("paint") || lower.contains("oil") ||
                    lower.contains("solvent") || lower.contains("bleach") ||
                    lower.contains("electronic") || lower.contains("phone") ||
                    lower.contains("computer") || lower.contains("light bulb") ||
                    lower.contains("mercury") || lower.contains("toxic") ||
                    lower.contains("hardware") || lower.contains("component")
                -> WasteCategory.HAZARDOUS

            // ---- Organic ----
            lower.contains("food") || lower.contains("fruit") ||
                    lower.contains("vegetable") || lower.contains("plant") ||
                    lower.contains("leaf") || lower.contains("grass") ||
                    lower.contains("wood") || lower.contains("compost") ||
                    lower.contains("scraps") || lower.contains("leftover") ||
                    lower.contains("peel") || lower.contains("meat") ||
                    lower.contains("bread") || lower.contains("coffee") ||
                    lower.contains("produce") || lower.contains("cuisine")
                -> WasteCategory.ORGANIC

            // ---- Recyclable (Paper, Cardboard, Newspaper, etc.) ----
            lower.contains("paper") || lower.contains("cardboard") ||
                    lower.contains("newspaper") || lower.contains("book") ||
                    lower.contains("magazine") || lower.contains("envelope") ||
                    lower.contains("tissue") || lower.contains("napkin") ||
                    lower.contains("box") || lower.contains("mail") ||
                    lower.contains("stationary") || lower.contains("sheet") ||
                    lower.contains("document") ||
            // ---- Recyclable (Glass) ----
                    lower.contains("glass") || lower.contains("jar") ||
                    lower.contains("window") || lower.contains("mirror") ||
                    lower.contains("wine bottle") || lower.contains("beer bottle") ||
                    lower.contains("glassware") || lower.contains("crystal") ||
            // ---- Recyclable (Metal) ----
                    lower.contains("metal") || lower.contains("tin") ||
                    lower.contains("aluminum") || lower.contains("aluminium") ||
                    lower.contains("can") || lower.contains("steel") ||
                    lower.contains("iron") || lower.contains("copper") ||
                    lower.contains("wire") || lower.contains("foil") ||
                    lower.contains("appliance") || lower.contains("scrap metal") ||
            // ---- Recyclable (Plastic) ----
                    lower.contains("plastic") || lower.contains("bottle") ||
                    lower.contains("bag") || lower.contains("container") ||
                    lower.contains("cup") || lower.contains("straw") ||
                    lower.contains("jug") || lower.contains("tub") ||
                    lower.contains("wrap") || lower.contains("film") ||
                    lower.contains("polystyrene") || lower.contains("synthetic") ||
                    lower.contains("pvc") || lower.contains("polyethylene") ||
            // ---- Recyclable (General) ----
                    lower.contains("recycle") || lower.contains("recyclable") ||
                    lower.contains("carton") || lower.contains("tetra") ||
                    lower.contains("packaging") || lower.contains("package")
                -> WasteCategory.RECYCLABLE

            else -> WasteCategory.MIXED_WASTE
        }
    }
}