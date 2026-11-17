package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.getyourmoneyupnotyourfunnyup.data.repository.AuthSessionSource
import com.example.getyourmoneyupnotyourfunnyup.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AuthForm { Login, Register }

data class AuthFields(
    val form: AuthForm = AuthForm.Login,
    val displayName: String = "",
    val email: String = "",
    val password: String = ""
)

sealed interface AuthUiState {
    data object Restoring : AuthUiState
    data class SignedOut(
        val fields: AuthFields = AuthFields(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null
    ) : AuthUiState
    data class SignedIn(val user: User, val statusMessage: String = "") : AuthUiState
}

/** Owns the S1/S2 session flow. Credentials are kept in memory, never saved state. */
class AuthViewModel(private val source: AuthSessionSource) : ViewModel() {
    private val mutableState = MutableStateFlow<AuthUiState>(AuthUiState.Restoring)
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var userJob: Job? = null

    init {
        val uid = source.currentUid
        if (uid == null) {
            mutableState.value = AuthUiState.SignedOut()
        } else {
            val request = ++generation
            source.restore(uid, source.currentEmail.orEmpty()) { result ->
                finishAuthentication(request, result, AuthFields(), "Loaded user")
            }
        }
    }

    fun setEmail(value: String) = edit { copy(email = value) }
    fun setPassword(value: String) = edit { copy(password = value) }
    fun setDisplayName(value: String) = edit { copy(displayName = value) }
    fun showRegister() = edit { copy(form = AuthForm.Register, password = "") }
    fun showLogin() = edit { copy(form = AuthForm.Login, password = "") }

    private fun edit(transform: AuthFields.() -> AuthFields) {
        val current = state.value as? AuthUiState.SignedOut ?: return
        if (!current.isLoading) mutableState.value = current.copy(fields = current.fields.transform(), errorMessage = null)
    }

    fun submit() {
        val current = state.value as? AuthUiState.SignedOut ?: return
        if (current.isLoading) return
        val fields = current.fields
        val valid = fields.email.isNotBlank() && fields.password.isNotBlank() &&
            (fields.form == AuthForm.Login || (fields.displayName.isNotBlank() && fields.password.length >= 6))
        if (!valid) {
            mutableState.value = current.copy(errorMessage = "Enter the required account details.")
            return
        }
        mutableState.value = current.copy(isLoading = true, errorMessage = null)
        val request = ++generation
        if (fields.form == AuthForm.Register) {
            source.register(fields.displayName, fields.email, fields.password) {
                finishAuthentication(request, it, fields, "Created user")
            }
        } else {
            source.login(fields.email, fields.password) {
                finishAuthentication(request, it, fields, "Fetched user")
            }
        }
    }

    private fun finishAuthentication(request: Long, result: Result<User>, fields: AuthFields, message: String) {
        viewModelScope.launch {
            if (request != generation) return@launch
            result.fold(
                onSuccess = { user ->
                    mutableState.value = AuthUiState.SignedIn(user, "$message from /users/${user.id}")
                    observeUser(user, request)
                },
                onFailure = { error ->
                    mutableState.value = AuthUiState.SignedOut(
                        fields = fields.copy(password = ""),
                        errorMessage = error.localizedMessage ?: "Could not sign in. Please try again."
                    )
                }
            )
        }
    }

    private fun observeUser(user: User, request: Long) {
        userJob?.cancel()
        userJob = viewModelScope.launch {
            try {
                source.observeUser(user) { message ->
                    viewModelScope.launch {
                        val current = state.value as? AuthUiState.SignedIn
                        if (generation == request && current != null) {
                            mutableState.value = current.copy(statusMessage = "Profile update failed: $message")
                        }
                    }
                }.collect { updated ->
                    val current = state.value as? AuthUiState.SignedIn
                    if (generation == request && current?.user?.id == updated.id) {
                        mutableState.value = current.copy(user = updated)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val current = state.value as? AuthUiState.SignedIn
                if (generation == request && current != null) {
                    mutableState.value = current.copy(statusMessage = "Profile update failed: ${error.localizedMessage ?: "Try again later"}")
                }
            }
        }
    }

    fun logout() {
        ++generation
        userJob?.cancel()
        userJob = null
        source.signOut()
        mutableState.value = AuthUiState.SignedOut()
    }
}
