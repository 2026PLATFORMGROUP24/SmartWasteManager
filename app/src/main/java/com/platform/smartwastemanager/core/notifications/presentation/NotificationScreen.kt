package com.platform.smartwastemanager.features.notifications.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Notification Centre — visible to drivers only.
 *
 * Lets the driver:
 *  1. Force-push a "New Waste Report" notification   (local or to all drivers)
 *  2. Force-push a "Collection Reminder" notification (local or to all users)
 *  3. Compose and send a custom announcement          (local or to all users)
 *
 * LOCAL  = shows notification on this device instantly. Good for testing the look.
 * PUSH   = sends via Firestore → Cloud Function → FCM to all targeted devices.
 *
 * Note: The driver is also a user, so PUSH notifications will also appear on the
 * driver's own device when the Cloud Function sends them back via FCM.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    viewModel: NotificationViewModel
) {
    val sendState by viewModel.sendState.collectAsStateWithLifecycle()
    val context   = LocalContext.current
    val snackbar  = remember { SnackbarHostState() }

    // Show result snackbar and reset state
    LaunchedEffect(sendState) {
        when (sendState) {
            is NotificationSendState.Success -> {
                snackbar.showSnackbar((sendState as NotificationSendState.Success).message)
                viewModel.resetState()
            }
            is NotificationSendState.Error -> {
                snackbar.showSnackbar("❌ ${(sendState as NotificationSendState.Error).message}")
                viewModel.resetState()
            }
            else -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ---- Header ----
            Text(
                text  = "Notification Centre",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text  = "Test notifications locally on this device, or push to all targeted users via FCM.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // ================================================================
            // SECTION 1 — New Waste Report Notification
            // ================================================================
            NotificationSection(
                icon        = Icons.Default.Warning,
                title       = "New Waste Report",
                description = "Simulates a resident submitting a report. Pushed to all DRIVERS.",
                content     = {
                    var category   by remember { mutableStateOf("Recyclable") }
                    var streetName by remember { mutableStateOf("123 Main Street") }

                    OutlinedTextField(
                        value         = category,
                        onValueChange = { category = it },
                        label         = { Text("Waste Category") },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true
                    )
                    OutlinedTextField(
                        value         = streetName,
                        onValueChange = { streetName = it },
                        label         = { Text("Street Name") },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true
                    )
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick  = {
                                viewModel.testReportNotificationLocal(context, category, streetName)
                            },
                            modifier = Modifier.weight(1f),
                            enabled  = sendState !is NotificationSendState.Sending
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Local")
                        }
                        Button(
                            onClick  = { viewModel.pushReportNotification(category, streetName) },
                            modifier = Modifier.weight(1f),
                            enabled  = sendState !is NotificationSendState.Sending
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Push to Drivers")
                        }
                    }
                }
            )

            HorizontalDivider()

            // ================================================================
            // SECTION 2 — Collection Reminder Notification
            // ================================================================
            NotificationSection(
                icon        = Icons.Default.Today,
                title       = "Collection Reminder",
                description = "Reminds residents to put out their bins. Pushed to ALL users.",
                content     = {
                    val days = listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday")
                    var selectedDay     by remember { mutableStateOf("Monday") }
                    var dayDropdownOpen by remember { mutableStateOf(false) }
                    var categoriesText  by remember { mutableStateOf("Recyclable, Glass") }

                    ExposedDropdownMenuBox(
                        expanded         = dayDropdownOpen,
                        onExpandedChange = { dayDropdownOpen = it }
                    ) {
                        OutlinedTextField(
                            value         = selectedDay,
                            onValueChange = {},
                            readOnly      = true,
                            label         = { Text("Day of Week") },
                            trailingIcon  = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = dayDropdownOpen)
                            },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded         = dayDropdownOpen,
                            onDismissRequest = { dayDropdownOpen = false }
                        ) {
                            days.forEach { day ->
                                DropdownMenuItem(
                                    text    = { Text(day) },
                                    onClick = { selectedDay = day; dayDropdownOpen = false }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value         = categoriesText,
                        onValueChange = { categoriesText = it },
                        label         = { Text("Categories (comma-separated)") },
                        placeholder   = { Text("Recyclable, Glass") },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true
                    )

                    val categories = categoriesText.split(",").map { it.trim() }.filter { it.isNotBlank() }

                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick  = {
                                viewModel.testReminderNotificationLocal(context, selectedDay, categories)
                            },
                            modifier = Modifier.weight(1f),
                            enabled  = sendState !is NotificationSendState.Sending
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Local")
                        }
                        Button(
                            onClick  = { viewModel.pushReminderNotification(selectedDay, categories) },
                            modifier = Modifier.weight(1f),
                            enabled  = sendState !is NotificationSendState.Sending
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Push to All")
                        }
                    }
                }
            )

            HorizontalDivider()

            // ================================================================
            // SECTION 3 — Announcement
            // ================================================================
            NotificationSection(
                icon        = Icons.Default.Campaign,
                title       = "Announcement",
                description = "Send a custom announcement to ALL users (drivers + residents).",
                content     = {
                    var title   by remember { mutableStateOf("") }
                    var message by remember { mutableStateOf("") }

                    OutlinedTextField(
                        value         = title,
                        onValueChange = { title = it },
                        label         = { Text("Announcement Title *") },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true
                    )
                    OutlinedTextField(
                        value         = message,
                        onValueChange = { message = it },
                        label         = { Text("Message *") },
                        modifier      = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp)
                    )

                    val canSend = title.isNotBlank() && message.isNotBlank() &&
                            sendState !is NotificationSendState.Sending

                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick  = {
                                viewModel.testAnnouncementNotificationLocal(context, title, message)
                            },
                            modifier = Modifier.weight(1f),
                            enabled  = canSend
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Local")
                        }
                        Button(
                            onClick  = { viewModel.pushAnnouncementNotification(title, message) },
                            modifier = Modifier.weight(1f),
                            enabled  = canSend
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Push to All")
                        }
                    }

                    // Sending spinner
                    if (sendState is NotificationSendState.Sending) {
                        Row(
                            modifier          = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Sending…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

// =====================================================================
// NotificationSection — reusable card wrapper for each notification type
// =====================================================================

@Composable
private fun NotificationSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text       = title,
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text  = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}