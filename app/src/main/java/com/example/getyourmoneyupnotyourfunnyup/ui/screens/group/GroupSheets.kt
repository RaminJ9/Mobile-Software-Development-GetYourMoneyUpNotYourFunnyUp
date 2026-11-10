package com.example.getyourmoneyupnotyourfunnyup.ui.screens.group

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.getyourmoneyupnotyourfunnyup.model.MoneyFormat
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppButton
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppIcon
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppIcons
import com.example.getyourmoneyupnotyourfunnyup.ui.components.ButtonSize
import com.example.getyourmoneyupnotyourfunnyup.ui.components.ButtonTone
import com.example.getyourmoneyupnotyourfunnyup.ui.components.ConfirmButtons
import com.example.getyourmoneyupnotyourfunnyup.ui.components.PersonAvatar
import com.example.getyourmoneyupnotyourfunnyup.ui.components.ReceiptThumb
import com.example.getyourmoneyupnotyourfunnyup.ui.components.SyncPill
import com.example.getyourmoneyupnotyourfunnyup.ui.components.cardOutline
import com.example.getyourmoneyupnotyourfunnyup.ui.screens.groups.MemberSearch
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppExtraColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppShapes
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppTypography
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.jakarta
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.AddMemberUi
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ExpenseSheetUi
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.GroupAction

@Composable
private fun SheetHandle() {
    Box(
        Modifier
            .padding(top = 10.dp, bottom = 6.dp)
            .size(40.dp, 5.dp)
            .background(AppColors.InputBorder, RoundedCornerShape(3.dp)),
    )
}

/** View expense: breakdown, receipt, payer, and the owner-only "Remove" confirmation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailsSheet(sheet: ExpenseSheetUi, onAction: (GroupAction) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onAction(GroupAction.CloseExpense) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = AppShapes.BottomSheet,
        containerColor = AppColors.White,
        scrimColor = AppExtraColors.Scrim,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(sheet.title, style = jakarta(20, FontWeight.ExtraBold), color = AppColors.Ink900)
                    Text("Paid by ${sheet.payerName} · ${sheet.date}", style = jakarta(14, FontWeight.Medium), color = AppColors.Ink600)
                }
                IconButton(
                    onClick = { onAction(GroupAction.CloseExpense) },
                    modifier = Modifier.offset(x = 8.dp, y = (-4).dp).semantics { contentDescription = "Close" },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = AppColors.Surface),
                ) {
                    AppIcon(AppIcons.Close, size = 18.dp, strokeWidth = 2.2f, tint = AppColors.Ink900)
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${MoneyFormat.kr(sheet.amountCents)} kr",
                    Modifier.weight(1f),
                    style = jakarta(34, FontWeight.ExtraBold, -0.03f),
                    color = AppColors.Ink900,
                )
                SyncPill(sheet.syncStatus, Modifier.padding(bottom = 6.dp))
            }
            if (sheet.hasReceipt) {
                Row(
                    Modifier.fillMaxWidth().cardOutline(corner = 16.dp, color = AppColors.Line).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ReceiptThumb()
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Receipt photo", style = AppTypography.ItemTitle, color = AppColors.Ink900)
                        Text("Saved with the expense", style = AppTypography.Caption, color = AppColors.Ink600)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Split between ${sheet.splits.size}",
                    Modifier.padding(bottom = 4.dp),
                    style = jakarta(13, FontWeight.Bold),
                    color = AppColors.Ink600,
                )
                sheet.splits.forEach { split ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (split.isMe) AppColors.Green50 else AppColors.White, RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PersonAvatar(split.avatar.initial, split.avatar.paletteIndex, size = 36.dp)
                        Text(split.name, Modifier.weight(1f), style = jakarta(15, FontWeight.SemiBold), color = AppColors.Ink900)
                        Text("${MoneyFormat.kr(split.amountCents)} kr", style = jakarta(15, FontWeight.Bold), color = AppColors.Ink900)
                    }
                }
            }
            when {
                sheet.canRemove && !sheet.removeConfirm -> AppButton(
                    text = "Remove expense",
                    onClick = { onAction(GroupAction.AskRemoveExpense) },
                    modifier = Modifier.fillMaxWidth(),
                    tone = ButtonTone.DangerSecondary,
                    size = ButtonSize.Medium,
                    leadingIcon = AppIcons.Trash,
                )
                sheet.canRemove -> Column(
                    Modifier.fillMaxWidth().background(AppColors.SurfaceSoft, RoundedCornerShape(16.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Remove this expense? Balances will update for ${sheet.splits.size} people and they'll be notified.",
                        style = jakarta(14, FontWeight.Medium, lineHeight = 20.sp),
                        color = AppColors.Ink900,
                    )
                    ConfirmButtons(
                        onCancel = { onAction(GroupAction.CancelRemoveExpense) },
                        onConfirm = { onAction(GroupAction.ConfirmRemoveExpense) },
                        cancelText = "Keep it",
                        confirmText = "Remove",
                        confirmTone = ButtonTone.Dark,
                    )
                }
                else -> Text(
                    "Only ${sheet.payerName} can edit or remove this expense.",
                    Modifier.fillMaxWidth(),
                    style = AppTypography.Caption,
                    color = AppColors.Ink600,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Add member view (with the S-Error "User does not exist" state). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMemberSheet(state: AddMemberUi, onAction: (GroupAction) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onAction(GroupAction.CloseAddMember) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = AppShapes.BottomSheet,
        containerColor = AppColors.White,
        scrimColor = AppExtraColors.Scrim,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Add member", Modifier.weight(1f), style = jakarta(20, FontWeight.ExtraBold), color = AppColors.Ink900)
                IconButton(
                    onClick = { onAction(GroupAction.CloseAddMember) },
                    modifier = Modifier.semantics { contentDescription = "Close" },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = AppColors.Surface),
                ) {
                    AppIcon(AppIcons.Close, size = 18.dp, strokeWidth = 2.2f, tint = AppColors.Ink900)
                }
            }
            MemberSearch(
                query = state.query,
                onQueryChange = { onAction(GroupAction.MemberQueryChanged(it)) },
                onSubmit = { onAction(GroupAction.SubmitMemberQuery) },
                notFoundContact = state.notFoundContact,
                onInvite = { onAction(GroupAction.InviteContact) },
                suggestions = state.suggestions,
                onAddSuggestion = { onAction(GroupAction.AddSuggested(it)) },
            )
        }
    }
}
