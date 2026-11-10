package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.getyourmoneyupnotyourfunnyup.data.repository.LedgerRepository
import com.example.getyourmoneyupnotyourfunnyup.model.DateLabels
import com.example.getyourmoneyupnotyourfunnyup.model.DietRule
import com.example.getyourmoneyupnotyourfunnyup.model.LedgerSnapshot
import com.example.getyourmoneyupnotyourfunnyup.model.MoneyFormat
import com.example.getyourmoneyupnotyourfunnyup.model.Person
import com.example.getyourmoneyupnotyourfunnyup.model.SettlementStatus
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.activeExpenses
import com.example.getyourmoneyupnotyourfunnyup.model.groupSettlements
import com.example.getyourmoneyupnotyourfunnyup.model.myGroups
import com.example.getyourmoneyupnotyourfunnyup.model.netBalances
import com.example.getyourmoneyupnotyourfunnyup.model.settlePlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlin.math.abs

enum class GroupTab(val label: String) { Activity("Activity"), Balances("Balances"), Members("Members") }

@Immutable
data class AvatarUi(val initial: String, val paletteIndex: Int)

/** The coloured card under the group header. */
sealed interface GroupHero {
    data class Owe(val toName: String, val amountCents: Long, val state: PayState) : GroupHero
    data class Owed(val amountCents: Long, val fromText: String, val allReminded: Boolean) : GroupHero
    data class Settled(val subtitle: String) : GroupHero
}

enum class LedgerRowKind { Settle, Receipt, Plain }

@Immutable
data class LedgerRowUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val amountCents: Long,
    val kind: LedgerRowKind,
    /** "You: 115,00", "to you", "from you" or empty. */
    val shareLabel: String,
    /** Sync state of an expense (null for payments). */
    val syncStatus: SyncStatus?,
    val opensDetails: Boolean,
)

@Immutable
data class BalanceRowUi(val name: String, val netCents: Long, val fraction: Float)

enum class SettleKind { FromMe, ToMe, Others }

@Immutable
data class SettleRowUi(
    val key: String,
    val debtorId: String,
    val from: AvatarUi,
    val to: AvatarUi,
    val label: String,
    val amountCents: Long,
    val kind: SettleKind,
    /** For [SettleKind.FromMe]: the pay flow. */
    val payState: PayState = PayState.Idle,
    /** For [SettleKind.ToMe]. */
    val reminded: Boolean = false,
    val markedPaid: Boolean = false,
)

@Immutable
data class MemberRowUi(
    val id: String,
    val name: String,
    val avatar: AvatarUi,
    val note: String,
    val netCents: Long,
    val isMe: Boolean,
    val canRemove: Boolean,
    val warning: String?,
    val isInvite: Boolean = false,
)

@Immutable
data class SplitRowUi(val name: String, val avatar: AvatarUi, val amountCents: Long, val isMe: Boolean)

@Immutable
data class ExpenseSheetUi(
    val id: String,
    val title: String,
    val payerName: String,
    val date: String,
    val amountCents: Long,
    val syncStatus: SyncStatus,
    val hasReceipt: Boolean,
    val splits: List<SplitRowUi>,
    val canRemove: Boolean,
    val removeConfirm: Boolean,
)

@Immutable
data class PersonSuggestionUi(val id: String, val name: String, val contact: String, val avatar: AvatarUi)

@Immutable
data class AddMemberUi(
    val query: String,
    val notFoundContact: String?,
    val suggestions: List<PersonSuggestionUi>,
)

sealed interface GroupUiState {
    data object Missing : GroupUiState

    @Immutable
    data class Content(
        val offlineMessage: String?,
        val name: String,
        val avatar: AvatarUi,
        val memberCount: Int,
        val createdLabel: String,
        val description: String?,
        val memberStack: List<AvatarUi>,
        val isFavourite: Boolean,
        val canDelete: Boolean,
        val hero: GroupHero,
        val tab: GroupTab,
        val ledgerRows: List<LedgerRowUi>,
        val expenseCount: Int,
        val balances: List<BalanceRowUi>,
        val settleRows: List<SettleRowUi>,
        val settleCount: Int,
        val members: List<MemberRowUi>,
        val sheet: ExpenseSheetUi?,
        val addMember: AddMemberUi?,
        val menuOpen: Boolean,
        val confirmDelete: Boolean,
    ) : GroupUiState
}

sealed interface GroupAction {
    data class SelectTab(val tab: GroupTab) : GroupAction
    data object AskPay : GroupAction
    data object CancelPay : GroupAction
    data object ConfirmPay : GroupAction
    data object RemindAll : GroupAction
    data class Remind(val debtorId: String) : GroupAction
    data class MarkPaid(val debtorId: String) : GroupAction
    data class OpenExpense(val expenseId: String) : GroupAction
    data object CloseExpense : GroupAction
    data object AskRemoveExpense : GroupAction
    data object CancelRemoveExpense : GroupAction
    data object ConfirmRemoveExpense : GroupAction
    data class RetrySync(val expenseId: String) : GroupAction
    data class AskRemoveMember(val memberId: String) : GroupAction
    data object KeepMember : GroupAction
    data class ConfirmRemoveMember(val memberId: String) : GroupAction
    data object OpenAddMember : GroupAction
    data object CloseAddMember : GroupAction
    data class MemberQueryChanged(val query: String) : GroupAction
    data object SubmitMemberQuery : GroupAction
    data class AddSuggested(val personId: String) : GroupAction
    data object InviteContact : GroupAction
    data object ToggleMenu : GroupAction
    data object ToggleFavourite : GroupAction
    data object AskDelete : GroupAction
    data object CancelDelete : GroupAction
}

data class GroupLocalState(
    val tab: GroupTab = GroupTab.Activity,
    val sheetExpenseId: String? = null,
    val removeConfirm: Boolean = false,
    val warnMemberId: String? = null,
    val payConfirm: Boolean = false,
    /** Payments the user made from this screen: settlement id → creditor id. */
    val myPayments: Map<String, String> = emptyMap(),
    /** "Mark as paid" taps this visit: debtor id → amount. */
    val markedPaid: Map<String, Long> = emptyMap(),
    val addMemberOpen: Boolean = false,
    val memberQuery: String = "",
    val memberNotFound: String? = null,
    val menuOpen: Boolean = false,
    val confirmDelete: Boolean = false,
)

private fun Person.avatar(meId: String) = AvatarUi(initial, if (id == meId) -1 else paletteIndex)

private fun LedgerSnapshot.displayName(id: String) = if (id == currentUserId) "You" else person(id).name

fun buildGroupUiState(
    snapshot: LedgerSnapshot,
    groupId: String,
    online: Boolean,
    local: GroupLocalState,
    now: Long,
): GroupUiState {
    val group = snapshot.group(groupId) ?: return GroupUiState.Missing
    val me = snapshot.currentUserId
    val memberIds = group.members.map { it.userId }
    val net = snapshot.netBalances(groupId)
    val plan = snapshot.settlePlan(groupId)
    val settlements = snapshot.groupSettlements(groupId)

    // My payments made on this screen and their progress.
    val myPayments = local.myPayments.mapNotNull { (settlementId, creditorId) ->
        settlements.firstOrNull { it.id == settlementId }?.let { Triple(it, creditorId, it.status == SettlementStatus.COMPLETED) }
    }
    val pendingPayment = myPayments.firstOrNull { !it.third }
    val myDebts = plan.filter { it.debtorId == me }
    val owedToMe = plan.filter { it.creditorId == me }
    val reminded = snapshot.reminders.filter { it.groupId == groupId && it.fromId == me }.map { it.toId }.toSet()

    val hero: GroupHero = when {
        pendingPayment != null -> GroupHero.Owe(
            snapshot.person(pendingPayment.second).name, pendingPayment.first.amountCents, PayState.Pending,
        )
        myDebts.isNotEmpty() -> GroupHero.Owe(
            toName = if (myDebts.size == 1) snapshot.person(myDebts.first().creditorId).name else "${myDebts.size} people",
            amountCents = myDebts.sumOf { it.amountCents },
            state = if (local.payConfirm) PayState.Confirm else PayState.Idle,
        )
        owedToMe.isNotEmpty() -> GroupHero.Owed(
            amountCents = owedToMe.sumOf { it.amountCents },
            fromText = owedToMe.joinToString(" · ") { snapshot.person(it.debtorId).name + " " + MoneyFormat.kr(it.amountCents) },
            allReminded = owedToMe.all { it.debtorId in reminded },
        )
        myPayments.isNotEmpty() -> {
            val last = myPayments.last()
            GroupHero.Settled("Paid ${snapshot.person(last.second).name} ${MoneyFormat.kr(last.first.amountCents)} kr just now")
        }
        else -> GroupHero.Settled("Nobody owes you anything here")
    }

    // Activity: expenses and payments, newest first.
    val expenses = snapshot.activeExpenses(groupId)
    val expenseRows = expenses.map { e ->
        val mine = e.splits.firstOrNull { it.userId == me }?.amountOwedCents ?: 0L
        e.createdAt to LedgerRowUi(
            id = e.id, title = e.title,
            subtitle = "Paid by ${snapshot.displayName(e.payerId)} · ${DateLabels.relativeDay(e.createdAt, now)}",
            amountCents = e.amountCents,
            kind = if (e.receiptLocalUri != null) LedgerRowKind.Receipt else LedgerRowKind.Plain,
            shareLabel = "You: ${MoneyFormat.kr(mine)}",
            syncStatus = e.syncStatus, opensDetails = true,
        )
    }
    val paymentRows = settlements.map { s ->
        val title = when {
            s.debtorId == me -> "You paid ${snapshot.person(s.creditorId).name}"
            s.creditorId == me -> "${snapshot.person(s.debtorId).name} paid you"
            else -> "${snapshot.person(s.debtorId).name} paid ${snapshot.person(s.creditorId).name}"
        }
        val status = if (s.status == SettlementStatus.COMPLETED) "Settled" else "Paying…"
        s.createdAt to LedgerRowUi(
            id = s.id, title = title, subtitle = "$status · ${DateLabels.relativeDay(s.createdAt, now)}",
            amountCents = s.amountCents, kind = LedgerRowKind.Settle,
            shareLabel = when (me) { s.creditorId -> "to you"; s.debtorId -> "from you"; else -> "" },
            syncStatus = null, opensDetails = false,
        )
    }
    val ledgerRows = (expenseRows + paymentRows).sortedByDescending { it.first }.map { it.second }

    // Balances chart: members plus anyone who still has an open balance.
    val balanceIds = memberIds + net.filter { it.value != 0L && it.key !in memberIds }.keys
    val maxAbs = balanceIds.maxOfOrNull { abs(net[it] ?: 0L) }?.coerceAtLeast(1L) ?: 1L
    val balances = balanceIds.map { id ->
        val value = net[id] ?: 0L
        val fraction = if (value == 0L) 0f else maxOf(abs(value).toFloat() / maxAbs, 0.06f)
        BalanceRowUi(snapshot.displayName(id), value, fraction)
    }

    // Settle up: the plan, plus payments made this visit (shown as paid).
    val planRows = plan.map { t ->
        val kind = when (me) { t.debtorId -> SettleKind.FromMe; t.creditorId -> SettleKind.ToMe; else -> SettleKind.Others }
        SettleRowUi(
            key = "${t.debtorId}>${t.creditorId}", debtorId = t.debtorId,
            from = snapshot.person(t.debtorId).avatar(me), to = snapshot.person(t.creditorId).avatar(me),
            label = "${snapshot.displayName(t.debtorId)} → ${if (t.creditorId == me) "you" else snapshot.person(t.creditorId).name}",
            amountCents = t.amountCents, kind = kind,
            payState = when {
                kind != SettleKind.FromMe -> PayState.Idle
                pendingPayment != null -> PayState.Pending
                local.payConfirm -> PayState.Confirm
                else -> PayState.Idle
            },
            reminded = t.debtorId in reminded,
        )
    }.filterNot { it.kind == SettleKind.FromMe && pendingPayment != null }
    val paidRows = myPayments.map { (settlement, creditorId, done) ->
        SettleRowUi(
            key = "paid:${settlement.id}", debtorId = me,
            from = snapshot.me.avatar(me), to = snapshot.person(creditorId).avatar(me),
            label = "You → ${snapshot.person(creditorId).name}", amountCents = settlement.amountCents,
            kind = SettleKind.FromMe, payState = if (done) PayState.Paid else PayState.Pending,
        )
    }
    val markedRows = local.markedPaid.map { (debtorId, amount) ->
        SettleRowUi(
            key = "marked:$debtorId", debtorId = debtorId,
            from = snapshot.person(debtorId).avatar(me), to = snapshot.me.avatar(me),
            label = "${snapshot.person(debtorId).name} → you", amountCents = amount,
            kind = SettleKind.ToMe, markedPaid = true,
        )
    }
    val settleRows = paidRows + planRows + markedRows

    // Members.
    val organiser = group.createdBy
    val members = memberIds.map { id ->
        val person = snapshot.person(id)
        val value = net[id] ?: 0L
        val diet = when {
            DietRule.NO_ALCOHOL in person.dietRules -> "no alcohol"
            DietRule.NO_MEAT in person.dietRules -> "vegetarian"
            else -> null
        }
        val note = when {
            id == me -> "That’s you"
            id == organiser && diet != null -> "Organiser · $diet"
            id == organiser -> "Organiser"
            diet != null -> diet.replaceFirstChar { it.uppercase() }
            else -> "Member"
        }
        val warning = if (local.warnMemberId == id) {
            val balance = when {
                value < 0 -> " still owes ${MoneyFormat.kr(value)} kr"
                value > 0 -> " is still owed ${MoneyFormat.kr(value)} kr"
                else -> " has no open balance"
            }
            person.name + balance + ". Removing them can leave the group unbalanced."
        } else null
        MemberRowUi(
            id = id, name = snapshot.displayName(id), avatar = person.avatar(me), note = note, netCents = value,
            isMe = id == me, canRemove = id != me, warning = warning,
        )
    } + snapshot.invites.filter { it.groupId == groupId }.map { invite ->
        MemberRowUi(
            id = "invite:${invite.contact}", name = invite.contact, avatar = AvatarUi(invite.contact.take(1).uppercase(), 8),
            note = "Invited · joins when they sign up", netCents = 0, isMe = false, canRemove = false, warning = null,
            isInvite = true,
        )
    }

    // Expense details sheet.
    val sheet = local.sheetExpenseId?.let { id -> expenses.firstOrNull { it.id == id } }?.let { e ->
        ExpenseSheetUi(
            id = e.id, title = e.title, payerName = snapshot.displayName(e.payerId),
            date = DateLabels.relativeDay(e.createdAt, now), amountCents = e.amountCents, syncStatus = e.syncStatus,
            hasReceipt = e.receiptLocalUri != null,
            splits = e.splits.map { split ->
                SplitRowUi(snapshot.displayName(split.userId), snapshot.person(split.userId).avatar(me), split.amountOwedCents, split.userId == me)
            },
            canRemove = e.payerId == me, removeConfirm = local.removeConfirm,
        )
    }

    // Add member: people the user has split with who are not in this group yet.
    val addMember = if (local.addMemberOpen) {
        val shared = snapshot.myGroups().flatMap { g -> g.members.map { it.userId } }
            .filter { it != me && it !in memberIds }
            .groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { snapshot.person(it.key).name })
            .take(4)
            .map { snapshot.person(it.key) }
        AddMemberUi(
            query = local.memberQuery,
            notFoundContact = local.memberNotFound,
            suggestions = shared.map { PersonSuggestionUi(it.id, it.name, it.contact, it.avatar(me)) },
        )
    } else null

    return GroupUiState.Content(
        offlineMessage = if (online) null else offlineMessage(snapshot.lastSyncedAt, "New expenses sync when you're back."),
        name = group.name,
        avatar = AvatarUi(group.name.take(1).uppercase(), group.colorIndex),
        memberCount = memberIds.size,
        createdLabel = if (organiser == me) "Created by you" else "Created by ${snapshot.person(organiser).name}",
        description = group.description,
        memberStack = memberIds.take(3).map { snapshot.person(it).avatar(me) },
        isFavourite = groupId in snapshot.favouriteGroupIds,
        canDelete = organiser == me,
        hero = hero,
        tab = local.tab,
        ledgerRows = ledgerRows,
        expenseCount = expenses.size,
        balances = balances,
        settleRows = settleRows,
        settleCount = plan.size,
        members = members,
        sheet = sheet,
        addMember = addMember,
        menuOpen = local.menuOpen,
        confirmDelete = local.confirmDelete,
    )
}

/** View group: balances, ledger, members, expense details. */
class GroupViewModel(
    private val ledger: LedgerRepository,
    private val groupId: String,
) : ViewModel() {

    private val local = MutableStateFlow(GroupLocalState())

    val uiState: StateFlow<GroupUiState> =
        combine(ledger.snapshot, ledger.isOnline, local) { snapshot, online, localState ->
            buildGroupUiState(snapshot, groupId, online, localState, ledger.now())
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            buildGroupUiState(ledger.snapshot.value, groupId, ledger.isOnline.value, local.value, ledger.now()),
        )

    fun onAction(action: GroupAction) {
        val snapshot = ledger.snapshot.value
        val me = snapshot.currentUserId
        when (action) {
            is GroupAction.SelectTab -> local.update { it.copy(tab = action.tab, warnMemberId = null) }
            GroupAction.AskPay -> local.update { it.copy(payConfirm = true) }
            GroupAction.CancelPay -> local.update { it.copy(payConfirm = false) }
            GroupAction.ConfirmPay -> {
                val debts = snapshot.settlePlan(groupId).filter { it.debtorId == me }
                val ids = debts.associate { ledger.payDebt(groupId, it.creditorId, it.amountCents) to it.creditorId }
                local.update { it.copy(payConfirm = false, myPayments = it.myPayments + ids) }
            }
            GroupAction.RemindAll -> snapshot.settlePlan(groupId).filter { it.creditorId == me }.forEach { transfer ->
                val alreadySent = snapshot.reminders.any { it.groupId == groupId && it.fromId == me && it.toId == transfer.debtorId }
                if (!alreadySent) ledger.sendReminder(groupId, transfer.debtorId)
            }
            is GroupAction.Remind -> ledger.sendReminder(groupId, action.debtorId)
            is GroupAction.MarkPaid -> {
                val transfer = snapshot.settlePlan(groupId).firstOrNull { it.debtorId == action.debtorId && it.creditorId == me }
                if (transfer != null) {
                    ledger.markAsPaid(groupId, action.debtorId, transfer.amountCents)
                    local.update { it.copy(markedPaid = it.markedPaid + (action.debtorId to transfer.amountCents)) }
                }
            }
            is GroupAction.OpenExpense -> local.update { it.copy(sheetExpenseId = action.expenseId, removeConfirm = false) }
            GroupAction.CloseExpense -> local.update { it.copy(sheetExpenseId = null, removeConfirm = false) }
            GroupAction.AskRemoveExpense -> local.update { it.copy(removeConfirm = true) }
            GroupAction.CancelRemoveExpense -> local.update { it.copy(removeConfirm = false) }
            GroupAction.ConfirmRemoveExpense -> {
                local.value.sheetExpenseId?.let(ledger::removeExpense)
                local.update { it.copy(sheetExpenseId = null, removeConfirm = false) }
            }
            is GroupAction.RetrySync -> ledger.retrySync(action.expenseId)
            is GroupAction.AskRemoveMember -> {
                val balance = snapshot.netBalances(groupId)[action.memberId] ?: 0L
                if (balance == 0L) ledger.removeMember(groupId, action.memberId)
                else local.update { it.copy(warnMemberId = action.memberId) }
            }
            GroupAction.KeepMember -> local.update { it.copy(warnMemberId = null) }
            is GroupAction.ConfirmRemoveMember -> {
                ledger.removeMember(groupId, action.memberId)
                local.update { it.copy(warnMemberId = null) }
            }
            GroupAction.OpenAddMember -> local.update { it.copy(addMemberOpen = true, memberQuery = "", memberNotFound = null) }
            GroupAction.CloseAddMember -> local.update { it.copy(addMemberOpen = false, memberQuery = "", memberNotFound = null) }
            is GroupAction.MemberQueryChanged -> local.update { it.copy(memberQuery = action.query, memberNotFound = null) }
            GroupAction.SubmitMemberQuery -> {
                val query = local.value.memberQuery.trim()
                if (query.isEmpty()) return
                val person = ledger.findPerson(query)
                if (person != null) {
                    ledger.addMember(groupId, person.id)
                    local.update { it.copy(addMemberOpen = false, memberQuery = "", tab = GroupTab.Members) }
                } else {
                    local.update { it.copy(memberNotFound = query) }
                }
            }
            is GroupAction.AddSuggested -> {
                ledger.addMember(groupId, action.personId)
                local.update { it.copy(addMemberOpen = false, memberQuery = "", tab = GroupTab.Members) }
            }
            GroupAction.InviteContact -> {
                local.value.memberNotFound?.let { ledger.invite(groupId, it) }
                local.update { it.copy(addMemberOpen = false, memberQuery = "", memberNotFound = null, tab = GroupTab.Members) }
            }
            GroupAction.ToggleMenu -> local.update { it.copy(menuOpen = !it.menuOpen) }
            GroupAction.ToggleFavourite -> {
                ledger.toggleFavourite(groupId)
                local.update { it.copy(menuOpen = false) }
            }
            GroupAction.AskDelete -> local.update { it.copy(menuOpen = false, confirmDelete = true) }
            GroupAction.CancelDelete -> local.update { it.copy(confirmDelete = false) }
        }
    }

    /** Deletes the group; the screen navigates back afterwards. */
    fun deleteGroup() {
        local.update { it.copy(confirmDelete = false) }
        ledger.deleteGroup(groupId)
    }
}
