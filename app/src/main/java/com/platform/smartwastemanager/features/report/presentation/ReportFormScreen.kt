package com.platform.smartwastemanager.features.report.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Warning
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
import com.platform.smartwastemanager.features.report.domain.ReportType
import com.platform.smartwastemanager.features.report.domain.WasteCategory

/**
 * Waste Report form screen.
 *
 * - Collects waste category, report type, and street name.
 * - Shows an AI scan summary card when the user arrived via the camera scan flow.
 * - Shows a low-confidence warning card when the model wasn't sure, prompting
 *   the user to re-scan or correct the category manually.
 * - Requests ACCESS_FINE_LOCATION + ACCESS_COARSE_LOCATION before fetching GPS.
 *   If denied, the street name stays empty and the user types it manually.
 *
 * KEY FIX — manual location preserved:
 *   Both LaunchedEffect blocks that call fetchLocation() now guard on
 *   !isManualLocation. Once the user picks a pin on LocationPickerMapScreen,
 *   isManualLocation = true and fetchLocation() is never called on recompose,
 *   so the manually chosen coordinates are never overwritten by GPS.
 *
 * @param onNavigateToLocationPicker Navigates to the full-screen map location picker.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ReportFormScreen(
    viewModel: ReportViewModel,
    currentUserUid: String,
    onNavigateBack: () -> Unit,
    onSubmitSuccess: () -> Unit,
    onNavigateToLocationPicker: () -> Unit
) {
    val context = LocalContext.current

    // ---- Observe all ViewModel state ----
    val uiState            by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedCategory   by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val aiLabels           by viewModel.aiLabels.collectAsStateWithLifecycle()
    val isLowConfidence    by viewModel.isLowConfidence.collectAsStateWithLifecycle()
    val selectedReportType by viewModel.selectedReportType.collectAsStateWithLifecycle()
    val streetName         by viewModel.streetName.collectAsStateWithLifecycle()
    val isLocating         by viewModel.isLocating.collectAsStateWithLifecycle()
    // NEW — true when the user has confirmed a pin on the map picker
    val isManualLocation   by viewModel.isManualLocation.collectAsStateWithLifecycle()

    // ---- Location permissions ----
    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // Request permission + fetch location when the screen first opens.
    // GUARD: skip fetchLocation if the user already picked a manual pin —
    // we must not overwrite their chosen coordinates on recompose.
    LaunchedEffect(Unit) {
        if (!isManualLocation) {
            if (locationPermissions.allPermissionsGranted) {
                viewModel.fetchLocation(context)
            } else {
                locationPermissions.launchMultiplePermissionRequest()
            }
        }
    }

    // Fetch location automatically once the user grants permission mid-session.
    // Same guard — do not overwrite a manual pick.
    LaunchedEffect(locationPermissions.allPermissionsGranted) {
        if (locationPermissions.allPermissionsGranted && !isManualLocation) {
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

    // ---- Root layout ----
    Column(modifier = Modifier.fillMaxSize()) {

        // ---- Top bar ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text  = "Submit Report",
                style = MaterialTheme.typography.titleLarge
            )
        }

        // ---- Scrollable form body ----
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ================================================================
            // AI SCAN RESULTS CARD
            // Shown only when the user arrived via the camera scan flow and the
            // classifier returned at least one label.
            // ================================================================
            AnimatedVisibility(visible = aiLabels.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {

                        // Card header
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector        = Icons.Default.Info,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier           = Modifier.size(20.dp)
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

                        // Detected category pill — shows what the AI picked
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
                                    text       = selectedCategory,
                                    style      = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color      = MaterialTheme.colorScheme.onTertiary,
                                    modifier   = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Top-5 raw model labels with confidence bars
                        Text(
                            text  = "Top objects seen by the model:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.height(6.dp))

                        aiLabels.forEach { (label, confidence) ->
                            Row(
                                modifier              = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                // Truncate very long label strings so they don't overflow
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
                                modifier   = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp),
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
            // Shown when the model scanned something but wasn't confident.
            // Only visible when aiLabels is also non-empty (i.e. a scan happened).
            // Gives the user actionable tips and a shortcut back to the camera.
            // ================================================================
            AnimatedVisibility(visible = isLowConfidence && aiLabels.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector        = Icons.Default.Warning,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.onErrorContainer,
                                modifier           = Modifier.size(20.dp)
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

                        // Quick shortcut back to the camera
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
            // LOCATION PERMISSION BANNER
            // Only shown when location was denied — never blocks form submission.
            // ================================================================
            if (!locationPermissions.allPermissionsGranted) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text  = "📍 Location permission needed",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text  = "Grant location access to auto-detect your street name, " +
                                    "or pick a location on the map below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { locationPermissions.launchMultiplePermissionRequest() }
                        ) {
                            Text(
                                text  = "Grant Permission",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // ================================================================
            // CATEGORY DROPDOWN
            // Pre-filled by the AI classifier. User can override.
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
                    trailingIcon  = {
                        ExposedDropdownMenuDefaults.TrailingIcon(categoryDropdownExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
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
                    trailingIcon  = {
                        ExposedDropdownMenuDefaults.TrailingIcon(reportTypeDropdownExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
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
            // STREET NAME FIELD + MAP PICKER BUTTON + GPS REFRESH
            // Auto-populated by GPS + Geocoder. User can edit freely.
            // The map icon opens LocationPickerMapScreen for precise pin picking.
            // The location icon refreshes GPS (and clears any manual pin).
            // ================================================================
            Text("Location", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value         = streetName,
                    onValueChange = { viewModel.setStreetName(it) },
                    label         = { Text("Street Name") },
                    placeholder   = { Text("Auto-detected from GPS…") },
                    supportingText = {
                        Text(
                            when {
                                isLocating ->
                                    "📍 Detecting location…"
                                isManualLocation ->
                                    "📌 Manual pin set — tap 🗺 to change, or ↺ to use GPS"
                                !locationPermissions.allPermissionsGranted ->
                                    "⚠️ No permission — pick on map or enter manually"
                                streetName.isEmpty() ->
                                    "📍 Tap ↺ to detect"
                                else ->
                                    "✅ GPS detected. Tap 🗺 to pick a different spot."
                            }
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))

                // ---- Map picker button (NEW) ----
                // Opens LocationPickerMapScreen so the user can drop a pin.
                // After confirming, setManualLocation() is called and isManualLocation
                // becomes true, which prevents the LaunchedEffects above from
                // overwriting the chosen coordinates.
                IconButton(onClick = onNavigateToLocationPicker) {
                    Icon(
                        imageVector        = Icons.Default.Map,
                        contentDescription = "Pick location on map",
                        tint               = MaterialTheme.colorScheme.primary
                    )
                }

                // ---- GPS refresh button ----
                // Calls clearManualAndFetchGps() which resets the manual flag first,
                // then fetches a fresh GPS position — safe to call even after a manual pick.
                IconButton(
                    onClick  = { viewModel.clearManualAndFetchGps(context) },
                    enabled  = !isLocating && locationPermissions.allPermissionsGranted
                ) {
                    if (isLocating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Icon(
                            imageVector        = Icons.Default.LocationOn,
                            contentDescription = "Refresh GPS location",
                            tint = if (locationPermissions.allPermissionsGranted)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // ================================================================
            // AUTO-FILLED INFO CARD
            // Reminds the user which fields are set automatically.
            // ================================================================
            Card(
                colors = CardDefaults.cardColors(
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
            // SUBMISSION ERROR MESSAGE
            // Shown below the info card when the Firestore write fails.
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
            // Disabled while loading. Shows a spinner during submission.
            // ================================================================
            Button(
                onClick  = { viewModel.submitReport(currentUserUid) },
                enabled  = uiState !is ReportUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
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