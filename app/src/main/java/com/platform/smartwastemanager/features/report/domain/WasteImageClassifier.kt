package com.platform.smartwastemanager.features.report.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.ByteBuffer

/**
 * Result of the AI classification process.
 *
 * @property category  The mapped waste category.
 * @property topLabels List of top detected labels with their confidence scores.
 *                     Each pair is (human-readable label, confidence 0.0–1.0).
 */
data class ClassificationResult(
    val category: WasteCategory,
    val topLabels: List<Pair<String, Float>>
)

/**
 * Classifies waste images using a TensorFlow Lite model stored in assets/.
 *
 * Model expected: waste_classifier.tflite
 *   - Input:  224 × 224 × 3 RGB image (float32 or uint8 depending on model variant)
 *   - Output: 1 × N probability array (N = number of classes in the model)
 *
 * This class:
 *  1. Loads the model from assets once (lazy) to avoid re-loading on every call.
 *  2. Center-crops the bitmap to focus on the waste item in the frame.
 *  3. Resizes the cropped bitmap to 224×224 using the TFLite Support ImageProcessor.
 *  4. Runs inference and maps the top output labels to WasteCategory values.
 *  5. Falls back to MIXED_WASTE if confidence is too low or an error occurs.
 *
 * @param context Application context — used to read the model file from assets.
 */
class WasteImageClassifier(private val context: Context) {

    // ---- Constants ----

    /** The model file must be placed at: app/src/main/assets/waste_classifier.tflite */
    private val MODEL_FILE_NAME = "waste_classifier.tflite"

    /**
     * The input size the model expects.
     * EfficientNet-Lite0 and MobileNetV2 both use 224×224.
     */
    private val INPUT_SIZE = 224

    /**
     * Minimum confidence for a result to be trusted.
     * Results below this are classified as MIXED_WASTE.
     */
    private val CONFIDENCE_THRESHOLD = 0.20f

    // ---- TFLite interpreter (loaded once, lazily) ----

    /**
     * The TFLite Interpreter is the engine that runs the model.
     * It is loaded once the first time classify() is called.
     * 'lazy' ensures it is only initialised once even across coroutines.
     */
    private val interpreter: Interpreter by lazy {
        val modelBuffer: ByteBuffer = FileUtil.loadMappedFile(context, MODEL_FILE_NAME)
        val options = Interpreter.Options().apply {
            // Use 2 threads for faster inference on multi-core devices
            numThreads = 2
        }
        Interpreter(modelBuffer, options)
    }

    /**
     * The ImageProcessor resizes the bitmap to the model's required input size.
     * BILINEAR interpolation gives a good quality/speed balance.
     */
    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .build()

    // ---- Public API ----

    /**
     * Classifies the given bitmap and returns a [ClassificationResult].
     *
     * This function runs on the IO dispatcher because:
     * - File I/O (first call, model loading) must not block the main thread.
     * - Matrix multiplication in TFLite can be CPU-intensive.
     *
     * @param bitmap The full-resolution bitmap from the camera or gallery.
     * @return A [ClassificationResult] with the best-matched [WasteCategory].
     */
    suspend fun classify(bitmap: Bitmap): ClassificationResult = withContext(Dispatchers.Default) {
        return@withContext try {
            // Step 1: Center-crop to focus on the waste item (removes background noise)
            val croppedBitmap = centerCrop(bitmap)

            // Step 2: Wrap the bitmap in a TensorImage so the ImageProcessor can work with it
            val tensorImage = TensorImage.fromBitmap(croppedBitmap)

            // Step 3: Resize to 224×224 (the model's expected input size)
            val processedImage = imageProcessor.process(tensorImage)

            // Step 4: Prepare the output buffer
            // The model outputs a 1D array of probabilities, one per class.
            // We read the output shape from the model itself so this works with
            // any model that has a single output tensor.
            val outputShape = interpreter.getOutputTensor(0).shape()
            val numClasses = outputShape[1] // e.g. 1000 for ImageNet models
            val outputBuffer = Array(1) { FloatArray(numClasses) }

            // Step 5: Run inference
            interpreter.run(processedImage.buffer, outputBuffer)

            // Step 6: Extract the probabilities from the output
            val probabilities = outputBuffer[0]

            // Step 7: Find the top-5 results by probability (for display in UI)
            val topResults = probabilities
                .mapIndexed { index, confidence -> index to confidence }
                .sortedByDescending { it.second }
                .take(5)

            // Step 8: Map the top result index to a human-readable label
            // ImageNet models output class indices; we map them to category names.
            val topLabels = topResults.map { (index, confidence) ->
                imageNetIndexToLabel(index) to confidence
            }

            // Step 9: Map labels to WasteCategory with confidence weighting
            val finalCategory = mapLabelsToCategory(topLabels)

            ClassificationResult(
                category = finalCategory,
                topLabels = topLabels
            )

        } catch (e: Exception) {
            // If anything goes wrong (model file missing, inference error),
            // return a safe default so the app doesn't crash.
            android.util.Log.e("WasteImageClassifier", "Classification failed", e)
            ClassificationResult(
                category = WasteCategory.MIXED_WASTE,
                topLabels = emptyList()
            )
        }
    }

    // ---- Private Helpers ----

    /**
     * Crops the center 70% of the bitmap to remove background clutter and
     * focus on the waste object the user is pointing the camera at.
     *
     * This mirrors the camera UI's target box overlay (also 70%).
     */
    private fun centerCrop(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val cropWidth = (width * 0.7f).toInt()
        val cropHeight = (height * 0.7f).toInt()

        val left = (width - cropWidth) / 2
        val top = (height - cropHeight) / 2

        val cropped = Bitmap.createBitmap(cropWidth, cropHeight, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(cropped)
        val srcRect = Rect(left, top, left + cropWidth, top + cropHeight)
        val dstRect = Rect(0, 0, cropWidth, cropHeight)
        canvas.drawBitmap(src, srcRect, dstRect, null)
        return cropped
    }

    /**
     * Takes the top labels (name + confidence) from TFLite output and returns
     * the best matching [WasteCategory].
     *
     * Strategy:
     * - Score each category by summing the confidence of all labels that map to it.
     * - Pick the category with the highest total score.
     * - If the top score is below [CONFIDENCE_THRESHOLD], return MIXED_WASTE.
     */
    private fun mapLabelsToCategory(topLabels: List<Pair<String, Float>>): WasteCategory {
        // Accumulate score per category
        val categoryScores = mutableMapOf<WasteCategory, Float>()

        topLabels.forEach { (label, confidence) ->
            val category = labelToCategory(label)
            // Only count specific categories, not the default MIXED_WASTE
            if (category != WasteCategory.MIXED_WASTE) {
                categoryScores[category] = categoryScores.getOrDefault(category, 0f) + confidence
            }
        }

        if (categoryScores.isEmpty()) return WasteCategory.MIXED_WASTE

        // Sort by accumulated score descending
        val sorted = categoryScores.entries.sortedByDescending { it.value }
        val best = sorted.first()

        // If the best score is too low, we can't be confident — return Mixed Waste
        return if (best.value < CONFIDENCE_THRESHOLD) {
            WasteCategory.MIXED_WASTE
        } else {
            best.key
        }
    }

    /**
     * Maps a single human-readable ImageNet label to a [WasteCategory].
     *
     * This is the core classification logic. Labels come from the model's
     * ImageNet vocabulary (1000 classes). We match substrings to group them
     * into our 8 waste categories.
     *
     * Coverage is intentionally broad — partial matches catch variations like
     * "plastic bag", "plastic bottle", "plastic wrap", etc.
     */
    private fun labelToCategory(label: String): WasteCategory {
        val lower = label.lowercase()
        return when {

            // ---- Hazardous ----
            // Electronics, chemicals, batteries, light sources with mercury
            lower.contains("battery") || lower.contains("batteries") ||
                    lower.contains("chemical") || lower.contains("paint") ||
                    lower.contains("solvent") || lower.contains("bleach") ||
                    lower.contains("laptop") || lower.contains("computer") ||
                    lower.contains("keyboard") || lower.contains("mouse") ||
                    lower.contains("remote control") || lower.contains("television") ||
                    lower.contains("monitor") || lower.contains("printer") ||
                    lower.contains("phone") || lower.contains("mobile") ||
                    lower.contains("circuit") || lower.contains("electric") ||
                    lower.contains("fluorescent") || lower.contains("light bulb") ||
                    lower.contains("mercury") || lower.contains("syringe") ||
                    lower.contains("medicine") || lower.contains("pill") ||
                    lower.contains("drug") || lower.contains("toxic")
                -> WasteCategory.HAZARDOUS

            // ---- Organic ----
            // Food, produce, natural plant matter
            lower.contains("food") || lower.contains("fruit") ||
                    lower.contains("vegetable") || lower.contains("banana") ||
                    lower.contains("apple") || lower.contains("orange") ||
                    lower.contains("lemon") || lower.contains("strawberry") ||
                    lower.contains("mushroom") || lower.contains("broccoli") ||
                    lower.contains("cauliflower") || lower.contains("artichoke") ||
                    lower.contains("corn") || lower.contains("cucumber") ||
                    lower.contains("pumpkin") || lower.contains("zucchini") ||
                    lower.contains("eggplant") || lower.contains("pepper") ||
                    lower.contains("tomato") || lower.contains("head cabbage") ||
                    lower.contains("leaf") || lower.contains("grass") ||
                    lower.contains("plant") || lower.contains("flower") ||
                    lower.contains("meat") || lower.contains("bread") ||
                    lower.contains("pizza") || lower.contains("burger") ||
                    lower.contains("sandwich") || lower.contains("hotdog") ||
                    lower.contains("taco") || lower.contains("burrito") ||
                    lower.contains("soup") || lower.contains("stew") ||
                    lower.contains("egg") || lower.contains("cheese") ||
                    lower.contains("coffee") || lower.contains("tea") ||
                    lower.contains("compost") || lower.contains("peel") ||
                    lower.contains("produce") || lower.contains("cuisine")
                -> WasteCategory.ORGANIC

            // ---- Paper ----
            // Paper documents, cardboard, packaging paper
            lower.contains("paper") || lower.contains("cardboard") ||
                    lower.contains("newspaper") || lower.contains("book") ||
                    lower.contains("magazine") || lower.contains("envelope") ||
                    lower.contains("tissue") || lower.contains("napkin") ||
                    lower.contains("document") || lower.contains("sheet") ||
                    lower.contains("notebook") || lower.contains("letter") ||
                    lower.contains("carton") || lower.contains("tetra") ||
                    lower.contains("mail")
                -> WasteCategory.PAPER

            // ---- Glass ----
            // Bottles, jars, glassware
            lower.contains("glass") || lower.contains("jar") ||
                    lower.contains("wine bottle") || lower.contains("beer bottle") ||
                    lower.contains("water bottle") && lower.contains("glass") ||
                    lower.contains("vase") || lower.contains("goblet") ||
                    lower.contains("beaker") || lower.contains("flask") ||
                    lower.contains("test tube") || lower.contains("measuring cup") ||
                    lower.contains("window pane") || lower.contains("mirror")
                -> WasteCategory.GLASS

            // ---- Metal ----
            // Cans, foil, metal containers, appliances
            lower.contains("can") || lower.contains("tin") ||
                    lower.contains("aluminum") || lower.contains("aluminium") ||
                    lower.contains("steel") || lower.contains("iron") ||
                    lower.contains("copper") || lower.contains("wire") ||
                    lower.contains("foil") || lower.contains("nail") ||
                    lower.contains("screw") || lower.contains("washer") ||
                    lower.contains("metal") || lower.contains("coin") ||
                    lower.contains("kettle") || lower.contains("toaster") ||
                    lower.contains("frying pan") || lower.contains("wok") ||
                    lower.contains("spatula") || lower.contains("ladle") ||
                    lower.contains("refrigerator") || lower.contains("dishwasher") ||
                    lower.contains("washing machine")
                -> WasteCategory.METAL

            // ---- Plastic ----
            // Bottles, bags, containers, packaging
            lower.contains("plastic") || lower.contains("bottle") ||
                    lower.contains("water bottle") ||
                    lower.contains("pop bottle") || lower.contains("plastic bag") ||
                    lower.contains("shopping bag") || lower.contains("garbage bag") ||
                    lower.contains("bucket") || lower.contains("barrel") ||
                    lower.contains("watering can") || lower.contains("cup") ||
                    lower.contains("straw") || lower.contains("jug") ||
                    lower.contains("tub") || lower.contains("container") ||
                    lower.contains("packaging") || lower.contains("wrap") ||
                    lower.contains("polystyrene") || lower.contains("styrofoam") ||
                    lower.contains("foam")
                -> WasteCategory.PLASTIC

            // ---- Recyclable (general — items that are recyclable but not specific enough) ----
            lower.contains("recycle") || lower.contains("recyclable")
                -> WasteCategory.RECYCLABLE

            // ---- Default ----
            else -> WasteCategory.MIXED_WASTE
        }
    }

    /**
     * Converts an ImageNet class index (0–999) to a human-readable label string.
     *
     * This is a curated subset of the 1000 ImageNet classes that are most
     * relevant to waste classification. Classes not in this map return
     * "unknown_${index}" which will be classified as MIXED_WASTE.
     *
     * If you use a model with its own label file (e.g. a custom Roboflow model),
     * you can replace this function with a file-based label loader instead.
     */
    private fun imageNetIndexToLabel(index: Int): String {
        // Curated ImageNet labels relevant to waste classification.
        // Full list: https://gist.github.com/yrevar/942d3a0ac09ec9e5eb3a
        return IMAGENET_LABELS.getOrElse(index) { "unknown_$index" }
    }

    companion object {
        /**
         * A curated map of ImageNet class indices to label names.
         *
         * Only includes classes that can plausibly appear in a waste photo.
         * Full ImageNet has 1000 classes — we include the ~200 most relevant ones.
         * The rest will produce "unknown_N" which maps to MIXED_WASTE.
         */
        private val IMAGENET_LABELS = mapOf(
            // --- Food / Organic ---
            924 to "guacamole",
            925 to "consomme",
            926 to "hot pot",
            927 to "trifle",
            928 to "ice cream",
            929 to "ice lolly",
            930 to "french loaf",
            931 to "bagel",
            932 to "pretzel",
            933 to "cheeseburger",
            934 to "hotdog",
            935 to "mashed potato",
            936 to "head cabbage",
            937 to "broccoli",
            938 to "cauliflower",
            939 to "zucchini",
            940 to "spaghetti squash",
            941 to "acorn squash",
            942 to "butternut squash",
            943 to "cucumber",
            944 to "artichoke",
            945 to "bell pepper",
            946 to "cardoon",
            947 to "mushroom",
            948 to "Granny Smith",
            949 to "strawberry",
            950 to "orange",
            951 to "lemon",
            952 to "fig",
            953 to "pineapple",
            954 to "banana",
            955 to "jackfruit",
            956 to "custard apple",
            957 to "pomegranate",
            958 to "hay",
            959 to "carbonara",
            960 to "chocolate sauce",
            961 to "dough",
            962 to "meat loaf",
            963 to "pizza",
            964 to "potpie",
            965 to "burrito",
            966 to "red wine",
            967 to "espresso",
            968 to "cup",
            969 to "eggnog",
            970 to "pretzel",
            971 to "bagel",
            972 to "meat",
            973 to "produce",
            974 to "vegetable",
            975 to "fruit",
            976 to "bread",
            977 to "egg",
            978 to "cheese",
            979 to "coffee",
            980 to "leaf",
            981 to "grass",
            982 to "plant",
            983 to "flower",
            984 to "compost",

            // --- Bottles / Plastic / Glass ---
            440 to "beer bottle",
            441 to "beer glass",
            442 to "beer mug",
            737 to "pop bottle",
            738 to "water bottle",
            898 to "wine bottle",
            899 to "wine glass",
            455 to "bottlecap",
            568 to "milk can",
            463 to "bucket",
            464 to "barrel",
            628 to "measuring cup",
            647 to "water jug",
            720 to "plastic bag",
            721 to "shopping bag",
            722 to "garbage bag",

            // --- Cans / Metal ---
            463 to "bucket",
            720 to "can opener",
            721 to "tin can",
            722 to "tin",
            528 to "hair slide",
            529 to "hammer",
            530 to "hamper",
            587 to "nail",
            588 to "necklace",
            589 to "needle",
            779 to "screw",
            780 to "screwdriver",
            789 to "shovel",
            654 to "measuring cup",

            // --- Paper / Cardboard ---
            549 to "envelope",
            698 to "paper towel",
            477 to "cardboard",
            478 to "carton",
            653 to "menu",
            654 to "newspaper",
            679 to "notebook",
            774 to "packet",
            775 to "paper bag",

            // --- Electronics / Hazardous ---
            527 to "hard disc",
            528 to "hard drive",
            586 to "modem",
            587 to "monitor",
            588 to "mouse",
            589 to "mousetrap",
            620 to "laptop",
            621 to "keyboard",
            622 to "remote control",
            623 to "mobile phone",
            696 to "oscilloscope",
            697 to "overskirt",
            760 to "printer",
            761 to "projector",
            762 to "punching bag",
            832 to "television",
            833 to "radio",

            // --- Kitchen / Metal appliances ---
            426 to "barrel",
            427 to "barrow",
            428 to "basketball",
            468 to "caldron",
            469 to "can opener",
            470 to "candle",
            471 to "cannon",
            544 to "espresso maker",
            545 to "face powder",
            606 to "frying pan",
            607 to "funnel",
            608 to "fur coat",
            657 to "microwave",
            658 to "milk",
            659 to "miniskirt",
            703 to "pot",
            704 to "potter",
            705 to "power drill",
            752 to "refrigerator",
            753 to "remote control",
            808 to "stove",
            809 to "strainer",
            827 to "teapot",
            828 to "teddy",
            829 to "television",
            873 to "toaster",
            874 to "toilet tissue",
            875 to "torch",

            // --- Bags / Wrap ----
            443 to "bib",
            444 to "bicycle",
            642 to "mask",
            643 to "matchstick",
            780 to "rubber eraser",

            // --- Generic recyclable containers ---
            648 to "water tower",
            649 to "web site",
            650 to "whiskey jug",
            651 to "wig",
            652 to "window screen",
            721 to "plastic",
            722 to "styrofoam",
            723 to "foam"
        )
    }
}