package com.platform.smartwastemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.platform.smartwastemanager.core.navigation.AppNavHost
import com.platform.smartwastemanager.core.navigation.BottomNavItem
import com.platform.smartwastemanager.core.navigation.Routes
import com.platform.smartwastemanager.core.theme.SmartWasteManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartWasteManagerTheme {
                SmartWasteManagerAppContent()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartWasteManagerAppContent() {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext
            as SmartWasteManagerApp

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val startDestination = if (app.container.authRepository.isUserSignedIn()) {
        Routes.HOME
    } else {
        Routes.LOGIN
    }

    // Routes where the top bar and bottom bar should be hidden
    val authRoutes = setOf(Routes.LOGIN, Routes.SIGN_UP, Routes.RESET_PASSWORD)
    val isOnAuthScreen = currentDestination?.route in authRoutes

    // Map each route to a human-readable title for the top bar
    val screenTitle = when (currentDestination?.route) {
        Routes.HOME    -> "Home"
        Routes.REPORT  -> "Report Waste"
        Routes.MAP     -> "Map"
        Routes.GUIDES  -> "Recycling Guides"
        else           -> "Smart Waste Manager"
    }

    // Show a confirmation dialog before logging out
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out") },
            text = { Text("Are you sure you want to log out?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        app.container.authRepository.signOut()
                        // Navigate to Login and clear the entire back stack
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                ) {
                    Text("Log Out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            // Only show the top bar on main app screens, not on auth screens
            if (!isOnAuthScreen) {
                TopAppBar(
                    title = { Text(screenTitle) },
                    actions = {
                        // Logout button in the top-right corner
                        IconButton(onClick = { showLogoutDialog = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Log out"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        },
        bottomBar = {
            if (!isOnAuthScreen) {
                NavigationBar {
                    BottomNavItem.all.forEach { item ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) },
                            selected = currentDestination?.hierarchy?.any {
                                it.route == item.route
                            } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            startDestination = startDestination,
            authRepository = app.container.authRepository,
            modifier = Modifier.padding(innerPadding)
        )
    }
}