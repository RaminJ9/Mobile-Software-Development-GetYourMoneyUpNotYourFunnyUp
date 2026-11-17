package com.example.getyourmoneyupnotyourfunnyup.ui.auth

import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppTab
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.AuthGateway
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.AuthMode
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.AuthViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loginSuccess_redirectsUserToHomeTab() = runTest(dispatcher) {
        val gateway = FakeAuthGateway()
        val viewModel = AuthViewModel(gateway)

        viewModel.onEmailChange("victor@example.dk")
        viewModel.onPasswordChange("sommerferie26")
        viewModel.login()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.session is SessionState.Authenticated)
        val authSession = state.session as SessionState.Authenticated
        assertEquals(AppTab.Home, authSession.selectedTab)
        assertEquals("victor@example.dk", authSession.user.email)
        assertEquals("", state.password)
    }

    @Test
    fun registerSuccess_createsProfileWithPhoneAndRedirectsToHomeTab() = runTest(dispatcher) {
        val gateway = FakeAuthGateway()
        val viewModel = AuthViewModel(gateway)

        viewModel.switchToRegister()
        assertEquals(AuthMode.Register, viewModel.state.value.mode)

        viewModel.onDisplayNameChange("Victor Munk")
        viewModel.onEmailChange("victor@example.dk")
        viewModel.onPhoneNumberChange("+45 12 34 56 78")
        viewModel.onPasswordChange("sommerferie26")
        viewModel.register()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.session is SessionState.Authenticated)
        val authSession = state.session as SessionState.Authenticated
        assertEquals(AppTab.Home, authSession.selectedTab)
        assertEquals("Victor Munk", authSession.user.displayName)
        assertEquals("+45 12 34 56 78", authSession.user.phoneNumber)
    }

    @Test
    fun existingSessionOnLaunch_restoresUserAndRedirectsToHomeTab() = runTest(dispatcher) {
        val gateway = FakeAuthGateway(
            currentAuthUid = "uid-existing",
            currentAuthEmail = "victor@example.dk"
        )
        val viewModel = AuthViewModel(gateway)
        advanceUntilIdle()

        val session = viewModel.state.value.session
        assertTrue(session is SessionState.Authenticated)
        assertEquals(AppTab.Home, (session as SessionState.Authenticated).selectedTab)
        assertEquals("uid-existing", session.user.id)
    }

    @Test
    fun selectTabAndLogout_switchesTabsAndReturnsToLoginScreenOnLogout() = runTest(dispatcher) {
        val gateway = FakeAuthGateway()
        val viewModel = AuthViewModel(gateway)

        viewModel.onEmailChange("victor@example.dk")
        viewModel.onPasswordChange("sommerferie26")
        viewModel.login()
        advanceUntilIdle()

        // Navigate across the 5 bottom navigation tabs
        for (tab in AppTab.entries) {
            viewModel.selectTab(tab)
            val session = viewModel.state.value.session as SessionState.Authenticated
            assertEquals(tab, session.selectedTab)
        }

        // Logout terminates session and returns to S1 Login
        viewModel.logout()
        assertTrue(gateway.signedOut)
        assertEquals(SessionState.Unauthenticated, viewModel.state.value.session)
        assertEquals(AuthMode.Login, viewModel.state.value.mode)
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun offlineRegistrationFallback_stillRedirectsUserToHomeWithoutBlocking() = runTest(dispatcher) {
        val gateway = FakeAuthGateway(simulateOfflineSyncFailure = true)
        val viewModel = AuthViewModel(gateway)

        viewModel.switchToRegister()
        viewModel.onDisplayNameChange("Victor Munk")
        viewModel.onEmailChange("victor@example.dk")
        viewModel.onPhoneNumberChange("+45 12 34 56 78")
        viewModel.onPasswordChange("sommerferie26")
        viewModel.register()
        advanceUntilIdle()

        val session = viewModel.state.value.session
        assertTrue(session is SessionState.Authenticated)
        val authSession = session as SessionState.Authenticated
        assertEquals(AppTab.Home, authSession.selectedTab)
        assertEquals(SyncStatus.FAILED, authSession.user.syncStatus)
    }

    @Test
    fun forgotPassword_sendsResetLinkOrShowsValidationMessage() = runTest(dispatcher) {
        val gateway = FakeAuthGateway()
        val viewModel = AuthViewModel(gateway)

        // Blank email shows prompt
        viewModel.forgotPassword()
        assertTrue(viewModel.state.value.errorMessage?.contains("email") == true)

        // Valid email dispatches reset email
        viewModel.onEmailChange("victor@example.dk")
        viewModel.forgotPassword()
        advanceUntilIdle()

        assertNull(viewModel.state.value.errorMessage)
        assertEquals("Password reset link sent to victor@example.dk", viewModel.state.value.infoMessage)
    }
}

private class FakeAuthGateway(
    override val currentAuthUid: String? = null,
    override val currentAuthEmail: String? = null,
    private val simulateOfflineSyncFailure: Boolean = false
) : AuthGateway {
    var signedOut: Boolean = false

    override suspend fun fetchOrCreateUser(
        uid: String,
        fallbackEmail: String,
        fallbackName: String
    ): Result<User> = Result.success(
        User(
            id = uid,
            email = fallbackEmail,
            displayName = fallbackName
        )
    )

    override suspend fun login(email: String, password: String): Result<User> =
        Result.success(
            User(
                id = "uid-login",
                email = email,
                displayName = "Victor Munk",
                phoneNumber = "+45 12 34 56 78"
            )
        )

    override suspend fun register(
        displayName: String,
        email: String,
        phoneNumber: String,
        password: String
    ): Result<User> = Result.success(
        User(
            id = "uid-register",
            email = email,
            displayName = displayName,
            phoneNumber = phoneNumber,
            syncStatus = if (simulateOfflineSyncFailure) SyncStatus.FAILED else SyncStatus.SYNCED
        )
    )

    override suspend fun sendPasswordReset(email: String): Result<Unit> = Result.success(Unit)

    override fun signOut() {
        signedOut = true
    }
}
