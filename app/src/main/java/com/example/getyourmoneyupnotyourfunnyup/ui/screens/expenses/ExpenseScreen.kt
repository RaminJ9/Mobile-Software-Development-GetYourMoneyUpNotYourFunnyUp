package com.example.getyourmoneyupnotyourfunnyup.ui.screens.expenses

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.getyourmoneyupnotyourfunnyup.model.Expense
import com.example.getyourmoneyupnotyourfunnyup.model.ExpenseSplit
import com.example.getyourmoneyupnotyourfunnyup.model.Settlement
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.ui.components.SyncFeedback
import com.example.getyourmoneyupnotyourfunnyup.ui.components.SyncStatusStrip
import com.example.getyourmoneyupnotyourfunnyup.ui.components.rememberConnectionState
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.SyncPresentation
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.InlineActionState
import com.example.getyourmoneyupnotyourfunnyup.data.sync.ConnectionState
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.*
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ExpenseUiState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface LedgerRow {
    val key: String
    val createdAt: Long
    data class ExpenseRow(val value: Expense) : LedgerRow {
        override val key = "expense-${value.id}"
        override val createdAt = value.createdAt
    }
    data class PaymentRow(val value: Settlement) : LedgerRow {
        override val key = "payment-${value.id}"
        override val createdAt = value.createdAt
    }
}

@Composable
fun ExpenseRoute(viewModel: ExpenseViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connection = rememberConnectionState()
    ExpenseScreen(state, viewModel::open, viewModel::dismiss, viewModel::retry, modifier, connection, viewModel::askRemoval, viewModel::keepExpense, viewModel::confirmRemoval, viewModel::loadReceipt)
}

/** Group ledger and S9 details; expense entry and global Activity are separate flows. */
@Composable
fun ExpenseScreen(
    state: ExpenseUiState,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    connection: ConnectionState = ConnectionState.Unknown,
    onAskRemoval: () -> Unit = {}, onKeepExpense: () -> Unit = {}, onRemove: () -> Unit = {},
    loadReceipt: (suspend (Expense) -> String?)? = null
) {
    when (state) {
        ExpenseUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppColors.Green700) }
        is ExpenseUiState.Error -> Column(modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.Space12)) {
            Text(state.message, style = AppTypography.Body, color = AppColors.Ink600)
            if (state.canRetry) Button(onClick = onRetry, shape = AppShapes.InlineButton,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green700, contentColor = AppColors.White),
                modifier = Modifier.heightIn(min = AppTouchTargets.MinTouchTarget)) { Text("Try again") }
        }
        is ExpenseUiState.UnsupportedCurrency -> Text("Expense details currently support DKK. This group uses ${state.currency}.",
            modifier, style = AppTypography.Body, color = AppColors.Ink600)
        is ExpenseUiState.Success -> {
            val entries = remember(state.expenses, state.settlements) {
                (state.expenses.map { LedgerRow.ExpenseRow(it) } + state.settlements.map { LedgerRow.PaymentRow(it) })
                    .sortedWith(compareByDescending<LedgerRow> { it.createdAt }.thenBy { it.key })
            }
            val statuses = remember(state.expenses, state.settlements) { state.expenses.map { it.syncStatus } + state.settlements.map { it.syncStatus } }
            val sync = SyncPresentation(connection = connection,
                pendingCount = statuses.count { it != SyncStatus.SYNCED },
                failureMessage = if (SyncStatus.FAILED in statuses) "Some saved activity couldn't sync yet. Showing saved data." else null)
            LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(AppSpacing.Space12)) {
                item("heading") { Text("Group activity", style = AppTypography.Section, color = AppColors.Ink900) }
                if (sync.hasFeedback) item("sync") { SyncFeedback(sync) }
                state.notice?.let { message -> item("notice") { Text(message, style = AppTypography.Caption, color = AppColors.Ink600) } }
                if (entries.isEmpty()) item("empty") { Text("No expenses yet.", style = AppTypography.Body, color = AppColors.Ink600) }
                items(entries, key = { it.key }) { entry ->
                    when (entry) {
                    is LedgerRow.PaymentRow -> PaymentHistoryRow(entry.value, state)
                    is LedgerRow.ExpenseRow -> {
                    val expense = entry.value
                    Surface(onClick = { onOpen(expense.id) }, shape = AppShapes.ListCard,
                        modifier = Modifier.fillMaxWidth().testTag("expense-${expense.id}"),
                        color = AppColors.White, border = BorderStroke(1.dp, AppColors.Line)) {
                        Column(Modifier.padding(AppSpacing.Space16), verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
                            Text(expense.title, style = AppTypography.ItemTitle, color = AppColors.Ink900)
                            Text("Paid by ${state.name(expense.payerId)}", style = AppTypography.Caption, color = AppColors.Ink600)
                            Text(expenseDate(expense.createdAt), style = AppTypography.Caption, color = AppColors.Ink600)
                            Text(formatDanishAmountFromCents(expense.amountCents), style = AppTypography.Section, color = AppColors.Ink900)
                            val share = expense.splits.firstOrNull { it.userId == state.accountId }
                            Text(if (share == null) "Not included in this expense" else "Your share: ${formatDanishAmountFromCents(share.amountOwedCents)}",
                                style = AppTypography.Caption, color = AppColors.Ink600)
                            if (expense.syncStatus != SyncStatus.SYNCED) SyncStatusStrip(expense.syncStatus)
                        }
                    }
                    }
                    }
                }
            }
            state.selected?.let { ExpenseDetailsSheet(it, state, onDismiss, onAskRemoval, onKeepExpense, onRemove, loadReceipt) }
        }
    }
}

@Composable
private fun PaymentHistoryRow(payment: Settlement, state: ExpenseUiState.Success) {
    Surface(shape = AppShapes.ListCard, color = AppColors.Green50, border = BorderStroke(1.dp, AppColors.Line),
        modifier = Modifier.fillMaxWidth().testTag("payment-${payment.id}")) {
        Column(Modifier.padding(AppSpacing.Space16), verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
            Text("Virtual payment", style = AppTypography.ItemTitle, color = AppColors.Ink900)
            Text("${state.name(payment.debtorId)} paid ${state.name(payment.creditorId)}", style = AppTypography.Body, color = AppColors.Ink900)
            Text(expenseDate(payment.createdAt), style = AppTypography.Caption, color = AppColors.Ink600)
            Text(formatDanishAmountFromCents(payment.amountCents), style = AppTypography.Section, color = AppColors.Green700)
            Text("Virtual payment recorded", style = AppTypography.Caption, color = AppColors.Green700)
            SyncStatusStrip(payment.syncStatus)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseDetailsSheet(expense: Expense, state: ExpenseUiState.Success, onDismiss: () -> Unit,
    onAskRemoval: () -> Unit = {}, onKeepExpense: () -> Unit = {}, onRemove: () -> Unit = {}, loadReceipt: (suspend (Expense) -> String?)? = null) {
    val removing = state.removalAction == InlineActionState.Working
    ModalBottomSheet(onDismissRequest = onDismiss, shape = AppShapes.BottomSheet,
        containerColor = AppColors.White, contentColor = AppColors.Ink900,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !removing || it != SheetValue.Hidden })) {
        LazyColumn(Modifier.fillMaxWidth().testTag("expense-details"),
            contentPadding = PaddingValues(start = AppSpacing.Space16, end = AppSpacing.Space16, bottom = AppSpacing.Space24),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Space16)) {
            item("header") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Expense details", Modifier.weight(1f), style = AppTypography.TopBarTitle)
                    TextButton(onClick = onDismiss, enabled = !removing, modifier = Modifier.heightIn(min = AppTouchTargets.MinTouchTarget)) { Text("Close", color = AppColors.Green700) }
                }
            }
            item("summary") {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
                    Text(expense.title, style = AppTypography.Section)
                    Text("Paid by ${state.name(expense.payerId)}", style = AppTypography.Caption, color = AppColors.Ink600)
                    Text(expenseDate(expense.createdAt), style = AppTypography.Caption, color = AppColors.Ink600)
                    Text(formatDanishAmountFromCents(expense.amountCents), style = AppTypography.AmountLarge)
                    SyncStatusStrip(expense.syncStatus)
                }
            }
            item("receipt") { Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) { ReceiptImage(expense.receiptLocalUri, expense.receiptRemotePath, loadReceipt?.let { loader -> { loader(expense) } }) } }
            item("split-heading") { Text("Member shares", style = AppTypography.Section) }
            items(expense.splits, key = { it.userId }) { split ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space12)) {
                    Text(state.name(split.userId) + if (split.userId == state.accountId) " (You)" else "",
                        Modifier.weight(1f), style = AppTypography.Body)
                    Text(formatDanishAmountFromCents(split.amountOwedCents), style = AppTypography.CaptionStrong)
                }
            }
            if (state.removalAvailable && (expense.createdBy == state.accountId || expense.payerId == state.accountId)) item("remove") {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
                    when (val action = state.removalAction) {
                        InlineActionState.Working -> Text("Removing...", style = AppTypography.Caption, color = AppColors.Ink600)
                        InlineActionState.Confirming -> {
                            Text("Remove this expense? Balances will update. Recorded virtual payments will remain.", style = AppTypography.Caption, color = AppColors.Ink600)
                            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
                                OutlinedButton(onClick = onKeepExpense, modifier = Modifier.heightIn(min = AppTouchTargets.MinTouchTarget), shape = AppShapes.InlineButton) { Text("Keep it", color = AppColors.Green700) }
                                Button(onClick = onRemove, modifier = Modifier.heightIn(min = AppTouchTargets.MinTouchTarget), shape = AppShapes.InlineButton,
                                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Ink900, contentColor = AppColors.White)) { Text("Remove") }
                            }
                        }
                        else -> {
                            if (action is InlineActionState.Failed) Text(action.message, style = AppTypography.Caption, color = AppColors.Ink600)
                            TextButton(onClick = onAskRemoval, modifier = Modifier.heightIn(min = AppTouchTargets.MinTouchTarget)) { Text("Remove expense", color = AppColors.Ink600) }
                        }
                    }
                }
            }
            if (expense.createdBy != state.accountId && expense.payerId != state.accountId) item("ownership") {
                Text("Only ${state.name(expense.payerId)} can edit or remove this expense.", style = AppTypography.Caption, color = AppColors.Ink600)
            }
        }
    }
}

private fun expenseDate(timestamp: Long): String = SimpleDateFormat("dd. MMM yyyy, HH:mm", Locale.forLanguageTag("da-DK")).format(Date(timestamp))

@Preview(showBackground = true, name = "Expenses light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Expenses dark")
@Composable
private fun ExpensePreview() {
    GetYourMoneyUpNotYourFunnyUpTheme {
        ExpenseScreen(ExpenseUiState.Success("you", listOf(Expense("e", "g", "Dinner", 10000,
            currency = "DKK", payerId = "ida", createdBy = "ida", createdAt = 1700000000000,
            splits = listOf(ExpenseSplit("s", "e", "you", 10000)))), mapOf("you" to "Ramin", "ida" to "Ida")), {}, {}, {})
    }
}

@Preview(showBackground = true, name = "Expense details light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Expense details dark")
@Composable
private fun ExpenseDetailsPreview() {
    val expense = Expense("e", "g", "Dinner", 10000, currency = "DKK", payerId = "ida", createdBy = "ida",
        createdAt = 1700000000000, splits = listOf(ExpenseSplit("s", "e", "you", 10000)))
    GetYourMoneyUpNotYourFunnyUpTheme {
        ExpenseDetailsSheet(expense, ExpenseUiState.Success("you", listOf(expense), mapOf("you" to "Ramin", "ida" to "Ida")), {})
    }
}
