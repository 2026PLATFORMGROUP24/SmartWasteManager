package com.platform.smartwastemanager.features.home.presentation

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.platform.smartwastemanager.features.home.domain.CollectionDay
import java.text.SimpleDateFormat
import java.util.*

// ---------------------------------------------------------------------------
// Schedule activity status — three levels of "how relevant is this today"
// ---------------------------------------------------------------------------

/**
 * Describes how relevant a schedule card is relative to the current time.
 *
 * [ACTIVE_NOW]  — Correct day AND currently inside the collection time window.
 *                 Shown with a pulsing green indicator and "ACTIVE NOW" badge.
 *
 * [TODAY]       — Correct day, but either: no time range set, time hasn't started
 *                 yet, or collection window already ended today.
 *                 Shown with an amber indicator and "TODAY" badge + status sub-label.
 *
 * [UPCOMING]    — A future collection day (not today).
 *                 Shown in normal style with a "In X days" hint.
 */
enum class ScheduleStatus { ACTIVE_NOW, TODAY, UPCOMING }

/**
 * Holds the computed status and human-readable sub-labels for a schedule card.
 *
 * @param status        The activity level (see [ScheduleStatus]).
 * @param timeLabel     Short label shown next to the time range, e.g. "Starts in 2h 15m".
 * @param daysUntil     How many days until this collection day (0 = today, 1 = tomorrow…).
 */
data class ScheduleActivityInfo(
    val status: ScheduleStatus,
    val timeLabel: String,
    val daysUntil: Int
)

// ---------------------------------------------------------------------------
// HomeScreen
// ---------------------------------------------------------------------------

/**
 * Home screen — shows the weekly collection schedule.
 *
 * The schedule list is automatically scrolled so today's card (or the next
 * upcoming one) is visible near the top when the screen opens.
 *
 * User view:   Read-only cards. Tapping navigates to the linked guide (if any).
 * Driver view: Same list + FAB to ScheduleManagementScreen.
 *              Card taps go to ZoneListScreen.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    isDriver: Boolean,
    onNavigateToManage: () -> Unit,
    onNavigateToGuide: (String) -> Unit,
    onNavigateToZones: (scheduleDayId: String, scheduleDayName: String) -> Unit = { _, _ -> },
    isDriverInDriverView: Boolean = false
) {
    val schedules          by viewModel.schedules.collectAsStateWithLifecycle()
    val isDriverViewActive by viewModel.isDriverViewActive.collectAsStateWithLifecycle()
    val uiState            by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val listState         = rememberLazyListState()

    // Compute activity info for every schedule outside the list so we can
    // find today's index for auto-scroll.
    val activityInfos = remember(schedules) {
        schedules.map { computeActivityInfo(it) }
    }

    // Auto-scroll to the first TODAY/ACTIVE_NOW card when the list loads.
    LaunchedEffect(schedules) {
        if (schedules.isEmpty()) return@LaunchedEffect
        val todayIndex = activityInfos.indexOfFirst {
            it.status == ScheduleStatus.ACTIVE_NOW || it.status == ScheduleStatus.TODAY
        }
        if (todayIndex >= 0) {
            // Scroll so the today card is fully visible with a little breathing room above.
            listState.animateScrollToItem(
                index = (todayIndex - 1).coerceAtLeast(0)
            )
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
            if (isDriver && isDriverViewActive) {
                FloatingActionButton(
                    onClick        = onNavigateToManage,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Manage Schedules",
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

            // ---- Header row: title + today's date ----
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text       = "Collection Schedule",
                    style      = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                // Today's day name in a small chip — quick visual anchor
                val todayName = remember {
                    SimpleDateFormat("EEEE", Locale.getDefault()).format(Date())
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text     = todayName,
                        style    = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color    = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (schedules.isEmpty()) {
                Box(
                    modifier         = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text  = if (isDriver && isDriverViewActive)
                            "No schedules yet.\nTap + to add the first one."
                        else
                            "No schedules have been set yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state               = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding      = PaddingValues(bottom = 88.dp)
                ) {
                    items(schedules.zip(activityInfos), key = { it.first.id }) { (schedule, info) ->
                        ScheduleCard(
                            schedule             = schedule,
                            activityInfo         = info,
                            isDriverInDriverView = isDriverInDriverView,
                            onNavigateToGuide    = onNavigateToGuide,
                            onNavigateToZones    = onNavigateToZones
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// ScheduleCard
// ---------------------------------------------------------------------------

/**
 * A single schedule card with a three-level activity indicator.
 *
 * Visual design per status:
 *
 *  ACTIVE_NOW — Green primaryContainer background, 2 dp primary border,
 *               pulsing green dot + "● ACTIVE NOW" badge in top-left,
 *               elevated shadow.
 *
 *  TODAY      — Amber/secondary tinted background, amber border,
 *               solid amber dot + "📅 TODAY" badge,
 *               time-status sub-label ("Starts in 2h 15m" / "Collection ended" / "Scheduled for today").
 *
 *  UPCOMING   — Neutral surfaceVariant background, no border,
 *               small grey dot + "In X days" label (or "Tomorrow").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleCard(
    schedule: CollectionDay,
    activityInfo: ScheduleActivityInfo,
    isDriverInDriverView: Boolean,
    onNavigateToGuide: (String) -> Unit,
    onNavigateToZones: (scheduleDayId: String, scheduleDayName: String) -> Unit
) {
    var showNoGuideDialog by remember { mutableStateOf(false) }

    if (showNoGuideDialog) {
        AlertDialog(
            onDismissRequest = { showNoGuideDialog = false },
            title            = { Text("No Guide Available") },
            text             = {
                Text("There is no recycling guide linked to ${schedule.dayOfWeek}'s collection yet.")
            },
            confirmButton = {
                TextButton(onClick = { showNoGuideDialog = false }) { Text("OK") }
            }
        )
    }

    // ---- Colour scheme per status ----
    val cardContainerColor = when (activityInfo.status) {
        ScheduleStatus.ACTIVE_NOW -> MaterialTheme.colorScheme.primaryContainer
        ScheduleStatus.TODAY      -> MaterialTheme.colorScheme.secondaryContainer
        ScheduleStatus.UPCOMING   -> MaterialTheme.colorScheme.surfaceVariant
    }
    val cardBorder = when (activityInfo.status) {
        ScheduleStatus.ACTIVE_NOW -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        ScheduleStatus.TODAY      -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary)
        ScheduleStatus.UPCOMING   -> null
    }
    val cardElevation = when (activityInfo.status) {
        ScheduleStatus.ACTIVE_NOW -> 8.dp
        ScheduleStatus.TODAY      -> 4.dp
        ScheduleStatus.UPCOMING   -> 1.dp
    }
    val dayTextColor = when (activityInfo.status) {
        ScheduleStatus.ACTIVE_NOW -> MaterialTheme.colorScheme.onPrimaryContainer
        ScheduleStatus.TODAY      -> MaterialTheme.colorScheme.onSecondaryContainer
        ScheduleStatus.UPCOMING   -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .clickable {
                if (isDriverInDriverView) {
                    onNavigateToZones(schedule.id, schedule.dayOfWeek)
                } else {
                    if (schedule.linkedGuideId != null) {
                        onNavigateToGuide(schedule.linkedGuideId)
                    } else {
                        showNoGuideDialog = true
                    }
                }
            },
        colors    = CardDefaults.cardColors(containerColor = cardContainerColor),
        border    = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ---- Status banner strip at the top of the card ----
            // Only rendered for TODAY and ACTIVE_NOW — hidden for plain upcoming cards.
            if (activityInfo.status != ScheduleStatus.UPCOMING) {
                StatusBannerStrip(activityInfo = activityInfo)
            }

            // ---- Card body ----
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // ---- Left side ----
                Column(modifier = Modifier.weight(1f)) {

                    // Day name row + upcoming hint for non-today cards
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text       = schedule.dayOfWeek,
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color      = dayTextColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        // "In X days" / "Tomorrow" label for upcoming cards
                        if (activityInfo.status == ScheduleStatus.UPCOMING && activityInfo.daysUntil > 0) {
                            val daysLabel = when (activityInfo.daysUntil) {
                                1    -> "Tomorrow"
                                else -> "In ${activityInfo.daysUntil} days"
                            }
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                    text     = daysLabel,
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Optional time range
                    if (!schedule.collectionTimeRange.isNullOrBlank()) {
                        Text(
                            text     = "🕐 ${schedule.collectionTimeRange}",
                            style    = MaterialTheme.typography.bodySmall,
                            color    = dayTextColor.copy(
                                alpha = if (activityInfo.status == ScheduleStatus.UPCOMING) 0.7f else 1f
                            ),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    // Waste category chips
                    if (schedule.wasteCategories.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement   = Arrangement.spacedBy(4.dp)
                        ) {
                            schedule.wasteCategories.forEach { category ->
                                SuggestionChip(
                                    onClick = {},
                                    label   = {
                                        Text(
                                            text  = category,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                // ---- Right side: action hint ----
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier            = Modifier.padding(start = 8.dp)
                ) {
                    if (isDriverInDriverView) {
                        Icon(
                            imageVector        = Icons.Default.Map,
                            contentDescription = "Manage Route",
                            tint               = MaterialTheme.colorScheme.primary,
                            modifier           = Modifier.size(20.dp)
                        )
                        Text(
                            text  = "Manage\nRoute",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        // "View Guide" hint — always visible (Rule 17)
                        val guideLinked = schedule.linkedGuideId != null
                        val hintColor = if (guideLinked)
                            MaterialTheme.colorScheme.primary
                        else
                            dayTextColor.copy(alpha = 0.4f)
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "View Guide",
                            tint               = hintColor,
                            modifier           = Modifier.size(20.dp)
                        )
                        Text(
                            text  = "View Guide",
                            style = MaterialTheme.typography.labelSmall,
                            color = hintColor
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// StatusBannerStrip — the coloured strip at the top of TODAY / ACTIVE_NOW cards
// ---------------------------------------------------------------------------

/**
 * A slim horizontal strip rendered at the very top edge of a schedule card
 * for [ScheduleStatus.TODAY] and [ScheduleStatus.ACTIVE_NOW] cards.
 *
 * ACTIVE_NOW: green background, pulsing dot, "● ACTIVE NOW" text, time label.
 * TODAY:      amber/secondary background, solid dot, "📅 TODAY" text, time label
 *             e.g. "Starts in 2h 15m" or "Collection ended".
 */
@Composable
private fun StatusBannerStrip(activityInfo: ScheduleActivityInfo) {
    val isActive = activityInfo.status == ScheduleStatus.ACTIVE_NOW

    // Background colour of the strip
    val stripColor = if (isActive)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.secondary

    val onStripColor = if (isActive)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSecondary

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .background(stripColor)
            .padding(horizontal = 16.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isActive) {
                // Pulsing dot for ACTIVE_NOW
                PulsingDot(color = onStripColor)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text       = "ACTIVE NOW",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color      = onStripColor
                )
            } else {
                // Static dot for TODAY
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(onStripColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text       = "TODAY",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color      = onStripColor
                )
            }
        }

        // Time status label on the right side of the strip
        if (activityInfo.timeLabel.isNotBlank()) {
            Text(
                text  = activityInfo.timeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = onStripColor.copy(alpha = 0.85f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// PulsingDot — animated indicator for ACTIVE_NOW
// ---------------------------------------------------------------------------

/**
 * A small circle that pulses (alpha oscillates) to draw attention to the
 * currently active collection window.
 */
@Composable
private fun PulsingDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 0.3f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

// ---------------------------------------------------------------------------
// computeActivityInfo — single source of truth for schedule status
// ---------------------------------------------------------------------------

/**
 * Computes the [ScheduleActivityInfo] for a single [CollectionDay].
 *
 * Logic:
 *
 * 1. Determine how many days from today until the schedule's day (0–6).
 *    We always look forward in the week, wrapping around Sunday→Monday.
 *
 * 2. If daysUntil > 0 → status = UPCOMING, timeLabel = "".
 *
 * 3. If daysUntil == 0 (today):
 *    a. No time range set → status = TODAY, timeLabel = "Scheduled for today".
 *    b. Time range set and now is within window → status = ACTIVE_NOW,
 *       timeLabel = "Ends in Xh Ym".
 *    c. Time range set and now is before window → status = TODAY,
 *       timeLabel = "Starts in Xh Ym".
 *    d. Time range set and now is after window → status = TODAY,
 *       timeLabel = "Collection ended".
 *
 * Called outside composition (pure function) for use in remember() and LaunchedEffect.
 */
fun computeActivityInfo(schedule: CollectionDay): ScheduleActivityInfo {
    return try {
        val now        = Calendar.getInstance()
        val todayDow   = now.get(Calendar.DAY_OF_WEEK)  // 1=Sun, 2=Mon … 7=Sat

        // Map day-of-week name → Calendar constant
        val scheduleDow = dayNameToCalendarDow(schedule.dayOfWeek)
            ?: return ScheduleActivityInfo(ScheduleStatus.UPCOMING, "", 99)

        // Days until the scheduled day (always 0–6, wrapping forward)
        val daysUntil = ((scheduleDow - todayDow + 7) % 7)

        if (daysUntil > 0) {
            // ---- Future day ----
            return ScheduleActivityInfo(ScheduleStatus.UPCOMING, "", daysUntil)
        }

        // ---- Today (daysUntil == 0) ----
        val range = schedule.collectionTimeRange
        if (range.isNullOrBlank()) {
            // No time range — we know it's today but can't say more
            return ScheduleActivityInfo(ScheduleStatus.TODAY, "Scheduled for today", 0)
        }

        // Parse the time range — format "HH:mm – HH:mm" (en-dash separator)
        val parts = range.split("–")
        if (parts.size != 2) {
            return ScheduleActivityInfo(ScheduleStatus.TODAY, "Scheduled for today", 0)
        }

        val timeFormat  = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
        val startParsed = timeFormat.parse(parts[0].trim()) ?: return ScheduleActivityInfo(ScheduleStatus.TODAY, "", 0)
        val endParsed   = timeFormat.parse(parts[1].trim()) ?: return ScheduleActivityInfo(ScheduleStatus.TODAY, "", 0)

        // Build today's start/end as full Calendar objects so we can diff them
        val todayStart = Calendar.getInstance().apply {
            val s = Calendar.getInstance().also { c ->
                c.time = startParsed
            }
            set(Calendar.HOUR_OF_DAY, s.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE,      s.get(Calendar.MINUTE))
            set(Calendar.SECOND,      0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayEnd = Calendar.getInstance().apply {
            val e = Calendar.getInstance().also { c ->
                c.time = endParsed
            }
            set(Calendar.HOUR_OF_DAY, e.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE,      e.get(Calendar.MINUTE))
            set(Calendar.SECOND,      0)
            set(Calendar.MILLISECOND, 0)
        }

        val nowMs   = now.timeInMillis
        val startMs = todayStart.timeInMillis
        val endMs   = todayEnd.timeInMillis

        return when {
            nowMs in startMs..endMs -> {
                // Currently inside the collection window
                val minutesLeft = ((endMs - nowMs) / 60_000).toInt()
                ScheduleActivityInfo(
                    status     = ScheduleStatus.ACTIVE_NOW,
                    timeLabel  = "Ends in ${formatMinutes(minutesLeft)}",
                    daysUntil  = 0
                )
            }
            nowMs < startMs -> {
                // Window hasn't started yet
                val minutesUntil = ((startMs - nowMs) / 60_000).toInt()
                ScheduleActivityInfo(
                    status    = ScheduleStatus.TODAY,
                    timeLabel = "Starts in ${formatMinutes(minutesUntil)}",
                    daysUntil = 0
                )
            }
            else -> {
                // Window has already ended today
                ScheduleActivityInfo(
                    status    = ScheduleStatus.TODAY,
                    timeLabel = "Collection ended",
                    daysUntil = 0
                )
            }
        }
    } catch (e: Exception) {
        ScheduleActivityInfo(ScheduleStatus.UPCOMING, "", 99)
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Converts a locale-aware day name (e.g. "Monday", "Maandag") to the
 * Calendar.DAY_OF_WEEK constant (1 = Sunday … 7 = Saturday).
 *
 * We compare against both the full English names AND the device-locale
 * display names so the app works in all locales.
 */
private fun dayNameToCalendarDow(dayName: String): Int? {
    // English fallback map (schedules are stored as English names)
    val englishMap = mapOf(
        "sunday"    to Calendar.SUNDAY,
        "monday"    to Calendar.MONDAY,
        "tuesday"   to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY,
        "thursday"  to Calendar.THURSDAY,
        "friday"    to Calendar.FRIDAY,
        "saturday"  to Calendar.SATURDAY
    )
    val lower = dayName.lowercase(Locale.getDefault()).trim()
    // Try English first (most likely since the app stores English names)
    englishMap[lower]?.let { return it }

    // Try device locale display names as a secondary fallback
    for (dow in Calendar.SUNDAY..Calendar.SATURDAY) {
        val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, dow) }
        val localeName = cal
            .getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault())
            ?.lowercase(Locale.getDefault())
        if (localeName == lower) return dow
    }
    return null
}

/**
 * Formats a number of minutes into a human-friendly string.
 * Examples: 15 → "15m", 90 → "1h 30m", 60 → "1h"
 */
private fun formatMinutes(totalMinutes: Int): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h == 0        -> "${m}m"
        m == 0        -> "${h}h"
        else          -> "${h}h ${m}m"
    }
}