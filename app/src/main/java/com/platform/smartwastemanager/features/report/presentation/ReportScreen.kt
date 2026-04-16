package com.platform.smartwastemanager.features.report.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Entry point for the Waste Reporting feature.
 *
 * Presents two options to the user:
 * 1. Scan with Camera — takes a photo, AI classifies the waste type,
 *    then pre-fills the report form.
 * 2. Report Manually — opens the form directly with default values.
 */
@Composable
fun ReportScreen(
    isDriverInDriverView: Boolean,
    onNavigateToScan: () -> Unit,
    onNavigateToForm: () -> Unit
) {
    val enabled = !isDriverInDriverView
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .alpha(if (enabled) 1f else 0.45f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // ---- Title ----
        Text(
            text = "📷 Report Waste",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Choose how you want to report waste.\nThe AI scan will auto-detect the category for you.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(40.dp))

        // ---- Scan with Camera button ----
        Button(
            onClick = onNavigateToScan,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = enabled
        ) {
            Icon(
                imageVector = Icons.Default.Camera,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scan with Camera (AI)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---- Report Manually button ----
        OutlinedButton(
            onClick = onNavigateToForm,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = enabled
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Report Manually")
        }

        if (!enabled) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Switch to user view to report waste",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
