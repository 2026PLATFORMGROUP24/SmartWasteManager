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
 * ManageZonesScreen — global zone CRUD for drivers.
 *
 * Drivers can see all zones they have created (globally, not per schedule day)
 * and delete them. Creating a new zone navigates to ZoneMapPickerScreen.
 *
 * This screen does NOT assign zones to schedule days — that is done in ZonePickerScreen.
 *
 * @param viewModel       RouteViewModel.
 * @param onNavigateBack  Pop back to the previous screen.
 * @param onCreateZone    Navigate to ZoneMapPickerScreen to draw a new zone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageZonesScreen(
    viewModel: RouteViewModel,
    onNavigateBack: () -> Unit,
    onCreateZone: () -> Unit
) {
    // Load ALL zones, not just the driver's zones
    LaunchedEffect(Unit) {
        viewModel.loadAllZones()  // This shows all zones in the system
    }

    val allZonesState by viewModel.allZonesState.collectAsStateWithLifecycle()
    val actionState   by viewModel.actionState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var zoneToDelete by remember { mutableStateOf<Zone?>(null) }
    var searchQuery by remember { mutableStateOf("") }

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

    // ---- Delete confirmation dialog ----
    if (zoneToDelete != null) {
        AlertDialog(
            onDismissRequest = { zoneToDelete = null },
            title = { Text("Delete Zone?") },
            text  = {
                Text(
                    "Permanently delete zone '${zoneToDelete!!.name}'?\n\n" +
                            "It will be removed from any schedule days it was assigned to."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGlobalZone(zoneToDelete!!.id)
                    zoneToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { zoneToDelete = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick        = onCreateZone,
                icon           = { Icon(Icons.Default.AddLocation, contentDescription = null) },
                text           = { Text("Create Zone") },
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
                modifier          = Modifier.padding(top = 16.dp, bottom = 8.dp)
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text       = "Global Zones",
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text  = "Create and manage collection zones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search zones") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
            )
            Spacer(modifier = Modifier.height(12.dp))

            // ---- Content ----
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
                    val filteredZones = if (searchQuery.isBlank()) {
                        state.zones
                    } else {
                        state.zones.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    }

                    if (filteredZones.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector        = Icons.Default.AddLocation,
                                    contentDescription = null,
                                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier           = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    if (state.zones.isEmpty()) "No zones created yet." else "No zones match your search.",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    if (state.zones.isEmpty()) {
                                        "Tap 'Create Zone' to draw your first collection area."
                                    } else {
                                        "Try a different search term."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding      = PaddingValues(bottom = 88.dp)
                        ) {
                            items(filteredZones, key = { it.id }) { zone ->
                                GlobalZoneCard(
                                    zone          = zone,
                                    onDeleteClick = { zoneToDelete = zone }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Card showing one global zone with its details and a delete button. */
@Composable
private fun GlobalZoneCard(
    zone: Zone,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector        = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text       = zone.name,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
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
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector        = Icons.Default.DeleteOutline,
                    contentDescription = "Delete zone",
                    tint               = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
