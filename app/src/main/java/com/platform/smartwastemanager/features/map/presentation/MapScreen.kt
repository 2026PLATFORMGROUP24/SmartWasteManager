package com.platform.smartwastemanager.features.map.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.GeoPoint
import com.google.maps.android.compose.*
import com.platform.smartwastemanager.core.util.LocationHelper
import com.platform.smartwastemanager.features.map.domain.MapPin
import com.platform.smartwastemanager.features.map.domain.Zone
import com.platform.smartwastemanager.features.report.domain.ReportType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    routeViewModel: RouteViewModel,
    isDriverInDriverView: Boolean,
    driverUid: String = "",
    onNavigateToActiveRoute: () -> Unit = {}
) {
    val uiState           by viewModel.uiState.collectAsStateWithLifecycle()
    val driverZones       by viewModel.driverZones.collectAsStateWithLifecycle()
    val radiusSelectState by viewModel.radiusSelectState.collectAsStateWithLifecycle()

    val context           = LocalContext.current
    val scope             = rememberCoroutineScope()
    val focusManager      = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var searchQuery  by remember { mutableStateOf("") }
    var isSearching  by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    val defaultPosition     = LatLng(-26.2041, 28.0473)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultPosition, 11f)
    }

    var currentStreetName by remember { mutableStateOf("") }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            val center = cameraPositionState.position.target
            val street = LocationHelper.getStreetName(context, GeoPoint(center.latitude, center.longitude))
            currentStreetName = if (street != "Unknown location") street else ""
        }
    }

    LaunchedEffect(Unit) {
        if (locationPermissions.allPermissionsGranted) {
            val geoPoint = LocationHelper.getCurrentLocation(context)
            if (geoPoint.latitude != 0.0 || geoPoint.longitude != 0.0) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(LatLng(geoPoint.latitude, geoPoint.longitude), 15f)
                )
            }
        } else {
            locationPermissions.launchMultiplePermissionRequest()
        }
    }

    LaunchedEffect(locationPermissions.allPermissionsGranted) {
        if (locationPermissions.allPermissionsGranted) {
            val geoPoint = LocationHelper.getCurrentLocation(context)
            if (geoPoint.latitude != 0.0 || geoPoint.longitude != 0.0) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(LatLng(geoPoint.latitude, geoPoint.longitude), 15f)
                )
            }
        }
    }

    LaunchedEffect(isDriverInDriverView, driverUid) {
        if (isDriverInDriverView && driverUid.isNotBlank()) viewModel.loadDriverZones(driverUid)
        else viewModel.clearDriverZones()
    }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    // Convenience casts
    val activeRadius = radiusSelectState as? RadiusSelectState.Active

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.loadPins()
                if (isDriverInDriverView && driverUid.isNotBlank()) viewModel.loadDriverZones(driverUid)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                // ============================================================
                // GOOGLE MAP
                // ============================================================
                GoogleMap(
                    modifier            = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties          = MapProperties(
                        isMyLocationEnabled = locationPermissions.allPermissionsGranted
                    ),
                    uiSettings = MapUiSettings(
                        myLocationButtonEnabled = false,
                        zoomControlsEnabled     = true
                    ),
                    onMapClick = { latLng ->
                        // When in radius mode a tap moves the circle centre
                        if (activeRadius != null) {
                            viewModel.updateRadiusCenter(latLng.latitude, latLng.longitude)
                        }
                    }
                ) {
                    // ---- Waste-report pins ----
                    if (uiState is MapUiState.Success) {
                        val pins = (uiState as MapUiState.Success).pins
                        pins.forEach { pin ->
                            key(pin.reportId) {
                                MapPinMarker(pin = pin, dateFormat = dateFormat)
                            }
                        }
                    }

                    // ---- Driver zone overlays (hidden while radius mode is active to declutter) ----
                    if (isDriverInDriverView && activeRadius == null) {
                        driverZones.forEach { zone -> ZoneOverlay(zone = zone) }
                    }


                    // ---- Radius selection overlay (circle only — no centre marker) ----
                    if (activeRadius != null) {
                        val centre = LatLng(activeRadius.centerLat, activeRadius.centerLng)
                        Circle(
                            center      = centre,
                            radius      = activeRadius.radiusMeters.toDouble(),
                            fillColor   = Color(0x2200AAFF),
                            strokeColor = Color(0xFF0077CC),
                            strokeWidth = 3f
                        )
                    }
                }

                // ============================================================
                // SEARCH BAR  (top centre)
                // ============================================================
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        modifier  = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        shape     = RoundedCornerShape(24.dp),
                        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier          = Modifier
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextField(
                                value         = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder   = { Text("Search location...") },
                                modifier      = Modifier.weight(1f),
                                colors        = TextFieldDefaults.colors(
                                    focusedContainerColor   = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor   = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                                singleLine      = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    if (searchQuery.isNotBlank()) {
                                        scope.launch {
                                            isSearching = true
                                            val result = LocationHelper.getCoordinates(context, searchQuery)
                                            isSearching = false
                                            if (result != null) {
                                                cameraPositionState.animate(
                                                    CameraUpdateFactory.newLatLngZoom(
                                                        LatLng(result.latitude, result.longitude), 15f
                                                    )
                                                )
                                                focusManager.clearFocus()
                                            } else {
                                                snackbarHostState.showSnackbar("Location not found")
                                            }
                                        }
                                    }
                                })
                            )
                            if (isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        }
                    }

                    if (currentStreetName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors    = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            ),
                            shape     = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier          = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Place, contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text  = currentStreetName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // ============================================================
                // ZONE LEGEND  (top start, below search bar, hidden in radius mode)
                // ============================================================
                if (isDriverInDriverView && driverZones.isNotEmpty() && activeRadius == null) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 150.dp, start = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.93f)
                        ),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier          = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(color = Color(0xFF00C853), shape = CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text       = "${driverZones.size} zone${if (driverZones.size != 1) "s" else ""} shown",
                                style      = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color      = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // ============================================================
                // LOADING / ERROR / EMPTY states
                // ============================================================
                when (val state = uiState) {
                    is MapUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    is MapUiState.Error -> {
                        Card(
                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(state.message, color = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { viewModel.loadPins() }) { Text("Retry") }
                            }
                        }
                    }
                    is MapUiState.Success -> {
                        if (state.pins.isEmpty() && !isDriverInDriverView) {
                            Card(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 150.dp, start = 16.dp, end = 16.dp),
                                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Text("✅ No pending waste reports",
                                    style    = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp))
                            }
                        }
                    }
                }

                // ============================================================
                // CANCEL ICON (top end) — shown while radius mode is active
                // ============================================================
                if (isDriverInDriverView && activeRadius != null) {
                    SmallFloatingActionButton(
                        onClick        = { viewModel.exitRadiusMode() },
                        modifier       = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 150.dp, end = 16.dp),
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancel radius selection",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                // ============================================================
                // RADIUS CONTROL PANEL — full-width, pinned to very bottom,
                // sits above the bottom navigation bar and covers zoom controls
                // ============================================================
                if (isDriverInDriverView && activeRadius != null) {
                    Card(
                        modifier  = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                        shape     = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        colors    = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            // Title row: label + live count
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Select Collection Radius",
                                    style      = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text  = "${activeRadius.pinsInRadius.size} report${if (activeRadius.pinsInRadius.size != 1) "s" else ""} in range",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (activeRadius.pinsInRadius.isEmpty())
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    else
                                        MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Text(
                                "Tap the map to move the selection centre",
                                style    = MaterialTheme.typography.labelSmall,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                            )

                            // Radius slider
                            Row(
                                modifier          = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Radius:",
                                    style    = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.width(52.dp)
                                )
                                Slider(
                                    value         = activeRadius.radiusMeters,
                                    onValueChange = { viewModel.updateRadius(it) },
                                    valueRange    = 200f..5000f,
                                    steps         = 47,
                                    modifier      = Modifier.weight(1f)
                                )
                                Text(
                                    text     = if (activeRadius.radiusMeters >= 1000f)
                                        "${"%.1f".format(activeRadius.radiusMeters / 1000f)} km"
                                    else
                                        "${activeRadius.radiusMeters.toInt()} m",
                                    style    = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .width(52.dp)
                                        .padding(start = 6.dp)
                                )
                            }

                            // Action buttons: [Use My Location] [Start Route]
                            Row(
                                modifier              = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick  = {
                                        scope.launch {
                                            val gp = LocationHelper.getCurrentLocation(context)
                                            if (gp.latitude != 0.0 || gp.longitude != 0.0) {
                                                viewModel.updateRadiusCenter(gp.latitude, gp.longitude)
                                                cameraPositionState.animate(
                                                    CameraUpdateFactory.newLatLngZoom(
                                                        LatLng(gp.latitude, gp.longitude), 14f
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.MyLocation,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "My Location",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }

                                Button(
                                    onClick  = {
                                        val pins = activeRadius.pinsInRadius
                                        if (pins.isNotEmpty()) {
                                            scope.launch {
                                                val gp = LocationHelper.getCurrentLocation(context)
                                                routeViewModel.loadRouteForPins(
                                                    pins      = pins,
                                                    driverLat = gp.latitude,
                                                    driverLng = gp.longitude
                                                )
                                                viewModel.exitRadiusMode()
                                                onNavigateToActiveRoute()
                                            }
                                        }
                                    },
                                    enabled  = activeRadius.pinsInRadius.isNotEmpty(),
                                    modifier = Modifier.weight(1f),
                                    colors   = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Navigation,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Start Route (${activeRadius.pinsInRadius.size})",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }

                // ============================================================
                // MY LOCATION FAB  (bottom start — hidden while radius panel is open)
                // ============================================================
                if (activeRadius == null) {
                    FloatingActionButton(
                        onClick = {
                            if (locationPermissions.allPermissionsGranted) {
                                scope.launch {
                                    val geoPoint = LocationHelper.getCurrentLocation(context)
                                    if (geoPoint.latitude != 0.0 || geoPoint.longitude != 0.0) {
                                        cameraPositionState.animate(
                                            CameraUpdateFactory.newLatLngZoom(
                                                LatLng(geoPoint.latitude, geoPoint.longitude), 15f
                                            )
                                        )
                                    }
                                }
                            } else {
                                locationPermissions.launchMultiplePermissionRequest()
                            }
                        },
                        modifier       = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "My Location",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }

                // ============================================================
                // RADIUS-SELECT FAB  (top end, driver only, visible when NOT in radius mode)
                // ============================================================
                if (isDriverInDriverView && activeRadius == null) {
                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch {
                                val gp = LocationHelper.getCurrentLocation(context)
                                val lat = if (gp.latitude  != 0.0) gp.latitude  else cameraPositionState.position.target.latitude
                                val lng = if (gp.longitude != 0.0) gp.longitude else cameraPositionState.position.target.longitude
                                viewModel.enterRadiusMode(lat, lng)
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 14f)
                                )
                            }
                        },
                        modifier       = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 150.dp, end = 16.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            imageVector        = Icons.Default.DeleteSweep,
                            contentDescription = "Select radius for collection",
                            tint               = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

            } // Box
        } // PullToRefreshBox

        LaunchedEffect(isRefreshing) {
            if (isRefreshing) {
                delay(700)
                isRefreshing = false
            }
        }
    }
}

// =====================================================================
// ZoneOverlay
// =====================================================================

@Composable
private fun ZoneOverlay(zone: Zone) {
    val centre = LatLng(zone.centerLat, zone.centerLng)
    Circle(
        center      = centre,
        radius      = zone.radiusMeters,
        fillColor   = Color(0x2200C853),
        strokeColor = Color(0xFF00C853),
        strokeWidth = 2f
    )
    Marker(
        state   = rememberMarkerState(position = centre),
        title   = "📍 ${zone.name}",
        snippet = "${zone.radiusMeters.toInt()} m radius",
        icon    = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)
    )
}

// =====================================================================
// MapPinMarker — waste-report pin (no dismiss mode)
// =====================================================================

@Composable
private fun MapPinMarker(
    pin: MapPin,
    dateFormat: java.text.SimpleDateFormat
) {
    val position      = LatLng(pin.location.latitude, pin.location.longitude)
    val formattedTime = remember(pin.timestamp) { dateFormat.format(pin.timestamp.toDate()) }

    val markerHue = if (pin.reportType == ReportType.OVERFLOWING_BIN.displayName)
        BitmapDescriptorFactory.HUE_RED
    else
        BitmapDescriptorFactory.HUE_AZURE

    MarkerInfoWindowContent(
        state   = rememberMarkerState(position = position),
        title   = pin.category,
        snippet = "${pin.streetName}\n$formattedTime",
        icon    = BitmapDescriptorFactory.defaultMarker(markerHue)
    ) { _ ->
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (pin.reportType == ReportType.OVERFLOWING_BIN.displayName) "⚠️ " else "🗑️ ",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(pin.category, style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                pin.reportType,
                style = MaterialTheme.typography.labelSmall,
                color = if (pin.reportType == ReportType.OVERFLOWING_BIN.displayName)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            )
            Text("📍 ${pin.streetName}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("🕐 $formattedTime", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
