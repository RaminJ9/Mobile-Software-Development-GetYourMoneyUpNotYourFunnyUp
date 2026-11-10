package com.example.getyourmoneyupnotyourfunnyup.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppButton
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppIcon
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppIcons
import com.example.getyourmoneyupnotyourfunnyup.ui.components.FillingScrollColumn
import com.example.getyourmoneyupnotyourfunnyup.ui.components.LabeledTextField
import com.example.getyourmoneyupnotyourfunnyup.ui.components.Notice
import com.example.getyourmoneyupnotyourfunnyup.ui.components.NoticeTone
import com.example.getyourmoneyupnotyourfunnyup.ui.components.Spinner
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppTypography
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.jakarta
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.AuthAction
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.AuthMode
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.AuthUiState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.AuthViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.appViewModel

const val APP_NAME = "Get Your Money Up Not Your Funny Up"

/** S1 Login and S2 Registration (same layout, different fields). */
@Composable
fun AuthRoute(
    mode: AuthMode,
    onAuthenticated: (user: User, phone: String?) -> Unit,
    onSwitchMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = appViewModel { AuthViewModel(it.authRepository, mode) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.authenticatedUser) {
        state.authenticatedUser?.let { user ->
            onAuthenticated(user, state.registeredPhone)
            viewModel.onAuthenticatedHandled()
        }
    }
    AuthScreen(state = state, onAction = viewModel::onAction, onSwitchMode = onSwitchMode, modifier = modifier)
}

@Composable
fun AuthScreen(
    state: AuthUiState,
    onAction: (AuthAction) -> Unit,
    onSwitchMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLogin = state.mode == AuthMode.Login
    FillingScrollColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 28.dp),
    ) {
        Column(
            modifier = Modifier.padding(top = 56.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BrandMark()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (isLogin) "Welcome back" else "Split bills,\nstay friends",
                    style = AppTypography.Display,
                    color = AppColors.Ink900,
                )
                Text(
                    if (isLogin) "Log in to see your groups and who owes what."
                    else "Create a profile so friends can add you to their groups.",
                    style = jakarta(15, FontWeight.Medium, lineHeight = 22.5.sp),
                    color = AppColors.Ink600,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (!isLogin) {
                LabeledTextField(
                    label = "Full name",
                    value = state.fullName,
                    onValueChange = { onAction(AuthAction.NameChanged(it)) },
                )
            }
            LabeledTextField(
                label = "Email",
                value = state.email,
                onValueChange = { onAction(AuthAction.EmailChanged(it)) },
                keyboardType = KeyboardType.Email,
            )
            if (!isLogin) {
                LabeledTextField(
                    label = "Phone number",
                    value = state.phone,
                    onValueChange = { onAction(AuthAction.PhoneChanged(it)) },
                    keyboardType = KeyboardType.Phone,
                    supportingText = "Friends can find you by email or phone.",
                )
            }
            LabeledTextField(
                label = "Password",
                value = state.password,
                onValueChange = { onAction(AuthAction.PasswordChanged(it)) },
                keyboardType = KeyboardType.Password,
                visualTransformation = if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(
                        onClick = { onAction(AuthAction.TogglePasswordVisibility) },
                        modifier = Modifier.semantics {
                            contentDescription = if (state.passwordVisible) "Hide password" else "Show password"
                        },
                    ) {
                        AppIcon(AppIcons.Eye, size = 20.dp, tint = AppColors.Ink600)
                    }
                },
            )
            if (isLogin) {
                TextButton(
                    onClick = { onAction(AuthAction.ForgotPassword) },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Forgot password?", style = jakarta(14, FontWeight.Bold), color = AppColors.Green700)
                }
            }
            state.infoMessage?.let {
                Notice(tone = NoticeTone.Success, icon = AppIcons.Check) {
                    Text(it, style = jakarta(14, FontWeight.SemiBold), color = AppColors.Green700)
                }
            }
            state.errorMessage?.let {
                Notice(tone = NoticeTone.Warning, icon = AppIcons.AlertCircle) {
                    Text(it, style = jakarta(14, FontWeight.Medium, lineHeight = 20.sp), color = AppColors.Amber800)
                }
            }
        }

        Spacer(Modifier.weight(1f).heightIn(min = 24.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(
                onClick = { onAction(AuthAction.Submit) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canSubmit,
            ) {
                if (state.isLoading) {
                    Spinner(size = 20.dp, color = AppColors.White, trackColor = AppColors.Green500)
                } else {
                    Text(if (isLogin) "Log in" else "Create account", style = AppTypography.ButtonPrimary)
                }
            }
            TextButton(onClick = onSwitchMode, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(
                    buildAnnotatedString {
                        append(if (isLogin) "New here? " else "Already have an account? ")
                        withStyle(SpanStyle(color = AppColors.Green700, fontWeight = FontWeight.ExtraBold)) {
                            append(if (isLogin) "Create an account" else "Log in")
                        }
                    },
                    style = jakarta(15, FontWeight.SemiBold),
                    color = AppColors.Ink900,
                )
            }
        }
    }
}

/** Two overlapping coins on a soft green tile, next to the app name. */
@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(AppColors.Green100, RoundedCornerShape(14.dp)),
        ) {
            Box(Modifier.offset(9.dp, 9.dp).size(18.dp).background(AppColors.Green300, CircleShape))
            Box(Modifier.offset(17.dp, 17.dp).size(18.dp).background(AppColors.Green700, CircleShape))
        }
        Text(
            APP_NAME,
            modifier = Modifier.padding(start = 10.dp),
            style = jakarta(18, FontWeight.ExtraBold, -0.02f, lineHeight = 22.sp),
            color = AppColors.Ink900,
        )
    }
}

@Preview(name = "Log in", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun LoginPreview() {
    GetYourMoneyUpNotYourFunnyUpTheme {
        AuthScreen(
            state = AuthUiState(mode = AuthMode.Login, email = "victor@example.dk", password = "sommerferie26"),
            onAction = {},
            onSwitchMode = {},
        )
    }
}

@Preview(name = "Create account", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun RegisterPreview() {
    GetYourMoneyUpNotYourFunnyUpTheme {
        AuthScreen(
            state = AuthUiState(
                mode = AuthMode.Register,
                fullName = "Victor Munk",
                email = "victor@example.dk",
                phone = "+45 12 34 56 78",
                password = "sommerferie26",
            ),
            onAction = {},
            onSwitchMode = {},
        )
    }
}
