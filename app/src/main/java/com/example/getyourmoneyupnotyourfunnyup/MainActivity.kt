package com.example.getyourmoneyupnotyourfunnyup

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.getyourmoneyupnotyourfunnyup.data.repository.AuthRepository
import com.example.getyourmoneyupnotyourfunnyup.data.repository.FirebaseProfileRepository
import com.example.getyourmoneyupnotyourfunnyup.data.repository.RepositoryAuthSessionSource
import com.example.getyourmoneyupnotyourfunnyup.ui.navigation.AppShell
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.auth.LoginScreen
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.auth.RegistrationScreen
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.AuthForm
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.AuthUiState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The design is light-only, so system bars always use dark icons on a transparent background.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            // Screens follow the light-only hi-fi; switch this on once dark variants are designed.
            GetYourMoneyUpNotYourFunnyUpTheme(darkTheme = false) {
                AppRoot()
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
    // Log in and registration keep clear of the system bars here; the signed-in shell handles them per
    // screen, so its screens can run edge to edge (the camera, for example).
    val authModifier = modifier.background(AppColors.White).systemBarsPadding()

    when (val current = state) {
        AuthUiState.Restoring -> Box(authModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is AuthUiState.SignedOut -> {
            val fields = current.fields
            if (fields.form == AuthForm.Login) {
                LoginScreen(
                    modifier = authModifier,
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
                    modifier = authModifier,
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
