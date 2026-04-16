package com.platform.smartwastemanager.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.platform.smartwastemanager.core.util.LocationHelper
import com.platform.smartwastemanager.features.auth.domain.UserRole
import com.platform.smartwastemanager.features.auth.presentation.AuthViewModel
import com.platform.smartwastemanager.features.auth.presentation.LoginScreen
import com.platform.smartwastemanager.features.auth.presentation.ResetPasswordScreen
import com.platform.smartwastemanager.features.auth.presentation.SignUpScreen
import com.platform.smartwastemanager.features.guide.presentation.GuideDetailScreen
import com.platform.smartwastemanager.features.guide.presentation.GuideEditorScreen
import com.platform.smartwastemanager.features.guide.presentation.GuideListScreen
import com.platform.smartwastemanager.features.guide.presentation.GuideViewModel
import com.platform.smartwastemanager.features.home.domain.CollectionDay
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
import kotlinx.coroutines.launch
import com.platform.smartwastemanager.features.notifications.presentation.NotificationScreen
import com.platform.smartwastemanager.features.notifications.presentation.NotificationViewModel
import com.platform.smartwastemanager.features.announcement.presentation.AnnouncementScreen
import com.platform.smartwastemanager.features.announcement.presentation.AnnouncementViewModel
import com.platform.smartwastemanager.features.collectionpoint.presentation.CollectionPointViewModel
import com.platform.smartwastemanager.features.collectionpoint.presentation.CollectionPointsScreen
import com.platform.smartwastemanager.features.collectionpoint.presentation.CollectionPointPickerScreen

/**
 * Central navigation host for the app.
 *
 * Rule 3  : All ViewModels are created in MainActivity and passed here as parameters.
 * Rule 11 : AppNavHost and MainActivity signatures must always be in sync.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel,
    reportViewModel: ReportViewModel,
    mapViewModel: MapViewModel,
    routeViewModel: RouteViewModel,
    guideViewModel: GuideViewModel,
    notificationViewModel: NotificationViewModel,
    announcementViewModel: AnnouncementViewModel,
    collectionPointViewModel: CollectionPointViewModel,
    modifier: Modifier = Modifier
) {
    val currentUser        by authViewModel.currentUser.collectAsStateWithLifecycle()
    val isDriverViewActive by homeViewModel.isDriverViewActive.collectAsStateWithLifecycle()
    val schedules          by homeViewModel.schedules.collectAsStateWithLifecycle()
    val guides             by guideViewModel.guides.collectAsStateWithLifecycle()

    val isDriver             = currentUser?.role == UserRole.DRIVER
    val isDriverInDriverView = isDriver && isDriverViewActive

    NavHost(
        navController    = navController,
        startDestination = startDestination,
        modifier         = modifier
    ) {

        // ======================== AUTH ========================

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

        // ======================== HOME ========================

        // Update HomeScreen composable call (around line 116-129):
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
                onNavigateToCollectionPointPicker = {
                    navController.navigate(Routes.COLLECTION_POINT_PICKER)
                },
                onNavigateToZoneManagement = {
                    navController.navigate(Routes.MANAGE_ZONES)
                },
                onCalculateRoute = { zoneId, scheduleDayId ->
                    // TODO: Implement route calculation based on marked points
                },
                isDriverInDriverView = isDriverInDriverView
            )
        }

        composable(Routes.MANAGE_SCHEDULES) {
            ScheduleManagementScreen(
                viewModel      = homeViewModel,
                driverUid      = currentUser?.uid ?: "",
                guides         = guides,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ======================== ZONES ========================

        composable(
            route     = Routes.ZONE_LIST,
            arguments = listOf(
                navArgument("scheduleDayId")   { type = NavType.StringType },
                navArgument("scheduleDayName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val scheduleDayId   = backStackEntry.arguments?.getString("scheduleDayId")   ?: ""
            val scheduleDayName = backStackEntry.arguments?.getString("scheduleDayName") ?: ""

            val schedule = schedules.find { it.id == scheduleDayId }
                ?: CollectionDay(id = scheduleDayId, dayOfWeek = scheduleDayName)

            val zoneListScope = rememberCoroutineScope()
            val context       = navController.context

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
                    zoneListScope.launch {
                        val gp = try { LocationHelper.getCurrentLocation(context) }
                        catch (e: Exception) { null }
                        routeViewModel.loadRouteForZone(
                            zone      = zone,
                            driverLat = gp?.latitude  ?: 0.0,
                            driverLng = gp?.longitude ?: 0.0
                        )
                        navController.navigate(Routes.buildActiveRoute(zone.name))
                    }
                }
            )
        }

        composable(
            route     = Routes.ZONE_PICKER,
            arguments = listOf(
                navArgument("scheduleDayId")   { type = NavType.StringType },
                navArgument("scheduleDayName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val scheduleDayId      = backStackEntry.arguments?.getString("scheduleDayId")   ?: ""
            val scheduleDayName    = backStackEntry.arguments?.getString("scheduleDayName") ?: ""
            val alreadyAssignedIds = schedules.find { it.id == scheduleDayId }?.zoneIds
                ?: emptyList()

            ZonePickerScreen(
                viewModel          = routeViewModel,
                alreadyAssignedIds = alreadyAssignedIds,
                scheduleDayName    = scheduleDayName,
                onNavigateBack     = { navController.popBackStack() }
            )
        }

        composable(Routes.MANAGE_ZONES) {
            ManageZonesScreen(
                viewModel      = routeViewModel,
                onNavigateBack = { navController.popBackStack() },
                onCreateZone   = { navController.navigate(Routes.ZONE_MAP_PICKER) }
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

        // ======================== ANNOUNCEMENTS ========================

        composable(Routes.ANNOUNCEMENTS) {
            AnnouncementScreen(
                viewModel            = announcementViewModel,
                isDriverInDriverView = isDriverInDriverView,
                currentUserUid       = currentUser?.uid ?: ""
            )
        }

        // ======================== COLLECTION POINTS ========================

        composable(Routes.COLLECTION_POINTS) {
            CollectionPointsScreen(
                viewModel          = collectionPointViewModel,
                onNavigateToCreate = { navController.navigate(Routes.COLLECTION_POINT_PICKER) },
                onPointSelected    = { point ->
                    collectionPointViewModel.selectPoint(point)
                    // Navigate to a schedule view filtered by this point's zone
                    // TODO: implement filtered schedule view
                }
            )
        }

        composable(Routes.COLLECTION_POINT_PICKER) {
            CollectionPointPickerScreen(
                viewModel      = collectionPointViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ======================== REPORT ========================

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

        // ======================== MAP ========================

        composable(Routes.MAP) {
            MapScreen(
                viewModel            = mapViewModel,
                isDriverInDriverView = isDriverInDriverView,
                driverUid            = currentUser?.uid ?: ""
            )
        }

        // ======================== GUIDES ========================

        composable(Routes.GUIDES) {
            GuideListScreen(
                viewModel              = guideViewModel,
                isDriverInDriverView   = isDriverInDriverView,
                onNavigateToDetail     = { guideId ->
                    navController.navigate(Routes.buildGuideDetail(guideId))
                },
                onNavigateToEditor     = { navController.navigate(Routes.GUIDE_EDITOR) },
                onNavigateToEditorEdit = { guideId ->
                    navController.navigate(Routes.buildGuideEditor(guideId))
                }
            )
        }

        composable(
            route     = Routes.GUIDE_DETAIL,
            arguments = listOf(navArgument("guideId") { type = NavType.StringType })
        ) { backStackEntry ->
            val guideId = backStackEntry.arguments?.getString("guideId") ?: ""
            GuideDetailScreen(
                viewModel            = guideViewModel,
                guideId              = guideId,
                isDriverInDriverView = isDriverInDriverView,
                onNavigateBack       = { navController.popBackStack() },
                onNavigateToEdit     = { id ->
                    navController.navigate(Routes.buildGuideEditor(id))
                }
            )
        }

        composable(Routes.GUIDE_EDITOR) {
            GuideEditorScreen(
                viewModel      = guideViewModel,
                guideId        = null,
                currentUserUid = currentUser?.uid ?: "",
                onNavigateBack = { navController.popBackStack() },
                onSaveSuccess  = { newId ->
                    navController.navigate(Routes.buildGuideDetail(newId)) {
                        popUpTo(Routes.GUIDE_EDITOR) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route     = Routes.GUIDE_EDITOR_EDIT,
            arguments = listOf(navArgument("guideId") { type = NavType.StringType })
        ) { backStackEntry ->
            val guideId = backStackEntry.arguments?.getString("guideId") ?: ""
            GuideEditorScreen(
                viewModel      = guideViewModel,
                guideId        = guideId,
                currentUserUid = currentUser?.uid ?: "",
                onNavigateBack = { navController.popBackStack() },
                onSaveSuccess  = { updatedId ->
                    navController.navigate(Routes.buildGuideDetail(updatedId)) {
                        popUpTo(Routes.buildGuideEditor(guideId)) { inclusive = true }
                    }
                }
            )
        }

        // ======================== NOTIFICATIONS ========================

        composable(Routes.NOTIFICATIONS) {
            NotificationScreen(
                viewModel      = notificationViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
