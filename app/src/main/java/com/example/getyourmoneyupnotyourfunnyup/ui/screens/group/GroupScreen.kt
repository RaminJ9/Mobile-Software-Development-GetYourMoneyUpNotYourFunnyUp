package com.example.getyourmoneyupnotyourfunnyup.ui.screens.group

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.getyourmoneyupnotyourfunnyup.data.demo.DemoLedger
import com.example.getyourmoneyupnotyourfunnyup.model.MoneyFormat
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppButton
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppIcon
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppIcons
import com.example.getyourmoneyupnotyourfunnyup.ui.components.BalanceChip
import com.example.getyourmoneyupnotyourfunnyup.ui.components.ButtonSize
import com.example.getyourmoneyupnotyourfunnyup.ui.components.ButtonTone
import com.example.getyourmoneyupnotyourfunnyup.ui.components.ChipTone
import com.example.getyourmoneyupnotyourfunnyup.ui.components.ConfirmButtons
import com.example.getyourmoneyupnotyourfunnyup.ui.components.EmptyCard
import com.example.getyourmoneyupnotyourfunnyup.ui.components.GroupAvatar
import com.example.getyourmoneyupnotyourfunnyup.ui.components.OfflineBanner
import com.example.getyourmoneyupnotyourfunnyup.ui.components.OneLine
import com.example.getyourmoneyupnotyourfunnyup.ui.components.PersonAvatar
import com.example.getyourmoneyupnotyourfunnyup.ui.components.SegmentedTabs
import com.example.getyourmoneyupnotyourfunnyup.ui.components.Spinner
import com.example.getyourmoneyupnotyourfunnyup.ui.components.cardOutline
import com.example.getyourmoneyupnotyourfunnyup.ui.components.dashedBorder
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.home.StatusStrip
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppExtraColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppTypography
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.jakarta
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.BalanceRowUi
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.GroupAction
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.GroupHero
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.GroupLocalState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.GroupTab
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.GroupUiState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.GroupViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.LedgerRowKind
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.LedgerRowUi
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.MemberRowUi
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.PayState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.SettleKind
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.SettleRowUi
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.appViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.buildGroupUiState

/** View group (with the members, balances and expense-detail flows). */
@Composable
fun GroupRoute(
    groupId: String,
    onBack: () -> Unit,
    onAddExpense: (groupId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = appViewModel(key = "group:$groupId") { GroupViewModel(it.ledgerRepository, groupId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GroupScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        onAddExpense = { onAddExpense(groupId) },
        onDelete = {
            viewModel.deleteGroup()
            onBack()
        },
        modifier = modifier,
    )
}

@Composable
fun GroupScreen(
    state: GroupUiState,
    onAction: (GroupAction) -> Unit,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        GroupUiState.Missing -> MissingGroup(onBack, modifier)
        is GroupUiState.Content -> {
            GroupContent(state, onAction, onBack, onAddExpense, modifier)
            state.sheet?.let { ExpenseDetailsSheet(it, onAction) }
            state.addMember?.let { AddMemberSheet(it, onAction) }
            if (state.confirmDelete) {
                AlertDialog(
                    onDismissRequest = { onAction(GroupAction.CancelDelete) },
                    title = { Text("Delete ${state.name}?", style = jakarta(20, FontWeight.ExtraBold)) },
                    text = {
                        Text(
                            "Everyone in the group loses its expenses and balances. This can't be undone.",
                            style = jakarta(15, FontWeight.Medium, lineHeight = 22.sp),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = onDelete) { Text("Delete", style = jakarta(15, FontWeight.Bold), color = AppColors.Red700) }
                    },
                    dismissButton = {
                        TextButton(onClick = { onAction(GroupAction.CancelDelete) }) {
                            Text("Keep it", style = jakarta(15, FontWeight.Bold), color = AppColors.Ink900)
                        }
                    },
                    containerColor = AppColors.White,
                )
            }
        }
    }
}

@Composable
private fun GroupContent(
    state: GroupUiState.Content,
    onAction: (GroupAction) -> Unit,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.White)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth().offset(x = (-4).dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) {
                AppIcon(AppIcons.ArrowLeft, size = 22.dp, strokeWidth = 2f, tint = AppColors.Ink900)
            }
            Box(Modifier.weight(1f))
            Box {
                IconButton(
                    onClick = { onAction(GroupAction.ToggleMenu) },
                    modifier = Modifier.offset(x = 8.dp).semantics { contentDescription = "Group settings" },
                ) {
                    AppIcon(AppIcons.More, size = 22.dp, tint = AppColors.Ink900)
                }
                DropdownMenu(
                    expanded = state.menuOpen,
                    onDismissRequest = { onAction(GroupAction.ToggleMenu) },
                    containerColor = AppColors.White,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    DropdownMenuItem(
                        text = { Text(if (state.isFavourite) "Remove from favourites" else "Add to favourites", style = jakarta(14, FontWeight.SemiBold)) },
                        leadingIcon = {
                            AppIcon(
                                AppIcons.Star, size = 20.dp,
                                tint = if (state.isFavourite) AppColors.StarGold else AppColors.Ink600,
                                fill = if (state.isFavourite) AppColors.StarGold else null,
                            )
                        },
                        onClick = { onAction(GroupAction.ToggleFavourite) },
                    )
                    if (state.canDelete) {
                        DropdownMenuItem(
                            text = { Text("Delete group", style = jakarta(14, FontWeight.SemiBold), color = AppColors.Red800) },
                            leadingIcon = { AppIcon(AppIcons.Trash, size = 20.dp, tint = AppColors.Red800) },
                            onClick = { onAction(GroupAction.AskDelete) },
                        )
                    }
                }
            }
        }

        state.offlineMessage?.let { OfflineBanner(it, Modifier.offset(y = (-8).dp)) }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                GroupAvatar(state.avatar.initial, state.avatar.paletteIndex, size = 56.dp, corner = 18.dp, fontSize = 22)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    OneLine(state.name, AppTypography.ScreenTitle, AppColors.Ink900)
                    Text("${state.memberCount} members · ${state.createdLabel}", style = AppTypography.Caption, color = AppColors.Ink600)
                }
                Row(
                    modifier = Modifier
                        .clickable(role = Role.Button) { onAction(GroupAction.SelectTab(GroupTab.Members)) }
                        .semantics(mergeDescendants = true) { contentDescription = "See members" }
                        .padding(vertical = 6.dp),
                ) {
                    state.memberStack.forEachIndexed { index, avatar ->
                        PersonAvatar(
                            avatar.initial, avatar.paletteIndex, size = 32.dp, ringColor = AppColors.White,
                            modifier = Modifier.offset(x = (-10 * index).dp),
                        )
                    }
                }
            }
            state.description?.let {
                Text(it, style = jakarta(14, FontWeight.Medium, lineHeight = 21.sp), color = AppColors.Ink600)
            }
        }

        GroupHeroCard(state.hero, onAction)

        SegmentedTabs(
            labels = GroupTab.entries.map { it.label },
            selectedIndex = state.tab.ordinal,
            onSelect = { onAction(GroupAction.SelectTab(GroupTab.entries[it])) },
        )

        when (state.tab) {
            GroupTab.Activity -> ActivityTab(state, onAction, onAddExpense)
            GroupTab.Balances -> BalancesTab(state, onAction)
            GroupTab.Members -> MembersTab(state.members, onAction)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Hero
// ---------------------------------------------------------------------------------------------

@Composable
private fun GroupHeroCard(hero: GroupHero, onAction: (GroupAction) -> Unit) {
    when (hero) {
        is GroupHero.Owe -> Column(
            Modifier.fillMaxWidth().background(AppColors.Red100, RoundedCornerShape(24.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("You owe ${hero.toName}", style = jakarta(13, FontWeight.SemiBold), color = AppColors.Red800)
                    Text("${MoneyFormat.kr(hero.amountCents)} kr", style = AppTypography.Amount, color = AppColors.Red700)
                }
                when (hero.state) {
                    PayState.Idle -> AppButton(
                        text = "Pay", onClick = { onAction(GroupAction.AskPay) },
                        tone = ButtonTone.Pay, size = ButtonSize.Medium, contentPadding = PaddingValues(horizontal = 22.dp),
                    )
                    PayState.Pending -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Spinner(size = 16.dp, color = AppColors.Red700, trackColor = AppColors.Red300)
                        Text("Paying…", style = jakarta(14, FontWeight.SemiBold), color = AppColors.Red800)
                    }
                    else -> Unit
                }
            }
            if (hero.state == PayState.Confirm) {
                ConfirmButtons(
                    onCancel = { onAction(GroupAction.CancelPay) },
                    onConfirm = { onAction(GroupAction.ConfirmPay) },
                    confirmText = "Confirm ${MoneyFormat.kr(hero.amountCents)} kr",
                    cancelTone = ButtonTone.OnTint,
                )
            }
        }
        is GroupHero.Owed -> Row(
            Modifier.fillMaxWidth().background(AppColors.Green100, RoundedCornerShape(24.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("You're owed", style = jakarta(13, FontWeight.SemiBold), color = AppColors.Green600)
                Text("+${MoneyFormat.kr(hero.amountCents)} kr", style = AppTypography.Amount, color = AppColors.Green700)
                Text(hero.fromText, style = AppTypography.Caption, color = AppColors.Green600)
            }
            if (hero.allReminded) {
                Row(
                    Modifier.height(44.dp).background(AppColors.White, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AppIcon(AppIcons.Check, size = 16.dp, strokeWidth = 2.4f, tint = AppColors.Green700)
                    Text("Reminded", style = jakarta(14, FontWeight.Bold), color = AppColors.Green700)
                }
            } else {
                AppButton(
                    text = "Remind", onClick = { onAction(GroupAction.RemindAll) },
                    size = ButtonSize.Medium, leadingIcon = AppIcons.Bell, contentPadding = PaddingValues(horizontal = 18.dp),
                )
            }
        }
        is GroupHero.Settled -> Row(
            Modifier.fillMaxWidth().background(AppColors.Green100, RoundedCornerShape(24.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(44.dp).background(AppColors.White, CircleShape), contentAlignment = Alignment.Center) {
                AppIcon(AppIcons.Check, size = 22.dp, strokeWidth = 2.4f, tint = AppColors.Green700)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("You're all square", style = jakarta(18, FontWeight.ExtraBold), color = AppColors.Green700)
                Text(hero.subtitle, style = AppTypography.Caption, color = AppColors.Green600)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Activity tab
// ---------------------------------------------------------------------------------------------

@Composable
private fun ActivityTab(state: GroupUiState.Content, onAction: (GroupAction) -> Unit, onAddExpense: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${state.expenseCount} expenses · newest first",
                Modifier.weight(1f),
                style = jakarta(13, FontWeight.Bold),
                color = AppColors.Ink600,
            )
            AppButton(
                text = "Add expense", onClick = onAddExpense, tone = ButtonTone.Soft, size = ButtonSize.Compact,
                leadingIcon = AppIcons.Plus, contentPadding = PaddingValues(horizontal = 14.dp),
            )
        }
        state.ledgerRows.forEach { row -> LedgerRow(row, onAction) }
        if (state.ledgerRows.isEmpty()) {
            EmptyCard(title = "No expenses yet", text = "Scan a receipt or add an expense and we'll split it for you.")
        }
    }
}

@Composable
private fun LedgerRow(row: LedgerRowUi, onAction: (GroupAction) -> Unit) {
    val settle = row.kind == LedgerRowKind.Settle
    Column(Modifier.fillMaxWidth().cardOutline()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.White)
                .then(if (row.opensDetails) Modifier.clickable { onAction(GroupAction.OpenExpense(row.id)) } else Modifier)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(44.dp).background(if (settle) AppColors.Green100 else AppColors.Surface, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val icon = when (row.kind) {
                    LedgerRowKind.Settle -> AppIcons.Swap
                    LedgerRowKind.Receipt -> AppIcons.Receipt
                    LedgerRowKind.Plain -> AppIcons.Card
                }
                AppIcon(icon, size = 20.dp, strokeWidth = if (settle) 2f else 1.8f, tint = if (settle) AppColors.Green700 else AppColors.Ink900)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OneLine(row.title, AppTypography.ItemTitle, AppColors.Ink900)
                OneLine(row.subtitle, AppTypography.Caption, AppColors.Ink600)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    MoneyFormat.kr(row.amountCents),
                    style = jakarta(15, FontWeight.ExtraBold),
                    color = if (settle) AppColors.Green700 else AppColors.Ink900,
                    maxLines = 1,
                )
                if (row.shareLabel.isNotEmpty()) {
                    Text(row.shareLabel, style = jakarta(12, FontWeight.SemiBold), color = AppColors.Ink600)
                }
            }
        }
        when (row.syncStatus) {
            SyncStatus.PENDING_INSERT, SyncStatus.PENDING_UPDATE, SyncStatus.PENDING_DELETE -> Row(
                Modifier.fillMaxWidth().background(AppColors.Amber50).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spinner(size = 12.dp, color = AppColors.Amber800, trackColor = AppColors.Amber400)
                Text("Saved on your phone · waiting to sync", style = jakarta(13, FontWeight.SemiBold), color = AppColors.Amber800)
            }
            SyncStatus.FAILED -> Row(
                Modifier.fillMaxWidth().background(AppColors.Red50).padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppIcon(AppIcons.AlertCircle, size = 16.dp, strokeWidth = 2f, tint = AppColors.Red800)
                Text(
                    "Couldn't sync yet · retrying automatically",
                    Modifier.weight(1f),
                    style = jakarta(13, FontWeight.SemiBold),
                    color = AppColors.Red800,
                )
                AppButton(
                    text = "Try now", onClick = { onAction(GroupAction.RetrySync(row.id)) },
                    tone = ButtonTone.OnTint, size = ButtonSize.Compact, contentPadding = PaddingValues(horizontal = 12.dp),
                )
            }
            else -> Unit
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Balances tab
// ---------------------------------------------------------------------------------------------

@Composable
private fun BalancesTab(state: GroupUiState.Content, onAction: (GroupAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(
            Modifier.fillMaxWidth().cardOutline().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row {
                Text("Owes", Modifier.weight(1f), style = jakarta(12, FontWeight.Bold), color = AppColors.Ink600)
                Text("Is owed", style = jakarta(12, FontWeight.Bold), color = AppColors.Ink600)
            }
            state.balances.forEach { BalanceBar(it) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Settle up", style = jakarta(16, FontWeight.ExtraBold), color = AppColors.Ink900)
                Text(
                    when (state.settleCount) {
                        0 -> "Everyone is square"
                        1 -> "1 payment clears every balance in the group"
                        else -> "${state.settleCount} payments clear every balance in the group"
                    },
                    style = AppTypography.Caption,
                    color = AppColors.Ink600,
                )
            }
            state.settleRows.forEach { SettleRow(it, onAction) }
        }
    }
}

@Composable
private fun BalanceBar(row: BalanceRowUi) {
    val negative = row.netCents < 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Text(row.name, Modifier.width(56.dp), style = jakarta(14, FontWeight.Bold), color = AppColors.Ink900, maxLines = 1)
        BoxWithConstraints(Modifier.weight(1f).height(24.dp)) {
            val half = maxWidth / 2
            Box(Modifier.offset(x = half).width(1.dp).fillMaxHeight().background(AppColors.InputBorder))
            if (row.fraction > 0f) {
                val width = half * row.fraction
                Box(
                    Modifier
                        .offset(x = if (negative) half - width else half)
                        .width(width)
                        .fillMaxHeight()
                        .background(if (negative) AppColors.Red300 else AppColors.GreenBar, RoundedCornerShape(8.dp)),
                )
            }
        }
        Text(
            when {
                row.netCents == 0L -> "0,00"
                else -> MoneyFormat.signed(row.netCents, plusSign = true)
            },
            Modifier.width(82.dp),
            style = jakarta(14, FontWeight.ExtraBold),
            color = when {
                row.netCents == 0L -> AppColors.Ink600
                negative -> AppColors.Red700
                else -> AppColors.Green700
            },
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

@Composable
private fun SettleRow(row: SettleRowUi, onAction: (GroupAction) -> Unit) {
    val done = row.payState == PayState.Paid || row.markedPaid
    val (background, border) = when {
        done -> AppColors.Green50 to AppColors.Green200
        row.kind == SettleKind.FromMe -> AppColors.Red50 to AppColors.Red200
        row.kind == SettleKind.ToMe -> AppExtraColors.GreenTint to AppExtraColors.GreenTintBorder
        else -> AppColors.White to AppColors.LineSoft
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(18.dp))
            .border(1.dp, border, RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row {
                PersonAvatar(row.from.initial, row.from.paletteIndex, size = 32.dp, ringColor = AppColors.White)
                PersonAvatar(row.to.initial, row.to.paletteIndex, size = 32.dp, ringColor = AppColors.White, modifier = Modifier.offset(x = (-8).dp))
            }
            Text(row.label, Modifier.weight(1f), style = jakarta(14, FontWeight.SemiBold), color = AppColors.Ink900)
            Text(
                "${MoneyFormat.kr(row.amountCents)} kr",
                style = jakarta(15, FontWeight.ExtraBold),
                color = when {
                    done -> AppColors.Green700
                    row.kind == SettleKind.FromMe -> AppColors.Red700
                    row.kind == SettleKind.ToMe -> AppColors.Green700
                    else -> AppColors.Ink900
                },
                maxLines = 1,
            )
        }
        when (row.kind) {
            SettleKind.FromMe -> when (row.payState) {
                PayState.Idle -> AppButton(
                    text = "Pay ${MoneyFormat.kr(row.amountCents)} kr", onClick = { onAction(GroupAction.AskPay) },
                    modifier = Modifier.fillMaxWidth(), tone = ButtonTone.Pay, size = ButtonSize.Small,
                )
                PayState.Confirm -> ConfirmButtons(
                    onCancel = { onAction(GroupAction.CancelPay) },
                    onConfirm = { onAction(GroupAction.ConfirmPay) },
                    cancelTone = ButtonTone.OnTint,
                )
                PayState.Pending -> StatusStrip("Paying…", AppColors.White, AppColors.Ink600)
                PayState.Paid -> StatusStrip("Paid · settled", AppColors.Green100, AppColors.Green700)
            }
            SettleKind.ToMe -> if (row.markedPaid) {
                StatusStrip("Marked as paid", AppColors.Green100, AppColors.Green700)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(
                        text = if (row.reminded) "Reminder sent" else "Remind",
                        onClick = { onAction(GroupAction.Remind(row.debtorId)) },
                        modifier = Modifier.weight(1f),
                        tone = if (row.reminded) ButtonTone.Soft else ButtonTone.Primary,
                        size = ButtonSize.Small,
                        enabled = true,
                    )
                    AppButton(
                        text = "Mark as paid", onClick = { onAction(GroupAction.MarkPaid(row.debtorId)) },
                        modifier = Modifier.weight(1f), tone = ButtonTone.GreenOutline, size = ButtonSize.Small,
                    )
                }
            }
            SettleKind.Others -> Unit
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Members tab
// ---------------------------------------------------------------------------------------------

@Composable
private fun MembersTab(members: List<MemberRowUi>, onAction: (GroupAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DashedAddButton("Add member", onClick = { onAction(GroupAction.OpenAddMember) })
        members.forEach { MemberRow(it, onAction) }
    }
}

@Composable
internal fun DashedAddButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Int = 52) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .dashedBorder(AppColors.Green200, 18.dp)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(AppIcons.Plus, size = 18.dp, strokeWidth = 2.2f, tint = AppColors.Green700)
        Text(text, style = jakarta(15, FontWeight.Bold), color = AppColors.Green700)
    }
}

@Composable
private fun MemberRow(row: MemberRowUi, onAction: (GroupAction) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, if (row.warning != null) AppColors.Amber400 else AppColors.LineSoft, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (row.isInvite) {
                Box(
                    Modifier.size(44.dp).dashedBorder(AppColors.Amber500, 22.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(row.avatar.initial, style = jakarta(17, FontWeight.ExtraBold), color = AppColors.Amber800) }
            } else {
                PersonAvatar(row.avatar.initial, row.avatar.paletteIndex)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OneLine(row.name, AppTypography.ItemTitle, AppColors.Ink900)
                Text(row.note, style = AppTypography.Caption, color = AppColors.Ink600)
            }
            when {
                row.isInvite -> BalanceChip("Invited", ChipTone.Neutral)
                row.netCents < 0 -> BalanceChip(
                    (if (row.isMe) "You owe " else "Owes ") + MoneyFormat.kr(row.netCents), ChipTone.Owe,
                )
                row.netCents > 0 -> BalanceChip("Owed ${MoneyFormat.kr(row.netCents)}", ChipTone.Owed)
                else -> BalanceChip("Settled", ChipTone.Neutral)
            }
            if (row.canRemove) {
                Box(
                    Modifier
                        .size(40.dp, 44.dp)
                        .clickable(role = Role.Button) { onAction(GroupAction.AskRemoveMember(row.id)) }
                        .semantics { contentDescription = "Remove ${row.name}" },
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(AppIcons.Close, size = 18.dp, strokeWidth = 2f, tint = AppColors.Ink400)
                }
            }
        }
        row.warning?.let { warning ->
            Column(
                Modifier.fillMaxWidth().background(AppColors.Amber100, RoundedCornerShape(14.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(warning, style = jakarta(14, FontWeight.Medium, lineHeight = 20.sp), color = AppColors.Amber800)
                ConfirmButtons(
                    onCancel = { onAction(GroupAction.KeepMember) },
                    onConfirm = { onAction(GroupAction.ConfirmRemoveMember(row.id)) },
                    cancelText = "Keep ${row.name}",
                    confirmText = "Remove anyway",
                    cancelTone = ButtonTone.OnTint,
                    confirmTone = ButtonTone.WarningOutline,
                )
            }
        }
    }
}

@Composable
private fun MissingGroup(onBack: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().background(AppColors.White).statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) {
            AppIcon(AppIcons.ArrowLeft, size = 22.dp, strokeWidth = 2f, tint = AppColors.Ink900)
        }
        EmptyCard(title = "This group is gone", text = "It was deleted or you were removed from it.")
    }
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

private fun preview(groupId: String, local: GroupLocalState = GroupLocalState()): GroupUiState {
    val now = System.currentTimeMillis()
    return buildGroupUiState(DemoLedger.create(now), groupId, true, local, now)
}

@Preview(name = "Group – you owe", showBackground = true, widthDp = 412, heightDp = 1100)
@Composable
private fun GroupOwePreview() {
    GetYourMoneyUpNotYourFunnyUpTheme { GroupScreen(preview("ski"), {}, {}, {}, {}) }
}

@Preview(name = "Group – balances", showBackground = true, widthDp = 412, heightDp = 1100)
@Composable
private fun GroupBalancesPreview() {
    GetYourMoneyUpNotYourFunnyUpTheme { GroupScreen(preview("ski", GroupLocalState(tab = GroupTab.Balances)), {}, {}, {}, {}) }
}

@Preview(name = "Group – you're owed / members", showBackground = true, widthDp = 412, heightDp = 1100)
@Composable
private fun GroupMembersPreview() {
    GetYourMoneyUpNotYourFunnyUpTheme {
        GroupScreen(preview("kol", GroupLocalState(tab = GroupTab.Members, warnMemberId = "ida")), {}, {}, {}, {})
    }
}
