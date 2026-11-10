package com.example.getyourmoneyupnotyourfunnyup.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Application color palette aligned with the HiFi Style Guide (`docs/HiFi.html`).
 *
 * Core visual rules:
 * - **Green = good**: Money in, paid, synced, done. The colour you're rewarded with.
 * - **Red = pay this**: Only for money you owe. It goes away once you pay.
 * - **Everything else**: White, ink and soft grey. Amber only for warnings and waiting-to-sync.
 * - **Pastels are fills**: Text and buttons use the deep shade of the same hue (all pass 4.5:1).
 */
object AppColors {
    // Green – positive & primary
    val Green700 = Color(0xFF1E6B45) // Primary buttons, links, active tab, money in
    val Green800 = Color(0xFF14502F) // Hover / pressed primary state
    val Green600 = Color(0xFF2A5A3F) // Subtitle text on green fills
    val Green500 = Color(0xFF3FA36B) // Progress, unread dots, focus ring
    val Green300 = Color(0xFF8FD6AA) // "You" avatar, accents on dark
    val GreenBar = Color(0xFF9FDCB5) // "Is owed" bars in balance chart
    val Green200 = Color(0xFFC6E8D2) // Paid card border, dashed add-item border
    val Green100 = Color(0xFFDDF3E4) // Positive fills, soft buttons, chips
    val Green50  = Color(0xFFEEF9F1) // Paid / settled card background

    // Red – only for money you owe
    val Red700 = Color(0xFFA93A2F) // Pay buttons, amounts you owe
    val Red800 = Color(0xFF8E3128) // Text on red fills
    val Red300 = Color(0xFFF4B9B2) // "Owes" bars in balance chart
    val Red200 = Color(0xFFF6D3CE) // Payments-due dropdown border
    val Red100 = Color(0xFFFCE4E1) // "You owe" card, owe chips
    val Red50  = Color(0xFFFDF1EF) // Payments-due dropdown, error strips

    // Ink & surfaces
    val Ink900      = Color(0xFF17201B) // Main text
    val Ink700      = Color(0xFF3D4842) // Offline banner text
    val Ink600      = Color(0xFF56635B) // Secondary text, inactive tabs
    val Ink400      = Color(0xFF7A857F) // Muted icons, disabled/excluded member text
    val SwitchOff   = Color(0xFFC9D1CC) // Inactive switch track, neutral spinner track
    val InputBorder = Color(0xFFDCE2DE) // Text field borders, bottom sheet drag handle
    val Line        = Color(0xFFE6EBE8) // Borders, dividers
    val LineSoft    = Color(0xFFEEF1EF) // Card borders, list dividers
    val Surface     = Color(0xFFF1F4F2) // Search, segmented tabs, settled chip
    val SurfaceSoft = Color(0xFFF4F7F5) // Subtle containers, empty state cards
    val White       = Color(0xFFFFFFFF) // App background

    // Amber – warnings & waiting to sync
    val Amber800 = Color(0xFF6E4500) // Warning text
    val Amber700 = Color(0xFF8A5A12) // Syncing subtitle text on group rows
    val Amber500 = Color(0xFFE0B04F) // Highlight input border
    val Amber400 = Color(0xFFF0C870) // Highlight border on a misread item
    val Amber200 = Color(0xFFFFE7B0) // Low-confidence price tag background
    val Amber100 = Color(0xFFFFF4DC) // Warnings, waiting-to-sync
    val Amber50  = Color(0xFFFFF6E3) // Pending expense strip background

    // Special & accent tokens
    val Camera       = Color(0xFF101814) // Camera background only
    val ReceiptPaper = Color(0xFFFBFAF6) // Receipt paper preview background
    val Lavender800  = Color(0xFF3E2C68) // Diet rule note text
    val Lavender50   = Color(0xFFF3EEFC) // Diet rule notes (no alcohol / meat)
    val StarGold     = Color(0xFFE8B84A) // Favourite star icon

    // Group colours (never green or red, so a slice/avatar is never mistaken for "paid" or "owe")
    val GroupBlue     = Color(0xFFA9C8F5)
    val GroupLavender = Color(0xFFC9B6F0)
    val GroupPeach    = Color(0xFFF7C59F)
    val GroupButter   = Color(0xFFF3DE8A)
    val GroupTeal     = Color(0xFF9ED9D2)
    val GroupSand     = Color(0xFFE2CDB0)
    val GroupSky      = Color(0xFFB9DCEB)
    val GroupLilac    = Color(0xFFE4CBE9)
    val GroupApricot  = Color(0xFFF2D7B6)

    val GroupColors: List<Color> = listOf(
        GroupBlue,
        GroupLavender,
        GroupPeach,
        GroupButter,
        GroupTeal,
        GroupSand,
        GroupSky,
        GroupLilac,
        GroupApricot
    )

    /**
     * Returns a deterministic group/member colour from [GroupColors] based on an index or key.
     * Note: The current authenticated user ("You") always uses [Green300].
     */
    fun groupColorAt(index: Int): Color =
        GroupColors[index.mod(GroupColors.size)]
}

// Top-level convenience aliases
val Green700 = AppColors.Green700
val Green500 = AppColors.Green500
val Green300 = AppColors.Green300
val Green100 = AppColors.Green100
val Green50 = AppColors.Green50

val Red700 = AppColors.Red700
val Red800 = AppColors.Red800
val Red300 = AppColors.Red300
val Red100 = AppColors.Red100
val Red50 = AppColors.Red50

val Ink900 = AppColors.Ink900
val Ink600 = AppColors.Ink600
val Line = AppColors.Line
val Surface = AppColors.Surface
val White = AppColors.White

val Amber800 = AppColors.Amber800
val Amber400 = AppColors.Amber400
val Amber100 = AppColors.Amber100
