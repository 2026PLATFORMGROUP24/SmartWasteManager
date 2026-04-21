package com.platform.smartwastemanager.features.report.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platform.smartwastemanager.features.report.domain.WasteCategory
import com.platform.smartwastemanager.features.report.domain.WasteReport
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportHistoryScreen(
    viewModel: ReportHistoryViewModel,
    currentUserUid: String,
    onNavigateBack: () -> Unit,
    onNavigateToMap: (reportId: String, latitude: Double, longitude: Double) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val snackbarHostState = remember { SnackbarHostState() }
    var isRefreshing by remember { mutableStateOf(false) }
    var reportToDelete by remember { mutableStateOf<WasteReport?>(null) }

    LaunchedEffect(currentUserUid) {
        viewModel.loadReports(currentUserUid)
    }

    LaunchedEffect(uiState) {
        val error = (uiState as? ReportHistoryUiState.Error)?.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("My Reports") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.refresh()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            when (val state = uiState) {
                is ReportHistoryUiState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Loading reports...")
                    }
                }

                is ReportHistoryUiState.Error -> {
                    EmptyStateMessage(text = "Could not load reports.\nPull down to retry.")
                }

                is ReportHistoryUiState.Success -> {
                    if (state.reports.isEmpty()) {
                        EmptyStateMessage(text = "You have no active reports.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.reports, key = { it.id }) { report ->
                                ReportHistoryCard(
                                    report = report,
                                    formattedTimestamp = dateFormat.format(report.timestamp.toDate()),
                                    onDelete = { reportToDelete = report },
                                    onShowOnMap = {
                                        onNavigateToMap(
                                            report.id,
                                            report.location.latitude,
                                            report.location.longitude
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

    }

    LaunchedEffect(isRefreshing, uiState) {
        if (isRefreshing && uiState !is ReportHistoryUiState.Loading) {
            isRefreshing = false
        }
    }

    reportToDelete?.let { report ->
        val locationName = report.streetName.ifBlank { "Unknown location" }
        AlertDialog(
            onDismissRequest = { reportToDelete = null },
            title = { Text("Delete Report?") },
            text = {
                Text("Delete your ${report.category} report at \"$locationName\"? This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteReport(report.id)
                    reportToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { reportToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EmptyStateMessage(text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ReportHistoryCard(
    report: WasteReport,
    formattedTimestamp: String,
    onDelete: () -> Unit,
    onShowOnMap: () -> Unit
) {
    val (categoryIcon, categoryTint) = categoryMeta(report.category)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = null,
                    tint = categoryTint,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = report.category,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "📍 ${report.streetName}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "🕐 $formattedTimestamp",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Delete")
                }
                Button(
                    onClick = onShowOnMap,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Show on Map")
                }
            }
        }
    }
}

private fun categoryMeta(categoryName: String): Pair<ImageVector, Color> {
    return when (WasteCategory.fromDisplayName(categoryName)) {
        WasteCategory.RECYCLABLE -> Icons.Default.Eco to Color(0xFF2E7D32)
        WasteCategory.ORGANIC -> Icons.Default.Eco to Color(0xFF558B2F)
        WasteCategory.GLASS -> Icons.Default.Warning to Color(0xFF006064)
        WasteCategory.METAL -> Icons.Default.Warning to Color(0xFF455A64)
        WasteCategory.HAZARDOUS -> Icons.Default.LocalFireDepartment to Color(0xFFC62828)
        WasteCategory.MIXED_WASTE -> Icons.Default.Warning to Color(0xFF6D4C41)
    }
}
