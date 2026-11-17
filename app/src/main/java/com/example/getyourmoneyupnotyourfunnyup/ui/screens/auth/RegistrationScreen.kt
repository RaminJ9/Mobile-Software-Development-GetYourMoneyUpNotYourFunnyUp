package com.example.getyourmoneyupnotyourfunnyup.ui.screens.auth

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppSpacing
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme

/**
 * S2 · Create account (`docs/HiFi.html`):
 * - Brand logo + "Split bills,\nstay friends" + "Create a profile so friends can add you to their groups."
 * - Full name, Email, Phone number ("Friends can find you by email or phone."), and Password inputs
 * - Primary "Create account" button (`56dp`) and "Already have an account? Log in" mode switch
 */
@Composable
fun RegistrationScreen(
    modifier: Modifier = Modifier,
    displayName: String,
    email: String,
    password: String,
    isLoading: Boolean,
    errorMessage: String?,
    onDisplayNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRegisterClick: () -> Unit,
    onNavigateToLogin: () -> Unit,
    phoneNumber: String = "",
    onPhoneNumberChange: (String) -> Unit = {}
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.White)
            .verticalScroll(rememberScrollState())
            .padding(
                start = AppSpacing.Space24,
                end = AppSpacing.Space24,
                bottom = AppSpacing.ScreenBottomPadding
            )
    ) {
        AuthBrandHeader(
            title = "Split bills,\nstay friends",
            subtitle = "Create a profile so friends can add you to their groups."
        )

        if (!errorMessage.isNullOrBlank()) {
            AuthBanner(
                message = errorMessage,
                isError = true,
                modifier = Modifier.padding(bottom = 14.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HiFiLabeledTextField(
                label = "Full name",
                value = displayName,
                onValueChange = onDisplayNameChange,
                placeholder = "Victor Munk",
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )

            HiFiLabeledTextField(
                label = "Email",
                value = email,
                onValueChange = onEmailChange,
                placeholder = "victor@example.dk",
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )

            HiFiLabeledTextField(
                label = "Phone number",
                value = phoneNumber,
                onValueChange = onPhoneNumberChange,
                placeholder = "+45 12 34 56 78",
                helperText = "Friends can find you by email or phone.",
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next
                )
            )

            HiFiLabeledTextField(
                label = "Password",
                value = password,
                onValueChange = onPasswordChange,
                placeholder = "6+ characters",
                enabled = !isLoading,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (displayName.isNotBlank() && email.isNotBlank() && password.length >= 6 && !isLoading) {
                            onRegisterClick()
                        }
                    }
                ),
                trailingIcon = {
                    PasswordVisibilityToggleButton(
                        passwordVisible = passwordVisible,
                        onToggle = { passwordVisible = !passwordVisible }
                    )
                }
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(AppSpacing.Space16))

        AuthBottomActions(
            primaryLabel = "Create account",
            isLoading = isLoading,
            primaryEnabled = displayName.isNotBlank() && email.isNotBlank() && password.length >= 6,
            onPrimaryClick = onRegisterClick,
            switchLeadText = "Already have an account?",
            switchActionText = "Log in",
            onSwitchModeClick = onNavigateToLogin
        )
    }
}

@Preview(showBackground = true, name = "Registration Light")
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "Registration Dark"
)
@Composable
private fun RegistrationScreenPreview() {
    GetYourMoneyUpNotYourFunnyUpTheme {
        Surface(color = AppColors.White) {
            RegistrationScreen(
                displayName = "Victor Munk",
                email = "victor@example.dk",
                phoneNumber = "+45 12 34 56 78",
                password = "sommerferie26",
                isLoading = false,
                errorMessage = null,
                onDisplayNameChange = {},
                onEmailChange = {},
                onPasswordChange = {},
                onRegisterClick = {},
                onNavigateToLogin = {}
            )
        }
    }
}
