package com.platform.smartwastemanager.features.report.presentation

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
 * Flow:
 *   1. User sees live camera preview with a 70% target box overlay.
 *   2. User taps Capture (or picks from Gallery).
 *   3. The bitmap is stored in [pendingCropBitmap] state.
 *   4. CropScreen is shown on top — user adjusts the crop rectangle.
 *   5. On "Use Crop": classifyImage() is called and we navigate to the form.
 *   6. On "Retake": pendingCropBitmap is cleared, camera preview returns.
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

    // When this is non-null, CropScreen is shown instead of the camera preview.
    // Set after a capture or gallery pick; cleared when the user taps "Retake".
    var pendingCropBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor   = remember { ContextCompat.getMainExecutor(context) }

    // ---- Gallery picker ----
    // Loads the bitmap and routes it through CropScreen instead of
    // calling classifyImage() directly.
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = if (Build.VERSION.SDK_INT < 28) {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                } else {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                }
                // Store in state — this triggers CropScreen to appear
                pendingCropBitmap = bitmap
            } catch (e: Exception) {
                Log.e("ScanScreen", "Gallery import failed", e)
            }
        }
    }

    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown() } }

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
    }

    // ================================================================
    // CROP SCREEN OVERLAY
    // When a bitmap is pending crop, show CropScreen on top of everything.
    // This uses a simple conditional rather than navigation so the camera
    // preview is still alive underneath (no re-binding on retake).
    // ================================================================
    val cropBitmap = pendingCropBitmap
    if (cropBitmap != null) {
        CropScreen(
            bitmap          = cropBitmap,
            viewModel       = viewModel,
            onCropConfirmed = {
                // CropScreen called viewModel.classifyImage() already — just navigate
                pendingCropBitmap = null
                onNavigateToForm()
            },
            onRetake = {
                // Clear the pending bitmap — camera preview reappears
                pendingCropBitmap = null
                isCapturing = false
            }
        )
        // Return early so the camera UI is not drawn on top of CropScreen
        return
    }

    // ================================================================
    // CAMERA PREVIEW + CONTROLS
    // ================================================================
    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
    ) {

        if (!cameraPermission.status.isGranted) {
            // ---- Permission not yet granted ----
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Text(
                    text  = "Camera permission is required to scan waste.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { cameraPermission.launchPermissionRequest() }) {
                    Text("Grant Permission")
                }
            }
        } else {

            // ---- Live camera preview (Full Screen) ----
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

            // ---- Target Box Overlay (70% center area) ----
            // This matches the default crop rect shown in CropScreen.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth  = size.width
                val canvasHeight = size.height
                val boxWidth     = canvasWidth  * 0.7f
                val boxHeight    = canvasHeight * 0.7f
                val left         = (canvasWidth  - boxWidth)  / 2
                val top          = (canvasHeight - boxHeight) / 2

                val cornerLength = 40.dp.toPx()
                val strokeWidth  = 3.dp.toPx()
                val color        = Color.White.copy(alpha = 0.7f)

                // Corner bracket lines
                drawLine(color, Offset(left, top), Offset(left + cornerLength, top), strokeWidth)
                drawLine(color, Offset(left, top), Offset(left, top + cornerLength), strokeWidth)
                drawLine(color, Offset(left + boxWidth, top), Offset(left + boxWidth - cornerLength, top), strokeWidth)
                drawLine(color, Offset(left + boxWidth, top), Offset(left + boxWidth, top + cornerLength), strokeWidth)
                drawLine(color, Offset(left, top + boxHeight), Offset(left + cornerLength, top + boxHeight), strokeWidth)
                drawLine(color, Offset(left, top + boxHeight), Offset(left, top + boxHeight - cornerLength), strokeWidth)
                drawLine(color, Offset(left + boxWidth, top + boxHeight), Offset(left + boxWidth - cornerLength, top + boxHeight), strokeWidth)
                drawLine(color, Offset(left + boxWidth, top + boxHeight), Offset(left + boxWidth, top + boxHeight - cornerLength), strokeWidth)

                // Dim outside the box
                drawRect(Color.Black.copy(alpha = 0.3f), Offset.Zero, Size(canvasWidth, top))
                drawRect(Color.Black.copy(alpha = 0.3f), Offset(0f, top + boxHeight), Size(canvasWidth, canvasHeight - (top + boxHeight)))
                drawRect(Color.Black.copy(alpha = 0.3f), Offset(0f, top), Size(left, boxHeight))
                drawRect(Color.Black.copy(alpha = 0.3f), Offset(left + boxWidth, top), Size(canvasWidth - (left + boxWidth), boxHeight))
            }

            // ---- Back Button (Top Left) ----
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 48.dp, start = 16.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f)
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint               = Color.White
                    )
                }
            }

            // ---- Hint text ----
            Text(
                text     = "Place item inside the frame",
                style    = MaterialTheme.typography.bodyMedium,
                color    = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp)
                    .background(Color.Black.copy(alpha = 0.45f), shape = RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )

            // ---- Bottom Controls Row ----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp, start = 32.dp, end = 32.dp)
            ) {
                // Gallery Button (Bottom Left)
                Surface(
                    modifier = Modifier.align(Alignment.CenterStart),
                    shape    = CircleShape,
                    color    = Color.Black.copy(alpha = 0.45f)
                ) {
                    IconButton(
                        onClick  = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.PhotoLibrary,
                            contentDescription = "Select from gallery",
                            tint               = Color.White,
                            modifier           = Modifier.size(28.dp)
                        )
                    }
                }

                // Capture Button (Center)
                LargeFloatingActionButton(
                    onClick = {
                        val capture = imageCaptureUseCase ?: return@LargeFloatingActionButton
                        isCapturing = true

                        capture.takePicture(
                            cameraExecutor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                    val bitmap: Bitmap = imageProxy.toBitmap()
                                    imageProxy.close()

                                    mainExecutor.execute {
                                        // Store bitmap — CropScreen will appear
                                        pendingCropBitmap = bitmap
                                        isCapturing = false
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    Log.e("ScanScreen", "Capture failed", exception)
                                    mainExecutor.execute { isCapturing = false }
                                }
                            }
                        )
                    },
                    modifier       = Modifier
                        .align(Alignment.Center)
                        .size(80.dp),
                    shape          = CircleShape,
                    containerColor = Color.White,
                    contentColor   = MaterialTheme.colorScheme.primary
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    } else {
                        Icon(
                            imageVector        = Icons.Default.Camera,
                            contentDescription = "Capture",
                            modifier           = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }
    }
}