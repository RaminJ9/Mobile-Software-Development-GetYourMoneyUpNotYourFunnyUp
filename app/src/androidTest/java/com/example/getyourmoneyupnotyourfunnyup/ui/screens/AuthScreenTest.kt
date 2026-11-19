package com.example.getyourmoneyupnotyourfunnyup.ui.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.auth.LoginScreen
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.auth.RegistrationScreen
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun registrationPhoneControlUpdatesStateAndSubmitDelegates() {
        val phone = mutableStateOf("")
        var submitted = false
        compose.setContent {
            GetYourMoneyUpNotYourFunnyUpTheme {
                RegistrationScreen(displayName = "Victor", email = "victor@example.dk", password = "password",
                    isLoading = false, errorMessage = null, onDisplayNameChange = {}, onEmailChange = {}, onPasswordChange = {},
                    onRegisterClick = { submitted = true }, onNavigateToLogin = {},
                    phoneNumber = phone.value, onPhoneNumberChange = { phone.value = it })
            }
        }
        compose.onNodeWithContentDescription("Phone number").performScrollTo().performTextInput("+4512345678")
        compose.onNodeWithText("Create account", useUnmergedTree = true).performScrollTo().performClick()
        compose.runOnIdle { assertEquals("+4512345678", phone.value); assertTrue(submitted) }
    }

    @Test fun loginForgotPasswordControlDelegatesWithoutLoggingIn() {
        var reset = false
        var loggedIn = false
        compose.setContent {
            GetYourMoneyUpNotYourFunnyUpTheme {
                LoginScreen(email = "victor@example.dk", password = "", isLoading = false, errorMessage = null,
                    onEmailChange = {}, onPasswordChange = {}, onLoginClick = { loggedIn = true },
                    onNavigateToRegister = {}, onForgotPasswordClick = { reset = true })
            }
        }
        compose.onNodeWithText("Forgot password?").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(reset); assertFalse(loggedIn) }
    }
}
