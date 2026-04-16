package com.platform.smartwastemanager.features.collectionpoint.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platform.smartwastemanager.features.collectionpoint.domain.CollectionPoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCollectionPointsScreen(
    viewModel: CollectionPointViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCreate: () -> Unit,
    selectedPointId: String?,
    onPointSelected: (CollectionPoint) -> Unit
) {
    val collectionPoints by viewModel.collectionPoints.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf<CollectionPoint?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val filteredPoints = remember(collectionPoints, searchQuery) {
        if (searchQuery.isBlank()) collectionPoints
        else collectionPoints.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.streetName.contains(searchQuery, ignoreCase = true)
        }
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            is CollectionPointUiState.Success -> {
                snackbarHostState.showSnackbar((uiState as CollectionPointUiState.Success).message)
                viewModel.resetUiState()
            }
            is CollectionPointUiState.Error -> {
                snackbarHostState.showSnackbar((uiState as CollectionPointUiState.Error).message)
                viewModel.resetUiState()
            }
            else -> Unit
        }
    }

    val canAddMore = remember(collectionPoints.size) {
        collectionPoints.size < MAX_COLLECTION_POINTS
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Manage Collection Points") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (canAddMore) {
                FloatingActionButton(onClick = onNavigateToCreate) {
                    Icon(Icons.Default.Add, "Add")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search collection points") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Collection Points: ${collectionPoints.size}/$MAX_COLLECTION_POINTS",
                style = MaterialTheme.typography.bodyMedium,
                color = if (collectionPoints.size >= MAX_COLLECTION_POINTS) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            if (!canAddMore) {
                Text(
                    text = "Maximum limit reached. Delete a point to add more.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredPoints.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isBlank()) {
                            "No collection points yet.\nTap + to add your first one."
                        } else {
                            "No matching points found."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredPoints, key = { it.id }) { point ->
                        CollectionPointManagementCard(
                            point = point,
                            isSelected = point.id == selectedPointId,
                            onSelect = {
                                onPointSelected(point)
                            },
                            onDelete = { showDeleteDialog = point }
                        )
                    }
                }
            }
        }
    }

    showDeleteDialog?.let { point ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Collection Point?") },
            text = { Text("Delete ${point.name}? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCollectionPoint(point.id)
                    showDeleteDialog = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun CollectionPointManagementCard(
    point: CollectionPoint,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = point.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = point.streetName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
