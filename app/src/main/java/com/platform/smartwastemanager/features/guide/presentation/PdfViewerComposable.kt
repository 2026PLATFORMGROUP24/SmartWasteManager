package com.platform.smartwastemanager.features.guide.presentation

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest

private const val MAX_RENDER_DIMENSION = 2048
private const val MIN_ZOOM_SCALE = 1f
private const val MAX_ZOOM_SCALE = 3f
private const val ZOOM_STEP = 0.25f

private fun stablePdfCacheName(url: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
    val hex = digest.joinToString(separator = "") { "%02x".format(it) }
    return "guide_pdf_$hex.pdf"
}

/**
 * Native PDF viewer using Android's PdfRenderer API.
 * Downloads PDF from Firebase Storage URL and displays all pages in a scrollable column.
 * Supports zoom controls.
 */
@Composable
fun PdfViewer(
    pdfUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pageBitmaps by remember(pdfUrl) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var pageCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var zoomScale by remember { mutableFloatStateOf(MIN_ZOOM_SCALE) }

    DisposableEffect(pdfUrl) {
        onDispose {
            pageBitmaps.forEach(Bitmap::recycle)
            pageBitmaps = emptyList()
        }
    }

    LaunchedEffect(pdfUrl) {
        isLoading = true
        errorMessage = null
        pageCount = 0
        zoomScale = MIN_ZOOM_SCALE
        pageBitmaps.forEach(Bitmap::recycle)
        pageBitmaps = emptyList()

        runCatching {
            withContext(Dispatchers.IO) {
                val parsedUrl = URL(pdfUrl)
                require(parsedUrl.protocol.equals("https", ignoreCase = true)) {
                    "Only HTTPS PDF URLs are supported."
                }

                val targetFile = File(context.cacheDir, stablePdfCacheName(pdfUrl))
                if (!targetFile.exists()) {
                    parsedUrl.openStream().use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                ParcelFileDescriptor.open(targetFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        List(renderer.pageCount) { index ->
                            renderer.openPage(index).use { page ->
                                val pageWidth = page.width.toFloat()
                                val pageHeight = page.height.toFloat()
                                val renderScale = minOf(
                                    MAX_RENDER_DIMENSION / pageWidth,
                                    MAX_RENDER_DIMENSION / pageHeight,
                                    1f
                                )
                                val bitmap = Bitmap.createBitmap(
                                    (pageWidth * renderScale).toInt().coerceAtLeast(1),
                                    (pageHeight * renderScale).toInt().coerceAtLeast(1),
                                    Bitmap.Config.ARGB_8888
                                )
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bitmap
                            }
                        }
                    }
                }
            }
        }.onSuccess { renderedPages ->
            pageBitmaps = renderedPages
            pageCount = renderedPages.size
            isLoading = false
            if (renderedPages.isEmpty()) {
                errorMessage = "PDF has no pages."
            }
        }.onFailure {
            isLoading = false
            errorMessage = "Could not load PDF."
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(onClick = {
                zoomScale = (zoomScale - ZOOM_STEP).coerceAtLeast(MIN_ZOOM_SCALE)
            }) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out")
            }

            Slider(
                value = zoomScale,
                onValueChange = { zoomScale = it },
                valueRange = MIN_ZOOM_SCALE..MAX_ZOOM_SCALE,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = {
                zoomScale = (zoomScale + ZOOM_STEP).coerceAtMost(MAX_ZOOM_SCALE)
            }) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in")
            }
        }

        Text(
            text = "Zoom ${(zoomScale * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> CircularProgressIndicator()
                errorMessage != null -> Text(
                    text = errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(12.dp)
                )
                pageBitmaps.isEmpty() -> Text("No PDF preview available.")
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pageBitmaps.forEachIndexed { index, bitmap ->
                            Text(
                                text = "Page ${index + 1} / $pageCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "PDF page ${index + 1}",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            scaleX = zoomScale
                                            scaleY = zoomScale
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
