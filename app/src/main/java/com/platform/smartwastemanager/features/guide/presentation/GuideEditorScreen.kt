package com.platform.smartwastemanager.features.guide.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.platform.smartwastemanager.features.guide.domain.RecyclingGuide

/**
 * Guide editor screen — used exclusively by drivers to create or edit a recycling guide.
 *
 * Modes:
 *  - Create : [guideId] is null.  A new Firestore document is created on save.
 *  - Edit   : [guideId] is a real Firestore document ID. The document is updated on save.
 *
 * Image flow:
 *  - Existing images (edit mode) are listed with a ✕ button so the driver can remove them.
 *  - Images picked from the gallery are shown locally with a ✕ button before uploading.
 *  - On save, the ViewModel uploads new images and appends the download URLs to Firestore.
 *
 * @param guideId        Null = create mode. Non-null = edit mode (existing document ID).
 * @param currentUserUid UID of the signed-in driver (stored as createdBy on new guides).
 * @param onNavigateBack Called when the driver taps the back arrow.
 * @param onSaveSuccess  Called with the saved guide ID after a successful save.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val saveState   by viewModel.saveUiState.collectAsStateWithLifecycle()

    // ---- Editable form fields ----
    var title             by remember { mutableStateOf("") }
    var contentMarkdown   by remember { mutableStateOf("") }
    // URLs already stored in Firestore — driver can remove individual ones
    var existingImageUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    // Local URIs picked from the gallery (not yet uploaded to Storage)
    var newImageUris      by remember { mutableStateOf<List<Uri>>(emptyList()) }
    // Guard so we only pre-fill the form once when the guide data arrives
    var hasPreloaded      by remember { mutableStateOf(false) }
    var isEasyEditorMode  by remember { mutableStateOf(true) }

    // In edit mode, load the existing guide
    LaunchedEffect(guideId) {
        if (isEditMode && guideId != null) {
            viewModel.loadGuideById(guideId)
        }
    }

    // Pre-fill the form once the guide has loaded
    LaunchedEffect(detailState) {
        if (!hasPreloaded && detailState is GuideDetailUiState.Success) {
            val guide         = (detailState as GuideDetailUiState.Success).guide
            title             = guide.title
            contentMarkdown   = guide.contentMarkdown
            existingImageUrls = guide.imageUrls
            hasPreloaded      = true
        }
    }

    // Navigate away once the save operation completes successfully
    LaunchedEffect(saveState) {
        if (saveState is GuideSaveUiState.Success) {
            val savedId = (saveState as GuideSaveUiState.Success).guideId
            viewModel.resetSaveState()
            onSaveSuccess(savedId)
        }
    }

    // Clean up ViewModel state when the driver leaves this screen
    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetSaveState()
            viewModel.resetDetailState()
        }
    }

    // Multi-image picker from the device gallery
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        newImageUris = newImageUris + uris
    }

    // Form validation
    val isTitleEmpty   = title.isBlank()
    val isContentEmpty = contentMarkdown.isBlank()
    val isSaving       = saveState is GuideSaveUiState.Saving
    val canSave        = !isTitleEmpty && !isContentEmpty && !isSaving

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Edit Guide" else "New Guide") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor             = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor          = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->

        // Show a loading spinner while fetching the guide in edit mode
        if (isEditMode && !hasPreloaded && detailState is GuideDetailUiState.Loading) {
            Box(
                modifier         = Modifier
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ---- Guide title ----
            OutlinedTextField(
                value         = title,
                onValueChange = { title = it },
                label         = { Text("Guide Title *") },
                placeholder   = { Text("e.g. How to Sort Recyclables") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                isError       = isTitleEmpty && title.isNotEmpty(),
                supportingText = if (isTitleEmpty && title.isNotEmpty()) {
                    { Text("Title is required") }
                } else null
            )

            SegmentedButtonRow {
                SegmentedButton(
                    selected = isEasyEditorMode,
                    onClick = { isEasyEditorMode = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Easy Editor") }
                SegmentedButton(
                    selected = !isEasyEditorMode,
                    onClick = { isEasyEditorMode = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Markdown") }
            }

            if (isEasyEditorMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                        onClick = { contentMarkdown += if (contentMarkdown.isBlank()) "**bold**" else "\n**bold**" },
                        label = { Text("Bold") },
                        leadingIcon = { Icon(Icons.Default.FormatBold, contentDescription = null) }
                    )
                    AssistChip(
                        onClick = { contentMarkdown += if (contentMarkdown.isBlank()) "*italic*" else "\n*italic*" },
                        label = { Text("Italic") },
                        leadingIcon = { Icon(Icons.Default.FormatItalic, contentDescription = null) }
                    )
                    AssistChip(
                        onClick = { contentMarkdown += if (contentMarkdown.isBlank()) "- list item" else "\n- list item" },
                        label = { Text("List") },
                        leadingIcon = { Icon(Icons.Default.FormatListBulleted, contentDescription = null) }
                    )
                    AssistChip(
                        onClick = { contentMarkdown += if (contentMarkdown.isBlank()) "> tip" else "\n> tip" },
                        label = { Text("Quote") },
                        leadingIcon = { Icon(Icons.Default.FormatQuote, contentDescription = null) }
                    )
                }
            }

            // ---- Content editor ----
            OutlinedTextField(
                value         = contentMarkdown,
                onValueChange = { contentMarkdown = it },
                label         = { Text(if (isEasyEditorMode) "Guide Content *" else "Guide Content (Markdown) *") },
                placeholder   = {
                    Text(
                        "# Heading\n\n" +
                                "Describe how to sort or dispose of this waste type.\n\n" +
                                "## What goes in:\n" +
                                "- Item one\n" +
                                "- Item two\n\n" +
                                "## What does NOT go in:\n" +
                                "- Contaminated items\n\n" +
                                "> 💡 Tip: rinse containers before recycling."
                    )
                },
                modifier      = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp),
                isError       = isContentEmpty && contentMarkdown.isNotEmpty(),
                supportingText = if (isContentEmpty && contentMarkdown.isNotEmpty()) {
                    { Text("Content is required") }
                } else null
            )

            // Quick Markdown syntax reminder for the driver
            SuggestionChip(
                onClick = {},
                label   = {
                    Text("💡  # H1   ## H2   **Bold**   *Italic*   - Bullet   > Quote")
                }
            )

            // ================================================================
            // IMAGES SECTION
            // ================================================================

            Text(
                text  = "Images",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Already-uploaded images (edit mode) — show with a remove button
            existingImageUrls.forEach { url ->
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model              = url,
                        contentDescription = "Existing image",
                        modifier           = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale       = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text     = "Uploaded image",
                        modifier = Modifier.weight(1f),
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = { existingImageUrls = existingImageUrls - url }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove image",
                            tint               = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Newly picked local images — show preview with a remove button
            newImageUris.forEach { uri ->
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model              = uri,
                        contentDescription = "New image preview",
                        modifier           = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale       = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text     = "Ready to upload",
                        modifier = Modifier.weight(1f),
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = { newImageUris = newImageUris - uri }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove image",
                            tint               = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Button to open the device gallery
            OutlinedButton(
                onClick  = { imagePicker.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Images from Gallery")
            }

            // ---- Save error message ----
            if (saveState is GuideSaveUiState.Error) {
                Text(
                    text  = (saveState as GuideSaveUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // ---- Save / Publish button ----
            Button(
                onClick  = {
                    if (!canSave) return@Button

                    val guide = RecyclingGuide(
                        id              = guideId ?: "",          // empty string on create
                        title           = title.trim(),
                        contentMarkdown = contentMarkdown.trim(),
                        imageUrls       = existingImageUrls,      // ViewModel appends new ones
                        createdBy       = currentUserUid
                    )

                    if (isEditMode) {
                        viewModel.updateGuide(guide, newImageUris)
                    } else {
                        viewModel.createGuide(guide, newImageUris)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled  = canSave
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color       = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Saving…")
                } else {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isEditMode) "Update Guide" else "Publish Guide")
                }
            }

            // Bottom padding so the Save button is never hidden behind the nav bar
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
