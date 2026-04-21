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
import com.platform.smartwastemanager.features.report.domain.WasteCategory

private const val MAX_LABEL_DISPLAY_LENGTH = 36

/**
 * Waste report form screen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ReportFormScreen(
    viewModel: ReportViewModel,
    currentUserUid: String,
    onNavigateBack: () -> Unit,
    onSubmitSuccess: () -> Unit,
    onNavigateToLocationPicker: () -> Unit,
    onNavigateToScan: () -> Unit
) {
    val context = LocalContext.current

    val uiState          by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val aiLabels         by viewModel.aiLabels.collectAsStateWithLifecycle()
    val isLowConfidence  by viewModel.isLowConfidence.collectAsStateWithLifecycle()
    val streetName       by viewModel.streetName.collectAsStateWithLifecycle()
    val isLocating       by viewModel.isLocating.collectAsStateWithLifecycle()
    val isManualLocation by viewModel.isManualLocation.collectAsStateWithLifecycle()

    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    LaunchedEffect(Unit) {
        if (!isManualLocation) {
            if (locationPermissions.allPermissionsGranted) {
                viewModel.fetchLocation(context)
            } else {
                locationPermissions.launchMultiplePermissionRequest()
            }
        }
    }

    LaunchedEffect(locationPermissions.allPermissionsGranted) {
        if (locationPermissions.allPermissionsGranted && !isManualLocation) {
            viewModel.fetchLocation(context)
        }
    }

    var showSuccessDialog by remember { mutableStateOf(false) }
    LaunchedEffect(uiState) {
        if (uiState is ReportUiState.Success) showSuccessDialog = true
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Report Submitted") },
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

    Column(modifier = Modifier.fillMaxSize()) {

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Submit Report", style = MaterialTheme.typography.titleLarge)
        }

        var categoryDropdownExpanded by remember { mutableStateOf(false) }

        WasteReportingTab(
            uiState                  = uiState,
            selectedCategory         = selectedCategory,
            aiLabels                 = aiLabels,
            isLowConfidence          = isLowConfidence,
            streetName               = streetName,
            isLocating               = isLocating,
            isManualLocation         = isManualLocation,
            categoryDropdownExpanded = categoryDropdownExpanded,
            onCategoryExpand         = { categoryDropdownExpanded = it },
            locationPermissionsGranted = locationPermissions.allPermissionsGranted,
            onRequestLocationPermission = { locationPermissions.launchMultiplePermissionRequest() },
            onCategorySelected       = { viewModel.setCategory(it) },
            onStreetNameChange       = { viewModel.setStreetName(it) },
            onNavigateToLocationPicker = onNavigateToLocationPicker,
            onRefreshGps             = { viewModel.clearManualAndFetchGps(context) },
            onSubmit                 = { viewModel.submitReport(currentUserUid) },
            onScanAgain              = onNavigateToScan
        )
    }
}

// =============================================================================
// Waste Reporting tab content extracted for readability
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WasteReportingTab(
    uiState: ReportUiState,
    selectedCategory: String,
    aiLabels: List<Pair<String, Float>>,
    isLowConfidence: Boolean,
    streetName: String,
    isLocating: Boolean,
    isManualLocation: Boolean,
    categoryDropdownExpanded: Boolean,
    onCategoryExpand: (Boolean) -> Unit,
    locationPermissionsGranted: Boolean,
    onRequestLocationPermission: () -> Unit,
    onCategorySelected: (String) -> Unit,
    onStreetNameChange: (String) -> Unit,
    onNavigateToLocationPicker: () -> Unit,
    onRefreshGps: () -> Unit,
    onSubmit: () -> Unit,
    onScanAgain: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AnimatedVisibility(visible = aiLabels.isNotEmpty()) {
            Card(
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "AI Scan Results",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Detected as: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Surface(color = MaterialTheme.colorScheme.tertiary, shape = MaterialTheme.shapes.small) {
                            Text(
                                selectedCategory,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Top objects seen by the model:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(6.dp))
                    aiLabels.take(5).forEach { (label, confidence) ->
                        val displayLabel = if (label.length >= MAX_LABEL_DISPLAY_LENGTH) {
                            "${label.take(MAX_LABEL_DISPLAY_LENGTH - 3)}..."
                        } else {
                            label
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                displayLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${(confidence * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        LinearProgressIndicator(
                            progress = { confidence },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = MaterialTheme.colorScheme.tertiary,
                            trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.15f)
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "You can change the category below if the AI got it wrong.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }

        AnimatedVisibility(visible = isLowConfidence && aiLabels.isNotEmpty()) {
            Card(
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Low Confidence Scan",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "The AI wasn't confident about this item. Try: closer framing, better lighting, plain surface. Or correct the category below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onScanAgain) {
                        Text(
                            "Scan Again",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Location permission banner
        if (!locationPermissionsGranted) {
            Card(
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Location permission needed", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Grant location access to auto-detect your street name, or pick on the map.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onRequestLocationPermission) {
                        Text("Grant Permission", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        // Category dropdown
        Text("Waste Category", style = MaterialTheme.typography.labelLarge)
        ExposedDropdownMenuBox(
            expanded         = categoryDropdownExpanded,
            onExpandedChange = onCategoryExpand
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
                onDismissRequest = { onCategoryExpand(false) }
            ) {
                WasteCategory.entries.forEach { category ->
                    DropdownMenuItem(
                        text    = { Text(category.displayName) },
                        onClick = { onCategorySelected(category.displayName); onCategoryExpand(false) }
                    )
                }
            }
        }

        // Street name + location
        Text("Location", style = MaterialTheme.typography.labelLarge)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value         = streetName,
                onValueChange = onStreetNameChange,
                label         = { Text("Street Name") },
                placeholder   = { Text("Auto-detected from GPS...") },
                supportingText = {
                    Text(when {
                        isLocating              -> "Detecting location..."
                        isManualLocation        -> "Manual pin set"
                        !locationPermissionsGranted -> "No permission — pick on map or enter manually"
                        streetName.isEmpty()    -> "Tap refresh to detect"
                        else                    -> "GPS detected. Tap map to pick a spot."
                    })
                },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onNavigateToLocationPicker) {
                Icon(Icons.Default.Map, contentDescription = "Pick location on map",
                    tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onRefreshGps, enabled = !isLocating && locationPermissionsGranted) {
                if (isLocating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Default.LocationOn, contentDescription = "Refresh GPS",
                        tint = if (locationPermissionsGranted) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.outline)
                }
            }
        }

        // Auto-filled info
        Card(
            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Auto-filled fields", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Timestamp: Now  |  Status: Pending  |  Type: Regular Pickup",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }

        if (uiState is ReportUiState.Error) {
            Text("${(uiState as ReportUiState.Error).message}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick  = onSubmit,
            enabled  = uiState !is ReportUiState.Loading,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            if (uiState is ReportUiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Submit Report")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
