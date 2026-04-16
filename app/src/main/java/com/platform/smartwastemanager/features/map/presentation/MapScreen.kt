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
import com.google.maps.android.compose.*
import com.platform.smartwastemanager.core.util.LocationHelper
import com.platform.smartwastemanager.features.map.domain.MapPin
import com.platform.smartwastemanager.features.map.domain.Zone
import com.platform.smartwastemanager.features.report.domain.ReportType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Map screen — shows pending waste report pins and, for drivers in driver view,
 * also shows their zone circles as overlays.
 *
 * @param driverUid  UID of the signed-in user. Pass empty string for non-drivers.
 *                   Used to load zone overlays for drivers.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    isDriverInDriverView: Boolean,
    driverUid: String = ""
) {
    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()
    val isDismissMode by viewModel.isDismissMode.collectAsStateWithLifecycle()
    val pinToConfirm  by viewModel.pinToConfirmDismiss.collectAsStateWithLifecycle()
    val driverZones   by viewModel.driverZones.collectAsStateWithLifecycle()

    val context           = LocalContext.current
    val scope             = rememberCoroutineScope()
    val focusManager      = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    val defaultPosition = LatLng(-26.2041, 28.0473)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultPosition, 11f)
    }

    // Centre on device location when screen opens
    LaunchedEffect(Unit) {
        if (locationPermissions.allPermissionsGranted) {
            val geoPoint = LocationHelper.getCurrentLocation(context)
            if (geoPoint.latitude != 0.0 || geoPoint.longitude != 0.0) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(geoPoint.latitude, geoPoint.longitude), 15f
                    )
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
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(geoPoint.latitude, geoPoint.longitude), 15f
                    )
                )
            }
        }
    }

    // Load / clear driver zones based on view mode
    LaunchedEffect(isDriverInDriverView, driverUid) {
        if (isDriverInDriverView && driverUid.isNotBlank()) {
            viewModel.loadDriverZones(driverUid)
        } else {
            viewModel.clearDriverZones()
        }
    }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    // ---- Dismiss confirmation dialog ----
    if (pinToConfirm != null && isDriverInDriverView) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDismiss() },
            title = { Text("Dismiss Report?") },
            text  = {
                Text(
                    "Mark this ${pinToConfirm!!.category} report at " +
                            "'${pinToConfirm!!.streetName}' as dismissed?\n\n" +
                            "The pin will be removed from the map."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDismiss() }) {
                    Text("Dismiss", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDismiss() }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.loadPins()
                if (isDriverInDriverView && driverUid.isNotBlank()) {
                    viewModel.loadDriverZones(driverUid)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // ================================================================
            // GOOGLE MAP
            // ================================================================
            GoogleMap(
                modifier            = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties          = MapProperties(
                    isMyLocationEnabled = locationPermissions.allPermissionsGranted
                ),
                uiSettings = MapUiSettings(
                    myLocationButtonEnabled = false,
                    zoomControlsEnabled     = true
                )
            ) {
                // ---- Waste report pins (everyone) ----
                if (uiState is MapUiState.Success) {
                    val pins = (uiState as MapUiState.Success).pins
                    pins.forEach { pin ->
                        MapPinMarker(
                            pin           = pin,
                            isDismissMode = isDismissMode && isDriverInDriverView,
                            dateFormat    = dateFormat,
                            onDismissTap  = { viewModel.onPinTappedForDismiss(pin) }
                        )
                    }
                }

                // ---- Driver zone overlays (driver view only) ----
                if (isDriverInDriverView) {
                    driverZones.forEach { zone ->
                        ZoneOverlay(zone = zone)
                    }
                }
            }

            // ================================================================
            // SEARCH BAR
            // ================================================================
            Card(
                modifier  = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape     = RoundedCornerShape(24.dp),
                colors    = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                        keyboardActions = KeyboardActions(
                            onSearch = {
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
                            }
                        )
                    )
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                }
            }

            // ================================================================
            // ZONE LEGEND — shown when driver view is active and zones exist
            // ================================================================
            if (isDriverInDriverView && driverZones.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 80.dp, start = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                            .copy(alpha = 0.93f)
                    ),
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier          = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Small green circle swatch — background import now present
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = Color(0xFF00C853),
                                    shape = CircleShape
                                )
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

            // ================================================================
            // LOADING / ERROR states
            // ================================================================
            when (val state = uiState) {
                is MapUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is MapUiState.Error -> {
                    Card(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        colors   = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier            = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
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
                                .padding(top = 80.dp, start = 16.dp, end = 16.dp),
                            colors   = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                "✅ No pending waste reports",
                                style    = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            // ================================================================
            // DRIVER OVERLAYS (dismiss mode banner + toggle FAB)
            // ================================================================
            if (isDriverInDriverView) {
                if (isDismissMode) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 80.dp, start = 16.dp, end = 16.dp),
                        colors   = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            "🗑️ Dismiss Mode — tap a pin to dismiss it",
                            style    = MaterialTheme.typography.bodyMedium,
                            color    = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                SmallFloatingActionButton(
                    onClick        = { viewModel.toggleDismissMode() },
                    modifier       = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 80.dp, end = 16.dp),
                    containerColor = if (isDismissMode)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector        = if (isDismissMode) Icons.Default.Close
                        else Icons.Default.DeleteSweep,
                        contentDescription = if (isDismissMode) "Exit dismiss mode"
                        else "Enter dismiss mode",
                        tint               = if (isDismissMode) Color.White
                        else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // ================================================================
            // MY LOCATION FAB
            // ================================================================
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
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = "My Location",
                    tint               = MaterialTheme.colorScheme.onSecondaryContainer
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

// =====================================================================
// ZoneOverlay — draws one zone circle + centre marker on the map
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
// MapPinMarker — waste report pin
// =====================================================================

@Composable
private fun MapPinMarker(
    pin: MapPin,
    isDismissMode: Boolean,
    dateFormat: java.text.SimpleDateFormat,
    onDismissTap: () -> Unit
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
        icon    = BitmapDescriptorFactory.defaultMarker(markerHue),
        onClick = { _ -> if (isDismissMode) { onDismissTap(); true } else false }
    ) { _ ->
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (pin.reportType == ReportType.OVERFLOWING_BIN.displayName) "⚠️ " else "🗑️ ",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    pin.category,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
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
            Text(
                "📍 ${pin.streetName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "🕐 $formattedTime",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
