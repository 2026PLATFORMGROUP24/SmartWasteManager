package com.platform.smartwastemanager.features.home.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platform.smartwastemanager.features.collectionpoint.domain.CollectionPoint
import com.platform.smartwastemanager.features.home.domain.CollectionDay
import com.platform.smartwastemanager.features.map.domain.Zone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    isDriver: Boolean,
    onNavigateToManage: () -> Unit,
    onNavigateToGuide: (String) -> Unit,
    onNavigateToZones: (scheduleDayId: String, scheduleDayName: String) -> Unit = { _, _ -> },
    onNavigateToCollectionPointPicker: () -> Unit,
    onNavigateToZoneManagement: () -> Unit,
    onCalculateRoute: (zoneName: String, scheduleDayId: String) -> Unit,
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
            // Only show FAB for drivers to create schedules
            if (isDriver && isDriverViewActive && selectedZone != null) {
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

            Text(
                text       = "Collection Schedule",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

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
            } else {
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
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded         = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value         = selectedPoint?.name ?: if (collectionPoints.isEmpty()) "No collection points" else "Select collection point",
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

            if (collectionPoints.isNotEmpty()) {
                HorizontalDivider()
            }

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Add New Collection Point",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                onClick = {
                    expanded = false
                    onNavigateToCollectionPointPicker()
                }
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (selectedPoint == null && collectionPoints.isEmpty()) {
        Box(
            modifier         = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                    text  = "Select 'Add New Collection Point' from the dropdown above to get started.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else if (schedules.isEmpty()) {
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

            if (schedule.linkedGuideId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { onNavigateToGuide(schedule.linkedGuideId) }) {
                    Text("📖 View Recycling Guide")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriverHomeContent(
    zones: List<Zone>,
    selectedZone: Zone?,
    schedules: List<CollectionDay>,
    onSelectZone: (Zone) -> Unit,
    onNavigateToZoneManagement: () -> Unit,
    onCalculateRoute: (zoneName: String, scheduleDayId: String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded         = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value         = selectedZone?.name ?: if (zones.isEmpty()) "No zones" else "Select zone",
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

            if (zones.isNotEmpty()) {
                HorizontalDivider()
            }

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Add New Zone",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                onClick = {
                    expanded = false
                    onNavigateToZoneManagement()
                }
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (selectedZone == null && zones.isEmpty()) {
        Box(
            modifier         = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                    text  = "Select 'Add New Zone' from the dropdown above to get started.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else if (schedules.isEmpty()) {
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
                    selectedZone     = selectedZone,
                    onCalculateRoute = onCalculateRoute
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DriverScheduleCard(
    schedule: CollectionDay,
    selectedZone: Zone?,
    onCalculateRoute: (zoneName: String, scheduleDayId: String) -> Unit
) {
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .clickable(enabled = selectedZone != null) {
                selectedZone?.let { zone ->
                    onCalculateRoute(zone.name, schedule.id)
                }
            },
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
