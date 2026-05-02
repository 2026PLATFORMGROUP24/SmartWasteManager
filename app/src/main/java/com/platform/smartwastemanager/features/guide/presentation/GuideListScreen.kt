package com.platform.smartwastemanager.features.guide.presentation

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.platform.smartwastemanager.features.guide.domain.GuideContentType
import com.platform.smartwastemanager.features.guide.domain.RecyclingGuide
import com.platform.smartwastemanager.features.guide.domain.isValidYoutubeVideoId
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.delay
import androidx.compose.material3.ExperimentalMaterial3Api

/**
 * Guide list screen — shows all recycling guides as scrollable cards.
 *
 * User view  : tap a card to read the full guide (or open YouTube directly for videos).
 * Driver view: FAB to create a guide; each card has Edit and Delete icons.
 *
 * @param isDriverInDriverView True when a driver is in driver view (shows edit/delete controls).
 * @param onNavigateToDetail   Navigate to the detail screen for the tapped guide.
 * @param onNavigateToEditor   Navigate to the editor in create mode.
 * @param onNavigateToEditorEdit Navigate to the editor in edit mode for the given guide ID.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideListScreen(
    viewModel: GuideViewModel,
    isDriverInDriverView: Boolean,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToEditor: () -> Unit,
    onNavigateToEditorEdit: (String) -> Unit
) {
    val uiState       by viewModel.listUiState.collectAsStateWithLifecycle()
    val deleteSuccess by viewModel.deleteSuccess.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val dateFormat        = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    var searchQuery       by remember { mutableStateOf("") }
    var selectedFilter    by remember { mutableStateOf<GuideContentType?>(null) }
    var isRefreshing      by remember { mutableStateOf(false) }

    // Track which guide the driver wants to delete (null = no dialog open)
    var guideToDelete by remember { mutableStateOf<RecyclingGuide?>(null) }

    // Show a snackbar when a guide has been deleted
    LaunchedEffect(deleteSuccess) {
        if (deleteSuccess) {
            snackbarHostState.showSnackbar("Guide deleted")
            viewModel.resetDeleteSuccess()
        }
    }

    // ---- Delete confirmation dialog ----
    if (guideToDelete != null) {
        AlertDialog(
            onDismissRequest = { guideToDelete = null },
            title = { Text("Delete Guide?") },
            text  = {
                Text("Delete \"${guideToDelete!!.title}\"? This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGuide(guideToDelete!!.id)
                    guideToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { guideToDelete = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Only show the create FAB to drivers in driver view
            if (isDriverInDriverView) {
                FloatingActionButton(
                    onClick        = onNavigateToEditor,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create new guide")
                }
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.loadGuides()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (val state = uiState) {

                    is GuideListUiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    is GuideListUiState.Error -> {
                        Column(
                            modifier            = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Could not load guides", color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.loadGuides() }) { Text("Retry") }
                        }
                    }

                    is GuideListUiState.Success -> {
                        val filteredGuides = state.guides.filter { guide ->
                            val matchesSearch = searchQuery.isBlank() ||
                                    guide.title.contains(searchQuery, ignoreCase = true) ||
                                    guide.contentMarkdown.contains(searchQuery, ignoreCase = true)
                            val matchesFilter = selectedFilter == null || guide.getContentType() == selectedFilter
                            matchesSearch && matchesFilter
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Search and Filter controls
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp)
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Search guides") },
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = selectedFilter == null,
                                        onClick = { selectedFilter = null },
                                        label = { Text("All") }
                                    )
                                    FilterChip(
                                        selected = selectedFilter == GuideContentType.YOUTUBE,
                                        onClick = { selectedFilter = GuideContentType.YOUTUBE },
                                        label = { Text("Videos") }
                                    )
                                    FilterChip(
                                        selected = selectedFilter == GuideContentType.MARKDOWN,
                                        onClick = { selectedFilter = GuideContentType.MARKDOWN },
                                        label = { Text("Written") }
                                    )
                                }
                            }

                            if (filteredGuides.isEmpty()) {
                                // No search results or empty state message
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    val emptyMsg = if (state.guides.isEmpty()) {
                                        if (isDriverInDriverView) "No guides yet. Tap + to create the first one."
                                        else "No recycling guides available yet."
                                    } else {
                                        "No guides match your criteria."
                                    }
                                    Text(
                                        text = emptyMsg,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier            = Modifier.fillMaxSize(),
                                    contentPadding      = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(filteredGuides, key = { it.id }) { guide ->
                                        GuideListCard(
                                            guide                = guide,
                                            isDriverInDriverView = isDriverInDriverView,
                                            dateFormat           = dateFormat,
                                            onClick              = {
                                                if (guide.getContentType() == GuideContentType.YOUTUBE) {
                                                    val videoId = guide.externalUrl.trim()
                                                    val intent = Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=$videoId".toUri())
                                                    context.startActivity(intent)
                                                } else {
                                                    onNavigateToDetail(guide.id)
                                                }
                                            },
                                            onEdit               = { onNavigateToEditorEdit(guide.id) },
                                            onDelete             = { guideToDelete = guide }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        LaunchedEffect(isRefreshing) {
            if (isRefreshing) {
                delay(700)
                isRefreshing = false
            }
        }
    }
}

// =====================================================================
// GuideListCard — one card shown in the guide list
// =====================================================================

@Composable
private fun GuideListCard(
    guide: RecyclingGuide,
    isDriverInDriverView: Boolean,
    dateFormat: SimpleDateFormat,
    onClick:  () -> Unit,
    onEdit:   () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 1. Multimedia section (Image or YouTube Thumbnail)
            when (guide.getContentType()) {
                GuideContentType.MARKDOWN -> if (guide.imageUrls.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(guide.imageUrls) { imageUrl ->
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillParentMaxWidth()
                                    .height(170.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                GuideContentType.YOUTUBE -> {
                    val videoId = guide.externalUrl.trim()
                    if (isValidYoutubeVideoId(videoId)) {
                        AsyncImage(
                            model = "https://img.youtube.com/vi/$videoId/0.jpg",
                            contentDescription = "YouTube thumbnail",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }

            // Note: Indicators near top (YouTube/Markdown) and "Posted by" removed per request.

            // 2. Title row + action buttons
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = guide.title,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis
                    )
                    
                    // Preview text: only shown for Markdown (video id text removed for YouTube)
                    val preview = remember(guide.contentMarkdown, guide.getContentType()) {
                        if (guide.getContentType() == GuideContentType.MARKDOWN) {
                            guide.contentMarkdown
                                .lines()
                                .filter { line -> line.isNotBlank() && !line.startsWith("#") }
                                .joinToString(" ")
                                .take(130)
                        } else ""
                    }
                    
                    if (preview.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text     = preview,
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Edit / Delete buttons — driver view only
                if (isDriverInDriverView) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit guide",
                            tint               = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete guide",
                            tint               = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Bottom row: Updated date (Image count indicator removed per request)
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text  = "Updated ${dateFormat.format(guide.updatedAt.toDate())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
