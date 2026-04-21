package com.platform.smartwastemanager.features.report.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.*

/**
 * Entry point for the Waste Reporting feature.
 *
 * Presents two action buttons and a live Report History section showing
 * the current user's pending / active waste reports.
 */
@Composable
fun ReportScreen(
    isDriverInDriverView: Boolean,
    currentUserUid: String,
    viewModel: ReportViewModel,
    onNavigateToScan: () -> Unit,
    onNavigateToForm: () -> Unit
) {
    val enabled = !isDriverInDriverView
    val userReports by viewModel.userReports.collectAsStateWithLifecycle()

    // Load user reports whenever the screen is shown
    LaunchedEffect(currentUserUid) {
        if (currentUserUid.isNotBlank()) viewModel.loadUserReports(currentUserUid)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .alpha(if (enabled) 1f else 0.45f),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ---- Title ----
        item {
            Text(
                text = "📷 Report Waste",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Choose how you want to report waste.\nThe AI scan will auto-detect the category for you.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ---- Scan with Camera button ----
        item {
            Button(
                onClick = onNavigateToScan,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = enabled
            ) {
                Icon(imageVector = Icons.Default.Camera, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan with Camera (AI)")
            }
        }

        // ---- Report Manually button ----
        item {
            OutlinedButton(
                onClick = onNavigateToForm,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = enabled
            ) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Report Manually")
            }

            if (!enabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Switch to user view to report waste",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // ---- Report History section header ----
        item {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "📋 Report History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Your currently active / pending reports",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (userReports.isEmpty()) {
            item {
                Text(
                    text = "No active reports at the moment.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            items(userReports, key = { it.id }) { report ->
                ReportHistoryCard(report = report)
            }
        }
    }
}

@Composable
private fun ReportHistoryCard(report: com.platform.smartwastemanager.features.report.domain.WasteReport) {
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val statusColor = when (report.status.lowercase()) {
        "pending" -> MaterialTheme.colorScheme.tertiary
        else      -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = report.category,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector        = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text  = report.streetName.ifBlank { "Location not set" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text  = dateFormatter.format(report.timestamp.toDate()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            Surface(
                color = statusColor.copy(alpha = 0.15f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text     = report.status.replaceFirstChar { it.uppercase() },
                    style    = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color    = statusColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
