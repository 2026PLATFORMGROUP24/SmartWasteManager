package com.platform.smartwastemanager.features.home.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platform.smartwastemanager.features.home.domain.CollectionDay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Home screen — shows the weekly collection schedule.
 *
 * User view:   Read-only cards. Tapping navigates to the linked guide (if any).
 * Driver view (driver-view active): Cards tap to the Zone List for that day.
 *              The right-side hint changes to "Manage Route" to make this clear.
 *              A FAB lets the driver navigate to ScheduleManagementScreen.
 *
 * @param onNavigateToZones Called with (scheduleDayId, scheduleDayName) when a driver
 *                          in driver view taps a schedule card.
 * @param isDriverInDriverView True when the current user is a driver AND is in driver view.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    isDriver: Boolean,
    onNavigateToManage: () -> Unit,
    onNavigateToGuide: (String) -> Unit,
    onNavigateToZones: (scheduleDayId: String, scheduleDayName: String) -> Unit = { _, _ -> },
    isDriverInDriverView: Boolean = false
) {
    val schedules          by viewModel.schedules.collectAsStateWithLifecycle()
    val isDriverViewActive by viewModel.isDriverViewActive.collectAsStateWithLifecycle()
    val uiState            by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (isDriver && isDriverViewActive) {
                FloatingActionButton(
                    onClick        = onNavigateToManage,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Manage Schedules",
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
                text       = "Collection Schedule",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(vertical = 16.dp)
            )

            if (schedules.isEmpty()) {
                Box(
                    modifier         = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text  = if (isDriver && isDriverViewActive)
                            "No schedules yet.\nTap + to add the first one."
                        else
                            "No schedules have been set yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(schedules) { schedule ->
                        ScheduleCard(
                            schedule             = schedule,
                            isDriverInDriverView = isDriverInDriverView,
                            onNavigateToGuide    = onNavigateToGuide,
                            onNavigateToZones    = onNavigateToZones
                        )
                    }
                }
            }
        }
    }
}

/**
 * A single schedule card.
 *
 * Click behaviour:
 *   • Driver in driver view → navigates to ZoneListScreen for this day.
 *   • Everyone else (users, drivers in user view) → guide behaviour (existing).
 *
 * The right-side hint label changes accordingly:
 *   • Driver in driver view : "Manage Route" with a Map icon.
 *   • Others               : "View Guide" with an arrow (existing).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleCard(
    schedule: CollectionDay,
    isDriverInDriverView: Boolean,
    onNavigateToGuide: (String) -> Unit,
    onNavigateToZones: (scheduleDayId: String, scheduleDayName: String) -> Unit
) {
    var showNoGuideDialog by remember { mutableStateOf(false) }

    val isCurrentlyActive = remember(schedule) { checkIfScheduleIsActive(schedule) }

    if (showNoGuideDialog) {
        AlertDialog(
            onDismissRequest = { showNoGuideDialog = false },
            title            = { Text("No Guide Available") },
            text             = {
                Text("There is no recycling guide linked to ${schedule.dayOfWeek}'s collection yet.")
            },
            confirmButton = {
                TextButton(onClick = { showNoGuideDialog = false }) { Text("OK") }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isDriverInDriverView) {
                    // Driver in driver view → open zone list for this schedule day
                    onNavigateToZones(schedule.id, schedule.dayOfWeek)
                } else {
                    // User or driver-in-user-view → guide behaviour (Rule 17)
                    if (schedule.linkedGuideId != null) {
                        onNavigateToGuide(schedule.linkedGuideId)
                    } else {
                        showNoGuideDialog = true
                    }
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentlyActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isCurrentlyActive)
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else
            null,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isCurrentlyActive) 8.dp else 2.dp
        )
    ) {
        Row(
            modifier                = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement   = Arrangement.SpaceBetween,
            verticalAlignment       = Alignment.CenterVertically
        ) {
            // ---- Left side: day, time range, category chips ----
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text       = schedule.dayOfWeek,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = if (isCurrentlyActive)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isCurrentlyActive) {
                        Surface(
                            shape    = MaterialTheme.shapes.extraSmall,
                            color    = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                text       = "NOW",
                                style      = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onPrimary,
                                modifier   = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (!schedule.collectionTimeRange.isNullOrBlank()) {
                    Text(
                        text     = "🕐 ${schedule.collectionTimeRange}",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = if (isCurrentlyActive)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                if (schedule.wasteCategories.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement   = Arrangement.spacedBy(4.dp)
                    ) {
                        schedule.wasteCategories.forEach { category ->
                            SuggestionChip(
                                onClick = {},
                                label   = {
                                    Text(category, style = MaterialTheme.typography.labelSmall)
                                }
                            )
                        }
                    }
                }
            }

            // ---- Right side: hint changes based on role+view ----
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier.padding(start = 8.dp)
            ) {
                if (isDriverInDriverView) {
                    // Driver view hint: "Manage Route"
                    Icon(
                        imageVector        = Icons.Default.Map,
                        contentDescription = "Manage Route",
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(20.dp)
                    )
                    Text(
                        text  = "Manage\nRoute",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    // User/user-view hint: "View Guide" (Rule 17 — always visible)
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "View Guide",
                        tint               = if (schedule.linkedGuideId != null)
                            MaterialTheme.colorScheme.primary
                        else
                            (if (isCurrentlyActive)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.4f),
                        modifier           = Modifier.size(20.dp)
                    )
                    Text(
                        text  = "View Guide",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (schedule.linkedGuideId != null)
                            MaterialTheme.colorScheme.primary
                        else
                            (if (isCurrentlyActive)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

private fun checkIfScheduleIsActive(schedule: CollectionDay): Boolean {
    return try {
        val calendar   = Calendar.getInstance()
        val currentDay = calendar.getDisplayName(
            Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()
        )
        if (!schedule.dayOfWeek.equals(currentDay, ignoreCase = true)) return false

        val range = schedule.collectionTimeRange ?: return false
        val parts = range.split("–")
        if (parts.size != 2) return false

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val startTime  = timeFormat.parse(parts[0].trim()) ?: return false
        val endTime    = timeFormat.parse(parts[1].trim()) ?: return false
        val currentTime = timeFormat.parse(timeFormat.format(calendar.time)) ?: return false

        !currentTime.before(startTime) && !currentTime.after(endTime)
    } catch (e: Exception) { false }
}