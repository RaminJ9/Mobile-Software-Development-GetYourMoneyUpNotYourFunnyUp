package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.getyourmoneyupnotyourfunnyup.data.repository.AuthRepository
import com.example.getyourmoneyupnotyourfunnyup.data.repository.LedgerRepository
import com.example.getyourmoneyupnotyourfunnyup.model.DietRule
import com.example.getyourmoneyupnotyourfunnyup.model.LedgerSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NotifyKey(val label: String, val description: String) {
    NewExpenses("New expenses", "When someone adds an expense you’re part of"),
    ChangedExpenses("Changed expenses", "When an expense you’re in is edited or removed"),
    PaymentReminders("Payment reminders", "When a friend reminds you to pay"),
    SyncUpdates("Sync updates", "When an offline expense finishes syncing"),
}

@Immutable
data class ProfileUiState(
    val name: String,
    val email: String,
    val phone: String?,
    val initial: String,
    val editing: Boolean,
    val draftName: String,
    val draftEmail: String,
    val draftPhone: String,
    val noAlcohol: Boolean,
    val noMeat: Boolean,
    val notify: Map<NotifyKey, Boolean>,
    /** Short confirmation shown at the bottom ("Saved"). */
    val toast: String?,
)

sealed interface ProfileAction {
    data object StartEdit : ProfileAction
    data object CancelEdit : ProfileAction
    data object SaveEdit : ProfileAction
    data class DraftName(val value: String) : ProfileAction
    data class DraftEmail(val value: String) : ProfileAction
    data class DraftPhone(val value: String) : ProfileAction
    data class SetDietRule(val rule: DietRule, val enabled: Boolean) : ProfileAction
    data class SetNotify(val key: NotifyKey, val enabled: Boolean) : ProfileAction
    data object ChangePassword : ProfileAction
}

data class ProfileLocalState(
    val editing: Boolean = false,
    val draftName: String = "",
    val draftEmail: String = "",
    val draftPhone: String = "",
    val toast: String? = null,
)

fun buildProfileUiState(snapshot: LedgerSnapshot, local: ProfileLocalState): ProfileUiState {
    val me = snapshot.me
    val prefs = snapshot.notificationPrefs
    return ProfileUiState(
        name = me.name,
        email = me.contact,
        phone = me.phone,
        initial = me.initial,
        editing = local.editing,
        draftName = local.draftName,
        draftEmail = local.draftEmail,
        draftPhone = local.draftPhone,
        noAlcohol = DietRule.NO_ALCOHOL in me.dietRules,
        noMeat = DietRule.NO_MEAT in me.dietRules,
        notify = mapOf(
            NotifyKey.NewExpenses to prefs.newExpenses,
            NotifyKey.ChangedExpenses to prefs.changedExpenses,
            NotifyKey.PaymentReminders to prefs.paymentReminders,
            NotifyKey.SyncUpdates to prefs.syncUpdates,
        ),
        toast = local.toast,
    )
}

/** View profile: details, "Never charge me for" rules, notification settings, account. */
class ProfileViewModel(
    private val ledger: LedgerRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val local = MutableStateFlow(ProfileLocalState())
    private var toastJob: Job? = null

    val uiState: StateFlow<ProfileUiState> = combine(ledger.snapshot, local, ::buildProfileUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, buildProfileUiState(ledger.snapshot.value, local.value))

    fun onAction(action: ProfileAction) {
        when (action) {
            ProfileAction.StartEdit -> {
                val me = ledger.snapshot.value.me
                local.update { it.copy(editing = true, draftName = me.name, draftEmail = me.contact, draftPhone = me.phone.orEmpty()) }
            }
            ProfileAction.CancelEdit -> local.update { it.copy(editing = false) }
            ProfileAction.SaveEdit -> {
                val draft = local.value
                ledger.updateProfile(draft.draftName, draft.draftEmail, draft.draftPhone)
                local.update { it.copy(editing = false) }
                showToast("Saved")
            }
            is ProfileAction.DraftName -> local.update { it.copy(draftName = action.value) }
            is ProfileAction.DraftEmail -> local.update { it.copy(draftEmail = action.value) }
            is ProfileAction.DraftPhone -> local.update { it.copy(draftPhone = action.value) }
            is ProfileAction.SetDietRule -> {
                ledger.setDietRule(action.rule, action.enabled)
                showToast("Saved")
            }
            is ProfileAction.SetNotify -> {
                val prefs = ledger.snapshot.value.notificationPrefs
                ledger.setNotificationPrefs(
                    when (action.key) {
                        NotifyKey.NewExpenses -> prefs.copy(newExpenses = action.enabled)
                        NotifyKey.ChangedExpenses -> prefs.copy(changedExpenses = action.enabled)
                        NotifyKey.PaymentReminders -> prefs.copy(paymentReminders = action.enabled)
                        NotifyKey.SyncUpdates -> prefs.copy(syncUpdates = action.enabled)
                    },
                )
                showToast("Saved")
            }
            ProfileAction.ChangePassword -> {
                val email = ledger.snapshot.value.me.contact
                authRepository.sendPasswordReset(email) { result ->
                    showToast(if (result.isSuccess) "Reset link sent to $email" else "Couldn't send the reset email")
                }
            }
        }
    }

    private fun showToast(message: String) {
        local.update { it.copy(toast = message) }
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(if (message == "Saved") 1_600 else 3_000)
            local.update { it.copy(toast = null) }
        }
    }
}
