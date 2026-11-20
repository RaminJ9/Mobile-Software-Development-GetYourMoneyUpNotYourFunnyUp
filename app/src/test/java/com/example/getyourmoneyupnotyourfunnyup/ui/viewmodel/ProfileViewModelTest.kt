package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import com.example.getyourmoneyupnotyourfunnyup.data.repository.ProfileRepository
import com.example.getyourmoneyupnotyourfunnyup.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val user = User("u", "u@test", "Ramin", updatedAt = 1L)
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeRepository(initial: User) : ProfileRepository {
        val users = MutableStateFlow<User?>(initial)
        var saves = 0
        val emailRequests = mutableListOf<String>()
        override suspend fun requestEmailChange(userId: String, email: String) {
            error?.let { throw IllegalStateException(it) }
            emailRequests += email
        }
        var error: String? = null
        var gate: CompletableDeferred<Unit>? = null
        override fun observeProfile(userId: String) = users
        override suspend fun save(userId: String, changes: ProfileChanges): User {
            saves++
            gate?.await()
            error?.let { throw IllegalStateException(it) }
            val current = users.value!!
            return current.copy(
                displayName = changes.displayName, phoneNumber = changes.phoneNumber,
                excludeAlcohol = changes.excludeAlcohol, excludeMeat = changes.excludeMeat,
                notificationSettings = changes.notificationSettings,
                syncStatus = SyncStatus.PENDING_UPDATE, updatedAt = current.updatedAt + 1
            ).also { users.value = it }
        }
    }

    @Test fun cancelDiscardsDraftWithoutSaving() = runTest(dispatcher) {
        val repo = FakeRepository(user); val vm = ProfileViewModel(user, repo)
        vm.edit(); vm.setName("Changed"); vm.setPhone("12345678"); vm.cancel()
        assertNull(vm.state.value.draft)
        assertEquals(user, vm.state.value.user)
        assertEquals(0, repo.saves)
    }

    @Test fun saveTrimsDetailsAndShowsLocalPendingUntilAcknowledged() = runTest(dispatcher) {
        val repo = FakeRepository(user); val vm = ProfileViewModel(user, repo)
        vm.edit(); vm.setName(" Ramin Javed "); vm.setPhone(" +45 12345678 "); vm.saveDetails()
        assertNull(vm.state.value.draft)
        assertTrue(vm.state.value.saved)
        assertEquals("Ramin Javed", vm.state.value.user.displayName)
        assertEquals("+45 12345678", vm.state.value.user.phoneNumber)
        assertEquals(user.email, vm.state.value.user.email)
        assertEquals(SyncStatus.PENDING_UPDATE, vm.state.value.user.syncStatus)
        repo.users.value = vm.state.value.user.copy(syncStatus = SyncStatus.SYNCED)
        assertEquals(SyncStatus.SYNCED, vm.state.value.user.syncStatus)
    }

    @Test fun invalidNameOrPhoneDoesNotWrite() = runTest(dispatcher) {
        val repo = FakeRepository(user); val vm = ProfileViewModel(user, repo)
        vm.edit(); vm.setName(" "); vm.saveDetails()
        assertEquals("Enter your full name.", vm.state.value.errorMessage)
        vm.setName("Ramin"); vm.setPhone("letters"); vm.saveDetails()
        assertNotNull(vm.state.value.errorMessage)
        assertEquals(0, repo.saves)
    }

    @Test fun localFailureKeepsDraftForRetryAndPreventsDuplicateTap() = runTest(dispatcher) {
        val repo = FakeRepository(user).apply { gate = CompletableDeferred(); error = "Storage unavailable" }
        val vm = ProfileViewModel(user, repo)
        vm.edit(); vm.setName("Changed"); vm.saveDetails(); vm.saveDetails()
        assertTrue(vm.state.value.isSaving)
        assertEquals(1, repo.saves)
        repo.gate!!.complete(Unit)
        assertFalse(vm.state.value.isSaving)
        assertEquals("Changed", vm.state.value.draft!!.name)
        assertEquals("Storage unavailable", vm.state.value.errorMessage)
        repo.error = null; vm.saveDetails()
        assertNull(vm.state.value.draft)
    }

    @Test fun allSixSwitchesSaveIndependently() = runTest(dispatcher) {
        val repo = FakeRepository(user); val vm = ProfileViewModel(user, repo)
        vm.toggle(ProfilePreference.Alcohol, true)
        vm.toggle(ProfilePreference.Meat, true)
        for (preference in listOf(ProfilePreference.NewExpenses, ProfilePreference.ChangedExpenses, ProfilePreference.PaymentReminders, ProfilePreference.SyncUpdates)) {
            vm.toggle(preference, false)
        }
        assertEquals(6, repo.saves)
        assertTrue(vm.state.value.user.excludeAlcohol)
        assertTrue(vm.state.value.user.excludeMeat)
        assertEquals(NotificationSettings(false, false, false, false), vm.state.value.user.notificationSettings)
    }

    @Test fun refreshPreservesDraftAndIgnoresOlderOrOtherAccountData() = runTest(dispatcher) {
        val repo = FakeRepository(user); val vm = ProfileViewModel(user, repo)
        vm.edit(); vm.setName("Unsaved")
        repo.users.value = user.copy(displayName = "Remote", updatedAt = 2L)
        assertEquals("Unsaved", vm.state.value.draft!!.name)
        repo.users.value = user.copy(displayName = "Old", updatedAt = 1L)
        assertEquals("Remote", vm.state.value.user.displayName)
        repo.users.value = user.copy(id = "another", displayName = "Other", updatedAt = 3L)
        assertEquals("u", vm.state.value.user.id)
    }

    @Test fun missingServiceCannotPretendToSave() = runTest(dispatcher) {
        val vm = ProfileViewModel(user, null)
        vm.edit(); vm.toggle(ProfilePreference.Alcohol, true)
        assertFalse(vm.state.value.canSave)
        assertNull(vm.state.value.draft)
        assertFalse(vm.state.value.saved)
        assertEquals(user, vm.state.value.user)
    }
    @Test fun emailChangeRequestsVerificationWithoutPretendingEmailAlreadyChanged() = runTest(dispatcher) {
        val repo = FakeRepository(user); val vm = ProfileViewModel(user, repo)
        vm.edit(); vm.setEmail(" new@test.dk "); vm.saveDetails()
        assertEquals(listOf("new@test.dk"), repo.emailRequests)
        assertEquals(user.email, vm.state.value.user.email)
        assertTrue(vm.state.value.infoMessage!!.contains("Verification link sent"))
        assertNull(vm.state.value.draft)
    }
    @Test fun invalidEmailOrVerificationFailureKeepsDraftAndDoesNotSave() = runTest(dispatcher) {
        val repo = FakeRepository(user); val vm = ProfileViewModel(user, repo)
        vm.edit(); vm.setEmail("bad address"); vm.saveDetails()
        assertEquals(0, repo.saves); assertTrue(repo.emailRequests.isEmpty())
        vm.setEmail("new@test.dk"); repo.error = "Log in again"; vm.saveDetails()
        assertEquals(0, repo.saves); assertNotNull(vm.state.value.draft)
        assertEquals("Log in again", vm.state.value.errorMessage)
    }

}
