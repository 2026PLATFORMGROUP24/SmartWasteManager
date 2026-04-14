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
 * Map screen showing all pending waste report locations as red markers.
 *
 * User behaviour:
 *  - See red markers for all pending reports.
 *  - Tap a marker to see a info window (category, street, time).
 *
 * Driver behaviour (extra):
 *  - Toggle "Dismiss Mode" button in the bottom bar.
 *  - In dismiss mode, tapping a pin shows a confirmation dialog.
 *  - Confirming sets the report to "dismissed" and removes the pin.
 */
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    isDriver: Boolean
) {
    val uiState         by viewModel.uiState.collectAsStateWithLifecycle()
    val isDismissMode   by viewModel.isDismissMode.collectAsStateWithLifecycle()
    val pinToConfirm    by viewModel.pinToConfirmDismiss.collectAsStateWithLifecycle()

    // Default camera position — centred roughly on South Africa (adjust to your region)
    val defaultPosition = LatLng(-26.2041, 28.0473)  // Johannesburg as default
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultPosition, 11f)
    }

    // Date formatter for pin info windows
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    // ---- Dismiss confirmation dialog ----
    if (pinToConfirm != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDismiss() },
            title = { Text("Dismiss Report?") },
            text = {
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
                TextButton(onClick = { viewModel.cancelDismiss() }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {

        when (val state = uiState) {

            // ---- Loading ----
            is MapUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            // ---- Error ----
            is MapUiState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadPins() }) { Text("Retry") }
                }
            }

            // ---- Map with pins ----
            is MapUiState.Success -> {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState
                ) {
                    state.pins.forEach { pin ->
                        MapPinMarker(
                            pin         = pin,
                            isDismissMode = isDismissMode && isDriver,
                            dateFormat  = dateFormat,
                            onDismissTap = { viewModel.onPinTappedForDismiss(pin) }
                        )
                    }
                }

                // ---- Empty state overlay ----
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
                            text = "✅ No pending waste reports",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // ---- Driver dismiss mode banner ----
                if (isDismissMode && isDriver) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "🗑️ Dismiss Mode — tap a pin to dismiss it",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // ---- Driver dismiss mode toggle button ----
                if (isDriver) {
                    FloatingActionButton(
                        onClick = { viewModel.toggleDismissMode() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        containerColor = if (isDismissMode)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            imageVector = if (isDismissMode) Icons.Default.Close
                            else Icons.Default.DeleteSweep,
                            contentDescription = if (isDismissMode) "Exit dismiss mode"
                            else "Enter dismiss mode",
                            tint = if (isDismissMode) Color.White
                            else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // ---- Pin count badge ----
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = "📍 ${state.pins.size} pending report${if (state.pins.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * A single red map marker for a pending waste report.
 *
 * When [isDismissMode] is true (driver), tapping the marker calls [onDismissTap].
 * Otherwise, tapping shows the standard info window.
 */
@Composable
private fun MapPinMarker(
    pin: MapPin,
    isDismissMode: Boolean,
    dateFormat: java.text.SimpleDateFormat,
    onDismissTap: () -> Unit
) {
    val position = LatLng(pin.location.latitude, pin.location.longitude)
    val formattedTime = remember(pin.timestamp) {
        dateFormat.format(pin.timestamp.toDate())
    }

    MarkerInfoWindowContent(
        state = rememberMarkerState(position = position),
        title = pin.category,
        snippet = "${pin.streetName}\n$formattedTime",
        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
        onClick = { marker ->
            if (isDismissMode) {
                onDismissTap()
                true // consume the click (don't show info window in dismiss mode)
            } else {
                false // let the default info window show
            }
        }
    ) { _ ->
        // Custom info window content
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "🗑️ ${pin.category}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "📍 ${pin.streetName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "🕐 $formattedTime",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}