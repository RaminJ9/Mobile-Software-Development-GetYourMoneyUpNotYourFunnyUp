package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.getyourmoneyupnotyourfunnyup.data.receipt.ReceiptPhotos
import com.example.getyourmoneyupnotyourfunnyup.data.receipt.ReceiptScanner
import com.example.getyourmoneyupnotyourfunnyup.data.repository.ScanGroup
import com.example.getyourmoneyupnotyourfunnyup.data.repository.ScanSource
import com.example.getyourmoneyupnotyourfunnyup.model.DanishAmount
import com.example.getyourmoneyupnotyourfunnyup.model.Expense
import com.example.getyourmoneyupnotyourfunnyup.model.ExpenseSplit
import com.example.getyourmoneyupnotyourfunnyup.model.ReceiptItem
import com.example.getyourmoneyupnotyourfunnyup.model.ReceiptItemKind
import com.example.getyourmoneyupnotyourfunnyup.model.ReceiptSplitter
import com.example.getyourmoneyupnotyourfunnyup.model.ScanPerson
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.formatDanishAmountFromCents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Steps of the "Add expense" flow (Scan receipt in the hi-fi). */
enum class ScanStep { Starting, Permission, Camera, Reading, Review, Split, Saved }

/** A circle with an initial. [paletteIndex] picks the group colour; -1 is "You" (always Green300). */
@Immutable
data class AvatarUi(val initial: String, val paletteIndex: Int)

@Immutable
data class ScanGroupUi(val id: String, val name: String, val avatar: AvatarUi)

@Immutable
data class RuleNoticeUi(val avatar: AvatarUi, val text: String)

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
data class ScanUiState(
    val step: ScanStep,
    val manual: Boolean,
    val flashOn: Boolean,
    val permissionDenied: Boolean,
    /** The receipt photo on the phone (file URI), null for typed-in expenses. */
    val photo: String?,
    /** Lines found so far while reading, null until the photo has been read. */
    val foundLines: Int?,
    /** The photo couldn't be read; the items start empty for the user to type in. */
    val readFailed: Boolean,
    /** Shown on the camera when taking the photo failed. */
    val cameraMessage: String?,
    val group: ScanGroupUi?,
    val groups: List<ScanGroupUi>,
    val groupPickerOpen: Boolean,
    val title: String,
    val payer: PayerUi?,
    val payers: List<PayerUi>,
    val payerPickerOpen: Boolean,
    val items: List<ReviewItemUi>,
    val totalCents: Long,
    /** The total printed on the receipt, as read (or typed); the items are checked against it. */
    val receiptTotalText: String,
    /** [receiptTotalText] as an amount; null for typed-in expenses or when no total is known. */
    val receiptTotalCents: Long?,
    val people: List<SplitPersonUi>,
    /** One note for each person a diet rule took off items ("Sofie doesn't eat meat …"). */
    val ruleNotices: List<RuleNoticeUi>,
    val splitItems: List<SplitItemUi>,
    val extrasNote: String?,
    val shares: List<ShareUi>,
    /** True while the expense is being saved, so Save can't be pressed twice. */
    val saving: Boolean,
    /** Why the last save failed; the receipt stays as it was so the user can try again. */
    val saveError: String?,
    val saved: SavedUi?,
    val receiptViewerOpen: Boolean,
) {
    val canContinue: Boolean get() = group != null && totalCents > 0 && items.any { DanishAmount.parse(it.priceText) != null }
    val canSave: Boolean get() = canContinue && shares.isNotEmpty() && !saving
}

sealed interface ScanAction {
    data class PermissionChecked(val granted: Boolean) : ScanAction
    data object CameraDenied : ScanAction
    data object OpenCamera : ScanAction
    /** The camera saved a photo ([photo] is its file URI). */
    data class PhotoTaken(val photo: String) : ScanAction
    /** A photo was picked from the gallery ([photo] is its content URI). */
    data class PhotoPicked(val photo: String) : ScanAction
    data object CaptureFailed : ScanAction
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
    data class ReceiptTotalChanged(val value: String) : ScanAction
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
    val title: String = "",
    val items: List<ReceiptItem> = emptyList(),
    val photo: String? = null,
    val foundLines: Int? = null,
    val readFailed: Boolean = false,
    /** The receipt's printed total as text: filled in from the photo, editable by the user. */
    val receiptTotalText: String = "",
    val cameraMessage: String? = null,
    val excluded: Set<String> = emptySet(),
    val overrides: Map<Int, List<String>> = emptyMap(),
    val saving: Boolean = false,
    val saveError: String? = null,
    /** The expense as saved; set once saving succeeded. */
    val saved: Expense? = null,
    val receiptViewerOpen: Boolean = false,
)

/** Amount without "kr" (the screen adds it where the design shows it). */
private fun amount(cents: Long) = formatDanishAmountFromCents(cents, includeSuffix = false)

private fun ScanPerson.avatarFor(meId: String) = AvatarUi(initial, if (id == meId) -1 else colourIndex)

/** Natural-language list: "Ida", "Ida and Jonas", "Ida, Jonas and Mads". */
private fun List<String>.joinNatural(): String = if (size <= 1) joinToString() else dropLast(1).joinToString(", ") + " and " + last()

/** The group the receipt goes to: the one picked, or the most recently active one. */
private fun List<ScanGroup>.chosen(groupId: String?): ScanGroup? =
    groupId?.let { id -> firstOrNull { it.id == id } } ?: maxByOrNull { it.lastActivityAt }

/**
 * Everything the scan screens show, from the account's groups, the connection, the flow's own state and
 * the saved expense's sync status. A pure function, so it is easy to test.
 */
fun buildScanUiState(
    groups: List<ScanGroup>,
    me: String,
    online: Boolean,
    local: ScanLocalState,
    savedStatus: SyncStatus?,
): ScanUiState {
    val group = groups.chosen(local.groupId)
    val members = group?.members.orEmpty()
    fun nameFor(id: String) = if (id == me) "You" else members.firstOrNull { it.id == id }?.name ?: "Someone"
    val payerId = local.payerId?.takeIf { id -> members.any { it.id == id } } ?: me
    val active = members.filter { it.id !in local.excluded }
    val total = local.items.sumOf { it.priceCents }
    val shares = if (group == null) emptyMap() else ReceiptSplitter.shares(local.items, members, local.overrides, local.excluded, payerId)

    // Diet-rule notices: one for each person automatically taken off items.
    val regular = local.items.filterNot { it.sharedByShare }
    val ruleNotices = active.filter { it.hasDietRule }.mapNotNull { person ->
        // Items the diet rule took this person off, and nobody has added them back to.
        val removed = regular.filter { item ->
            person.id !in ReceiptSplitter.defaultSharers(item, members) &&
                person.id !in ReceiptSplitter.sharers(item, members, local.overrides, local.excluded)
        }
        if (removed.isEmpty()) return@mapNotNull null
        val alcohol = removed.any { it.isAlcohol && person.excludeAlcohol }
        val meat = removed.any { it.isMeat && person.excludeMeat }
        val what = when {
            alcohol && meat -> "drink alcohol or eat meat"
            alcohol -> "drink alcohol"
            else -> "eat meat"
        }
        val items = if (removed.size == 1) "1 item" else "${removed.size} items"
        val text = if (person.id == me) {
            "You don't $what and were taken off $items. Tap your circle on an item to add yourself back."
        } else {
            "${person.name} doesn't $what and was taken off $items. Tap ${person.name}'s circle on an item to add them back."
        }
        RuleNoticeUi(person.avatarFor(me), text)
    }

    val splitItems = regular.map { item ->
        val who = ReceiptSplitter.sharers(item, members, local.overrides, local.excluded)
        SplitItemUi(
            id = item.id,
            name = item.name.ifBlank { "Item" },
            priceCents = item.priceCents,
            who = active.map { WhoUi(it.id, nameFor(it.id), it.avatarFor(me), it.id in who) },
            eachLabel = when (who.size) {
                0 -> "Pick someone"
                1 -> "Only " + nameFor(who.first()).replace("You", "you")
                else -> "${amount(item.priceCents / who.size)} each"
            },
        )
    }
    val extras = local.items.filter { it.sharedByShare }
    val extrasNote = if (extras.isEmpty()) null else extras.joinToString(" and ") {
        val label = if (it.kind == ReceiptItemKind.DEPOSIT) "Deposit (pant)" else "discount"
        "$label ${formatDanishAmountFromCents(it.priceCents)}"
    }.replaceFirstChar { it.uppercase() } + " " + (if (extras.size == 1) "is" else "are") + " shared by how much each person had."

    val saved = local.saved?.let { expense ->
        val synced = savedStatus == SyncStatus.SYNCED
        val savedPayer = expense.payerId
        val payerName = nameFor(savedPayer)
        val owers = expense.splits.filter { it.userId != savedPayer && it.amountOwedCents > 0 }.map { split ->
            val person = members.firstOrNull { it.id == split.userId } ?: ScanPerson(split.userId, "Someone", 0)
            when {
                savedPayer == me -> OwerUi(person.avatarFor(me), "${person.name} owes you", split.amountOwedCents, OwerTone.Incoming)
                split.userId == me -> OwerUi(person.avatarFor(me), "You owe $payerName", split.amountOwedCents, OwerTone.Outgoing)
                else -> OwerUi(person.avatarFor(me), "${person.name} owes $payerName", split.amountOwedCents, OwerTone.Neutral)
            }
        }
        val notified = expense.splits.map { it.userId }.filter { it != me }.map(::nameFor)
        SavedUi(
            title = expense.title,
            totalCents = expense.amountCents,
            payerLabel = if (savedPayer == me) "you" else payerName,
            owers = owers,
            myShareCents = expense.splits.firstOrNull { it.userId == me }?.amountOwedCents ?: 0L,
            synced = synced,
            statusText = when {
                synced && notified.isEmpty() -> "Synced"
                synced -> "Synced · ${notified.joinNatural()} notified"
                online -> "Saved on your phone · syncing…"
                else -> "Saved on your phone · syncs when you're online"
            },
        )
    }

    return ScanUiState(
        step = local.step,
        manual = local.manual,
        flashOn = local.flashOn,
        permissionDenied = local.permissionDenied,
        photo = local.photo,
        foundLines = local.foundLines,
        readFailed = local.readFailed,
        cameraMessage = local.cameraMessage,
        group = group?.let { ScanGroupUi(it.id, it.name, AvatarUi(it.name.take(1).uppercase(), it.colourIndex)) },
        groups = groups.map { ScanGroupUi(it.id, it.name, AvatarUi(it.name.take(1).uppercase(), it.colourIndex)) },
        groupPickerOpen = local.groupPickerOpen,
        title = local.title,
        payer = members.firstOrNull { it.id == payerId }?.let { PayerUi(it.id, nameFor(it.id), it.avatarFor(me), true) },
        payers = members.map { PayerUi(it.id, nameFor(it.id), it.avatarFor(me), it.id == payerId) },
        payerPickerOpen = local.payerPickerOpen,
        items = local.items.map { item ->
            ReviewItemUi(
                id = item.id, name = item.name, priceText = item.priceText,
                tag = when {
                    item.kind == ReceiptItemKind.DEPOSIT -> ItemTag.Deposit
                    // A typed negative line is split like a discount, so it is labelled as one.
                    item.kind == ReceiptItemKind.DISCOUNT || item.priceCents < 0 -> ItemTag.Discount
                    item.isAlcohol -> ItemTag.Alcohol
                    item.isMeat -> ItemTag.Meat
                    item.lowConfidence -> ItemTag.CheckPrice
                    else -> null
                },
                lowConfidence = item.lowConfidence,
                isNegative = item.priceCents < 0,
            )
        },
        totalCents = total,
        receiptTotalText = local.receiptTotalText,
        receiptTotalCents = if (local.manual) null else DanishAmount.parse(local.receiptTotalText),
        people = members.map {
            SplitPersonUi(it.id, nameFor(it.id), it.avatarFor(me), it.id !in local.excluded, canToggle = it.id != me)
        },
        ruleNotices = ruleNotices,
        splitItems = splitItems,
        extrasNote = extrasNote,
        shares = active.map { ShareUi(nameFor(it.id), shares[it.id] ?: 0L, it.id == me) },
        saving = local.saving,
        saveError = local.saveError,
        saved = saved,
        receiptViewerOpen = local.receiptViewerOpen,
    )
}

/**
 * Add expense: Ask for device permission → Camera view → reading → check items →
 * "Select group member's share" → Confirmation. Saving writes to the phone first (and queues the upload);
 * the confirmation follows the expense's real sync status.
 *
 * @param meId the signed-in account; @param online whether the phone has a usable connection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModel(
    private val source: ScanSource,
    private val meId: String,
    initialGroupId: String?,
    private val scanner: ReceiptScanner,
    private val photos: ReceiptPhotos,
    online: Flow<Boolean>,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val local = MutableStateFlow(ScanLocalState(groupId = initialGroupId))
    private var readingJob: Job? = null

    private val groups: StateFlow<List<ScanGroup>> = source.groups.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val savedStatus: Flow<SyncStatus?> = local
        .map { it.saved }
        .distinctUntilChanged()
        .flatMapLatest { expense -> if (expense == null) flowOf(null) else source.syncStatus(expense.id, expense.groupId) }

    val uiState: StateFlow<ScanUiState> =
        combine(groups, online.distinctUntilChanged(), local, savedStatus) { groups, isOnline, localState, status ->
            buildScanUiState(groups, meId, isOnline, localState, status)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            buildScanUiState(groups.value, meId, true, local.value, null),
        )

    fun onAction(action: ScanAction) {
        when (action) {
            is ScanAction.PermissionChecked -> local.update {
                if (it.step == ScanStep.Starting) it.copy(step = if (action.granted) ScanStep.Camera else ScanStep.Permission) else it
            }
            ScanAction.CameraDenied -> local.update { it.copy(permissionDenied = true) }
            ScanAction.OpenCamera -> {
                readingJob?.cancel()
                discardUnsavedPhoto()
                local.update { it.copy(step = ScanStep.Camera, permissionDenied = false, cameraMessage = null) }
            }
            is ScanAction.PhotoTaken -> read(action.photo)
            is ScanAction.PhotoPicked -> read(action.photo)
            ScanAction.CaptureFailed -> local.update { it.copy(cameraMessage = "Couldn't take the photo. Try again.") }
            ScanAction.TypeManually -> local.update {
                it.copy(
                    step = ScanStep.Review, manual = true, title = "",
                    items = listOf(ReceiptItem(1, "", "")), overrides = emptyMap(), excluded = emptySet(),
                )
            }
            ScanAction.ToggleFlash -> local.update { it.copy(flashOn = !it.flashOn) }
            ScanAction.BackToReview -> local.update { it.copy(step = ScanStep.Review, saveError = null) }
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
            is ScanAction.ReceiptTotalChanged -> local.update { it.copy(receiptTotalText = action.value) }
            is ScanAction.RemoveItem -> local.update { state -> state.copy(items = state.items.filterNot { it.id == action.id }) }
            ScanAction.AddItem -> local.update { state ->
                val id = (state.items.maxOfOrNull { it.id } ?: 0) + 1
                state.copy(items = state.items + ReceiptItem(id, "", ""))
            }
            is ScanAction.TogglePerson -> local.update { state ->
                if (action.personId == meId) state
                else state.copy(excluded = if (action.personId in state.excluded) state.excluded - action.personId else state.excluded + action.personId)
            }
            is ScanAction.ToggleItemPerson -> toggleItemPerson(action.itemId, action.personId)
            ScanAction.Save -> save()
            // The saved photo now belongs to the expense, so it is kept.
            ScanAction.ScanAnother -> local.update { ScanLocalState(step = ScanStep.Camera, groupId = it.groupId) }
            is ScanAction.ShowReceipt -> local.update { it.copy(receiptViewerOpen = action.show) }
        }
    }

    /**
     * Saves [picked] as the receipt photo and reads it on the phone. Whatever happens the user ends up on
     * Check items: with the items that were found, or, if the photo couldn't be read, with one empty row
     * to type into (the photo is still attached).
     */
    private fun read(picked: String) {
        readingJob?.cancel()
        discardUnsavedPhoto()
        local.update {
            it.copy(
                step = ScanStep.Reading, manual = false, cameraMessage = null, readFailed = false, foundLines = null,
                photo = null, overrides = emptyMap(), excluded = emptySet(), receiptTotalText = "",
            )
        }
        readingJob = viewModelScope.launch {
            // Saving isn't interrupted, so a finished file is always recorded and can be cleaned up later.
            val photo = withContext(NonCancellable) { runCatching { photos.save(picked) }.getOrNull() }
            if (photo == null) {
                local.update { it.copy(step = ScanStep.Camera, cameraMessage = "Couldn't open that photo. Try another one.") }
                return@launch
            }
            local.update { it.copy(photo = photo) }
            ensureActive()
            val scanned = try {
                scanner.read(photo)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            val items = scanned?.items.orEmpty()
            local.update { it.copy(foundLines = items.size) }
            delay(RESULT_PAUSE_MS) // let the "Found N lines → Checking the total" checklist finish
            local.update {
                it.copy(
                    step = ScanStep.Review,
                    title = scanned?.merchant ?: "Receipt",
                    items = items.ifEmpty { listOf(ReceiptItem(1, "", "")) },
                    receiptTotalText = scanned?.totalCents?.takeIf { items.isNotEmpty() }?.let(DanishAmount::forInput).orEmpty(),
                    readFailed = items.isEmpty(),
                )
            }
        }
    }

    /** A photo that never made it into a saved expense is deleted from the phone (and forgotten). */
    private fun discardUnsavedPhoto() {
        val state = local.value
        if (state.saved != null) return
        state.photo?.let(photos::delete)
        local.update { it.copy(photo = null) }
    }

    override fun onCleared() = discardUnsavedPhoto()

    private fun updateItem(id: Int, transform: (ReceiptItem) -> ReceiptItem) = local.update { state ->
        state.copy(items = state.items.map { if (it.id == id) transform(it) else it })
    }

    private fun toggleItemPerson(itemId: Int, personId: String) {
        val state = local.value
        val members = groups.value.chosen(state.groupId)?.members ?: return
        val item = state.items.firstOrNull { it.id == itemId } ?: return
        val current = state.overrides[itemId] ?: ReceiptSplitter.defaultSharers(item, members)
        val next = if (personId in current) current - personId else current + personId
        local.update { it.copy(overrides = it.overrides + (itemId to next)) }
    }

    private fun save() {
        val ui = uiState.value
        val state = local.value
        if (!ui.canSave || state.saved != null) return
        val group = groups.value.chosen(state.groupId) ?: return
        val payerId = ui.payer?.id ?: meId
        val shares = ReceiptSplitter.shares(state.items, group.members, state.overrides, state.excluded, payerId)
        val id = UUID.randomUUID().toString()
        val photo = state.photo.takeUnless { state.manual }
        val expense = Expense(
            id = id,
            groupId = group.id,
            title = state.title.trim().ifEmpty { if (state.manual) "Expense" else "Receipt" },
            amountCents = ui.totalCents,
            currency = group.currency,
            payerId = payerId,
            category = if (photo != null) "Receipt" else "General",
            receiptLocalUri = photo,
            splits = shares.filterValues { it != 0L }.map { (userId, owed) -> ExpenseSplit("$id-$userId", id, userId, owed) },
            createdAt = clock(),
            createdBy = meId,
        )
        local.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            try {
                val stored = source.save(expense)
                local.update { it.copy(step = ScanStep.Saved, saving = false, saved = stored, title = stored.title) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                local.update { it.copy(saving = false, saveError = error.message ?: "Couldn't save the expense. Try again.") }
            }
        }
    }

    companion object {
        /** How long "Found N lines" stays up before Check items opens. */
        const val RESULT_PAUSE_MS = 900L

        /** "Netto · Tue 3 Nov" */
        fun receiptHeading(title: String, now: Long): String =
            "$title · " + SimpleDateFormat("EEE d MMM", Locale.UK).format(Date(now))
    }
}
