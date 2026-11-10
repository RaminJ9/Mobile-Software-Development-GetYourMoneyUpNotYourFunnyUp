package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.example.getyourmoneyupnotyourfunnyup.data.repository.AuthRepository
import com.example.getyourmoneyupnotyourfunnyup.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class AuthMode { Login, Register }

@Immutable
data class AuthUiState(
    val mode: AuthMode = AuthMode.Login,
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    /** Set once sign-in succeeds; the screen hands it to the session and calls [AuthViewModel.onAuthenticatedHandled]. */
    val authenticatedUser: User? = null,
    /** Phone number typed during registration, passed on to the profile. */
    val registeredPhone: String? = null,
) {
    val canSubmit: Boolean
        get() = !isLoading && email.isNotBlank() && password.isNotBlank() &&
            (mode == AuthMode.Login || fullName.isNotBlank())
}

sealed interface AuthAction {
    data class NameChanged(val value: String) : AuthAction
    data class EmailChanged(val value: String) : AuthAction
    data class PhoneChanged(val value: String) : AuthAction
    data class PasswordChanged(val value: String) : AuthAction
    data object TogglePasswordVisibility : AuthAction
    data object Submit : AuthAction
    data object ForgotPassword : AuthAction
}

/** S1 Login / S2 Registration, backed by Firebase Auth through [AuthRepository]. */
class AuthViewModel(
    private val authRepository: AuthRepository,
    mode: AuthMode,
) : ViewModel() {

    private val state = MutableStateFlow(AuthUiState(mode = mode))
    val uiState: StateFlow<AuthUiState> = state.asStateFlow()

    fun onAction(action: AuthAction) {
        when (action) {
            is AuthAction.NameChanged -> state.update { it.copy(fullName = action.value, errorMessage = null) }
            is AuthAction.EmailChanged -> state.update { it.copy(email = action.value, errorMessage = null, infoMessage = null) }
            is AuthAction.PhoneChanged -> state.update { it.copy(phone = action.value) }
            is AuthAction.PasswordChanged -> state.update { it.copy(password = action.value, errorMessage = null) }
            AuthAction.TogglePasswordVisibility -> state.update { it.copy(passwordVisible = !it.passwordVisible) }
            AuthAction.Submit -> submit()
            AuthAction.ForgotPassword -> forgotPassword()
        }
    }

    fun onAuthenticatedHandled() = state.update { it.copy(authenticatedUser = null) }

    private fun submit() {
        val current = state.value
        if (!current.canSubmit) return
        state.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
        val onResult: (Result<User>) -> Unit = { result ->
            result
                .onSuccess { user ->
                    state.update {
                        it.copy(isLoading = false, authenticatedUser = user, registeredPhone = it.phone.ifBlank { null })
                    }
                }
                .onFailure { error ->
                    state.update { it.copy(isLoading = false, errorMessage = error.localizedMessage ?: "Something went wrong") }
                }
        }
        when (current.mode) {
            AuthMode.Login -> authRepository.loginUser(current.email, current.password, onResult)
            AuthMode.Register -> authRepository.registerUser(current.fullName, current.email, current.password, onResult)
        }
    }

    private fun forgotPassword() {
        val email = state.value.email.trim()
        if (email.isEmpty()) {
            state.update { it.copy(errorMessage = "Type your email first, then tap “Forgot password?” again.") }
            return
        }
        authRepository.sendPasswordReset(email) { result ->
            state.update {
                if (result.isSuccess) it.copy(infoMessage = "We sent a reset link to $email.", errorMessage = null)
                else it.copy(errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Couldn't send the reset email")
            }
        }
    }
}
