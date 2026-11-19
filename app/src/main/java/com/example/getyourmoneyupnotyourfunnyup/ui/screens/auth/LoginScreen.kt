package com.example.getyourmoneyupnotyourfunnyup.ui.screens.auth

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppSpacing
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppTouchTargets
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppTypography
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme

/**
 * S1 · Log in (`docs/HiFi.html`):
 * - Brand logo + "Welcome back" + "Log in to see your groups and who owes what."
 * - Email & Password inputs (`52dp` height, `16dp` radius) with Show/Hide password toggle
 * - "Forgot password?" inline link
 * - Primary "Log in" button (`56dp`) and "New here? Create an account" mode switch
 */
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    email: String,
    password: String,
    isLoading: Boolean,
    errorMessage: String?,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onNavigateToRegister: () -> Unit,
    infoMessage: String? = null,
    onForgotPasswordClick: () -> Unit = {}
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
            title = "Welcome back",
            subtitle = "Log in to see your groups and who owes what."
        )

        if (!errorMessage.isNullOrBlank()) {
            AuthBanner(
                message = errorMessage,
                isError = true,
                modifier = Modifier.padding(bottom = 14.dp)
            )
        } else if (!infoMessage.isNullOrBlank()) {
            AuthBanner(
                message = infoMessage,
                isError = false,
                modifier = Modifier.padding(bottom = 14.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                label = "Password",
                value = password,
                onValueChange = onPasswordChange,
                placeholder = "••••••••",
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
                        if (email.isNotBlank() && password.isNotBlank() && !isLoading) {
                            onLoginClick()
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

        TextButton(
            onClick = onForgotPasswordClick,
            enabled = !isLoading,
            modifier = Modifier
                .align(Alignment.End)
                .heightIn(min = AppTouchTargets.MinTouchTarget)
        ) {
            Text(
                text = "Forgot password?",
                style = AppTypography.ButtonInline,
                color = AppColors.Green700
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(AppSpacing.Space16))

        AuthBottomActions(
            primaryLabel = "Log in",
            isLoading = isLoading,
            primaryEnabled = email.isNotBlank() && password.isNotBlank(),
            onPrimaryClick = onLoginClick,
            switchLeadText = "New here?",
            switchActionText = "Create an account",
            onSwitchModeClick = onNavigateToRegister
        )
    }
}

@Preview(showBackground = true, name = "Login Light")
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "Login Dark"
)
@Composable
private fun LoginScreenPreview() {
    GetYourMoneyUpNotYourFunnyUpTheme {
        Surface(color = AppColors.White) {
            LoginScreen(
                email = "victor@example.dk",
                password = "sommerferie26",
                isLoading = false,
                errorMessage = null,
                onEmailChange = {},
                onPasswordChange = {},
                onLoginClick = {},
                onNavigateToRegister = {}
            )
        }
    }
}
