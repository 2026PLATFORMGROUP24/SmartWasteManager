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
                    Text(
                        text       = "Collection Schedule: Driver",
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
    val now = currentDateTime.toLocalDate()
    val nowTime = currentDateTime.toLocalTime()
    val scheduleDay = remember(schedule.dayOfWeek) { schedule.dayOfWeek.toDayOfWeekLocal() }
    
    // Anchor to the current week (starting Monday) to correctly identify "previous" or "upcoming" days
    val targetDate = remember(scheduleDay, now) {
        scheduleDay?.let {
            val monday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            monday.plusDays((it.value - 1).toLong())
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

    // A day is passed if it was before today, or it's today and the end time has passed
    val isToday = daysDiff == 0L
    val isPassed = daysDiff < 0 || (isToday && endTime != null && nowTime.isAfter(endTime))
    
    // Logic for enabling "Ready for Collection":
    // - Enabled if it is TODAY (and not passed)
    // - Enabled for TOMORROW (daysDiff == 1) if current time is after 4 PM today
    val isEnabled = when {
        isPassed -> false
        isToday -> true
        daysDiff == 1L -> nowTime.hour >= 16
        else -> false
    }

    val isMarked = selectedPoint?.markedForCollectionDays?.contains(schedule.id) == true

    val badgeText = when {
        isToday -> if (isPassed) "PASSED" else "TODAY"
        daysDiff == 1L -> "TOMORROW"
        daysDiff > 1L -> "IN $daysDiff DAYS"
        else -> "PASSED"
    }

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
                if (isToday && !isPassed) Modifier.border(
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)),
                    shape = MaterialTheme.shapes.medium
                ) else Modifier
            )
            .clickable(enabled = schedule.linkedGuideId != null) {
                schedule.linkedGuideId?.let { onNavigateToGuide(it) }
            },
        colors    = CardDefaults.cardColors(
            containerColor = when {
                isToday && isMarked -> MaterialTheme.colorScheme.primaryContainer
                isToday && !isPassed -> MaterialTheme.colorScheme.tertiaryContainer
                isMarked -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                else -> MaterialTheme.colorScheme.secondaryContainer
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isToday && !isPassed) 6.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(schedule.dayOfWeek, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        badgeText.let { badge ->
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = if (isToday && !isPassed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(text = badge, style = MaterialTheme.typography.labelSmall, color = if (isToday && !isPassed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
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
            if (isEnabled && startTime != null) {
                val scheduleStart = targetDate.atTime(startTime)
                val scheduleEnd = endTime?.let { targetDate.atTime(it) }
                
                val timerText = when {
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
                        Icon(Icons.Default.Timer, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
                            enabled = !isPassed
                        ) { Text("Unmark") }
                    } else {
                        val buttonText = when {
                            isEnabled -> "Ready for Collection"
                            isPassed -> "Collection Ended"
                            daysDiff == 1L && nowTime.hour < 16 -> {
                                val openTime = LocalTime.of(16, 0)
                                val duration = Duration.between(nowTime, openTime)
                                "Opens in ${formatDuration(duration)}"
                            }
                            daysDiff >= 1L -> {
                                // Countdown to 4 PM of the day BEFORE the schedule day
                                val openDateTime = targetDate.minusDays(1).atTime(16, 0)
                                if (currentDateTime.isBefore(openDateTime)) {
                                    val duration = Duration.between(currentDateTime, openDateTime)
                                    "Opens in ${formatDuration(duration)}"
                                } else {
                                    "Ready for Collection"
                                }
                            }
                            else -> "Ready for Collection"
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
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60

    return when {
        hours > 0 -> {
            val hText = if (hours == 1L) "hour" else "hours"
            val mText = if (minutes == 1L) "minute" else "minutes"
            if (minutes > 0) {
                "$hours $hText $minutes $mText"
            } else {
                "$hours $hText"
            }
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
    onCalculateRoute: (zoneName: String, scheduleDayId: String) -> Unit
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
            val today = LocalDate.now().dayOfWeek
            val todayIndex = remember(schedules) {
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
                    DriverScheduleCard(schedule = schedules[page], selectedZone = selectedZone, onCalculateRoute = onCalculateRoute)
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
    onCalculateRoute: (zoneName: String, scheduleDayId: String) -> Unit
) {
    val now = remember { LocalDate.now() }
    val scheduleDay = remember(schedule.dayOfWeek) { schedule.dayOfWeek.toDayOfWeekLocal() }
    val collectionDate = remember(scheduleDay, now) { scheduleDay?.let { now.with(TemporalAdjusters.nextOrSame(it)) } }
    val daysUntilCollection = remember(collectionDate, now) { collectionDate?.let { ChronoUnit.DAYS.between(now, it).toInt() } }
    val isToday = daysUntilCollection == 0
    val badgeText = when {
        isToday -> "TODAY"
        daysUntilCollection == 1 -> "TOMORROW"
        daysUntilCollection != null && daysUntilCollection > 1 -> "IN $daysUntilCollection DAYS"
        else -> null
    }

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
                if (isToday) Modifier.border(
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = alpha)),
                    shape = MaterialTheme.shapes.medium
                ) else Modifier
            )
            .clickable(enabled = selectedZone != null) {
                selectedZone?.let { zone -> onCalculateRoute(zone.name, schedule.id) }
            },
        colors    = CardDefaults.cardColors(containerColor = if (isToday) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isToday) 6.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(schedule.dayOfWeek, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        badgeText?.let { badge ->
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(text = badge, style = MaterialTheme.typography.labelSmall, color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                    }
                    if (!schedule.collectionTimeRange.isNullOrBlank()) {
                        Text("🕐 ${schedule.collectionTimeRange}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
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
            Spacer(modifier = Modifier.height(8.dp))
            Text("Tap to calculate route", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
