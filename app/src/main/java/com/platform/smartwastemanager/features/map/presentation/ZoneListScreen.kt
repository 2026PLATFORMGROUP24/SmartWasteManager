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
import com.platform.smartwastemanager.features.home.domain.CollectionDay
import com.platform.smartwastemanager.features.map.domain.Zone

/**
 * ZoneListScreen — shown when a driver taps a schedule card on HomeScreen.
 *
 * Shows the zones currently assigned to this schedule day.
 * The driver can:
 *   - Unassign a zone from this day (zone is NOT deleted globally).
 *   - Tap "Assign Zone" FAB to open ZonePickerScreen and pick from global zones.
 *   - Tap "Manage Zones" to open ManageZonesScreen (global zone create/delete).
 *   - Tap "Load Route" on a zone to navigate to ActiveRouteScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneListScreen(
    viewModel: RouteViewModel,
    schedule: CollectionDay,
    onNavigateBack: () -> Unit,
    onAssignZone: () -> Unit,
    onManageZones: () -> Unit,
    onLoadRoute: (Zone) -> Unit
) {
    // Load the zones assigned to this schedule day when the screen opens
    LaunchedEffect(schedule.id) {
        viewModel.loadZonesForSchedule(schedule)
    }

    val zoneListState by viewModel.zoneListUiState.collectAsStateWithLifecycle()
    val actionState   by viewModel.actionState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Zone pending unassignment confirmation
    var zoneToUnassign by remember { mutableStateOf<Zone?>(null) }

    // Show feedback snackbars
    LaunchedEffect(actionState) {
        when (actionState) {
            is RouteActionState.Success -> {
                snackbarHostState.showSnackbar((actionState as RouteActionState.Success).message)
                viewModel.resetActionState()
            }
            is RouteActionState.Error -> {
                snackbarHostState.showSnackbar((actionState as RouteActionState.Error).message)
                viewModel.resetActionState()
            }
            else -> {}
        }
    }

    // ---- Unassign confirmation dialog ----
    if (zoneToUnassign != null) {
        AlertDialog(
            onDismissRequest = { zoneToUnassign = null },
            title = { Text("Remove Zone?") },
            text  = {
                Text(
                    "Remove '${zoneToUnassign!!.name}' from ${schedule.dayOfWeek}?\n\n" +
                            "The zone itself will NOT be deleted — it can be re-assigned later."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.unassignZoneFromSchedule(zoneToUnassign!!.id)
                    zoneToUnassign = null
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { zoneToUnassign = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick        = onAssignZone,
                icon           = { Icon(Icons.Default.AddLocation, contentDescription = null) },
                text           = { Text("Assign Zone") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor   = MaterialTheme.colorScheme.onPrimary
            )
        }
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
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = "${schedule.dayOfWeek} — Collection Zones",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text  = "Zones assigned to this collection day",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---- "Manage Zones" button — navigates to global zone CRUD ----
            OutlinedButton(
                onClick  = onManageZones,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Icon(Icons.Default.EditLocation, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Manage Global Zones")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ---- Content ----
            when (val state = zoneListState) {

                is ZoneListUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is ZoneListUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }

                is ZoneListUiState.Success -> {
                    if (state.zones.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.AddLocation,
                                    contentDescription = null,
                                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No zones assigned yet.",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Tap 'Assign Zone' to link a zone to ${schedule.dayOfWeek}.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding      = PaddingValues(bottom = 88.dp)
                        ) {
                            items(state.zones, key = { it.id }) { zone ->
                                AssignedZoneCard(
                                    zone          = zone,
                                    actionState   = actionState,
                                    onLoadRoute   = { onLoadRoute(zone) },
                                    onUnassign    = { zoneToUnassign = zone }
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
 * Card for a zone assigned to the current schedule day.
 * Shows zone name, radius, coordinates, a Load Route button, and an unassign button.
 */
@Composable
private fun AssignedZoneCard(
    zone: Zone,
    actionState: RouteActionState,
    onLoadRoute: () -> Unit,
    onUnassign: () -> Unit
) {
    val isLoading = actionState is RouteActionState.Loading

    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Zone name row + unassign button
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector        = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text       = zone.name,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                // Unassign = remove from this day (not a global delete)
                IconButton(onClick = onUnassign) {
                    Icon(
                        imageVector        = Icons.Default.LinkOff,
                        contentDescription = "Remove from this day",
                        tint               = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text  = "📍 ${String.format("%.4f", zone.centerLat)}, " +
                        "${String.format("%.4f", zone.centerLng)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text  = "⭕ Radius: ${zone.radiusMeters.toInt()} m",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick  = onLoadRoute,
                modifier = Modifier.fillMaxWidth(),
                enabled  = !isLoading,
                colors   = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Route, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Load Route")
            }
        }
    }
}