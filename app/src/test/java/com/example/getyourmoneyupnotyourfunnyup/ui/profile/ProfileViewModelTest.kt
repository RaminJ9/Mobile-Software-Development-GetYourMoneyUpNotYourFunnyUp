package com.example.getyourmoneyupnotyourfunnyup.ui.profile

import com.example.getyourmoneyupnotyourfunnyup.model.DietaryRules
import com.example.getyourmoneyupnotyourfunnyup.model.NotificationSettings
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ProfileActions
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ProfileUiState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val sampleUser = User(
        id = "usr-victor",
        email = "victor@example.dk",
        displayName = "Victor Munk",
        phoneNumber = "+45 12 34 56 78",
        dietaryRules = DietaryRules(excludeAlcohol = false, excludeMeat = false),
        notificationSettings = NotificationSettings(
            newExpenses = true,
            changedExpenses = true,
            paymentReminders = true,
            syncUpdates = true
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun editAndSaveProfile_updatesNameEmailPhoneAndShowsSavedToast() = runTest(dispatcher) {
        val userFlow = MutableStateFlow<User?>(sampleUser)
        val actions = FakeProfileActions(userFlow)
        val viewModel = ProfileViewModel(
            userFlow = userFlow,
            actions = actions,
            initialUser = sampleUser,
            toastDurationMillis = 1600L
        )
        advanceUntilIdle()

        viewModel.startEdit()
        assertTrue((viewModel.state.value as ProfileUiState.Success).isEditing)

        viewModel.onDraftNameChange("Victor M. Munk")
        viewModel.onDraftEmailChange("victor.munk@example.dk")
        viewModel.onDraftPhoneChange("+45 87 65 43 21")
        viewModel.saveEdit()

        val savedState = viewModel.state.value as ProfileUiState.Success
        assertFalse(savedState.isEditing)
        assertEquals("Victor M. Munk", savedState.user.displayName)
        assertEquals("victor.munk@example.dk", savedState.user.email)
        assertEquals("+45 87 65 43 21", savedState.user.phoneNumber)
        assertTrue(savedState.showSavedToast)

        // After 1600ms, the floating "Saved" pill hides automatically
        advanceTimeBy(1700L)
        assertFalse((viewModel.state.value as ProfileUiState.Success).showSavedToast)
    }

    @Test
    fun cancelEdit_discardsDraftAndRestoresOriginalUserValues() = runTest(dispatcher) {
        val userFlow = MutableStateFlow<User?>(sampleUser)
        val viewModel = ProfileViewModel(userFlow = userFlow, initialUser = sampleUser)
        advanceUntilIdle()

        viewModel.startEdit()
        viewModel.onDraftNameChange("Temporary Name")
        viewModel.cancelEdit()

        val state = viewModel.state.value as ProfileUiState.Success
        assertFalse(state.isEditing)
        assertEquals("Victor Munk", state.draftName)
        assertEquals("Victor Munk", state.user.displayName)
    }

    @Test
    fun toggleDietaryAndNotificationPreferences_persistsAllSixSwitches() = runTest(dispatcher) {
        val userFlow = MutableStateFlow<User?>(sampleUser)
        val actions = FakeProfileActions(userFlow)
        val viewModel = ProfileViewModel(
            userFlow = userFlow,
            actions = actions,
            initialUser = sampleUser,
            toastDurationMillis = 0L
        )
        advanceUntilIdle()

        // Toggle "Never charge me for" rules (Alcohol & Meat)
        viewModel.toggleExcludeAlcohol()
        viewModel.toggleExcludeMeat()
        advanceUntilIdle()

        var state = viewModel.state.value as ProfileUiState.Success
        assertTrue(state.user.dietaryRules.excludeAlcohol)
        assertTrue(state.user.dietaryRules.excludeMeat)

        // Toggle "Notify me about" switches (New expenses, Changed expenses, Payment reminders, Sync updates)
        viewModel.toggleNewExpenses()
        viewModel.toggleChangedExpenses()
        viewModel.togglePaymentReminders()
        viewModel.toggleSyncUpdates()
        advanceUntilIdle()

        state = viewModel.state.value as ProfileUiState.Success
        assertFalse(state.user.notificationSettings.newExpenses)
        assertFalse(state.user.notificationSettings.changedExpenses)
        assertFalse(state.user.notificationSettings.paymentReminders)
        assertFalse(state.user.notificationSettings.syncUpdates)
        assertFalse(state.user.notificationSettings.expenseAlerts)
    }

    @Test
    fun offlineProfileSave_preservesLocalChangesAndExposesFailedSyncStatus() = runTest(dispatcher) {
        val userFlow = MutableStateFlow<User?>(sampleUser)
        val actions = FakeProfileActions(userFlow, simulateOffline = true)
        val viewModel = ProfileViewModel(
            userFlow = userFlow,
            actions = actions,
            initialUser = sampleUser,
            toastDurationMillis = 0L
        )
        advanceUntilIdle()

        viewModel.startEdit()
        viewModel.onDraftPhoneChange("+45 99 88 77 66")
        viewModel.saveEdit()
        advanceUntilIdle()

        val state = viewModel.state.value as ProfileUiState.Success
        assertEquals("+45 99 88 77 66", state.user.phoneNumber)
        assertEquals(SyncStatus.FAILED, state.user.syncStatus)
    }
}

private class FakeProfileActions(
    private val userFlow: MutableStateFlow<User?>,
    private val simulateOffline: Boolean = false
) : ProfileActions {
    override suspend fun updateProfile(
        user: User,
        fullName: String,
        email: String,
        phoneNumber: String
    ): Result<User> {
        val updated = user.copy(
            displayName = fullName,
            email = email,
            phoneNumber = phoneNumber,
            syncStatus = if (simulateOffline) SyncStatus.FAILED else SyncStatus.SYNCED
        )
        userFlow.value = updated
        return Result.success(updated)
    }

    override suspend fun updatePreferences(
        user: User,
        dietaryRules: DietaryRules,
        notificationSettings: NotificationSettings
    ): Result<User> {
        val updated = user.copy(
            dietaryRules = dietaryRules,
            notificationSettings = notificationSettings,
            syncStatus = if (simulateOffline) SyncStatus.FAILED else SyncStatus.SYNCED
        )
        userFlow.value = updated
        return Result.success(updated)
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = Result.success(Unit)
}
