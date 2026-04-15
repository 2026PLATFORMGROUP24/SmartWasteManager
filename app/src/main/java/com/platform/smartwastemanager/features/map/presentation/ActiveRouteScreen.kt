package com.platform.smartwastemanager.features.map.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
 * ActiveRouteScreen — the full-screen route execution view.
 *
 * States handled:
 *   [ActiveRouteUiState.Calculating] — spinner while OSRM calculates.
 *   [ActiveRouteUiState.Ready]       — shows all stops on the map + "Start Route" button.
 *   [ActiveRouteUiState.InProgress]  — shows the current stop card + "Collect" button.
 *                                      Completed stops are greyed out; current stop is highlighted.
 *   [ActiveRouteUiState.Completed]   — success screen; driver can navigate back.
 *   [ActiveRouteUiState.Error]       — error card with a back button.
 *
 * @param viewModel        RouteViewModel — startRoute() and collectCurrentStop() called here.
 * @param zoneName         Name of the zone, displayed in the header.
 * @param onNavigateBack   Pop back to ZoneListScreen.
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

    // Default fallback map position (Johannesburg)
    val defaultPosition  = LatLng(-26.2041, 28.0473)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultPosition, 13f)
    }

    // Show action error (e.g. collect failure) as a snackbar
    LaunchedEffect(actionState) {
        if (actionState is RouteActionState.Error) {
            snackbarHostState.showSnackbar((actionState as RouteActionState.Error).message)
            viewModel.resetActionState()
        }
    }

    // When route becomes InProgress, fly the camera to the first stop
    LaunchedEffect(routeState) {
        if (routeState is ActiveRouteUiState.InProgress) {
            val inProgress = routeState as ActiveRouteUiState.InProgress
            val current = inProgress.stops[inProgress.currentStopIndex]
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(current.location.latitude, current.location.longitude),
                    16f
                )
            )
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

            // ---- Top bar ----
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

            // ---- Main content based on route state ----
            when (val state = routeState) {

                // ---- Calculating spinner ----
                is ActiveRouteUiState.Calculating -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text  = "Calculating optimised route…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text  = "Using free OSRM routing service",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ---- Ready state: all stops on map + Start Route button ----
                is ActiveRouteUiState.Ready -> {
                    RouteMapWithStops(
                        stops               = state.stops,
                        currentStopIndex    = -1,         // -1 = not started yet
                        cameraPositionState = cameraPositionState
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    StopListPanel(
                        stops            = state.stops,
                        currentStopIndex = -1
                    )
                    // "Start Route" call-to-action
                    Button(
                        onClick  = { viewModel.startRoute() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Start Route  (${state.stops.size} stop${if (state.stops.size != 1) "s" else ""})",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }

                // ---- InProgress: current stop highlighted, Collect button ----
                is ActiveRouteUiState.InProgress -> {
                    val currentStop = state.stops[state.currentStopIndex]

                    // Map showing all remaining stops
                    RouteMapWithStops(
                        stops               = state.stops,
                        currentStopIndex    = state.currentStopIndex,
                        cameraPositionState = cameraPositionState,
                        modifier            = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )

                    // Current stop card
                    Surface(
                        tonalElevation = 4.dp,
                        modifier       = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {

                            // Progress indicator
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text  = "Stop ${state.currentStopIndex + 1} of ${state.stops.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text  = "${state.stops.size - state.currentStopIndex - 1} remaining",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            LinearProgressIndicator(
                                progress = {
                                    state.currentStopIndex.toFloat() / state.stops.size.toFloat()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            )

                            // Stop details
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text       = currentStop.streetName,
                                            style      = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color      = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text  = "🗑️ ${currentStop.category}  •  Regular Pickup",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Collect button
                            Button(
                                onClick  = { viewModel.collectCurrentStop() },
                                modifier = Modifier.fillMaxWidth(),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text  = "Collect",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        }
                    }
                }

                // ---- Completed ----
                is ActiveRouteUiState.Completed -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint     = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(80.dp)
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
                                onClick = {
                                    viewModel.resetRoute()
                                    onNavigateBack()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Back to Zones")
                            }
                        }
                    }
                }

                // ---- Error ----
                is ActiveRouteUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.padding(24.dp),
                            colors   = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint     = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text  = state.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(onClick = {
                                    viewModel.resetRoute()
                                    onNavigateBack()
                                }) {
                                    Text("Go Back")
                                }
                            }
                        }
                    }
                }

                // ---- Idle (shouldn't normally be shown) ----
                is ActiveRouteUiState.Idle -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

/**
 * The map component shared by Ready and InProgress states.
 *
 * Renders all stops as markers:
 *   - Green  = already collected
 *   - Blue   = current stop (highlighted)
 *   - Red    = upcoming stop
 *
 * Draws a thin polyline connecting stops in route order.
 */
@Composable
private fun RouteMapWithStops(
    stops: List<RouteStop>,
    currentStopIndex: Int,
    cameraPositionState: CameraPositionState,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(280.dp)
) {
    GoogleMap(
        modifier            = modifier,
        cameraPositionState = cameraPositionState,
        uiSettings          = MapUiSettings(zoomControlsEnabled = true)
    ) {
        // Draw the route line connecting all stops in order
        if (stops.size >= 2) {
            val routePoints = stops.map { LatLng(it.location.latitude, it.location.longitude) }
            Polyline(
                points = routePoints,
                color  = Color(0xFF1565C0),   // dark blue
                width  = 8f
            )
        }

        // Draw each stop as a coloured marker
        stops.forEachIndexed { index, stop ->
            val position = LatLng(stop.location.latitude, stop.location.longitude)
            val markerHue = when {
                stop.isCollected        -> BitmapDescriptorFactory.HUE_GREEN   // done
                index == currentStopIndex -> BitmapDescriptorFactory.HUE_AZURE  // current
                else                    -> BitmapDescriptorFactory.HUE_RED      // upcoming
            }
            Marker(
                state   = rememberMarkerState(position = position),
                title   = "Stop ${index + 1}: ${stop.streetName}",
                snippet = stop.category,
                icon    = BitmapDescriptorFactory.defaultMarker(markerHue)
            )
        }
    }
}

/**
 * A compact scrollable list of all stops shown below the map.
 * Used in the Ready state so the driver can preview the route before starting.
 */
@Composable
private fun StopListPanel(
    stops: List<RouteStop>,
    currentStopIndex: Int
) {
    LazyColumn(
        modifier        = Modifier
            .fillMaxWidth()
            .heightIn(max = 180.dp),
        contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(stops) { index, stop ->
            val isCurrent   = index == currentStopIndex
            val isCollected = stop.isCollected
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stop number badge
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = when {
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
                        text  = stop.streetName,
                        style = MaterialTheme.typography.bodySmall,
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
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Collected",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}