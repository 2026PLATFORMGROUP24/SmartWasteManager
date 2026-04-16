package com.platform.smartwastemanager.features.announcement.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platform.smartwastemanager.features.announcement.domain.Announcement
import java.text.SimpleDateFormat
import java.util.*

/**
 * Announcements screen.
 *
 * User view:   Read-only list of announcements sorted by date (newest first).
 * Driver view: Same list + FAB to create new announcements + delete button on each card.
 */
@Composable
fun AnnouncementScreen(
    viewModel: AnnouncementViewModel,
    isDriverInDriverView: Boolean,
    currentUserUid: String
) {
    val announcements by viewModel.announcements.collectAsStateWithLifecycle()
    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        when (uiState) {
            is AnnouncementUiState.Success -> {
                snackbarHostState.showSnackbar((uiState as AnnouncementUiState.Success).message)
                viewModel.resetUiState()
            }
            is AnnouncementUiState.Error -> {
                snackbarHostState.showSnackbar((uiState as AnnouncementUiState.Error).message)
                viewModel.resetUiState()
            }
            else -> {}
        }
    }

    if (showCreateDialog) {
        CreateAnnouncementDialog(
            onDismiss = { showCreateDialog = false },
            onCreate  = { title, message ->
                viewModel.createAnnouncement(title, message, currentUserUid)
                showCreateDialog = false
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (isDriverInDriverView) {
                FloatingActionButton(
                    onClick        = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector        = Icons.Default.Add,
                        contentDescription = "Create Announcement",
                        tint               = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text       = "Announcements",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            if (announcements.isEmpty()) {
                Box(
                    modifier         = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text  = "No announcements yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding      = PaddingValues(bottom = 88.dp)
                ) {
                    items(announcements, key = { it.id }) { announcement ->
                        AnnouncementCard(
                            announcement         = announcement,
                            isDriverInDriverView = isDriverInDriverView,
                            onDelete             = { viewModel.deleteAnnouncement(announcement.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnouncementCard(
    announcement: Announcement,
    isDriverInDriverView: Boolean,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title            = { Text("Delete Announcement") },
            text             = { Text("Are you sure you want to delete \"${announcement.title}\"?") },
            confirmButton    = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = announcement.title,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text  = announcement.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                // Driver view: delete button
                if (isDriverInDriverView) {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector        = Icons.Default.Delete,
                            contentDescription = "Delete Announcement",
                            tint               = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Date label
            val dateFormat = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
            Text(
                text  = dateFormat.format(announcement.createdAt.toDate()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun CreateAnnouncementDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, message: String) -> Unit
) {
    var title   by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("New Announcement") },
        text             = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value        = title,
                    onValueChange = { title = it },
                    label         = { Text("Title") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value        = message,
                    onValueChange = { message = it },
                    label         = { Text("Message") },
                    minLines      = 3,
                    maxLines      = 6,
                    modifier      = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(title, message) },
                enabled = title.isNotBlank() && message.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}