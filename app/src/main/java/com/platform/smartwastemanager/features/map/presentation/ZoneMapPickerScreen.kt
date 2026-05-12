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
 * ZoneMapPickerScreen — lets the driver define a new global circular zone on the map.
 *
 * This screen ONLY creates the zone globally (stored in route_zones).
 * Assigning the zone to a schedule day is done separately in ZonePickerScreen.
 *
 * Three ways to drop the pin:
 *   A) Type a place name in the Zone Name field and press Search.
 *   B) Long-press anywhere on the map.
 *   C) Pan to the spot, then tap the [+] FAB.
 *
 * FIX: Now displays all existing zones as grey overlays so drivers can see
 *      where zones already exist and avoid overlaps.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ZoneMapPickerScreen(
    viewModel: RouteViewModel,
    driverUid: String,
    onNavigateBack: () -> Unit
) {
    val actionState   by viewModel.actionState.collectAsStateWithLifecycle()
    val allZonesState by viewModel.allZonesState.collectAsStateWithLifecycle()
    val context       = LocalContext.current
    val focusManager  = LocalFocusManager.current
    val scope         = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var zoneName     by remember { mutableStateOf("") }
    var pickedLatLng by remember { mutableStateOf<LatLng?>(null) }
    var radiusMeters by remember { mutableFloatStateOf(500f) }
    var isSearching  by remember { mutableStateOf(false) }

    val defaultPosition = LatLng(-26.2041, 28.0473) // Johannesburg fallback
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultPosition, 13f)
    }

    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // Load all existing zones so we can show them on the map
    LaunchedEffect(Unit) {
        viewModel.loadAllZones()
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
                // We navigate back immediately. 
                // ManageZonesScreen (the previous screen) also observes this viewModel 
                // and will show the success snackbar itself.
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

    fun searchAndPlacePin() {
        val query = zoneName.trim()
        if (query.isBlank()) return
        scope.launch {
            isSearching = true
            focusManager.clearFocus()
            val result = LocationHelper.getCoordinates(context, query)
            isSearching = false
            if (result != null) {
                val latLng = LatLng(result.latitude, result.longitude)
                pickedLatLng = latLng
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

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            // ---- Top bar ----
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
                    text       = "Create New Zone",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // ---- Map ----
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
                    uiSettings = MapUiSettings(
                        myLocationButtonEnabled = true,
                        zoomControlsEnabled     = true
                    ),
                    onMapLongClick = { latLng -> pickedLatLng = latLng }
                ) {
                    // ---- Show all existing zones as grey overlays ----
                    if (allZonesState is AllZonesUiState.Success) {
                        val existingZones = (allZonesState as AllZonesUiState.Success).zones
                        existingZones.forEach { zone ->
                            val center = LatLng(zone.centerLat, zone.centerLng)
                            Marker(
                                state = rememberMarkerState(position = center),
                                title = zone.name,
                                alpha = 0.5f
                            )
                            Circle(
                                center      = center,
                                radius      = zone.radiusMeters,
                                fillColor   = Color.Gray.copy(alpha = 0.15f),
                                strokeColor = Color.Gray.copy(alpha = 0.5f),
                                strokeWidth = 2f
                            )
                        }
                    }

                    // ---- Show the NEW zone being created ----
                    // key(pickedLatLng) forces the MarkerState to be recreated whenever
                    // the user taps a new location, so the pin always tracks the circle.
                    pickedLatLng?.let { centre ->
                        key(centre) {
                            Marker(
                                state = rememberMarkerState(position = centre),
                                title = zoneName.ifBlank { "New Zone Centre" }
                            )
                        }
                        Circle(
                            center      = centre,
                            radius      = radiusMeters.toDouble(),
                            fillColor   = Color(0x3000C853),
                            strokeColor = Color(0xFF00C853),
                            strokeWidth = 3f
                        )
                    }
                }

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

                // [+] FAB — Option C: pin at current camera position
                FloatingActionButton(
                    onClick = { pickedLatLng = cameraPositionState.position.target },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor   = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Place zone centre here")
                }

                if (isSearching) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        colors   = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier  = Modifier.padding(16.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Searching…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // ---- Bottom panel ----
            Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {

                    OutlinedTextField(
                        value         = zoneName,
                        onValueChange = { zoneName = it },
                        label         = { Text("Zone Name / Search") },
                        placeholder   = { Text("e.g. Sandton, Main Road Soweto…") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        trailingIcon  = {
                            if (zoneName.isNotBlank()) {
                                if (isSearching) {
                                    CircularProgressIndicator(
                                        modifier    = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    IconButton(onClick = { searchAndPlacePin() }) {
                                        Icon(Icons.Default.Search, contentDescription = "Search")
                                    }
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { searchAndPlacePin() })
                    )

                    Text(
                        text  = "💡 Type an area name and press Search (⌕) to find & pin it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )

                    // Radius slider
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

                    // Show existing zones count
                    if (allZonesState is AllZonesUiState.Success) {
                        val count = (allZonesState as AllZonesUiState.Success).zones.size
                        Text(
                            text  = "ℹ️ $count existing zone${if (count != 1) "s" else ""} shown in grey",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    pickedLatLng?.let {
                        Text(
                            text  = "📍 ${String.format("%.5f", it.latitude)}, " +
                                    "${String.format("%.5f", it.longitude)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    val isSaving = actionState is RouteActionState.Loading
                    Button(
                        onClick = {
                            val centre = pickedLatLng ?: return@Button
                            viewModel.addGlobalZone(
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
