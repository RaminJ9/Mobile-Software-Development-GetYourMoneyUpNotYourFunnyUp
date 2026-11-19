package com.example.getyourmoneyupnotyourfunnyup.ui.profile

import com.example.getyourmoneyupnotyourfunnyup.data.repository.ProfileRepository
import com.example.getyourmoneyupnotyourfunnyup.model.*
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Incoming profile scenarios adapted to the retained local-first save contract. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val user = User("u", "victor@example.dk", "Victor Munk", updatedAt = 1L, phoneNumber = "+45 12 34 56 78")
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun editAndSaveUpdatesNameAndPhoneWithoutChangingLoginEmail() = runTest(dispatcher) {
        val repo = FakeRepository(user); val vm = ProfileViewModel(user, repo); advanceUntilIdle()
        vm.edit(); vm.setName("Victor M. Munk"); vm.setPhone("+45 87 65 43 21"); vm.saveDetails(); advanceUntilIdle()
        assertNull(vm.state.value.draft)
        assertEquals("Victor M. Munk", vm.state.value.user.displayName)
        assertEquals("+45 87 65 43 21", vm.state.value.user.phoneNumber)
        assertEquals(user.email, vm.state.value.user.email)
        assertTrue(vm.state.value.saved)
    }
    @Test fun cancelEditDiscardsDraftWithoutSaving() = runTest(dispatcher) {
        val repo = FakeRepository(user); val vm = ProfileViewModel(user, repo); advanceUntilIdle()
        vm.edit(); vm.setName("Temporary"); vm.cancel()
        assertNull(vm.state.value.draft)
        assertEquals(user, vm.state.value.user)
        assertEquals(0, repo.saves)
    }
    @Test fun dietaryAndNotificationChoicesPersistIndependently() = runTest(dispatcher) {
        val vm = ProfileViewModel(user, FakeRepository(user)); advanceUntilIdle()
        for (preference in ProfilePreference.entries) {
            vm.toggle(preference, preference == ProfilePreference.Alcohol || preference == ProfilePreference.Meat)
            advanceUntilIdle()
        }
        assertTrue(vm.state.value.user.excludeAlcohol)
        assertTrue(vm.state.value.user.excludeMeat)
        assertEquals(NotificationSettings(false, false, false, false), vm.state.value.user.notificationSettings)
    }
    @Test fun offlineSaveKeepsLocalProfileAndShowsUnsyncedStatus() = runTest(dispatcher) {
        val vm = ProfileViewModel(user, FakeRepository(user, offline = true)); advanceUntilIdle()
        vm.edit(); vm.setPhone("+45 99 88 77 66"); vm.saveDetails(); advanceUntilIdle()
        assertEquals("+45 99 88 77 66", vm.state.value.user.phoneNumber)
        assertEquals(SyncStatus.FAILED, vm.state.value.user.syncStatus)
    }
}

private class FakeRepository(initial: User, private val offline: Boolean = false) : ProfileRepository {
    private val users = MutableStateFlow<User?>(initial)
    var saves = 0
    override fun observeProfile(userId: String) = users
    override suspend fun save(userId: String, changes: ProfileChanges): User {
        saves++
        val current = users.value!!
        return current.copy(displayName = changes.displayName, phoneNumber = changes.phoneNumber,
            excludeAlcohol = changes.excludeAlcohol, excludeMeat = changes.excludeMeat,
            notificationSettings = changes.notificationSettings,
            updatedAt = current.updatedAt + 1, syncStatus = if (offline) SyncStatus.FAILED else SyncStatus.SYNCED
        ).also { users.value = it }
    }
}
