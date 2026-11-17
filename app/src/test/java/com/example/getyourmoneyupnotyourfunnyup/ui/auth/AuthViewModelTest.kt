package com.example.getyourmoneyupnotyourfunnyup.ui.auth

import com.example.getyourmoneyupnotyourfunnyup.data.repository.AuthSessionSource
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Incoming auth scenarios adapted to the single retained session/source contract. */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun loginSuccessCreatesAuthenticatedSession() = runTest(dispatcher) {
        val source = FakeSource(); val vm = AuthViewModel(source)
        vm.setEmail("victor@example.dk"); vm.setPassword("password"); vm.submit(); advanceUntilIdle()
        assertEquals("victor@example.dk", (vm.state.value as AuthUiState.SignedIn).user.email)
    }
    @Test fun registrationPreservesPhoneNumber() = runTest(dispatcher) {
        val vm = AuthViewModel(FakeSource())
        vm.showRegister(); vm.setDisplayName("Victor Munk"); vm.setEmail("victor@example.dk")
        vm.setPhoneNumber("+45 12 34 56 78"); vm.setPassword("password"); vm.submit(); advanceUntilIdle()
        val user = (vm.state.value as AuthUiState.SignedIn).user
        assertEquals("Victor Munk", user.displayName)
        assertEquals("+45 12 34 56 78", user.phoneNumber)
    }
    @Test fun existingSessionRestoresUser() = runTest(dispatcher) {
        val vm = AuthViewModel(FakeSource(currentUid = "existing")); advanceUntilIdle()
        assertEquals("existing", (vm.state.value as AuthUiState.SignedIn).user.id)
    }
    @Test fun logoutReturnsToLoginAndClearsCredentials() = runTest(dispatcher) {
        val source = FakeSource(); val vm = AuthViewModel(source)
        vm.setEmail("victor@example.dk"); vm.setPassword("password"); vm.submit(); advanceUntilIdle()
        vm.logout()
        assertTrue(source.signedOut)
        assertEquals(AuthUiState.SignedOut(), vm.state.value)
    }
    @Test fun registrationWithUnsyncedProfileStillEntersAuthenticatedSession() = runTest(dispatcher) {
        val vm = AuthViewModel(FakeSource(offline = true))
        vm.showRegister(); vm.setDisplayName("Victor"); vm.setEmail("victor@example.dk"); vm.setPassword("password")
        vm.submit(); advanceUntilIdle()
        assertEquals(SyncStatus.FAILED, (vm.state.value as AuthUiState.SignedIn).user.syncStatus)
    }
    @Test fun forgotPasswordValidatesEmailAndReportsAcceptedRequest() = runTest(dispatcher) {
        val source = FakeSource(); val vm = AuthViewModel(source)
        vm.forgotPassword()
        assertTrue((vm.state.value as AuthUiState.SignedOut).errorMessage!!.contains("email"))
        vm.setEmail("victor@example.dk"); vm.forgotPassword(); advanceUntilIdle()
        assertEquals("victor@example.dk", source.resetEmail)
        assertNotNull((vm.state.value as AuthUiState.SignedOut).infoMessage)
        assertNull((vm.state.value as AuthUiState.SignedOut).errorMessage)
    }
}

private class FakeSource(override val currentUid: String? = null, private val offline: Boolean = false) : AuthSessionSource {
    override val currentEmail = "victor@example.dk"
    private val users = MutableStateFlow<User?>(null)
    var signedOut = false
    var resetEmail: String? = null
    private fun complete(user: User, callback: (Result<User>) -> Unit) { users.value = user; callback(Result.success(user)) }
    override fun restore(uid: String, email: String, onResult: (Result<User>) -> Unit) = complete(User(uid, email, "Victor"), onResult)
    override fun login(email: String, password: String, onResult: (Result<User>) -> Unit) = complete(User("login", email, "Victor"), onResult)
    override fun register(name: String, email: String, password: String, phoneNumber: String?, onResult: (Result<User>) -> Unit) = complete(
        User("register", email, name, phoneNumber = phoneNumber, syncStatus = if (offline) SyncStatus.FAILED else SyncStatus.SYNCED), onResult
    )
    override fun observeUser(user: User, onError: (String) -> Unit) = kotlinx.coroutines.flow.flow {
        users.collect { if (it != null) emit(it) }
    }
    override fun resetPassword(email: String, onResult: (Result<Unit>) -> Unit) { resetEmail = email; onResult(Result.success(Unit)) }
    override fun signOut() { signedOut = true }
}
