package com.example.getyourmoneyupnotyourfunnyup.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radius tokens aligned with the HiFi Style Guide (`docs/HiFi.html`):
 * - `10 dp`: inputs (small), price inputs
 * - `12 dp`: balance chips, sync pills, active segmented tab
 * - `14 dp`: inline buttons, 44dp group avatar tiles
 * - `16 dp`: text fields, secondary 48dp buttons, segmented tab bars, alert strips
 * - `18 dp`: big 56dp primary buttons, debt/member/split cards
 * - `20 dp`: list cards, settings groups, filter pills
 * - `22 dp`: large group avatar tiles
 * - `24 dp`: hero cards, payments-due dropdown
 * - `28 dp`: bottom sheets
 */
object AppShapes {
    val InputSmall = RoundedCornerShape(10.dp)
    val Chip = RoundedCornerShape(12.dp)
    val InlineButton = RoundedCornerShape(14.dp)
    val SecondaryButton = RoundedCornerShape(16.dp)
    val TextField = RoundedCornerShape(16.dp)
    val SegmentedBar = RoundedCornerShape(16.dp)
    val BigButton = RoundedCornerShape(18.dp)
    val ListCard = RoundedCornerShape(20.dp)
    val Pill = RoundedCornerShape(20.dp)
    val AvatarTile = RoundedCornerShape(22.dp)
    val HeroCard = RoundedCornerShape(24.dp)
    val BottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val Circle = CircleShape
}

/**
 * Spacing scale and layout dimensions aligned with the HiFi Style Guide (`docs/HiFi.html`).
 *
 * Spacing rules:
 * - Scale: `4, 8, 12, 16, 20, 24 dp`
 * - Screen side padding: `16 dp`
 * - Gap between cards: `8–12 dp`
 * - Gap between sections: `20 dp`
 */
object AppSpacing {
    val Space4 = 4.dp
    val Space8 = 8.dp
    val Space12 = 12.dp
    val Space16 = 16.dp
    val Space20 = 20.dp
    val Space24 = 24.dp

    val ScreenHorizontalPadding = 16.dp
    val ScreenTopPadding = 20.dp
    val ScreenBottomPadding = 28.dp
    val CardGapSmall = 8.dp
    val CardGap = 12.dp
    val SectionGap = 20.dp
}

/**
 * Minimum touch target and component height standards from the HiFi Style Guide:
 * - Nothing tappable smaller than `44 dp`
 * - Primary buttons: `56 dp`
 * - Secondary buttons: `48 dp`
 * - Inline buttons: `44 dp`
 * - Text fields: `52 dp`
 */
object AppTouchTargets {
    val MinTouchTarget = 44.dp
    val InlineButtonHeight = 44.dp
    val SecondaryButtonHeight = 48.dp
    val TextFieldHeight = 52.dp
    val PrimaryButtonHeight = 56.dp
    val ScanFabSize = 64.dp
    val BottomNavHeight = 80.dp
}

/**
 * Material 3 [Shapes] mapped to the HiFi corner radius scale.
 */
val Shapes = Shapes(
    extraSmall = AppShapes.InputSmall,
    small = AppShapes.InlineButton,
    medium = AppShapes.BigButton,
    large = AppShapes.ListCard,
    extraLarge = AppShapes.HeroCard
)
