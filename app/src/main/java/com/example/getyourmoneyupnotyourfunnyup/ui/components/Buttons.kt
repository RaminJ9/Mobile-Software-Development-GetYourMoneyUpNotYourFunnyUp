package com.example.getyourmoneyupnotyourfunnyup.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppExtraColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.jakarta

/** Button colour roles from the style guide. */
@Immutable
enum class ButtonTone(val container: Color, val content: Color, val border: Color? = null, val borderWidth: Dp = 1.dp) {
    /** Primary · Save expense */
    Primary(AppColors.Green700, AppColors.White),

    /** Pay · only for money owed */
    Pay(AppColors.Red700, AppColors.White),

    /** Soft · Add expense */
    Soft(AppColors.Green100, AppColors.Green700),

    /** Soft red, e.g. "Pay 94,50 kr" inside the payments-due list. */
    PaySoft(AppColors.Red100, AppColors.Red700),

    /** Secondary · Cancel */
    Secondary(AppColors.White, AppColors.Ink900, AppColors.Line),

    /** White button on a tinted card (no border). */
    OnTint(AppColors.White, AppColors.Ink900),

    /** Text · Scan another receipt */
    Text(Color.Transparent, AppColors.Green700),

    /** Neutral destructive confirm ("Remove"). */
    Dark(AppColors.Ink900, AppColors.White),

    /** Amber outline used inside warnings ("Remove anyway", "Send invite"). */
    WarningOutline(Color.Transparent, AppColors.Amber800, AppColors.Amber800, 1.5.dp),

    /** Green outline, e.g. "Mark as paid". */
    GreenOutline(AppColors.White, AppColors.Green700, AppColors.Green200),

    /** Red outline on white, e.g. "Try now" in Activity. */
    RedOutline(AppColors.White, AppColors.Red800, AppExtraColors.RedSoftBorder, 1.5.dp),

    /** Muted red text on white ("Remove expense"). */
    DangerSecondary(AppColors.White, AppColors.Red800, AppColors.Line),
}

/** Height / radius / type pairs from the style guide: primary 56, secondary 48, inline 44. */
@Immutable
enum class ButtonSize(val height: Dp, val radius: Dp, val fontSize: Int) {
    Large(56.dp, 18.dp, 16),
    Medium(48.dp, 16.dp, 15),
    Small(44.dp, 14.dp, 14),
    Compact(40.dp, 14.dp, 14),
}

/**
 * Material 3 [Button] styled to the hi-fi design. Pass [content] for custom layouts, otherwise
 * [text] with optional leading/trailing icons is used.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Primary,
    size: ButtonSize = ButtonSize.Large,
    enabled: Boolean = true,
    leadingIcon: AppIconSpec? = null,
    trailingIcon: AppIconSpec? = null,
    fontWeight: FontWeight = FontWeight.Bold,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    AppButton(
        onClick = onClick,
        modifier = modifier,
        tone = tone,
        size = size,
        enabled = enabled,
        contentPadding = contentPadding,
    ) {
        if (leadingIcon != null) AppIcon(leadingIcon, size = 18.dp, strokeWidth = 2.2f)
        Text(
            text = text,
            style = jakarta(size.fontSize, fontWeight),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailingIcon != null) AppIcon(trailingIcon, size = 18.dp, strokeWidth = 2.2f)
    }
}

@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Primary,
    size: ButtonSize = ButtonSize.Large,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = size.height).height(size.height),
        enabled = enabled,
        shape = RoundedCornerShape(size.radius),
        colors = ButtonDefaults.buttonColors(
            containerColor = tone.container,
            contentColor = tone.content,
            disabledContainerColor = AppColors.LineSoft,
            disabledContentColor = AppColors.Ink600,
        ),
        border = tone.border?.let { BorderStroke(tone.borderWidth, it) },
        contentPadding = contentPadding,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** Two equal buttons side by side: "Cancel" / "Confirm" — money is always confirmed in place. */
@Composable
fun ConfirmButtons(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    cancelText: String = "Cancel",
    confirmText: String = "Confirm",
    cancelTone: ButtonTone = ButtonTone.Secondary,
    confirmTone: ButtonTone = ButtonTone.Pay,
    size: ButtonSize = ButtonSize.Small,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppButton(cancelText, onCancel, Modifier.weight(1f), tone = cancelTone, size = size)
        AppButton(confirmText, onConfirm, Modifier.weight(1f), tone = confirmTone, size = size)
    }
}
