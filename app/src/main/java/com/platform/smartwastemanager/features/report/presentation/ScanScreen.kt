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
import androidx.compose.material3.Surface
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
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.Executors

/**
 * Camera preview screen.
 *
 * - Camera binding is delegated to CameraHelper.kt (no ListenableFuture in this file).
 * - Navigation and Compose state in onCaptureSuccess are dispatched to the main thread
 *   via mainExecutor to prevent the "setCurrentState must be on main thread" crash.
 * - The top bar uses a fixed 56.dp Surface so it never clips with the preview.
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

    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing         by remember { mutableStateOf(false) }

    // Background thread — bitmap work happens here
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    // Main thread — all Compose state + navigation must run here
    val mainExecutor   = remember { ContextCompat.getMainExecutor(context) }

    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown() } }

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ---- Top bar — fixed height Surface prevents clipping ----
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint               = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text  = "Scan Waste",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        if (!cameraPermission.status.isGranted) {
            // ---- Permission not yet granted ----
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
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
                        bindCameraToLifecycle(
                            context        = ctx,
                            lifecycleOwner = lifecycleOwner,
                            previewView    = previewView,
                            onCaptureBound = { capture -> imageCaptureUseCase = capture }
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
                        .padding(top = 12.dp)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            // ---- Capture button bar ----
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color    = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Button(
                    onClick = {
                        val capture = imageCaptureUseCase ?: return@Button
                        isCapturing = true

                        capture.takePicture(
                            cameraExecutor, // ← callback fires on background thread
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                    // ✅ Bitmap work — safe on background thread
                                    val bitmap: Bitmap = imageProxy.toBitmap()
                                    imageProxy.close()
                                    viewModel.classifyImage(bitmap)

                                    // ✅ Compose state + navigation — must be on main thread
                                    mainExecutor.execute {
                                        isCapturing = false
                                        onNavigateToForm()
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    Log.e("ScanScreen", "Capture failed", exception)
                                    mainExecutor.execute { isCapturing = false }
                                }
                            }
                        )
                    },
                    enabled  = !isCapturing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp)
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color    = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector        = Icons.Default.Camera,
                            contentDescription = null,
                            modifier           = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Capture & Analyse")
                    }
                }
            }
        }
    }
}