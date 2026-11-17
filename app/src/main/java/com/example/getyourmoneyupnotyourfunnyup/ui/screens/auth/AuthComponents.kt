package com.example.getyourmoneyupnotyourfunnyup.ui.screens.auth

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppShapes
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppSpacing
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppTouchTargets
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppTypography
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.PlusJakartaSansFontFamily

/**
 * Brand header shared by S1 Login and S2 Registration (`docs/HiFi.html`).
 */
@Composable
fun AuthBrandHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 44.dp, bottom = AppSpacing.Space24),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Space16)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(AppTouchTargets.MinTouchTarget)
                    .background(AppColors.Green100, AppShapes.InlineButton)
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = 9.dp, y = 9.dp)
                        .size(18.dp)
                        .background(AppColors.Green300, AppShapes.Circle)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-9).dp, y = (-9).dp)
                        .size(18.dp)
                        .background(AppColors.Green700, AppShapes.Circle)
                )
            }
            Text(
                text = "Get Your Money Up",
                style = AppTypography.TopBarTitle,
                color = AppColors.Ink900
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
            Text(
                text = title,
                style = AppTypography.Display,
                color = AppColors.Ink900
            )
            Text(
                text = subtitle,
                style = AppTypography.Body,
                color = AppColors.Ink600
            )
        }
    }
}

/**
 * HiFi labeled text field matching `docs/HiFi.html` (`14sp` Bold label above a `52dp` height,
 * `16dp`-radius input box with `1.5dp` `#DCE2DE` border and `2dp` `#3FA36B` focus ring).
 */
@Composable
fun HiFiLabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    helperText: String? = null,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> AppColors.LineSoft
            isFocused -> AppColors.Green500
            else -> AppColors.InputBorder
        },
        label = "inputBorderColor"
    )
    val borderWidth = if (isFocused) 2.dp else 1.5.dp

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = AppTypography.ButtonInline,
            color = AppColors.Ink900
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = AppTypography.Body.copy(
                fontSize = 16.sp,
                color = AppColors.Ink900
            ),
            cursorBrush = SolidColor(AppColors.Green700),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier
                .fillMaxWidth()
                .height(AppTouchTargets.TextFieldHeight)
                .onFocusChanged { isFocused = it.isFocused }
                .semantics { contentDescription = label },
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AppTouchTargets.TextFieldHeight)
                        .clip(AppShapes.TextField)
                        .background(
                            color = if (enabled) AppColors.White else AppColors.SurfaceSoft,
                            shape = AppShapes.TextField
                        )
                        .border(
                            width = borderWidth,
                            color = borderColor,
                            shape = AppShapes.TextField
                        )
                        .padding(
                            start = AppSpacing.Space16,
                            end = if (trailingIcon != null) AppSpacing.Space4 else AppSpacing.Space16
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                text = placeholder,
                                style = AppTypography.Body.copy(fontSize = 16.sp),
                                color = AppColors.Ink400
                            )
                        }
                        innerTextField()
                    }
                    if (trailingIcon != null) {
                        trailingIcon()
                    }
                }
            }
        )
        if (!helperText.isNullOrBlank()) {
            Text(
                text = helperText,
                style = AppTypography.Caption,
                color = AppColors.Ink600
            )
        }
    }
}

/**
 * Password visibility toggle button (`44dp` touch target) with outline eye icon from `docs/HiFi.html`.
 */
@Composable
fun PasswordVisibilityToggleButton(
    passwordVisible: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = if (passwordVisible) "Hide password" else "Show password"
    val eyeVector = remember(passwordVisible) {
        val path = if (passwordVisible) {
            "M17.94 17.94A10.07 10.07 0 0 1 12 19c-6.5 0-10-7-10-7a18.45 18.45 0 0 1 5.06-5.94 M9.9 4.24A9.12 9.12 0 0 1 12 4c6.5 0 10 7 10 7a18.5 18.5 0 0 1-2.16 3.19 M1 1l22 22"
        } else {
            "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z M12 9a3 3 0 1 0 0 6 3 3 0 1 0 0-6z"
        }
        ImageVector.Builder(label, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = addPathNodes(path),
            fill = null,
            stroke = SolidColor(AppColors.Ink600),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ).build()
    }

    IconButton(
        onClick = onToggle,
        modifier = modifier.size(AppTouchTargets.MinTouchTarget)
    ) {
        Icon(
            imageVector = eyeVector,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = AppColors.Ink600
        )
    }
}

/**
 * Primary `56dp` CTA button and `48dp` mode switch button at the bottom of S1/S2 Auth screens.
 */
@Composable
fun AuthBottomActions(
    primaryLabel: String,
    isLoading: Boolean,
    primaryEnabled: Boolean,
    onPrimaryClick: () -> Unit,
    switchLeadText: String,
    switchActionText: String,
    onSwitchModeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)
    ) {
        Button(
            onClick = onPrimaryClick,
            enabled = primaryEnabled && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppTouchTargets.PrimaryButtonHeight),
            shape = AppShapes.BigButton,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.Green700,
                contentColor = AppColors.White,
                disabledContainerColor = AppColors.Green100,
                disabledContentColor = AppColors.Green700
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = AppColors.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = primaryLabel,
                    style = AppTypography.ButtonPrimary
                )
            }
        }

        TextButton(
            onClick = onSwitchModeClick,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppTouchTargets.SecondaryButtonHeight),
            shape = AppShapes.SecondaryButton
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = AppColors.Ink900
                        )
                    ) {
                        append(switchLeadText)
                        append(" ")
                    }
                    withStyle(
                        SpanStyle(
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = AppColors.Green700
                        )
                    ) {
                        append(switchActionText)
                    }
                }
            )
        }
    }
}

/**
 * Semantic alert banner for auth errors (`Red50` / `Red800`) or info confirmations (`Green100` / `Green700`).
 */
@Composable
fun AuthBanner(
    message: String,
    isError: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.SecondaryButton,
        color = if (isError) AppColors.Red50 else AppColors.Green100,
        border = BorderStroke(1.dp, if (isError) AppColors.Red200 else AppColors.Green200)
    ) {
        Text(
            text = message,
            style = AppTypography.BodyStrong,
            color = if (isError) AppColors.Red800 else AppColors.Green700,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}
