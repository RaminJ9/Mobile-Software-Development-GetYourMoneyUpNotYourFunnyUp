package com.example.getyourmoneyupnotyourfunnyup.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import kotlin.math.abs

private val LightColorScheme = lightColorScheme(
    primary = AppColors.Green700,
    onPrimary = AppColors.White,
    primaryContainer = AppColors.Green100,
    onPrimaryContainer = AppColors.Green700,
    secondary = AppColors.Green500,
    onSecondary = AppColors.White,
    secondaryContainer = AppColors.Green50,
    onSecondaryContainer = AppColors.Green600,
    tertiary = AppColors.Amber800,
    onTertiary = AppColors.White,
    tertiaryContainer = AppColors.Amber100,
    onTertiaryContainer = AppColors.Amber800,
    error = AppColors.Red700,
    onError = AppColors.White,
    errorContainer = AppColors.Red100,
    onErrorContainer = AppColors.Red800,
    background = AppColors.White,
    onBackground = AppColors.Ink900,
    surface = AppColors.White,
    onSurface = AppColors.Ink900,
    surfaceVariant = AppColors.Surface,
    onSurfaceVariant = AppColors.Ink600,
    surfaceContainerLowest = AppColors.White,
    surfaceContainerLow = AppColors.SurfaceSoft,
    surfaceContainer = AppColors.Surface,
    surfaceContainerHigh = AppColors.LineSoft,
    outline = AppColors.Line,
    outlineVariant = AppColors.LineSoft
)

private val DarkColorScheme = darkColorScheme(
    primary = AppColors.Green300,
    onPrimary = AppColors.Camera,
    primaryContainer = AppColors.Green700,
    onPrimaryContainer = AppColors.Green100,
    secondary = AppColors.Green500,
    onSecondary = AppColors.White,
    secondaryContainer = AppColors.Green600,
    onSecondaryContainer = AppColors.Green50,
    tertiary = AppColors.Amber400,
    onTertiary = AppColors.Amber800,
    tertiaryContainer = AppColors.Amber800,
    onTertiaryContainer = AppColors.Amber100,
    error = AppColors.Red300,
    onError = AppColors.Red800,
    errorContainer = AppColors.Red800,
    onErrorContainer = AppColors.Red100,
    background = AppColors.Camera,
    onBackground = AppColors.White,
    surface = AppColors.Ink900,
    onSurface = AppColors.White,
    surfaceVariant = AppColors.Ink700,
    onSurfaceVariant = AppColors.SwitchOff,
    outline = AppColors.Ink600,
    outlineVariant = AppColors.Ink700
)

/**
 * Application Material 3 theme configured with the HiFi color palette ([AppColors]),
 * Plus Jakarta Sans / JetBrains Mono typography ([Typography]), and rounded geometry ([Shapes]).
 *
 * Note: [dynamicColor] defaults to `false` so semantic color rules
 * (Green = positive/paid/synced, Red = only for money owed, Group colours = never green/red)
 * are preserved across all Android versions.
 */
@Composable
fun GetYourMoneyUpNotYourFunnyUpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

/**
 * Formats an integer amount in minor units (øre / cents) using the HiFi Danish currency format:
 * e.g., `124050L` -> `"1.240,50"` or `"1.240,50 kr"` when [includeSuffix] is `true`.
 * Always renders 2 decimal places with `.` for thousands and `,` for decimals.
 */
fun formatDanishAmountFromCents(
    amountCents: Long,
    includeSuffix: Boolean = true,
    includePlusSign: Boolean = false
): String {
    val absCents = abs(amountCents)
    val whole = absCents / 100
    val decimals = absCents % 100
    val wholeFormatted = String.format(Locale.US, "%,d", whole).replace(',', '.')
    val sign = when {
        amountCents < 0 -> "−"
        amountCents > 0 && includePlusSign -> "+"
        else -> ""
    }
    val formatted = String.format(Locale.ROOT, "%s%s,%02d", sign, wholeFormatted, decimals)
    return if (includeSuffix) "$formatted kr" else formatted
}
