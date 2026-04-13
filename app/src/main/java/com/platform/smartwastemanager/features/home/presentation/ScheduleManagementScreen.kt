package com.platform.smartwastemanager.features.home.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platform.smartwastemanager.core.util.Constants
import com.platform.smartwastemanager.features.home.domain.CollectionDay

// ─────────────────────────────────────────────────────────
// Helper — formats a hour (0–23) and minute (0–59) to "HH:mm"
// ─────────────────────────────────────────────────────────
private fun formatTime(hour: Int, minute: Int): String =
    "%02d:%02d".format(hour, minute)

// ─────────────────────────────────────────────────────────
// Helper — parses "HH:mm" back to a Pair(hour, minute).
// Returns null if the string is blank or malformed.
// ─────────────────────────────────────────────────────────
private fun parseTime(value: String): Pair<Int, Int>? {
    val parts = value.split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return Pair(h, m)
}

/**
 * A reusable Material 3 time-picker dialog.
 *
 * Material 3 ships a TimePicker composable but no ready-made dialog wrapper,
 * so we build a thin one here using Dialog + a Card to give it a surface.
 *
 * @param title       Label shown above the clock face (e.g. "Select Start Time").
 * @param initialHour Hour to pre-select when the dialog opens (0–23).
 * @param initialMinute Minute to pre-select (0–59).
 * @param onDismiss   Called when the user cancels without confirming.
 * @param onConfirm   Called with the chosen hour and minute.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    // TimePickerState holds the user's current selection inside the clock face
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true   // 24-hour clock; set false for AM/PM if preferred
    )

    // Use Dialog (not AlertDialog) so we can fully control the layout/size
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Title text
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // The actual clock-face picker
                TimePicker(state = timePickerState)

                // Cancel / OK buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        onConfirm(timePickerState.hour, timePickerState.minute)
                    }) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// ScheduleManagementScreen
// ─────────────────────────────────────────────────────────

/**
 * Schedule Management screen — visible to drivers only.
 * Allows drivers to Create / Edit / Delete schedule entries.
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

    var showDialog by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<CollectionDay?>(null) }
    var scheduleToDelete by remember { mutableStateOf<CollectionDay?>(null) }

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

    // ---- Create / Edit Dialog ----
    if (showDialog) {
        ScheduleDialog(
            existingSchedule = editingSchedule,
            onDismiss = { showDialog = false; editingSchedule = null },
            onConfirm = { day, categories, timeRange, guideId ->
                if (editingSchedule != null) {
                    viewModel.updateSchedule(
                        editingSchedule!!.copy(
                            dayOfWeek = day,
                            wasteCategories = categories,
                            collectionTimeRange = timeRange,
                            linkedGuideId = guideId
                        )
                    )
                } else {
                    viewModel.createSchedule(
                        dayOfWeek = day,
                        wasteCategories = categories,
                        collectionTimeRange = timeRange,
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
                Text(
                    "Delete the ${scheduleToDelete!!.dayOfWeek} schedule " +
                            "(${scheduleToDelete!!.wasteCategories.joinToString()})?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSchedule(scheduleToDelete!!.id)
                    scheduleToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { scheduleToDelete = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Schedules") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
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
                onClick = { editingSchedule = null; showDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add schedule",
                    tint = MaterialTheme.colorScheme.onPrimary)
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
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No schedules yet. Tap + to add one.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(schedules) { schedule ->
                        ManageScheduleCard(
                            schedule = schedule,
                            onEdit = { editingSchedule = schedule; showDialog = true },
                            onDelete = { scheduleToDelete = schedule }
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// ManageScheduleCard
// ─────────────────────────────────────────────────────────

/** Management list card — shows day, time range, categories, guide status. */
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
                if (!schedule.collectionTimeRange.isNullOrBlank()) {
                    Text(
                        text = "🕐 ${schedule.collectionTimeRange}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (schedule.wasteCategories.isNotEmpty()) {
                    Text(
                        text = schedule.wasteCategories.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (schedule.linkedGuideId != null) {
                    Text(
                        text = "Guide linked ✓",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// ScheduleDialog — Create / Edit a schedule entry
// ─────────────────────────────────────────────────────────

/**
 * Dialog for creating or editing a schedule entry.
 *
 * Time range uses Material 3 TimePicker dialogs (clock-face UI) instead of
 * plain text fields. Tapping "Select Start Time" or "Select End Time" opens
 * a dedicated clock-face picker dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDialog(
    existingSchedule: CollectionDay?,
    onDismiss: () -> Unit,
    onConfirm: (
        day: String,
        categories: List<String>,
        timeRange: String?,
        guideId: String?
    ) -> Unit
) {
    val allCategories = listOf(
        "Recyclable", "Organic", "Paper", "Glass",
        "Plastic", "Metal", "Hazardous", "Mixed Waste"
    )

    // ---- Day of week state ----
    var selectedDay by remember { mutableStateOf(existingSchedule?.dayOfWeek ?: "") }
    var dayDropdownExpanded by remember { mutableStateOf(false) }

    // ---- Category multi-select state ----
    var selectedCategories by remember {
        mutableStateOf<Set<String>>(
            existingSchedule?.wasteCategories?.toSet() ?: emptySet()
        )
    }

    // ---- Time range state ----
    // Parse the existing "HH:mm – HH:mm" string back into separate hour/minute values.
    // If null or blank, default to 07:00 – 17:00 as a sensible starting point.
    val existingRange = existingSchedule?.collectionTimeRange
    val existingStart = existingRange?.substringBefore("–")?.trim()
    val existingEnd = existingRange?.substringAfter("–")?.trim()

    var startHour by remember { mutableStateOf(parseTime(existingStart ?: "")?.first ?: 7) }
    var startMinute by remember { mutableStateOf(parseTime(existingStart ?: "")?.second ?: 0) }
    var endHour by remember { mutableStateOf(parseTime(existingEnd ?: "")?.first ?: 17) }
    var endMinute by remember { mutableStateOf(parseTime(existingEnd ?: "")?.second ?: 0) }

    // Controls whether a time range is included at all
    var includeTimeRange by remember { mutableStateOf(!existingRange.isNullOrBlank()) }

    // Controls which time picker dialog is visible (null = none, "start" or "end")
    var activeTimePicker by remember { mutableStateOf<String?>(null) }

    // ---- Guide ID state ----
    var guideId by remember { mutableStateOf(existingSchedule?.linkedGuideId ?: "") }

    // ---- Time picker dialogs (rendered outside AlertDialog to avoid nesting issues) ----
    if (activeTimePicker == "start") {
        TimePickerDialog(
            title = "Select Start Time",
            initialHour = startHour,
            initialMinute = startMinute,
            onDismiss = { activeTimePicker = null },
            onConfirm = { h, m ->
                startHour = h
                startMinute = m
                activeTimePicker = null
            }
        )
    }
    if (activeTimePicker == "end") {
        TimePickerDialog(
            title = "Select End Time",
            initialHour = endHour,
            initialMinute = endMinute,
            onDismiss = { activeTimePicker = null },
            onConfirm = { h, m ->
                endHour = h
                endMinute = m
                activeTimePicker = null
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (existingSchedule != null) "Edit Schedule" else "Add Schedule")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // ── Day of week dropdown ──────────────────────────────────
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
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = dayDropdownExpanded
                            )
                        },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = dayDropdownExpanded,
                        onDismissRequest = { dayDropdownExpanded = false }
                    ) {
                        Constants.DAYS_OF_WEEK.forEach { day ->
                            DropdownMenuItem(
                                text = { Text(day) },
                                onClick = { selectedDay = day; dayDropdownExpanded = false }
                            )
                        }
                    }
                }

                // ── Multi-select Waste Categories ─────────────────────────
                Text(
                    text = "Waste Categories (select all that apply)",
                    style = MaterialTheme.typography.labelMedium
                )
                allCategories.forEach { category ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = category in selectedCategories,
                            onCheckedChange = { isChecked ->
                                selectedCategories = if (isChecked)
                                    selectedCategories + category
                                else
                                    selectedCategories - category
                            }
                        )
                        Text(
                            text = category,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                // ── Collection Time Range ─────────────────────────────────
                // Toggle row — driver can choose to include a time range or not
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = includeTimeRange,
                        onCheckedChange = { includeTimeRange = it }
                    )
                    Text(
                        text = "Set collection time range",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                // Show the two time buttons only when the toggle is enabled
                if (includeTimeRange) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // ---- Start time button ----
                        OutlinedButton(
                            onClick = { activeTimePicker = "start" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = formatTime(startHour, startMinute),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // Separator label
                        Text(
                            text = "to",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )

                        // ---- End time button ----
                        OutlinedButton(
                            onClick = { activeTimePicker = "end" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = formatTime(endHour, endMinute),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // ── Optional Linked Guide ID ──────────────────────────────
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
                    // Only build a time range string if the toggle is on
                    val timeRange = if (includeTimeRange)
                        "${formatTime(startHour, startMinute)} – ${formatTime(endHour, endMinute)}"
                    else null

                    onConfirm(
                        selectedDay,
                        selectedCategories.toList(),
                        timeRange,
                        guideId.ifBlank { null }
                    )
                }
            ) {
                Text(if (existingSchedule != null) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}