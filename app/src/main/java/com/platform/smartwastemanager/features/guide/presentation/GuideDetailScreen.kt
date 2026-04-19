package com.platform.smartwastemanager.features.guide.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.platform.smartwastemanager.features.guide.domain.GuideContentType
import com.platform.smartwastemanager.features.guide.domain.isValidYoutubeVideoId
import dev.jeziellago.compose.markdowntext.MarkdownText

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
                                    val thumbnailUrl = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(16f / 9f),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                AsyncImage(
                                                    model = thumbnailUrl,
                                                    contentDescription = "Video thumbnail",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.PlayCircle,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(80.dp)
                                                        .align(Alignment.Center),
                                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(24.dp))

                                        Button(
                                            onClick = {
                                                val appIntent = Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse("vnd.youtube:$videoId")
                                                )
                                                val webIntent = Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse("https://www.youtube.com/watch?v=$videoId")
                                                )
                                                runCatching { context.startActivity(appIntent) }
                                                    .onFailure { context.startActivity(webIntent) }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayCircle,
                                                contentDescription = null
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Watch on YouTube")
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Opens in YouTube app or browser",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            GuideContentType.GOOGLE_DOC -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Google Document",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = guide.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(
                                        onClick = {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(guide.externalUrl))
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Open in Browser")
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Opens in your default browser",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
private fun PdfContent(url: String) {
    if (url.isBlank()) {
        Text("Invalid PDF URL", color = MaterialTheme.colorScheme.error)
        return
    }
    PdfViewer(
        pdfUrl = url,
        modifier = Modifier
            .fillMaxWidth()
            .height(520.dp)
    )
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
