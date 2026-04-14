package com.platform.smartwastemanager.features.map.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.platform.smartwastemanager.features.map.domain.MapPin
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Map screen.
 *
 * [isDriverInDriverView] controls visibility of pending report pins:
 *   - true  → driver in driver view: pins visible + dismiss FAB shown
 *   - false → regular user OR driver in user-view: empty map shown
 *
 * Design decision: users can still open the map tab (it's in the bottom nav
 * for all roles), but they see a clean map with no pending report locations.
 * This preserves privacy — waste report GPS coordinates are driver-only data.
 */
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    isDriverInDriverView: Boolean          // renamed from isDriver for clarity
) {
    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()
    val isDismissMode by viewModel.isDismissMode.collectAsStateWithLifecycle()
    val pinToConfirm  by viewModel.pinToConfirmDismiss.collectAsStateWithLifecycle()

    val defaultPosition     = LatLng(-26.2041, 28.0473)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultPosition, 11f)
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

    Box(modifier = Modifier.fillMaxSize()) {

        // ---- User view: clean map with no pins ----
        if (!isDriverInDriverView) {
            GoogleMap(
                modifier            = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState
            )
            // Friendly info card for users
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text     = "🗺️ Map — your local area",
                    style    = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
            return@Box
        }

        // ---- Driver view: full map with pending pins ----
        when (val state = uiState) {

            is MapUiState.Loading -> {
                // Still show the map underneath while loading
                GoogleMap(
                    modifier            = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState
                )
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            is MapUiState.Error -> {
                GoogleMap(
                    modifier            = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState
                )
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadPins() }) { Text("Retry") }
                }
            }

            is MapUiState.Success -> {
                GoogleMap(
                    modifier            = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState
                ) {
                    state.pins.forEach { pin ->
                        MapPinMarker(
                            pin           = pin,
                            isDismissMode = isDismissMode,
                            dateFormat    = dateFormat,
                            onDismissTap  = { viewModel.onPinTappedForDismiss(pin) }
                        )
                    }
                }

                // ---- No pins message ----
                if (state.pins.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp, start = 16.dp, end = 16.dp),
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

                // ---- Dismiss mode banner ----
                if (isDismissMode) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp, start = 16.dp, end = 16.dp),
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

                // ---- Dismiss mode FAB ----
                FloatingActionButton(
                    onClick        = { viewModel.toggleDismissMode() },
                    modifier       = Modifier.align(Alignment.BottomEnd).padding(16.dp),
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

                // ---- Pin count badge ----
                Card(
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text     = "📍 ${state.pins.size} pending report${if (state.pins.size != 1) "s" else ""}",
                        style    = MaterialTheme.typography.labelMedium,
                        color    = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
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

    MarkerInfoWindowContent(
        state   = rememberMarkerState(position = position),
        title   = pin.category,
        snippet = "${pin.streetName}\n$formattedTime",
        icon    = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
        onClick = { _ ->
            if (isDismissMode) { onDismissTap(); true } else false
        }
    ) { _ ->
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text  = "🗑️ ${pin.category}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
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