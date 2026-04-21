package com.platform.smartwastemanager.features.report.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Entry point for the Scan/Report feature.
 *
 * Presents two options to the user:
 * 1. Scan with Camera — takes a photo, AI classifies the waste type,
 *    then opens the form with both "Waste Reporting" and "Ask AI" tabs.
 * 2. Skip Scanning — opens the form directly with default values;
 *    the Ask AI tab will prompt the user to scan an image.
 *
 * The driver-mode restriction has been removed: drivers can now access
 * this feature in both driver and user views.
 */
@Composable
fun ReportScreen(
    isDriverInDriverView: Boolean,
    onNavigateToScan: () -> Unit,
    onNavigateToForm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // ---- Title ----
        Text(
            text = "📷 Scan & Report Waste",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Scan a waste item for AI-powered recycling advice and reporting.\nThe AI scan will auto-detect the category for you.",
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
                .height(56.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scan with Camera (AI)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---- Skip Scanning button (formerly "Report Manually") ----
        OutlinedButton(
            onClick = onNavigateToForm,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Skip Scanning")
        }
    }
}
