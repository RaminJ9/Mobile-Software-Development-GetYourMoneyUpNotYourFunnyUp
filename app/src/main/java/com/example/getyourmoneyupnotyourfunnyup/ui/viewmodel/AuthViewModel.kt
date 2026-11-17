package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.getyourmoneyupnotyourfunnyup.data.repository.AuthRepository
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

enum class AuthMode {
    Login,
    Register
}

sealed interface SessionState {
    data object Unauthenticated : SessionState

    @Immutable
    data class Authenticated(
        val user: User,
        val selectedTab: AppTab = AppTab.Home
    ) : SessionState
}

@Immutable
data class AuthUiState(
    val session: SessionState = SessionState.Unauthenticated,
    val mode: AuthMode = AuthMode.Login,
    val displayName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

/**
 * Testable authentication service contract backed by [AuthRepository] in production.
 */
interface AuthGateway {
    val currentAuthUid: String?
    val currentAuthEmail: String?

    suspend fun fetchOrCreateUser(
        uid: String,
        fallbackEmail: String,
        fallbackName: String
    ): Result<User>

    suspend fun login(
        email: String,
        password: String
    ): Result<User>

    suspend fun register(
        displayName: String,
        email: String,
        phoneNumber: String,
        password: String
    ): Result<User>

    suspend fun sendPasswordReset(email: String): Result<Unit>

    fun signOut()

    companion object {
        fun fromAuthRepository(authRepository: AuthRepository): AuthGateway =
            object : AuthGateway {
                override val currentAuthUid: String?
                    get() = authRepository.currentAuthUid

                override val currentAuthEmail: String?
                    get() = authRepository.currentAuthEmail

                override suspend fun fetchOrCreateUser(
                    uid: String,
                    fallbackEmail: String,
                    fallbackName: String
                ): Result<User> = suspendCoroutine { cont ->
                    authRepository.fetchOrCreateUserDocument(
                        uid = uid,
                        fallbackEmail = fallbackEmail,
                        fallbackName = fallbackName,
                        onResult = { cont.resume(it) }
                    )
                }

                override suspend fun login(
                    email: String,
                    password: String
                ): Result<User> = suspendCoroutine { cont ->
                    authRepository.loginUser(
                        email = email,
                        password = password,
                        onResult = { cont.resume(it) }
                    )
                }

                override suspend fun register(
                    displayName: String,
                    email: String,
                    phoneNumber: String,
                    password: String
                ): Result<User> = suspendCoroutine { cont ->
                    authRepository.registerUser(
                        displayName = displayName,
                        email = email,
                        password = password,
                        phoneNumber = phoneNumber,
                        onResult = { cont.resume(it) }
                    )
                }

                override suspend fun sendPasswordReset(email: String): Result<Unit> =
                    suspendCoroutine { cont ->
                        authRepository.sendPasswordResetEmail(
                            email = email,
                            onResult = { cont.resume(it) }
                        )
                    }

                override fun signOut() {
                    authRepository.signOut()
                }
            }
    }
}

class AuthViewModel(
    private val gateway: AuthGateway
) : ViewModel() {

    private val mutableState = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = mutableState.asStateFlow()

    init {
        restoreSession()
    }

    fun restoreSession() {
        val existingUid = gateway.currentAuthUid ?: return
        val existingEmail = gateway.currentAuthEmail.orEmpty()
        mutableState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = gateway.fetchOrCreateUser(
                uid = existingUid,
                fallbackEmail = existingEmail,
                fallbackName = existingEmail.substringBefore("@").ifEmpty { "User" }
            )
            result.onSuccess { user ->
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = null,
                        session = SessionState.Authenticated(
                            user = user,
                            selectedTab = AppTab.Home
                        )
                    )
                }
            }.onFailure {
                mutableState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onDisplayNameChange(value: String) {
        mutableState.update { it.copy(displayName = value, errorMessage = null, infoMessage = null) }
    }

    fun onEmailChange(value: String) {
        mutableState.update { it.copy(email = value, errorMessage = null, infoMessage = null) }
    }

    fun onPhoneNumberChange(value: String) {
        mutableState.update { it.copy(phoneNumber = value, errorMessage = null, infoMessage = null) }
    }

    fun onPasswordChange(value: String) {
        mutableState.update { it.copy(password = value, errorMessage = null, infoMessage = null) }
    }

    fun switchToRegister() {
        mutableState.update {
            it.copy(
                mode = AuthMode.Register,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun switchToLogin() {
        mutableState.update {
            it.copy(
                mode = AuthMode.Login,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun login() {
        val current = mutableState.value
        if (current.isLoading) return
        if (current.email.isBlank() || current.password.isBlank()) {
            mutableState.update { it.copy(errorMessage = "Please enter your email and password.") }
            return
        }

        mutableState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
        viewModelScope.launch {
            gateway.login(
                email = current.email.trim(),
                password = current.password
            ).onSuccess { user ->
                // Redirect directly to Home (S3 View groups) as specified in the navigation model
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        password = "",
                        errorMessage = null,
                        infoMessage = null,
                        session = SessionState.Authenticated(
                            user = user,
                            selectedTab = AppTab.Home
                        )
                    )
                }
            }.onFailure { err ->
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.localizedMessage ?: "Login failed"
                    )
                }
            }
        }
    }

    fun register() {
        val current = mutableState.value
        if (current.isLoading) return
        if (current.displayName.isBlank() || current.email.isBlank() || current.password.length < 6) {
            mutableState.update {
                it.copy(errorMessage = "Please enter your name, email, and a password with at least 6 characters.")
            }
            return
        }

        mutableState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
        viewModelScope.launch {
            gateway.register(
                displayName = current.displayName.trim(),
                email = current.email.trim(),
                phoneNumber = current.phoneNumber.trim(),
                password = current.password
            ).onSuccess { user ->
                // Redirect directly to Home (S3 View groups) as specified in the navigation model
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        password = "",
                        errorMessage = null,
                        infoMessage = null,
                        session = SessionState.Authenticated(
                            user = user,
                            selectedTab = AppTab.Home
                        )
                    )
                }
            }.onFailure { err ->
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.localizedMessage ?: "Registration failed"
                    )
                }
            }
        }
    }

    fun forgotPassword() {
        val current = mutableState.value
        if (current.isLoading) return
        if (current.email.isBlank()) {
            mutableState.update {
                it.copy(
                    errorMessage = "Enter your email address above so we can send a reset link.",
                    infoMessage = null
                )
            }
            return
        }

        mutableState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
        viewModelScope.launch {
            gateway.sendPasswordReset(current.email.trim())
                .onSuccess {
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            infoMessage = "Password reset link sent to ${current.email.trim()}",
                            errorMessage = null
                        )
                    }
                }
                .onFailure { err ->
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Couldn't send password reset link."
                        )
                    }
                }
        }
    }

    fun selectTab(tab: AppTab) {
        mutableState.update { current ->
            val authSession = current.session as? SessionState.Authenticated ?: return@update current
            current.copy(session = authSession.copy(selectedTab = tab))
        }
    }

    fun onUserUpdated(user: User) {
        mutableState.update { current ->
            val authSession = current.session as? SessionState.Authenticated ?: return@update current
            current.copy(session = authSession.copy(user = user))
        }
    }

    fun logout() {
        gateway.signOut()
        mutableState.update {
            it.copy(
                session = SessionState.Unauthenticated,
                mode = AuthMode.Login,
                password = "",
                isLoading = false,
                errorMessage = null,
                infoMessage = null
            )
        }
    }
}
