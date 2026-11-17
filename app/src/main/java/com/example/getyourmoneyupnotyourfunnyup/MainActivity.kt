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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.repository.FirebaseProfileRepository
import com.example.getyourmoneyupnotyourfunnyup.data.repository.AuthRepository
import com.example.getyourmoneyupnotyourfunnyup.data.repository.RepositoryAuthSessionSource
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.auth.LoginScreen
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.auth.RegistrationScreen
import com.example.getyourmoneyupnotyourfunnyup.ui.navigation.AppShell
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
                    onNavigateToRegister = authViewModel::showRegister
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
                    onNavigateToLogin = authViewModel::showLogin
                )
            }
        }
        is AuthUiState.SignedIn -> {
            val profileRepository = remember(current.user.id, applicationContext) {
                FirebaseProfileRepository(AppDatabase.getInstance(applicationContext))
            }
            AppShell(
                modifier = modifier,
                user = current.user,
                onLogout = authViewModel::logout,
                profileRepository = profileRepository
            )
        }
    }
}
