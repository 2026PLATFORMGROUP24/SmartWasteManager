package com.platform.smartwastemanager.features.guide.presentation

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.platform.smartwastemanager.features.guide.domain.GuideContentType
import com.platform.smartwastemanager.features.guide.domain.RecyclingGuide
import com.platform.smartwastemanager.features.guide.domain.isValidYoutubeVideoId
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.delay
import org.json.JSONObject

private const val DRAFT_AUTOSAVE_INTERVAL_MS = 30_000L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GuideEditorScreen(
    viewModel: GuideViewModel,
    guideId: String?,
    currentUserUid: String,
    onNavigateBack: () -> Unit,
    onSaveSuccess: (String) -> Unit
) {
    val isEditMode = guideId != null
    val detailState by viewModel.detailUiState.collectAsStateWithLifecycle()
    val saveState by viewModel.saveUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("guide_editor_drafts", 0) }
    val draftKey = remember(guideId) { "guide_draft_${guideId ?: "new"}" }

    var title by remember { mutableStateOf("") }
    var contentType by remember { mutableStateOf(GuideContentType.MARKDOWN) }
    var contentMarkdown by remember { mutableStateOf(TextFieldValue("")) }
    var externalUrl by remember { mutableStateOf("") }
    var existingImageUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var newImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showMarkdownPreview by remember { mutableStateOf(false) }
    var showMarkdownGuide by remember { mutableStateOf(false) }
    var hasPreloaded by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    var initialSnapshot by remember { mutableStateOf("") }

    fun buildSnapshot(): String = listOf(
        title,
        contentType.name,
        contentMarkdown.text,
        externalUrl,
        existingImageUrls.joinToString(","),
        newImageUris.joinToString(",") { it.toString() }
    ).joinToString("||")

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        newImageUris = newImageUris + uris
    }

    LaunchedEffect(guideId) {
        if (isEditMode && guideId != null) {
            viewModel.loadGuideById(guideId)
        } else {
            val raw = prefs.getString(draftKey, null)
            if (!raw.isNullOrBlank()) {
                runCatching {
                    val json = JSONObject(raw)
                    title = json.optString("title", "")
                    contentType = runCatching { GuideContentType.valueOf(json.optString("contentType", GuideContentType.MARKDOWN.name)) }
                        .getOrDefault(GuideContentType.MARKDOWN)
                    contentMarkdown = TextFieldValue(json.optString("contentMarkdown", ""))
                    externalUrl = json.optString("externalUrl", "")
                }
            }
            hasPreloaded = true
            initialSnapshot = buildSnapshot()
        }
    }

    LaunchedEffect(detailState) {
        if (!hasPreloaded && detailState is GuideDetailUiState.Success) {
            val guide = (detailState as GuideDetailUiState.Success).guide
            title = guide.title
            contentType = guide.getContentType()
            contentMarkdown = TextFieldValue(guide.contentMarkdown)
            externalUrl = guide.externalUrl
            existingImageUrls = guide.imageUrls
            hasPreloaded = true
            initialSnapshot = buildSnapshot()
        }
    }

    LaunchedEffect(title, contentType, contentMarkdown.text, externalUrl) {
        if (!hasPreloaded) return@LaunchedEffect
        while (true) {
            delay(DRAFT_AUTOSAVE_INTERVAL_MS)
            val json = JSONObject().apply {
                put("title", title)
                put("contentType", contentType.name)
                put("contentMarkdown", contentMarkdown.text)
                put("externalUrl", externalUrl)
            }
            prefs.edit().putString(draftKey, json.toString()).apply()
        }
    }

    LaunchedEffect(saveState) {
        if (saveState is GuideSaveUiState.Success) {
            prefs.edit().remove(draftKey).apply()
            onSaveSuccess((saveState as GuideSaveUiState.Success).guideId)
            delay(100)
            viewModel.resetSaveState()
            viewModel.resetDetailState()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetSaveState()
            viewModel.resetDetailState()
        }
    }

    val currentSnapshot = buildSnapshot()
    val hasUnsavedChanges = hasPreloaded && currentSnapshot != initialSnapshot
    BackHandler(enabled = hasUnsavedChanges) {
        showDiscardDialog = true
    }

    val isSaving = saveState is GuideSaveUiState.Saving
    val isTitleValid = title.isNotBlank()
    val isMarkdownValid = contentType != GuideContentType.MARKDOWN || contentMarkdown.text.isNotBlank()
    val isYoutubeValid = contentType != GuideContentType.YOUTUBE || isValidYoutubeVideoId(externalUrl.trim())

    val canSave = isTitleValid &&
            isMarkdownValid &&
            isYoutubeValid &&
            !isSaving

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes. Are you sure you want to leave?") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onNavigateBack()
                }) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Edit Guide" else "New Guide") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasUnsavedChanges) showDiscardDialog = true else onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        if (isEditMode && !hasPreloaded && detailState is GuideDetailUiState.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Guide Title *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text("Content Type", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GuideContentType.entries.forEach { type ->
                    FilterChip(
                        selected = contentType == type,
                        onClick = { contentType = type },
                        label = {
                            Text(
                                when (type) {
                                    GuideContentType.MARKDOWN -> "Markdown"
                                    GuideContentType.YOUTUBE -> "YouTube"
                                }
                            )
                        }
                    )
                }
            }

            when (contentType) {
                GuideContentType.MARKDOWN -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Quick Insert", style = MaterialTheme.typography.labelMedium)
                        SuggestionChip(
                            onClick = { showMarkdownGuide = !showMarkdownGuide },
                            label = { Text("Syntax Guide") },
                            icon = { Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }

                    if (showMarkdownGuide) {
                        MarkdownSyntaxLegend()
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        fun insertAtCursor(snippet: String) {
                            val text = contentMarkdown.text
                            val sel = contentMarkdown.selection
                            val start = sel.start.coerceIn(0, text.length)
                            val end = sel.end.coerceIn(0, text.length)
                            val newText = text.substring(0, start) + snippet + text.substring(end)
                            val newCursor = start + snippet.length
                            contentMarkdown = TextFieldValue(
                                text = newText,
                                selection = TextRange(newCursor)
                            )
                        }
                        AssistChip(onClick = { insertAtCursor("**bold**") }, label = { Text("Bold") })
                        AssistChip(onClick = { insertAtCursor("*italic*") }, label = { Text("Italic") })
                        AssistChip(onClick = { insertAtCursor("`code`") }, label = { Text("Code") })
                        AssistChip(onClick = { insertAtCursor("> quote") }, label = { Text("Quote") })
                        AssistChip(onClick = { insertAtCursor("- list item") }, label = { Text("List") })
                        AssistChip(onClick = { insertAtCursor("# heading") }, label = { Text("Heading") })
                    }

                    OutlinedTextField(
                        value = contentMarkdown,
                        onValueChange = { contentMarkdown = it },
                        label = { Text("Markdown Content *") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp)
                    )

                    val wordCount = remember(contentMarkdown.text) {
                        contentMarkdown.text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
                    }
                    Text(
                        text = "${contentMarkdown.text.length} chars • $wordCount words",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SuggestionChip(
                        onClick = { showMarkdownPreview = !showMarkdownPreview },
                        label = { Text(if (showMarkdownPreview) "Hide Preview" else "Show Preview") }
                    )

                    if (showMarkdownPreview) {
                        MarkdownText(
                            markdown = contentMarkdown.text,
                            style = androidx.compose.ui.text.TextStyle(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Text("Images", style = MaterialTheme.typography.titleSmall)
                    existingImageUrls.forEach { url ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Uploaded image", modifier = Modifier.weight(1f))
                            IconButton(onClick = { existingImageUrls = existingImageUrls - url }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove")
                            }
                        }
                    }
                    newImageUris.forEach { uri ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Ready to upload", modifier = Modifier.weight(1f))
                            IconButton(onClick = { newImageUris = newImageUris - uri }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove")
                            }
                        }
                    }
                    OutlinedButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Pick Images")
                    }
                }

                GuideContentType.YOUTUBE -> {
                    OutlinedTextField(
                        value = externalUrl,
                        onValueChange = { externalUrl = it.trim() },
                        label = { Text("YouTube Video ID") },
                        supportingText = {
                            Column {
                                Text("Enter only the video ID (11 characters)")
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "To get the YouTube video ID, copy the characters after 'v=' in the video URL. For example, in https://youtube.com/watch?v=abc123XYZ78, the ID is abc123XYZ78.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        isError = externalUrl.isNotBlank() && !isYoutubeValid
                    )
                    if (externalUrl.isNotBlank() && !isYoutubeValid) {
                        Text("Invalid YouTube ID", color = MaterialTheme.colorScheme.error)
                    }
                    if (isYoutubeValid && externalUrl.isNotBlank()) {
                        Text("Preview thumbnail", style = MaterialTheme.typography.labelMedium)
                        AsyncImage(
                            model = "https://img.youtube.com/vi/${externalUrl.trim()}/mqdefault.jpg",
                            contentDescription = "YouTube preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            if (saveState is GuideSaveUiState.Error) {
                Text((saveState as GuideSaveUiState.Error).message, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    if (!canSave) return@Button
                    val normalizedExternal = when (contentType) {
                        GuideContentType.YOUTUBE -> externalUrl.trim()
                        GuideContentType.MARKDOWN -> ""
                    }

                    val guide = RecyclingGuide(
                        id = guideId ?: "",
                        title = title.trim(),
                        contentType = contentType.name,
                        contentMarkdown = if (contentType == GuideContentType.MARKDOWN) contentMarkdown.text.trim() else "",
                        externalUrl = normalizedExternal,
                        imageUrls = if (contentType == GuideContentType.MARKDOWN) existingImageUrls else emptyList(),
                        createdBy = currentUserUid
                    )

                    val uploadImages = if (contentType == GuideContentType.MARKDOWN) newImageUris else emptyList()
                    if (isEditMode) {
                        viewModel.updateGuide(guide, uploadImages)
                    } else {
                        viewModel.createGuide(guide, uploadImages)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSave
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Saving…")
                } else {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isEditMode) "Update Guide" else "Publish Guide")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun MarkdownSyntaxLegend() {
    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Markdown Syntax", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            
            val items = listOf(
                "# Heading" to "Heading 1",
                "## Heading" to "Heading 2",
                "**bold**" to "Bold text",
                "*italic*" to "Italic text",
                "- item" to "Bullet point",
                "1. item" to "Numbered list",
                "> quote" to "Blockquote",
                "`code`" to "Inline code",
                "---" to "Horizontal rule"
            )

            items.chunked(2).forEach { rowItems ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    rowItems.forEach { (syntax, desc) ->
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(syntax, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(80.dp))
                            Text(desc, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (rowItems.size < 2) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
