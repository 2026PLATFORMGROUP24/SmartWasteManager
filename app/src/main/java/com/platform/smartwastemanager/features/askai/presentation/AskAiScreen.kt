package com.platform.smartwastemanager.features.askai.presentation

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.platform.smartwastemanager.features.report.presentation.bindCameraToLifecycle
import dev.jeziellago.compose.markdowntext.MarkdownText
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class
)
@Composable
fun AskAiScreen(
    viewModel: AskAiViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = ContextCompat.getMainExecutor(context)

    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var showEditLabelDialog by remember { mutableStateOf(false) }
    var userPrompt by remember { mutableStateOf("") }

    val shortcuts = listOf(
        "How do I recycle this?",
        "Is this biodegradable?",
        "How should I dispose of this safely?",
        "Can I reuse this?"
    )

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
                viewModel.onImageCaptured(bitmap)
            } catch (e: Exception) {
                Log.e("AskAiScreen", "Gallery import failed", e)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ask AI Assistant") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.capturedImage == null) {
                // ---- Camera Preview Stage ----
                Box(modifier = Modifier.fillMaxSize()) {
                    if (!cameraPermission.status.isGranted) {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Camera permission is required.")
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { cameraPermission.launchPermissionRequest() }) {
                                Text("Grant Permission")
                            }
                        }
                    } else {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                bindCameraToLifecycle(
                                    context = ctx,
                                    lifecycleOwner = lifecycleOwner,
                                    previewView = previewView,
                                    onCaptureBound = { capture -> imageCaptureUseCase = capture }
                                )
                                previewView
                            }
                        )

                        // Bottom Controls
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(bottom = 32.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { galleryLauncher.launch("image/*") },
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, "Gallery", tint = Color.White)
                            }

                            LargeFloatingActionButton(
                                onClick = {
                                    val capture = imageCaptureUseCase ?: return@LargeFloatingActionButton
                                    isCapturing = true
                                    capture.takePicture(
                                        cameraExecutor,
                                        object : ImageCapture.OnImageCapturedCallback() {
                                            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                                val bitmap = imageProxy.toBitmap()
                                                imageProxy.close()
                                                mainExecutor.execute {
                                                    viewModel.onImageCaptured(bitmap)
                                                    isCapturing = false
                                                }
                                            }
                                            override fun onError(exception: ImageCaptureException) {
                                                mainExecutor.execute { isCapturing = false }
                                            }
                                        }
                                    )
                                },
                                shape = CircleShape
                            ) {
                                if (isCapturing) {
                                    CircularProgressIndicator()
                                } else {
                                    Icon(Icons.Default.Camera, "Capture")
                                }
                            }

                            // Spacer to balance the gallery button
                            Box(modifier = Modifier.size(48.dp))
                        }
                    }
                }
            } else {
                // ---- Interaction Stage ----
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Image Preview
                    AsyncImage(
                        model = uiState.capturedImage,
                        contentDescription = "Captured Item",
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Identified Label
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Identified as:",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                if (uiState.isClassifying) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                } else {
                                    Text(
                                        uiState.identifiedLabel,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            IconButton(onClick = { showEditLabelDialog = true }) {
                                Icon(Icons.Default.Edit, "Edit Label")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Prompt Input
                    OutlinedTextField(
                        value = userPrompt,
                        onValueChange = { userPrompt = it },
                        label = { Text("Ask anything about this item") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(
                                onClick = { 
                                    viewModel.askAi(userPrompt)
                                    userPrompt = ""
                                },
                                enabled = userPrompt.isNotBlank() && !uiState.isLoading
                            ) {
                                Icon(Icons.Default.AutoAwesome, "Ask")
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Shortcut Chips
                    Text(
                        "Quick Prompts:",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        shortcuts.forEach { shortcut ->
                            SuggestionChip(
                                onClick = { viewModel.askAi(shortcut) },
                                label = { Text(shortcut) },
                                enabled = !uiState.isLoading
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // AI Response
                    if (uiState.isLoading) {
                        CircularProgressIndicator()
                        Text("Consulting AI...", modifier = Modifier.padding(top = 8.dp))
                    } else if (uiState.aiResponse.isNotBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.AutoAwesome, 
                                        "AI", 
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "AI Suggestions",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                                MarkdownText(
                                    markdown = uiState.aiResponse,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = { viewModel.reset() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Text("Reset & Scan New Item")
                    }
                }
            }
        }
    }

    // Edit Label Dialog
    if (showEditLabelDialog) {
        var tempLabel by remember { mutableStateOf(uiState.identifiedLabel) }
        AlertDialog(
            onDismissRequest = { showEditLabelDialog = false },
            title = { Text("Correct Label") },
            text = {
                OutlinedTextField(
                    value = tempLabel,
                    onValueChange = { tempLabel = it },
                    label = { Text("Waste Item Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onLabelChanged(tempLabel)
                    showEditLabelDialog = false
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditLabelDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Error Snackbar
    uiState.error?.let { errorMsg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("AI Error") },
            text = { Text(errorMsg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK")
                }
            }
        )
    }
}

/**
 * Extension to convert ImageProxy to Bitmap.
 * Reusing logic to avoid unresolved reference.
 */
private fun ImageProxy.toBitmap(): Bitmap {
    val buffer = planes[0].buffer
    buffer.rewind()
    val bytes = ByteArray(buffer.capacity())
    buffer.get(bytes)
    val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    val matrix = android.graphics.Matrix()
    matrix.postRotate(imageInfo.rotationDegrees.toFloat())
    
    return Bitmap.createBitmap(
        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
    )
}
