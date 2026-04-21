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
import com.platform.smartwastemanager.features.guide.presentation.GuideDetailScreen
import com.platform.smartwastemanager.features.guide.presentation.GuideEditorScreen
import com.platform.smartwastemanager.features.guide.presentation.GuideListScreen
import com.platform.smartwastemanager.features.guide.presentation.GuideViewModel
import com.platform.smartwastemanager.features.home.presentation.HomeScreen
import com.platform.smartwastemanager.features.home.presentation.HomeViewModel
import com.platform.smartwastemanager.features.home.presentation.ScheduleManagementScreen
import com.platform.smartwastemanager.features.map.presentation.ActiveRouteScreen
import com.platform.smartwastemanager.features.map.presentation.ManageZonesScreen
import com.platform.smartwastemanager.features.map.presentation.MapScreen
import com.platform.smartwastemanager.features.map.presentation.MapViewModel
import com.platform.smartwastemanager.features.map.presentation.RouteViewModel
import com.platform.smartwastemanager.features.report.presentation.LocationPickerMapScreen
import com.platform.smartwastemanager.features.report.presentation.ReportFormScreen
import com.platform.smartwastemanager.features.report.presentation.ReportScreen
import com.platform.smartwastemanager.features.report.presentation.ReportViewModel
import com.platform.smartwastemanager.features.report.presentation.ScanScreen
import com.platform.smartwastemanager.features.notifications.presentation.NotificationScreen
import com.platform.smartwastemanager.features.notifications.presentation.NotificationViewModel
import com.platform.smartwastemanager.features.announcement.presentation.AnnouncementScreen
import com.platform.smartwastemanager.features.announcement.presentation.AnnouncementViewModel
import com.platform.smartwastemanager.features.collectionpoint.presentation.CollectionPointViewModel
import com.platform.smartwastemanager.features.collectionpoint.presentation.CollectionPointPickerScreen
import com.platform.smartwastemanager.features.collectionpoint.presentation.ManageCollectionPointsScreen
import com.platform.smartwastemanager.features.map.presentation.ZoneMapPickerScreen
import com.platform.smartwastemanager.features.aiassist.presentation.AiAssistViewModel
import com.platform.smartwastemanager.features.aiassist.presentation.AiHistoryScreen

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
    aiAssistViewModel: AiAssistViewModel,
    modifier: Modifier = Modifier
) {
    val currentUser        by authViewModel.currentUser.collectAsStateWithLifecycle()
    val isDriverViewActive by homeViewModel.isDriverViewActive.collectAsStateWithLifecycle()
    val guides             by guideViewModel.guides.collectAsStateWithLifecycle()
    val selectedZone       by homeViewModel.selectedZone.collectAsStateWithLifecycle()
    val selectedPoint      by homeViewModel.selectedPoint.collectAsStateWithLifecycle()

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

        composable(Routes.HOME) {
            HomeScreen(
                viewModel            = homeViewModel,
                isDriver             = isDriver,
                onNavigateToManage   = { navController.navigate(Routes.MANAGE_SCHEDULES) },
                onNavigateToGuide    = { guideId ->
                   navController.navigate(Routes.buildGuideDetail(guideId))
                },
                onNavigateToZones    = { _, _ -> /* Deprecated - not used in new zone-based system */ },
                onNavigateToManagePoints = {
                    navController.navigate(Routes.MANAGE_COLLECTION_POINTS)
                },
                onNavigateToManageZones = {
                    navController.navigate(Routes.MANAGE_ZONES)
                },
                onCalculateRoute = { zoneName, scheduleDayId ->
                    navController.navigate(Routes.buildActiveRoute(zoneName, scheduleDayId))
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

        composable(Routes.MANAGE_ZONES) {
            ManageZonesScreen(
                viewModel      = routeViewModel,
                onNavigateBack = { navController.popBackStack() },
                onCreateZone   = { navController.navigate(Routes.ZONE_MAP_PICKER) },
                currentDriverUid = currentUser?.uid ?: "",
                selectedZone   = selectedZone,
                onSelectZone   = { homeViewModel.selectZone(it) }
            )
        }

        composable(Routes.MANAGE_COLLECTION_POINTS) {
            ManageCollectionPointsScreen(
                viewModel = collectionPointViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCreate = { navController.navigate(Routes.COLLECTION_POINT_PICKER) },
                selectedPointId = selectedPoint?.id,
                onPointSelected = { point -> homeViewModel.selectCollectionPoint(point) }
            )
        }

        composable(
            route     = Routes.ACTIVE_ROUTE,
            arguments = listOf(
                navArgument("zoneName") { type = NavType.StringType },
                navArgument("scheduleDayId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val zoneName = (backStackEntry.arguments?.getString("zoneName") ?: "")
                .replace("_", " ")
            val scheduleDayId = backStackEntry.arguments?.getString("scheduleDayId") ?: ""
            ActiveRouteScreen(
                viewModel      = routeViewModel,
                zoneName       = zoneName,
                scheduleDayId  = scheduleDayId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        // ADD this composable route (it's missing from your current AppNavHost):

        composable(Routes.ZONE_MAP_PICKER) {
            ZoneMapPickerScreen(
                viewModel      = routeViewModel,
                driverUid      = currentUser?.uid ?: "",
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
            ManageCollectionPointsScreen(
                viewModel = collectionPointViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCreate = { navController.navigate(Routes.COLLECTION_POINT_PICKER) },
                selectedPointId = selectedPoint?.id,
                onPointSelected = { point -> homeViewModel.selectCollectionPoint(point) }
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
                isDriverInDriverView = isDriverInDriverView,
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
                aiAssistViewModel          = aiAssistViewModel,
                currentUserUid             = currentUser?.uid ?: "",
                onNavigateBack             = { navController.popBackStack() },
                onSubmitSuccess            = {
                    navController.navigate(Routes.REPORT) {
                        popUpTo(Routes.REPORT) { inclusive = true }
                    }
                },
                onNavigateToLocationPicker = { navController.navigate(Routes.LOCATION_PICKER) },
                onNavigateToScan           = {
                    navController.navigate(Routes.SCAN) {
                        popUpTo(Routes.REPORT_FORM) { inclusive = true }
                    }
                }
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
                    // Navigate to guides list instead of detail
                    navController.navigate(Routes.GUIDES) {
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

        // ======================== AI HISTORY ========================

        composable(Routes.AI_HISTORY) {
            AiHistoryScreen(
                viewModel        = aiAssistViewModel,
                currentUserUid   = currentUser?.uid ?: "",
                onNavigateBack   = { navController.popBackStack() }
            )
        }
    }
}
