package com.platform.smartwastemanager.features.home.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platform.smartwastemanager.features.collectionpoint.domain.CollectionPoint
import com.platform.smartwastemanager.features.home.domain.CollectionDay
import com.platform.smartwastemanager.features.map.domain.Zone
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    isDriver: Boolean,
    onNavigateToManage: () -> Unit,
    onNavigateToGuide: (String) -> Unit,
    onNavigateToZones: (scheduleDayId: String, scheduleDayName: String) -> Unit = { _, _ -> },
    onNavigateToManagePoints: () -> Unit,
    onNavigateToManageZones: () -> Unit,
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
    var isRefreshing by remember { mutableStateOf(false) }

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
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.refreshCurrentView()
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                if (isDriver && isDriverViewActive) {

                    Text(
                        text       = "Collection Schedule: Driver",
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                else {
                    Text(
                        text       = "Collection Schedule",
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }


                Spacer(modifier = Modifier.height(12.dp))

                if (!isDriver || !isDriverViewActive) {
                    UserHomeContent(
                        collectionPoints = collectionPoints,
                        selectedPoint    = selectedPoint,
                        schedules        = schedules,
                        onSelectPoint    = { viewModel.selectCollectionPoint(it) },
                        onNavigateToManagePoints = onNavigateToManagePoints,
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
                        onNavigateToManageZones = onNavigateToManageZones,
                        onCalculateRoute  = onCalculateRoute
                    )
                }
            }
        }
        LaunchedEffect(isRefreshing) {
            if (isRefreshing) {
                delay(700)
                isRefreshing = false
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
    onNavigateToManagePoints: () -> Unit,
    onNavigateToGuide: (String) -> Unit,
    onMarkForCollection: (pointId: String, scheduleDayId: String) -> Unit,
    onUnmarkFromCollection: (pointId: String, scheduleDayId: String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val activePoint = selectedPoint ?: collectionPoints.firstOrNull()

    ExposedDropdownMenuBox(
        expanded         = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value         = activePoint?.name ?: if (collectionPoints.isEmpty()) "No collection points" else "Select collection point",
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
            activePoint?.let { point ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(point.name, fontWeight = FontWeight.Bold)
                                Text(
                                    point.streetName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    onClick = {
                        onSelectPoint(point)
                        expanded = false
                    }
                )
                HorizontalDivider()
            }

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Manage Collection Points",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                onClick = {
                    expanded = false
                    onNavigateToManagePoints()
                }
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (activePoint == null && collectionPoints.isEmpty()) {
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
                    text  = "You haven't added any collection points yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onNavigateToManagePoints) {
                    Text("Add Your First Point")
                }
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
    val now = remember { LocalDate.now() }
    val scheduleDay = remember(schedule.dayOfWeek) { schedule.dayOfWeek.toDayOfWeekOrNull() }
    val collectionDate = remember(scheduleDay, now) { scheduleDay?.let { now.with(TemporalAdjusters.nextOrSame(it)) } }
    val daysUntilCollection = remember(collectionDate, now) { collectionDate?.let { ChronoUnit.DAYS.between(now, it).toInt() } }
    val isMarked = selectedPoint?.markedForCollectionDays?.contains(schedule.id) == true
    val isToday = daysUntilCollection == 0

    val badgeText = when {
        isToday -> "TODAY"
        daysUntilCollection == 1 -> "TOMORROW"
        daysUntilCollection != null && daysUntilCollection > 1 -> "IN $daysUntilCollection DAYS"
        else -> null
    }

    val containerColor = when {
        isToday && isMarked -> MaterialTheme.colorScheme.primaryContainer
        isToday -> MaterialTheme.colorScheme.tertiaryContainer
        isMarked -> Color(0xFF4CAF50).copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .clickable(enabled = schedule.linkedGuideId != null) {
                schedule.linkedGuideId?.let { onNavigateToGuide(it) }
            },
        colors    = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isToday) 4.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text       = schedule.dayOfWeek,
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        badgeText?.let { badge ->
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = if (isToday) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                },
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isToday) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    if (!schedule.collectionTimeRange.isNullOrBlank()) {
                        Text(
                            text  = "🕐 ${schedule.collectionTimeRange}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Icon(
                    imageVector        = Icons.Default.Book,
                    contentDescription = "Recycling Guide",
                    tint               = if (schedule.linkedGuideId != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
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

            // Mark/Unmark buttons
            if (selectedPoint != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isMarked) {
                        OutlinedButton(
                            onClick = { onUnmarkFromCollection(selectedPoint.id, schedule.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Unmark")
                        }
                    } else {
                        Button(
                            onClick = { onMarkForCollection(selectedPoint.id, schedule.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Ready for Collection")
                        }
                    }
                }
            }

            // Guide hint at the bottom
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text  = if (schedule.linkedGuideId != null) {
                    "Tap to view recycling guide for this day"
                } else {
                    "No recycling guide available"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (schedule.linkedGuideId != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

private fun String.toDayOfWeekOrNull(): DayOfWeek? {
    val normalized = trim()
    if (normalized.isBlank()) return null

    return DayOfWeek.entries.firstOrNull { day ->
        day.name.equals(normalized, ignoreCase = true) ||
                day.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
                    .equals(normalized, ignoreCase = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriverHomeContent(
    zones: List<Zone>,
    selectedZone: Zone?,
    schedules: List<CollectionDay>,
    onSelectZone: (Zone) -> Unit,
    onNavigateToManageZones: () -> Unit,
    onCalculateRoute: (zoneName: String, scheduleDayId: String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val activeZone = selectedZone ?: zones.firstOrNull()

    ExposedDropdownMenuBox(
        expanded         = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value         = activeZone?.name ?: if (zones.isEmpty()) "No zones" else "Select zone",
            onValueChange = {},
            readOnly      = true,
            label         = { Text("Active Zone") },
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier      = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false }
        ) {
            activeZone?.let { zone ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Map, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(zone.name, fontWeight = FontWeight.Bold)
                        }
                    },
                    onClick = {
                        onSelectZone(zone)
                        expanded = false
                    }
                )
                HorizontalDivider()
            }

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Manage Zones",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                onClick = {
                    expanded = false
                    onNavigateToManageZones()
                }
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (activeZone == null && zones.isEmpty()) {
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
    val now = remember { LocalDate.now() }
    val scheduleDay = remember(schedule.dayOfWeek) { schedule.dayOfWeek.toDayOfWeekOrNull() }
    val collectionDate = remember(scheduleDay, now) { scheduleDay?.let { now.with(TemporalAdjusters.nextOrSame(it)) } }
    val daysUntilCollection = remember(collectionDate, now) { collectionDate?.let { ChronoUnit.DAYS.between(now, it).toInt() } }
    val isToday = daysUntilCollection == 0
    val badgeText = when {
        isToday -> "TODAY"
        daysUntilCollection == 1 -> "TOMORROW"
        daysUntilCollection != null && daysUntilCollection > 1 -> "IN $daysUntilCollection DAYS"
        else -> null
    }

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .clickable(enabled = selectedZone != null) {
                selectedZone?.let { zone ->
                    onCalculateRoute(zone.name, schedule.id)
                }
            },
        colors    = CardDefaults.cardColors(
            containerColor = if (isToday) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isToday) 4.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text       = schedule.dayOfWeek,
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        badgeText?.let { badge ->
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = if (isToday) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                },
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isToday) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
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