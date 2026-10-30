package com.example.getyourmoneyupnotyourfunnyup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.repository.AuthRepository
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.auth.LoginScreen
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.auth.RegistrationScreen
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.counter.CounterScreen
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme

private sealed interface AppDestination {
    data object Login : AppDestination
    data object Register : AppDestination
    data class Counter(val initialUser: User) : AppDestination
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GetYourMoneyUpNotYourFunnyUpTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppRoot(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun AppRoot(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val authRepository = remember {
        val database = AppDatabase.getInstance(context)
        AuthRepository(
            userDao = database.userDao,
            syncQueueDao = database.syncQueueDao
        )
    }

    var destination by remember { mutableStateOf<AppDestination>(AppDestination.Login) }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf("") }

    // If a session is already active on launch, fetch the user document from Firestore
    LaunchedEffect(Unit) {
        val existingUid = authRepository.currentAuthUid
        val existingEmail = authRepository.currentAuthEmail ?: ""
        if (existingUid != null) {
            isLoading = true
            authRepository.fetchOrCreateUserDocument(
                uid = existingUid,
                fallbackEmail = existingEmail,
                fallbackName = existingEmail.substringBefore("@").ifEmpty { "User" }
            ) { result ->
                isLoading = false
                result.onSuccess { user ->
                    statusMessage = "Loaded user from /users/${user.id}"
                    destination = AppDestination.Counter(user)
                }
            }
        }
    }

    when (val current = destination) {
        is AppDestination.Login -> {
            LoginScreen(
                modifier = modifier,
                email = email,
                password = password,
                isLoading = isLoading,
                errorMessage = errorMessage,
                onEmailChange = {
                    email = it
                    errorMessage = null
                },
                onPasswordChange = {
                    password = it
                    errorMessage = null
                },
                onLoginClick = {
                    isLoading = true
                    errorMessage = null
                    authRepository.loginUser(
                        email = email,
                        password = password
                    ) { result ->
                        isLoading = false
                        result
                            .onSuccess { user ->
                                statusMessage = "Fetched user from /users/${user.id}"
                                destination = AppDestination.Counter(user)
                            }
                            .onFailure { err ->
                                errorMessage = err.localizedMessage ?: "Login failed"
                            }
                    }
                },
                onNavigateToRegister = {
                    errorMessage = null
                    destination = AppDestination.Register
                }
            )
        }

        is AppDestination.Register -> {
            RegistrationScreen(
                modifier = modifier,
                displayName = displayName,
                email = email,
                password = password,
                isLoading = isLoading,
                errorMessage = errorMessage,
                onDisplayNameChange = {
                    displayName = it
                    errorMessage = null
                },
                onEmailChange = {
                    email = it
                    errorMessage = null
                },
                onPasswordChange = {
                    password = it
                    errorMessage = null
                },
                onRegisterClick = {
                    isLoading = true
                    errorMessage = null
                    authRepository.registerUser(
                        displayName = displayName,
                        email = email,
                        password = password
                    ) { result ->
                        isLoading = false
                        result
                            .onSuccess { user ->
                                statusMessage = "Created user in /users/${user.id}"
                                destination = AppDestination.Counter(user)
                            }
                            .onFailure { err ->
                                errorMessage = err.localizedMessage ?: "Registration failed"
                            }
                    }
                },
                onNavigateToLogin = {
                    errorMessage = null
                    destination = AppDestination.Login
                }
            )
        }

        is AppDestination.Counter -> {
            val observedUser by authRepository
                .observeUser(current.initialUser.id)
                .collectAsState(initial = current.initialUser)
            val activeUser = observedUser ?: current.initialUser

            // Attach real-time Firestore listener to `/users/{uid}`
            DisposableEffect(activeUser.id) {
                val registration = authRepository.listenToUserDocument(
                    uid = activeUser.id,
                    fallbackEmail = activeUser.email,
                    fallbackName = activeUser.displayName,
                    onError = { errMsg ->
                        statusMessage = "Firestore error: $errMsg"
                    }
                )
                onDispose {
                    registration.remove()
                }
            }

            CounterScreen(
                modifier = modifier,
                user = activeUser,
                statusMessage = statusMessage,
                onIncrementClick = {
                    statusMessage = "Saving count to /users/${activeUser.id}..."
                    authRepository.incrementUserCount(activeUser) { result ->
                        result
                            .onSuccess { savedCount ->
                                statusMessage = "Saved count ($savedCount) to /users/${activeUser.id}"
                            }
                            .onFailure { err ->
                                statusMessage = "Offline / Failed: ${err.localizedMessage}"
                            }
                    }
                },
                onLogoutClick = {
                    authRepository.signOut()
                    password = ""
                    errorMessage = null
                    statusMessage = ""
                    destination = AppDestination.Login
                }
            )
        }
    }
}
