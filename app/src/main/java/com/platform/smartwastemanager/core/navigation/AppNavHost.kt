package com.platform.smartwastemanager.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.platform.smartwastemanager.features.auth.domain.UserRole
import com.platform.smartwastemanager.features.auth.presentation.AuthViewModel
import com.platform.smartwastemanager.features.auth.presentation.LoginScreen
import com.platform.smartwastemanager.features.auth.presentation.ResetPasswordScreen
import com.platform.smartwastemanager.features.auth.presentation.SignUpScreen
import com.platform.smartwastemanager.features.guide.presentation.GuideListScreen
import com.platform.smartwastemanager.features.home.presentation.HomeScreen
import com.platform.smartwastemanager.features.home.presentation.HomeViewModel
import com.platform.smartwastemanager.features.home.presentation.ScheduleManagementScreen
import com.platform.smartwastemanager.features.map.presentation.MapScreen
import com.platform.smartwastemanager.features.map.presentation.MapViewModel
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
    modifier: Modifier = Modifier
) {
    val currentUser        by authViewModel.currentUser.collectAsStateWithLifecycle()
    val isDriverViewActive by homeViewModel.isDriverViewActive.collectAsStateWithLifecycle()

    val isDriver = currentUser?.role == UserRole.DRIVER

    // True only when the user IS a driver AND is currently in driver view (not user-view).
    // Regular users: false. Drivers in user-view: false. Drivers in driver-view: true.
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
                viewModel          = homeViewModel,
                isDriver           = isDriver,
                onNavigateToManage = { navController.navigate(Routes.MANAGE_SCHEDULES) },
                onNavigateToGuide  = { guideId ->
                    navController.navigate(Routes.buildGuideDetail(guideId))
                }
            )
        }

        composable(Routes.MANAGE_SCHEDULES) {
            ScheduleManagementScreen(
                viewModel      = homeViewModel,
                driverUid      = currentUser?.uid ?: "",
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
                viewModel       = reportViewModel,
                currentUserUid  = currentUser?.uid ?: "",
                onNavigateBack  = { navController.popBackStack() },
                onSubmitSuccess = {
                    navController.navigate(Routes.REPORT) {
                        popUpTo(Routes.REPORT) { inclusive = true }
                    }
                }
            )
        }

        // ==================== MAP ====================

        composable(Routes.MAP) {
            MapScreen(
                viewModel            = mapViewModel,
                // Pins are only visible to drivers actively in driver view.
                // Regular users and drivers in user-view see an empty map.
                isDriverInDriverView = isDriverInDriverView
            )
        }

        // ==================== GUIDES ====================

        composable(Routes.GUIDES) {
            GuideListScreen()
        }
    }
}