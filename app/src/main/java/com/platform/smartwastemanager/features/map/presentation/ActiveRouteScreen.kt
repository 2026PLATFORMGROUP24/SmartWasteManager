package com.platform.smartwastemanager.features.map.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.platform.smartwastemanager.features.map.domain.RouteStop

/**
 * ActiveRouteScreen — full-screen collection route execution.
 *
 * Layout philosophy:
 *   READY state:    Map (300 dp) → scrollable stop list → "Start Route" button.
 *   IN-PROGRESS:    Map fills the full screen. The current-stop card is overlaid
 *                   at the bottom as a floating panel — ALWAYS visible, no scrolling
 *                   needed to reach the "Collect" button.
 *   COMPLETED:      Celebration card centred on screen.
 *   CALCULATING:    Spinner centred on screen.
 *   ERROR:          Error card with "Go Back".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveRouteScreen(
    viewModel: RouteViewModel,
    zoneName: String,
    onNavigateBack: () -> Unit
) {
    val routeState  by viewModel.activeRouteState.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val defaultPosition = LatLng(-26.2041, 28.0473)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultPosition, 13f)
    }

    // Show collect/dismiss error as a snackbar without interrupting the route
    LaunchedEffect(actionState) {
        if (actionState is RouteActionState.Error) {
            snackbarHostState.showSnackbar((actionState as RouteActionState.Error).message)
            viewModel.resetActionState()
        }
    }

    // When the route transitions to Ready, fly the camera to fit all stops
    LaunchedEffect(routeState) {
        when (val state = routeState) {
            is ActiveRouteUiState.Ready -> {
                // Centre on the first stop so the driver can see where they're going
                val first = state.stops.firstOrNull() ?: return@LaunchedEffect
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(first.location.latitude, first.location.longitude), 14f
                    )
                )
            }
            is ActiveRouteUiState.InProgress -> {
                // Pan to the current stop every time it changes
                val current = state.stops.getOrNull(state.currentStopIndex) ?: return@LaunchedEffect
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(current.location.latitude, current.location.longitude), 16f
                    )
                )
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
        ) {

            // ---- Persistent top bar ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(start = 4.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    viewModel.resetRoute()
                    onNavigateBack()
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column {
                    Text(
                        text       = "Collection Route",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text  = zoneName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // ---- State-driven content ----
            when (val state = routeState) {

                // ----------------------------------------------------------------
                // CALCULATING — spinner while OSRM works
                // ----------------------------------------------------------------
                is ActiveRouteUiState.Calculating -> {
                    Box(
                        modifier         = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Calculating optimised route…",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Using free OSRM routing service",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ----------------------------------------------------------------
                // READY — preview map + stop list + Start Route button
                // ----------------------------------------------------------------
                is ActiveRouteUiState.Ready -> {
                    Column(modifier = Modifier.fillMaxSize()) {

                        // Map at a fixed height so the list + button always fit below
                        RouteMapWithStops(
                            stops               = state.stops,
                            currentStopIndex    = -1,
                            cameraPositionState = cameraPositionState,
                            modifier            = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                        )

                        // Stop preview list (scrollable, capped in height)
                        StopListPanel(
                            stops            = state.stops,
                            currentStopIndex = -1,
                            modifier         = Modifier
                                .fillMaxWidth()
                                .weight(1f)   // takes remaining space above the button
                        )

                        // ---- Start Route button — always at the very bottom ----
                        Surface(
                            tonalElevation = 4.dp,
                            modifier       = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick  = { viewModel.startRoute() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .height(52.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text  = "Start Route  •  ${state.stops.size} stop${if (state.stops.size != 1) "s" else ""}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // ----------------------------------------------------------------
                // IN PROGRESS — map fills screen, stop card overlaid at bottom
                // ----------------------------------------------------------------
                is ActiveRouteUiState.InProgress -> {
                    val currentStop = state.stops[state.currentStopIndex]

                    // Use a Box so the stop card can float over the map
                    Box(modifier = Modifier.fillMaxSize()) {

                        // Map fills the entire remaining space
                        RouteMapWithStops(
                            stops               = state.stops,
                            currentStopIndex    = state.currentStopIndex,
                            cameraPositionState = cameraPositionState,
                            modifier            = Modifier.fillMaxSize()
                        )

                        // ---- Current stop card — overlaid at the bottom ----
                        // Uses a rounded top surface so it looks like a bottom sheet.
                        // The Collect button is ALWAYS the last item — no scrolling needed.
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter),
                            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp)
                            ) {

                                // ---- Progress row ----
                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment     = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text  = "Stop ${state.currentStopIndex + 1} of ${state.stops.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text  = "${state.stops.size - state.currentStopIndex - 1} remaining",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Progress bar
                                LinearProgressIndicator(
                                    progress = {
                                        (state.currentStopIndex).toFloat() / state.stops.size.toFloat()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .height(6.dp),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )

                                // ---- Stop detail card ----
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier          = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector        = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint               = MaterialTheme.colorScheme.primary,
                                            modifier           = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text       = currentStop.streetName,
                                                style      = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color      = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text  = "🗑️ ${currentStop.category}  •  Regular Pickup",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // ---- COLLECT button — large, always visible, bottom of card ----
                                Button(
                                    onClick  = { viewModel.collectCurrentStop() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),   // tall target — easy to tap
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Icon(
                                        imageVector        = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier           = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text      = "Collect",
                                        style     = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // ----------------------------------------------------------------
                // COMPLETED
                // ----------------------------------------------------------------
                is ActiveRouteUiState.Completed -> {
                    Box(
                        modifier         = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier            = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector        = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.primary,
                                modifier           = Modifier.size(80.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text       = "Route Complete! ✅",
                                style      = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text  = "All pickups in '$zoneName' have been collected.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick  = { viewModel.resetRoute(); onNavigateBack() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Back to Zones")
                            }
                        }
                    }
                }

                // ----------------------------------------------------------------
                // ERROR
                // ----------------------------------------------------------------
                is ActiveRouteUiState.Error -> {
                    Box(
                        modifier         = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.padding(24.dp),
                            colors   = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier                = Modifier.padding(20.dp),
                                horizontalAlignment     = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector        = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint               = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier           = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text  = state.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(onClick = { viewModel.resetRoute(); onNavigateBack() }) {
                                    Text("Go Back")
                                }
                            }
                        }
                    }
                }

                // ----------------------------------------------------------------
                // IDLE — transitional state, show spinner
                // ----------------------------------------------------------------
                is ActiveRouteUiState.Idle -> {
                    Box(
                        modifier         = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

// =============================================================================
// PRIVATE COMPOSABLES
// =============================================================================

/**
 * The Google Map component used by both Ready and InProgress states.
 *
 * Marker colours:
 *   🟢 Green  = already collected (isCollected = true)
 *   🔵 Azure  = the current stop driver needs to visit next
 *   🔴 Red    = upcoming uncollected stop
 *
 * A blue polyline connects all stops in optimised route order.
 */
@Composable
private fun RouteMapWithStops(
    stops: List<RouteStop>,
    currentStopIndex: Int,
    cameraPositionState: CameraPositionState,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(300.dp)
) {
    GoogleMap(
        modifier            = modifier,
        cameraPositionState = cameraPositionState,
        uiSettings          = MapUiSettings(
            zoomControlsEnabled     = true,
            myLocationButtonEnabled = false
        )
    ) {
        // Route polyline — draw connecting line through all stops in order
        if (stops.size >= 2) {
            Polyline(
                points = stops.map { LatLng(it.location.latitude, it.location.longitude) },
                color  = Color(0xFF1565C0),
                width  = 8f
            )
        }

        // Stop markers
        stops.forEachIndexed { index, stop ->
            val position = LatLng(stop.location.latitude, stop.location.longitude)
            val hue = when {
                stop.isCollected          -> BitmapDescriptorFactory.HUE_GREEN  // done ✅
                index == currentStopIndex -> BitmapDescriptorFactory.HUE_AZURE  // current 🔵
                else                      -> BitmapDescriptorFactory.HUE_RED    // upcoming 🔴
            }
            Marker(
                state   = rememberMarkerState(position = position),
                title   = "Stop ${index + 1}: ${stop.streetName}",
                snippet = stop.category,
                icon    = BitmapDescriptorFactory.defaultMarker(hue)
            )
        }
    }
}

/**
 * Compact stop-list panel used in the Ready state.
 * Shows all stops with their number badge, street name, category, and a ✅ when collected.
 */
@Composable
private fun StopListPanel(
    stops: List<RouteStop>,
    currentStopIndex: Int,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 200.dp)
) {
    LazyColumn(
        modifier            = modifier,
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(stops) { index, stop ->
            val isCurrent   = index == currentStopIndex
            val isCollected = stop.isCollected
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Numbered badge
                Surface(
                    shape    = MaterialTheme.shapes.small,
                    color    = when {
                        isCollected -> MaterialTheme.colorScheme.tertiary
                        isCurrent   -> MaterialTheme.colorScheme.primary
                        else        -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text  = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isCollected -> MaterialTheme.colorScheme.onTertiary
                                isCurrent   -> MaterialTheme.colorScheme.onPrimary
                                else        -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = stop.streetName,
                        style      = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text  = stop.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isCollected) {
                    Icon(
                        imageVector        = Icons.Default.CheckCircle,
                        contentDescription = "Collected",
                        tint               = MaterialTheme.colorScheme.tertiary,
                        modifier           = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}