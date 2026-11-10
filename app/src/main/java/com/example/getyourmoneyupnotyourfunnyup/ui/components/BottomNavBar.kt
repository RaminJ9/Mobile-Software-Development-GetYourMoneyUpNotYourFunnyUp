package com.example.getyourmoneyupnotyourfunnyup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.jakarta

/** Top-level tabs of the authenticated app. */
enum class MainTab(val label: String, val icon: AppIconSpec) {
    Home("Home", AppIcons.Home),
    Groups("Groups", AppIcons.Users),
    Activity("Activity", AppIcons.Clock),
    Profile("Profile", AppIcons.User),
}

/**
 * Bottom navigation: Home · Groups · (raised) Scan · Activity · Profile. 80 dp tall plus the system
 * navigation bar inset (edge-to-edge).
 */
@Composable
fun BottomNavBar(
    selected: MainTab?,
    onSelect: (MainTab) -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().background(AppColors.White)) {
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(AppColors.LineSoft))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(79.dp)
                .padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            NavItem(MainTab.Home, selected == MainTab.Home, onSelect, Modifier.weight(1f))
            NavItem(MainTab.Groups, selected == MainTab.Groups, onSelect, Modifier.weight(1f))
            ScanItem(onScan, Modifier.weight(1f))
            NavItem(MainTab.Activity, selected == MainTab.Activity, onSelect, Modifier.weight(1f))
            NavItem(MainTab.Profile, selected == MainTab.Profile, onSelect, Modifier.weight(1f))
        }
        Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
    }
}

@Composable
private fun NavItem(tab: MainTab, selected: Boolean, onSelect: (MainTab) -> Unit, modifier: Modifier) {
    val color = if (selected) AppColors.Green700 else AppColors.Ink600
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 32.dp),
                role = Role.Tab,
            ) { onSelect(tab) }
            .semantics { this.selected = selected }
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppIcon(tab.icon, tint = color, fill = if (selected) AppColors.Green100 else null)
        Text(tab.label, style = jakarta(12, if (selected) FontWeight.Bold else FontWeight.SemiBold), color = color)
    }
}

@Composable
private fun ScanItem(onScan: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onScan,
            )
            .semantics(mergeDescendants = true) { contentDescription = "Scan receipt" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                // Equivalent of CSS `margin-top: -30px`: the button rises above the bar.
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val lift = 30.dp.roundToPx()
                    layout(placeable.width, placeable.height - lift) { placeable.place(0, -lift) }
                }
                .shadow(12.dp, CircleShape, ambientColor = AppColors.Green700, spotColor = AppColors.Green700)
                .size(64.dp)
                .background(AppColors.Green700, CircleShape)
                .border(4.dp, AppColors.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIcons.Scan, size = 28.dp, strokeWidth = 2f, tint = Color.White)
        }
        Text("Scan", style = jakarta(12, FontWeight.Bold), color = AppColors.Green700)
    }
}

@Preview(showBackground = true, widthDp = 412)
@Composable
private fun BottomNavBarPreview() {
    GetYourMoneyUpNotYourFunnyUpTheme {
        Box(Modifier.padding(top = 40.dp)) {
            BottomNavBar(selected = MainTab.Home, onSelect = {}, onScan = {})
        }
    }
}
