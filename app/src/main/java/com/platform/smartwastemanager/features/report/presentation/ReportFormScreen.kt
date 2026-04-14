package com.platform.smartwastemanager.features.report.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.platform.smartwastemanager.features.report.domain.ReportType
import com.platform.smartwastemanager.features.report.domain.WasteCategory

/**
 * Waste Report form screen.
 *
 * Requests ACCESS_FINE_LOCATION + ACCESS_COARSE_LOCATION before fetching GPS.
 * If permission is denied the street name field stays empty and the user can
 * type it manually. The form is never blocked — location is optional input.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ReportFormScreen(
    viewModel: ReportViewModel,
    currentUserUid: String,
    onNavigateBack: () -> Unit,
    onSubmitSuccess: () -> Unit
) {
    val context = LocalContext.current

    val uiState            by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedCategory   by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedReportType by viewModel.selectedReportType.collectAsStateWithLifecycle()
    val streetName         by viewModel.streetName.collectAsStateWithLifecycle()
    val isLocating         by viewModel.isLocating.collectAsStateWithLifecycle()

    // ---- Location permissions (both fine and coarse) ----
    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // When the screen first appears:
    //  1. Request location permission if not yet granted.
    //  2. If already granted, fetch location immediately.
    LaunchedEffect(Unit) {
        if (locationPermissions.allPermissionsGranted) {
            viewModel.fetchLocation(context)
        } else {
            locationPermissions.launchMultiplePermissionRequest()
        }
    }

    // Once the user grants permission (dialog dismissed), fetch location automatically.
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text  = "Submit Report",
                style = MaterialTheme.typography.titleLarge
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ---- Location permission banner (shown only when denied) ----
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
                            text  = "Grant location access to auto-detect your street name, or type it manually below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { locationPermissions.launchMultiplePermissionRequest() }
                        ) {
                            Text(
                                "Grant Permission",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // ---- Category dropdown ----
            Text("Waste Category", style = MaterialTheme.typography.labelLarge)
            ExposedDropdownMenuBox(
                expanded = categoryDropdownExpanded,
                onExpandedChange = { categoryDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedCategory,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(categoryDropdownExpanded)
                    },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = categoryDropdownExpanded,
                    onDismissRequest = { categoryDropdownExpanded = false }
                ) {
                    WasteCategory.entries.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.displayName) },
                            onClick = {
                                viewModel.setCategory(category.displayName)
                                categoryDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // ---- Report Type dropdown ----
            Text("Report Type", style = MaterialTheme.typography.labelLarge)
            ExposedDropdownMenuBox(
                expanded = reportTypeDropdownExpanded,
                onExpandedChange = { reportTypeDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedReportType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Report Type") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(reportTypeDropdownExpanded)
                    },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = reportTypeDropdownExpanded,
                    onDismissRequest = { reportTypeDropdownExpanded = false }
                ) {
                    ReportType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.displayName) },
                            onClick = {
                                viewModel.setReportType(type.displayName)
                                reportTypeDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // ---- Street Name field ----
            Text("Location", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = streetName,
                    onValueChange = { viewModel.setStreetName(it) },
                    label = { Text("Street Name") },
                    placeholder = { Text("Auto-detected from GPS…") },
                    supportingText = {
                        Text(
                            when {
                                isLocating -> "📍 Detecting location…"
                                !locationPermissions.allPermissionsGranted ->
                                    "⚠️ No permission — enter manually"
                                streetName.isEmpty() -> "📍 Tap refresh to detect"
                                else -> "✅ GPS detected. You can edit if needed."
                            }
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Refresh GPS button — only active when permission is granted
                IconButton(
                    onClick = { viewModel.fetchLocation(context) },
                    enabled = !isLocating && locationPermissions.allPermissionsGranted
                ) {
                    if (isLocating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Refresh location",
                            tint = if (locationPermissions.allPermissionsGranted)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // ---- Auto-filled info card ----
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

            // ---- Error message ----
            if (uiState is ReportUiState.Error) {
                Text(
                    text  = "⚠️ ${(uiState as ReportUiState.Error).message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ---- Submit button ----
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