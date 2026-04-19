package com.platform.smartwastemanager.features.guide.presentation

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.github.barteksc.pdfviewer.PDFView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.platform.smartwastemanager.features.guide.domain.GuideContentType
import com.platform.smartwastemanager.features.guide.domain.isValidYoutubeVideoId
import dev.jeziellago.compose.markdowntext.MarkdownText
import java.io.File
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideDetailScreen(
    viewModel: GuideViewModel,
    guideId: String,
    isDriverInDriverView: Boolean,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit
) {
    val detailState by viewModel.detailUiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    LaunchedEffect(guideId) {
        viewModel.loadGuideById(guideId)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.resetDetailState() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = (detailState as? GuideDetailUiState.Success)?.guide?.title ?: "Guide"
                    Text(title, maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (detailState is GuideDetailUiState.Success) {
                        IconButton(onClick = {
                            val guide = (detailState as GuideDetailUiState.Success).guide
                            val shareText = when (guide.getContentType()) {
                                GuideContentType.MARKDOWN -> "${guide.title}\n\n${guide.contentMarkdown.take(200)}"
                                else -> "${guide.title}\n${guide.externalUrl}"
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share guide"))
                        }) {
                            Icon(Icons.Default.IosShare, contentDescription = "Share")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            if (isDriverInDriverView && detailState is GuideDetailUiState.Success) {
                FloatingActionButton(onClick = { onNavigateToEdit(guideId) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = detailState) {
                is GuideDetailUiState.Idle,
                is GuideDetailUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is GuideDetailUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadGuideById(guideId) }) { Text("Retry") }
                    }
                }

                is GuideDetailUiState.Success -> {
                    val guide = state.guide
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(16.dp)
                    ) {
                        when (guide.getContentType()) {
                            GuideContentType.MARKDOWN -> {
                                if (guide.imageUrls.isNotEmpty()) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(guide.imageUrls) { url ->
                                            AsyncImage(
                                                model = url,
                                                contentDescription = "Guide image",
                                                modifier = Modifier
                                                    .height(200.dp)
                                                    .width(280.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                MarkdownText(
                                    markdown = normalizeMarkdown(guide.contentMarkdown),
                                    style = TextStyle(
                                        fontSize = 16.sp,
                                        lineHeight = 24.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            GuideContentType.YOUTUBE -> {
                                val videoId = guide.externalUrl.trim()
                                if (!isValidYoutubeVideoId(videoId)) {
                                    Text("Invalid YouTube video ID", color = MaterialTheme.colorScheme.error)
                                } else {
                                    YouTubeContent(videoId = videoId)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    SuggestionChip(
                                        onClick = {
                                            val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId"))
                                            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/$videoId"))
                                            runCatching { context.startActivity(appIntent) }
                                                .onFailure { context.startActivity(webIntent) }
                                        },
                                        label = { Text("Open in YouTube app") }
                                    )
                                }
                            }

                            GuideContentType.GOOGLE_DOC -> {
                                GoogleDocContent(url = guide.externalUrl)
                                Spacer(modifier = Modifier.height(10.dp))
                                SuggestionChip(
                                    onClick = {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(guide.externalUrl))
                                        )
                                    },
                                    label = { Text("Open in browser") }
                                )
                            }

                            GuideContentType.PDF -> {
                                PdfContent(url = guide.externalUrl)
                                Spacer(modifier = Modifier.height(10.dp))
                                SuggestionChip(
                                    onClick = {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(guide.externalUrl))
                                        )
                                    },
                                    label = { Text("Download PDF") }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(88.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun YouTubeContent(videoId: String) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val errorState = remember { mutableStateOf<String?>(null) }

    if (errorState.value != null) {
        Text(errorState.value ?: "Unable to load video", color = MaterialTheme.colorScheme.error)
        return
    }

    AndroidView(
        factory = { context ->
            YouTubePlayerView(context).apply {
                lifecycleOwner.lifecycle.addObserver(this)
                addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        youTubePlayer.cueVideo(videoId, 0f)
                    }

                    override fun onError(
                        youTubePlayer: YouTubePlayer,
                        error: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.PlayerConstants.PlayerError
                    ) {
                        errorState.value = "Video is unavailable."
                    }
                })
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
    )
}

@Composable
private fun GoogleDocContent(url: String) {
    if (!url.startsWith("https://docs.google.com/")) {
        Text(
            "Invalid Google Docs URL. It must start with https://docs.google.com/",
            color = MaterialTheme.colorScheme.error
        )
        return
    }

    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxWidth().height(520.dp)) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = false
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            errorObj: WebResourceError?
                        ) {
                            isLoading = false
                            error = "Could not load Google Doc. Verify public access settings."
                        }
                    }
                    loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        if (error != null) {
            Text(
                text = error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(Color.White)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun PdfContent(url: String) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var file by remember(url) { mutableStateOf<File?>(null) }

    LaunchedEffect(url) {
        isLoading = true
        error = null
        file = null

        val result = withContext(Dispatchers.IO) {
            runCatching {
                val safeHash = sha256(url)
                val target = File(context.cacheDir, "guide_$safeHash.pdf")
                URL(url).openStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                target
            }
        }

        result.onSuccess {
            file = it
            isLoading = false
        }.onFailure {
            error = "Failed to download PDF."
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(520.dp)
    ) {
        file?.let { pdfFile ->
            AndroidView(
                factory = { context -> PDFView(context, null) },
                update = { view ->
                    view.fromFile(pdfFile)
                        .enableSwipe(true)
                        .enableDoubletap(true)
                        .swipeHorizontal(false)
                        .load()
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        if (error != null) {
            Text(
                text = error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

private fun normalizeMarkdown(content: String): String {
    if (content.isBlank()) return content
    val lines = content.lines()
    val result = StringBuilder()
    lines.forEachIndexed { index, line ->
        result.append(line)
        if (index < lines.lastIndex) {
            val next = lines[index + 1]
            if (line.isNotBlank() && next.isNotBlank()) {
                result.append("  ")
            }
            result.append("\n")
        }
    }
    return result.toString()
}

private fun sha256(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
