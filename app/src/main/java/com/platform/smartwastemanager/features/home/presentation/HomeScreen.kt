package com.platform.smartwastemanager.features.home.presentation

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platform.smartwastemanager.features.collectionpoint.domain.CollectionPoint
import com.platform.smartwastemanager.features.home.domain.CollectionDay
import com.platform.smartwastemanager.features.map.domain.Zone
import java.text.SimpleDateFormat
import java.util.*

/**
 * NEW ZONE-BASED HOME SCREEN
 *
 * USER VIEW:
 * - If no collection points → show empty state with "Create Collection Point" button
 * - If has points → show point selector dropdown + schedule for selected point's zone
 * - Each schedule day has "Mark for Collection" button
 *
 * DRIVER VIEW:
 * - Show zone selector dropdown
 * - Show schedules for selected zone
 * - Each schedule day is clickable → calculates route from marked points in that zone
 * - FAB to create new schedules for the selected zone
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    isDriver: Boolean,
    onNavigateToManage: () -> Unit,
    onNavigateToGuide: (String) -> Unit,
    onNavigateToZones: (scheduleDayId: String, scheduleDayName: String) -> Unit = { _, _ -> },
    onNavigateToCollectionPointPicker: () -> Unit,
    onNavigateToZoneManagement: () -> Unit,
    onCalculateRoute: (zoneId: String, scheduleDayId: String) -> Unit,
    isDriverInDriverView: Boolean = false
) {
    val collectionPoints   by viewModel.collectionPoints.collectAsStateWithLifecycle()
    val selectedPoint      by viewModel.selectedPoint.collectAsStateWithLifecycle()
    val zones              by viewModel.zones.collectAsStateWithLifecycle()
    val selectedZone       by viewModel.selectedZone.collectAsStateWithLifecycle()
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
            // USER: Create collection point button
            if (!isDriver || !isDriverViewActive) {
                FloatingActionButton(
                    onClick        = onNavigateToCollectionPointPicker,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector        = Icons.Default.Add,
                        contentDescription = "Add Collection Point",
                        tint               = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            // DRIVER: Create schedule for selected zone
            else if (selectedZone != null) {
                FloatingActionButton(
                    onClick        = onNavigateToManage,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector        = Icons.Default.Add,
                        contentDescription = "Add Schedule",
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
            Spacer(modifier = Modifier.height(16.dp))

            // ========== HEADER ==========
            Text(
                text       = "Collection Schedule",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ========== USER VIEW ==========
            if (!isDriver || !isDriverViewActive) {
                UserHomeContent(
                    collectionPoints = collectionPoints,
                    selectedPoint    = selectedPoint,
                    schedules        = schedules,
                    onSelectPoint    = { viewModel.selectCollectionPoint(it) },
                    onNavigateToCollectionPointPicker = onNavigateToCollectionPointPicker,
                    onNavigateToGuide = onNavigateToGuide,
                    onMarkForCollection = { pointId, scheduleDayId ->
                        viewModel.markPointForCollection(pointId, scheduleDayId)
                    },
                    onUnmarkFromCollection = { pointId, scheduleDayId ->
                        viewModel.unmarkPointFromCollection(pointId, scheduleDayId)
                    }
                )
            }
            // ========== DRIVER VIEW ==========
            else {
                DriverHomeContent(
                    zones             = zones,
                    selectedZone      = selectedZone,
                    schedules         = schedules,
                    onSelectZone      = { viewModel.selectZone(it) },
                    onNavigateToZoneManagement = onNavigateToZoneManagement,
                    onCalculateRoute  = onCalculateRoute
                )
            }
        }
    }
}

// =====================================================================
// USER VIEW CONTENT
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserHomeContent(
    collectionPoints: List<CollectionPoint>,
    selectedPoint: CollectionPoint?,
    schedules: List<CollectionDay>,
    onSelectPoint: (CollectionPoint) -> Unit,
    onNavigateToCollectionPointPicker: () -> Unit,
    onNavigateToGuide: (String) -> Unit,
    onMarkForCollection: (pointId: String, scheduleDayId: String) -> Unit,
    onUnmarkFromCollection: (pointId: String, scheduleDayId: String) -> Unit
) {
    // ========== EMPTY STATE ==========
    if (collectionPoints.isEmpty()) {
        Box(
            modifier         = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text       = "No Collection Points",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text  = "Create a collection point to view your zone's schedule and mark days for pickup.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onNavigateToCollectionPointPicker) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Collection Point")
                }
            }
        }
        return
    }

    // ========== COLLECTION POINT SELECTOR ==========
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded         = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value         = selectedPoint?.name ?: "Select collection point",
            onValueChange = {},
            readOnly      = true,
            label         = { Text("My Collection Point") },
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier      = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false }
        ) {
            collectionPoints.forEach { point ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(point.name, fontWeight = FontWeight.Bold)
                            Text(
                                point.streetName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onSelectPoint(point)
                        expanded = false
                    }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // ========== SCHEDULE LIST ==========
    if (schedules.isEmpty()) {
        Box(
            modifier         = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text  = "No schedule set for this zone yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding      = PaddingValues(bottom = 88.dp)
        ) {
            items(schedules, key = { it.id }) { schedule ->
                UserScheduleCard(
                    schedule        = schedule,
                    selectedPoint   = selectedPoint,
                    onNavigateToGuide = onNavigateToGuide,
                    onMarkForCollection = onMarkForCollection,
                    onUnmarkFromCollection = onUnmarkFromCollection
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserScheduleCard(
    schedule: CollectionDay,
    selectedPoint: CollectionPoint?,
    onNavigateToGuide: (String) -> Unit,
    onMarkForCollection: (pointId: String, scheduleDayId: String) -> Unit,
    onUnmarkFromCollection: (pointId: String, scheduleDayId: String) -> Unit
) {
    val isMarked = selectedPoint?.markedForCollectionDays?.contains(schedule.id) == true

    var showNoGuideDialog by remember { mutableStateOf(false) }

    if (showNoGuideDialog) {
        AlertDialog(
            onDismissRequest = { showNoGuideDialog = false },
            title            = { Text("No Guide Available") },
            text             = { Text("There is no recycling guide linked to ${schedule.dayOfWeek}'s collection yet.") },
            confirmButton    = { TextButton(onClick = { showNoGuideDialog = false }) { Text("OK") } }
        )
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(
            containerColor = if (isMarked)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isMarked) 4.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = schedule.dayOfWeek,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (!schedule.collectionTimeRange.isNullOrBlank()) {
                        Text(
                            text  = "🕐 ${schedule.collectionTimeRange}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // Mark/Unmark button
                if (selectedPoint != null) {
                    if (isMarked) {
                        IconButton(onClick = {
                            onUnmarkFromCollection(selectedPoint.id, schedule.id)
                        }) {
                            Icon(
                                imageVector        = Icons.Default.CheckCircle,
                                contentDescription = "Marked",
                                tint               = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Button(onClick = {
                            onMarkForCollection(selectedPoint.id, schedule.id)
                        }) {
                            Text("Mark for Collection")
                        }
                    }
                }
            }

            // Waste category chips
            if (schedule.wasteCategories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement   = Arrangement.spacedBy(4.dp)
                ) {
                    schedule.wasteCategories.forEach { category ->
                        SuggestionChip(
                            onClick = {},
                            label   = { Text(category, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            // View Guide link
            if (schedule.linkedGuideId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { onNavigateToGuide(schedule.linkedGuideId) }) {
                    Text("📖 View Recycling Guide")
                }
            }
        }
    }
}

// =====================================================================
// DRIVER VIEW CONTENT
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriverHomeContent(
    zones: List<Zone>,
    selectedZone: Zone?,
    schedules: List<CollectionDay>,
    onSelectZone: (Zone) -> Unit,
    onNavigateToZoneManagement: () -> Unit,
    onCalculateRoute: (zoneId: String, scheduleDayId: String) -> Unit
) {
    // ========== EMPTY STATE ==========
    if (zones.isEmpty()) {
        Box(
            modifier         = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text       = "No Zones Created",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text  = "Create zones first to manage collection schedules.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onNavigateToZoneManagement) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Manage Zones")
                }
            }
        }
        return
    }

    // ========== ZONE SELECTOR ==========
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded         = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value         = selectedZone?.name ?: "Select zone",
            onValueChange = {},
            readOnly      = true,
            label         = { Text("Zone") },
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier      = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false }
        ) {
            zones.forEach { zone ->
                DropdownMenuItem(
                    text    = { Text(zone.name) },
                    onClick = {
                        onSelectZone(zone)
                        expanded = false
                    }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // ========== SCHEDULE LIST ==========
    if (schedules.isEmpty()) {
        Box(
            modifier         = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text  = "No schedules for this zone yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text  = "Tap + to create a schedule.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding      = PaddingValues(bottom = 88.dp)
        ) {
            items(schedules, key = { it.id }) { schedule ->
                DriverScheduleCard(
                    schedule         = schedule,
                    onCalculateRoute = { onCalculateRoute(schedule.zoneId, schedule.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DriverScheduleCard(
    schedule: CollectionDay,
    onCalculateRoute: () -> Unit
) {
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .clickable { onCalculateRoute() },
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = schedule.dayOfWeek,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (!schedule.collectionTimeRange.isNullOrBlank()) {
                        Text(
                            text  = "🕐 ${schedule.collectionTimeRange}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Icon(
                    imageVector        = Icons.Default.Map,
                    contentDescription = "Calculate Route",
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(24.dp)
                )
            }

            // Waste category chips
            if (schedule.wasteCategories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement   = Arrangement.spacedBy(4.dp)
                ) {
                    schedule.wasteCategories.forEach { category ->
                        SuggestionChip(
                            onClick = {},
                            label   = { Text(category, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text  = "Tap to calculate route from marked collection points",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}