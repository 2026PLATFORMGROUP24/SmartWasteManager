package com.platform.smartwastemanager.features.report.domain

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
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
 * @property category       The mapped waste category.
 * @property topLabels      Top-5 raw label names and confidence scores from the model.
 * @property debugInfo      Human-readable string showing what the model saw.
 * @property lowConfidence  True when the model wasn't confident — UI should prompt re-scan.
 */
data class ClassificationResult(
    val category: WasteCategory,
    val topLabels: List<Pair<String, Float>>,
    val debugInfo: String = "",
    val lowConfidence: Boolean = false
)

/**
 * Classifies waste images using a TensorFlow Lite model stored in assets/.
 *
 * Model: waste_classifier.tflite  (MobileNet V1 1.0 224 quant)
 *   Input:  224 × 224 × 3  uint8 image
 *   Output: [1, 1001] uint8 — 1001 ImageNet classes (index 0 = background)
 *
 * Key accuracy improvements over a single-crop approach:
 *  1. Multi-crop inference — runs 3 crops and averages scores to reduce background bias.
 *  2. Background label penalty — known background labels (grass, floor, sky…)
 *     are zeroed out so they never beat actual waste items.
 *  3. Low-confidence flag — tells the UI to prompt the user to retake the photo.
 *
 * @param context Application context — used to read the model file from assets.
 */
class WasteImageClassifier(private val context: Context) {

    private val TAG = "WasteClassifier"

    /** Model file at: app/src/main/assets/waste_classifier.tflite */
    private val MODEL_FILE_NAME = "waste_classifier.tflite"

    /** MobileNet V1 expects 224×224 input. */
    private val INPUT_SIZE = 224

    /**
     * Minimum averaged confidence for the best category to be trusted.
     * Lowered to 0.08f because multi-crop averaging dilutes individual scores.
     * If no category reaches this, MIXED_WASTE is returned with lowConfidence=true.
     */
    private val CONFIDENCE_THRESHOLD = 0.08f

    // ---- TFLite Interpreter — loaded once, lazily ----
    private val interpreter: Interpreter by lazy {
        val modelBuffer: ByteBuffer = FileUtil.loadMappedFile(context, MODEL_FILE_NAME)
        Interpreter(modelBuffer, Interpreter.Options().apply { numThreads = 2 })
    }

    /** Resizes input bitmap to 224×224 using bilinear interpolation. */
    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .build()

    // ---- Public API ----

    /**
     * Classifies the given bitmap using multi-crop inference.
     *
     * Three crops are run through the model independently:
     *   - Tight crop  (50% center) — focuses tightly on the waste item
     *   - Medium crop (70% center) — balanced view, matches the camera viewfinder box
     *   - Full image  (100%)       — provides full context
     *
     * Scores for all 1001 classes are averaged across the three crops.
     * This means background labels that only dominate the full image are diluted,
     * while waste item labels that appear consistently across crops are boosted.
     *
     * @param bitmap Full-resolution bitmap from camera or gallery.
     */
    suspend fun classify(bitmap: Bitmap): ClassificationResult = withContext(Dispatchers.Default) {
        return@withContext try {
            // Ensure ARGB_8888 — TensorImage requires this
            val rgbBitmap = ensureArgb8888(bitmap)

            // Run inference on three different crops
            val tightScores  = runInference(cropCenter(rgbBitmap, 0.50f))  // 50% tight crop
            val mediumScores = runInference(cropCenter(rgbBitmap, 0.70f))  // 70% medium crop
            val fullScores   = runInference(rgbBitmap)                      // full image

            // Average all three score arrays element-by-element.
            // Each array has 1001 floats (one per ImageNet class).
            val numClasses = tightScores.size
            val averagedScores = FloatArray(numClasses) { i ->
                (tightScores[i] + mediumScores[i] + fullScores[i]) / 3f
            }

            // Apply background penalty — zero out scores for labels that are
            // environment/surface labels, not waste items.
            val penalisedScores = applyBackgroundPenalty(averagedScores)

            // Find top-5 results after penalty
            val topResults = penalisedScores
                .mapIndexed { index, score -> index to score }
                .sortedByDescending { it.second }
                .take(5)

            // Resolve indices to label names
            val topLabels = topResults.map { (index, score) ->
                MOBILENET_V1_LABELS.getOrElse(index) { "unknown[$index]" } to score
            }

            // Build debug string
            val debugInfo = topLabels.joinToString("\n") { (label, conf) ->
                "• $label → ${(conf * 100).toInt()}%"
            }
            Log.d(TAG, "Multi-crop averaged top labels:\n$debugInfo")

            // Map labels → WasteCategory
            val (finalCategory, bestScore) = mapLabelsToCategoryWithScore(topLabels)
            val isLowConfidence = bestScore < CONFIDENCE_THRESHOLD

            Log.d(TAG, "Category: ${finalCategory.displayName}, score: $bestScore, low=$isLowConfidence")

            ClassificationResult(
                category      = finalCategory,
                topLabels     = topLabels,
                debugInfo     = debugInfo,
                lowConfidence = isLowConfidence
            )

        } catch (e: Exception) {
            Log.e(TAG, "Classification failed: ${e.message}", e)
            ClassificationResult(
                category      = WasteCategory.MIXED_WASTE,
                topLabels     = emptyList(),
                debugInfo     = "Error: ${e.message}",
                lowConfidence = true
            )
        }
    }

    // ---- Private Helpers ----

    /**
     * Runs a single TFLite inference pass on the given bitmap.
     * Returns a FloatArray of 1001 probabilities (one per ImageNet class).
     *
     * MobileNet V1 quant outputs bytes (0–255).
     * We convert each byte to float with: (byte AND 0xFF) / 255f
     */
    private fun runInference(bitmap: Bitmap): FloatArray {
        val tensorImage = TensorImage.fromBitmap(bitmap)
        val processedImage = imageProcessor.process(tensorImage)

        val outputShape = interpreter.getOutputTensor(0).shape()
        val numClasses = outputShape[outputShape.size - 1]
        val outputBuffer = Array(1) { ByteArray(numClasses) }

        interpreter.run(processedImage.buffer, outputBuffer)

        // Convert quantised byte output to normalised float probabilities
        return FloatArray(numClasses) { i ->
            (outputBuffer[0][i].toInt() and 0xFF) / 255f
        }
    }

    /**
     * Center-crops a bitmap to [fraction] of its dimensions.
     * E.g. fraction=0.5 crops the center 50%×50% of the image.
     *
     * This is used to create crops at different zoom levels for multi-crop inference.
     * A tighter crop (0.5) reduces background area. A wider crop (0.7 or 1.0) gives more context.
     */
    private fun cropCenter(src: Bitmap, fraction: Float): Bitmap {
        val cropWidth  = (src.width  * fraction).toInt().coerceAtLeast(1)
        val cropHeight = (src.height * fraction).toInt().coerceAtLeast(1)
        val left = (src.width  - cropWidth)  / 2
        val top  = (src.height - cropHeight) / 2
        return Bitmap.createBitmap(src, left, top, cropWidth, cropHeight)
    }

    /**
     * Converts a bitmap to ARGB_8888 config if it isn't already.
     * TensorImage.fromBitmap() requires ARGB_8888.
     * Camera bitmaps are sometimes HARDWARE or RGB_565.
     */
    private fun ensureArgb8888(src: Bitmap): Bitmap {
        return if (src.config == Bitmap.Config.ARGB_8888) src
        else src.copy(Bitmap.Config.ARGB_8888, false)
    }

    /**
     * Zeroes out scores for known background/environment labels.
     *
     * These are ImageNet classes that commonly appear as the dominant prediction
     * when a waste item is photographed in a real-world setting (on grass, a road,
     * a table, a floor, etc.). Without this penalty they would beat actual waste labels.
     *
     * Strategy: look up each index's label name, check if it's in BACKGROUND_LABELS,
     * and if so set its score to 0f before category mapping.
     */
    private fun applyBackgroundPenalty(scores: FloatArray): FloatArray {
        val result = scores.copyOf()
        for (i in result.indices) {
            val label = MOBILENET_V1_LABELS[i]?.lowercase() ?: continue
            // Check if any background keyword matches this label
            if (BACKGROUND_LABELS.any { bgWord -> label.contains(bgWord) }) {
                result[i] = 0f
            }
        }
        return result
    }

    /**
     * Scores each WasteCategory by summing confidence of all top labels that map to it.
     * Returns a Pair of (best WasteCategory, its total score).
     * Returns (MIXED_WASTE, 0f) when no category scores above zero.
     */
    private fun mapLabelsToCategoryWithScore(
        topLabels: List<Pair<String, Float>>
    ): Pair<WasteCategory, Float> {
        val categoryScores = mutableMapOf<WasteCategory, Float>()

        topLabels.forEach { (label, confidence) ->
            val category = labelToCategory(label)
            if (category != WasteCategory.MIXED_WASTE) {
                categoryScores[category] =
                    categoryScores.getOrDefault(category, 0f) + confidence
            }
        }

        Log.d(TAG, "Category scores: $categoryScores")

        if (categoryScores.isEmpty()) return WasteCategory.MIXED_WASTE to 0f
        val best = categoryScores.maxByOrNull { it.value }!!
        return best.key to best.value
    }

    /**
     * Maps a single ImageNet label string to one of the app WasteCategories.
     * Uses substring matching — more specific checks come before broader ones.
     */
    private fun labelToCategory(label: String): WasteCategory {
        val l = label.lowercase()
        return when {

            // ---- Hazardous ----
            l.contains("laptop") || l.contains("notebook, laptop") ||
                    l.contains("desktop computer") || l.contains("screen, crt screen") ||
                    l.contains("monitor") || l.contains("television") || l.contains("tv") ||
                    l.contains("remote control") || l.contains("cellular telephone") ||
                    l.contains("pay-phone") || l.contains("hand-held computer") ||
                    l.contains("ipod") || l.contains("printer") ||
                    l.contains("mouse, computer mouse") || l.contains("keyboard") ||
                    l.contains("hard disc") || l.contains("circuit breaker") ||
                    l.contains("oscilloscope") || l.contains("loudspeaker") ||
                    l.contains("tape player") || l.contains("cd player") ||
                    l.contains("cassette") || l.contains("syringe") ||
                    l.contains("pill bottle") || l.contains("medicine chest") ||
                    l.contains("battery")
                -> WasteCategory.HAZARDOUS

            // ---- Organic ----
            l.contains("banana") || l.contains("orange") || l.contains("lemon") ||
                    l.contains("strawberry") || l.contains("pineapple") ||
                    l.contains("pomegranate") || l.contains("jackfruit") ||
                    l.contains("granny smith") || l.contains("custard apple") ||
                    l.contains("mushroom") || l.contains("broccoli") ||
                    l.contains("cauliflower") || l.contains("zucchini") ||
                    l.contains("cucumber") || l.contains("artichoke") ||
                    l.contains("bell pepper") || l.contains("head cabbage") ||
                    l.contains("acorn squash") || l.contains("butternut squash") ||
                    l.contains("spaghetti squash") || l.contains("cardoon") ||
                    l.contains("pizza") || l.contains("hotdog") || l.contains("cheeseburger") ||
                    l.contains("burrito") || l.contains("meat loaf") || l.contains("potpie") ||
                    l.contains("french loaf") || l.contains("bagel") || l.contains("pretzel") ||
                    l.contains("guacamole") || l.contains("trifle") || l.contains("ice cream") ||
                    l.contains("carbonara") || l.contains("chocolate sauce") ||
                    l.contains("dough") || l.contains("espresso") || l.contains("eggnog") ||
                    l.contains("mashed potato") || l.contains("hay") ||
                    l.contains("grocery store") || l.contains("butcher shop") ||
                    l.contains("bakery") || l.contains("corn") || l.contains("fig,") ||
                    l.contains("daisy") || l.contains("rapeseed") || l.contains("acorn") ||
                    l.contains("bolete") || l.contains("agaric") || l.contains("gyromitra") ||
                    l.contains("hen-of-the-woods")
                -> WasteCategory.ORGANIC

            // ---- Recyclable (paper) ----
            l.contains("envelope") || l.contains("paper towel") ||
                    l.contains("newspaper") || l.contains("book jacket") ||
                    l.contains("menu") || l.contains("packet") ||
                    l.contains("carton") || l.contains("mailbag") ||
                    l.contains("pencil box") || l.contains("comic book") ||
                    l.contains("book") || l.contains("cardboard") ||
                    l.contains("toilet tissue") || l.contains("toilet paper")
                -> WasteCategory.RECYCLABLE

            // ---- Glass — check BEFORE generic "bottle" to avoid plastic winning ----
            l.contains("beer glass") || l.contains("wine glass") ||
                    l.contains("goblet") || l.contains("beaker") ||
                    l.contains("pitcher, ewer") || l.contains("vase") ||
                    l.contains("beer bottle") || l.contains("wine bottle") ||
                    (l.contains("glass") && !l.contains("sunglass") &&
                            !l.contains("magnifying") && !l.contains("looking glass") &&
                            !l.contains("spyglass") && !l.contains("glass, drinking glass"))
                -> WasteCategory.GLASS

            // ---- Metal ----
            l.contains("can opener") || l.contains("tin can") ||
                    l.contains("milk can") || l.contains("watering can") ||
                    l.contains("garbage can") || l.contains("ashcan") ||
                    l.contains("dutch oven") || l.contains("wok") ||
                    l.contains("caldron") || l.contains("frying pan") ||
                    l.contains("spatula") || l.contains("ladle") ||
                    l.contains("knife") || l.contains("cleaver") ||
                    l.contains("nail") || l.contains("screw") ||
                    l.contains("hammer") || l.contains("wrench") ||
                    l.contains("shovel") || l.contains("hatchet") ||
                    l.contains("chain") || l.contains("padlock") ||
                    l.contains("safe") || l.contains("barbell") ||
                    l.contains("dumbbell") || l.contains("steel drum") ||
                    l.contains("iron, smoothing iron") || l.contains("refrigerator") ||
                    l.contains("dishwasher") || l.contains("washing machine") ||
                    l.contains("toaster") || l.contains("wire") || l.contains("coil") ||
                    l.contains("foil") || l.contains("metal") ||
                    // pot only if NOT flowerpot
                    (l.contains("pot") && !l.contains("flowerpot") && !l.contains("pottery"))
                -> WasteCategory.METAL

            // ---- Recyclable (plastic) ----
            l.contains("water bottle") || l.contains("pop bottle") ||
                    l.contains("plastic bag") || l.contains("shopping basket") ||
                    l.contains("bottlecap") || l.contains("jug") ||
                    l.contains("bucket, pail") || l.contains("tub, vat") ||
                    l.contains("cup") || l.contains("straw") ||
                    l.contains("balloon") || l.contains("rubber eraser") ||
                    l.contains("poncho") || l.contains("bib") ||
                    l.contains("plastic") || l.contains("container ship") ||
                    // generic bottle — after glass checks so glass bottles go to GLASS
                    l.contains("bottle")
                -> WasteCategory.RECYCLABLE

            // ---- Recyclable (general) ----
            l.contains("recycle") || l.contains("recyclable")
                -> WasteCategory.RECYCLABLE

            else -> WasteCategory.MIXED_WASTE
        }
    }

    companion object {

        /**
         * Background / environment label keywords.
         *
         * When any of these words appear in a MobileNet label, that label's score
         * is zeroed before category mapping. This prevents the environment the
         * waste item is sitting on or in front of from dominating the result.
         *
         * Real-world scenario examples this handles:
         *   - Plastic bottle on grass  → "lakeside", "valley" zeroed → bottle wins
         *   - Cardboard on pavement    → "sidewalk", "road" zeroed   → cardboard wins
         *   - Can on a table           → "dining table" zeroed       → can wins
         *   - Glass jar on a shelf     → "bookcase", "library" zeroed → jar wins
         */
        private val BACKGROUND_LABELS = setOf(
            // ---- Nature / outdoor surfaces ----
            "lakeside", "lakeshore", "seashore", "coast", "seacoast",
            "sandbar", "sand bar", "coral reef", "geyser",
            "valley", "vale", "volcano", "alp", "promontory", "headland",
            "cliff", "bubble", "rapeseed",
            // ---- Ground / floor / road surfaces ----
            "sidewalk", "pavement", "road", "path", "track",
            "tiled floor", "floor", "carpet", "rug", "doormat",
            "gravel", "dirt", "soil", "mud", "sand",
            // ---- Vegetation ----
            "lawn", "grass", "hay", "straw", "thatch",
            "leaf", "leaves", "foliage", "tree", "bush", "shrub",
            "daisy", "dandelion", "flower", "petal", "plant",
            // ---- Sky / water / weather ----
            "sky", "cloud", "fog", "mist", "rain",
            "ocean", "sea", "river", "stream", "pond", "lake", "water",
            "wave", "surf",
            // ---- Walls / structures (background surfaces) ----
            "wall", "brick", "stone wall", "concrete", "cement",
            "fence", "railing", "gate", "picket fence",
            "cliff dwelling", "dam", "dam", "breakwater",
            // ---- Indoor surfaces ----
            "table", "desk", "shelf", "counter", "surface",
            "tablecloth", "placemat",
            // ---- Lighting / sky conditions ----
            "spotlight", "beam", "sunlight",
            // ---- People / animals that aren't the waste item ----
            "person", "man", "woman", "child", "face",
            "hand", "arm", "leg", "shoe",
            // ---- Fabric / clothing that is background ----
            "curtain", "drape", "blind", "shoji",
            // ---- Architecture that is background ----
            "window", "door", "wall clock", "fire hydrant",
            "street sign", "traffic light", "manhole cover"
        )

        /**
         * Complete MobileNet V1 1001-class label list.
         * Index 0 = background. Indices 1–1000 = ImageNet ILSVRC 2012 classes.
         */
        val MOBILENET_V1_LABELS = mapOf(
            0 to "background",
            1 to "tench, Tinca tinca",
            2 to "goldfish, Carassius auratus",
            3 to "great white shark, white shark",
            4 to "tiger shark, Galeocerdo cuvieri",
            5 to "hammerhead, hammerhead shark",
            6 to "electric ray, crampfish, numbfish, torpedo",
            7 to "stingray",
            8 to "cock",
            9 to "hen",
            10 to "ostrich, Struthio camelus",
            11 to "brambling, Fringilla montifringilla",
            12 to "goldfinch, Carduelis carduelis",
            13 to "house finch, linnet, Carpodacus mexicanus",
            14 to "junco, snowbird",
            15 to "indigo bunting, indigo finch, indigo bird, Passerina cyanea",
            16 to "robin, American robin, Turdus migratorius",
            17 to "bulbul",
            18 to "jay",
            19 to "magpie",
            20 to "chickadee",
            21 to "water ouzel, dipper",
            22 to "kite",
            23 to "bald eagle, American eagle, Haliaeetus leucocephalus",
            24 to "vulture",
            25 to "great grey owl, great gray owl, Strix nebulosa",
            26 to "European fire salamander, Salamandra salamandra",
            27 to "common newt, Triturus vulgaris",
            28 to "eft",
            29 to "spotted salamander, Ambystoma maculatum",
            30 to "axolotl, mud puppy, Ambystoma mexicanum",
            31 to "bullfrog, Rana catesbeiana",
            32 to "tree frog, tree-frog",
            33 to "tailed frog, bell toad, ribbed toad, tailed toad, Ascaphus trui",
            34 to "loggerhead, loggerhead turtle, Caretta caretta",
            35 to "leatherback turtle, leatherback, leathery turtle, Dermochelys coriacea",
            36 to "mud turtle",
            37 to "terrapin",
            38 to "box turtle, box tortoise",
            39 to "banded gecko",
            40 to "common iguana, iguana, Iguana iguana",
            41 to "American chameleon, anole, Anolis carolinensis",
            42 to "whiptail, whiptail lizard",
            43 to "agama",
            44 to "frilled lizard, Chlamydosaurus kingi",
            45 to "alligator lizard",
            46 to "Gila monster, Heloderma suspectum",
            47 to "green lizard, Lacerta viridis",
            48 to "African chameleon, Chamaeleo chamaeleon",
            49 to "Komodo dragon, Komodo lizard, dragon lizard, giant lizard, Varanus komodoensis",
            50 to "African crocodile, Nile crocodile, Crocodylus niloticus",
            51 to "American alligator, Alligator mississipiensis",
            52 to "triceratops",
            53 to "thunder snake, worm snake, Carphophis amoenus",
            54 to "ringneck snake, ring-necked snake, ring snake",
            55 to "hognose snake, puff adder, sand viper",
            56 to "green snake, grass snake",
            57 to "king snake, kingsnake",
            58 to "garter snake, grass snake",
            59 to "water snake",
            60 to "vine snake",
            61 to "night snake, Hypsiglena torquata",
            62 to "boa constrictor, Constrictor constrictor",
            63 to "rock python, rock snake, Python sebae",
            64 to "Indian cobra, Naja naja",
            65 to "green mamba",
            66 to "sea snake",
            67 to "horned viper, cerastes, sand viper, horned asp, Cerastes cornutus",
            68 to "diamondback, diamondback rattlesnake, Crotalus adamanteus",
            69 to "sidewinder, horned rattlesnake, Crotalus cerastes",
            70 to "trilobite",
            71 to "harvestman, daddy longlegs, Phalangium opilio",
            72 to "scorpion",
            73 to "black and gold garden spider, Argiope aurantia",
            74 to "barn spider, Araneus cavaticus",
            75 to "garden spider, Aranea diademata",
            76 to "black widow, Latrodectus mactans",
            77 to "tarantula",
            78 to "wolf spider, hunting spider",
            79 to "tick",
            80 to "centipede",
            81 to "black grouse",
            82 to "ptarmigan",
            83 to "ruffed grouse, partridge, Bonasa umbellus",
            84 to "prairie chicken, prairie grouse, prairie fowl",
            85 to "peacock",
            86 to "quail",
            87 to "partridge",
            88 to "African grey, African gray, Psittacus erithacus",
            89 to "macaw",
            90 to "sulphur-crested cockatoo, Kakatoe galerita, Cacatua galerita",
            91 to "lorikeet",
            92 to "coucal",
            93 to "bee eater",
            94 to "hornbill",
            95 to "hummingbird",
            96 to "jacamar",
            97 to "toucan",
            98 to "drake",
            99 to "red-breasted merganser, Mergus serrator",
            100 to "goose",
            101 to "black swan, Cygnus atratus",
            102 to "tusker",
            103 to "echidna, spiny anteater, anteater",
            104 to "platypus, duckbill, duckbilled platypus, duck-billed platypus, Ornithorhynchus anatinus",
            105 to "wallaby, brush kangaroo",
            106 to "koala, koala bear, kangaroo bear, native bear, Phascolarctos cinereus",
            107 to "wombat",
            108 to "jellyfish",
            109 to "sea anemone, anemone",
            110 to "brain coral",
            111 to "flatworm, platyhelminth",
            112 to "nematode, nematode worm, roundworm",
            113 to "conch",
            114 to "snail",
            115 to "slug",
            116 to "sea slug, nudibranch",
            117 to "chiton, coat-of-mail shell, sea cradle, polyplacophore",
            118 to "chambered nautilus, pearly nautilus, nautilus",
            119 to "Dungeness crab, Cancer magister",
            120 to "rock crab, Cancer irroratus",
            121 to "fiddler crab",
            122 to "king crab, Alaska crab, Alaskan king crab, Alaska king crab, Paralithodes camtschatica",
            123 to "American lobster, Northern lobster, Maine lobster, Homarus americanus",
            124 to "spiny lobster, langouste, rock lobster, crawfish, crayfish, sea crawfish",
            125 to "crayfish, crawfish, crawdad, crawdaddy",
            126 to "hermit crab",
            127 to "isopod",
            128 to "white stork, Ciconia ciconia",
            129 to "black stork, Ciconia nigra",
            130 to "spoonbill",
            131 to "flamingo",
            132 to "little blue heron, Egretta caerulea",
            133 to "American egret, great white heron, Egretta albus",
            134 to "bittern",
            135 to "crane",
            136 to "limpkin, Aramus pictus",
            137 to "European gallinule, Porphyrio porphyrio",
            138 to "American coot, marsh hen, mud hen, water hen, Fulica americana",
            139 to "bustard",
            140 to "ruddy turnstone, Arenaria interpres",
            141 to "red-backed sandpiper, dunlin, Erolia alpina",
            142 to "redshank, Tringa totanus",
            143 to "dowitcher",
            144 to "oystercatcher, oyster catcher",
            145 to "pelican",
            146 to "king penguin, Aptenodytes patagonica",
            147 to "albatross, mollymawk",
            148 to "grey whale, gray whale, devilfish, Eschrichtius gibbosus, Eschrichtius robustus",
            149 to "killer whale, killer, orca, grampus, sea wolf, Orcinus orca",
            150 to "dugong, Dugong dugon",
            151 to "sea lion",
            152 to "Chihuahua",
            153 to "Japanese spaniel",
            154 to "Maltese dog, Maltese terrier, Maltese",
            155 to "Pekinese, Pekingese, Peke",
            156 to "Shih-Tzu",
            157 to "Blenheim spaniel",
            158 to "papillon",
            159 to "toy terrier",
            160 to "Rhodesian ridgeback",
            161 to "Afghan hound, Afghan",
            162 to "basset, basset hound",
            163 to "beagle",
            164 to "bloodhound, sleuthhound",
            165 to "bluetick",
            166 to "black-and-tan coonhound",
            167 to "Walker hound, Walker foxhound",
            168 to "English foxhound",
            169 to "redbone",
            170 to "borzoi, Russian wolfhound",
            171 to "Irish wolfhound",
            172 to "Italian greyhound",
            173 to "whippet",
            174 to "Ibizan hound, Ibizan Podenco",
            175 to "Norwegian elkhound, elkhound",
            176 to "otterhound, otter hound",
            177 to "Saluki, gazelle hound",
            178 to "Scottish deerhound, deerhound",
            179 to "Weimaraner",
            180 to "Staffordshire bullterrier, Staffordshire bull terrier",
            181 to "American Staffordshire terrier, Staffordshire terrier, American pit bull terrier, pit bull terrier",
            182 to "Bedlington terrier",
            183 to "Border terrier",
            184 to "Kerry blue terrier",
            185 to "Irish terrier",
            186 to "Norfolk terrier",
            187 to "Norwich terrier",
            188 to "Yorkshire terrier",
            189 to "wire-haired fox terrier",
            190 to "Lakeland terrier",
            191 to "Sealyham terrier, Sealyham",
            192 to "Airedale, Airedale terrier",
            193 to "cairn, cairn terrier",
            194 to "Australian terrier",
            195 to "Dandie Dinmont, Dandie Dinmont terrier",
            196 to "Boston bull, Boston terrier",
            197 to "miniature schnauzer",
            198 to "giant schnauzer",
            199 to "standard schnauzer",
            200 to "Scotch terrier, Scottish terrier, Scottie",
            201 to "Tibetan terrier, chrysanthemum dog",
            202 to "silky terrier, Sydney silky",
            203 to "soft-coated wheaten terrier",
            204 to "West Highland white terrier",
            205 to "Lhasa, Lhasa apso",
            206 to "flat-coated retriever",
            207 to "curly-coated retriever",
            208 to "golden retriever",
            209 to "Labrador retriever",
            210 to "Chesapeake Bay retriever",
            211 to "German short-haired pointer",
            212 to "vizsla, Hungarian pointer",
            213 to "English setter",
            214 to "Irish setter, red setter",
            215 to "Gordon setter",
            216 to "Brittany spaniel",
            217 to "clumber, clumber spaniel",
            218 to "English springer, English springer spaniel",
            219 to "Welsh springer spaniel",
            220 to "cocker spaniel, English cocker spaniel, cocker",
            221 to "Sussex spaniel",
            222 to "Irish water spaniel",
            223 to "kuvasz",
            224 to "schipperke",
            225 to "groenendael",
            226 to "malinois",
            227 to "briard",
            228 to "kelpie",
            229 to "komondor",
            230 to "Old English sheepdog, bobtail",
            231 to "Shetland sheepdog, Shetland sheep dog, Shetland",
            232 to "collie",
            233 to "Border collie",
            234 to "Bouvier des Flandres, Bouviers des Flandres",
            235 to "Rottweiler",
            236 to "German shepherd, German shepherd dog, German police dog, alsatian",
            237 to "Doberman, Doberman pinscher",
            238 to "miniature pinscher",
            239 to "Greater Swiss Mountain dog",
            240 to "Bernese mountain dog",
            241 to "Appenzeller",
            242 to "EntleBucher",
            243 to "boxer",
            244 to "bull mastiff",
            245 to "Tibetan mastiff",
            246 to "French bulldog",
            247 to "Great Dane",
            248 to "Saint Bernard, St Bernard",
            249 to "Eskimo dog, husky",
            250 to "malamute, malemute, Alaskan malamute",
            251 to "Siberian husky",
            252 to "dalmatian, coach dog, carriage dog",
            253 to "affenpinscher, monkey pinscher, monkey dog",
            254 to "basenji",
            255 to "pug, pug-dog",
            256 to "Leonberg",
            257 to "Newfoundland, Newfoundland dog",
            258 to "Great Pyrenees",
            259 to "Samoyed, Samoyede",
            260 to "Pomeranian",
            261 to "chow, chow chow",
            262 to "keeshond",
            263 to "Brabancon griffon",
            264 to "Pembroke, Pembroke Welsh corgi",
            265 to "Cardigan, Cardigan Welsh corgi",
            266 to "toy poodle",
            267 to "miniature poodle",
            268 to "standard poodle",
            269 to "Mexican hairless",
            270 to "timber wolf, grey wolf, gray wolf, Canis lupus",
            271 to "white wolf, Arctic wolf, Canis lupus tundrarum",
            272 to "red wolf, maned wolf, Canis rufus, Canis niger",
            273 to "coyote, prairie wolf, brush wolf, Canis latrans",
            274 to "dingo, warrigal, warragal, Canis dingo",
            275 to "dhole, Cuon alpinus",
            276 to "African hunting dog, hyena dog, Cape hunting dog, Lycaon pictus",
            277 to "hyena, hyaena",
            278 to "red fox, Vulpes vulpes",
            279 to "kit fox, Vulpes macrotis",
            280 to "Arctic fox, white fox, Alopex lagopus",
            281 to "grey fox, gray fox, Urocyon cinereoargenteus",
            282 to "tabby, tabby cat",
            283 to "tiger cat",
            284 to "Persian cat",
            285 to "Siamese cat, Siamese",
            286 to "Egyptian cat",
            287 to "cougar, puma, catamount, mountain lion, painter, panther, Felis concolor",
            288 to "lynx, catamount",
            289 to "leopard, Panthera pardus",
            290 to "snow leopard, ounce, Panthera uncia",
            291 to "jaguar, panther, Panthera onca, Felis onca",
            292 to "lion, king of beasts, Panthera leo",
            293 to "tiger, Panthera tigris",
            294 to "cheetah, chetah, Acinonyx jubatus",
            295 to "brown bear, bruin, Ursus arctos",
            296 to "American black bear, black bear, Ursus americanus, Euarctos americanus",
            297 to "ice bear, polar bear, Ursus Maritimus, Thalarctos maritimus",
            298 to "sloth bear, Melursus ursinus, Ursus ursinus",
            299 to "mongoose",
            300 to "meerkat, mierkat",
            301 to "tiger beetle",
            302 to "ladybug, ladybeetle, lady beetle, ladybird, ladybird beetle",
            303 to "ground beetle, carabid beetle",
            304 to "long-horned beetle, longicorn, longicorn beetle",
            305 to "leaf beetle, chrysomelid",
            306 to "dung beetle",
            307 to "rhinoceros beetle",
            308 to "weevil",
            309 to "fly",
            310 to "bee",
            311 to "ant, emmet, pismire",
            312 to "grasshopper, hopper",
            313 to "cricket",
            314 to "walking stick, walkingstick, stick insect",
            315 to "cockroach, roach",
            316 to "mantis, mantid",
            317 to "cicada, cicala",
            318 to "leafhopper",
            319 to "lacewing, lacewing fly",
            320 to "dragonfly, darning needle, devil's darning needle, sewing needle, snake feeder, snake doctor, mosquito hawk, skeeter hawk",
            321 to "damselfly",
            322 to "admiral",
            323 to "ringlet, ringlet butterfly",
            324 to "monarch, monarch butterfly, milkweed butterfly, Danaus plexippus",
            325 to "cabbage butterfly",
            326 to "sulphur butterfly, sulfur butterfly",
            327 to "lycaenid, lycaenid butterfly",
            328 to "starfish, sea star",
            329 to "sea urchin",
            330 to "sea cucumber, holothurian",
            331 to "wood rabbit, cottontail, cottontail rabbit",
            332 to "hare",
            333 to "Angora, Angora rabbit",
            334 to "hamster",
            335 to "porcupine, hedgehog",
            336 to "fox squirrel, eastern fox squirrel, Sciurus niger",
            337 to "marmot",
            338 to "beaver",
            339 to "guinea pig, Cavia cobaya",
            340 to "sorrel",
            341 to "zebra",
            342 to "pig, hog, grunter, squealer, Sus scrofa",
            343 to "wild boar, boar, Sus scrofa",
            344 to "warthog",
            345 to "hippopotamus, hippo, river horse, Hippopotamus amphibius",
            346 to "ox",
            347 to "water buffalo, water ox, Asiatic buffalo, Bubalus bubalis",
            348 to "bison",
            349 to "ram, tup",
            350 to "bighorn, bighorn sheep, cimarron, Rocky Mountain bighorn, Rocky Mountain sheep, Ovis canadensis",
            351 to "ibex, Capra ibex",
            352 to "hartebeest",
            353 to "impala, Aepyceros melampus",
            354 to "gazelle",
            355 to "Arabian camel, dromedary, Camelus dromedarius",
            356 to "llama",
            357 to "weasel",
            358 to "mink",
            359 to "polecat, fitch, foulmart, foumart, Mustela putorius",
            360 to "black-footed ferret, ferret, Mustela nigripes",
            361 to "otter",
            362 to "skunk, polecat, wood pussy",
            363 to "badger",
            364 to "armadillo",
            365 to "three-toed sloth, ai, Bradypus tridactylus",
            366 to "orangutan, orang, orangutang, Pongo pygmaeus",
            367 to "gorilla, Gorilla gorilla",
            368 to "chimpanzee, chimp, Pan troglodytes",
            369 to "gibbon, Hylobates lar",
            370 to "siamang, Hylobates syndactylus, Symphalangus syndactylus",
            371 to "guenon, guenon monkey",
            372 to "patas, hussar monkey, Erythrocebus patas",
            373 to "baboon",
            374 to "macaque",
            375 to "langur",
            376 to "colobus, colobus monkey",
            377 to "proboscis monkey, Nasalis larvatus",
            378 to "marmoset",
            379 to "capuchin, ringtail, Cebus capucinus",
            380 to "howler monkey, howler",
            381 to "titi, titi monkey",
            382 to "spider monkey, Ateles geoffroyi",
            383 to "squirrel monkey, Saimiri sciureus",
            384 to "Madagascar cat, ring-tailed lemur, Lemur catta",
            385 to "indri, indris, Indri indri, Indri brevicaudatus",
            386 to "Indian elephant, Elephas maximus",
            387 to "African elephant, Loxodonta africana",
            388 to "lesser panda, red panda, panda, bear cat, cat bear, Ailurus fulgens",
            389 to "giant panda, panda, panda bear, coon bear, Ailuropoda melanoleuca",
            390 to "barracouta, snoek",
            391 to "eel",
            392 to "coho, cohoe, coho salmon, blue jack, silver salmon, Oncorhynchus kisutch",
            393 to "rock beauty, Holocanthus tricolor",
            394 to "anemone fish",
            395 to "sturgeon",
            396 to "gar, garfish, garpike, billfish, Lepisosteus osseus",
            397 to "lionfish",
            398 to "puffer, pufferfish, blowfish, globefish",
            399 to "abacus",
            400 to "abaya",
            401 to "academic gown, academic robe, judge's robe",
            402 to "accordion, piano accordion, squeeze box",
            403 to "acoustic guitar",
            404 to "aircraft carrier, carrier, flattop, attack aircraft carrier",
            405 to "airliner",
            406 to "airship, dirigible",
            407 to "altar",
            408 to "ambulance",
            409 to "amphibian, amphibious vehicle",
            410 to "analog clock",
            411 to "apiary, bee house",
            412 to "apron",
            413 to "ashcan, trash can, garbage can, wastebin, ash bin, ash-bin, ashbin, dustbin, trash barrel, trash bin",
            414 to "assault rifle, assault gun",
            415 to "backpack, back pack, knapsack, packsack, rucksack, haversack",
            416 to "bakery, bakeshop, bakehouse",
            417 to "balance beam, beam",
            418 to "balloon",
            419 to "ballpoint, ballpoint pen, ballpen, Biro",
            420 to "Band Aid",
            421 to "banjo",
            422 to "bannister, banister, balustrade, balusters, handrail",
            423 to "barbell",
            424 to "barber chair",
            425 to "barbershop",
            426 to "barn",
            427 to "barometer",
            428 to "barrel, cask",
            429 to "barrow, garden cart, lawn cart, wheelbarrow",
            430 to "baseball",
            431 to "basketball",
            432 to "bassinet",
            433 to "bassoon",
            434 to "bathing cap, swimming cap",
            435 to "bath towel",
            436 to "bathtub, bathing tub, bath, tub",
            437 to "beach wagon, station wagon, wagon, estate car, beach waggon, station waggon, waggon",
            438 to "beacon, lighthouse, beacon light, pharos",
            439 to "beaker",
            440 to "bearskin, busby, shako",
            441 to "beer bottle",
            442 to "beer glass",
            443 to "bell cote, bell cot",
            444 to "bib",
            445 to "bicycle-built-for-two, tandem bicycle, tandem",
            446 to "bikini, two-piece",
            447 to "binder, ring-binder",
            448 to "binoculars, field glasses, opera glasses",
            449 to "birdhouse",
            450 to "boathouse",
            451 to "bobsled, bobsleigh, bob",
            452 to "bolo tie, bolo, bola tie, bola",
            453 to "bonnet, poke bonnet",
            454 to "bookcase",
            455 to "bookshop, bookstore, bookstall",
            456 to "bottlecap",
            457 to "bow",
            458 to "bow tie, bow-tie, bowtie",
            459 to "brass, memorial tablet, plaque",
            460 to "brassiere, bra, bandeau",
            461 to "breakwater, groin, groyne, mole, bulwark, seawall, jetty",
            462 to "breastplate, aegis, egis",
            463 to "broom",
            464 to "bucket, pail",
            465 to "buckle",
            466 to "bulletproof vest",
            467 to "bullet train, bullet",
            468 to "butcher shop, meat market",
            469 to "cab, hack, taxi, taxicab",
            470 to "caldron, cauldron",
            471 to "candle, taper, wax light",
            472 to "cannon",
            473 to "canoe",
            474 to "can opener, tin opener",
            475 to "cardigan",
            476 to "car mirror",
            477 to "carousel, carrousel, merry-go-round, roundabout, whirligig",
            478 to "carpenter's kit, tool kit",
            479 to "carton",
            480 to "car wheel",
            481 to "cash machine, cash dispenser, automated teller machine, automatic teller machine, automated teller, automatic teller, ATM",
            482 to "cassette",
            483 to "cassette player",
            484 to "castle",
            485 to "catamaran",
            486 to "CD player",
            487 to "cello, violoncello",
            488 to "cellular telephone, cellular phone, cellphone, cell, mobile phone",
            489 to "chain",
            490 to "chainlink fence",
            491 to "chain mail, ring mail, mail, chain armor, chain armour, ring armor, ring armour",
            492 to "chain saw, chainsaw",
            493 to "chest",
            494 to "chiffonier, commode",
            495 to "chime, bell, gong",
            496 to "china cabinet, china closet",
            497 to "Christmas stocking",
            498 to "church, church building",
            499 to "cinema, movie theater, movie theatre, movie house, picture palace",
            500 to "cleaver, meat cleaver, chopper",
            501 to "cliff dwelling",
            502 to "cloak",
            503 to "clog, geta, patten, sabot",
            504 to "cocktail shaker",
            505 to "coffee mug",
            506 to "coffeepot",
            507 to "coil, spiral, volute, whorl, helix",
            508 to "combination lock",
            509 to "computer keyboard, keypad",
            510 to "confectionery, confectionary, candy store",
            511 to "container ship, containership, container vessel",
            512 to "convertible",
            513 to "corkscrew, bottle screw",
            514 to "cornet, horn, trumpet, trump",
            515 to "cowboy boot",
            516 to "cowboy hat, ten-gallon hat",
            517 to "cradle",
            518 to "crane",
            519 to "crash helmet",
            520 to "crate",
            521 to "crib, cot",
            522 to "Crock Pot",
            523 to "croquet ball",
            524 to "crutch",
            525 to "cuirass",
            526 to "dam, dike, dyke",
            527 to "desk",
            528 to "desktop computer",
            529 to "dial telephone, dial phone",
            530 to "diaper, nappy, napkin",
            531 to "digital clock",
            532 to "digital watch",
            533 to "dining table, board",
            534 to "dishrag, dishcloth",
            535 to "dishwasher, dish washer, dishwashing machine",
            536 to "disk brake, disc brake",
            537 to "dock, dockage, docking facility",
            538 to "dogsled, dog sled, dog sleigh",
            539 to "dome",
            540 to "doormat, welcome mat",
            541 to "drilling platform, offshore rig",
            542 to "drum, membranophone, tympan",
            543 to "drumstick",
            544 to "dumbbell",
            545 to "Dutch oven",
            546 to "electric fan, blower",
            547 to "electric guitar",
            548 to "electric locomotive",
            549 to "entertainment center",
            550 to "envelope",
            551 to "espresso maker",
            552 to "face powder",
            553 to "feather boa, boa",
            554 to "file, file cabinet, filing cabinet",
            555 to "fireboat",
            556 to "fire engine, fire truck",
            557 to "fire screen, fireguard",
            558 to "flagpole, flagstaff",
            559 to "flute, transverse flute",
            560 to "folding chair",
            561 to "football helmet",
            562 to "forklift",
            563 to "fountain",
            564 to "fountain pen",
            565 to "four-poster",
            566 to "freight car",
            567 to "French horn, horn",
            568 to "frying pan, frypan, skillet",
            569 to "fur coat",
            570 to "garbage truck, dustcart",
            571 to "gasmask, respirator, gas helmet",
            572 to "gas pump, gasoline pump, petrol pump, island dispenser",
            573 to "goblet",
            574 to "go-kart",
            575 to "golf ball",
            576 to "golfcart, golf cart",
            577 to "gondola",
            578 to "gong, tam-tam",
            579 to "gown",
            580 to "grand piano, grand",
            581 to "greenhouse, nursery, glasshouse",
            582 to "grille, radiator grille",
            583 to "grocery store, grocery, food market, market",
            584 to "guillotine",
            585 to "hair slide",
            586 to "hair spray",
            587 to "half track",
            588 to "hammer",
            589 to "hamper",
            590 to "hand blower, blow dryer, blow drier, hair dryer, hair drier",
            591 to "hand-held computer, hand-held microcomputer",
            592 to "handkerchief, hankie, hanky, hanky-panky",
            593 to "hard disc, hard disk, fixed disk",
            594 to "harmonica, mouth organ, harp, mouth harp",
            595 to "harp",
            596 to "harvester, reaper",
            597 to "hatchet",
            598 to "holster",
            599 to "home theater, home theatre",
            600 to "honeycomb",
            601 to "hook, claw",
            602 to "hoopskirt, crinoline",
            603 to "horizontal bar, high bar",
            604 to "horse cart, horse-cart",
            605 to "hourglass",
            606 to "iPod",
            607 to "iron, smoothing iron",
            608 to "jack-o'-lantern",
            609 to "jean, blue jean, denim",
            610 to "jeep, landrover",
            611 to "jersey, T-shirt, tee shirt",
            612 to "jigsaw puzzle",
            613 to "jinrikisha, ricksha, rickshaw",
            614 to "joystick",
            615 to "kimono",
            616 to "knee pad",
            617 to "knot",
            618 to "lab coat, laboratory coat",
            619 to "ladle",
            620 to "lampshade, lamp shade",
            621 to "laptop, laptop computer",
            622 to "lawn mower, mower",
            623 to "lens cap, lens cover",
            624 to "letter opener, paper knife, paperknife",
            625 to "library",
            626 to "lifeboat",
            627 to "lighter, light, igniter, ignitor",
            628 to "limousine, limo",
            629 to "liner, ocean liner",
            630 to "lipstick, lip rouge",
            631 to "Loafer",
            632 to "lotion",
            633 to "loudspeaker, speaker, speaker unit, loudspeaker system, speaker system",
            634 to "loupe, jeweler's loupe",
            635 to "lumbermill, sawmill",
            636 to "magnetic compass",
            637 to "mailbag, postbag",
            638 to "mailbox, letter box",
            639 to "maillot",
            640 to "maillot, tank suit",
            641 to "manhole cover",
            642 to "maraca",
            643 to "marimba, xylophone",
            644 to "mask",
            645 to "matchstick",
            646 to "maypole",
            647 to "maze, labyrinth",
            648 to "measuring cup",
            649 to "medicine chest, medicine cabinet",
            650 to "megalith, megalithic structure",
            651 to "microphone, mike",
            652 to "microwave, microwave oven",
            653 to "military uniform",
            654 to "milk can",
            655 to "minibus",
            656 to "miniskirt, mini",
            657 to "minivan",
            658 to "missile",
            659 to "mitten",
            660 to "mixing bowl",
            661 to "mobile home, manufactured home",
            662 to "Model T",
            663 to "modem",
            664 to "monastery",
            665 to "monitor",
            666 to "moped",
            667 to "mortar",
            668 to "mortarboard",
            669 to "mosque",
            670 to "mosquito net",
            671 to "motor scooter, scooter",
            672 to "mountain bike, all-terrain bike, off-roader",
            673 to "mountain tent",
            674 to "mouse, computer mouse",
            675 to "mousetrap",
            676 to "moving van",
            677 to "muzzle",
            678 to "nail",
            679 to "neck brace",
            680 to "necklace",
            681 to "needle",
            682 to "Nematode",
            683 to "nipple",
            684 to "notebook, notebook computer",
            685 to "obelisk",
            686 to "oboe, hautboy, hautbois",
            687 to "ocarina, sweet potato",
            688 to "odometer, hodometer, mileometer, milometer",
            689 to "oil filter",
            690 to "organ, pipe organ",
            691 to "oscilloscope, scope, cathode-ray oscilloscope, CRO",
            692 to "overskirt",
            693 to "oxcart",
            694 to "oxygen mask",
            695 to "packet",
            696 to "paddle, boat paddle",
            697 to "paddlewheel, paddle wheel",
            698 to "padlock",
            699 to "paintbrush",
            700 to "pajama, pyjama, pj's, jammies",
            701 to "palace",
            702 to "panpipe, pandean pipe, syrinx",
            703 to "paper towel",
            704 to "parachute, chute",
            705 to "parallel bars, bars",
            706 to "park bench",
            707 to "parking meter",
            708 to "passenger car, coach, carriage",
            709 to "patio, terrace",
            710 to "pay-phone, pay-station",
            711 to "pedestal, plinth, footstall",
            712 to "pencil box, pencil case",
            713 to "pencil sharpener",
            714 to "perfume, essence",
            715 to "Petri dish",
            716 to "photocopier",
            717 to "pick, plectrum, plectron",
            718 to "pickelhaube",
            719 to "picket fence, paling",
            720 to "pickup, pickup truck",
            721 to "pier",
            722 to "piggy bank, penny bank",
            723 to "pill bottle",
            724 to "pillow",
            725 to "ping-pong ball",
            726 to "pinwheel",
            727 to "pirate, pirate ship",
            728 to "pitcher, ewer",
            729 to "plane, carpenter's plane, woodworking plane",
            730 to "planetarium",
            731 to "plastic bag",
            732 to "plate rack",
            733 to "plow, plough",
            734 to "plunger, plumber's helper",
            735 to "Polaroid camera, Polaroid Land camera",
            736 to "pole",
            737 to "police van, police wagon, paddy wagon, patrol wagon, wagon, black Maria",
            738 to "poncho",
            739 to "pool table, billiard table, snooker table",
            740 to "pop bottle, soda bottle",
            741 to "pot, flowerpot",
            742 to "potter's wheel",
            743 to "power drill",
            744 to "prayer rug, prayer mat",
            745 to "printer",
            746 to "prison, prison house",
            747 to "projectile, missile",
            748 to "projector",
            749 to "puck, hockey puck",
            750 to "punching bag, punch bag, punching ball, punchball",
            751 to "purse",
            752 to "quill, quill pen",
            753 to "quilt, comforter, comfort, puff",
            754 to "racer, race car, racing car",
            755 to "racket, racquet",
            756 to "radiator",
            757 to "radio, wireless",
            758 to "radio telescope, radio reflector",
            759 to "rain barrel",
            760 to "recreational vehicle, RV, R.V.",
            761 to "reel",
            762 to "reflex camera",
            763 to "refrigerator, icebox",
            764 to "remote control, remote",
            765 to "restaurant, eating house, eating place, eatery",
            766 to "revolver, six-gun, six-shooter",
            767 to "rifle",
            768 to "rocking chair, rocker",
            769 to "rotisserie",
            770 to "rubber eraser, rubber, pencil eraser",
            771 to "rugby ball",
            772 to "rule, ruler",
            773 to "running shoe",
            774 to "safe",
            775 to "safety pin",
            776 to "saltshaker, salt shaker",
            777 to "sandal",
            778 to "sarong",
            779 to "saxophone",
            780 to "scabbard",
            781 to "scale, weighing machine",
            782 to "school bus",
            783 to "schooner",
            784 to "scoreboard",
            785 to "screen, CRT screen",
            786 to "screw",
            787 to "sewing machine",
            788 to "shield, buckler",
            789 to "shoe shop, shoe-shop, shoe store",
            790 to "shoji",
            791 to "shopping basket",
            792 to "shopping cart",
            793 to "shovel",
            794 to "shower cap",
            795 to "shower curtain",
            796 to "ski",
            797 to "ski mask",
            798 to "sleeping bag",
            799 to "slide rule, slipstick",
            800 to "sliding door",
            801 to "slot, one-armed bandit",
            802 to "snorkel",
            803 to "snowmobile",
            804 to "snowplow, snowplough",
            805 to "soap dispenser",
            806 to "soccer ball",
            807 to "sock",
            808 to "solar dish, solar collector, solar furnace",
            809 to "sombrero",
            810 to "soup bowl",
            811 to "space bar",
            812 to "space heater",
            813 to "space shuttle",
            814 to "spatula",
            815 to "speedboat",
            816 to "spider web, cobweb",
            817 to "spindle",
            818 to "sports car, sport car",
            819 to "spotlight, spot",
            820 to "stage",
            821 to "steam locomotive",
            822 to "steel arch bridge",
            823 to "steel drum",
            824 to "stethoscope",
            825 to "stole",
            826 to "stone wall",
            827 to "stopwatch, stop watch",
            828 to "stove",
            829 to "strainer",
            830 to "streetcar, tram, tramcar, trolley, trolley car",
            831 to "stretcher",
            832 to "studio couch, day bed",
            833 to "stupa, tope",
            834 to "submarine, pigboat, sub, U-boat",
            835 to "suit, suit of clothes",
            836 to "sundial",
            837 to "sunglass",
            838 to "sunglasses, dark glasses, shades",
            839 to "sunscreen, sunblock, sun blocker",
            840 to "suspension bridge",
            841 to "swab, mop, swob",
            842 to "sweatshirt",
            843 to "swimming trunks, bathing trunks",
            844 to "swing",
            845 to "switch, electric switch, electrical switch",
            846 to "syringe",
            847 to "table lamp",
            848 to "tank, army tank, armored combat vehicle, armoured combat vehicle",
            849 to "tape player",
            850 to "teapot",
            851 to "teddy, teddy bear",
            852 to "television, television system",
            853 to "tennis ball",
            854 to "thatch, thatched roof",
            855 to "theater curtain, theatre curtain",
            856 to "thimble",
            857 to "thresher, thrasher, threshing machine",
            858 to "throne",
            859 to "tile roof",
            860 to "toaster",
            861 to "tobacco shop, tobacconist shop, tobacconist",
            862 to "toilet seat",
            863 to "torch",
            864 to "totem pole",
            865 to "tow truck, tow car, wrecker",
            866 to "toyshop",
            867 to "tractor",
            868 to "trailer truck, tractor trailer, trucking rig, rig, articulated lorry, semi",
            869 to "tray",
            870 to "trench coat",
            871 to "tricycle, trike, velocipede",
            872 to "trimaran",
            873 to "tripod",
            874 to "triumphal arch",
            875 to "trolleybus",
            876 to "trombone",
            877 to "tub, vat",
            878 to "turnstile",
            879 to "typewriter keyboard",
            880 to "umbrella",
            881 to "unicycle, monocycle",
            882 to "upright, upright piano",
            883 to "vacuum, vacuum cleaner",
            884 to "vase",
            885 to "vault",
            886 to "velvet",
            887 to "vending machine",
            888 to "vestment",
            889 to "viaduct",
            890 to "violin, fiddle",
            891 to "volleyball",
            892 to "waffle iron",
            893 to "wall clock",
            894 to "wallet, billfold, notecase, pocketbook",
            895 to "wardrobe, closet, press",
            896 to "warplane, military plane",
            897 to "washbasin, handbasin, washbowl, lavabo, wash-hand basin",
            898 to "washer, automatic washer, washing machine",
            899 to "water bottle",
            900 to "water jug",
            901 to "water tower",
            902 to "whiskey jug",
            903 to "whistle",
            904 to "wig",
            905 to "window screen",
            906 to "window shade",
            907 to "Windsor tie",
            908 to "wine bottle",
            909 to "wing",
            910 to "wok",
            911 to "wooden spoon",
            912 to "wool, woolen, woollen",
            913 to "worm fence, snake fence, snake-rail fence, Virginia fence",
            914 to "wreck",
            915 to "yawl",
            916 to "yurt",
            917 to "web site, website, internet site, site",
            918 to "comic book",
            919 to "crossword puzzle, crossword",
            920 to "street sign",
            921 to "traffic light, traffic signal, stoplight",
            922 to "book jacket, dust cover, dust jacket, dust wrapper",
            923 to "menu",
            924 to "plate",
            925 to "guacamole",
            926 to "consomme",
            927 to "hot pot, hotpot",
            928 to "trifle",
            929 to "ice cream, icecream",
            930 to "ice lolly, lolly, lollipop, popsicle",
            931 to "French loaf",
            932 to "bagel, beigel",
            933 to "pretzel",
            934 to "cheeseburger",
            935 to "hotdog, hot dog, red hot",
            936 to "mashed potato",
            937 to "head cabbage",
            938 to "broccoli",
            939 to "cauliflower",
            940 to "zucchini, courgette",
            941 to "spaghetti squash",
            942 to "acorn squash",
            943 to "butternut squash",
            944 to "cucumber, cuke",
            945 to "artichoke, globe artichoke",
            946 to "bell pepper",
            947 to "cardoon",
            948 to "mushroom",
            949 to "Granny Smith",
            950 to "strawberry",
            951 to "orange",
            952 to "lemon",
            953 to "fig",
            954 to "pineapple, ananas",
            955 to "banana",
            956 to "jackfruit, jak, jack",
            957 to "custard apple",
            958 to "pomegranate",
            959 to "hay",
            960 to "carbonara",
            961 to "chocolate sauce, chocolate syrup",
            962 to "dough",
            963 to "meat loaf, meatloaf",
            964 to "pizza, pizza pie",
            965 to "potpie",
            966 to "burrito",
            967 to "red wine",
            968 to "espresso",
            969 to "cup",
            970 to "eggnog",
            971 to "alp",
            972 to "bubble",
            973 to "cliff, drop, drop-off",
            974 to "coral reef",
            975 to "geyser",
            976 to "lakeside, lakeshore",
            977 to "promontory, headland, head, foreland",
            978 to "sandbar, sand bar",
            979 to "seashore, coast, seacoast, sea-coast",
            980 to "valley, vale",
            981 to "volcano",
            982 to "ballplayer, baseball player",
            983 to "groom, bridegroom",
            984 to "scuba diver",
            985 to "rapeseed",
            986 to "daisy",
            987 to "yellow lady's slipper, yellow lady-slipper, Cypripedium calceolus, Cypripedium parviflorum",
            988 to "corn",
            989 to "acorn",
            990 to "hip, rose hip, rosehip",
            991 to "buckeye, horse chestnut, conker",
            992 to "coral fungus",
            993 to "agaric",
            994 to "gyromitra",
            995 to "stinkhorn, carrion fungus",
            996 to "earthstar",
            997 to "hen-of-the-woods, hen of the woods, Polyporus frondosus, Grifola frondosa",
            998 to "bolete",
            999 to "ear, spike, capitulum",
            1000 to "toilet tissue, toilet paper, bathroom tissue"
        )
    }
}
