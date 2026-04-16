package com.platform.smartwastemanager.features.notifications.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
 * Notification Centre — visible to drivers in driver view only.
 *
 * Lets the driver:
 *  1. Force-push a "New Waste Report" notification to all drivers
 *  2. Force-push a "Collection Reminder" to all users
 *  3. Compose and send a custom announcement to all users
 *
 * PUSH = writes to Firestore → Cloud Function fans out via FCM to all targets.
 *
 * @param onNavigateBack Called when the driver taps the back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    viewModel: NotificationViewModel,
    onNavigateBack: () -> Unit
) {
    val sendState by viewModel.sendState.collectAsStateWithLifecycle()
    val context   = LocalContext.current
    val snackbar  = remember { SnackbarHostState() }

    // Show result snackbar and reset state after each action
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
        topBar = {
            TopAppBar(
                title = { Text("Notification Centre") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor             = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor          = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
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

            // ---- Header description ----
            Text(
                text  = "Push notifications to users via FCM. " +
                        "The request is sent to Firestore and your Cloud Function " +
                        "fans it out to all targeted devices.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // ================================================================
            // SECTION 1 — New Waste Report Notification → all DRIVERS
            // ================================================================
            NotificationSection(
                icon        = Icons.Default.Warning,
                title       = "New Waste Report",
                description = "Pushed to all DRIVERS. Simulates a resident submitting a report.",
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

                    SendButton(
                        label     = "Push to All Drivers",
                        isSending = sendState is NotificationSendState.Sending,
                        onClick   = { viewModel.pushReportNotification(category, streetName) }
                    )
                }
            )

            HorizontalDivider()

            // ================================================================
            // SECTION 2 — Collection Reminder → ALL users
            // ================================================================
            NotificationSection(
                icon        = Icons.Default.Today,
                title       = "Collection Reminder",
                description = "Pushed to ALL users. Reminds residents to put out their bins.",
                content     = {
                    val days = listOf(
                        "Monday","Tuesday","Wednesday",
                        "Thursday","Friday","Saturday","Sunday"
                    )
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
                                ExposedDropdownMenuDefaults.TrailingIcon(
                                    expanded = dayDropdownOpen
                                )
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
                                    onClick = {
                                        selectedDay     = day
                                        dayDropdownOpen = false
                                    }
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

                    val categories = categoriesText
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }

                    SendButton(
                        label     = "Push Reminder to All Users",
                        isSending = sendState is NotificationSendState.Sending,
                        onClick   = {
                            viewModel.pushReminderNotification(selectedDay, categories)
                        }
                    )
                }
            )

            HorizontalDivider()

            // ================================================================
            // SECTION 3 — Announcement → ALL users
            // ================================================================
            NotificationSection(
                icon        = Icons.Default.Campaign,
                title       = "Announcement",
                description = "Pushed to ALL users (drivers + residents).",
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

                    val canSend = title.isNotBlank() &&
                            message.isNotBlank() &&
                            sendState !is NotificationSendState.Sending

                    SendButton(
                        label     = "Send Announcement to All",
                        isSending = sendState is NotificationSendState.Sending,
                        enabled   = canSend,
                        onClick   = { viewModel.pushAnnouncementNotification(title, message) }
                    )
                }
            )

            // Sending indicator
            if (sendState is NotificationSendState.Sending) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Sending…", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// =====================================================================
// Reusable composables
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

@Composable
private fun SendButton(
    label: String,
    isSending: Boolean,
    enabled: Boolean = !isSending,
    onClick: () -> Unit
) {
    Button(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled  = enabled
    ) {
        if (isSending) {
            CircularProgressIndicator(
                modifier    = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color       = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.width(8.dp))
            Text("Sending…")
        } else {
            Icon(Icons.Default.Send, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    }
}