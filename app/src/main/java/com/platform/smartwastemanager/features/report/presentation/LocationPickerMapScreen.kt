package com.platform.smartwastemanager.features.report.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
 * LocationPickerMapScreen — a full-screen map picker for the waste report form.
 *
 * The user sees their current GPS location as the default pin position.
 * A stationary crosshair overlay in the map centre always shows the exact
 * coordinates that will be submitted — dragging the map moves the world
 * under the crosshair, giving precise visual feedback at all times.
 *
 * Features:
 *   • Auto-GPS: opens centred on the device's current location.
 *   • Search: type a street/place name to jump to it.
 *   • "Use My GPS" FAB: instantly re-centres on device location.
 *   • Live street name preview: reverse-geocodes the crosshair position
 *     as the user drags, so they always know the street that will be saved.
 *   • "Confirm This Location" button: commits the pin and returns to form.
 *
 * @param viewModel      ReportViewModel — setManualLocation() is called on confirm.
 * @param onNavigateBack Pop back to ReportFormScreen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun LocationPickerMapScreen(
    viewModel: ReportViewModel,
    onNavigateBack: () -> Unit
) {
    val context      = LocalContext.current
    val scope        = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val isLocating      by viewModel.isLocating.collectAsStateWithLifecycle()
    val existingLocation by viewModel.location.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // ---- Search state ----
    var searchQuery  by remember { mutableStateOf("") }
    var isSearching  by remember { mutableStateOf(false) }

    // ---- Live preview of the street at the crosshair position ----
    // Updated every time the camera stops moving (idle).
    var previewStreetName by remember { mutableStateOf("Drag map to select location…") }
    var isReverseGeocoding by remember { mutableStateOf(false) }

    // ---- Map camera ----
    // Start at the existing location if already set, otherwise Johannesburg fallback.
    val startLatLng = if (existingLocation.latitude != 0.0 || existingLocation.longitude != 0.0) {
        LatLng(existingLocation.latitude, existingLocation.longitude)
    } else {
        LatLng(-26.2041, 28.0473)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(startLatLng, 16f)
    }

    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // Move to device GPS location when screen opens (if permission granted and no prior pick)
    LaunchedEffect(Unit) {
        if (locationPermissions.allPermissionsGranted &&
            existingLocation.latitude == 0.0 && existingLocation.longitude == 0.0
        ) {
            val geoPoint = LocationHelper.getCurrentLocation(context)
            if (geoPoint.latitude != 0.0 || geoPoint.longitude != 0.0) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(geoPoint.latitude, geoPoint.longitude), 16f
                    )
                )
            }
        } else if (!locationPermissions.allPermissionsGranted) {
            locationPermissions.launchMultiplePermissionRequest()
        }
    }

    // Reverse-geocode the crosshair position whenever the camera stops moving.
    // CameraPositionState.isMoving becomes false when the user lifts their finger.
    val isMapMoving = cameraPositionState.isMoving
    LaunchedEffect(isMapMoving) {
        if (!isMapMoving) {
            // Camera just stopped — reverse-geocode the centre position
            val centre = cameraPositionState.position.target
            isReverseGeocoding = true
            val street = LocationHelper.getStreetName(
                context,
                com.google.firebase.firestore.GeoPoint(centre.latitude, centre.longitude)
            )
            previewStreetName = street
            isReverseGeocoding = false
        }
    }

    // Helper: search for a place and fly the camera there
    fun searchLocation() {
        val query = searchQuery.trim()
        if (query.isBlank()) return
        scope.launch {
            isSearching = true
            focusManager.clearFocus()
            val result = LocationHelper.getCoordinates(context, query)
            isSearching = false
            if (result != null) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(result.latitude, result.longitude), 16f
                    )
                )
            } else {
                snackbarHostState.showSnackbar("Location \"$query\" not found. Try a different search.")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            // ================================================================
            // GOOGLE MAP — fills entire screen
            // ================================================================
            GoogleMap(
                modifier            = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties          = MapProperties(
                    isMyLocationEnabled = locationPermissions.allPermissionsGranted,
                    mapType             = com.google.android.gms.maps.GoogleMap.MAP_TYPE_NORMAL,
                    isBuildingsEnabled  = true,
                    isTrafficEnabled    = false
                ),
                uiSettings = MapUiSettings(
                    myLocationButtonEnabled = false,    // we have our own GPS FAB
                    zoomControlsEnabled     = true,
                    scrollGesturesEnabled   = true,
                    zoomGesturesEnabled     = true,
                    tiltGesturesEnabled     = false,
                    rotationGesturesEnabled = false     // keep north-up for clarity
                )
            )
            // No markers inside the GoogleMap block — the crosshair overlay IS the pin.
            // This approach means the pin never "snaps" — it's always exactly centred.

            // ================================================================
            // CROSSHAIR OVERLAY — always centred, always shows where pin will go
            // ================================================================
            CrosshairPin(
                modifier = Modifier.align(Alignment.Center)
            )

            // ================================================================
            // TOP BAR — back button + title
            // ================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f))
                    .padding(start = 4.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = "Pick Report Location",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text  = "Drag map so the  ＋  is on the waste location",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // ================================================================
            // SEARCH BAR — below the top bar
            // ================================================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp, start = 12.dp, end = 12.dp),
                shape     = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors    = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier          = Modifier
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    TextField(
                        value         = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder   = { Text("Search street or area…", style = MaterialTheme.typography.bodySmall) },
                        modifier      = Modifier.weight(1f),
                        singleLine    = true,
                        colors        = TextFieldDefaults.colors(
                            focusedContainerColor   = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor   = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { searchLocation() })
                    )
                    when {
                        isSearching -> CircularProgressIndicator(
                            modifier = Modifier.size(20.dp), strokeWidth = 2.dp
                        )
                        searchQuery.isNotEmpty() -> IconButton(
                            onClick  = { searchQuery = "" },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                        else -> IconButton(
                            onClick  = { searchLocation() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // ================================================================
            // GPS FAB — bottom-start — snaps camera back to device location
            // ================================================================
            FloatingActionButton(
                onClick = {
                    if (locationPermissions.allPermissionsGranted) {
                        scope.launch {
                            val geoPoint = LocationHelper.getCurrentLocation(context)
                            if (geoPoint.latitude != 0.0 || geoPoint.longitude != 0.0) {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(geoPoint.latitude, geoPoint.longitude), 16f
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
                    .padding(start = 16.dp, bottom = 200.dp),   // above the bottom card
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor   = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                if (isLocating) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.MyLocation, contentDescription = "Use my GPS location")
                }
            }

            // ================================================================
            // BOTTOM CONFIRMATION CARD
            // Shows the live street name + Confirm button.
            // Always fully visible — floats over the bottom of the map.
            // ================================================================
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
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {

                    // ---- Selected location preview ----
                    Text(
                        text  = "Selected Location",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Card(
                        colors = CardDefaults.cardColors(
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
                                modifier           = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                if (isReverseGeocoding || cameraPositionState.isMoving) {
                                    // Show a subtle shimmer while the map is moving
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 1.5.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text  = "Locating…",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                } else {
                                    Text(
                                        text       = previewStreetName,
                                        style      = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color      = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                // Show the raw coordinates for precision
                                val centre = cameraPositionState.position.target
                                Text(
                                    text  = "${String.format("%.5f", centre.latitude)}, " +
                                            "${String.format("%.5f", centre.longitude)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // ---- Confirm button ----
                    // Disabled while the map is moving or while reverse-geocoding.
                    val canConfirm = !cameraPositionState.isMoving && !isReverseGeocoding
                    Button(
                        onClick = {
                            // Capture the exact camera centre at the moment of tap
                            val confirmedLatLng = cameraPositionState.position.target
                            viewModel.setManualLocation(context, confirmedLatLng)
                            onNavigateBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = canConfirm,
                        colors  = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text      = "Confirm This Location",
                            style     = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * The crosshair/pin overlay that sits statically in the centre of the map.
 *
 * Consists of:
 *   - A vertical line (the pin shaft)
 *   - A filled circle at the top (the pin head)
 *   - A small shadow dot at the bottom (the pin point touching the ground)
 *
 * Because this is drawn OVER the map (not as a map marker), it never drifts
 * or lags — it is always pixel-perfectly centred, showing exactly what
 * coordinates the camera centre is pointing at.
 */
@Composable
private fun CrosshairPin(modifier: Modifier = Modifier) {
    Box(
        modifier          = modifier,
        contentAlignment  = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.offset(y = (-20).dp) // offset so pin tip = map centre
        ) {
            // Pin head (filled circle)
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.LocationOn,
                    contentDescription = "Report location",
                    tint               = MaterialTheme.colorScheme.onPrimary,
                    modifier           = Modifier.size(14.dp)
                )
            }
            // Pin shaft
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(20.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            // Shadow dot — the actual point touching the map
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
            )
        }
    }
}
