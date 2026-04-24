package com.platform.smartwastemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.platform.smartwastemanager.features.guide.presentation.GuideViewModel
import com.platform.smartwastemanager.features.home.presentation.HomeViewModel
import com.platform.smartwastemanager.features.map.presentation.MapViewModel
import com.platform.smartwastemanager.features.map.presentation.RouteViewModel
import com.platform.smartwastemanager.features.notifications.presentation.NotificationViewModel
import com.platform.smartwastemanager.features.report.presentation.ReportViewModel
import com.platform.smartwastemanager.features.collectionpoint.presentation.CollectionPointViewModel
import com.platform.smartwastemanager.features.announcement.presentation.AnnouncementViewModel
import com.platform.smartwastemanager.features.askai.presentation.AskAiViewModel
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.AutoAwesome

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

    val navController      = rememberNavController()
    val navBackStackEntry  by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // =========================================================================
    // ViewModels — created ONCE here at Activity level (Rule 3).
    // =========================================================================

    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.factory(app.container.authRepository)
    )

    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            app.container.scheduleRepository,
            app.container.viewToggleRepository,
            app.container.collectionPointRepository,
            app.container.mapRepository
        )
    )
    val reportViewModel: ReportViewModel = viewModel(
        factory = ReportViewModel.factory(
            app.container.reportRepository,
            app.container.wasteImageClassifier
        )
    )
    val mapViewModel: MapViewModel = viewModel(
        factory = MapViewModel.factory(app.container.mapRepository)
    )

    val routeViewModel: RouteViewModel = viewModel(
        factory = RouteViewModel.factory(
            app.container.mapRepository
        )
    )
    val guideViewModel: GuideViewModel = viewModel(
        factory = GuideViewModel.factory(app.container.guideRepository)
    )
    val notificationViewModel: NotificationViewModel = viewModel(
        factory = NotificationViewModel.factory(app.container.notificationRepository)
    )
    val announcementViewModel: AnnouncementViewModel = viewModel(
        factory = AnnouncementViewModel.factory(
            app.container.announcementRepository,
            app.container.notificationRepository
        )
    )
    val collectionPointViewModel: CollectionPointViewModel = viewModel(
        factory = CollectionPointViewModel.factory(
            app.container.collectionPointRepository,
            app.container.mapRepository
        )
    )
    val askAiViewModel: AskAiViewModel = viewModel(
        factory = AskAiViewModel.factory(
            app,
            app.container.aiChatRepository,
            app.container.wasteImageClassifier
        )
    )

    // =========================================================================
    // onAuthSuccess — called after login, signup, AND session restore (Rule 5).
    // =========================================================================
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — notifications degrade gracefully either way */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    authViewModel.onAuthSuccess = {
        homeViewModel.loadCollectionPoints()
        homeViewModel.loadZones()
        homeViewModel.refreshCurrentView()
        mapViewModel.loadPins()
        guideViewModel.loadGuides()
        announcementViewModel.loadAnnouncements()
        collectionPointViewModel.loadCurrentUserPoints()
    }

    authViewModel.onSignOut = {
        homeViewModel.clearAllData()
        collectionPointViewModel.clearAllData()
    }

    val currentUser        by authViewModel.currentUser.collectAsStateWithLifecycle()
    val isDriver           = currentUser?.role == UserRole.DRIVER
    val isDriverViewActive by homeViewModel.isDriverViewActive.collectAsStateWithLifecycle()
    val isViewingAsUser    = isDriver && !isDriverViewActive

    val startDestination = if (app.container.authRepository.isUserSignedIn()) {
        Routes.HOME
    } else {
        Routes.LOGIN
    }

    val authRoutes     = setOf(Routes.LOGIN, Routes.SIGN_UP, Routes.RESET_PASSWORD)
    val isOnAuthScreen = currentDestination?.route in authRoutes

    val screenTitle = when {
        currentDestination?.route == Routes.HOME             -> "Home"
        currentDestination?.route == Routes.MANAGE_SCHEDULES -> "Manage Schedules"
        currentDestination?.route == Routes.REPORT           -> "Report Waste"
        currentDestination?.route == Routes.SCAN             -> "Scan Waste"
        currentDestination?.route == Routes.REPORT_FORM      -> "Submit Report"
        currentDestination?.route == Routes.MAP              -> "Map"
        currentDestination?.route == Routes.GUIDES           -> "Recycling Guides"
        currentDestination?.route == Routes.GUIDE_EDITOR     -> "New Guide"
        currentDestination?.route == Routes.ZONE_MAP_PICKER  -> "Create Zone"
        currentDestination?.route == Routes.MANAGE_ZONES     -> "Global Zones"
        currentDestination?.route == Routes.ACTIVE_ROUTE     -> "Active Route"
        currentDestination?.route == Routes.ANNOUNCEMENTS    -> "Announcements"
        currentDestination?.route == Routes.COLLECTION_POINTS -> "My Collection Points"
        currentDestination?.route == Routes.COLLECTION_POINT_PICKER -> "Set Collection Point"
        currentDestination?.route == Routes.ASK_AI           -> "Ask AI"
        currentDestination?.route?.startsWith("guides/editor/") == true -> "Edit Guide"
        currentDestination?.route?.startsWith("guides/")              == true -> "Guide"
        currentDestination?.route?.startsWith("home/zones/picker")    == true -> "Assign Zone"
        currentDestination?.route?.startsWith("home/zones/")          == true -> "Collection Zones"
        currentDestination?.route == Routes.NOTIFICATIONS    -> "Notification Centre"
        else                                                  -> "Smart Waste Manager"
    }

    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title            = { Text("Log Out") },
            text             = { Text("Are you sure you want to log out?") },
            confirmButton    = {
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
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(screenTitle)
                                currentUser?.username?.let { username ->
                                    Text(
                                        text = "👤 $username",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        },
                        actions = {
                            // ---- ASK AI BUTTON (TOP RIGHT) ----
                            if (currentDestination?.route != Routes.ASK_AI) {
                                IconButton(onClick = {
                                    askAiViewModel.reset()
                                    navController.navigate(Routes.ASK_AI)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Ask AI Assistant",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (isDriver && isDriverViewActive) {
                                IconButton(onClick = {
                                    navController.navigate(Routes.NOTIFICATIONS) {
                                        launchSingleTop = true
                                    }
                                }) {
                                    Icon(
                                        imageVector        = Icons.Default.Notifications,
                                        contentDescription = "Notification Centre"
                                    )
                                }
                            }
                            if (isDriver) {
                                IconButton(onClick = { homeViewModel.toggleDriverView() }) {
                                    Icon(
                                        imageVector = if (isDriverViewActive)
                                            Icons.Default.PersonOff
                                        else
                                            Icons.Default.Person,
                                        contentDescription = if (isDriverViewActive)
                                            "Switch to User View"
                                        else
                                            "Switch to Driver View"
                                    )
                                }
                            }
                            IconButton(onClick = { showLogoutDialog = true }) {
                                Icon(
                                    imageVector        = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Log out"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor         = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor      = MaterialTheme.colorScheme.onPrimaryContainer,
                            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )

                    if (isViewingAsUser) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text  = "👀  Viewing as User  —  tap ",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Icon(
                                imageVector        = Icons.Default.Person,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier           = Modifier.size(16.dp)
                            )
                            Text(
                                text  = "  to switch back",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (!isOnAuthScreen) {
                NavigationBar {
                    BottomNavItem.all.forEach { item ->
                        val isReportDisabled = isDriver && isDriverViewActive && item.route == Routes.REPORT
                        NavigationBarItem(
                            icon     = { Icon(item.icon, contentDescription = item.label) },
                            label    = { Text(item.label) },
                            selected = currentDestination?.hierarchy?.any {
                                it.route == item.route
                            } == true,
                            enabled = !isReportDisabled,
                            onClick  = {
                                if (!isReportDisabled) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState    = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        AppNavHost(
            navController             = navController,
            startDestination          = startDestination,
            authViewModel             = authViewModel,
            homeViewModel             = homeViewModel,
            reportViewModel           = reportViewModel,
            mapViewModel              = mapViewModel,
            routeViewModel            = routeViewModel,
            guideViewModel            = guideViewModel,
            notificationViewModel     = notificationViewModel,
            announcementViewModel     = announcementViewModel,
            collectionPointViewModel  = collectionPointViewModel,
            askAiViewModel            = askAiViewModel,
            modifier                  = Modifier.padding(innerPadding)
        )
    }
}
