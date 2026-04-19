package com.platform.smartwastemanager.features.guide.presentation

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

@Composable
fun PdfViewer(
    pdfUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pdfFile by remember(pdfUrl) { mutableStateOf<File?>(null) }
    var renderedPage by remember { mutableStateOf<Bitmap?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var scale by remember { mutableFloatStateOf(1f) }
    var translationX by remember { mutableFloatStateOf(0f) }
    var translationY by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        onDispose {
            renderedPage?.recycle()
        }
    }

    LaunchedEffect(pdfUrl, refreshTick) {
        isLoading = true
        errorMessage = null
        pageCount = 0
        currentPage = 0
        pdfFile = null
        scale = 1f
        translationX = 0f
        translationY = 0f
        renderedPage?.recycle()
        renderedPage = null

        runCatching {
            withContext(Dispatchers.IO) {
                val targetFile = File(context.cacheDir, "guide_pdf_${pdfUrl.hashCode()}.pdf")
                URL(pdfUrl).openStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                targetFile
            }
        }.onSuccess { file ->
            pdfFile = file
            runCatching {
                withContext(Dispatchers.IO) {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            renderer.pageCount
                        }
                    }
                }
            }.onSuccess { pages ->
                pageCount = pages
                isLoading = false
                if (pages == 0) {
                    errorMessage = "PDF has no pages."
                }
            }.onFailure {
                isLoading = false
                errorMessage = "Could not read PDF."
            }
        }.onFailure {
            isLoading = false
            errorMessage = "Could not load PDF."
        }
    }

    LaunchedEffect(pdfFile, currentPage) {
        val file = pdfFile ?: return@LaunchedEffect
        if (pageCount <= 0 || currentPage !in 0 until pageCount) return@LaunchedEffect

        isLoading = true
        runCatching {
            withContext(Dispatchers.IO) {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        renderer.openPage(currentPage).use { page ->
                            val bitmap = Bitmap.createBitmap(
                                page.width.coerceAtMost(2048),
                                page.height.coerceAtMost(2048),
                                Bitmap.Config.ARGB_8888
                            )
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap
                        }
                    }
                }
            }
        }.onSuccess { bitmap ->
            renderedPage?.recycle()
            renderedPage = bitmap
            isLoading = false
            errorMessage = null
            scale = 1f
            translationX = 0f
            translationY = 0f
        }.onFailure {
            isLoading = false
            errorMessage = "Could not render PDF page."
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
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
                renderedPage != null -> Image(
                    bitmap = renderedPage!!.asImageBitmap(),
                    contentDescription = "PDF page ${currentPage + 1}",
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(currentPage, renderedPage) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = (scale * zoom).coerceIn(1f, 4f)
                                scale = nextScale
                                if (nextScale == 1f) {
                                    translationX = 0f
                                    translationY = 0f
                                } else {
                                    translationX += pan.x
                                    translationY += pan.y
                                }
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.translationX = translationX
                            this.translationY = translationY
                        }
                )
                else -> Text("No PDF preview available.")
            }

            SuggestionChip(
                onClick = { refreshTick++ },
                label = { Text("Refresh") },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )
        }

        if (pageCount > 0 && errorMessage == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { currentPage-- },
                    enabled = currentPage > 0
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Previous page")
                }
                Text("Page ${currentPage + 1} / $pageCount")
                IconButton(
                    onClick = { currentPage++ },
                    enabled = currentPage < pageCount - 1
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Next page")
                }
            }
        }
    }
}
