package com.platform.smartwastemanager.features.guide.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.jeziellago.compose.markdowntext.MarkdownText

/**
 * Guide detail screen — renders the full guide Markdown content and images.
 *
 * Users  : read-only view.
 * Drivers: floating Edit button appears when in driver view.
 *
 * @param guideId            Firestore document ID of the guide to display.
 * @param isDriverInDriverView When true, an Edit FAB is shown.
 * @param onNavigateBack     Pops the back stack to the guide list.
 * @param onNavigateToEdit   Navigates to the editor in edit mode.
 */
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
    val scrollState  = rememberScrollState()

    // Load the guide the first time this screen appears
    LaunchedEffect(guideId) {
        viewModel.loadGuideById(guideId)
    }

    // Reset state when the user leaves this screen so re-entering always shows a fresh load
    DisposableEffect(Unit) {
        onDispose { viewModel.resetDetailState() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Show the guide title in the top bar once loaded
                    val title = (detailState as? GuideDetailUiState.Success)?.guide?.title ?: "Guide"
                    Text(title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor             = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor          = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor     = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            // Only drivers in driver view can edit a guide
            if (isDriverInDriverView && detailState is GuideDetailUiState.Success) {
                FloatingActionButton(
                    onClick        = { onNavigateToEdit(guideId) },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit guide")
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
                        modifier            = Modifier
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
                        // ---- Image gallery (horizontal scroll) ----
                        if (guide.imageUrls.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier              = Modifier.fillMaxWidth()
                            ) {
                                items(guide.imageUrls) { url ->
                                    AsyncImage(
                                        model              = url,
                                        contentDescription = "Guide image",
                                        modifier           = Modifier
                                            .height(200.dp)
                                            .width(280.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale       = ContentScale.Crop
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // ---- Rendered Markdown content ----
                        // MarkdownText renders headings, bold, bullets, quotes, etc.
                        MarkdownText(
                            markdown = guide.contentMarkdown,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Extra bottom padding so the FAB doesn't overlap the last line
                        Spacer(modifier = Modifier.height(88.dp))
                    }
                }
            }
        }
    }
}