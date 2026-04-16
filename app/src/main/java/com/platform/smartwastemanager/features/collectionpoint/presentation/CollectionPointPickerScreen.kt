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

/**
 * Map picker for creating a new collection point.
 *
 * User:
 * 1. Picks a location on the map by dragging.
 * 2. Names the collection point (e.g. "Home", "Office").
 * 3. System auto-assigns the zone based on the location (finds which zone polygon contains the point).
 * 4. Saves the collection point.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CollectionPointPickerScreen(
    viewModel: CollectionPointViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

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

    // Camera state
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(-25.7479, 28.2293), 12f) // Pretoria default
    }

    LaunchedEffect(Unit) {
        if (locationPermissions.allPermissionsGranted) {
            try {
                val loc = LocationHelper.getCurrentLocation(context)
                currentLocation = LatLng(loc.latitude, loc.longitude)
                selectedLocation = currentLocation
                cameraPositionState.position = CameraPosition.fromLatLngZoom(currentLocation!!, 15f)

                // Reverse geocode
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
                                    // TODO: Determine zoneId based on selectedLocation
                                    // For now, leave empty — driver will assign zones separately
                                    val newPoint = CollectionPoint(
                                        name       = pointName,
                                        location   = GeoPoint(selectedLocation!!.latitude, selectedLocation!!.longitude),
                                        streetName = streetName,
                                        zoneId     = "" // Will be assigned by system/driver
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
                selectedLocation?.let { loc ->
                    Marker(
                        state = MarkerState(position = loc),
                        title = "Collection Point"
                    )
                }
            }

            // Instruction hint
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