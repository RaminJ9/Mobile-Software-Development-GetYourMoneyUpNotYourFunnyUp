package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.getyourmoneyupnotyourfunnyup.data.repository.ProfileRepository
import com.example.getyourmoneyupnotyourfunnyup.model.ProfileChanges
import com.example.getyourmoneyupnotyourfunnyup.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ProfilePreference { Alcohol, Meat, NewExpenses, ChangedExpenses, PaymentReminders, SyncUpdates }
data class ProfileDraft(val name: String, val phone: String, val email: String = "")

sealed interface ProfileUiState {
    data class Success(
        val user: User,
        val draft: ProfileDraft? = null,
        val isSaving: Boolean = false,
        val errorMessage: String? = null,
        val saved: Boolean = false,
        val canSave: Boolean = true,
        val infoMessage: String? = null
    ) : ProfileUiState
}

class ProfileViewModel(initialUser: User, private val repository: ProfileRepository?) : ViewModel() {
    private val accountId = initialUser.id
    private val mutableState = MutableStateFlow(ProfileUiState.Success(initialUser, canSave = repository != null))
    val state = mutableState.asStateFlow()

    init {
        if (repository != null) viewModelScope.launch {
            try {
                repository.observeProfile(accountId).collect { user ->
                    val current = state.value
                    if (user != null && user.id == accountId && user.updatedAt >= current.user.updatedAt) {
                        mutableState.value = current.copy(user = user)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = state.value.copy(errorMessage = "Couldn't refresh your profile. ${error.localizedMessage.orEmpty()}")
            }
        }
    }

    fun edit() {
        val current = state.value
        if (!current.isSaving && current.canSave) mutableState.value = current.copy(
            draft = ProfileDraft(current.user.displayName, current.user.phoneNumber.orEmpty(), current.user.email), errorMessage = null, saved = false
        )
    }
    fun cancel() {
        if (!state.value.isSaving) mutableState.value = state.value.copy(draft = null, errorMessage = null)
    }
    fun setName(name: String) = updateDraft { copy(name = name) }
    fun setEmail(email: String) = updateDraft { copy(email = email) }
    fun setPhone(phone: String) = updateDraft { copy(phone = phone) }
    private fun updateDraft(change: ProfileDraft.() -> ProfileDraft) {
        val current = state.value
        if (!current.isSaving) mutableState.value = current.copy(draft = current.draft?.change(), errorMessage = null)
    }

    fun saveDetails() {
        val current = state.value
        val draft = current.draft ?: return
        val name = draft.name.trim()
        val phone = draft.phone.trim().ifEmpty { null }
        if (name.isBlank()) {
            mutableState.value = current.copy(errorMessage = "Enter your full name.")
            return
        }
        if (phone != null && (phone.none { it.isDigit() } || phone.any { !it.isDigit() && it !in "+ ()-" })) {
            mutableState.value = current.copy(errorMessage = "Enter a phone number using digits and an optional country code.")
            return
        }
        val email = draft.email.trim()
        if (!Regex("^[^\\s@]+@[^\\s@]+$").matches(email)) {
            mutableState.value = current.copy(errorMessage = "Enter a valid email address.")
            return
        }
        save(ProfileChanges.fromUser(current.user).copy(displayName = name, phoneNumber = phone), closeEditor = true,
            newEmail = email.takeIf { !it.equals(current.user.email, ignoreCase = true) })
    }

    fun toggle(preference: ProfilePreference, enabled: Boolean) {
        val current = state.value
        if (current.draft != null) return
        val user = current.user
        val changes = ProfileChanges.fromUser(user)
        val next = when (preference) {
            ProfilePreference.Alcohol -> changes.copy(excludeAlcohol = enabled)
            ProfilePreference.Meat -> changes.copy(excludeMeat = enabled)
            ProfilePreference.NewExpenses -> changes.copy(notificationSettings = user.notificationSettings.copy(expenseAlerts = enabled))
            ProfilePreference.ChangedExpenses -> changes.copy(notificationSettings = user.notificationSettings.copy(changedExpenseAlerts = enabled))
            ProfilePreference.PaymentReminders -> changes.copy(notificationSettings = user.notificationSettings.copy(paymentReminders = enabled))
            ProfilePreference.SyncUpdates -> changes.copy(notificationSettings = user.notificationSettings.copy(syncUpdates = enabled))
        }
        save(next, closeEditor = false)
    }

    private fun save(changes: ProfileChanges, closeEditor: Boolean, newEmail: String? = null) {
        val source = repository ?: return
        val current = state.value
        if (current.isSaving) return
        mutableState.value = current.copy(isSaving = true, errorMessage = null, saved = false)
        viewModelScope.launch {
            try {
                if (newEmail != null) source.requestEmailChange(accountId, newEmail)
                val saved = source.save(accountId, changes)
                val latest = state.value
                mutableState.value = latest.copy(
                    user = if (saved.updatedAt > latest.user.updatedAt) saved else latest.user,
                    draft = if (closeEditor) null else latest.draft, isSaving = false, saved = true,
                    infoMessage = if (newEmail != null) "Verification link sent to $newEmail. Open it, then log out and log in with your new email." else latest.infoMessage
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = state.value.copy(isSaving = false, errorMessage = error.localizedMessage ?: "Couldn't save your profile. Please try again.")
            }
        }
    }
}
