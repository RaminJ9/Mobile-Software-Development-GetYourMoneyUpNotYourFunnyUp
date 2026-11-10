package com.example.getyourmoneyupnotyourfunnyup.ui.screens.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.getyourmoneyupnotyourfunnyup.model.MoneyFormat
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppIcon
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppIcons
import com.example.getyourmoneyupnotyourfunnyup.ui.components.BalanceChip
import com.example.getyourmoneyupnotyourfunnyup.ui.components.ChipTone
import com.example.getyourmoneyupnotyourfunnyup.ui.components.GroupAvatar
import com.example.getyourmoneyupnotyourfunnyup.ui.components.OneLine
import com.example.getyourmoneyupnotyourfunnyup.ui.components.cardOutline
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppExtraColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppTypography
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.jakarta
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.GroupRowUi

/** "You owe 286,00" / "+145,00 to you" / "Settled". */
@Composable
fun NetBalanceChip(netCents: Long, modifier: Modifier = Modifier) {
    when {
        netCents < 0 -> BalanceChip("You owe ${MoneyFormat.kr(netCents)}", ChipTone.Owe, modifier)
        netCents > 0 -> BalanceChip("+${MoneyFormat.kr(netCents)} to you", ChipTone.Owed, modifier)
        else -> BalanceChip("Settled", ChipTone.Neutral, modifier)
    }
}

/**
 * A group in a list. On Home the favourite star sits next to the name and unsynced expenses replace
 * the subtitle; on the Groups tab a [trailing] star button toggles the favourite instead.
 */
@Composable
fun GroupRowCard(
    row: GroupRowUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showInlineStar: Boolean = true,
    showSyncState: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .cardOutline()
            .background(AppColors.White),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = if (trailing == null) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GroupAvatar(row.initial, row.paletteIndex)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OneLine(row.name, AppTypography.ItemTitle, AppColors.Ink900, Modifier.weight(1f, fill = false))
                    if (showInlineStar && row.isFavourite) {
                        AppIcon(
                            AppIcons.Star, size = 14.dp, strokeWidth = 1.5f,
                            tint = AppColors.StarGold, fill = AppColors.StarGold,
                            contentDescription = "Favourite",
                        )
                    }
                }
                if (showSyncState && row.unsyncedCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        AppIcon(AppIcons.Sync, size = 13.dp, strokeWidth = 2.2f, tint = AppColors.Amber700)
                        OneLine(
                            if (row.unsyncedCount == 1) "1 expense waiting to sync" else "${row.unsyncedCount} expenses waiting to sync",
                            jakarta(13, FontWeight.Medium), AppColors.Amber700,
                        )
                    }
                } else {
                    OneLine("${row.memberCount} members · ${row.activeLabel}", AppTypography.Caption, AppColors.Ink600)
                }
            }
            NetBalanceChip(row.netCents)
        }
        trailing?.invoke()
    }
}

/** Star button used on the Groups tab. */
@Composable
fun FavouriteToggle(isFavourite: Boolean, groupName: String, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .padding(end = 4.dp)
            .clickable(onClick = onToggle)
            .semantics {
                contentDescription = if (isFavourite) "Remove $groupName from favourites" else "Add $groupName to favourites"
            }
            .padding(horizontal = 10.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(
            AppIcons.Star, size = 20.dp,
            tint = if (isFavourite) AppColors.StarGold else AppExtraColors.StarOutline,
            fill = if (isFavourite) AppColors.StarGold else null,
        )
    }
}
