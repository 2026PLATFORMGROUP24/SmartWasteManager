package com.platform.smartwastemanager.features.home.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
 * User view:    Read-only list of schedule cards.
 * Driver view:  Same list + a "Manage Schedules" FAB to navigate to the CRUD screen.
 *
 * @param viewModel         The HomeViewModel (shared with ScheduleManagementScreen).
 * @param isDriver          True if the signed-in user has the DRIVER role.
 * @param onNavigateToManage Called when the driver taps the FAB — go to management screen.
 * @param onNavigateToGuide  Called when user taps a card that has a linked guide.
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

    // Snackbar host to show success/error messages at the bottom of the screen
    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar when there's a success or error message
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
            // Only drivers in driver view see the "Manage Schedules" button
            if (isDriver && isDriverViewActive) {
                FloatingActionButton(
                    onClick = onNavigateToManage,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
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

            // ---- Header ----
            Text(
                text = "Collection Schedule",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            // ---- Driver view banner ----
            // Shown when a driver has switched to User View so they know
            if (isDriver && !isDriverViewActive) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = "👀 You are viewing as a User",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // ---- Schedule list or empty state ----
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
                            onClick = {
                                if (schedule.linkedGuideId != null) {
                                    onNavigateToGuide(schedule.linkedGuideId)
                                } else {
                                    // The snackbar will show via a state update is not needed here
                                    // We'll handle it inline with a dialog shown from the card
                                }
                            },
                            onNoGuide = {
                                // Show a snackbar message when no guide is linked
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * A single card in the schedule list.
 * Shows the day of week and waste category.
 * Tapping navigates to the linked guide or shows a "no guide" message.
 */
@Composable
fun ScheduleCard(
    schedule: CollectionDay,
    onClick: () -> Unit,
    onNoGuide: () -> Unit
) {
    // Local state to show the "no guide" dialog
    var showNoGuideDialog by remember { mutableStateOf(false) }

    if (showNoGuideDialog) {
        AlertDialog(
            onDismissRequest = { showNoGuideDialog = false },
            title = { Text("No Guide Available") },
            text = { Text("There is no recycling guide linked to ${schedule.dayOfWeek}'s collection yet.") },
            confirmButton = {
                TextButton(onClick = { showNoGuideDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (schedule.linkedGuideId != null) {
                    onClick()
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
            Column {
                Text(
                    text = schedule.dayOfWeek,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = schedule.wasteCategory,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Show a subtle indicator if this card links to a guide
            if (schedule.linkedGuideId != null) {
                Text(
                    text = "📖 View Guide",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}