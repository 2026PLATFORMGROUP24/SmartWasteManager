package com.platform.smartwastemanager.features.map.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.platform.smartwastemanager.core.util.LocationHelper
import com.platform.smartwastemanager.features.map.domain.MapPin
import com.platform.smartwastemanager.features.report.domain.ReportType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Map screen.
 *
 * Shows pending waste reports as pins on the map for both users and drivers.
 * Drivers in "Driver View" have additional management capabilities (Dismiss Mode).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    isDriverInDriverView: Boolean
) {
    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()
    val isDismissMode by viewModel.isDismissMode.collectAsStateWithLifecycle()
    val pinToConfirm  by viewModel.pinToConfirmDismiss.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    // Default position (Johannesburg) used as fallback
    val defaultPosition = LatLng(-26.2041, 28.0473)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultPosition, 11f)
    }

    // Default to current location on start
    LaunchedEffect(Unit) {
        val geoPoint = LocationHelper.getCurrentLocation(context)
        if (geoPoint.latitude != 0.0 || geoPoint.longitude != 0.0) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(geoPoint.latitude, geoPoint.longitude),
                    15f
                )
            )
        }
    }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    // ---- Dismiss confirmation dialog (driver only) ----
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
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            // ---- Unified Map (Everyone sees pins now) ----
            GoogleMap(
                modifier            = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = true),
                uiSettings = MapUiSettings(
                    myLocationButtonEnabled = false,
                    zoomControlsEnabled = true
                )
            ) {
                if (uiState is MapUiState.Success) {
                    val pins = (uiState as MapUiState.Success).pins
                    pins.forEach { pin ->
                        MapPinMarker(
                            pin           = pin,
                            // Only allow dismiss interaction if driver is in driver view
                            isDismissMode = isDismissMode && isDriverInDriverView,
                            dateFormat    = dateFormat,
                            onDismissTap  = { viewModel.onPinTappedForDismiss(pin) }
                        )
                    }
                }
            }

            // ---- Search Bar (Top) ----
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search location...") },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        singleLine = true,
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
                                                    LatLng(result.latitude, result.longitude),
                                                    15f
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
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                }
            }

            // ---- Loading / Error Indicators ----
            when (val state = uiState) {
                is MapUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is MapUiState.Error -> {
                    Card(
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.message, color = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.loadPins() }) { Text("Retry") }
                        }
                    }
                }
                is MapUiState.Success -> {
                    // ---- Empty state message (Hidden for Drivers) ----
                    if (state.pins.isEmpty() && !isDriverInDriverView) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 80.dp, start = 16.dp, end = 16.dp), // Pushed down due to search bar
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text     = "✅ No pending waste reports",
                                style    = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            // ---- Driver-only Management Overlays ----
            if (isDriverInDriverView) {
                // Dismiss mode banner
                if (isDismissMode) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 80.dp, start = 16.dp, end = 16.dp), // Pushed down
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text     = "🗑️ Dismiss Mode — tap a pin to dismiss it",
                            style    = MaterialTheme.typography.bodyMedium,
                            color    = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Dismiss mode toggle FAB (Top Right, pushed down a bit)
                SmallFloatingActionButton(
                    onClick        = { viewModel.toggleDismissMode() },
                    modifier       = Modifier.align(Alignment.TopEnd).padding(top = 80.dp, end = 16.dp),
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

            // ---- Bottom Left Controls (Current Location) ----
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        val geoPoint = LocationHelper.getCurrentLocation(context)
                        if (geoPoint.latitude != 0.0 || geoPoint.longitude != 0.0) {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(geoPoint.latitude, geoPoint.longitude),
                                    15f
                                )
                            )
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "My Location",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun MapPinMarker(
    pin: MapPin,
    isDismissMode: Boolean,
    dateFormat: java.text.SimpleDateFormat,
    onDismissTap: () -> Unit
) {
    val position      = LatLng(pin.location.latitude, pin.location.longitude)
    val formattedTime = remember(pin.timestamp) { dateFormat.format(pin.timestamp.toDate()) }

    // Logic for marker color
    val markerHue = if (pin.reportType == ReportType.OVERFLOWING_BIN.displayName) {
        BitmapDescriptorFactory.HUE_RED
    } else {
        BitmapDescriptorFactory.HUE_GREEN
    }

    MarkerInfoWindowContent(
        state   = rememberMarkerState(position = position),
        title   = pin.category,
        snippet = "${pin.streetName}\n$formattedTime",
        icon    = BitmapDescriptorFactory.defaultMarker(markerHue),
        onClick = { _ ->
            if (isDismissMode) { onDismissTap(); true } else false
        }
    ) { _ ->
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text  = if (pin.reportType == ReportType.OVERFLOWING_BIN.displayName) "⚠️ " else "🗑️ ",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text  = pin.category,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text  = pin.reportType,
                style = MaterialTheme.typography.labelSmall,
                color = if (pin.reportType == ReportType.OVERFLOWING_BIN.displayName)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            )
            Text(
                text  = "📍 ${pin.streetName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text  = "🕐 $formattedTime",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
