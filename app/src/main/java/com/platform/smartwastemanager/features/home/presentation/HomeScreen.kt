package com.platform.smartwastemanager.features.home.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platform.smartwastemanager.features.home.domain.CollectionDay

/**
 * Home screen — shows the weekly collection schedule.
 *
 * User view:   Read-only list of schedule cards.
 * Driver view: Same list + a FAB to navigate to the schedule management screen.
 *
 * The "Viewing as User" banner is shown globally in the top app bar (MainActivity),
 * NOT here — so it is visible on every screen in the app.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    isDriver: Boolean,
    onNavigateToManage: () -> Unit,
    onNavigateToGuide: (String) -> Unit
) {
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val isDriverViewActive by viewModel.isDriverViewActive.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
            // FAB only visible to drivers in driver view
            if (isDriver && isDriverViewActive) {
                FloatingActionButton(
                    onClick = onNavigateToManage,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Manage Schedules",
                        tint = MaterialTheme.colorScheme.onPrimary
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
                text = "Collection Schedule",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            if (schedules.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isDriver && isDriverViewActive)
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
                            schedule = schedule,
                            onNavigateToGuide = onNavigateToGuide
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
 * Always shows an arrow + "View Guide" label on the right side so users
 * know the card is tappable. If no guide is linked, tapping shows a dialog.
 *
 * Shows all waste categories as small chips.
 * Shows the collection time range if one has been set by the driver.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleCard(
    schedule: CollectionDay,
    onNavigateToGuide: (String) -> Unit
) {
    var showNoGuideDialog by remember { mutableStateOf(false) }

    if (showNoGuideDialog) {
        AlertDialog(
            onDismissRequest = { showNoGuideDialog = false },
            title = { Text("No Guide Available") },
            text = {
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
                if (schedule.linkedGuideId != null) {
                    onNavigateToGuide(schedule.linkedGuideId)
                } else {
                    showNoGuideDialog = true
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ---- Left side: day, time range, category chips ----
            Column(modifier = Modifier.weight(1f)) {
                // Day name
                Text(
                    text = schedule.dayOfWeek,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                // Optional time range (e.g. "07:00 – 12:00")
                if (!schedule.collectionTimeRange.isNullOrBlank()) {
                    Text(
                        text = "🕐 ${schedule.collectionTimeRange}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Waste category chips — shown for every category selected
                if (schedule.wasteCategories.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        schedule.wasteCategories.forEach { category ->
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = category,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // ---- Right side: always-visible "View Guide" arrow hint ----
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "View Guide",
                    tint = if (schedule.linkedGuideId != null)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "View Guide",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (schedule.linkedGuideId != null)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f)
                )
            }
        }
    }
}