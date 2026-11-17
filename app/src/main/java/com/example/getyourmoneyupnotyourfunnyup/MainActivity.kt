package com.example.getyourmoneyupnotyourfunnyup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.repository.AuthRepository
import com.example.getyourmoneyupnotyourfunnyup.data.repository.DashboardDataSource
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.auth.LoginScreen
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.auth.RegistrationScreen
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.shell.AuthenticatedShell
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.AuthGateway
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.AuthMode
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.AuthViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.SessionState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GetYourMoneyUpNotYourFunnyUpTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = AppColors.White
                ) { innerPadding ->
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
    val database = remember { AppDatabase.getInstance(context) }
    val authRepository = remember {
        AuthRepository(
            userDao = database.userDao,
            syncQueueDao = database.syncQueueDao
        )
    }
    val dashboardDataSource = remember { DashboardDataSource(database) }
    val authViewModel = remember {
        AuthViewModel(AuthGateway.fromAuthRepository(authRepository))
    }

    val authState by authViewModel.state.collectAsStateWithLifecycle()

    when (val session = authState.session) {
        SessionState.Unauthenticated -> {
            when (authState.mode) {
                AuthMode.Login -> {
                    LoginScreen(
                        modifier = modifier,
                        email = authState.email,
                        password = authState.password,
                        isLoading = authState.isLoading,
                        errorMessage = authState.errorMessage,
                        infoMessage = authState.infoMessage,
                        onEmailChange = authViewModel::onEmailChange,
                        onPasswordChange = authViewModel::onPasswordChange,
                        onLoginClick = authViewModel::login,
                        onNavigateToRegister = authViewModel::switchToRegister,
                        onForgotPasswordClick = authViewModel::forgotPassword
                    )
                }

                AuthMode.Register -> {
                    RegistrationScreen(
                        modifier = modifier,
                        displayName = authState.displayName,
                        email = authState.email,
                        phoneNumber = authState.phoneNumber,
                        password = authState.password,
                        isLoading = authState.isLoading,
                        errorMessage = authState.errorMessage,
                        onDisplayNameChange = authViewModel::onDisplayNameChange,
                        onEmailChange = authViewModel::onEmailChange,
                        onPhoneNumberChange = authViewModel::onPhoneNumberChange,
                        onPasswordChange = authViewModel::onPasswordChange,
                        onRegisterClick = authViewModel::register,
                        onNavigateToLogin = authViewModel::switchToLogin
                    )
                }
            }
        }

        is SessionState.Authenticated -> {
            AuthenticatedShell(
                modifier = modifier,
                initialUser = session.user,
                selectedTab = session.selectedTab,
                onSelectTab = authViewModel::selectTab,
                authRepository = authRepository,
                dashboardDataSource = dashboardDataSource,
                onUserUpdated = authViewModel::onUserUpdated,
                onLogout = authViewModel::logout
            )
        }
    }
}
