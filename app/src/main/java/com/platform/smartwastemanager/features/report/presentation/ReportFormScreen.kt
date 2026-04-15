package com.platform.smartwastemanager.features.report.presentation

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.platform.smartwastemanager.features.report.domain.ReportType
import com.platform.smartwastemanager.features.report.domain.WasteCategory

/**
 * Waste Report form screen.
 *
 * Location section redesigned:
 *   - Replaced plain text field with a compact map preview card.
 *   - The card shows a live Google Map with a red pin on the confirmed location,
 *     the resolved street name, and two action buttons:
 *       • "Change on Map" — navigates to LocationPickerMapScreen for precise picking.
 *       • "Use GPS"       — re-fetches device location.
 *   - The user always sees the exact location being reported, removing ambiguity.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ReportFormScreen(
    viewModel: ReportViewModel,
    currentUserUid: String,
    onNavigateBack: () -> Unit,
    onSubmitSuccess: () -> Unit,
    onNavigateToLocationPicker: () -> Unit    // NEW — navigates to LocationPickerMapScreen
) {
    val context = LocalContext.current

    val uiState            by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedCategory   by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val aiLabels           by viewModel.aiLabels.collectAsStateWithLifecycle()
    val isLowConfidence    by viewModel.isLowConfidence.collectAsStateWithLifecycle()
    val selectedReportType by viewModel.selectedReportType.collectAsStateWithLifecycle()
    val streetName         by viewModel.streetName.collectAsStateWithLifecycle()
    val isLocating         by viewModel.isLocating.collectAsStateWithLifecycle()
    val currentLocation    by viewModel.location.collectAsStateWithLifecycle()
    val isManualLocation   by viewModel.isManualLocation.collectAsStateWithLifecycle()

    // ---- Location permissions ----
    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // Auto-fetch GPS location when screen opens or permission is granted
    LaunchedEffect(Unit) {
        if (locationPermissions.allPermissionsGranted) {
            viewModel.fetchLocation(context)
        } else {
            locationPermissions.launchMultiplePermissionRequest()
        }
    }
    LaunchedEffect(locationPermissions.allPermissionsGranted) {
        if (locationPermissions.allPermissionsGranted) {
            viewModel.fetchLocation(context)
        }
    }

    // ---- Success dialog ----
    var showSuccessDialog by remember { mutableStateOf(false) }
    LaunchedEffect(uiState) {
        if (uiState is ReportUiState.Success) showSuccessDialog = true
    }
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Report Submitted ✅") },
            text  = { Text("Your waste report has been submitted. Drivers will be notified.") },
            confirmButton = {
                TextButton(onClick = {
                    showSuccessDialog = false
                    onSubmitSuccess()
                    viewModel.resetForm()
                }) { Text("OK") }
            }
        )
    }

    // ---- Dropdown expanded states ----
    var categoryDropdownExpanded   by remember { mutableStateOf(false) }
    var reportTypeDropdownExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {

        // ---- Top bar ----
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Submit Report", style = MaterialTheme.typography.titleLarge)
        }

        // ---- Scrollable body ----
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ================================================================
            // AI SCAN RESULTS CARD
            // ================================================================
            AnimatedVisibility(visible = aiLabels.isNotEmpty()) {
                Card(
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint     = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text       = "🤖 AI Scan Results",
                                style      = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text  = "Detected as: ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.tertiary,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text      = selectedCategory,
                                    style     = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color     = MaterialTheme.colorScheme.onTertiary,
                                    modifier  = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text  = "Top objects seen by the model:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.height(6.dp))
                        aiLabels.forEach { (label, confidence) ->
                            Row(
                                modifier              = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Text(
                                    text     = label.take(36),
                                    style    = MaterialTheme.typography.bodySmall,
                                    color    = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text       = "${(confidence * 100).toInt()}%",
                                    style      = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color      = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            LinearProgressIndicator(
                                progress   = { confidence },
                                modifier   = Modifier.fillMaxWidth().height(4.dp),
                                color      = MaterialTheme.colorScheme.tertiary,
                                trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.15f)
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text  = "✏️ You can change the category below if the AI got it wrong.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // ================================================================
            // LOW CONFIDENCE WARNING CARD
            // ================================================================
            AnimatedVisibility(visible = isLowConfidence && aiLabels.isNotEmpty()) {
                Card(
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint     = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text       = "Low confidence scan",
                                style      = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text  = "The AI wasn't confident about this item. For a better result:\n" +
                                    "  • Move closer so the item fills the frame\n" +
                                    "  • Use better lighting or avoid glare\n" +
                                    "  • Place the item on a plain, clean surface\n\n" +
                                    "Or simply correct the category in the dropdown below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onNavigateBack) {
                            Text(
                                text       = "📷  Scan Again",
                                color      = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ================================================================
            // CATEGORY DROPDOWN
            // ================================================================
            Text("Waste Category", style = MaterialTheme.typography.labelLarge)
            ExposedDropdownMenuBox(
                expanded         = categoryDropdownExpanded,
                onExpandedChange = { categoryDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value         = selectedCategory,
                    onValueChange = {},
                    readOnly      = true,
                    label         = { Text("Category") },
                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(categoryDropdownExpanded) },
                    modifier      = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded         = categoryDropdownExpanded,
                    onDismissRequest = { categoryDropdownExpanded = false }
                ) {
                    WasteCategory.entries.forEach { category ->
                        DropdownMenuItem(
                            text    = { Text(category.displayName) },
                            onClick = {
                                viewModel.setCategory(category.displayName)
                                categoryDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // ================================================================
            // REPORT TYPE DROPDOWN
            // ================================================================
            Text("Report Type", style = MaterialTheme.typography.labelLarge)
            ExposedDropdownMenuBox(
                expanded         = reportTypeDropdownExpanded,
                onExpandedChange = { reportTypeDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value         = selectedReportType,
                    onValueChange = {},
                    readOnly      = true,
                    label         = { Text("Report Type") },
                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(reportTypeDropdownExpanded) },
                    modifier      = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded         = reportTypeDropdownExpanded,
                    onDismissRequest = { reportTypeDropdownExpanded = false }
                ) {
                    ReportType.entries.forEach { type ->
                        DropdownMenuItem(
                            text    = { Text(type.displayName) },
                            onClick = {
                                viewModel.setReportType(type.displayName)
                                reportTypeDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // ================================================================
            // LOCATION SECTION — map preview card (replaces plain text field)
            // ================================================================
            Text("Report Location", style = MaterialTheme.typography.labelLarge)

            LocationPreviewCard(
                currentLocation      = currentLocation,
                streetName           = streetName,
                isLocating           = isLocating,
                isManualLocation     = isManualLocation,
                locationGranted      = locationPermissions.allPermissionsGranted,
                onChangeOnMap        = onNavigateToLocationPicker,
                onUseGps             = {
                    if (locationPermissions.allPermissionsGranted) {
                        viewModel.fetchLocation(context)
                    } else {
                        locationPermissions.launchMultiplePermissionRequest()
                    }
                }
            )

            // ================================================================
            // AUTO-FILLED INFO CARD
            // ================================================================
            Card(
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text  = "Auto-filled fields",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text  = "📅 Timestamp: Now\n🔴 Status: Pending\n👤 Reported by: Your account",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // ================================================================
            // SUBMISSION ERROR
            // ================================================================
            if (uiState is ReportUiState.Error) {
                Text(
                    text  = "⚠️ ${(uiState as ReportUiState.Error).message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ================================================================
            // SUBMIT BUTTON
            // ================================================================
            Button(
                onClick  = { viewModel.submitReport(currentUserUid) },
                enabled  = uiState !is ReportUiState.Loading,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (uiState is ReportUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color    = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Submit Report")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * The compact location preview card embedded in the form.
 *
 * Shows:
 *   1. A live Google Map (180 dp tall) with a red pin on the confirmed location.
 *      If no location is set yet, shows a greyed placeholder.
 *   2. The resolved street name and a status badge (GPS / Manual / Detecting).
 *   3. Two action buttons: "Change on Map" and "Use GPS".
 *
 * This ensures the user always has an unambiguous visual of exactly where
 * the report will be placed on the map.
 */
@Composable
private fun LocationPreviewCard(
    currentLocation: com.google.firebase.firestore.GeoPoint,
    streetName: String,
    isLocating: Boolean,
    isManualLocation: Boolean,
    locationGranted: Boolean,
    onChangeOnMap: () -> Unit,
    onUseGps: () -> Unit
) {
    val hasLocation = currentLocation.latitude != 0.0 || currentLocation.longitude != 0.0

    // Camera state for the preview mini-map
    val previewCameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(
                if (hasLocation) currentLocation.latitude else -26.2041,
                if (hasLocation) currentLocation.longitude else 28.0473
            ),
            15f
        )
    }

    // Animate the mini-map camera whenever the confirmed location changes
    LaunchedEffect(currentLocation) {
        if (hasLocation) {
            previewCameraState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(currentLocation.latitude, currentLocation.longitude), 15f
                )
            )
        }
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {

            // ---- Mini map preview ----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                if (isLocating && !hasLocation) {
                    // Show a placeholder while first GPS fix is incoming
                    Box(
                        modifier         = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text  = "Detecting your location…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Live map preview — disabled gestures so it doesn't fight with form scroll
                    GoogleMap(
                        modifier            = Modifier.fillMaxSize(),
                        cameraPositionState = previewCameraState,
                        uiSettings          = MapUiSettings(
                            scrollGesturesEnabled   = false,   // non-interactive — tap "Change" instead
                            zoomGesturesEnabled     = false,
                            zoomControlsEnabled     = false,
                            rotationGesturesEnabled = false,
                            tiltGesturesEnabled     = false,
                            myLocationButtonEnabled = false
                        )
                    ) {
                        if (hasLocation) {
                            Marker(
                                state   = rememberMarkerState(
                                    position = LatLng(currentLocation.latitude, currentLocation.longitude)
                                ),
                                title   = streetName.ifBlank { "Report Location" },
                                icon    = BitmapDescriptorFactory.defaultMarker(
                                    BitmapDescriptorFactory.HUE_RED
                                )
                            )
                        }
                    }

                    // "Tap to change" overlay in the top-right corner of the mini map
                    TextButton(
                        onClick  = onChangeOnMap,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            )
                        ) {
                            Row(
                                modifier          = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint     = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text  = "Change",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // ---- Street name + status + action buttons ----
            Column(modifier = Modifier.padding(12.dp)) {

                // Status badge row
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector        = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        // Street name or loading state
                        when {
                            isLocating -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 1.5.dp
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text  = "Detecting location…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            streetName.isBlank() -> {
                                Text(
                                    text  = "No location set yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            else -> {
                                Text(
                                    text       = streetName,
                                    style      = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Source badge: GPS or Manual
                    if (!isLocating && hasLocation) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = if (isManualLocation)
                                MaterialTheme.colorScheme.secondaryContainer
                            else
                                MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text  = if (isManualLocation) "📌 Manual" else "📡 GPS",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isManualLocation)
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                else
                                    MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Coordinates sub-label
                if (hasLocation && !isLocating) {
                    Text(
                        text  = "${String.format("%.5f", currentLocation.latitude)}, " +
                                "${String.format("%.5f", currentLocation.longitude)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action buttons
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // "Change on Map" — primary action for manual location picking
                    OutlinedButton(
                        onClick  = onChangeOnMap,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Map,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text  = "Change on Map",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    // "Use GPS" — revert to auto-detected location
                    OutlinedButton(
                        onClick  = onUseGps,
                        modifier = Modifier.weight(1f),
                        enabled  = !isLocating
                    ) {
                        if (isLocating) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(14.dp),
                                strokeWidth = 1.5.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text  = "Use GPS",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}