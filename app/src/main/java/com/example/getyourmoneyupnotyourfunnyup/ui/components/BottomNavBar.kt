package com.example.getyourmoneyupnotyourfunnyup.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppShapes
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppSpacing
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppTouchTargets
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppTypography
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme

/**
 * The 5 primary destinations in the authenticated bottom navigation shell (`docs/HiFi.html`).
 */
enum class AppTab(
    val label: String,
    val fillPath: String,
    val strokePath: String
) {
    Home(
        label = "Home",
        fillPath = "M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z",
        strokePath = "M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"
    ),
    Groups(
        label = "Groups",
        fillPath = "M9 3a4 4 0 1 0 0 8 4 4 0 1 0 0-8z",
        strokePath = "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 3a4 4 0 1 0 0 8 4 4 0 1 0 0-8z M22 21v-2a4 4 0 0 0-3-3.87 M16 3.13a4 4 0 0 1 0 7.75"
    ),
    Scan(
        label = "Scan",
        fillPath = "",
        strokePath = "M3 7V5a2 2 0 0 1 2-2h2 M17 3h2a2 2 0 0 1 2 2v2 M21 17v2a2 2 0 0 1-2 2h-2 M7 21H5a2 2 0 0 1-2-2v-2 M7 9h10 M7 12.5h10 M7 16h6"
    ),
    Activity(
        label = "Activity",
        fillPath = "M12 3a9 9 0 1 0 0 18 9 9 0 1 0 0-18z",
        strokePath = "M12 3a9 9 0 1 0 0 18 9 9 0 1 0 0-18z M12 7.5V12l3 2"
    ),
    Profile(
        label = "Profile",
        fillPath = "M12 4a4 4 0 1 0 0 8 4 4 0 1 0 0-8z M4 21a8 8 0 0 1 16 0",
        strokePath = "M12 4a4 4 0 1 0 0 8 4 4 0 1 0 0-8z M4 21a8 8 0 0 1 16 0"
    )
}

/**
 * 5-Tab Bottom Navigation Bar matching `docs/HiFi.html`:
 * - Height `80.dp` with `1.dp` top divider (`AppColors.LineSoft`)
 * - 4 standard tabs (`Home`, `Groups`, `Activity`, `Profile`) with `Green100` fill + `Green700` stroke when active
 * - Center elevated `64.dp` circular `Scan` button (`AppColors.Green700` with `4.dp` white border)
 */
@Composable
fun BottomNavBar(
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
    hasUnreadActivity: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.White)
    ) {
        // Top 1dp divider line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AppColors.LineSoft)
                .align(Alignment.TopCenter)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppTouchTargets.BottomNavHeight)
                .padding(start = AppSpacing.Space8, end = AppSpacing.Space8, bottom = AppSpacing.Space12),
            verticalAlignment = Alignment.Bottom
        ) {
            AppTab.entries.forEach { tab ->
                if (tab == AppTab.Scan) {
                    ScanNavTabItem(
                        isSelected = selectedTab == AppTab.Scan,
                        onClick = { onSelectTab(AppTab.Scan) },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    StandardNavTabItem(
                        tab = tab,
                        isSelected = selectedTab == tab,
                        showUnreadDot = tab == AppTab.Activity && hasUnreadActivity,
                        onClick = { onSelectTab(tab) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StandardNavTabItem(
    tab: AppTab,
    isSelected: Boolean,
    showUnreadDot: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (isSelected) AppColors.Green700 else AppColors.Ink600
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .heightIn(min = AppTouchTargets.MinTouchTarget)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .semantics {
                selected = isSelected
                contentDescription = "${tab.label} tab"
            }
            .padding(top = AppSpacing.Space8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Space4)
    ) {
        Box {
            NavTabIcon(
                tab = tab,
                isSelected = isSelected,
                strokeColor = tint
            )
            if (showUnreadDot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .background(AppColors.Green500, AppShapes.Circle)
                )
            }
        }
        Text(
            text = tab.label,
            style = AppTypography.Label.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
            ),
            color = tint
        )
    }
}

@Composable
private fun ScanNavTabItem(
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .semantics {
                selected = isSelected
                contentDescription = "Scan tab"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Space4)
    ) {
        Surface(
            modifier = Modifier
                .offset(y = (-14).dp)
                .size(AppTouchTargets.ScanFabSize)
                .shadow(
                    elevation = 8.dp,
                    shape = AppShapes.Circle,
                    ambientColor = AppColors.Green700,
                    spotColor = AppColors.Green700
                ),
            shape = AppShapes.Circle,
            color = if (isSelected) AppColors.Green800 else AppColors.Green700,
            contentColor = AppColors.White,
            border = BorderStroke(4.dp, AppColors.White)
        ) {
            Box(contentAlignment = Alignment.Center) {
                val vector = remember {
                    ImageVector.Builder("ScanIcon", 24.dp, 24.dp, 24f, 24f).addPath(
                        pathData = addPathNodes(AppTab.Scan.strokePath),
                        fill = null,
                        stroke = SolidColor(AppColors.White),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round
                    ).build()
                }
                Icon(
                    imageVector = vector,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = AppColors.White
                )
            }
        }
        Text(
            text = AppTab.Scan.label,
            style = AppTypography.Label,
            color = AppColors.Green700
        )
    }
}

@Composable
private fun NavTabIcon(
    tab: AppTab,
    isSelected: Boolean,
    strokeColor: Color
) {
    val vector = remember(tab, isSelected, strokeColor) {
        ImageVector.Builder(tab.name, 24.dp, 24.dp, 24f, 24f).apply {
            if (isSelected && tab.fillPath.isNotEmpty()) {
                addPath(
                    pathData = addPathNodes(tab.fillPath),
                    fill = SolidColor(AppColors.Green100),
                    stroke = null
                )
            }
            addPath(
                pathData = addPathNodes(tab.strokePath),
                fill = null,
                stroke = SolidColor(strokeColor),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }.build()
    }
    Icon(
        imageVector = vector,
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = Color.Unspecified
    )
}

@Preview(showBackground = true, name = "BottomNavBar Light")
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "BottomNavBar Dark"
)
@Composable
private fun BottomNavBarPreview() {
    GetYourMoneyUpNotYourFunnyUpTheme {
        Surface(color = AppColors.White) {
            Column(modifier = Modifier.padding(top = 32.dp)) {
                BottomNavBar(
                    selectedTab = AppTab.Home,
                    onSelectTab = {},
                    hasUnreadActivity = true
                )
            }
        }
    }
}
