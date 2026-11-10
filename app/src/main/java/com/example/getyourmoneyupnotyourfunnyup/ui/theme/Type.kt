package com.example.getyourmoneyupnotyourfunnyup.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.getyourmoneyupnotyourfunnyup.R

/**
 * Plus Jakarta Sans downloadable Google Font family (weights 400, 500, 600, 700, 800).
 */
val PlusJakartaSansFontFamily = FontFamily(
    Font(resId = R.font.plus_jakarta_sans_regular, weight = FontWeight.Normal),
    Font(resId = R.font.plus_jakarta_sans_medium, weight = FontWeight.Medium),
    Font(resId = R.font.plus_jakarta_sans_semibold, weight = FontWeight.SemiBold),
    Font(resId = R.font.plus_jakarta_sans_bold, weight = FontWeight.Bold),
    Font(resId = R.font.plus_jakarta_sans_extrabold, weight = FontWeight.ExtraBold)
)

/**
 * JetBrains Mono downloadable Google Font family (weights 400, 500) — used exclusively for receipts.
 */
val JetBrainsMonoFontFamily = FontFamily(
    Font(resId = R.font.jetbrains_mono_regular, weight = FontWeight.Normal),
    Font(resId = R.font.jetbrains_mono_medium, weight = FontWeight.Medium)
)

/**
 * Semantic typography tokens directly matching the HiFi Style Guide (`docs/HiFi.html`).
 *
 * Type scale summary:
 * - **Display**: 30sp / 800 / -3% ("Snap a receipt", "Welcome back")
 * - **Screen title**: 24sp / 800 / -2% ("Hi, Victor")
 * - **Amount**: 28sp / 800 / -3% ("412,50 kr")
 * - **Section**: 18sp / 800 ("Your groups")
 * - **Item title**: 15sp / 700 ("Ski trip Åre")
 * - **Body**: 15sp / 500 / 1.5 line-height ("Everything is read on your phone.")
 * - **Caption**: 13sp / 500 ("6 members · Active today")
 * - **Label**: 12sp / 700 ("Home   Groups   Scan", balance chips)
 */
object AppTypography {
    val Display = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 34.5.sp,
        letterSpacing = (-0.03).em
    )

    val AmountLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.03).em
    )

    val Amount = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.03).em
    )

    val ScreenTitleLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.02).em
    )

    val ScreenTitle = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.02).em
    )

    val TopBarTitle = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.01).em
    )

    val Section = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.01).em
    )

    val Subsection = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 16.sp,
        lineHeight = 20.sp
    )

    val ButtonPrimary = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp
    )

    val ButtonSecondary = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 19.sp
    )

    val ButtonInline = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    )

    val ItemTitle = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    )

    val Body = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.5.sp
    )

    val BodyStrong = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )

    val Caption = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )

    val CaptionStrong = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )

    val Label = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )

    val Tag = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )

    val ReceiptMono = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 16.sp
    )

    val ReceiptMonoTotal = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp
    )
}

/**
 * Material 3 [Typography] mapped to the HiFi Plus Jakarta Sans scale.
 */
val Typography = Typography(
    displayLarge = AppTypography.AmountLarge,
    displayMedium = AppTypography.Display,
    displaySmall = AppTypography.Amount,
    headlineLarge = AppTypography.ScreenTitleLarge,
    headlineMedium = AppTypography.ScreenTitle,
    headlineSmall = AppTypography.TopBarTitle,
    titleLarge = AppTypography.Section,
    titleMedium = AppTypography.ItemTitle,
    titleSmall = AppTypography.ButtonInline,
    bodyLarge = AppTypography.Body,
    bodyMedium = AppTypography.BodyStrong,
    bodySmall = AppTypography.Caption,
    labelLarge = AppTypography.ButtonPrimary,
    labelMedium = AppTypography.CaptionStrong,
    labelSmall = AppTypography.Label
)
