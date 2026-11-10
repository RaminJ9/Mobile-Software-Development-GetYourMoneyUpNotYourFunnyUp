package com.example.getyourmoneyupnotyourfunnyup.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * Small additions on top of the shared design system ([AppColors], [AppTypography], [AppShapes])
 * that the hi-fi screens need. Prefer the shared tokens; use these only for the cases below.
 */

/**
 * One-off shades used by individual hi-fi screens that are not part of the style-guide palette
 * (tinted card borders, receipt ink, the bottom-sheet scrim, …).
 */
object AppExtraColors {
    val GreenTint = Color(0xFFF4FBF6) // "Someone owes you" settle-up row
    val GreenTintBorder = Color(0xFFD7EEDF)
    val UnreadTint = Color(0xFFF7FBF8) // Unread activity card
    val UnreadBorder = Color(0xFFDCEFE3)
    val RedSoftBorder = Color(0xFFE9B3AC) // "Try now" outline in Activity
    val AmberInputTint = Color(0xFFFFFBF0) // Text field showing a "not found" warning
    val StarOutline = Color(0xFFA9B3AD) // Un-starred favourite
    val SurfaceFaint = Color(0xFFF7F9F8) // Receipt item rows in "Check items"
    val PieCaption = Color(0xFF4F5B54) // "kr this month" inside the donut
    val ReceiptInk = Color(0xFF2A2F2C)
    val ReceiptMuted = Color(0xFF6B716D)
    val ReceiptRule = Color(0xFF9AA19C)
    val Scrim = Color(0x6B17201B) // Behind bottom sheets (Ink900 at 42%)
}

/** Colour names for the group colour picker's accessibility labels, in [AppColors.GroupColors] order. */
val GroupColorNames = listOf("Blue", "Lavender", "Peach", "Butter", "Teal", "Sand", "Sky", "Lilac", "Apricot")

/**
 * Avatar colour for a palette index from the data layer. Negative means the signed-in user, who is
 * always [AppColors.Green300] ("You").
 */
fun avatarColor(paletteIndex: Int): Color =
    if (paletteIndex < 0) AppColors.Green300 else AppColors.groupColorAt(paletteIndex)

/**
 * Plus Jakarta Sans style for the one-off sizes in the hi-fi that have no [AppTypography] token.
 * [tracking] is letter spacing in em (e.g. -0.02f = -2%).
 */
fun jakarta(
    size: Int,
    weight: FontWeight,
    tracking: Float = 0f,
    lineHeight: TextUnit = TextUnit.Unspecified,
): TextStyle = TextStyle(
    fontFamily = PlusJakartaSansFontFamily,
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = if (tracking == 0f) TextUnit.Unspecified else tracking.em,
    lineHeight = lineHeight,
)
