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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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

// ---- All 8 waste categories shown as checkboxes (Rule 15) ----
private val ALL_CATEGORIES = listOf(
    "Recyclable", "Organic", "Paper", "Glass",
    "Plastic", "Metal", "Hazardous", "Mixed Waste"
)

// ---- Days available in the day-of-week dropdown ----
private val DAYS_OF_WEEK = listOf(
    "Monday", "Tuesday", "Wednesday", "Thursday",
    "Friday", "Saturday", "Sunday"
)

/**
 * Schedule management screen — lets drivers create, edit, and delete collection schedule entries.
 *
 * Phase 5 upgrade: the "Linked Guide" field is now a dropdown of real guide titles
 * (previously it was a raw text field requiring the driver to know the Firestore ID).
 *
 * Rules followed:
 *  14 — wasteCategories is always List<String>
 *  15 — categories shown as multi-select checkboxes
 *  16 — dialog wrapped in verticalScroll for small screens
 *
 * @param viewModel      HomeViewModel that owns schedule state.
 * @param driverUid      UID of the signed-in driver (used as createdBy on new entries).
 * @param guides         All recycling guides — used to populate the guide picker dropdown.
 * @param onNavigateBack Pop back to the home screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleManagementScreen(
    viewModel: HomeViewModel,
    driverUid: String,
    guides: List<RecyclingGuide>,
    onNavigateBack: () -> Unit
) {
    // ---- Observe ViewModel state using the CORRECT property names ----
    val uiState   by viewModel.uiState.collectAsStateWithLifecycle()   // HomeUiState
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // Which entry is being edited / created (null = no dialog open)
    // An entry with a blank id is the "create new" sentinel
    var dialogEntry   by remember { mutableStateOf<CollectionDay?>(null) }
    var entryToDelete by remember { mutableStateOf<CollectionDay?>(null) }

    // Show a snackbar for errors and successes reported by the ViewModel
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

    // ---- Delete confirmation dialog ----
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

    // ---- Create / Edit dialog ----
    if (dialogEntry != null) {
        ScheduleDialog(
            existingEntry = dialogEntry!!,
            guides        = guides,
            onDismiss     = { dialogEntry = null },

            // REPLACE lines 154-177 with this corrected version:
            onSave = { day, categories, timeRange, linkedGuideId, entryId ->
                if (entryId.isBlank()) {
                    // CREATE — call createSchedule() with individual params
                    viewModel.createSchedule(
                        dayOfWeek           = day,
                        wasteCategories     = categories,
                        collectionTimeRange = timeRange,
                        linkedGuideId       = linkedGuideId,
                        driverUid           = driverUid
                    )
                } else {
                    // UPDATE — build a CollectionDay object and pass it
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
                onClick        = { dialogEntry = CollectionDay() }, // blank id = create mode
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
                // Loading and no data yet — show a spinner
                uiState is HomeUiState.Loading && schedules.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                // Loaded but empty — show a helpful message
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

                // Data available — show the list
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

// =====================================================================
// ScheduleManagementCard — one row in the management list
// =====================================================================

@Composable
private fun ScheduleManagementCard(
    entry: CollectionDay,
    guides: List<RecyclingGuide>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    // Resolve the linked guide title for display on the card
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
                // Day of week
                Text(
                    text       = entry.dayOfWeek,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                // Collection time range (if set)
                if (!entry.collectionTimeRange.isNullOrBlank()) {
                    Text(
                        text  = "🕐 ${entry.collectionTimeRange}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Waste categories
                if (entry.wasteCategories.isNotEmpty()) {
                    Text(
                        text  = entry.wasteCategories.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                // Linked guide title (if any)
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

// =====================================================================
// ScheduleDialog — create / edit dialog
// =====================================================================

/**
 * The onSave callback passes individual values instead of a CollectionDay object.
 * This avoids the need for ScheduleManagementScreen to know whether to call
 * createSchedule() or updateSchedule() inside the dialog itself.
 *
 * @param onSave  (dayOfWeek, categories, timeRange, linkedGuideId, existingId)
 *                existingId is blank when creating a new entry.
 */
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

    // ---- Day of week ----
    var selectedDay     by remember { mutableStateOf(existingEntry.dayOfWeek.ifBlank { DAYS_OF_WEEK.first() }) }
    var dayDropdownOpen by remember { mutableStateOf(false) }

    // ---- Waste categories — use a plain MutableList wrapped in remember (Rule 15) ----
    // We avoid mutableStateSetOf to prevent the import/inference issues.
    val selectedCategories = remember {
        existingEntry.wasteCategories.toMutableStateList()
    }

    // ---- Collection time range ----
    var startTime by remember {
        val range = existingEntry.collectionTimeRange ?: ""
        val parts = if (range.contains("–")) range.split("–") else listOf("", "")
        mutableStateOf(parts.getOrNull(0)?.trim() ?: "")
    }
    var endTime by remember {
        val range = existingEntry.collectionTimeRange ?: ""
        val parts = if (range.contains("–")) range.split("–") else listOf("", "")
        mutableStateOf(parts.getOrNull(1)?.trim() ?: "")
    }

    // ---- Linked guide dropdown ----
    var guideDropdownOpen by remember { mutableStateOf(false) }
    var selectedGuide     by remember {
        mutableStateOf(guides.find { it.id == existingEntry.linkedGuideId })
    }

    // ---- Validation ----
    val categoriesError = selectedCategories.isEmpty()
    val canSave         = selectedCategories.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditMode) "Edit Schedule" else "New Schedule") },
        text  = {
            // Rule 16: wrap all content in verticalScroll for small screens
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // ============================================================
                // 1. DAY OF WEEK — single-select dropdown
                // ============================================================
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

                // ============================================================
                // 2. WASTE CATEGORIES — multi-select checkboxes (Rule 15)
                // ============================================================
                Text(
                    text  = "Waste Categories *",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (categoriesError)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )

                ALL_CATEGORIES.forEach { category ->
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
                            text  = category,
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

                // ============================================================
                // 3. COLLECTION TIME RANGE — two separate text fields
                // ============================================================
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
                        value         = startTime,
                        onValueChange = { startTime = it },
                        label         = { Text("Start") },
                        placeholder   = { Text("07:00") },
                        modifier      = Modifier.weight(1f),
                        singleLine    = true
                    )
                    OutlinedTextField(
                        value         = endTime,
                        onValueChange = { endTime = it },
                        label         = { Text("End") },
                        placeholder   = { Text("12:00") },
                        modifier      = Modifier.weight(1f),
                        singleLine    = true
                    )
                }

                // ============================================================
                // 4. LINKED GUIDE — dropdown of real guide titles (Phase 5)
                //    Replaces the raw Firestore-ID text field from before.
                // ============================================================
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
                        // "None" option removes any previously linked guide
                        DropdownMenuItem(
                            text    = { Text("None") },
                            onClick = {
                                selectedGuide     = null
                                guideDropdownOpen = false
                            }
                        )
                        // One row per guide
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

            } // end Column
        },   // end text lambda
        confirmButton = {
            TextButton(
                onClick = {
                    if (!canSave) return@TextButton

                    // Build the combined time range string, or null if either field is blank
                    val timeRange: String? = if (startTime.isNotBlank() && endTime.isNotBlank()) {
                        "${startTime.trim()} – ${endTime.trim()}"
                    } else {
                        null
                    }

                    onSave(
                        selectedDay,
                        selectedCategories.toList(),
                        timeRange,
                        selectedGuide?.id,
                        existingEntry.id          // blank = create, non-blank = update
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