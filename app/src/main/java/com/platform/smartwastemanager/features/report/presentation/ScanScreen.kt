package com.platform.smartwastemanager.features.report.presentation

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.Executors

/**
 * Camera preview screen.
 *
 * All ProcessCameraProvider / ListenableFuture calls are delegated to
 * [bindCameraToLifecycle] in CameraHelper.kt — a plain Kotlin file with
 * no Compose imports. This prevents the "Cannot access ListenableFuture"
 * classpath conflict that occurs when those calls appear alongside Compose code.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanScreen(
    viewModel: ReportViewModel,
    onNavigateToForm: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ---- Camera permission ----
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    // ImageCapture is set by CameraHelper once the camera is bound
    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing         by remember { mutableStateOf(false) }

    // Executor for takePicture callbacks (runs off the main thread)
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown() } }

    // Ask for permission on first composition if not already granted
    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ---- Top bar ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text  = "Scan Waste",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        if (!cameraPermission.status.isGranted) {
            // ---- Permission not granted ----
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text  = "Camera permission is required to scan waste.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { cameraPermission.launchPermissionRequest() }) {
                        Text("Grant Permission")
                    }
                }
            }
        } else {
            // ---- Live camera preview ----
            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)

                        // Delegate ALL ListenableFuture usage to CameraHelper.kt.
                        // No ListenableFuture type appears in this file at all.
                        bindCameraToLifecycle(
                            context        = ctx,
                            lifecycleOwner = lifecycleOwner,
                            previewView    = previewView,
                            onCaptureBound = { capture ->
                                imageCaptureUseCase = capture
                            }
                        )

                        previewView
                    }
                )

                // Hint overlay
                Text(
                    text     = "Point camera at waste item",
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            // ---- Capture button ----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        val capture = imageCaptureUseCase ?: return@Button
                        isCapturing = true
                        capture.takePicture(
                            cameraExecutor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                    val bitmap: Bitmap = imageProxy.toBitmap()
                                    imageProxy.close()
                                    viewModel.classifyImage(bitmap)
                                    isCapturing = false
                                    onNavigateToForm()
                                }
                                override fun onError(exception: ImageCaptureException) {
                                    Log.e("ScanScreen", "Capture failed", exception)
                                    isCapturing = false
                                }
                            }
                        )
                    },
                    enabled  = !isCapturing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color    = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector      = Icons.Default.Camera,
                            contentDescription = null,
                            modifier         = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Capture & Analyse")
                    }
                }
            }
        }
    }
}