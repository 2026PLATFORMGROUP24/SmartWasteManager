package com.platform.smartwastemanager.features.guide.presentation

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.platform.smartwastemanager.features.guide.domain.GuideContentType
import com.platform.smartwastemanager.features.guide.domain.RecyclingGuide
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.delay
import org.json.JSONObject

private const val DRAFT_AUTOSAVE_INTERVAL_MS = 30_000L
private const val MAX_PDF_SIZE_BYTES = 10L * 1024L * 1024L

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
    var contentMarkdown by remember { mutableStateOf("") }
    var externalUrl by remember { mutableStateOf("") }
    var existingImageUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var newImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPdfName by remember { mutableStateOf<String?>(null) }
    var selectedPdfSizeBytes by remember { mutableStateOf<Long?>(null) }
    var showMarkdownPreview by remember { mutableStateOf(false) }
    var hasPreloaded by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    var initialSnapshot by remember { mutableStateOf("") }

    fun buildSnapshot(): String = listOf(
        title,
        contentType.name,
        contentMarkdown,
        externalUrl,
        existingImageUrls.joinToString(","),
        newImageUris.joinToString(",") { it.toString() },
        selectedPdfUri?.toString().orEmpty()
    ).joinToString("||")

    fun loadPdfMeta(uri: Uri) {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                selectedPdfName = if (nameIndex >= 0) cursor.getString(nameIndex) else "selected.pdf"
                selectedPdfSizeBytes = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else null
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        newImageUris = newImageUris + uris
    }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedPdfUri = uri
            loadPdfMeta(uri)
        }
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
                    contentMarkdown = json.optString("contentMarkdown", "")
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
            contentMarkdown = guide.contentMarkdown
            externalUrl = guide.externalUrl
            existingImageUrls = guide.imageUrls
            hasPreloaded = true
            initialSnapshot = buildSnapshot()
        }
    }

    LaunchedEffect(title, contentType, contentMarkdown, externalUrl) {
        if (!hasPreloaded) return@LaunchedEffect
        while (true) {
            delay(DRAFT_AUTOSAVE_INTERVAL_MS)
            val json = JSONObject().apply {
                put("title", title)
                put("contentType", contentType.name)
                put("contentMarkdown", contentMarkdown)
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
    val isMarkdownValid = contentType != GuideContentType.MARKDOWN || contentMarkdown.isNotBlank()
    val isYoutubeValid = contentType != GuideContentType.YOUTUBE || externalUrl.trim().matches(Regex("^[A-Za-z0-9_-]{11}$"))
    val isGoogleDocValid = contentType != GuideContentType.GOOGLE_DOC || externalUrl.trim().startsWith("https://docs.google.com/")
    val isPdfSizeValid = (selectedPdfSizeBytes ?: 0L) <= MAX_PDF_SIZE_BYTES
    val hasPdfSource = contentType != GuideContentType.PDF || selectedPdfUri != null || (isEditMode && externalUrl.isNotBlank())

    val canSave = isTitleValid && isMarkdownValid && isYoutubeValid && isGoogleDocValid && isPdfSizeValid && hasPdfSource && !isSaving

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
                                    GuideContentType.GOOGLE_DOC -> "Google Doc"
                                    GuideContentType.PDF -> "PDF"
                                }
                            )
                        }
                    )
                }
            }

            when (contentType) {
                GuideContentType.MARKDOWN -> {
                    Text("Quick Insert", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(onClick = { contentMarkdown += if (contentMarkdown.isBlank()) "**bold**" else "\n\n**bold**" }, label = { Text("Bold") })
                        AssistChip(onClick = { contentMarkdown += if (contentMarkdown.isBlank()) "*italic*" else "\n\n*italic*" }, label = { Text("Italic") })
                        AssistChip(onClick = { contentMarkdown += if (contentMarkdown.isBlank()) "`code`" else "\n\n`code`" }, label = { Text("Code") })
                        AssistChip(onClick = { contentMarkdown += if (contentMarkdown.isBlank()) "> quote" else "\n\n> quote" }, label = { Text("Quote") })
                        AssistChip(onClick = { contentMarkdown += if (contentMarkdown.isBlank()) "- list item" else "\n\n- list item" }, label = { Text("List") })
                        AssistChip(onClick = { contentMarkdown += if (contentMarkdown.isBlank()) "# heading" else "\n\n# heading" }, label = { Text("Heading") })
                    }

                    OutlinedTextField(
                        value = contentMarkdown,
                        onValueChange = { contentMarkdown = it },
                        label = { Text("Markdown Content *") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp)
                    )

                    val wordCount = remember(contentMarkdown) {
                        contentMarkdown.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
                    }
                    Text(
                        text = "${contentMarkdown.length} chars • $wordCount words",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SuggestionChip(
                        onClick = { showMarkdownPreview = !showMarkdownPreview },
                        label = { Text(if (showMarkdownPreview) "Hide Preview" else "Show Preview") }
                    )

                    if (showMarkdownPreview) {
                        MarkdownText(
                            markdown = contentMarkdown,
                            style = TextStyle(
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
                        supportingText = { Text("Enter only the video ID, not the full URL") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = externalUrl.isNotBlank() && !isYoutubeValid
                    )
                    if (externalUrl.isNotBlank() && !isYoutubeValid) {
                        Text("Invalid YouTube ID", color = MaterialTheme.colorScheme.error)
                    }
                    if (isYoutubeValid && externalUrl.isNotBlank()) {
                        Text("Preview thumbnail", style = MaterialTheme.typography.labelMedium)
                        AsyncImage(
                            model = "https://img.youtube.com/vi/${externalUrl.trim()}/0.jpg",
                            contentDescription = "YouTube preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                GuideContentType.GOOGLE_DOC -> {
                    OutlinedTextField(
                        value = externalUrl,
                        onValueChange = { externalUrl = it.trim() },
                        label = { Text("Google Docs URL") },
                        supportingText = { Text("Paste the shareable link to your Google Doc") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = externalUrl.isNotBlank() && !isGoogleDocValid
                    )
                    Text(
                        "Must be publicly accessible or 'Anyone with the link can view'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                GuideContentType.PDF -> {
                    OutlinedButton(onClick = { pdfPicker.launch("application/pdf") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Select PDF")
                    }
                    if (selectedPdfName != null) {
                        val sizeMb = ((selectedPdfSizeBytes ?: 0L).toDouble() / (1024.0 * 1024.0))
                        Text(
                            text = "$selectedPdfName (${String.format("%.2f", sizeMb)} MB)",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (!isPdfSizeValid) {
                            Text("PDF must be 10MB or less.", color = MaterialTheme.colorScheme.error)
                        }
                    } else if (isEditMode && externalUrl.isNotBlank()) {
                        Text("Existing PDF is attached.", style = MaterialTheme.typography.bodySmall)
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
                        GuideContentType.YOUTUBE,
                        GuideContentType.GOOGLE_DOC -> externalUrl.trim()
                        GuideContentType.PDF -> if (selectedPdfUri == null) externalUrl else ""
                        GuideContentType.MARKDOWN -> ""
                    }

                    val guide = RecyclingGuide(
                        id = guideId ?: "",
                        title = title.trim(),
                        contentType = contentType.name,
                        contentMarkdown = if (contentType == GuideContentType.MARKDOWN) contentMarkdown.trim() else "",
                        externalUrl = normalizedExternal,
                        imageUrls = if (contentType == GuideContentType.MARKDOWN) existingImageUrls else emptyList(),
                        createdBy = currentUserUid
                    )

                    val uploadImages = if (contentType == GuideContentType.MARKDOWN) newImageUris else emptyList()
                    val pdfToUpload = if (contentType == GuideContentType.PDF) selectedPdfUri else null
                    if (isEditMode) {
                        viewModel.updateGuide(guide, uploadImages, pdfToUpload)
                    } else {
                        viewModel.createGuide(guide, uploadImages, pdfToUpload)
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
