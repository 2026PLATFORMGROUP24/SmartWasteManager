package com.platform.smartwastemanager.features.aiassist.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Quick-prompt suggestions surfaced below the text field. */
private val QUICK_PROMPTS = listOf(
    "Please tell me how to recycle this",
    "Can I put this in the regular bin?",
    "Is this hazardous waste?",
    "Where should I dispose of this?",
    "How do I prepare this item for recycling?"
)

/**
 * Ask AI screen — displayed as the "Ask AI" tab inside ReportFormScreen.
 *
 * Phases:
 *  1. LABEL EDITING  — user sees TFLite labels as editable chips; can add / remove / edit.
 *  2. PROMPT INPUT   — after confirming labels, user picks / types a prompt.
 *  3. RESULT         — after sending, the AI response is displayed (read-only).
 *
 * @param viewModel          The shared [AiAssistViewModel].
 * @param currentUserUid     Logged-in user's Firebase UID.
 * @param scannedBitmap      The bitmap captured by the camera (null if no image).
 * @param onRequestScan      Called when the user taps "Scan an Image" (no image yet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistScreen(
    viewModel: AiAssistViewModel,
    currentUserUid: String,
    scannedBitmap: Bitmap?,
    onRequestScan: () -> Unit
) {
    val editableLabels  by viewModel.editableLabels.collectAsStateWithLifecycle()
    val labelsConfirmed by viewModel.labelsConfirmed.collectAsStateWithLifecycle()
    val promptText      by viewModel.promptText.collectAsStateWithLifecycle()
    val promptSent      by viewModel.promptSent.collectAsStateWithLifecycle()
    val uiState         by viewModel.uiState.collectAsStateWithLifecycle()

    // Local state for adding / editing labels
    var showAddLabelDialog   by remember { mutableStateOf(false) }
    var newLabelText         by remember { mutableStateOf("") }
    var editingLabel         by remember { mutableStateOf<String?>(null) }
    var editLabelText        by remember { mutableStateOf("") }

    // ---- Add label dialog ----
    if (showAddLabelDialog) {
        AlertDialog(
            onDismissRequest = { showAddLabelDialog = false; newLabelText = "" },
            title = { Text("Add Label") },
            text = {
                OutlinedTextField(
                    value         = newLabelText,
                    onValueChange = { newLabelText = it },
                    label         = { Text("Label") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addLabel(newLabelText)
                    showAddLabelDialog = false
                    newLabelText = ""
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddLabelDialog = false; newLabelText = "" }) { Text("Cancel") }
            }
        )
    }

    // ---- Edit label dialog ----
    val labelBeingEdited = editingLabel
    if (labelBeingEdited != null) {
        AlertDialog(
            onDismissRequest = { editingLabel = null },
            title = { Text("Edit Label") },
            text = {
                OutlinedTextField(
                    value         = editLabelText,
                    onValueChange = { editLabelText = it },
                    label         = { Text("Label") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateLabel(labelBeingEdited, editLabelText)
                    editingLabel = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingLabel = null }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ================================================================
        // CASE: No image scanned yet
        // ================================================================
        if (scannedBitmap == null && editableLabels.isEmpty()) {
            NoImagePrompt(onRequestScan = onRequestScan)
            return@Column
        }

        // ================================================================
        // SCANNED IMAGE THUMBNAIL
        // ================================================================
        scannedBitmap?.let { bmp ->
            Card(
                modifier  = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Image(
                    bitmap             = bmp.asImageBitmap(),
                    contentDescription = "Scanned waste image",
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
            }
        }

        // ================================================================
        // PHASE 1 — LABEL EDITING
        // ================================================================
        if (!labelsConfirmed) {
            LabelEditingSection(
                labels            = editableLabels,
                onRemoveLabel     = { viewModel.removeLabel(it) },
                onEditLabel       = { label ->
                    editingLabel  = label
                    editLabelText = label
                },
                onAddLabel        = { showAddLabelDialog = true },
                onConfirmLabels   = { viewModel.confirmLabels() }
            )
        } else {
            // ================================================================
            // PHASE 2 — PROMPT INPUT (labels confirmed, prompt not yet sent)
            // ================================================================
            // Show confirmed labels as read-only chips
            ConfirmedLabelsRow(labels = editableLabels)

            if (!promptSent) {
                PromptInputSection(
                    promptText    = promptText,
                    onPromptChange = { viewModel.setPrompt(it) },
                    onQuickPrompt  = { viewModel.setPrompt(it) },
                    onSend         = { viewModel.sendPrompt(currentUserUid) },
                    isLoading      = uiState is AiAssistUiState.Loading
                )
            } else {
                // ================================================================
                // PHASE 3 — RESULT
                // ================================================================
                PromptResultSection(
                    prompt  = promptText,
                    uiState = uiState
                )
            }
        }
    }
}

// ============================================================================
// Sub-composables
// ============================================================================

@Composable
private fun NoImagePrompt(onRequestScan: () -> Unit) {
    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(32.dp))
        Text("🤖", style = MaterialTheme.typography.displayLarge)
        Text(
            text  = "Ask AI about Recycling",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text  = "Scan or take a photo of your waste item to get AI-powered recycling advice.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick  = onRequestScan,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Scan an Image")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabelEditingSection(
    labels: List<String>,
    onRemoveLabel: (String) -> Unit,
    onEditLabel: (String) -> Unit,
    onAddLabel: () -> Unit,
    onConfirmLabels: () -> Unit
) {
    Card(
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Label,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text       = "AI Detected Labels",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text  = "Tap a label to edit it, or long-press to remove. Add labels if anything is missing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(12.dp))

            // Chips using a simple wrapping row
            FlowRowLabels(
                labels        = labels,
                onEdit        = onEditLabel,
                onRemove      = onRemoveLabel
            )

            Spacer(Modifier.height(8.dp))

            // Add label button
            OutlinedButton(
                onClick  = onAddLabel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Label")
            }

            Spacer(Modifier.height(8.dp))

            // Confirm button
            Button(
                onClick  = onConfirmLabels,
                modifier = Modifier.fillMaxWidth(),
                enabled  = labels.isNotEmpty()
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Looks Good — Confirm Labels", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Renders labels as a wrapping row of chips with tap-to-edit and dismiss. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FlowRowLabels(
    labels: List<String>,
    onEdit: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement   = Arrangement.spacedBy(6.dp)
    ) {
        labels.forEach { label ->
            InputChip(
                selected  = false,
                onClick   = { onEdit(label) },
                label     = { Text(label, maxLines = 1) },
                trailingIcon = {
                    IconButton(
                        onClick  = { onRemove(label) },
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove $label",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun ConfirmedLabelsRow(labels: List<String>) {
    Card(
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text       = "✅ Confirmed labels:",
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text  = labels.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun PromptInputSection(
    promptText: String,
    onPromptChange: (String) -> Unit,
    onQuickPrompt: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Text(
            text       = "Ask about recycling this item:",
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        // Quick prompt buttons
        Text(
            text  = "Quick prompts:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        QUICK_PROMPTS.forEach { suggestion ->
            SuggestionChip(
                onClick  = { onQuickPrompt(suggestion) },
                label    = { Text(suggestion, style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Free-text prompt field
        OutlinedTextField(
            value         = promptText,
            onValueChange = onPromptChange,
            label         = { Text("Your question") },
            modifier      = Modifier.fillMaxWidth(),
            minLines      = 2,
            maxLines      = 4
        )

        // One-prompt limit hint
        Text(
            text  = "ℹ️ You can send one question per scan session.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Send button
        Button(
            onClick  = onSend,
            modifier = Modifier.fillMaxWidth(),
            enabled  = promptText.trim().isNotEmpty() && !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier  = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color     = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
                Text("Asking AI…")
            } else {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ask AI", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PromptResultSection(
    prompt: String,
    uiState: AiAssistUiState
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // Your question
        Card(
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text       = "Your question:",
                    style      = MaterialTheme.typography.labelSmall,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = prompt,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // AI response
        when (uiState) {
            is AiAssistUiState.Loading -> {
                Box(
                    modifier            = Modifier.fillMaxWidth(),
                    contentAlignment    = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is AiAssistUiState.Success -> {
                Card(
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint     = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text       = "AI Recycling Advice",
                                style      = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text  = uiState.response,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            is AiAssistUiState.Error -> {
                Card(
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text     = uiState.message,
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            else -> Unit
        }
    }
}
