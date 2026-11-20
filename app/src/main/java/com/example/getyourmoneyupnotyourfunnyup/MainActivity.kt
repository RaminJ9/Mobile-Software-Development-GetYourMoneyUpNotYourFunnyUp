package com.example.getyourmoneyupnotyourfunnyup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.receipt.WorkManagerReceiptUploads
import com.example.getyourmoneyupnotyourfunnyup.data.receipt.scheduleMissing
import com.example.getyourmoneyupnotyourfunnyup.data.repository.AuthRepository
import com.example.getyourmoneyupnotyourfunnyup.data.repository.FirebaseProfileRepository
import com.example.getyourmoneyupnotyourfunnyup.data.repository.RepositoryAuthSessionSource
import com.example.getyourmoneyupnotyourfunnyup.ui.navigation.AppShell
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.auth.LoginScreen
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.auth.RegistrationScreen
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.AuthForm
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.AuthUiState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GetYourMoneyUpNotYourFunnyUpTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppRoot(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun AppRoot(modifier: Modifier = Modifier) {
    val applicationContext = LocalContext.current.applicationContext
    val factory = remember(applicationContext) {
        viewModelFactory {
            initializer {
                val database = AppDatabase.getInstance(applicationContext)
                AuthViewModel(RepositoryAuthSessionSource(AuthRepository(database.userDao, database.syncQueueDao)))
            }
        }
    }
    val authViewModel: AuthViewModel = viewModel(factory = factory)
    val state by authViewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        AuthUiState.Restoring -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is AuthUiState.SignedOut -> {
            val fields = current.fields
            if (fields.form == AuthForm.Login) {
                LoginScreen(
                    modifier = modifier,
                    email = fields.email,
                    password = fields.password,
                    isLoading = current.isLoading,
                    errorMessage = current.errorMessage,
                    onEmailChange = authViewModel::setEmail,
                    onPasswordChange = authViewModel::setPassword,
                    onLoginClick = authViewModel::submit,
                    onNavigateToRegister = authViewModel::showRegister,
                    infoMessage = current.infoMessage,
                    onForgotPasswordClick = authViewModel::forgotPassword
                )
            } else {
                RegistrationScreen(
                    modifier = modifier,
                    displayName = fields.displayName,
                    email = fields.email,
                    password = fields.password,
                    isLoading = current.isLoading,
                    errorMessage = current.errorMessage,
                    onDisplayNameChange = authViewModel::setDisplayName,
                    onEmailChange = authViewModel::setEmail,
                    onPasswordChange = authViewModel::setPassword,
                    onRegisterClick = authViewModel::submit,
                    onNavigateToLogin = authViewModel::showLogin,
                    phoneNumber = fields.phoneNumber,
                    onPhoneNumberChange = authViewModel::setPhoneNumber
                )
            }
        }
        is AuthUiState.SignedIn -> {
            val database = remember(applicationContext) {
                AppDatabase.getInstance(applicationContext)
            }
            val profileRepository = remember(current.user.id, database) {
                FirebaseProfileRepository(database)
            }
            // Receipt photos go to cloud storage in the background, so other members can see them.
            val receiptUploads = remember(applicationContext) {
                WorkManagerReceiptUploads(WorkManager.getInstance(applicationContext))
            }
            LaunchedEffect(current.user.id, receiptUploads) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { receiptUploads.scheduleMissing(database, current.user.id) }
                }
            }
            var cloudReadError by remember(current.user.id) { mutableStateOf<String?>(null) }
            DisposableEffect(current.user.id, database) {
                val session = com.example.getyourmoneyupnotyourfunnyup.data.sync.RemoteGroupSession(database, current.user.id,
                    { Firebase.auth.currentUser?.uid }, com.google.firebase.firestore.FirebaseFirestore.getInstance(),
                    onError = { cloudReadError = it })
                onDispose { session.close() }
            }
            val syncScheduler = remember { (applicationContext as MoneyApplication).syncScheduler }
            LaunchedEffect(current.user.id, syncScheduler) {
                var known = emptySet<Long>()
                database.syncQueueDao.observePendingQueue().collect { pending ->
                    val ids = pending.map { it.queueId }.toSet()
                    if (ids.any { it !in known } && Firebase.auth.currentUser?.uid == current.user.id) {
                        syncScheduler.scheduleAutomatic(current.user.id)
                    }
                    known = ids
                }
            }
            AppShell(
                modifier = modifier,
                user = current.user,
                onLogout = authViewModel::logout,
                profileRepository = profileRepository,
                database = database,
                cloudReadError = cloudReadError,
                receiptUploads = receiptUploads
            )
        }
    }
}
