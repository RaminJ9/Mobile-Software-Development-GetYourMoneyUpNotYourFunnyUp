package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.getyourmoneyupnotyourfunnyup.data.repository.AuthRepository
import com.example.getyourmoneyupnotyourfunnyup.model.DietaryRules
import com.example.getyourmoneyupnotyourfunnyup.model.NotificationSettings
import com.example.getyourmoneyupnotyourfunnyup.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Abstraction for persisting profile edits, dietary rules, notification settings, and password resets.
 */
interface ProfileActions {
    suspend fun updateProfile(
        user: User,
        fullName: String,
        email: String,
        phoneNumber: String
    ): Result<User>

    suspend fun updatePreferences(
        user: User,
        dietaryRules: DietaryRules,
        notificationSettings: NotificationSettings
    ): Result<User>

    suspend fun sendPasswordReset(email: String): Result<Unit>

    companion object {
        fun fromAuthRepository(authRepository: AuthRepository): ProfileActions =
            object : ProfileActions {
                override suspend fun updateProfile(
                    user: User,
                    fullName: String,
                    email: String,
                    phoneNumber: String
                ): Result<User> = suspendCoroutine { cont ->
                    authRepository.updateProfile(
                        user = user,
                        fullName = fullName,
                        email = email,
                        phoneNumber = phoneNumber,
                        onResult = { cont.resume(it) }
                    )
                }

                override suspend fun updatePreferences(
                    user: User,
                    dietaryRules: DietaryRules,
                    notificationSettings: NotificationSettings
                ): Result<User> = suspendCoroutine { cont ->
                    authRepository.updatePreferences(
                        user = user,
                        dietaryRules = dietaryRules,
                        notificationSettings = notificationSettings,
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
            }
    }
}

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Error(val message: String) : ProfileUiState

    @Immutable
    data class Success(
        val user: User,
        val isEditing: Boolean = false,
        val draftName: String = user.displayName,
        val draftEmail: String = user.email,
        val draftPhone: String = user.phoneNumber,
        val showSavedToast: Boolean = false,
        val infoMessage: String? = null,
        val errorMessage: String? = null,
        val isOffline: Boolean = false
    ) : ProfileUiState {
        val initial: String
            get() {
                val source = if (isEditing) draftName else user.displayName
                return source.trim().firstOrNull()?.uppercase() ?: "Y"
            }
    }
}

class ProfileViewModel(
    private val userFlow: Flow<User?>,
    private val actions: ProfileActions? = null,
    initialUser: User? = null,
    private val toastDurationMillis: Long = 1600L
) : ViewModel() {

    private val mutableState = MutableStateFlow<ProfileUiState>(
        if (initialUser != null) ProfileUiState.Success(initialUser) else ProfileUiState.Loading
    )
    val state: StateFlow<ProfileUiState> = mutableState.asStateFlow()

    private var toastJob: Job? = null

    init {
        observeUser()
    }

    private fun observeUser() {
        viewModelScope.launch {
            try {
                userFlow.collect { latestUser ->
                    if (latestUser == null) return@collect
                    val current = mutableState.value as? ProfileUiState.Success
                    mutableState.value = if (current == null) {
                        ProfileUiState.Success(user = latestUser)
                    } else if (current.isEditing) {
                        current.copy(user = latestUser)
                    } else {
                        current.copy(
                            user = latestUser,
                            draftName = latestUser.displayName,
                            draftEmail = latestUser.email,
                            draftPhone = latestUser.phoneNumber
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val current = mutableState.value as? ProfileUiState.Success
                if (current == null) {
                    mutableState.value = ProfileUiState.Error(e.message ?: "Couldn't load your profile")
                }
            }
        }
    }

    private fun updateSuccess(transform: (ProfileUiState.Success) -> ProfileUiState.Success) {
        mutableState.update { current ->
            if (current is ProfileUiState.Success) transform(current) else current
        }
    }

    fun startEdit() = updateSuccess { current ->
        current.copy(
            isEditing = true,
            draftName = current.user.displayName,
            draftEmail = current.user.email,
            draftPhone = current.user.phoneNumber,
            errorMessage = null,
            infoMessage = null
        )
    }

    fun cancelEdit() = updateSuccess { current ->
        current.copy(
            isEditing = false,
            draftName = current.user.displayName,
            draftEmail = current.user.email,
            draftPhone = current.user.phoneNumber,
            errorMessage = null
        )
    }

    fun onDraftNameChange(value: String) = updateSuccess { current ->
        current.copy(draftName = value, errorMessage = null)
    }

    fun onDraftEmailChange(value: String) = updateSuccess { current ->
        current.copy(draftEmail = value, errorMessage = null)
    }

    fun onDraftPhoneChange(value: String) = updateSuccess { current ->
        current.copy(draftPhone = value, errorMessage = null)
    }

    fun saveEdit() {
        val current = mutableState.value as? ProfileUiState.Success ?: return
        val trimmedName = current.draftName.trim()
        val trimmedEmail = current.draftEmail.trim()
        val trimmedPhone = current.draftPhone.trim()

        if (trimmedName.isBlank()) {
            updateSuccess { it.copy(errorMessage = "Please enter your full name.") }
            return
        }
        if (trimmedEmail.isBlank()) {
            updateSuccess { it.copy(errorMessage = "Please enter your email address.") }
            return
        }

        val optimisticUser = current.user.copy(
            displayName = trimmedName,
            email = trimmedEmail,
            phoneNumber = trimmedPhone
        )
        updateSuccess {
            it.copy(
                user = optimisticUser,
                isEditing = false,
                draftName = trimmedName,
                draftEmail = trimmedEmail,
                draftPhone = trimmedPhone,
                errorMessage = null,
                infoMessage = null
            )
        }
        triggerSavedToast()

        val service = actions ?: return
        viewModelScope.launch {
            service.updateProfile(
                user = current.user,
                fullName = trimmedName,
                email = trimmedEmail,
                phoneNumber = trimmedPhone
            ).onSuccess { savedUser ->
                updateSuccess { state ->
                    state.copy(
                        user = savedUser,
                        draftName = if (state.isEditing) state.draftName else savedUser.displayName,
                        draftEmail = if (state.isEditing) state.draftEmail else savedUser.email,
                        draftPhone = if (state.isEditing) state.draftPhone else savedUser.phoneNumber
                    )
                }
            }.onFailure { err ->
                updateSuccess { state ->
                    state.copy(errorMessage = err.localizedMessage ?: "Couldn't save profile changes.")
                }
            }
        }
    }

    fun toggleExcludeAlcohol() {
        val current = mutableState.value as? ProfileUiState.Success ?: return
        val nextRules = current.user.dietaryRules.copy(
            excludeAlcohol = !current.user.dietaryRules.excludeAlcohol
        )
        persistPreferences(nextRules, current.user.notificationSettings)
    }

    fun toggleExcludeMeat() {
        val current = mutableState.value as? ProfileUiState.Success ?: return
        val nextRules = current.user.dietaryRules.copy(
            excludeMeat = !current.user.dietaryRules.excludeMeat
        )
        persistPreferences(nextRules, current.user.notificationSettings)
    }

    fun toggleNewExpenses() {
        val current = mutableState.value as? ProfileUiState.Success ?: return
        val notif = current.user.notificationSettings
        val nextNotif = notif.copy(
            newExpenses = !notif.newExpenses,
            expenseAlerts = !notif.newExpenses || notif.changedExpenses
        )
        persistPreferences(current.user.dietaryRules, nextNotif)
    }

    fun toggleChangedExpenses() {
        val current = mutableState.value as? ProfileUiState.Success ?: return
        val notif = current.user.notificationSettings
        val nextNotif = notif.copy(
            changedExpenses = !notif.changedExpenses,
            expenseAlerts = notif.newExpenses || !notif.changedExpenses
        )
        persistPreferences(current.user.dietaryRules, nextNotif)
    }

    fun togglePaymentReminders() {
        val current = mutableState.value as? ProfileUiState.Success ?: return
        val notif = current.user.notificationSettings
        val nextNotif = notif.copy(paymentReminders = !notif.paymentReminders)
        persistPreferences(current.user.dietaryRules, nextNotif)
    }

    fun toggleSyncUpdates() {
        val current = mutableState.value as? ProfileUiState.Success ?: return
        val notif = current.user.notificationSettings
        val nextNotif = notif.copy(syncUpdates = !notif.syncUpdates)
        persistPreferences(current.user.dietaryRules, nextNotif)
    }

    private fun persistPreferences(
        dietaryRules: DietaryRules,
        notificationSettings: NotificationSettings
    ) {
        val current = mutableState.value as? ProfileUiState.Success ?: return
        val optimisticUser = current.user.copy(
            dietaryRules = dietaryRules,
            notificationSettings = notificationSettings
        )
        updateSuccess { it.copy(user = optimisticUser, errorMessage = null) }
        triggerSavedToast()

        val service = actions ?: return
        viewModelScope.launch {
            service.updatePreferences(
                user = current.user,
                dietaryRules = dietaryRules,
                notificationSettings = notificationSettings
            ).onSuccess { savedUser ->
                updateSuccess { it.copy(user = savedUser) }
            }
        }
    }

    fun requestPasswordChange() {
        val current = mutableState.value as? ProfileUiState.Success ?: return
        val service = actions
        if (service == null) {
            updateSuccess {
                it.copy(
                    infoMessage = "Password reset link sent to ${current.user.email}",
                    errorMessage = null
                )
            }
            triggerSavedToast()
            return
        }
        viewModelScope.launch {
            service.sendPasswordReset(current.user.email)
                .onSuccess {
                    updateSuccess {
                        it.copy(
                            infoMessage = "Password reset link sent to ${current.user.email}",
                            errorMessage = null
                        )
                    }
                    triggerSavedToast()
                }
                .onFailure { err ->
                    updateSuccess {
                        it.copy(
                            errorMessage = err.localizedMessage ?: "Couldn't send password reset email.",
                            infoMessage = null
                        )
                    }
                }
        }
    }

    private fun triggerSavedToast() {
        toastJob?.cancel()
        updateSuccess { it.copy(showSavedToast = true) }
        if (toastDurationMillis > 0) {
            toastJob = viewModelScope.launch {
                delay(toastDurationMillis)
                updateSuccess { it.copy(showSavedToast = false) }
            }
        }
    }
}
