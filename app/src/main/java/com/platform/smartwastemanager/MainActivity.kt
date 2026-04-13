package com.platform.smartwastemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.platform.smartwastemanager.core.navigation.AppNavHost
import com.platform.smartwastemanager.core.navigation.BottomNavItem
import com.platform.smartwastemanager.core.navigation.Routes
import com.platform.smartwastemanager.core.theme.SmartWasteManagerTheme
import com.platform.smartwastemanager.features.auth.domain.UserRole
import com.platform.smartwastemanager.features.auth.presentation.AuthViewModel
import com.platform.smartwastemanager.features.home.presentation.HomeViewModel

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

    // ViewModels created here so the top bar can access them too
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.factory(app.container.authRepository)
    )
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            app.container.scheduleRepository,
            app.container.viewToggleRepository
        )
    )

    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    val isDriver = currentUser?.role == UserRole.DRIVER
    val isDriverViewActive by homeViewModel.isDriverViewActive.collectAsStateWithLifecycle()

    val startDestination = if (app.container.authRepository.isUserSignedIn()) {
        Routes.HOME
    } else {
        Routes.LOGIN
    }

    val authRoutes = setOf(Routes.LOGIN, Routes.SIGN_UP, Routes.RESET_PASSWORD)
    val isOnAuthScreen = currentDestination?.route in authRoutes

    val screenTitle = when (currentDestination?.route) {
        Routes.HOME              -> "Home"
        Routes.MANAGE_SCHEDULES  -> "Manage Schedules"
        Routes.REPORT            -> "Report Waste"
        Routes.MAP               -> "Map"
        Routes.GUIDES            -> "Recycling Guides"
        else                     -> "Smart Waste Manager"
    }

    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out") },
            text = { Text("Are you sure you want to log out?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    authViewModel.signOut()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }) {
                    Text("Log Out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            if (!isOnAuthScreen) {
                TopAppBar(
                    title = { Text(screenTitle) },
                    actions = {
                        // ---- Driver view toggle (only visible to drivers) ----
                        if (isDriver) {
                            IconButton(onClick = { homeViewModel.toggleDriverView() }) {
                                Icon(
                                    imageVector = if (isDriverViewActive) Icons.Default.PersonOff
                                    else Icons.Default.Person,
                                    contentDescription = if (isDriverViewActive) "Switch to User View"
                                    else "Switch to Driver View"
                                )
                            }
                        }
                        // ---- Logout button ----
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
                            icon = { Icon(item.icon, contentDescription = item.label) },
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
            scheduleRepository = app.container.scheduleRepository,
            viewToggleRepository = app.container.viewToggleRepository,
            modifier = Modifier.padding(innerPadding)
        )
    }
}