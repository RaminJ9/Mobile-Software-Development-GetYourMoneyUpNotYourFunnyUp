package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.getyourmoneyupnotyourfunnyup.data.demo.DemoLedger
import com.example.getyourmoneyupnotyourfunnyup.data.repository.ExpenseDraft
import com.example.getyourmoneyupnotyourfunnyup.data.repository.LedgerRepository
import com.example.getyourmoneyupnotyourfunnyup.model.DietRule
import com.example.getyourmoneyupnotyourfunnyup.model.LedgerSnapshot
import com.example.getyourmoneyupnotyourfunnyup.model.MoneyFormat
import com.example.getyourmoneyupnotyourfunnyup.model.Person
import com.example.getyourmoneyupnotyourfunnyup.model.ReceiptItem
import com.example.getyourmoneyupnotyourfunnyup.model.ReceiptItemKind
import com.example.getyourmoneyupnotyourfunnyup.model.ReceiptSplitter
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.lastActivityAt
import com.example.getyourmoneyupnotyourfunnyup.model.myGroups
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Steps of the "Add expense" flow (Scan receipt in the hi-fi). */
enum class ScanStep { Starting, Permission, Camera, Reading, Review, Split, Saved }

@Immutable
data class ScanGroupUi(val id: String, val name: String, val avatar: AvatarUi)

@Immutable
data class PayerUi(val id: String, val name: String, val avatar: AvatarUi, val selected: Boolean)

enum class ItemTag(val label: String) {
    Deposit("Deposit · shared by share"),
    Discount("Discount · shared by share"),
    Alcohol("Alcohol"),
    Meat("Meat"),
    CheckPrice("Check this price"),
}

@Immutable
data class ReviewItemUi(
    val id: Int,
    val name: String,
    val priceText: String,
    val tag: ItemTag?,
    val lowConfidence: Boolean,
    val isNegative: Boolean,
)

@Immutable
data class SplitPersonUi(val id: String, val name: String, val avatar: AvatarUi, val included: Boolean, val canToggle: Boolean)

@Immutable
data class WhoUi(val personId: String, val name: String, val avatar: AvatarUi, val on: Boolean)

@Immutable
data class SplitItemUi(val id: Int, val name: String, val priceCents: Long, val who: List<WhoUi>, val eachLabel: String)

@Immutable
data class ShareUi(val name: String, val amountCents: Long, val isMe: Boolean)

enum class OwerTone { Incoming, Outgoing, Neutral }

@Immutable
data class OwerUi(val avatar: AvatarUi, val text: String, val amountCents: Long, val tone: OwerTone)

@Immutable
data class SavedUi(
    val title: String,
    val totalCents: Long,
    val payerLabel: String,
    val owers: List<OwerUi>,
    val myShareCents: Long,
    val synced: Boolean,
    /** "Synced · Ida, Jonas and Mads notified" or "Saved on your phone · syncing…". */
    val statusText: String,
)

@Immutable
data class ReceiptPreviewUi(val merchant: String, val subtitle: String, val lines: List<Pair<String, String>>, val total: String)

@Immutable
data class ScanUiState(
    val step: ScanStep,
    val manual: Boolean,
    val flashOn: Boolean,
    val permissionDenied: Boolean,
    val receipt: ReceiptPreviewUi,
    val group: ScanGroupUi?,
    val groups: List<ScanGroupUi>,
    val groupPickerOpen: Boolean,
    val title: String,
    val payer: PayerUi?,
    val payers: List<PayerUi>,
    val payerPickerOpen: Boolean,
    val items: List<ReviewItemUi>,
    val totalCents: Long,
    val receiptTotalCents: Long?,
    val people: List<SplitPersonUi>,
    val ruleNotice: String?,
    val ruleAvatar: AvatarUi?,
    val splitItems: List<SplitItemUi>,
    val extrasNote: String?,
    val shares: List<ShareUi>,
    val saved: SavedUi?,
    val receiptViewerOpen: Boolean,
) {
    val canContinue: Boolean get() = group != null && totalCents > 0 && items.any { MoneyFormat.parse(it.priceText) != null }
    val canSave: Boolean get() = canContinue && shares.isNotEmpty()
}

sealed interface ScanAction {
    data class PermissionChecked(val granted: Boolean) : ScanAction
    data object CameraDenied : ScanAction
    data object OpenCamera : ScanAction
    data object Shoot : ScanAction
    data object TypeManually : ScanAction
    data object ToggleFlash : ScanAction
    data object BackToReview : ScanAction
    data object GoToSplit : ScanAction
    data object ToggleGroupPicker : ScanAction
    data class PickGroup(val groupId: String) : ScanAction
    data object TogglePayerPicker : ScanAction
    data class PickPayer(val personId: String) : ScanAction
    data class TitleChanged(val value: String) : ScanAction
    data class ItemNameChanged(val id: Int, val value: String) : ScanAction
    data class ItemPriceChanged(val id: Int, val value: String) : ScanAction
    data class RemoveItem(val id: Int) : ScanAction
    data object AddItem : ScanAction
    data class TogglePerson(val personId: String) : ScanAction
    data class ToggleItemPerson(val itemId: Int, val personId: String) : ScanAction
    data object Save : ScanAction
    data object ScanAnother : ScanAction
    data class ShowReceipt(val show: Boolean) : ScanAction
}

data class ScanLocalState(
    val step: ScanStep = ScanStep.Starting,
    val manual: Boolean = false,
    val flashOn: Boolean = false,
    val permissionDenied: Boolean = false,
    val groupId: String? = null,
    val groupPickerOpen: Boolean = false,
    val payerId: String? = null,
    val payerPickerOpen: Boolean = false,
    val title: String = DemoLedger.RECEIPT_MERCHANT,
    val items: List<ReceiptItem> = DemoLedger.receiptItems,
    val excluded: Set<String> = emptySet(),
    val overrides: Map<Int, List<String>> = emptyMap(),
    val savedExpenseId: String? = null,
    val savedShares: Map<String, Long> = emptyMap(),
    val receiptViewerOpen: Boolean = false,
)

private fun Person.avatarFor(meId: String) = AvatarUi(initial, if (id == meId) -1 else paletteIndex)

private fun LedgerSnapshot.nameFor(id: String) = if (id == currentUserId) "You" else person(id).name

/** Natural-language list: "Ida", "Ida and Jonas", "Ida, Jonas and Mads". */
private fun List<String>.joinNatural(): String = when (size) {
    0 -> "Nobody"
    1 -> first()
    else -> dropLast(1).joinToString(", ") + " and " + last()
}

fun buildScanUiState(snapshot: LedgerSnapshot, online: Boolean, local: ScanLocalState, now: Long): ScanUiState {
    val me = snapshot.currentUserId
    val myGroups = snapshot.myGroups()
    val group = local.groupId?.let(snapshot::group) ?: myGroups.maxByOrNull { snapshot.lastActivityAt(it.id) }
    val members = group?.members?.map { snapshot.person(it.userId) }.orEmpty()
    val payerId = local.payerId?.takeIf { id -> members.any { it.id == id } } ?: me
    val active = members.filter { it.id !in local.excluded }

    val total = local.items.sumOf { it.priceCents }
    val shares = if (group == null) emptyMap() else ReceiptSplitter.shares(local.items, members, local.overrides, local.excluded, payerId)

    // Diet-rule notice: the first person automatically taken off items.
    val regular = local.items.filter { it.kind == ReceiptItemKind.REGULAR }
    val ruled = active.firstNotNullOfOrNull { person ->
        // Items the diet rule took this person off, and nobody has added them back to.
        val removed = regular.count { item ->
            person.id !in ReceiptSplitter.defaultSharers(item, members) &&
                person.id !in ReceiptSplitter.sharers(item, members, local.overrides, local.excluded)
        }
        if (removed > 0 && person.dietRules.isNotEmpty()) person to removed else null
    }
    val ruleNotice = ruled?.let { (person, count) ->
        val why = if (DietRule.NO_ALCOHOL in person.dietRules) "doesn't drink alcohol" else "doesn't eat meat"
        val items = if (count == 1) "1 item" else "$count items"
        "${person.name} $why and was taken off $items. Tap ${person.name}'s circle on an item to add them back."
    }

    val splitItems = regular.map { item ->
        val who = ReceiptSplitter.sharers(item, members, local.overrides, local.excluded)
        SplitItemUi(
            id = item.id,
            name = item.name.ifBlank { "Item" },
            priceCents = item.priceCents,
            who = active.map { WhoUi(it.id, snapshot.nameFor(it.id), it.avatarFor(me), it.id in who) },
            eachLabel = when (who.size) {
                0 -> "Pick someone"
                1 -> "Only " + snapshot.nameFor(who.first()).replace("You", "you")
                else -> "${MoneyFormat.kr(item.priceCents / who.size)} each"
            },
        )
    }
    val extras = local.items.filter { it.kind != ReceiptItemKind.REGULAR }
    val extrasNote = if (extras.isEmpty()) null else extras.joinToString(" and ") {
        val label = if (it.kind == ReceiptItemKind.DEPOSIT) "Deposit (pant)" else "discount"
        "$label ${MoneyFormat.signed(it.priceCents)} kr"
    }.replaceFirstChar { it.uppercase() } + " " + (if (extras.size == 1) "is" else "are") + " shared by how much each person had."

    val saved = local.savedExpenseId?.let { id ->
        val expense = snapshot.expenses.firstOrNull { it.id == id }
        val synced = expense?.syncStatus == SyncStatus.SYNCED
        val payerName = snapshot.nameFor(payerId)
        val owers = local.savedShares.filter { (id, amount) -> id != payerId && amount > 0 }.map { (id, amount) ->
            val person = snapshot.person(id)
            when {
                payerId == me -> OwerUi(person.avatarFor(me), "${person.name} owes you", amount, OwerTone.Incoming)
                id == me -> OwerUi(person.avatarFor(me), "You owe $payerName", amount, OwerTone.Outgoing)
                else -> OwerUi(person.avatarFor(me), "${person.name} owes $payerName", amount, OwerTone.Neutral)
            }
        }
        val notified = local.savedShares.keys.filter { it != me }.map { snapshot.person(it).name }.joinNatural()
        SavedUi(
            title = expense?.title ?: local.title,
            totalCents = expense?.amountCents ?: total,
            payerLabel = if (payerId == me) "you" else payerName,
            owers = owers,
            myShareCents = local.savedShares[me] ?: 0L,
            synced = synced,
            statusText = when {
                synced -> "Synced · $notified notified"
                online -> "Saved on your phone · syncing…"
                else -> "Saved on your phone · syncs when you're online"
            },
        )
    }

    val stamp = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.UK).format(Date(now))
    return ScanUiState(
        step = local.step,
        manual = local.manual,
        flashOn = local.flashOn,
        permissionDenied = local.permissionDenied,
        receipt = ReceiptPreviewUi(
            merchant = DemoLedger.RECEIPT_MERCHANT,
            subtitle = "${DemoLedger.RECEIPT_STORE} · $stamp",
            lines = DemoLedger.receiptItems.map {
                it.name.uppercase() to (if (it.lowConfidence) DemoLedger.RECEIPT_CORRECT_LOW_PRICE else MoneyFormat.signed(it.priceCents))
            },
            total = MoneyFormat.kr(DemoLedger.RECEIPT_TOTAL_CENTS),
        ),
        group = group?.let { ScanGroupUi(it.id, it.name, AvatarUi(it.name.take(1).uppercase(), it.colorIndex)) },
        groups = myGroups.map { ScanGroupUi(it.id, it.name, AvatarUi(it.name.take(1).uppercase(), it.colorIndex)) },
        groupPickerOpen = local.groupPickerOpen,
        title = local.title,
        payer = members.firstOrNull { it.id == payerId }?.let { PayerUi(it.id, snapshot.nameFor(it.id), it.avatarFor(me), true) },
        payers = members.map { PayerUi(it.id, snapshot.nameFor(it.id), it.avatarFor(me), it.id == payerId) },
        payerPickerOpen = local.payerPickerOpen,
        items = local.items.map { item ->
            ReviewItemUi(
                id = item.id, name = item.name, priceText = item.priceText,
                tag = when {
                    item.kind == ReceiptItemKind.DEPOSIT -> ItemTag.Deposit
                    item.kind == ReceiptItemKind.DISCOUNT -> ItemTag.Discount
                    item.isAlcohol -> ItemTag.Alcohol
                    item.lowConfidence -> ItemTag.CheckPrice
                    else -> null
                },
                lowConfidence = item.lowConfidence,
                isNegative = item.priceCents < 0,
            )
        },
        totalCents = total,
        receiptTotalCents = if (local.manual) null else DemoLedger.RECEIPT_TOTAL_CENTS,
        people = members.map {
            SplitPersonUi(it.id, snapshot.nameFor(it.id), it.avatarFor(me), it.id !in local.excluded, canToggle = it.id != me)
        },
        ruleNotice = ruleNotice,
        ruleAvatar = ruled?.first?.avatarFor(me),
        splitItems = splitItems,
        extrasNote = extrasNote,
        shares = active.map { ShareUi(snapshot.nameFor(it.id), shares[it.id] ?: 0L, it.id == me) },
        saved = saved,
        receiptViewerOpen = local.receiptViewerOpen,
    )
}

/**
 * Add expense: Ask for device permission → Camera view → reading → check items →
 * "Select group member's share" → Confirmation. Saving writes locally first and the confirmation
 * follows the expense's real sync status.
 */
class ScanViewModel(
    private val ledger: LedgerRepository,
    initialGroupId: String?,
) : ViewModel() {

    private val local = MutableStateFlow(ScanLocalState(groupId = initialGroupId))
    private var readingJob: Job? = null

    val uiState: StateFlow<ScanUiState> =
        combine(ledger.snapshot, ledger.isOnline, local) { snapshot, online, localState ->
            buildScanUiState(snapshot, online, localState, ledger.now())
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            buildScanUiState(ledger.snapshot.value, ledger.isOnline.value, local.value, ledger.now()),
        )

    fun onAction(action: ScanAction) {
        when (action) {
            is ScanAction.PermissionChecked -> local.update {
                if (it.step == ScanStep.Starting) it.copy(step = if (action.granted) ScanStep.Camera else ScanStep.Permission) else it
            }
            ScanAction.CameraDenied -> local.update { it.copy(permissionDenied = true) }
            ScanAction.OpenCamera -> local.update { it.copy(step = ScanStep.Camera, permissionDenied = false) }
            ScanAction.Shoot -> startReading()
            ScanAction.TypeManually -> local.update {
                it.copy(
                    step = ScanStep.Review, manual = true, title = "",
                    items = listOf(ReceiptItem(1, "", "")), overrides = emptyMap(), excluded = emptySet(),
                )
            }
            ScanAction.ToggleFlash -> local.update { it.copy(flashOn = !it.flashOn) }
            ScanAction.BackToReview -> local.update { it.copy(step = ScanStep.Review) }
            ScanAction.GoToSplit -> if (uiState.value.canContinue) local.update { it.copy(step = ScanStep.Split, payerPickerOpen = false) }
            ScanAction.ToggleGroupPicker -> local.update { it.copy(groupPickerOpen = !it.groupPickerOpen) }
            is ScanAction.PickGroup -> local.update {
                it.copy(groupId = action.groupId, groupPickerOpen = false, payerId = null, excluded = emptySet(), overrides = emptyMap())
            }
            ScanAction.TogglePayerPicker -> local.update { it.copy(payerPickerOpen = !it.payerPickerOpen) }
            is ScanAction.PickPayer -> local.update { it.copy(payerId = action.personId, payerPickerOpen = false) }
            is ScanAction.TitleChanged -> local.update { it.copy(title = action.value) }
            is ScanAction.ItemNameChanged -> updateItem(action.id) { it.copy(name = action.value) }
            is ScanAction.ItemPriceChanged -> updateItem(action.id) { it.copy(priceText = action.value, lowConfidence = false) }
            is ScanAction.RemoveItem -> local.update { state -> state.copy(items = state.items.filterNot { it.id == action.id }) }
            ScanAction.AddItem -> local.update { state ->
                val id = (state.items.maxOfOrNull { it.id } ?: 0) + 1
                state.copy(items = state.items + ReceiptItem(id, "", ""))
            }
            is ScanAction.TogglePerson -> local.update { state ->
                if (action.personId == ledger.snapshot.value.currentUserId) state
                else state.copy(excluded = if (action.personId in state.excluded) state.excluded - action.personId else state.excluded + action.personId)
            }
            is ScanAction.ToggleItemPerson -> toggleItemPerson(action.itemId, action.personId)
            ScanAction.Save -> save()
            ScanAction.ScanAnother -> local.update {
                ScanLocalState(step = ScanStep.Camera, groupId = it.groupId)
            }
            is ScanAction.ShowReceipt -> local.update { it.copy(receiptViewerOpen = action.show) }
        }
    }

    private fun startReading() {
        local.update {
            it.copy(
                step = ScanStep.Reading, manual = false, title = DemoLedger.RECEIPT_MERCHANT,
                items = DemoLedger.receiptItems, overrides = emptyMap(), excluded = emptySet(),
            )
        }
        readingJob?.cancel()
        readingJob = viewModelScope.launch {
            delay(READING_MS)
            local.update { if (it.step == ScanStep.Reading) it.copy(step = ScanStep.Review) else it }
        }
    }

    private fun updateItem(id: Int, transform: (ReceiptItem) -> ReceiptItem) = local.update { state ->
        state.copy(items = state.items.map { if (it.id == id) transform(it) else it })
    }

    private fun toggleItemPerson(itemId: Int, personId: String) {
        val snapshot = ledger.snapshot.value
        val state = local.value
        val groupId = uiState.value.group?.id ?: return
        val members = snapshot.group(groupId)?.members?.map { snapshot.person(it.userId) }.orEmpty()
        val item = state.items.firstOrNull { it.id == itemId } ?: return
        val current = state.overrides[itemId] ?: ReceiptSplitter.defaultSharers(item, members)
        val next = if (personId in current) current - personId else current + personId
        local.update { it.copy(overrides = it.overrides + (itemId to next)) }
    }

    private fun save() {
        val ui = uiState.value
        val group = ui.group ?: return
        if (!ui.canSave || local.value.savedExpenseId != null) return
        val snapshot = ledger.snapshot.value
        val state = local.value
        val members = snapshot.group(group.id)?.members?.map { snapshot.person(it.userId) }.orEmpty()
        val payerId = ui.payer?.id ?: snapshot.currentUserId
        val shares = ReceiptSplitter.shares(state.items, members, state.overrides, state.excluded, payerId)
        val title = state.title.trim().ifEmpty { if (state.manual) "Expense" else DemoLedger.RECEIPT_MERCHANT }
        val id = ledger.addExpense(
            ExpenseDraft(
                groupId = group.id,
                title = title,
                payerId = payerId,
                amountCents = ui.totalCents,
                shares = shares,
                hasReceipt = !state.manual,
            ),
        )
        local.update { it.copy(step = ScanStep.Saved, savedExpenseId = id, savedShares = shares, title = title) }
    }

    companion object {
        const val READING_MS = 2_200L

        /** "Netto · Tue 3 Nov" */
        fun receiptHeading(title: String, now: Long): String =
            "$title · " + SimpleDateFormat("EEE d MMM", Locale.UK).format(Date(now))
    }
}
