package com.platform.smartwastemanager.features.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.platform.smartwastemanager.features.auth.data.AuthRepository
import com.platform.smartwastemanager.features.auth.domain.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds all the UI state for the authentication screens (Login, SignUp, ResetPassword).
 *
 * The screens observe [uiState] and react to changes automatically.
 * The screens call functions like [signIn], [signUp], [sendPasswordReset].
 */
class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    // The single source of truth for what the auth screens should display
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /**
     * Attempts to sign the user in.
     * Updates uiState to Loading → Success or Error.
     */
    fun signIn(email: String, password: String) {
        // Basic input validation before hitting Firebase
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Please fill in all fields")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading

            val result = authRepository.signIn(email.trim(), password)

            _uiState.value = if (result.isSuccess) {
                AuthUiState.Success(result.getOrNull()!!)
            } else {
                AuthUiState.Error(result.exceptionOrNull()?.message ?: "Sign in failed")
            }
        }
    }

    /**
     * Attempts to create a new account.
     * Updates uiState to Loading → Success or Error.
     */
    fun signUp(email: String, password: String, username: String, role: String) {
        // Basic input validation
        if (email.isBlank() || password.isBlank() || username.isBlank()) {
            _uiState.value = AuthUiState.Error("Please fill in all fields")
            return
        }
        if (password.length < 6) {
            _uiState.value = AuthUiState.Error("Password must be at least 6 characters")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading

            val result = authRepository.signUp(email.trim(), password, username.trim(), role)

            _uiState.value = if (result.isSuccess) {
                AuthUiState.Success(result.getOrNull()!!)
            } else {
                AuthUiState.Error(result.exceptionOrNull()?.message ?: "Sign up failed")
            }
        }
    }

    /**
     * Sends a password reset email.
     * Updates uiState to Loading → ResetEmailSent or Error.
     */
    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter your email address")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading

            val result = authRepository.sendPasswordResetEmail(email.trim())

            _uiState.value = if (result.isSuccess) {
                AuthUiState.ResetEmailSent
            } else {
                AuthUiState.Error(result.exceptionOrNull()?.message ?: "Failed to send reset email")
            }
        }
    }

    /**
     * Signs the user out and resets the UI state back to Idle.
     */
    fun signOut() {
        authRepository.signOut()
        _uiState.value = AuthUiState.Idle
    }

    /**
     * Resets the UI state back to Idle.
     * Call this when navigating away from a screen to clear old error messages.
     */
    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }

    /**
     * Factory that creates an AuthViewModel using the AuthRepository from AppContainer.
     * Used in MainActivity or any screen that needs this ViewModel.
     */
    companion object {
        fun factory(authRepository: AuthRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AuthViewModel(authRepository) as T
                }
            }
        }
    }
}

/**
 * Represents every possible state the auth UI can be in.
 * Using a sealed class means we can never forget to handle a state in the UI.
 */
sealed class AuthUiState {
    /** Nothing is happening — the form is just sitting there waiting for input. */
    object Idle : AuthUiState()

    /** A Firebase operation is in progress — show a loading spinner. */
    object Loading : AuthUiState()

    /** Sign in or sign up was successful. Navigate away from the auth screens. */
    data class Success(val user: User) : AuthUiState()

    /** Something went wrong. Show the message to the user. */
    data class Error(val message: String) : AuthUiState()

    /** Password reset email was sent successfully. */
    object ResetEmailSent : AuthUiState()
}