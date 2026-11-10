package com.example.getyourmoneyupnotyourfunnyup.ui.screens.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.getyourmoneyupnotyourfunnyup.data.demo.DemoLedger
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppButton
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppIcon
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppIcons
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppTextField
import com.example.getyourmoneyupnotyourfunnyup.ui.components.ButtonSize
import com.example.getyourmoneyupnotyourfunnyup.ui.components.ButtonTone
import com.example.getyourmoneyupnotyourfunnyup.ui.components.EmptyCard
import com.example.getyourmoneyupnotyourfunnyup.ui.components.FilterPill
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.jakarta
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.GroupFilter
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.GroupsAction
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.GroupsLocalState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.GroupsUiState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.GroupsViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.SummaryTone
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.appViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.buildGroupsUiState

@Composable
fun GroupsRoute(
    onOpenGroup: (String) -> Unit,
    onCreateGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = appViewModel { GroupsViewModel(it.ledgerRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GroupsScreen(state, viewModel::onAction, onOpenGroup, onCreateGroup, modifier)
}

@Composable
fun GroupsScreen(
    state: GroupsUiState,
    onAction: (GroupsAction) -> Unit,
    onOpenGroup: (String) -> Unit,
    onCreateGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(AppColors.White).statusBarsPadding()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Groups", Modifier.weight(1f), style = jakarta(26, FontWeight.ExtraBold, -0.02f), color = AppColors.Ink900)
                AppButton(
                    text = "New group",
                    onClick = onCreateGroup,
                    tone = ButtonTone.Soft,
                    size = ButtonSize.Compact,
                    leadingIcon = AppIcons.Plus,
                    contentPadding = PaddingValues(horizontal = 14.dp),
                )
            }
            AppTextField(
                value = state.query,
                onValueChange = { onAction(GroupsAction.QueryChanged(it)) },
                placeholder = "Search groups",
                borderless = true,
                containerColor = AppColors.Surface,
                leadingIcon = { AppIcon(AppIcons.Search, size = 20.dp, strokeWidth = 2f, tint = AppColors.Ink600) },
                contentDescription = "Search groups",
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GroupFilter.entries.forEach { filter ->
                val (background, content) = when (filter) {
                    GroupFilter.Owe -> AppColors.Red100 to AppColors.Red700
                    GroupFilter.Owed -> AppColors.Green100 to AppColors.Green700
                    else -> AppColors.Ink900 to AppColors.White
                }
                FilterPill(
                    label = filter.label,
                    count = state.counts[filter],
                    selected = filter == state.filter,
                    onClick = { onAction(GroupsAction.SelectFilter(filter)) },
                    selectedBackground = background,
                    selectedContent = content,
                )
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.summary?.let { summary ->
                item(key = "summary") {
                    val owe = state.summaryTone == SummaryTone.Owe
                    Text(
                        summary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                            .background(if (owe) AppColors.Red50 else AppColors.Green50, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        style = jakarta(14, FontWeight.Bold),
                        color = if (owe) AppColors.Red800 else AppColors.Green700,
                    )
                }
            }
            items(state.rows, key = { it.groupId }) { row ->
                GroupRowCard(
                    row = row,
                    onClick = { onOpenGroup(row.groupId) },
                    showInlineStar = false,
                    showSyncState = false,
                    trailing = {
                        FavouriteToggle(row.isFavourite, row.name, onToggle = { onAction(GroupsAction.ToggleFavourite(row.groupId)) })
                    },
                )
            }
            if (state.emptyTitle != null) {
                item(key = "empty") { EmptyCard(title = state.emptyTitle, text = state.emptyText) }
            }
        }
    }
}

@Preview(name = "Groups", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun GroupsPreview() {
    val now = System.currentTimeMillis()
    GetYourMoneyUpNotYourFunnyUpTheme {
        GroupsScreen(buildGroupsUiState(DemoLedger.create(now), GroupsLocalState(), now), {}, {}, {})
    }
}

@Preview(name = "Groups – you owe", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun GroupsOwePreview() {
    val now = System.currentTimeMillis()
    GetYourMoneyUpNotYourFunnyUpTheme {
        GroupsScreen(buildGroupsUiState(DemoLedger.create(now), GroupsLocalState(filter = GroupFilter.Owe), now), {}, {}, {})
    }
}
