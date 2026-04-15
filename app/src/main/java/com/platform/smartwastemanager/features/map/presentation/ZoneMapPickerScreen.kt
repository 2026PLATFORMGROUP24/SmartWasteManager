package com.platform.smartwastemanager.features.map.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.platform.smartwastemanager.core.util.LocationHelper
import kotlinx.coroutines.launch

/**
 * ZoneMapPickerScreen — lets the driver define a circular zone area on the map.
 *
 * HOW TO USE:
 *   Option A — Search by name (NEW):
 *     Type a place name or area (e.g. "Sandton", "Main Road Soweto") in the
 *     Zone Name field, then press the Search key on the keyboard. The map will
 *     fly to that location and automatically drop the zone centre pin there.
 *     You can still edit the name afterwards.
 *
 *   Option B — Long-press on the map:
 *     Long-press anywhere on the map to drop/move the zone centre pin.
 *
 *   Option C — Pan + [+] FAB:
 *     Pan the map to the desired location, then tap the green [+] FAB at the
 *     bottom-left to place the pin at the current camera centre.
 *
 *   After placing a pin, adjust the radius slider, confirm the name, and tap Save Zone.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ZoneMapPickerScreen(
    viewModel: RouteViewModel,
    driverUid: String,
    onNavigateBack: () -> Unit
) {
    val actionState    by viewModel.actionState.collectAsStateWithLifecycle()
    val context        = LocalContext.current
    val focusManager   = LocalFocusManager.current
    val scope          = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // ---- Form state ----
    var zoneName         by remember { mutableStateOf("") }
    var pickedLatLng     by remember { mutableStateOf<LatLng?>(null) }
    var radiusMeters     by remember { mutableFloatStateOf(500f) }
    var isSearching      by remember { mutableStateOf(false) }

    // ---- Map state ----
    val defaultPosition = LatLng(-26.2041, 28.0473)   // Johannesburg fallback
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultPosition, 13f)
    }

    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // Move camera to device's current location when the screen first opens
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

    // Navigate back after a successful save
    LaunchedEffect(actionState) {
        when (actionState) {
            is RouteActionState.Success -> {
                snackbarHostState.showSnackbar(
                    (actionState as RouteActionState.Success).message
                )
                viewModel.resetActionState()
                onNavigateBack()
            }
            is RouteActionState.Error -> {
                snackbarHostState.showSnackbar(
                    (actionState as RouteActionState.Error).message
                )
                viewModel.resetActionState()
            }
            else -> {}
        }
    }

    /**
     * Geocodes [zoneName] using the Android Geocoder (same helper used by MapScreen).
     * On success: flies the camera to the result, drops the pin there.
     * On failure: shows a snackbar explaining the issue.
     *
     * The zone name text is NOT changed — the driver typed it as the name AND
     * used it for the search, which is intentional. They can edit after if needed.
     */
    fun searchAndPlacePin() {
        val query = zoneName.trim()
        if (query.isBlank()) return
        scope.launch {
            isSearching = true
            focusManager.clearFocus()
            // LocationHelper.getCoordinates is the existing Geocoder helper used by MapScreen
            val result = LocationHelper.getCoordinates(context, query)
            isSearching = false
            if (result != null) {
                val latLng = LatLng(result.latitude, result.longitude)
                // Drop the pin at the found location
                pickedLatLng = latLng
                // Fly the camera to the result at a neighbourhood-level zoom
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(latLng, 15f)
                )
            } else {
                snackbarHostState.showSnackbar(
                    "Location \"$query\" not found. Try a different name or long-press on the map."
                )
            }
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

            // ---- Map (takes the upper portion of the screen) ----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                GoogleMap(
                    modifier            = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties          = MapProperties(
                        isMyLocationEnabled = locationPermissions.allPermissionsGranted
                    ),
                    uiSettings          = MapUiSettings(
                        myLocationButtonEnabled = true,
                        zoomControlsEnabled     = true
                    ),
                    onMapLongClick = { latLng ->
                        // Option B: long-press anywhere to drop / move the zone centre pin
                        pickedLatLng = latLng
                    }
                ) {
                    pickedLatLng?.let { centre ->
                        // Pin at the chosen zone centre
                        Marker(
                            state = rememberMarkerState(position = centre),
                            title = zoneName.ifBlank { "Zone Centre" }
                        )
                        // Semi-transparent green circle previewing the zone boundary
                        Circle(
                            center      = centre,
                            radius      = radiusMeters.toDouble(),
                            fillColor   = Color(0x3000C853),   // ~19% opacity green
                            strokeColor = Color(0xFF00C853),   // solid green border
                            strokeWidth = 3f
                        )
                    }
                }

                // ---- Instruction hint (shown until the first pin is placed) ----
                if (pickedLatLng == null) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                                .copy(alpha = 0.93f)
                        )
                    ) {
                        Text(
                            text     = "🔍 Search by name below  •  long-press map  •  or tap ＋",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // ---- [+] FAB (bottom-left) — Option C: pin at current camera centre ----
                FloatingActionButton(
                    onClick = {
                        pickedLatLng = cameraPositionState.position.target
                    },
                    modifier       = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor   = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector        = Icons.Default.Add,
                        contentDescription = "Place zone centre at current map position"
                    )
                }

                // ---- Search loading indicator (shown while geocoding) ----
                if (isSearching) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Searching…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // ---- Bottom panel: zone name (with search) + radius + save ----
            Surface(
                tonalElevation = 3.dp,
                modifier       = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {

                    // ---- Zone Name field — ALSO serves as a place search box ----
                    // Pressing the Search keyboard action geocodes the name and flies
                    // the camera to the result, placing the zone centre pin there.
                    OutlinedTextField(
                        value         = zoneName,
                        onValueChange = { zoneName = it },
                        label         = { Text("Zone Name / Search") },
                        placeholder   = { Text("e.g. Sandton, Main Road Soweto…") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        // Show a search icon on the right — tapping it triggers geocoding
                        trailingIcon  = {
                            if (zoneName.isNotBlank()) {
                                if (isSearching) {
                                    CircularProgressIndicator(
                                        modifier    = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    IconButton(onClick = { searchAndPlacePin() }) {
                                        Icon(
                                            imageVector        = Icons.Default.Search,
                                            contentDescription = "Search for this location"
                                        )
                                    }
                                }
                            }
                        },
                        // Pressing the Search key on the keyboard also triggers geocoding
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { searchAndPlacePin() }
                        )
                    )

                    // Helper text explaining what pressing Search does
                    Text(
                        text  = "💡 Type an area name and press Search (⌕) to find & pin it on the map",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )

                    // ---- Radius slider (100 m – 5 000 m) ----
                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text     = "Radius:",
                            style    = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(60.dp)
                        )
                        Slider(
                            value         = radiusMeters,
                            onValueChange = { radiusMeters = it },
                            valueRange    = 100f..5000f,
                            steps         = 48,
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

                    // Picked coordinates for driver reference
                    pickedLatLng?.let {
                        Text(
                            text  = "📍 ${String.format("%.5f", it.latitude)}, " +
                                    "${String.format("%.5f", it.longitude)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    // ---- Save Zone button ----
                    val isSaving = actionState is RouteActionState.Loading
                    Button(
                        onClick = {
                            val centre = pickedLatLng ?: return@Button
                            viewModel.addZone(
                                name         = zoneName,
                                centerLat    = centre.latitude,
                                centerLng    = centre.longitude,
                                radiusMeters = radiusMeters.toDouble(),
                                driverUid    = driverUid
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled  = !isSaving && pickedLatLng != null && zoneName.isNotBlank()
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color       = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isSaving) "Saving…" else "Save Zone")
                    }
                }
            }
        }
    }
}