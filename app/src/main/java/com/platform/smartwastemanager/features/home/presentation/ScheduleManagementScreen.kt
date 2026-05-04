package com.platform.smartwastemanager.features.home.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platform.smartwastemanager.features.guide.domain.RecyclingGuide
import com.platform.smartwastemanager.features.home.domain.CollectionDay

private val ALL_CATEGORIES = listOf(
    "Recyclable", "Organic", "Hazardous", "Mixed Waste"
)

private val DAYS_OF_WEEK = listOf(
    "Monday", "Tuesday", "Wednesday", "Thursday",
    "Friday", "Saturday", "Sunday"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleManagementScreen(
    viewModel: HomeViewModel,
    driverUid: String,
    guides: List<RecyclingGuide>,
    onNavigateBack: () -> Unit
) {
    val uiState   by viewModel.uiState.collectAsStateWithLifecycle()
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    var dialogEntry   by remember { mutableStateOf<CollectionDay?>(null) }
    var entryToDelete by remember { mutableStateOf<CollectionDay?>(null) }

    LaunchedEffect(uiState) {
        when (uiState) {
            is HomeUiState.Error -> {
                snackbarHostState.showSnackbar((uiState as HomeUiState.Error).message)
                viewModel.resetUiState()
            }
            is HomeUiState.Success -> {
                snackbarHostState.showSnackbar((uiState as HomeUiState.Success).message)
                viewModel.resetUiState()
            }
            else -> Unit
        }
    }

    if (entryToDelete != null) {
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Delete Schedule?") },
            text  = {
                Text(
                    "Delete the ${entryToDelete!!.dayOfWeek} entry? " +
                            "This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSchedule(entryToDelete!!.id)
                    entryToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (dialogEntry != null) {
        ScheduleDialog(
            existingEntry = dialogEntry!!,
            guides        = guides,
            onDismiss     = { dialogEntry = null },
            onSave = { day, categories, timeRange, linkedGuideId, entryId ->
                if (entryId.isBlank()) {
                    viewModel.createSchedule(
                        dayOfWeek           = day,
                        wasteCategories     = categories,
                        collectionTimeRange = timeRange,
                        linkedGuideId       = linkedGuideId,
                        driverUid           = driverUid
                    )
                } else {
                    val existingSchedule = dialogEntry!!
                    viewModel.updateSchedule(
                        existingSchedule.copy(
                            dayOfWeek           = day,
                            wasteCategories     = categories,
                            collectionTimeRange = timeRange,
                            linkedGuideId       = linkedGuideId
                        )
                    )
                }
                dialogEntry = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Schedules") },
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = { dialogEntry = CollectionDay() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add schedule entry")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState is HomeUiState.Loading && schedules.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                schedules.isEmpty() -> {
                    Column(
                        modifier            = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📅", style = MaterialTheme.typography.displayMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text  = "No schedule entries yet.\nTap + to add the first one.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier            = Modifier.fillMaxSize(),
                        contentPadding      = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(schedules, key = { it.id }) { entry ->
                            ScheduleManagementCard(
                                entry    = entry,
                                guides   = guides,
                                onEdit   = { dialogEntry = entry },
                                onDelete = { entryToDelete = entry }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleManagementCard(
    entry: CollectionDay,
    guides: List<RecyclingGuide>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val linkedGuideTitle = remember(entry.linkedGuideId, guides) {
        guides.find { it.id == entry.linkedGuideId }?.title
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = entry.dayOfWeek,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                if (!entry.collectionTimeRange.isNullOrBlank()) {
                    Text(
                        text  = "🕐 ${entry.collectionTimeRange}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (entry.wasteCategories.isNotEmpty()) {
                    Text(
                        text  = entry.wasteCategories.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (linkedGuideTitle != null) {
                    Text(
                        text  = "📖 $linkedGuideTitle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit entry",
                    tint               = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete entry",
                    tint               = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDialog(
    existingEntry: CollectionDay,
    guides: List<RecyclingGuide>,
    onDismiss: () -> Unit,
    onSave: (
        dayOfWeek: String,
        categories: List<String>,
        timeRange: String?,
        linkedGuideId: String?,
        existingId: String
    ) -> Unit
) {
    val isEditMode = existingEntry.id.isNotBlank()

    var selectedDay     by remember { mutableStateOf(existingEntry.dayOfWeek.ifBlank { DAYS_OF_WEEK.first() }) }
    var dayDropdownOpen by remember { mutableStateOf(false) }

    val selectedCategories = remember {
        // Map legacy sub-categories (Paper, Plastic, Glass, Metal) to their new groups
        existingEntry.wasteCategories.map { cat ->
            when (cat) {
                "Paper", "Plastic" -> "Recyclable"
                "Glass", "Metal"   -> "Mixed Waste"
                else -> cat
            }
        }.distinct().toMutableStateList()
    }

    // Parse existing time range or default to empty
    var startHour by remember {
        val range = existingEntry.collectionTimeRange ?: ""
        val parts = if (range.contains("–")) range.split("–") else listOf("", "")
        val time = parts.getOrNull(0)?.trim() ?: ""
        mutableStateOf(if (time.isNotBlank()) time.split(":")[0].toIntOrNull() ?: 7 else 7)
    }
    var startMinute by remember {
        val range = existingEntry.collectionTimeRange ?: ""
        val parts = if (range.contains("–")) range.split("–") else listOf("", "")
        val time = parts.getOrNull(0)?.trim() ?: ""
        mutableStateOf(if (time.isNotBlank()) time.split(":")[1].toIntOrNull() ?: 0 else 0)
    }
    var endHour by remember {
        val range = existingEntry.collectionTimeRange ?: ""
        val parts = if (range.contains("–")) range.split("–") else listOf("", "")
        val time = parts.getOrNull(1)?.trim() ?: ""
        mutableStateOf(if (time.isNotBlank()) time.split(":")[0].toIntOrNull() ?: 12 else 12)
    }
    var endMinute by remember {
        val range = existingEntry.collectionTimeRange ?: ""
        val parts = if (range.contains("–")) range.split("–") else listOf("", "")
        val time = parts.getOrNull(1)?.trim() ?: ""
        mutableStateOf(if (time.isNotBlank()) time.split(":")[1].toIntOrNull() ?: 0 else 0)
    }

    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    var guideDropdownOpen by remember { mutableStateOf(false) }
    var selectedGuide     by remember {
        mutableStateOf(guides.find { it.id == existingEntry.linkedGuideId })
    }

    val categoriesError = selectedCategories.isEmpty()
    val canSave         = selectedCategories.isNotEmpty()

    if (showStartTimePicker) {
        TimePickerDialog(
            initialHour = startHour,
            initialMinute = startMinute,
            onDismiss = { showStartTimePicker = false },
            onConfirm = { hour, minute ->
                startHour = hour
                startMinute = minute
                showStartTimePicker = false
            }
        )
    }

    if (showEndTimePicker) {
        TimePickerDialog(
            initialHour = endHour,
            initialMinute = endMinute,
            onDismiss = { showEndTimePicker = false },
            onConfirm = { hour, minute ->
                endHour = hour
                endMinute = minute
                showEndTimePicker = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditMode) "Edit Schedule" else "New Schedule") },
        text  = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                ExposedDropdownMenuBox(
                    expanded         = dayDropdownOpen,
                    onExpandedChange = { dayDropdownOpen = it }
                ) {
                    OutlinedTextField(
                        value         = selectedDay,
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Day of Week") },
                        trailingIcon  = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = dayDropdownOpen)
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded         = dayDropdownOpen,
                        onDismissRequest = { dayDropdownOpen = false }
                    ) {
                        DAYS_OF_WEEK.forEach { day ->
                            DropdownMenuItem(
                                text    = { Text(day) },
                                onClick = {
                                    selectedDay     = day
                                    dayDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                Text(
                    text  = "Waste Categories *",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (categoriesError)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )

                ALL_CATEGORIES.forEach { category ->
                    val subLabel = when (category) {
                        "Recyclable"  -> " (Paper, Plastic)"
                        "Mixed Waste" -> " (Glass, Metal)"
                        else -> ""
                    }

                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked         = selectedCategories.contains(category),
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (!selectedCategories.contains(category)) {
                                        selectedCategories.add(category)
                                    }
                                } else {
                                    selectedCategories.remove(category)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text  = category + subLabel,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (categoriesError) {
                    Text(
                        text  = "Select at least one category",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(
                    text  = "Collection Time Range (optional)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value         = String.format("%02d:%02d", startHour, startMinute),
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Start Time") },
                        trailingIcon  = {
                            IconButton(onClick = { showStartTimePicker = true }) {
                                Icon(Icons.Default.AccessTime, contentDescription = "Pick start time")
                            }
                        },
                        modifier      = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value         = String.format("%02d:%02d", endHour, endMinute),
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("End Time") },
                        trailingIcon  = {
                            IconButton(onClick = { showEndTimePicker = true }) {
                                Icon(Icons.Default.AccessTime, contentDescription = "Pick end time")
                            }
                        },
                        modifier      = Modifier.weight(1f)
                    )
                }

                Text(
                    text  = "Link Recycling Guide (optional)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ExposedDropdownMenuBox(
                    expanded         = guideDropdownOpen,
                    onExpandedChange = { guideDropdownOpen = it }
                ) {
                    OutlinedTextField(
                        value         = selectedGuide?.title ?: "None",
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Linked Guide") },
                        trailingIcon  = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = guideDropdownOpen)
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded         = guideDropdownOpen,
                        onDismissRequest = { guideDropdownOpen = false }
                    ) {
                        DropdownMenuItem(
                            text    = { Text("None") },
                            onClick = {
                                selectedGuide     = null
                                guideDropdownOpen = false
                            }
                        )
                        guides.forEach { guide ->
                            DropdownMenuItem(
                                text    = { Text(guide.title) },
                                onClick = {
                                    selectedGuide     = guide
                                    guideDropdownOpen = false
                                }
                            )
                        }
                    }
                }

            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!canSave) return@TextButton

                    val timeRange = String.format(
                        "%02d:%02d – %02d:%02d",
                        startHour, startMinute, endHour, endMinute
                    )

                    onSave(
                        selectedDay,
                        selectedCategories.toList(),
                        timeRange,
                        selectedGuide?.id,
                        existingEntry.id
                    )
                },
                enabled = canSave
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(timePickerState.hour, timePickerState.minute)
            }) {
                Text("OK")
            }
        },
        text = {
            TimePicker(state = timePickerState)
        }
    )
}