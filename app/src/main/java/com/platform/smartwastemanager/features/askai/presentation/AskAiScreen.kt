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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.platform.smartwastemanager.features.askai.data.AiChat
import com.platform.smartwastemanager.features.askai.data.AiChatMessage
import com.platform.smartwastemanager.features.report.presentation.bindCameraToLifecycle
import dev.jeziellago.compose.markdowntext.MarkdownText
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.min

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class)
@Composable
fun AskAiScreen(
    viewModel: AskAiViewModel,
    currentUserId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(currentUserId) {
        // init() also performs the device-support check internally
        if (currentUserId.isNotBlank()) viewModel.init(currentUserId)
    }

    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = ContextCompat.getMainExecutor(context)
    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

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
        onDispose { cameraExecutor.shutdown() }
    }

    // Handle back button based on screen mode
    val handleBack: () -> Unit = {
        when (uiState.screenMode) {
            AskAiScreenMode.HISTORY_CHAT -> viewModel.backFromHistoryChat()
            AskAiScreenMode.HISTORY_LIST -> viewModel.backFromHistoryList()
            AskAiScreenMode.CAMERA       -> viewModel.reset()
            AskAiScreenMode.CROP         -> viewModel.reset() // back to camera/landing
            AskAiScreenMode.REVIEW       -> {
                // Go back to crop so user can re-crop
                viewModel.backToCrop()
            }
            AskAiScreenMode.CHAT         -> viewModel.reset()
            AskAiScreenMode.LANDING      -> onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(when (uiState.screenMode) {
                        AskAiScreenMode.LANDING      -> "Ask AI Assistant"
                        AskAiScreenMode.CAMERA       -> "Scan Item"
                        AskAiScreenMode.CROP         -> "Crop Image"
                        AskAiScreenMode.REVIEW       -> "Confirm Item"
                        AskAiScreenMode.CHAT         -> "AI Chat"
                        AskAiScreenMode.HISTORY_LIST -> "Chat History"
                        AskAiScreenMode.HISTORY_CHAT -> uiState.selectedHistoryChat?.wasteLabel ?: "Chat"
                    })
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
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
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (uiState.screenMode) {

                // ── Landing ──────────────────────────────────────────────────
                AskAiScreenMode.LANDING -> LandingStage(
                    uiState   = uiState,
                    onScanNew = { viewModel.showCamera() },
                    onViewHistory = { viewModel.showHistory() },
                    onStartDownload = { viewModel.startDownload() }
                )

                // ── Camera ───────────────────────────────────────────────────
                AskAiScreenMode.CAMERA -> CameraStage(
                    cameraPermission = cameraPermission,
                    lifecycleOwner = lifecycleOwner,
                    isCapturing = isCapturing,
                    onCaptureBound = { imageCaptureUseCase = it },
                    onCapture = {
                        val capture = imageCaptureUseCase ?: return@CameraStage
                        isCapturing = true
                        capture.takePicture(
                            cameraExecutor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                    val bitmap = imageProxy.toRotatedBitmap()
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
                    onGallery = { galleryLauncher.launch("image/*") }
                )

                // ── Crop ─────────────────────────────────────────────────────
                AskAiScreenMode.CROP -> {
                    val raw = uiState.capturedImage
                    if (raw != null) {
                        CropStage(
                            bitmap = raw,
                            onCropConfirmed = { cropped -> viewModel.onImageCropped(cropped) }
                        )
                    }
                }

                // ── Review ───────────────────────────────────────────────────
                AskAiScreenMode.REVIEW -> ReviewStage(
                    uiState = uiState,
                    onLabelChanged = { viewModel.onLabelChanged(it) },
                    onConfirm = { viewModel.onLabelConfirmed() }
                )

                // ── Chat ─────────────────────────────────────────────────────
                AskAiScreenMode.CHAT -> ChatStage(
                    uiState = uiState,
                    onAskAi = { viewModel.askAi(it) },
                    onLabelChanged = { viewModel.onLabelChanged(it) },
                    onReset = { viewModel.reset() }
                )

                // ── History List ──────────────────────────────────────────────
                AskAiScreenMode.HISTORY_LIST -> HistoryListStage(
                    chats = uiState.chatHistory,
                    onOpenChat = { viewModel.openHistoryChat(it) }
                )

                // ── History Chat ──────────────────────────────────────────────
                AskAiScreenMode.HISTORY_CHAT -> {
                    val chat = uiState.selectedHistoryChat
                    if (chat != null) {
                        HistoryChatStage(
                            chat = chat,
                            onContinueChat = { viewModel.continueHistoryChat(chat) }
                        )
                    }
                }
            }
        }
    }

    // Error dialog
    uiState.error?.let { errorMsg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("AI Error") },
            text = { Text(errorMsg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) { Text("OK") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Landing Stage — choose Scan or History
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LandingStage(
    uiState: AskAiUiState,
    onScanNew: () -> Unit,
    onViewHistory: () -> Unit,
    onStartDownload: () -> Unit
) {
    // ── Unsupported device ─────────────────────────────────────────────────
    if (!uiState.isDeviceSupported) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Feature Not Supported",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "The AI Assistant requires Android 10 or higher and at least 4 GB of RAM. " +
                            "This device does not meet the minimum requirements.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "AI Waste Assistant",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Scan a waste item to get AI-powered disposal advice, or browse your past conversations.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))

        // ── Model error ────────────────────────────────────────────────────
        if (uiState.modelError != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ErrorOutline, null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        uiState.modelError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Download consent card ──────────────────────────────────────────
        if (!uiState.isModelReady && !uiState.isModelInitializing &&
            !uiState.isModelDownloading && !uiState.downloadStarted
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Download, null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "AI Model Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "This feature uses a local AI model (~400 MB) stored on your device. " +
                        "The download is a one-time setup and works offline after that.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onStartDownload,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download AI Model (~400 MB)")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Download progress ──────────────────────────────────────────────
        if (uiState.isModelDownloading) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Downloading AI Model…",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${uiState.modelDownloadProgress}%",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { uiState.modelDownloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Please keep the app open. This is a one-time download.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Model initialising ─────────────────────────────────────────────
        if (uiState.isModelInitializing) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Loading AI model into memory…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Normal action cards (only shown when model is ready) ───────────
        if (uiState.isModelReady) {
            // Scan new item card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onScanNew),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.CameraAlt, null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "Scan New Item",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Take a photo or pick from gallery",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // History card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onViewHistory),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondary,
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.History, null,
                                tint = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "Chat History",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (uiState.chatHistory.isNotEmpty())
                                "${uiState.chatHistory.size} past conversation${if (uiState.chatHistory.size == 1) "" else "s"}"
                            else "No conversations yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Camera Stage
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun CameraStage(
    cameraPermission: com.google.accompanist.permissions.PermissionState,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    isCapturing: Boolean,
    onCaptureBound: (ImageCapture) -> Unit,
    onCapture: () -> Unit,
    onGallery: () -> Unit
) {
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
                        onCaptureBound = onCaptureBound
                    )
                    previewView
                }
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onGallery,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.PhotoLibrary, "Gallery", tint = Color.White)
                }
                LargeFloatingActionButton(onClick = onCapture, shape = CircleShape) {
                    if (isCapturing) CircularProgressIndicator()
                    else Icon(Icons.Default.Camera, "Capture")
                }
                Box(modifier = Modifier.size(48.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Crop Stage — touch-based crop rectangle
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Displays the raw captured image with a draggable crop rectangle.
 * Each corner handle is individually draggable. Pressing "Crop" applies the
 * selection and passes the cropped [Bitmap] to [onCropConfirmed].
 */
@Composable
private fun CropStage(
    bitmap: Bitmap,
    onCropConfirmed: (Bitmap) -> Unit
) {
    // Size of the canvas once laid out
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Crop handles — each stored as a fraction [0,1] of canvas width/height
    // so they survive recomposition across size changes.
    // Default: 10 % inset on every side.
    var tl by remember { mutableStateOf(Offset(0.1f, 0.1f)) }
    var br by remember { mutableStateOf(Offset(0.9f, 0.9f)) }

    val handleRadius = 24.dp
    val strokeColor  = Color.White
    val overlayColor = Color.Black.copy(alpha = 0.55f)

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Drag the corners to frame the waste item, then tap Crop.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { canvasSize = it }
        ) {
            // Draw the bitmap
            if (canvasSize != IntSize.Zero) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Captured image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Overlay + crop handles
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(canvasSize) {
                        val w = canvasSize.width.toFloat()
                        val h = canvasSize.height.toFloat()
                        if (w == 0f || h == 0f) return@pointerInput
                        val rPx = handleRadius.toPx()

                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val pos = change.position
                            // Determine which handle is being dragged by proximity to finger
                            val tlPx = Offset(tl.x * w, tl.y * h)
                            val brPx = Offset(br.x * w, br.y * h)
                            val trPx = Offset(br.x * w, tl.y * h)
                            val blPx = Offset(tl.x * w, br.y * h)

                            fun Offset.dst(o: Offset) = (x - o.x) * (x - o.x) + (y - o.y) * (y - o.y)
                            val rSq = rPx * rPx * 4

                            when {
                                pos.dst(tlPx) < rSq -> tl = Offset(
                                    ((tl.x * w + dragAmount.x) / w).coerceIn(0f, br.x - 0.05f),
                                    ((tl.y * h + dragAmount.y) / h).coerceIn(0f, br.y - 0.05f)
                                )
                                pos.dst(brPx) < rSq -> br = Offset(
                                    ((br.x * w + dragAmount.x) / w).coerceIn(tl.x + 0.05f, 1f),
                                    ((br.y * h + dragAmount.y) / h).coerceIn(tl.y + 0.05f, 1f)
                                )
                                pos.dst(trPx) < rSq -> {
                                    br = br.copy(x = ((br.x * w + dragAmount.x) / w).coerceIn(tl.x + 0.05f, 1f))
                                    tl = tl.copy(y = ((tl.y * h + dragAmount.y) / h).coerceIn(0f, br.y - 0.05f))
                                }
                                pos.dst(blPx) < rSq -> {
                                    tl = tl.copy(x = ((tl.x * w + dragAmount.x) / w).coerceIn(0f, br.x - 0.05f))
                                    br = br.copy(y = ((br.y * h + dragAmount.y) / h).coerceIn(tl.y + 0.05f, 1f))
                                }
                            }
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                val left   = tl.x * w
                val top    = tl.y * h
                val right  = br.x * w
                val bottom = br.y * h
                // Dark overlay outside crop rect (4 rectangles)
                drawRect(overlayColor, size = Size(w, top))
                drawRect(overlayColor, topLeft = Offset(0f, bottom), size = Size(w, h - bottom))
                drawRect(overlayColor, topLeft = Offset(0f, top), size = Size(left, bottom - top))
                drawRect(overlayColor, topLeft = Offset(right, top), size = Size(w - right, bottom - top))

                // Crop rect border
                drawRect(strokeColor, topLeft = Offset(left, top), size = Size(right - left, bottom - top), style = Stroke(width = 3f))

                // Rule-of-thirds grid lines
                val thirdW = (right - left) / 3f
                val thirdH = (bottom - top) / 3f
                for (i in 1..2) {
                    drawLine(strokeColor.copy(alpha = 0.4f), Offset(left + i * thirdW, top), Offset(left + i * thirdW, bottom), strokeWidth = 1f)
                    drawLine(strokeColor.copy(alpha = 0.4f), Offset(left, top + i * thirdH), Offset(right, top + i * thirdH), strokeWidth = 1f)
                }

                // Corner handles
                listOf(
                    Offset(left, top), Offset(right, top),
                    Offset(left, bottom), Offset(right, bottom)
                ).forEach { handle ->
                    drawCircle(strokeColor, radius = 12f, center = handle)
                    drawCircle(Color(0xFF2196F3), radius = 8f, center = handle)
                }
            }
        }

        Button(
            onClick = {
                if (canvasSize == IntSize.Zero) return@Button
                val w = canvasSize.width.toFloat()
                val h = canvasSize.height.toFloat()

                // The image is rendered Fit inside the canvas; compute the actual
                // image rect within the canvas so we can map crop coords to pixels.
                val bmpW = bitmap.width.toFloat()
                val bmpH = bitmap.height.toFloat()
                val scale = min(w / bmpW, h / bmpH)
                val renderedW = bmpW * scale
                val renderedH = bmpH * scale
                val offsetX = (w - renderedW) / 2f
                val offsetY = (h - renderedH) / 2f

                // Convert canvas fraction coords → bitmap pixel coords
                val l = ((tl.x * w - offsetX) / scale).toInt().coerceIn(0, bitmap.width - 1)
                val t = ((tl.y * h - offsetY) / scale).toInt().coerceIn(0, bitmap.height - 1)
                val r = ((br.x * w - offsetX) / scale).toInt().coerceIn(l + 1, bitmap.width)
                val b = ((br.y * h - offsetY) / scale).toInt().coerceIn(t + 1, bitmap.height)

                val cropped = Bitmap.createBitmap(bitmap, l, t, r - l, b - t)
                onCropConfirmed(cropped)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Crop & Analyse")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Review Stage — shows ML results + editable label
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReviewStage(
    uiState: AskAiUiState,
    onLabelChanged: (String) -> Unit,
    onConfirm: () -> Unit
) {
    val croppedOrRaw = uiState.croppedImage ?: uiState.capturedImage

    // Local draft — starts empty, filled once classification finishes.
    // We deliberately do NOT re-sync after the user edits, so typing is stable.
    var labelDraft by remember { mutableStateOf("") }
    var userHasEdited by remember { mutableStateOf(false) }

    // One-shot: when classification completes and produces a label, fill the draft
    // (only if the user hasn't already typed something themselves).
    LaunchedEffect(uiState.identifiedLabel) {
        if (uiState.identifiedLabel.isNotBlank() && !userHasEdited) {
            labelDraft = uiState.identifiedLabel
        }
    }

        val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Cropped image preview ────────────────────────────────────────────
        if (croppedOrRaw != null) {
            androidx.compose.foundation.Image(
                bitmap = croppedOrRaw.asImageBitmap(),
                contentDescription = "Cropped waste item",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Classification results card ──────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome, null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "AI Image Classification",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "MobileNet analysed the cropped image and detected the following:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (uiState.isClassifying) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Analysing image…", style = MaterialTheme.typography.bodySmall)
                    }
                } else if (uiState.classificationResults.isEmpty()) {
                    Text(
                        "No results detected — please type the item name below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                } else {
                    uiState.classificationResults.take(5).forEachIndexed { idx, (label, confidence) ->
                        val isTop = idx == 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    labelDraft = label
                                    userHasEdited = true
                                    onLabelChanged(label)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = if (isTop) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "${idx + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isTop) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                label,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isTop) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${(confidence * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Tap a result to use it as the item label.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Editable label field ─────────────────────────────────────────────
        OutlinedTextField(
            value = labelDraft,
            onValueChange = { newText ->
                labelDraft = newText
                userHasEdited = true
                onLabelChanged(newText)
            },
            label = { Text("Waste Item Name") },
            placeholder = { Text("e.g. Plastic Bottle") },
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = {
                Text("Edit the label if needed — the AI assistant uses this to guide its response.")
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Confirm button ───────────────────────────────────────────────────
        Button(
            onClick = {
                // Ensure ViewModel has the latest draft before confirming
                if (labelDraft.isNotBlank()) onLabelChanged(labelDraft)
                onConfirm()
            },
            enabled = !uiState.isClassifying && labelDraft.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Confirm & Start AI Chat")
        }

        // Extra bottom padding so the button clears the nav bar
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chat Stage
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatStage(
    uiState: AskAiUiState,
    onAskAi: (String) -> Unit,
    onLabelChanged: (String) -> Unit,
    onReset: () -> Unit
) {
    val chat = uiState.currentChat
    val isClosed = chat?.isClosed ?: false
    val promptsLeft = if (chat != null) AiChat.MAX_PROMPTS - chat.promptCount else AiChat.MAX_PROMPTS
    var userPrompt by remember { mutableStateOf("") }
    var showEditLabelDialog by remember { mutableStateOf(false) }
    // Collapse the input panel while streaming so the response is fully visible
    var inputPanelExpanded by remember { mutableStateOf(true) }
    // Auto-collapse when streaming starts, re-expand when done
    LaunchedEffect(uiState.isStreaming) {
        if (uiState.isStreaming) inputPanelExpanded = false
    }

    val shortcuts = listOf(
        "How do I recycle this?",
        "Is this biodegradable?",
        "How should I dispose of this safely?",
        "Can I reuse this?"
    )

    val firestoreMessages = chat?.messages ?: emptyList()
    val hasFallbackResponse = firestoreMessages.isEmpty() && uiState.latestAiResponse.isNotBlank()

    val listState = rememberLazyListState()
    // Auto-scroll: when streaming, scroll to bottom on every new token
    LaunchedEffect(firestoreMessages.size, uiState.streamingResponse, uiState.latestAiResponse) {
        val itemCount = firestoreMessages.size +
                (if (uiState.isStreaming) 1 else 0) +
                (if (hasFallbackResponse) 1 else 0)
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Image + Label Header ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (uiState.capturedImage != null) {
                AsyncImage(
                    model = uiState.capturedImage,
                    contentDescription = "Captured Item",
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else if (chat?.imageUrl?.isNotBlank() == true) {
                AsyncImage(
                    model = chat.imageUrl,
                    contentDescription = "Item Image",
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Identified as:", style = MaterialTheme.typography.labelSmall)
                if (uiState.isClassifying) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    Text(
                        uiState.identifiedLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (uiState.isCreatingChat) {
                    Text(
                        "Saving session…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = { showEditLabelDialog = true }) {
                Icon(Icons.Default.Edit, "Edit Label")
            }
        }

        // ── Prompt limit / model-not-ready banner ─────────────────────────────
        when {
            isClosed -> Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lock, null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Chat closed — 5 prompt limit reached. View history only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            !uiState.isModelReady -> Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp), strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Preparing AI model…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            chat != null -> Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            ) {
                Text(
                    "$promptsLeft prompt${if (promptsLeft == 1) "" else "s"} remaining",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        HorizontalDivider()

        // ── Message thread ──────────────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (firestoreMessages.isEmpty() && !uiState.isCreatingChat &&
                !uiState.isLoading && !uiState.isStreaming && !hasFallbackResponse
            ) {
                item {
                    Text(
                        "Ask anything about this waste item below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            // Persisted messages
            items(firestoreMessages) { msg -> MessageBubble(msg) }

            // Fallback: last response when Firestore not available
            if (hasFallbackResponse) {
                item {
                    AiBubble(text = uiState.latestAiResponse, isStreaming = false)
                }
            }

            // ── Live typewriter streaming bubble ─────────────────────────
            if (uiState.isStreaming) {
                item {
                    AiBubble(
                        text = uiState.streamingResponse,
                        isStreaming = true
                    )
                }
            }
        }

        // ── Collapsible input panel ────────────────────────────────────────
        if (!isClosed) {
            // Toggle handle — always visible so the user can expand/collapse
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { inputPanelExpanded = !inputPanelExpanded }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (inputPanelExpanded) "Hide input" else "Type a message…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = if (inputPanelExpanded) Icons.Default.KeyboardArrowDown
                                      else Icons.Default.KeyboardArrowUp,
                        contentDescription = if (inputPanelExpanded) "Collapse input" else "Expand input",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = inputPanelExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    // ── Quick prompts ────────────────────────────────────────
                    if (!uiState.isStreaming && uiState.isModelReady) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            shortcuts.forEach { shortcut ->
                                SuggestionChip(
                                    onClick = { onAskAi(shortcut) },
                                    label = { Text(shortcut, style = MaterialTheme.typography.labelSmall) },
                                    enabled = !uiState.isLoading && !uiState.isClassifying
                                )
                            }
                        }
                    }

                    // ── Input row ────────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = userPrompt,
                            onValueChange = { userPrompt = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Ask about this item…") },
                            singleLine = true,
                            enabled = !uiState.isLoading && !uiState.isClassifying &&
                                      !uiState.isStreaming && uiState.isModelReady
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (userPrompt.isNotBlank()) {
                                    onAskAi(userPrompt)
                                    userPrompt = ""
                                }
                            },
                            enabled = userPrompt.isNotBlank() && !uiState.isLoading &&
                                      !uiState.isClassifying && !uiState.isStreaming &&
                                      uiState.isModelReady
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send, "Send",
                                tint = if (userPrompt.isNotBlank() && uiState.isModelReady)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    }
                }
            }
        }

        // ── Reset button ──────────────────────────────────────────────────
        // Removed: "Scan New Item" button cluttered the bottom of the screen.
        // Users can scan a new item by pressing the back arrow in the top bar.
    }

    // Edit label dialog
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
                    onLabelChanged(tempLabel)
                    showEditLabelDialog = false
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showEditLabelDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * An AI message bubble.  When [isStreaming] is true a blinking cursor is appended
 * to produce the live typewriter effect as tokens arrive.
 */
@Composable
private fun AiBubble(text: String, isStreaming: Boolean) {
    // Blinking cursor animation — only runs during streaming to save battery
    val cursorAlpha by if (isStreaming) {
        val infiniteTransition = rememberInfiniteTransition(label = "cursor")
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue  = 0f,
            animationSpec = infiniteRepeatable(
                animation  = tween(durationMillis = 530, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "cursorAlpha"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            Icons.Default.AutoAwesome, "AI",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp).padding(top = 4.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 4.dp, topEnd = 16.dp,
                bottomStart = 16.dp, bottomEnd = 16.dp
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                MarkdownText(
                    markdown = text,
                    style = MaterialTheme.typography.bodyMedium
                )
                // Blinking cursor shown only while streaming
                if (isStreaming) {
                    Text(
                        "▌",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = cursorAlpha)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: AiChatMessage) {
    val isUser = msg.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Icon(Icons.Default.AutoAwesome, "AI",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp).padding(top = 4.dp))
            Spacer(modifier = Modifier.width(6.dp))
        }
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = if (isUser) 16.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            if (isUser) {
                Text(
                    msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            } else {
                MarkdownText(
                    markdown = msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// History List Stage
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HistoryListStage(
    chats: List<AiChat>,
    onOpenChat: (AiChat) -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    if (chats.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.History, null, modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))
            Text("No chat history yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Scan a waste item to start your first AI conversation.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("📋 Your AI Chat History",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Tap a session to view or continue the conversation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
        }
        items(chats, key = { it.chatId }) { chat ->
            ChatHistoryCard(chat = chat, dateFormatter = dateFormatter, onClick = { onOpenChat(chat) })
        }
    }
}

@Composable
private fun ChatHistoryCard(
    chat: AiChat,
    dateFormatter: SimpleDateFormat,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (chat.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = chat.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
            } else {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AutoAwesome, null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(chat.wasteLabel, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${chat.messages.size} message${if (chat.messages.size == 1) "" else "s"} · ${chat.promptCount}/${AiChat.MAX_PROMPTS} prompts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(dateFormatter.format(chat.createdAt.toDate()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = if (chat.isClosed) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    if (chat.isClosed) "Closed" else "Open",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (chat.isClosed) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// History Chat Stage (view a past conversation)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HistoryChatStage(
    chat: AiChat,
    onContinueChat: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (chat.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = chat.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(chat.wasteLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${chat.promptCount}/${AiChat.MAX_PROMPTS} prompts used",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (chat.isClosed) {
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null,
                        tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("This chat is closed — 5 prompt limit reached.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        } else {
            Surface(modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${AiChat.MAX_PROMPTS - chat.promptCount} prompts remaining",
                        style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = onContinueChat) {
                        Icon(Icons.Default.Chat, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Continue Chat")
                    }
                }
            }
        }

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (chat.messages.isEmpty()) {
                item {
                    Text("No messages in this chat.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp))
                }
            }
            items(chat.messages) { msg -> MessageBubble(msg) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bitmap helper
// ─────────────────────────────────────────────────────────────────────────────

private fun ImageProxy.toRotatedBitmap(): Bitmap {
    val bmp = toBitmap()
    if (imageInfo.rotationDegrees == 0) return bmp
    val matrix = android.graphics.Matrix()
    matrix.postRotate(imageInfo.rotationDegrees.toFloat())
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
}
