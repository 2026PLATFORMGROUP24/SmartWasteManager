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
 * Manages UI state for all authentication screens.
 * Also holds [currentUser] so the rest of the app can read the signed-in user's role.
 */
class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /**
     * The currently signed-in user.
     * Null if nobody is signed in.
     * Set after a successful signIn() or signUp() call.
     * Also restored on app start if a session already exists.
     */
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    init {
        // If the user was already signed in when the app started (session persists),
        // restore their profile so the role is available immediately.
        restoreSessionIfNeeded()
    }

    /**
     * If Firebase has a persisted session, fetch the user's Firestore profile
     * so [currentUser] is populated before any screen loads.
     */
    private fun restoreSessionIfNeeded() {
        val uid = authRepository.getCurrentUserUid() ?: return
        viewModelScope.launch {
            val result = authRepository.fetchUserProfile(uid)
            if (result.isSuccess) {
                _currentUser.value = result.getOrNull()
            }
        }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Please fill in all fields")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = authRepository.signIn(email.trim(), password)
            if (result.isSuccess) {
                _currentUser.value = result.getOrNull()
                _uiState.value = AuthUiState.Success(result.getOrNull()!!)
            } else {
                _uiState.value = AuthUiState.Error(result.exceptionOrNull()?.message ?: "Sign in failed")
            }
        }
    }

    fun signUp(email: String, password: String, username: String, role: String) {
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
            if (result.isSuccess) {
                _currentUser.value = result.getOrNull()
                _uiState.value = AuthUiState.Success(result.getOrNull()!!)
            } else {
                _uiState.value = AuthUiState.Error(result.exceptionOrNull()?.message ?: "Sign up failed")
            }
        }
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter your email address")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = authRepository.sendPasswordResetEmail(email.trim())
            _uiState.value = if (result.isSuccess) AuthUiState.ResetEmailSent
            else AuthUiState.Error(result.exceptionOrNull()?.message ?: "Failed to send reset email")
        }
    }

    fun signOut() {
        authRepository.signOut()
        _currentUser.value = null
        _uiState.value = AuthUiState.Idle
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }

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

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Success(val user: User) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
    object ResetEmailSent : AuthUiState()
}