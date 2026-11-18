package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import com.example.getyourmoneyupnotyourfunnyup.data.repository.AuthSessionSource
import com.example.getyourmoneyupnotyourfunnyup.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val user = User("u", "user@example.com", "User", updatedAt = 1L)
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeSource(override val currentUid: String? = null) : AuthSessionSource {
        override val currentEmail = "user@example.com"
        var result: ((Result<User>) -> Unit)? = null
        var loginCalls = 0
        var registerCalls = 0
        var signedOut = false
        val updates = MutableStateFlow(User("u", "user@example.com", "User", updatedAt = 1L))
        override fun restore(uid: String, email: String, onResult: (Result<User>) -> Unit) { result = onResult }
        override fun login(email: String, password: String, onResult: (Result<User>) -> Unit) { loginCalls++; result = onResult }
        override fun register(name: String, email: String, password: String, phoneNumber: String?, onResult: (Result<User>) -> Unit) { registerCalls++; result = onResult }
        var listenerError: ((String) -> Unit)? = null
        override fun observeUser(user: User, onError: (String) -> Unit): Flow<User> {
            listenerError = onError
            return updates
        }
        override fun resetPassword(email: String, onResult: (Result<Unit>) -> Unit) { onResult(Result.success(Unit)) }
        override fun signOut() { signedOut = true }
    }
    private fun AuthViewModel.signedOut() = state.value as AuthUiState.SignedOut
    private fun AuthViewModel.credentials() { setEmail(user.email); setPassword("password") }

    @Test fun noSessionStartsAtLogin() = runTest(dispatcher) {
        val vm = AuthViewModel(FakeSource())
        assertEquals(AuthForm.Login, vm.signedOut().fields.form)
    }

    @Test fun loginShowsLoadingRejectsDoubleTapAndClearsCredentialsOnSuccess() = runTest(dispatcher) {
        val source = FakeSource(); val vm = AuthViewModel(source)
        vm.credentials(); vm.submit(); vm.submit()
        assertTrue(vm.signedOut().isLoading)
        assertEquals(1, source.loginCalls)
        source.result!!(Result.success(user))
        assertEquals(user, (vm.state.value as AuthUiState.SignedIn).user)
        vm.logout()
        assertEquals(AuthFields(), vm.signedOut().fields)
        assertTrue(source.signedOut)
    }

    @Test fun failureStopsLoadingAndAllowsRetry() = runTest(dispatcher) {
        val source = FakeSource(); val vm = AuthViewModel(source)
        vm.credentials(); vm.submit()
        source.result!!(Result.failure(IllegalStateException("Incorrect credentials")))
        assertFalse(vm.signedOut().isLoading)
        assertEquals("Incorrect credentials", vm.signedOut().errorMessage)
        assertEquals("", vm.signedOut().fields.password)
        vm.setPassword("retry-password"); vm.submit()
        assertEquals(2, source.loginCalls)
    }

    @Test fun registrationUsesRegistrationOperation() = runTest(dispatcher) {
        val source = FakeSource(); val vm = AuthViewModel(source)
        vm.showRegister(); vm.credentials(); vm.setDisplayName("User"); vm.submit()
        assertEquals(1, source.registerCalls)
        assertEquals(0, source.loginCalls)
        source.result!!(Result.success(user))
        assertTrue(vm.state.value is AuthUiState.SignedIn)
        vm.logout()
    }

    @Test fun existingSessionRestoresAndLocalUpdatesRemainVisible() = runTest(dispatcher) {
        val source = FakeSource("u"); val vm = AuthViewModel(source)
        assertEquals(AuthUiState.Restoring, vm.state.value)
        source.result!!(Result.success(user))
        source.updates.value = user.copy(count = 4)
        assertEquals(4, (vm.state.value as AuthUiState.SignedIn).user.count)
        vm.logout()
    }

    @Test fun restoreFailureIsVisibleInsteadOfSilentlyReturningToLogin() = runTest(dispatcher) {
        val source = FakeSource("u"); val vm = AuthViewModel(source)
        source.result!!(Result.failure(IllegalStateException("Profile unavailable offline")))
        assertEquals("Profile unavailable offline", vm.signedOut().errorMessage)
        assertFalse(vm.signedOut().isLoading)
    }

    @Test fun logoutIgnoresLateAuthenticationAndOldUserUpdates() = runTest(dispatcher) {
        val source = FakeSource("u"); val vm = AuthViewModel(source)
        val lateResult = source.result!!
        vm.logout(); lateResult(Result.success(user))
        source.updates.value = user.copy(count = 99)
        assertEquals(AuthUiState.SignedOut(), vm.state.value)
    }

    @Test fun logoutCancelsSignedInObservation() = runTest(dispatcher) {
        val source = FakeSource("u"); val vm = AuthViewModel(source)
        source.result!!(Result.success(user)); vm.logout()
        source.updates.value = user.copy(count = 99)
        assertEquals(AuthUiState.SignedOut(), vm.state.value)
    }
    @Test fun remoteErrorPreservesSignedInUserAndLocalObservation() = runTest(dispatcher) {
        val source = FakeSource("u"); val vm = AuthViewModel(source)
        source.result!!(Result.success(user))
        source.listenerError!!("Connection unavailable")
        assertTrue((vm.state.value as AuthUiState.SignedIn).statusMessage.contains("Connection unavailable"))
        source.updates.value = user.copy(count = 7)
        assertEquals(7, (vm.state.value as AuthUiState.SignedIn).user.count)
        vm.logout()
        source.listenerError!!("Late error")
        assertEquals(AuthUiState.SignedOut(), vm.state.value)
    }

}
