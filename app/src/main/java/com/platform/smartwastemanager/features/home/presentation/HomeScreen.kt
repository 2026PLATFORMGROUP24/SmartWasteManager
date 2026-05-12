package com.platform.smartwastemanager.features.home.presentation

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platform.smartwastemanager.features.collectionpoint.domain.CollectionPoint
import com.platform.smartwastemanager.features.home.domain.CollectionDay
import com.platform.smartwastemanager.features.map.domain.Zone
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
    @Suppress("UNUSED_PARAMETER") isDriverInDriverView: Boolean = false
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

    // Live clock state to update countdowns and timers every second
    var currentDateTime by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentDateTime = LocalDateTime.now()
            delay(1000)
        }
    }

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
                    val title = if (selectedZone != null) "Schedule: ${selectedZone?.name}" else "Collection Schedule: Driver"
                    Text(
                        text       = title,
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                } else {
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
                        },
                        currentDateTime = currentDateTime
                    )
                } else {
                    DriverHomeContent(
                        zones             = zones,
                        selectedZone      = selectedZone,
                        schedules         = schedules,
                        onSelectZone      = { viewModel.selectZone(it) },
                        onNavigateToManageZones = onNavigateToManageZones,
                        onCalculateRoute  = onCalculateRoute,
                        onToggleManualEnable = { scheduleId, isEnabled ->
                            viewModel.toggleScheduleManualEnable(scheduleId, isEnabled)
                        },
                        currentDateTime = currentDateTime
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
    onUnmarkFromCollection: (pointId: String, scheduleDayId: String) -> Unit,
    currentDateTime: LocalDateTime
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
            modifier      = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false }
        ) {
            collectionPoints.forEach { point ->
                val isSelected = point.id == activePoint?.id
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(point.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                Text(point.streetName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (isSelected) {
                                Surface(color = Color(0xFF4CAF50), shape = RoundedCornerShape(4.dp)) {
                                    Text("SELECTED", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    },
                    onClick = { onSelectPoint(point); expanded = false }
                )
            }
            if (collectionPoints.isNotEmpty()) HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Manage Collection Points", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                },
                onClick = { expanded = false; onNavigateToManagePoints() }
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (activePoint == null && collectionPoints.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("No Collection Points", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("You haven't added any collection points yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onNavigateToManagePoints) { Text("Add Your First Point") }
            }
        }
    } else if (schedules.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No schedule set for this zone yet.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        key(schedules) {
            val scope = rememberCoroutineScope()
            val today = currentDateTime.toLocalDate().dayOfWeek
            val todayIndex = remember(schedules, today) {
                schedules.indexOfFirst { it.dayOfWeek.toDayOfWeekLocal() == today }
            }
            val initialPage = if (todayIndex != -1) todayIndex else 0
            val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { schedules.size })

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = {
                        if (todayIndex != -1) {
                            scope.launch { pagerState.animateScrollToPage(todayIndex) }
                        }
                    },
                    enabled = todayIndex != -1
                ) {
                    Icon(Icons.Default.Today, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Go to Today")
                }
            }

            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 48.dp),
                pageSpacing = 16.dp,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) { page ->
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                Box(
                    modifier = Modifier.graphicsLayer {
                        val scale = 0.9f + (1f - 0.9f) * (1f - pageOffset.coerceIn(0f, 1f))
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.7f + (1f - 0.7f) * (1f - pageOffset.coerceIn(0f, 1f))
                    }
                ) {
                    UserScheduleCard(
                        schedule = schedules[page],
                        selectedPoint = selectedPoint,
                        onNavigateToGuide = onNavigateToGuide,
                        onMarkForCollection = onMarkForCollection,
                        onUnmarkFromCollection = onUnmarkFromCollection,
                        currentDateTime = currentDateTime
                    )
                }
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
    onUnmarkFromCollection: (pointId: String, scheduleDayId: String) -> Unit,
    currentDateTime: LocalDateTime
) {
    // Always use South African time for all schedule logic
    val saZoneId = java.time.ZoneId.of("Africa/Johannesburg")
    val saDateTime = currentDateTime.atZone(java.time.ZoneId.systemDefault()).withZoneSameInstant(saZoneId).toLocalDateTime()
    val now = saDateTime.toLocalDate()
    val nowTime = saDateTime.toLocalTime()
    val scheduleDay = remember(schedule.dayOfWeek) { schedule.dayOfWeek.toDayOfWeekLocal() }
    
    // Anchor to the current week (starting Monday) to correctly identify "previous" or "upcoming" days
    val targetDate = remember(scheduleDay, now) {
        scheduleDay?.let {
            val todayDow = now.dayOfWeek
            if (todayDow == it) {
                now
            } else {
                now.with(TemporalAdjusters.next(it))
            }
        }
    }

    if (targetDate == null) return

    val daysDiff = ChronoUnit.DAYS.between(now, targetDate)
    
    // Parse collection time range (e.g., "07:00 – 12:00")
    val timeRangeParts = remember(schedule.collectionTimeRange) {
        schedule.collectionTimeRange?.split("–", "-", "—")?.map { it.trim() }
    }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("[H][HH]:mm") }
    val startTime = remember(timeRangeParts) {
        timeRangeParts?.getOrNull(0)?.let { try { LocalTime.parse(it, timeFormatter) } catch (e: Exception) { null } }
    }
    val endTime = remember(timeRangeParts) {
        timeRangeParts?.getOrNull(1)?.let { try { LocalTime.parse(it, timeFormatter) } catch (e: Exception) { null } }
    }

    // A day is passed if it was before today, or it's today and the end time has passed, or marked completed
    val isToday = daysDiff == 0L
    val isPassed = schedule.isRouteCompleted || daysDiff < 0 || (isToday && endTime != null && nowTime.isAfter(endTime))

    // Logic for enabling "Ready for Collection":
    // - Enabled if it is TODAY (and not passed)
    // - Enabled for TOMORROW (daysDiff == 1) if current time is after 4 PM today
    // - MANUALLY ENABLED by driver
    val isEnabled = !schedule.isRouteCompleted && (schedule.isManuallyEnabled || when {
        isPassed -> false
        isToday -> true
        daysDiff == 1L -> nowTime.hour > 16 || (nowTime.hour == 16 && nowTime.minute >= 0)
        else -> false
    })

    val isMarked = selectedPoint?.markedForCollectionDays?.contains(schedule.id) == true

    // Badge only for the current calendar day
    val badgeText = if (isToday) "TODAY" else null

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .then(
                if (isToday && !schedule.isRouteCompleted) Modifier.border(
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)),
                    shape = MaterialTheme.shapes.medium
                ) else Modifier
            )
            .clickable(enabled = schedule.linkedGuideId != null) {
                schedule.linkedGuideId?.let { onNavigateToGuide(it) }
            },
        colors    = CardDefaults.cardColors(
            containerColor = when {
                schedule.isRouteCompleted -> MaterialTheme.colorScheme.errorContainer
                isToday && isMarked -> MaterialTheme.colorScheme.primaryContainer
                isToday -> MaterialTheme.colorScheme.tertiaryContainer
                isMarked -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                else -> MaterialTheme.colorScheme.secondaryContainer
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isToday && !schedule.isRouteCompleted) 6.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(schedule.dayOfWeek, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        badgeText?.let { badge ->
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = if (schedule.isRouteCompleted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(text = badge, style = MaterialTheme.typography.labelSmall, color = if (schedule.isRouteCompleted) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                    }
                    if (!schedule.collectionTimeRange.isNullOrBlank()) {
                        Text("🕐 ${schedule.collectionTimeRange}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Icon(Icons.Default.Book, null, tint = if (schedule.linkedGuideId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(24.dp))
            }

            // Display timer if enabled and start/end times are available
            if (startTime != null) {
                val scheduleStart = targetDate.atTime(startTime)
                val scheduleEnd = endTime?.let { targetDate.atTime(it) }
                
                val timerText = when {
                    schedule.isRouteCompleted -> "Collection ended"
                    !isEnabled -> null
                    currentDateTime.isBefore(scheduleStart) -> {
                        val duration = Duration.between(currentDateTime, scheduleStart)
                        "Starts in ${formatDuration(duration)}"
                    }
                    scheduleEnd != null && currentDateTime.isBefore(scheduleEnd) -> {
                        val duration = Duration.between(currentDateTime, scheduleEnd)
                        "Ends in ${formatDuration(duration)}"
                    }
                    else -> null
                }
                
                timerText?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (schedule.isRouteCompleted) Icons.Default.EventBusy else Icons.Default.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (schedule.isRouteCompleted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (schedule.isRouteCompleted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (schedule.wasteCategories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    schedule.wasteCategories.forEach { category -> SuggestionChip(onClick = {}, label = { Text(category, style = MaterialTheme.typography.labelSmall) }) }
                }
            }
            if (selectedPoint != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (isMarked) {
                        OutlinedButton(
                            onClick = { onUnmarkFromCollection(selectedPoint.id, schedule.id) },
                            modifier = Modifier.weight(1f),
                            enabled = !isPassed || schedule.isManuallyEnabled
                        ) { Text("Unmark") }
                    } else {
                        val buttonText = when {
                            schedule.isRouteCompleted -> "Collection Ended"
                            isEnabled -> "Ready for Collection"
                            else -> {
                                // Find the next open date-time in South African time
                                val baseOpenDateTime = targetDate.minusDays(1).atTime(16, 0)
                                val nextOpenDateTime = if (isPassed) {
                                    baseOpenDateTime.plusWeeks(1)
                                } else {
                                    baseOpenDateTime
                                }
                                var duration = Duration.between(saDateTime, nextOpenDateTime)
                                if (duration.isNegative) duration = Duration.ZERO
                                "Opens in ${formatDuration(duration)}"
                            }
                        }

                        Button(
                            onClick = { onMarkForCollection(selectedPoint.id, schedule.id) },
                            modifier = Modifier.weight(1f),
                            enabled = isEnabled
                        ) {
                            Text(buttonText)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = if (schedule.linkedGuideId != null) "Tap to view recycling guide" else "No recycling guide available", style = MaterialTheme.typography.labelSmall, color = if (schedule.linkedGuideId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatDuration(duration: Duration): String {
    val totalSeconds = duration.seconds
    if (totalSeconds <= 0) return "0 minutes"
    val days = totalSeconds / (3600 * 24)
    val hours = (totalSeconds % (3600 * 24)) / 3600
    val minutes = (totalSeconds % 3600) / 60

    return when {
        days > 0 -> {
            val dText = if (days == 1L) "day" else "days"
            val hText = if (hours == 1L) "hour" else "hours"
            if (hours > 0) "$days $dText $hours $hText" else "$days $dText"
        }
        hours > 0 -> {
            val hText = if (hours == 1L) "hour" else "hours"
            val mText = if (minutes == 1L) "minute" else "minutes"
            if (minutes > 0) "$hours $hText $minutes $mText" else "$hours $hText"
        }
        else -> {
            val mText = if (minutes == 1L) "minute" else "minutes"
            "$minutes $mText"
        }
    }
}

private fun String.toDayOfWeekLocal(): DayOfWeek? {
    val normalized = trim()
    if (normalized.isBlank()) return null
    return DayOfWeek.entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) || it.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()).equals(normalized, ignoreCase = true) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriverHomeContent(
    zones: List<Zone>,
    selectedZone: Zone?,
    schedules: List<CollectionDay>,
    onSelectZone: (Zone) -> Unit,
    onNavigateToManageZones: () -> Unit,
    onCalculateRoute: (zoneName: String, scheduleDayId: String) -> Unit,
    onToggleManualEnable: (scheduleId: String, isEnabled: Boolean) -> Unit,
    currentDateTime: LocalDateTime
) {
    var expanded by remember { mutableStateOf(false) }
    val activeZone = selectedZone ?: zones.firstOrNull()

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = activeZone?.name ?: if (zones.isEmpty()) "No zones" else "Select zone",
            onValueChange = {},
            readOnly = true,
            label = { Text("Active Zone") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            zones.forEach { zone ->
                val isSelected = zone.id == activeZone?.id
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Map, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(zone.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                            if (isSelected) {
                                Surface(color = Color(0xFF4CAF50), shape = RoundedCornerShape(4.dp)) {
                                    Text("SELECTED", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    },
                    onClick = { onSelectZone(zone); expanded = false }
                )
            }
            if (zones.isNotEmpty()) HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Manage Zones", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                },
                onClick = { expanded = false; onNavigateToManageZones() }
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (activeZone == null && zones.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Map, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("No Zones Created", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Select 'Add New Zone' from the dropdown to start.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else if (schedules.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No schedules for this zone yet.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Tap + to create a schedule.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        key(schedules) {
            val scope = rememberCoroutineScope()
            val today = currentDateTime.toLocalDate().dayOfWeek
            val todayIndex = remember(schedules, today) {
                schedules.indexOfFirst { it.dayOfWeek.toDayOfWeekLocal() == today }
            }
            val initialPage = if (todayIndex != -1) todayIndex else 0
            val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { schedules.size })

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = { if (todayIndex != -1) scope.launch { pagerState.animateScrollToPage(todayIndex) } },
                    enabled = todayIndex != -1
                ) {
                    Icon(Icons.Default.Today, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Go to Today")
                }
            }

            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 48.dp),
                pageSpacing = 16.dp,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) { page ->
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                Box(
                    modifier = Modifier.graphicsLayer {
                        val scale = 0.9f + (1f - 0.9f) * (1f - pageOffset.coerceIn(0f, 1f))
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.7f + (1f - 0.7f) * (1f - pageOffset.coerceIn(0f, 1f))
                    }
                ) {
                    DriverScheduleCard(
                        schedule = schedules[page],
                        selectedZone = selectedZone,
                        onCalculateRoute = onCalculateRoute,
                        onToggleManualEnable = onToggleManualEnable,
                        currentDateTime = currentDateTime
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DriverScheduleCard(
    schedule: CollectionDay,
    selectedZone: Zone?,
    onCalculateRoute: (zoneName: String, scheduleDayId: String) -> Unit,
    onToggleManualEnable: (scheduleId: String, isEnabled: Boolean) -> Unit,
    currentDateTime: LocalDateTime
) {
    val now = currentDateTime.toLocalDate()
    val nowTime = currentDateTime.toLocalTime()
    val scheduleDay = remember(schedule.dayOfWeek) { schedule.dayOfWeek.toDayOfWeekLocal() }
    
    val targetDate = remember(scheduleDay, now) {
        scheduleDay?.let {
            val monday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            monday.plusDays((it.value - 1).toLong())
        }
    }

    if (targetDate == null) return

    val daysDiff = ChronoUnit.DAYS.between(now, targetDate)
    
    val timeRangeParts = remember(schedule.collectionTimeRange) {
        schedule.collectionTimeRange?.split("–", "-", "—")?.map { it.trim() }
    }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("[H][HH]:mm") }
    val startTime = remember(timeRangeParts) {
        timeRangeParts?.getOrNull(0)?.let { try { LocalTime.parse(it, timeFormatter) } catch (e: Exception) { null } }
    }
    val endTime = remember(timeRangeParts) {
        timeRangeParts?.getOrNull(1)?.let { try { LocalTime.parse(it, timeFormatter) } catch (e: Exception) { null } }
    }

    val isToday = daysDiff == 0L
    val isPassed = schedule.isRouteCompleted || daysDiff < 0 || (isToday && endTime != null && nowTime.isAfter(endTime))

    // Badge only for the current calendar day
    val badgeText = if (isToday) "TODAY" else null

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .then(
                if (isToday && !schedule.isRouteCompleted) Modifier.border(
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = alpha)),
                    shape = MaterialTheme.shapes.medium
                ) else Modifier
            ),
        colors    = CardDefaults.cardColors(
            containerColor = when {
                schedule.isRouteCompleted -> MaterialTheme.colorScheme.errorContainer
                isToday -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.secondaryContainer
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isToday && !schedule.isRouteCompleted) 6.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(schedule.dayOfWeek, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        badgeText?.let { badge ->
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = if (schedule.isRouteCompleted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(text = badge, style = MaterialTheme.typography.labelSmall, color = if (schedule.isRouteCompleted) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                    }
                    if (!schedule.collectionTimeRange.isNullOrBlank()) {
                        Text("🕐 ${schedule.collectionTimeRange}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                    }

                    // Show "Ends in" or "Collection ended" timer
                    if (startTime != null) {
                        val scheduleEnd = endTime?.let { targetDate.atTime(it) }
                        val isScheduleStarted = isToday && !nowTime.isBefore(startTime) && !isPassed
                        
                        val timerText = when {
                            schedule.isRouteCompleted -> "Collection ended"
                            isScheduleStarted && scheduleEnd != null -> "Ends in ${formatDuration(Duration.between(currentDateTime, scheduleEnd))}"
                            else -> null
                        }

                        timerText?.let {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Icon(
                                    imageVector = if (schedule.isRouteCompleted) Icons.Default.EventBusy else Icons.Default.Timer,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (schedule.isRouteCompleted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (schedule.isRouteCompleted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Icon(Icons.Default.Map, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
            if (schedule.wasteCategories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    schedule.wasteCategories.forEach { category -> SuggestionChip(onClick = {}, label = { Text(category, style = MaterialTheme.typography.labelSmall) }) }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Manual Enable", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("Force 'Ready' for users", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = schedule.isManuallyEnabled,
                    onCheckedChange = { onToggleManualEnable(schedule.id, it) },
                    thumbContent = if (schedule.isManuallyEnabled) {
                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                    } else null,
                    enabled = !schedule.isRouteCompleted
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            
                    val isOpen = isToday && (startTime == null || !nowTime.isBefore(startTime)) && !isPassed
                    val isEnded = schedule.isRouteCompleted || (isToday && endTime != null && nowTime.isAfter(endTime))

                    Button(
                        onClick = { selectedZone?.let { zone -> onCalculateRoute(zone.name, schedule.id) } },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isOpen && selectedZone != null && !schedule.isRouteCompleted,
                        colors = if (schedule.isRouteCompleted) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                    ) {
                        val scheduleStart = targetDate.atTime(startTime ?: LocalTime.MIN)
                        val nextStart = if (isPassed && !schedule.isRouteCompleted) scheduleStart.plusWeeks(1) else scheduleStart

                        val timerText = when {
                            schedule.isRouteCompleted -> "Collection Ended"
                            isOpen -> "Calculate Route"
                            isEnded -> "Schedule Ended"
                            currentDateTime.isBefore(nextStart) -> "Schedule Starts in ${formatDuration(Duration.between(currentDateTime, nextStart))}"
                            else -> "Schedule Ended"
                        }
                        Text(timerText)
                    }
        }
    }
}
