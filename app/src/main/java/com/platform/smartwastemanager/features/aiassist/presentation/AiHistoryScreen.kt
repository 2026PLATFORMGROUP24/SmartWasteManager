package com.platform.smartwastemanager.features.aiassist.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.platform.smartwastemanager.features.aiassist.domain.AiAssistEntry
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * AI Assist History screen.
 *
 * Shows all past Ask AI sessions for the logged-in user, newest first.
 * Each entry displays the scanned image (if any), the confirmed labels,
 * the user's question, and the AI response.
 *
 * Firestore offline persistence provides automatic caching so entries are
 * visible even without a network connection.
 *
 * @param viewModel       The shared [AiAssistViewModel].
 * @param currentUserUid  Logged-in user's Firebase UID.
 * @param onNavigateBack  Called when the user taps the back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiHistoryScreen(
    viewModel: AiAssistViewModel,
    currentUserUid: String,
    onNavigateBack: () -> Unit
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    // Start streaming history when this screen opens
    LaunchedEffect(currentUserUid) {
        viewModel.loadHistory(currentUserUid)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ask AI History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor         = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor      = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        if (history.isEmpty()) {
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("🤖", style = MaterialTheme.typography.displayMedium)
                    Text(
                        text  = "No AI history yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text  = "Scan a waste item and tap Ask AI to get started.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier        = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding  = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(history, key = { it.id }) { entry ->
                    AiHistoryCard(entry = entry, dateFormat = dateFormat)
                }
            }
        }
    }
}

// =============================================================================
// AiHistoryCard — single entry card
// =============================================================================

@Composable
private fun AiHistoryCard(
    entry: AiAssistEntry,
    dateFormat: SimpleDateFormat
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ---- Image (if available) ----
            if (entry.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model              = entry.imageUrl,
                    contentDescription = "Scanned waste image",
                    modifier           = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentScale       = ContentScale.Crop
                )
                Spacer(Modifier.height(10.dp))
            }

            // ---- Labels ----
            if (entry.labels.isNotEmpty()) {
                Text(
                    text       = "🏷️ " + entry.labels.joinToString(" • "),
                    style      = MaterialTheme.typography.bodySmall,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
            }

            // ---- Question ----
            Text(
                text       = "❓ ${entry.prompt}",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface,
                maxLines   = 3,
                overflow   = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))

            // ---- AI Response ----
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector        = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(16.dp)
                )
                Text(
                    text     = entry.response,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurface,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(8.dp))

            // ---- Timestamp ----
            Text(
                text  = dateFormat.format(entry.timestamp.toDate()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
