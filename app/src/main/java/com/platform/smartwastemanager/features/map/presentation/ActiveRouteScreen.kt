package com.platform.smartwastemanager.features.map.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.platform.smartwastemanager.core.util.LocationHelper
import com.platform.smartwastemanager.features.map.domain.RouteStop
import kotlinx.coroutines.launch

/**
 * ActiveRouteScreen — full-screen collection route execution.
 *
 * The map draws the real road-following polyline returned by OSRM (blue line
 * tracing actual streets). Falls back to straight lines between stops if OSRM
 * geometry was unavailable.
 *
 * The bottom card (stop info + directions + Collect button) is collapsible —
 * tap the drag handle / header row to collapse or expand it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ActiveRouteScreen(
    viewModel: RouteViewModel,
    zoneName: String,
    onNavigateBack: () -> Unit
) {
    val routeState  by viewModel.activeRouteState.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()

    val context           = LocalContext.current
    val scope             = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    val defaultPosition = LatLng(-26.2041, 28.0473)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultPosition, 13f)
    }

    LaunchedEffect(actionState) {
        if (actionState is RouteActionState.Error) {
            snackbarHostState.showSnackbar((actionState as RouteActionState.Error).message)
            viewModel.resetActionState()
        }
    }

    LaunchedEffect(routeState) {
        when (val state = routeState) {
            is ActiveRouteUiState.Ready -> {
                val first = state.stops.firstOrNull() ?: return@LaunchedEffect
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(first.location.latitude, first.location.longitude), 14f
                    )
                )
            }
            is ActiveRouteUiState.InProgress -> {
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

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
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
                IconButton(onClick = { viewModel.resetRoute(); onNavigateBack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint               = MaterialTheme.colorScheme.onPrimaryContainer
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

            when (val state = routeState) {

                // ----------------------------------------------------------------
                // CALCULATING
                // ----------------------------------------------------------------
                is ActiveRouteUiState.Calculating -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("Calculating optimised route…", style = MaterialTheme.typography.bodyMedium)
                            Text("Using free OSRM routing service",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // ----------------------------------------------------------------
                // READY — map preview + stop list + Start button
                // ----------------------------------------------------------------
                is ActiveRouteUiState.Ready -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        RouteMapWithStops(
                            stops               = state.stops,
                            currentStopIndex    = -1,
                            roadPolyline        = state.roadPolyline,
                            cameraPositionState = cameraPositionState,
                            modifier            = Modifier.fillMaxWidth().height(300.dp)
                        )
                        StopListPanel(
                            stops            = state.stops,
                            currentStopIndex = -1,
                            modifier         = Modifier.fillMaxWidth().weight(1f)
                        )
                        Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick  = {
                                    scope.launch {
                                        val gp  = if (locationPermissions.allPermissionsGranted)
                                            LocationHelper.getCurrentLocation(context) else null
                                        val lat = gp?.latitude  ?: state.stops[0].location.latitude
                                        val lng = gp?.longitude ?: state.stops[0].location.longitude
                                        viewModel.startRoute(lat, lng)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .height(52.dp),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Start Route  •  ${state.stops.size} stop${if (state.stops.size != 1) "s" else ""}",
                                    style      = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // ----------------------------------------------------------------
                // IN PROGRESS — full-screen map + collapsible bottom card
                // ----------------------------------------------------------------
                is ActiveRouteUiState.InProgress -> {
                    val currentStop = state.stops[state.currentStopIndex]
                    val stopLat     = currentStop.location.latitude
                    val stopLng     = currentStop.location.longitude

                    // Starts expanded; driver taps handle to collapse/expand
                    var cardExpanded by remember { mutableStateOf(true) }

                    Box(modifier = Modifier.fillMaxSize()) {

                        // Map behind the card — road polyline drawn here
                        RouteMapWithStops(
                            stops               = state.stops,
                            currentStopIndex    = state.currentStopIndex,
                            roadPolyline        = state.roadPolyline,
                            cameraPositionState = cameraPositionState,
                            modifier            = Modifier.fillMaxSize()
                        )

                        // ---- Collapsible bottom card ----
                        Card(
                            modifier  = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                            shape     = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            colors    = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {

                                // Always-visible header — tap to toggle
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { cardExpanded = !cardExpanded }
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Drag-handle pill
                                    Box(
                                        modifier = Modifier
                                            .width(40.dp)
                                            .height(4.dp)
                                            .background(
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                                RoundedCornerShape(2.dp)
                                            )
                                    )
                                    Spacer(Modifier.height(8.dp))

                                    Row(
                                        modifier              = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment     = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text       = "Stop ${state.currentStopIndex + 1} of ${state.stops.size}",
                                                style      = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color      = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text  = currentStop.streetName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            imageVector        = if (cardExpanded)
                                                Icons.Default.KeyboardArrowDown
                                            else
                                                Icons.Default.KeyboardArrowUp,
                                            contentDescription = if (cardExpanded) "Collapse" else "Expand",
                                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier           = Modifier.size(24.dp)
                                        )
                                    }

                                    // Progress bar — always visible
                                    LinearProgressIndicator(
                                        progress  = {
                                            state.currentStopIndex.toFloat() / state.stops.size.toFloat()
                                        },
                                        modifier   = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                            .height(5.dp),
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }

                                // ---- Expandable body ----
                                AnimatedVisibility(
                                    visible = cardExpanded,
                                    enter   = expandVertically(),
                                    exit    = shrinkVertically()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .verticalScroll(rememberScrollState())
                                            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                                    ) {

                                        // Current stop detail
                                        Card(
                                            colors   = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier          = Modifier.fillMaxWidth().padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.LocationOn,
                                                    contentDescription = null,
                                                    tint               = MaterialTheme.colorScheme.primary,
                                                    modifier           = Modifier.size(28.dp)
                                                )
                                                Spacer(Modifier.width(10.dp))
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
                                                    val rem = state.stops.size - state.currentStopIndex - 1
                                                    Text(
                                                        text  = "$rem stop${if (rem != 1) "s" else ""} remaining",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(12.dp))

                                        // Directions panel
                                        Card(
                                            colors   = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.Navigation,
                                                        contentDescription = null,
                                                        tint     = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        "Directions",
                                                        style      = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color      = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                }
                                                Spacer(Modifier.height(6.dp))
                                                when {
                                                    state.directionsLoading -> {
                                                        Row(
                                                            verticalAlignment     = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            CircularProgressIndicator(
                                                                modifier    = Modifier.size(14.dp),
                                                                strokeWidth = 2.dp,
                                                                color       = MaterialTheme.colorScheme.onSecondaryContainer
                                                            )
                                                            Text(
                                                                "Getting directions…",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                                            )
                                                        }
                                                    }
                                                    state.directions.isNotEmpty() -> {
                                                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                            state.directions.forEachIndexed { i, step ->
                                                                Text(
                                                                    "${i + 1}. $step",
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                                )
                                                            }
                                                        }
                                                    }
                                                    else -> Text(
                                                        "No directions available.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                }
                                                Spacer(Modifier.height(8.dp))
                                                OutlinedButton(
                                                    onClick  = {
                                                        val uri    = Uri.parse("google.navigation:q=$stopLat,$stopLng&mode=d")
                                                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                                            setPackage("com.google.android.apps.maps")
                                                        }
                                                        context.startActivity(
                                                            Intent.createChooser(intent, "Open with navigation app")
                                                        )
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors   = ButtonDefaults.outlinedButtonColors(
                                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                ) {
                                                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Open in Maps")
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(14.dp))

                                        // Collect button
                                        Button(
                                            onClick  = {
                                                scope.launch {
                                                    val gp  = if (locationPermissions.allPermissionsGranted)
                                                        LocationHelper.getCurrentLocation(context) else null
                                                    val lat = gp?.latitude  ?: stopLat
                                                    val lng = gp?.longitude ?: stopLng
                                                    viewModel.collectCurrentStop(lat, lng)
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth().height(56.dp),
                                            colors   = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            ),
                                            shape    = MaterialTheme.shapes.medium
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(22.dp))
                                            Spacer(Modifier.width(10.dp))
                                            Text("Collect", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ----------------------------------------------------------------
                // COMPLETED
                // ----------------------------------------------------------------
                is ActiveRouteUiState.Completed -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier            = Modifier.padding(32.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(80.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("Route Complete! ✅", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("All pickups in '$zoneName' have been collected.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(32.dp))
                            Button(onClick = { viewModel.resetRoute(); onNavigateBack() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Back to Zones")
                            }
                        }
                    }
                }

                // ----------------------------------------------------------------
                // ERROR
                // ----------------------------------------------------------------
                is ActiveRouteUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Card(modifier = Modifier.padding(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(40.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(state.message, color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = { viewModel.resetRoute(); onNavigateBack() }) { Text("Go Back") }
                            }
                        }
                    }
                }

                is ActiveRouteUiState.Idle -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
 * Google Map showing the route.
 *
 * Polyline priority:
 *   1. If [roadPolyline] is non-empty → draw the actual road-following line from OSRM (blue).
 *   2. Otherwise → fall back to straight lines between stops (still blue, dashed visual via
 *      the existing behaviour, still useful when offline or OSRM unavailable).
 *
 * Markers:
 *   🟢 Green  = collected
 *   🔵 Azure  = current stop (driver is heading here)
 *   🔴 Red    = upcoming stops
 */
@Composable
private fun RouteMapWithStops(
    stops: List<RouteStop>,
    currentStopIndex: Int,
    roadPolyline: List<LatLng>,
    cameraPositionState: CameraPositionState,
    modifier: Modifier = Modifier.fillMaxWidth().height(300.dp)
) {
    GoogleMap(
        modifier            = modifier,
        cameraPositionState = cameraPositionState,
        uiSettings          = MapUiSettings(
            zoomControlsEnabled     = true,
            myLocationButtonEnabled = false
        )
    ) {
        // ---- Road polyline ----
        if (roadPolyline.isNotEmpty()) {
            // Real road path from OSRM geometry — thick blue line
            Polyline(
                points    = roadPolyline,
                color     = Color(0xFF1565C0),
                width     = 10f,
                geodesic  = true
            )
        } else if (stops.size >= 2) {
            // Fallback: straight lines between stops
            Polyline(
                points   = stops.map { LatLng(it.location.latitude, it.location.longitude) },
                color    = Color(0xFF1565C0),
                width    = 8f,
                geodesic = true
            )
        }

        // ---- Stop markers ----
        stops.forEachIndexed { index, stop ->
            val position = LatLng(stop.location.latitude, stop.location.longitude)
            val hue = when {
                stop.isCollected          -> BitmapDescriptorFactory.HUE_GREEN
                index == currentStopIndex -> BitmapDescriptorFactory.HUE_AZURE
                else                      -> BitmapDescriptorFactory.HUE_RED
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

/** Compact stop-list panel used in the Ready state. */
@Composable
private fun StopListPanel(
    stops: List<RouteStop>,
    currentStopIndex: Int,
    modifier: Modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
) {
    LazyColumn(
        modifier            = modifier,
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(stops) { index, stop ->
            val isCurrent   = index == currentStopIndex
            val isCollected = stop.isCollected
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                            "${index + 1}",
                            style      = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color      = when {
                                isCollected -> MaterialTheme.colorScheme.onTertiary
                                isCurrent   -> MaterialTheme.colorScheme.onPrimary
                                else        -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stop.streetName, style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal)
                    Text(stop.category, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isCollected) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Collected",
                        tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}