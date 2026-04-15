package com.platform.smartwastemanager.features.map.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platform.smartwastemanager.features.map.domain.Zone

/**
 * ZonePickerScreen — lets a driver pick from ALL global zones to assign to a schedule day.
 *
 * Shows every zone that exists in the route_zones collection.
 * Tapping "Assign" on a zone adds it to the current schedule day's zoneIds list.
 * Already-assigned zones show a disabled "Assigned" chip instead of a button.
 *
 * @param viewModel          RouteViewModel (created at Activity level — Rule 3).
 * @param alreadyAssignedIds The zoneIds already on the current schedule day.
 * @param scheduleDayName    Human-readable day name shown in the subtitle.
 * @param onNavigateBack     Pop back to ZoneListScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZonePickerScreen(
    viewModel: RouteViewModel,
    alreadyAssignedIds: List<String>,
    scheduleDayName: String,
    onNavigateBack: () -> Unit
) {
    // Load all global zones when the screen opens
    LaunchedEffect(Unit) {
        viewModel.loadAllZones()
    }

    val allZonesState     by viewModel.allZonesState.collectAsStateWithLifecycle()
    val actionState       by viewModel.actionState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // After a successful assignment navigate back automatically
    LaunchedEffect(actionState) {
        when (actionState) {
            is RouteActionState.Success -> {
                snackbarHostState.showSnackbar(
                    (actionState as RouteActionState.Success).message
                )
                viewModel.resetActionState()
                onNavigateBack()
            }
            is RouteActionState.Error -> {
                snackbarHostState.showSnackbar(
                    (actionState as RouteActionState.Error).message
                )
                viewModel.resetActionState()
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {

            // ---- Header ----
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier.padding(top = 16.dp, bottom = 8.dp)
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text       = "Assign a Zone",
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text  = "Select a zone to add to $scheduleDayName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---- Zone list ----
            when (val state = allZonesState) {

                is AllZonesUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is AllZonesUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }

                is AllZonesUiState.Success -> {
                    if (state.zones.isEmpty()) {
                        // No global zones exist yet
                        Box(
                            modifier         = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector        = Icons.Default.LocationOff,
                                    contentDescription = null,
                                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier           = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text  = "No global zones exist yet.",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text  = "Go to 'Manage Global Zones' to create one first.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding      = PaddingValues(bottom = 24.dp)
                        ) {
                            items(state.zones, key = { it.id }) { zone ->
                                val isAlreadyAssigned = zone.id in alreadyAssignedIds
                                ZonePickerCard(
                                    zone              = zone,
                                    isAlreadyAssigned = isAlreadyAssigned,
                                    isLoading         = actionState is RouteActionState.Loading,
                                    onAssign          = { viewModel.assignZoneToSchedule(zone) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card showing one global zone in the picker list.
 * Shows an "Assign" button if not yet assigned, or a greyed "✓ Assigned" badge if already on this day.
 */
@Composable
private fun ZonePickerCard(
    zone: Zone,
    isAlreadyAssigned: Boolean,
    isLoading: Boolean,
    onAssign: () -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (isAlreadyAssigned)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Zone info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector        = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint               = if (isAlreadyAssigned)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text       = zone.name,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color      = if (isAlreadyAssigned)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text  = "⭕ ${zone.radiusMeters.toInt()} m  •  " +
                            "📍 ${String.format("%.3f", zone.centerLat)}, " +
                            "${String.format("%.3f", zone.centerLng)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (isAlreadyAssigned) {
                // Static "already assigned" badge
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text     = "✓ Assigned",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else {
                Button(
                    onClick  = onAssign,
                    enabled  = !isLoading,
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Assign")
                }
            }
        }
    }
}