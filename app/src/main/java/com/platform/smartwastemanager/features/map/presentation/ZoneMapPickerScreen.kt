package com.platform.smartwastemanager.features.map.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.platform.smartwastemanager.core.util.LocationHelper

/**
 * ZoneMapPickerScreen — lets the driver visually pick a zone centre on the map.
 *
 * The driver:
 *   1. Long-presses (or uses "Set Here" button) to place a marker at the zone centre.
 *   2. Uses a slider to set the radius.
 *   3. Types a name for the zone.
 *   4. Taps "Save Zone".
 *
 * A semi-transparent green circle (drawn as a GoogleMaps Circle) shows the zone boundary.
 *
 * @param viewModel     RouteViewModel — addZone is called on save.
 * @param driverUid     Current driver's UID.
 * @param onNavigateBack Pop back to ZoneListScreen (zone list will refresh via Firestore listener).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ZoneMapPickerScreen(
    viewModel: RouteViewModel,
    driverUid: String,
    onNavigateBack: () -> Unit
) {
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val context     = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // ---- Form state ----
    var zoneName     by remember { mutableStateOf("") }
    var pickedLatLng by remember { mutableStateOf<LatLng?>(null) }
    var radiusMeters by remember { mutableFloatStateOf(500f) }  // slider 100–5000 m

    // ---- Map state ----
    val defaultPosition = LatLng(-26.2041, 28.0473)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultPosition, 13f)
    }

    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // Move camera to current location on start
    LaunchedEffect(Unit) {
        if (locationPermissions.allPermissionsGranted) {
            val geoPoint = LocationHelper.getCurrentLocation(context)
            if (geoPoint.latitude != 0.0 || geoPoint.longitude != 0.0) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(geoPoint.latitude, geoPoint.longitude), 14f
                    )
                )
            }
        } else {
            locationPermissions.launchMultiplePermissionRequest()
        }
    }

    // ---- React to save success: navigate back ----
    LaunchedEffect(actionState) {
        when (actionState) {
            is RouteActionState.Success -> {
                snackbarHostState.showSnackbar((actionState as RouteActionState.Success).message)
                viewModel.resetActionState()
                onNavigateBack()        // Return to zone list after successful save
            }
            is RouteActionState.Error -> {
                snackbarHostState.showSnackbar((actionState as RouteActionState.Error).message)
                viewModel.resetActionState()
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

            // ---- Top Bar ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 4.dp, end = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text       = "Pick Zone Area",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // ---- Map (takes most of the screen) ----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(
                        isMyLocationEnabled = locationPermissions.allPermissionsGranted
                    ),
                    uiSettings = MapUiSettings(
                        myLocationButtonEnabled = true,
                        zoomControlsEnabled = true
                    ),
                    onMapLongClick = { latLng ->
                        // Long-press on the map to set the zone centre
                        pickedLatLng = latLng
                    }
                ) {
                    pickedLatLng?.let { centre ->
                        // Pin at zone centre
                        Marker(
                            state = rememberMarkerState(position = centre),
                            title = zoneName.ifBlank { "Zone Centre" }
                        )
                        // Semi-transparent green circle showing the zone boundary
                        Circle(
                            center      = centre,
                            radius      = radiusMeters.toDouble(),
                            fillColor   = Color(0x3000C853),  // 19% opacity green
                            strokeColor = Color(0xFF00C853),  // solid green border
                            strokeWidth = 3f
                        )
                    }
                }

                // ---- "Use Current Location" button overlaid on map ----
                if (locationPermissions.allPermissionsGranted) {
                    SmallFloatingActionButton(
                        onClick = {
                            // Place zone centre at device's current location
                            // We read the camera's current position as a reasonable proxy
                            pickedLatLng = cameraPositionState.position.target
                        },
                        modifier       = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Use current map centre as zone centre",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // ---- Instruction overlay ----
                if (pickedLatLng == null) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text     = "👆 Long-press on the map to place the zone centre",
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            // ---- Bottom panel: name + radius + save ----
            Surface(
                tonalElevation = 3.dp,
                modifier       = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {

                    // Zone name field
                    OutlinedTextField(
                        value         = zoneName,
                        onValueChange = { zoneName = it },
                        label         = { Text("Zone Name") },
                        placeholder   = { Text("e.g. North Sector, Main Road Area") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Radius slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text  = "Radius:",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(60.dp)
                        )
                        Slider(
                            value         = radiusMeters,
                            onValueChange = { radiusMeters = it },
                            valueRange    = 100f..5000f,
                            steps         = 48,           // steps of ~100 m
                            modifier      = Modifier.weight(1f)
                        )
                        Text(
                            text     = "${radiusMeters.toInt()} m",
                            style    = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .width(64.dp)
                                .padding(start = 8.dp)
                        )
                    }

                    // Show picked location for driver's reference
                    pickedLatLng?.let {
                        Text(
                            text  = "📍 ${String.format("%.5f", it.latitude)}, ${String.format("%.5f", it.longitude)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Save button
                    val isSaving = actionState is RouteActionState.Loading
                    Button(
                        onClick = {
                            val centre = pickedLatLng
                            if (centre == null) {
                                return@Button
                            }
                            viewModel.addZone(
                                name          = zoneName,
                                centerLat     = centre.latitude,
                                centerLng     = centre.longitude,
                                radiusMeters  = radiusMeters.toDouble(),
                                driverUid     = driverUid
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled  = !isSaving && pickedLatLng != null && zoneName.isNotBlank()
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isSaving) "Saving..." else "Save Zone")
                    }
                }
            }
        }
    }
}