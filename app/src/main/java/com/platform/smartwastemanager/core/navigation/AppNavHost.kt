package com.platform.smartwastemanager.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.platform.smartwastemanager.features.auth.domain.UserRole
import com.platform.smartwastemanager.features.auth.presentation.AuthViewModel
import com.platform.smartwastemanager.features.auth.presentation.LoginScreen
import com.platform.smartwastemanager.features.auth.presentation.ResetPasswordScreen
import com.platform.smartwastemanager.features.auth.presentation.SignUpScreen
import com.platform.smartwastemanager.features.guide.presentation.GuideListScreen
import com.platform.smartwastemanager.features.home.presentation.HomeScreen
import com.platform.smartwastemanager.features.home.presentation.HomeViewModel
import com.platform.smartwastemanager.features.home.presentation.ScheduleManagementScreen
import com.platform.smartwastemanager.features.map.presentation.ActiveRouteScreen
import com.platform.smartwastemanager.features.map.presentation.MapScreen
import com.platform.smartwastemanager.features.map.presentation.MapViewModel
import com.platform.smartwastemanager.features.map.presentation.RouteViewModel
import com.platform.smartwastemanager.features.map.presentation.ZoneListScreen
import com.platform.smartwastemanager.features.map.presentation.ZoneMapPickerScreen
import com.platform.smartwastemanager.features.report.presentation.LocationPickerMapScreen
import com.platform.smartwastemanager.features.report.presentation.ReportFormScreen
import com.platform.smartwastemanager.features.report.presentation.ReportScreen
import com.platform.smartwastemanager.features.report.presentation.ReportViewModel
import com.platform.smartwastemanager.features.report.presentation.ScanScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel,
    reportViewModel: ReportViewModel,
    mapViewModel: MapViewModel,
    routeViewModel: RouteViewModel,
    modifier: Modifier = Modifier
) {
    val currentUser        by authViewModel.currentUser.collectAsStateWithLifecycle()
    val isDriverViewActive by homeViewModel.isDriverViewActive.collectAsStateWithLifecycle()

    val isDriver             = currentUser?.role == UserRole.DRIVER
    val isDriverInDriverView = isDriver && isDriverViewActive

    NavHost(
        navController    = navController,
        startDestination = startDestination,
        modifier         = modifier
    ) {

        // ==================== AUTH ====================

        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel          = authViewModel,
                onLoginSuccess     = {
                    navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
                },
                onNavigateToSignUp = { navController.navigate(Routes.SIGN_UP) },
                onNavigateToReset  = { navController.navigate(Routes.RESET_PASSWORD) }
            )
        }

        composable(Routes.SIGN_UP) {
            SignUpScreen(
                viewModel         = authViewModel,
                onSignUpSuccess   = {
                    navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
                },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }

        composable(Routes.RESET_PASSWORD) {
            ResetPasswordScreen(
                viewModel      = authViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ==================== HOME ====================

        composable(Routes.HOME) {
            HomeScreen(
                viewModel            = homeViewModel,
                isDriver             = isDriver,
                onNavigateToManage   = { navController.navigate(Routes.MANAGE_SCHEDULES) },
                onNavigateToGuide    = { guideId ->
                    navController.navigate(Routes.buildGuideDetail(guideId))
                },
                onNavigateToZones    = { scheduleDayId, scheduleDayName ->
                    navController.navigate(Routes.buildZoneList(scheduleDayId, scheduleDayName))
                },
                isDriverInDriverView = isDriverInDriverView
            )
        }

        composable(Routes.MANAGE_SCHEDULES) {
            ScheduleManagementScreen(
                viewModel      = homeViewModel,
                driverUid      = currentUser?.uid ?: "",
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ==================== ZONES ====================

        composable(
            route     = Routes.ZONE_LIST,
            arguments = listOf(
                navArgument("scheduleDayId")   { type = NavType.StringType },
                navArgument("scheduleDayName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val scheduleDayId   = backStackEntry.arguments?.getString("scheduleDayId") ?: ""
            val scheduleDayName = backStackEntry.arguments?.getString("scheduleDayName") ?: ""
            ZoneListScreen(
                viewModel       = routeViewModel,
                scheduleDayId   = scheduleDayId,
                scheduleDayName = scheduleDayName,
                driverUid       = currentUser?.uid ?: "",
                onNavigateBack  = { navController.popBackStack() },
                onAddZone       = { navController.navigate(Routes.ZONE_MAP_PICKER) },
                onLoadRoute     = { zone ->
                    routeViewModel.loadRouteForZone(zone)
                    navController.navigate(Routes.buildActiveRoute(zone.name))
                }
            )
        }

        composable(Routes.ZONE_MAP_PICKER) {
            ZoneMapPickerScreen(
                viewModel      = routeViewModel,
                driverUid      = currentUser?.uid ?: "",
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route     = Routes.ACTIVE_ROUTE,
            arguments = listOf(navArgument("zoneName") { type = NavType.StringType })
        ) { backStackEntry ->
            val zoneName = (backStackEntry.arguments?.getString("zoneName") ?: "")
                .replace("_", " ")
            ActiveRouteScreen(
                viewModel      = routeViewModel,
                zoneName       = zoneName,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ==================== REPORT ====================

        composable(Routes.REPORT) {
            ReportScreen(
                onNavigateToScan = { navController.navigate(Routes.SCAN) },
                onNavigateToForm = { navController.navigate(Routes.REPORT_FORM) }
            )
        }

        composable(Routes.SCAN) {
            ScanScreen(
                viewModel        = reportViewModel,
                onNavigateToForm = {
                    navController.navigate(Routes.REPORT_FORM) {
                        popUpTo(Routes.SCAN) { inclusive = true }
                    }
                },
                onNavigateBack   = { navController.popBackStack() }
            )
        }

        composable(Routes.REPORT_FORM) {
            ReportFormScreen(
                viewModel                  = reportViewModel,
                currentUserUid             = currentUser?.uid ?: "",
                onNavigateBack             = { navController.popBackStack() },
                onSubmitSuccess            = {
                    navController.navigate(Routes.REPORT) {
                        popUpTo(Routes.REPORT) { inclusive = true }
                    }
                },
                // NEW: open the full-screen location picker
                onNavigateToLocationPicker = { navController.navigate(Routes.LOCATION_PICKER) }
            )
        }

        // NEW: full-screen location picker — pushes onto back stack so
        // the user can pop back to the form with the confirmed location already set.
        composable(Routes.LOCATION_PICKER) {
            LocationPickerMapScreen(
                viewModel      = reportViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ==================== MAP ====================

        composable(Routes.MAP) {
            MapScreen(
                viewModel            = mapViewModel,
                isDriverInDriverView = isDriverInDriverView,
                driverUid            = currentUser?.uid ?: ""   // NEW — enables zone overlays
            )
        }

        // ==================== GUIDES ====================

        composable(Routes.GUIDES) {
            GuideListScreen()
        }
    }
}