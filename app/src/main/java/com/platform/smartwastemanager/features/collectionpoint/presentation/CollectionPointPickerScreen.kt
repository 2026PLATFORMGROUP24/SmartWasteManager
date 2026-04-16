package com.platform.smartwastemanager.features.collectionpoint.presentation

import android.Manifest
import android.location.Geocoder
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.GeoPoint
import com.google.maps.android.compose.*
import com.platform.smartwastemanager.core.util.LocationHelper
import com.platform.smartwastemanager.features.collectionpoint.domain.CollectionPoint
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CollectionPointPickerScreen(
    viewModel: CollectionPointViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // Get all zones from the ViewModel
    val allZones by viewModel.allZones.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    var selectedLocation by remember { mutableStateOf<LatLng?>(null) }
    var streetName by remember { mutableStateOf("") }
    var pointName by remember { mutableStateOf("") }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(-25.7479, 28.2293), 12f)
    }

    // Load zones on first launch
    LaunchedEffect(Unit) {
        viewModel.loadAllZones()

        if (locationPermissions.allPermissionsGranted) {
            try {
                val loc = LocationHelper.getCurrentLocation(context)
                currentLocation = LatLng(loc.latitude, loc.longitude)
                selectedLocation = currentLocation
                cameraPositionState.position = CameraPosition.fromLatLngZoom(currentLocation!!, 15f)

                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                streetName = addresses?.firstOrNull()?.getAddressLine(0) ?: "Unknown Location"
            } catch (e: Exception) {
                // Fallback to default
            }
        } else {
            locationPermissions.launchMultiplePermissionRequest()
        }
    }

    // Show UI state messages
    LaunchedEffect(uiState) {
        // Handle UI state if needed
    }

    Scaffold(
        bottomBar = {
            Surface(
                color           = MaterialTheme.colorScheme.surface,
                tonalElevation  = 4.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value        = pointName,
                        onValueChange = { pointName = it },
                        label         = { Text("Collection Point Name (e.g. Home, Office)") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth()
                    )
                    Text(
                        text  = "📍 $streetName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Show zone assignment status
                    if (selectedLocation != null && allZones.isNotEmpty()) {
                        val assignedZone = findZoneForLocation(selectedLocation!!, allZones)
                        if (assignedZone != null) {
                            Text(
                                text  = "🗺️ Zone: ${assignedZone.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text  = "⚠️ This location is not within any zone. Ask a driver to create a zone here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick  = onNavigateBack,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick  = {
                                if (pointName.isNotBlank() && selectedLocation != null) {
                                    // Find which zone contains this location
                                    val assignedZone = findZoneForLocation(selectedLocation!!, allZones)

                                    val newPoint = CollectionPoint(
                                        name       = pointName,
                                        location   = GeoPoint(selectedLocation!!.latitude, selectedLocation!!.longitude),
                                        streetName = streetName,
                                        zoneId     = assignedZone?.id ?: "" // Assign zone based on location
                                    )
                                    viewModel.createCollectionPoint(newPoint)
                                    onNavigateBack()
                                }
                            },
                            enabled  = pointName.isNotBlank() && selectedLocation != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            GoogleMap(
                modifier            = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties          = MapProperties(isMyLocationEnabled = locationPermissions.allPermissionsGranted),
                uiSettings          = MapUiSettings(myLocationButtonEnabled = true, zoomControlsEnabled = true),
                onMapClick          = { latLng ->
                    selectedLocation = latLng
                    scope.launch {
                        try {
                            val geocoder = Geocoder(context, Locale.getDefault())
                            val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                            streetName = addresses?.firstOrNull()?.getAddressLine(0) ?: "Unknown Location"
                        } catch (e: Exception) {
                            streetName = "Unknown Location"
                        }
                    }
                }
            ) {
                // Draw all zone circles on the map
                allZones.forEach { zone ->
                    Circle(
                        center = LatLng(zone.centerLat, zone.centerLng),
                        radius = zone.radiusMeters,
                        strokeColor = androidx.compose.ui.graphics.Color.Blue.copy(alpha = 0.5f),
                        fillColor = androidx.compose.ui.graphics.Color.Blue.copy(alpha = 0.1f),
                        strokeWidth = 2f
                    )
                }

                selectedLocation?.let { loc ->
                    Marker(
                        state = MarkerState(position = loc),
                        title = "Collection Point"
                    )
                }
            }

            Surface(
                modifier        = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                color           = MaterialTheme.colorScheme.primaryContainer,
                shape           = MaterialTheme.shapes.medium,
                tonalElevation  = 4.dp
            ) {
                Text(
                    text     = "📍 Tap on the map to set your collection point location",
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * Finds which zone contains the given location.
 * Returns null if the location is not within any zone.
 */
private fun findZoneForLocation(
    location: LatLng,
    zones: List<com.platform.smartwastemanager.features.map.domain.Zone>
): com.platform.smartwastemanager.features.map.domain.Zone? {
    return zones.find { zone ->
        val distance = haversineDistance(
            location.latitude, location.longitude,
            zone.centerLat, zone.centerLng
        )
        distance <= zone.radiusMeters
    }
}

/**
 * Calculates haversine distance in meters between two GPS points.
 */
private fun haversineDistance(
    lat1: Double, lng1: Double,
    lat2: Double, lng2: Double
): Double {
    val r = 6371000.0 // Earth radius in meters
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLng / 2) * Math.sin(dLng / 2)
    return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}