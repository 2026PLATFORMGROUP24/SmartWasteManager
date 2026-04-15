package com.platform.smartwastemanager.features.map.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Navigation
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
 * ActiveRouteScreen — full-screen collection route execution with turn-by-turn directions.
 *
 * States:
 *   READY       — Map preview + stop list + "Start Route" button.
 *   IN-PROGRESS — Full-screen map with a bottom card showing the current stop,
 *                 turn-by-turn directions, an "Open in Maps" button, and the Collect button.
 *   COMPLETED   — Celebration card.
 *   CALCULATING — Spinner.
 *   ERROR       — Error card + Go Back.
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

    // Show collect/dismiss errors as snackbars without stopping the route
    LaunchedEffect(actionState) {
        if (actionState is RouteActionState.Error) {
            snackbarHostState.showSnackbar((actionState as RouteActionState.Error).message)
            viewModel.resetActionState()
        }
    }

    // Pan the camera when route state changes
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
                val current = state.stops.getOrNull(state.currentStopIndex)
                    ?: return@LaunchedEffect
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
                // CALCULATING
                // ----------------------------------------------------------------
                is ActiveRouteUiState.Calculating -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Calculating optimised route…",
                                style = MaterialTheme.typography.bodyMedium)
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
                            cameraPositionState = cameraPositionState,
                            modifier            = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                        )

                        StopListPanel(
                            stops            = state.stops,
                            currentStopIndex = -1,
                            modifier         = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )

                        Surface(
                            tonalElevation = 4.dp,
                            modifier       = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick  = {
                                    // Get driver location then start the route
                                    scope.launch {
                                        val gp = if (locationPermissions.allPermissionsGranted)
                                            LocationHelper.getCurrentLocation(context)
                                        else null

                                        val lat = gp?.latitude ?: state.stops[0].location.latitude
                                        val lng = gp?.longitude ?: state.stops[0].location.longitude
                                        viewModel.startRoute(lat, lng)
                                    }
                                },
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
                                    text = "Start Route  •  ${state.stops.size} stop${if (state.stops.size != 1) "s" else ""}",
                                    style      = MaterialTheme.typography.titleSmall,
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
                    val stopLat     = currentStop.location.latitude
                    val stopLng     = currentStop.location.longitude

                    Box(modifier = Modifier.fillMaxSize()) {

                        // Map fills the full remaining space
                        RouteMapWithStops(
                            stops               = state.stops,
                            currentStopIndex    = state.currentStopIndex,
                            cameraPositionState = cameraPositionState,
                            modifier            = Modifier.fillMaxSize()
                        )

                        // ---- Bottom floating card ----
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter),
                            shape     = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            colors    = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp, vertical = 16.dp)
                            ) {

                                // Progress row
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

                                LinearProgressIndicator(
                                    progress = {
                                        state.currentStopIndex.toFloat() / state.stops.size.toFloat()
                                    },
                                    modifier   = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .height(6.dp),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )

                                // Stop detail card
                                Card(
                                    colors   = CardDefaults.cardColors(
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

                                Spacer(modifier = Modifier.height(12.dp))

                                // ---- Directions panel ----
                                Card(
                                    colors   = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector        = Icons.AutoMirrored.Filled.Navigation,
                                                contentDescription = null,
                                                tint               = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier           = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text       = "Directions",
                                                style      = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color      = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        when {
                                            // Directions are being fetched
                                            state.directionsLoading -> {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier    = Modifier.size(14.dp),
                                                        strokeWidth = 2.dp,
                                                        color       = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                    Text(
                                                        text  = "Getting directions…",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                }
                                            }

                                            // Directions loaded
                                            state.directions.isNotEmpty() -> {
                                                Column(
                                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    state.directions.forEachIndexed { index, step ->
                                                        Text(
                                                            text  = "${index + 1}. $step",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                    }
                                                }
                                            }

                                            // No directions available
                                            else -> {
                                                Text(
                                                    text  = "No directions available.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // "Open in Maps" button — launches Google Maps / navigation
                                        OutlinedButton(
                                            onClick  = {
                                                val uri = Uri.parse(
                                                    "google.navigation:q=$stopLat,$stopLng&mode=d"
                                                )
                                                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                                    setPackage("com.google.android.apps.maps")
                                                }
                                                // Fall back to any navigation app if Google Maps isn't installed
                                                val chooser = Intent.createChooser(
                                                    intent, "Open with navigation app"
                                                )
                                                context.startActivity(chooser)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors   = OutlinedButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        ) {
                                            Icon(
                                                imageVector        = Icons.Default.Map,
                                                contentDescription = null,
                                                modifier           = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Open in Maps")
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // ---- COLLECT button ----
                                Button(
                                    onClick  = {
                                        scope.launch {
                                            val gp = if (locationPermissions.allPermissionsGranted)
                                                LocationHelper.getCurrentLocation(context)
                                            else null
                                            val lat = gp?.latitude ?: stopLat
                                            val lng = gp?.longitude ?: stopLng
                                            viewModel.collectCurrentStop(lat, lng)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    colors   = ButtonDefaults.buttonColors(
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
                                        text       = "Collect",
                                        style      = MaterialTheme.typography.titleMedium,
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
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Card(
                            modifier = Modifier.padding(24.dp),
                            colors   = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier            = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
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

                // IDLE — transitional spinner
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
 * Google Map showing all stops as coloured markers and a polyline connecting them.
 * 🟢 Green  = collected  |  🔵 Azure = current stop  |  🔴 Red = upcoming
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
        if (stops.size >= 2) {
            Polyline(
                points = stops.map { LatLng(it.location.latitude, it.location.longitude) },
                color  = Color(0xFF1565C0),
                width  = 8f
            )
        }

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