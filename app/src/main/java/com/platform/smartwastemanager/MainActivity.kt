package com.platform.smartwastemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
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

/**
 * The single Activity that hosts the entire app.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SmartWasteManagerTheme {
                // Renamed to avoid clash with the Application class SmartWasteManagerApp
                SmartWasteManagerAppContent()
            }
        }
    }
}

/**
 * The root composable of the app.
 * Named "AppContent" to avoid a naming clash with the Application class
 * which is also called SmartWasteManagerApp.
 */
@Composable
fun SmartWasteManagerAppContent() {
    // Access the AppContainer from the Application class
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext
            as SmartWasteManagerApp

    val navController = rememberNavController()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Decide the first screen to show based on auth state
    val startDestination = if (app.container.authRepository.isUserSignedIn()) {
        Routes.HOME
    } else {
        Routes.LOGIN
    }

    // The auth routes — bottom bar should NOT be visible on these screens
    val authRoutes = setOf(Routes.LOGIN, Routes.SIGN_UP, Routes.RESET_PASSWORD)
    val isOnAuthScreen = currentDestination?.route in authRoutes

    Scaffold(
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