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
import com.platform.smartwastemanager.features.map.presentation.ManageZonesScreen
import com.platform.smartwastemanager.features.map.presentation.MapScreen
import com.platform.smartwastemanager.features.map.presentation.MapViewModel
import com.platform.smartwastemanager.features.map.presentation.RouteViewModel
import com.platform.smartwastemanager.features.map.presentation.ZoneListScreen
import com.platform.smartwastemanager.features.map.presentation.ZoneMapPickerScreen
import com.platform.smartwastemanager.features.map.presentation.ZonePickerScreen
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
    val schedules          by homeViewModel.schedules.collectAsStateWithLifecycle()

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

        // ZoneListScreen — shows zones assigned to a specific schedule day.
        // We look up the full CollectionDay object from the schedules list so we
        // can pass it to ZoneListScreen (it needs the zoneIds list, not just the ID).
        composable(
            route     = Routes.ZONE_LIST,
            arguments = listOf(
                navArgument("scheduleDayId")   { type = NavType.StringType },
                navArgument("scheduleDayName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val scheduleDayId   = backStackEntry.arguments?.getString("scheduleDayId") ?: ""
            val scheduleDayName = backStackEntry.arguments?.getString("scheduleDayName") ?: ""

            // Find the matching CollectionDay from the already-loaded schedules list.
            // If not found yet (e.g. list still loading), fall back to a stub with just the id.
            val schedule = schedules.find { it.id == scheduleDayId }
                ?: com.platform.smartwastemanager.features.home.domain.CollectionDay(
                    id         = scheduleDayId,
                    dayOfWeek  = scheduleDayName
                )

            ZoneListScreen(
                viewModel      = routeViewModel,
                schedule       = schedule,
                onNavigateBack = { navController.popBackStack() },
                onAssignZone   = {
                    navController.navigate(
                        Routes.buildZonePicker(scheduleDayId, scheduleDayName)
                    )
                },
                onManageZones  = { navController.navigate(Routes.MANAGE_ZONES) },
                onLoadRoute    = { zone ->
                    routeViewModel.loadRouteForZone(zone)
                    navController.navigate(Routes.buildActiveRoute(zone.name))
                }
            )
        }

        // ZonePickerScreen — pick from all global zones to assign to a schedule day.
        composable(
            route     = Routes.ZONE_PICKER,
            arguments = listOf(
                navArgument("scheduleDayId")   { type = NavType.StringType },
                navArgument("scheduleDayName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val scheduleDayId   = backStackEntry.arguments?.getString("scheduleDayId") ?: ""
            val scheduleDayName = backStackEntry.arguments?.getString("scheduleDayName") ?: ""

            // Pass the current zoneIds so the picker can mark already-assigned zones
            val schedule = schedules.find { it.id == scheduleDayId }
            val alreadyAssignedIds = schedule?.zoneIds ?: emptyList()

            ZonePickerScreen(
                viewModel          = routeViewModel,
                alreadyAssignedIds = alreadyAssignedIds,
                scheduleDayName    = scheduleDayName,
                onNavigateBack     = { navController.popBackStack() }
            )
        }

        // ManageZonesScreen — global zone CRUD (create / delete zones).
        composable(Routes.MANAGE_ZONES) {
            ManageZonesScreen(
                viewModel      = routeViewModel,
                onNavigateBack = { navController.popBackStack() },
                onCreateZone   = { navController.navigate(Routes.ZONE_MAP_PICKER) }
            )
        }

        // ZoneMapPickerScreen — map UI for drawing a new global zone.
        composable(Routes.ZONE_MAP_PICKER) {
            ZoneMapPickerScreen(
                viewModel      = routeViewModel,
                driverUid      = currentUser?.uid ?: "",
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ActiveRouteScreen — turn-by-turn driving route execution.
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
                onNavigateToLocationPicker = { navController.navigate(Routes.LOCATION_PICKER) }
            )
        }

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
                driverUid            = currentUser?.uid ?: ""
            )
        }

        // ==================== GUIDES ====================

        composable(Routes.GUIDES) {
            GuideListScreen()
        }
    }
}