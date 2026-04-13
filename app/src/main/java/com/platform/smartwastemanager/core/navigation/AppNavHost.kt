package com.platform.smartwastemanager.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.platform.smartwastemanager.features.auth.data.AuthRepository
import com.platform.smartwastemanager.features.auth.presentation.AuthViewModel
import com.platform.smartwastemanager.features.auth.presentation.LoginScreen
import com.platform.smartwastemanager.features.auth.presentation.ResetPasswordScreen
import com.platform.smartwastemanager.features.auth.presentation.SignUpScreen
import com.platform.smartwastemanager.features.guide.presentation.GuideListScreen
import com.platform.smartwastemanager.features.home.data.ScheduleRepository
import com.platform.smartwastemanager.features.home.presentation.HomeScreen
import com.platform.smartwastemanager.features.home.presentation.HomeViewModel
import com.platform.smartwastemanager.features.home.presentation.ScheduleManagementScreen
import com.platform.smartwastemanager.features.map.presentation.MapScreen
import com.platform.smartwastemanager.features.report.presentation.ReportScreen
import com.platform.smartwastemanager.core.util.ViewToggleRepository
import com.platform.smartwastemanager.features.auth.domain.UserRole

/**
 * The central navigation graph for the entire app.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
    authRepository: AuthRepository,
    scheduleRepository: ScheduleRepository,
    viewToggleRepository: ViewToggleRepository,
    modifier: Modifier = Modifier
) {
    // Shared ViewModels
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.factory(authRepository)
    )

    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(scheduleRepository, viewToggleRepository)
    )

    // Determine if the signed-in user is a driver so we can pass it to screens
    // We read the current user's role from AuthRepository indirectly — for now
    // we store the signed-in User in AuthViewModel after login.
    // A simple way: re-fetch role from Firestore via a currentUser StateFlow.
    // For Phase 3 we use a helper on AuthViewModel:
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    val isDriver = currentUser?.role == UserRole.DRIVER

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {

        // ==================== AUTH ====================

        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToSignUp = { navController.navigate(Routes.SIGN_UP) },
                onNavigateToReset = { navController.navigate(Routes.RESET_PASSWORD) }
            )
        }

        composable(Routes.SIGN_UP) {
            SignUpScreen(
                viewModel = authViewModel,
                onSignUpSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }

        composable(Routes.RESET_PASSWORD) {
            ResetPasswordScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ==================== MAIN APP ====================

        composable(Routes.HOME) {
            HomeScreen(
                viewModel = homeViewModel,
                isDriver = isDriver,
                onNavigateToManage = { navController.navigate(Routes.MANAGE_SCHEDULES) },
                onNavigateToGuide = { guideId ->
                    navController.navigate(Routes.buildGuideDetail(guideId))
                }
            )
        }

        composable(Routes.MANAGE_SCHEDULES) {
            ScheduleManagementScreen(
                viewModel = homeViewModel,
                driverUid = currentUser?.uid ?: "",
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.REPORT) {
            ReportScreen()
        }

        composable(Routes.MAP) {
            MapScreen()
        }

        composable(Routes.GUIDES) {
            GuideListScreen()
        }
    }
}