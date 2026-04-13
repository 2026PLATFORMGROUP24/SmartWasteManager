package com.platform.smartwastemanager.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import com.platform.smartwastemanager.features.home.presentation.HomeScreen
import com.platform.smartwastemanager.features.map.presentation.MapScreen
import com.platform.smartwastemanager.features.report.presentation.ReportScreen

/**
 * The central navigation graph for the entire app.
 *
 * This composable sets up every screen route and wires up the navigation
 * callbacks (e.g. "on login success, go to home").
 *
 * @param navController     Controls navigation actions (navigate, popBackStack, etc.)
 * @param startDestination  The first screen shown — either LOGIN or HOME depending on auth state.
 * @param authRepository    Passed in from AppContainer to create the AuthViewModel.
 * @param modifier          Optional modifier for the NavHost container.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
    authRepository: AuthRepository,
    modifier: Modifier = Modifier
) {
    // One shared AuthViewModel for all auth screens — they all need the same state
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.factory(authRepository)
    )

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {

        // ==================== AUTH SCREENS ====================

        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = {
                    // Go to Home and clear the entire auth back stack
                    // so the user can't press Back to get back to Login
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToSignUp = {
                    navController.navigate(Routes.SIGN_UP)
                },
                onNavigateToReset = {
                    navController.navigate(Routes.RESET_PASSWORD)
                }
            )
        }

        composable(Routes.SIGN_UP) {
            SignUpScreen(
                viewModel = authViewModel,
                onSignUpSuccess = {
                    // Same as login success — clear auth stack and go to Home
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.RESET_PASSWORD) {
            ResetPasswordScreen(
                viewModel = authViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // ==================== MAIN APP SCREENS ====================

        composable(Routes.HOME) {
            HomeScreen()
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