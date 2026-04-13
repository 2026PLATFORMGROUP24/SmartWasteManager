package com.platform.smartwastemanager.features.home.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platform.smartwastemanager.core.util.Constants
import com.platform.smartwastemanager.features.home.domain.CollectionDay

/**
 * Schedule Management screen — visible to drivers only.
 *
 * Allows drivers to:
 * - View all existing schedule entries
 * - Create a new entry (day + category + optional guide ID)
 * - Edit an existing entry
 * - Delete an entry
 *
 * @param viewModel     The shared HomeViewModel.
 * @param driverUid     The UID of the signed-in driver — stored in new schedule entries.
 * @param onNavigateBack Called when the driver taps the back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleManagementScreen(
    viewModel: HomeViewModel,
    driverUid: String,
    onNavigateBack: () -> Unit
) {
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // Controls whether the Create/Edit dialog is open
    var showDialog by remember { mutableStateOf(false) }

    // If non-null, we are editing this entry; if null, we are creating a new one
    var editingSchedule by remember { mutableStateOf<CollectionDay?>(null) }

    // Controls the delete confirmation dialog
    var scheduleToDelete by remember { mutableStateOf<CollectionDay?>(null) }

    // Show snackbar feedback
    LaunchedEffect(uiState) {
        when (uiState) {
            is HomeUiState.Success -> {
                snackbarHostState.showSnackbar((uiState as HomeUiState.Success).message)
                viewModel.resetUiState()
            }
            is HomeUiState.Error -> {
                snackbarHostState.showSnackbar((uiState as HomeUiState.Error).message)
                viewModel.resetUiState()
            }
            else -> {}
        }
    }

    // ---- Create/Edit Dialog ----
    if (showDialog) {
        ScheduleDialog(
            existingSchedule = editingSchedule,
            onDismiss = {
                showDialog = false
                editingSchedule = null
            },
            onConfirm = { day, category, guideId ->
                if (editingSchedule != null) {
                    // Editing an existing entry — keep the same ID and createdBy
                    viewModel.updateSchedule(
                        editingSchedule!!.copy(
                            dayOfWeek = day,
                            wasteCategory = category,
                            linkedGuideId = guideId
                        )
                    )
                } else {
                    // Creating a new entry
                    viewModel.createSchedule(
                        dayOfWeek = day,
                        wasteCategory = category,
                        linkedGuideId = guideId,
                        driverUid = driverUid
                    )
                }
                showDialog = false
                editingSchedule = null
            }
        )
    }

    // ---- Delete Confirmation Dialog ----
    if (scheduleToDelete != null) {
        AlertDialog(
            onDismissRequest = { scheduleToDelete = null },
            title = { Text("Delete Schedule") },
            text = {
                Text("Are you sure you want to delete the ${scheduleToDelete!!.dayOfWeek} — ${scheduleToDelete!!.wasteCategory} schedule?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSchedule(scheduleToDelete!!.id)
                        scheduleToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { scheduleToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Schedules") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingSchedule = null
                    showDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Add schedule",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
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
                text = "Current Schedules",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            if (schedules.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No schedules yet. Tap the button to add one.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(schedules) { schedule ->
                        ManageScheduleCard(
                            schedule = schedule,
                            onEdit = {
                                editingSchedule = schedule
                                showDialog = true
                            },
                            onDelete = {
                                scheduleToDelete = schedule
                            }
                        )
                    }
                }
            }
        }
    }
}

/** A schedule card with Edit and Delete action buttons for the management screen. */
@Composable
fun ManageScheduleCard(
    schedule: CollectionDay,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = schedule.dayOfWeek,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = schedule.wasteCategory,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (schedule.linkedGuideId != null) {
                    Text(
                        text = "Guide linked ✓",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Edit button
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Delete button
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * A dialog for creating or editing a schedule entry.
 * Pre-fills the fields when editing an existing entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDialog(
    existingSchedule: CollectionDay?,
    onDismiss: () -> Unit,
    onConfirm: (day: String, category: String, guideId: String?) -> Unit
) {
    // Pre-fill with existing values if editing, otherwise start blank
    var selectedDay by remember { mutableStateOf(existingSchedule?.dayOfWeek ?: "") }
    var selectedCategory by remember { mutableStateOf(existingSchedule?.wasteCategory ?: "") }
    var guideId by remember { mutableStateOf(existingSchedule?.linkedGuideId ?: "") }

    var dayDropdownExpanded by remember { mutableStateOf(false) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    val wasteCategories = listOf(
        "Recyclable", "Organic", "Paper", "Glass",
        "Plastic", "Metal", "Hazardous", "Mixed Waste"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (existingSchedule != null) "Edit Schedule" else "Add Schedule")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // ---- Day of Week Dropdown ----
                ExposedDropdownMenuBox(
                    expanded = dayDropdownExpanded,
                    onExpandedChange = { dayDropdownExpanded = !dayDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedDay,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Day of Week") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = dayDropdownExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = dayDropdownExpanded,
                        onDismissRequest = { dayDropdownExpanded = false }
                    ) {
                        Constants.DAYS_OF_WEEK.forEach { day ->
                            DropdownMenuItem(
                                text = { Text(day) },
                                onClick = {
                                    selectedDay = day
                                    dayDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // ---- Waste Category Dropdown ----
                ExposedDropdownMenuBox(
                    expanded = categoryDropdownExpanded,
                    onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Waste Category") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false }
                    ) {
                        wasteCategories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    selectedCategory = category
                                    categoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // ---- Optional Guide ID Field ----
                OutlinedTextField(
                    value = guideId,
                    onValueChange = { guideId = it },
                    label = { Text("Linked Guide ID (optional)") },
                    placeholder = { Text("Leave blank if none") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        selectedDay,
                        selectedCategory,
                        guideId.ifBlank { null }
                    )
                }
            ) {
                Text(if (existingSchedule != null) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}