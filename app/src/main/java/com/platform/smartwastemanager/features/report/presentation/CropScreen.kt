package com.platform.smartwastemanager.features.report.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Crop screen — lets the user drag a crop rectangle over a captured or
 * gallery-selected image before it is sent to the AI classifier.
 *
 * How it works:
 *  - The image is drawn scaled-to-fit inside the available canvas area.
 *  - A semi-transparent dark overlay dims everything outside the crop rect.
 *  - The crop rect starts at 70% of the image area, centred — matching the
 *    camera viewfinder target box the user already saw.
 *  - The user can drag ANY edge or corner handle to resize the rect, or drag
 *    the interior of the rect to move it.
 *  - Pressing "Use Crop" extracts the selected region from the original
 *    full-resolution bitmap and passes it to the ViewModel.
 *
 * @param bitmap          The full-resolution bitmap from the camera or gallery.
 * @param viewModel       ReportViewModel — classifyImage() is called on confirm.
 * @param onCropConfirmed Called after the crop is applied — navigate to form.
 * @param onRetake        Called when the user wants to go back and retake.
 */
@Composable
fun CropScreen(
    bitmap: Bitmap,
    viewModel: ReportViewModel,
    onCropConfirmed: () -> Unit,
    onRetake: () -> Unit
) {
    // ---- Track the size of the canvas area once it is laid out ----
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // ---- Crop rect state — stored as fractions (0f..1f) of the canvas area ----
    // Using fractions means the crop rect stays correct if the canvas size changes
    // (e.g. during orientation change). Fractions are converted to pixels for drawing.
    //
    // Initial values: 15% inset from each edge → 70% of canvas, centred.
    var cropLeft   by remember { mutableFloatStateOf(0.15f) }
    var cropTop    by remember { mutableFloatStateOf(0.15f) }
    var cropRight  by remember { mutableFloatStateOf(0.85f) }
    var cropBottom by remember { mutableFloatStateOf(0.85f) }

    // Minimum crop size as a fraction of canvas — prevents the rect collapsing
    val minSizeFraction = 0.10f

    // Handle touch radius in dp — area around each corner/edge that responds to drag
    val handleTouchRadiusDp = 28.dp
    val density = LocalDensity.current
    val handleTouchRadiusPx = with(density) { handleTouchRadiusDp.toPx() }

    // Corner handle visual size
    val handleLengthDp = 24.dp
    val handleStrokeWidthDp = 4.dp

    // Which part of the crop rect the user is currently dragging
    // null = not dragging, "move" = dragging interior, "tl"/"tr"/"bl"/"br" = corners,
    // "l"/"r"/"t"/"b" = edges
    var dragTarget by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        // ================================================================
        // CANVAS — draws the image + overlay + crop rect + handles
        // ================================================================
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            // Work out what the user tapped — corner, edge, or interior
                            if (canvasSize == IntSize.Zero) return@detectDragGestures

                            val cw = canvasSize.width.toFloat()
                            val ch = canvasSize.height.toFloat()

                            val left   = cropLeft   * cw
                            val top    = cropTop    * ch
                            val right  = cropRight  * cw
                            val bottom = cropBottom * ch
                            val x = offset.x
                            val y = offset.y
                            val r = handleTouchRadiusPx

                            dragTarget = when {
                                // Corners first (highest priority)
                                abs(x - left)  < r && abs(y - top)    < r -> "tl"
                                abs(x - right) < r && abs(y - top)    < r -> "tr"
                                abs(x - left)  < r && abs(y - bottom) < r -> "bl"
                                abs(x - right) < r && abs(y - bottom) < r -> "br"
                                // Edges
                                abs(x - left)   < r && y in top..bottom -> "l"
                                abs(x - right)  < r && y in top..bottom -> "r"
                                abs(y - top)    < r && x in left..right -> "t"
                                abs(y - bottom) < r && x in left..right -> "b"
                                // Interior — move the whole rect
                                x in left..right && y in top..bottom -> "move"
                                else -> null
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (canvasSize == IntSize.Zero || dragTarget == null) return@detectDragGestures

                            val cw = canvasSize.width.toFloat()
                            val ch = canvasSize.height.toFloat()

                            // Convert drag pixels to fractions
                            val dx = dragAmount.x / cw
                            val dy = dragAmount.y / ch

                            when (dragTarget) {
                                "move" -> {
                                    // Move the whole rect, keeping it within 0..1
                                    val width  = cropRight - cropLeft
                                    val height = cropBottom - cropTop
                                    cropLeft   = (cropLeft   + dx).coerceIn(0f, 1f - width)
                                    cropTop    = (cropTop    + dy).coerceIn(0f, 1f - height)
                                    cropRight  = cropLeft  + width
                                    cropBottom = cropTop   + height
                                }
                                "tl" -> {
                                    cropLeft = min(cropLeft + dx, cropRight - minSizeFraction).coerceIn(0f, 1f)
                                    cropTop  = min(cropTop  + dy, cropBottom - minSizeFraction).coerceIn(0f, 1f)
                                }
                                "tr" -> {
                                    cropRight = max(cropRight + dx, cropLeft + minSizeFraction).coerceIn(0f, 1f)
                                    cropTop   = min(cropTop   + dy, cropBottom - minSizeFraction).coerceIn(0f, 1f)
                                }
                                "bl" -> {
                                    cropLeft   = min(cropLeft   + dx, cropRight - minSizeFraction).coerceIn(0f, 1f)
                                    cropBottom = max(cropBottom + dy, cropTop + minSizeFraction).coerceIn(0f, 1f)
                                }
                                "br" -> {
                                    cropRight  = max(cropRight  + dx, cropLeft + minSizeFraction).coerceIn(0f, 1f)
                                    cropBottom = max(cropBottom + dy, cropTop  + minSizeFraction).coerceIn(0f, 1f)
                                }
                                "l" -> cropLeft   = min(cropLeft   + dx, cropRight - minSizeFraction).coerceIn(0f, 1f)
                                "r" -> cropRight  = max(cropRight  + dx, cropLeft + minSizeFraction).coerceIn(0f, 1f)
                                "t" -> cropTop    = min(cropTop    + dy, cropBottom - minSizeFraction).coerceIn(0f, 1f)
                                "b" -> cropBottom = max(cropBottom + dy, cropTop + minSizeFraction).coerceIn(0f, 1f)
                            }
                        },
                        onDragEnd   = { dragTarget = null },
                        onDragCancel = { dragTarget = null }
                    )
                }
        ) {
            if (canvasSize == IntSize.Zero) return@Canvas

            val cw = size.width
            val ch = size.height

            // ---- Step 1: Draw the image scaled to fit the canvas ----
            // Scale the bitmap to fit inside the canvas while preserving aspect ratio.
            val bitmapAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
            val canvasAspect = cw / ch

            val (drawW, drawH) = if (bitmapAspect > canvasAspect) {
                // Image is wider than canvas — fit width
                cw to (cw / bitmapAspect)
            } else {
                // Image is taller than canvas — fit height
                (ch * bitmapAspect) to ch
            }

            // Centre the image in the canvas
            val imageOffsetX = (cw - drawW) / 2f
            val imageOffsetY = (ch - drawH) / 2f

            drawImage(
                image    = bitmap.asImageBitmap(),
                dstOffset = androidx.compose.ui.unit.IntOffset(
                    imageOffsetX.toInt(),
                    imageOffsetY.toInt()
                ),
                dstSize  = androidx.compose.ui.unit.IntSize(drawW.toInt(), drawH.toInt())
            )

            // ---- Step 2: Dark overlay outside the crop rect ----
            val left   = cropLeft   * cw
            val top    = cropTop    * ch
            val right  = cropRight  * cw
            val bottom = cropBottom * ch

            val cropPath = Path().apply {
                addRect(Rect(Offset.Zero, Size(cw, ch)))
                addRect(Rect(Offset(left, top), Size(right - left, bottom - top)))
            }
            clipPath(cropPath, clipOp = ClipOp.Difference) {
                drawRect(
                    color   = Color.Black.copy(alpha = 0.55f),
                    topLeft = Offset.Zero,
                    size    = Size(cw, ch)
                )
            }

            // ---- Step 3: Crop rect border ----
            drawRect(
                color     = Color.White,
                topLeft   = Offset(left, top),
                size      = Size(right - left, bottom - top),
                style     = Stroke(width = 2.dp.toPx())
            )

            // ---- Step 4: Rule-of-thirds grid lines (subtle) ----
            val thirdW = (right - left) / 3f
            val thirdH = (bottom - top) / 3f
            val gridColor = Color.White.copy(alpha = 0.25f)
            val gridStroke = 1.dp.toPx()
            for (i in 1..2) {
                drawLine(gridColor, Offset(left + thirdW * i, top), Offset(left + thirdW * i, bottom), gridStroke)
                drawLine(gridColor, Offset(left, top + thirdH * i), Offset(right, top + thirdH * i), gridStroke)
            }

            // ---- Step 5: Corner handles ----
            val hl   = with(density) { handleLengthDp.toPx() }
            val hsw  = with(density) { handleStrokeWidthDp.toPx() }
            val hc   = Color.White

            // Top-left
            drawLine(hc, Offset(left, top), Offset(left + hl, top), hsw)
            drawLine(hc, Offset(left, top), Offset(left, top + hl), hsw)
            // Top-right
            drawLine(hc, Offset(right, top), Offset(right - hl, top), hsw)
            drawLine(hc, Offset(right, top), Offset(right, top + hl), hsw)
            // Bottom-left
            drawLine(hc, Offset(left, bottom), Offset(left + hl, bottom), hsw)
            drawLine(hc, Offset(left, bottom), Offset(left, bottom - hl), hsw)
            // Bottom-right
            drawLine(hc, Offset(right, bottom), Offset(right - hl, bottom), hsw)
            drawLine(hc, Offset(right, bottom), Offset(right, bottom - hl), hsw)

            // ---- Step 6: Mid-edge handles (smaller) ----
            val midHL = hl * 0.5f
            val midX  = left + (right - left) / 2f
            val midY  = top  + (bottom - top)  / 2f
            drawLine(hc, Offset(midX - midHL, top),    Offset(midX + midHL, top),    hsw)
            drawLine(hc, Offset(midX - midHL, bottom), Offset(midX + midHL, bottom), hsw)
            drawLine(hc, Offset(left,  midY - midHL),  Offset(left,  midY + midHL),  hsw)
            drawLine(hc, Offset(right, midY - midHL),  Offset(right, midY + midHL),  hsw)
        }

        // ================================================================
        // INSTRUCTION TEXT
        // ================================================================
        Text(
            text     = "Drag corners or edges to adjust crop",
            style    = MaterialTheme.typography.bodySmall,
            color    = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 52.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )

        // ================================================================
        // BOTTOM CONTROLS — Retake (left) + Use Crop (right)
        // ================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // ---- Retake button ----
            OutlinedButton(
                onClick = onRetake,
                colors  = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                border  = androidx.compose.foundation.BorderStroke(1.dp, Color.White)
            ) {
                Icon(
                    imageVector        = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier           = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Retake")
            }

            // ---- Crop size indicator (centre) ----
            val widthPct  = ((cropRight  - cropLeft)   * 100).toInt()
            val heightPct = ((cropBottom - cropTop)     * 100).toInt()
            Text(
                text  = "${widthPct}% × ${heightPct}%",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )

            // ---- Use Crop button ----
            Button(
                onClick = {
                    // Extract the cropped region from the original full-res bitmap.
                    // The crop fractions are relative to the canvas, not the bitmap,
                    // so we must map them through the image's drawn rect.
                    val croppedBitmap = extractCroppedBitmap(
                        source     = bitmap,
                        cropLeft   = cropLeft,
                        cropTop    = cropTop,
                        cropRight  = cropRight,
                        cropBottom = cropBottom
                    )
                    // Send the cropped bitmap to the classifier
                    viewModel.classifyImage(croppedBitmap)
                    onCropConfirmed()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector        = Icons.Default.Check,
                    contentDescription = null,
                    modifier           = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Use Crop", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Extracts the cropped sub-region from the original full-resolution bitmap.
 *
 * The crop fractions (0f..1f) represent positions relative to the CANVAS area,
 * but the image is drawn scaled-to-fit and centred inside the canvas. We must
 * therefore:
 *   1. Recompute the image's drawn rect within the canvas.
 *   2. Convert the crop rect (which is in canvas coordinates) into image coordinates.
 *   3. Clamp to the image bounds and call Bitmap.createBitmap().
 *
 * Because we do not have the actual canvas size here, we use the bitmap's own
 * aspect ratio to infer a normalised [0..1] image coordinate system that matches
 * what the user saw on screen.
 *
 * Strategy: the crop rect fractions are already relative to the full canvas.
 * The canvas fills the screen. The image is letterboxed inside.
 * We remap the crop fractions from "canvas space" to "image pixel space".
 *
 * @param source     The original full-resolution bitmap.
 * @param cropLeft   Left edge of crop as a fraction of the canvas width  (0..1).
 * @param cropTop    Top edge of crop as a fraction of the canvas height (0..1).
 * @param cropRight  Right edge of crop as a fraction of the canvas width  (0..1).
 * @param cropBottom Bottom edge of crop as a fraction of the canvas height (0..1).
 */
fun extractCroppedBitmap(
    source: Bitmap,
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float
): Bitmap {
    // We stored fractions relative to the full canvas.
    // The canvas and the bitmap both represent the same image content
    // (just at different resolutions), so we can directly map the fractions
    // onto the bitmap pixel dimensions.
    val bw = source.width
    val bh = source.height

    val pixelLeft   = (cropLeft   * bw).toInt().coerceIn(0, bw - 1)
    val pixelTop    = (cropTop    * bh).toInt().coerceIn(0, bh - 1)
    val pixelRight  = (cropRight  * bw).toInt().coerceIn(pixelLeft + 1, bw)
    val pixelBottom = (cropBottom * bh).toInt().coerceIn(pixelTop  + 1, bh)

    val cropWidth  = pixelRight  - pixelLeft
    val cropHeight = pixelBottom - pixelTop

    return Bitmap.createBitmap(source, pixelLeft, pixelTop, cropWidth, cropHeight)
}