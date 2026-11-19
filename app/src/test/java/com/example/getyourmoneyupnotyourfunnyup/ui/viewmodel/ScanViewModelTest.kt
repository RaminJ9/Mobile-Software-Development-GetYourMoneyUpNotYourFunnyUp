package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import com.example.getyourmoneyupnotyourfunnyup.data.receipt.ReceiptPhotos
import com.example.getyourmoneyupnotyourfunnyup.data.receipt.ReceiptScanner
import com.example.getyourmoneyupnotyourfunnyup.data.repository.ScanGroup
import com.example.getyourmoneyupnotyourfunnyup.data.repository.ScanSource
import com.example.getyourmoneyupnotyourfunnyup.model.Expense
import com.example.getyourmoneyupnotyourfunnyup.model.ReceiptItem
import com.example.getyourmoneyupnotyourfunnyup.model.ReceiptItemKind
import com.example.getyourmoneyupnotyourfunnyup.model.ScanPerson
import com.example.getyourmoneyupnotyourfunnyup.model.ScannedReceipt
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The scan flow with a stand-in scanner, photo store and data source (the real ones are tested on a device). */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {

    private val main = UnconfinedTestDispatcher()

    @Before fun setMain() = Dispatchers.setMain(main)

    @After fun resetMain() = Dispatchers.resetMain()

    private class FakePhotos : ReceiptPhotos {
        var unreadable = false
        val deleted = mutableListOf<String>()
        override suspend fun save(source: String): String =
            if (unreadable) error("not an image") else "file:///receipts/${source.substringAfterLast('/')}.jpg"
        override fun delete(photo: String) {
            deleted += photo
        }
    }

    /** Records what is saved; can fail, or hold a save "in progress" until [gate] completes. */
    private class FakeSource(groups: List<ScanGroup>) : ScanSource {
        override val groups: Flow<List<ScanGroup>> = MutableStateFlow(groups)
        val statuses = MutableStateFlow<Map<String, SyncStatus>>(emptyMap())
        val saved = mutableListOf<Expense>()
        var failure: Exception? = null
        var gate: CompletableDeferred<Unit>? = null

        override fun syncStatus(expenseId: String, groupId: String): Flow<SyncStatus?> = statuses.map { it[expenseId] }

        override suspend fun save(expense: Expense): Expense {
            gate?.await()
            failure?.let { throw it }
            saved += expense
            statuses.update { it + (expense.id to SyncStatus.PENDING_INSERT) }
            return expense.copy(syncStatus = SyncStatus.PENDING_INSERT)
        }
    }

    private val kitchen = ScanGroup(
        id = "kol", name = "Kollegiet", colourIndex = 0, currency = "DKK", lastActivityAt = 2_000L,
        members = listOf(
            ScanPerson("me", "Victor", 3),
            ScanPerson("ida", "Ida", 2),
            ScanPerson("mads", "Mads", 1, excludeAlcohol = true),
            ScanPerson("jonas", "Jonas", 4),
        ),
    )
    private val padel = ScanGroup("pad", "Padel", 5, "DKK", 1_000L, listOf(ScanPerson("me", "Victor", 3), ScanPerson("ida", "Ida", 2)))

    /** A Netto receipt as read: the wine was misread as 97,00 (the printed total says 211,95). */
    private val sampleReceipt = ScannedReceipt(
        merchant = "Netto",
        items = listOf(
            ReceiptItem(1, "Tuborg grøn 6-pak", "59,95", isAlcohol = true),
            ReceiptItem(2, "Pant A", "6,00", ReceiptItemKind.DEPOSIT),
            ReceiptItem(3, "Hakket oksekød", "45,00", isMeat = true),
            ReceiptItem(4, "Rugbrød", "22,00"),
            ReceiptItem(5, "Merlot rødvin", "97,00", isAlcohol = true, lowConfidence = true),
        ),
        totalCents = 21_195,
    )

    private fun scan(
        source: FakeSource = FakeSource(listOf(kitchen, padel)),
        photos: FakePhotos = FakePhotos(),
        scanner: ReceiptScanner = ReceiptScanner { sampleReceipt },
        online: Flow<Boolean> = MutableStateFlow(true),
        groupId: String? = "kol",
    ) = ScanViewModel(source, "me", groupId, scanner, photos, online, clock = { 5_000L })
        .apply { onAction(ScanAction.PermissionChecked(granted = true)) }

    /** Lets the reading finish, including the "Found N lines" pause. */
    private fun TestScope.finishReading() {
        runCurrent()
        advanceTimeBy(ScanViewModel.RESULT_PAUSE_MS + 1)
        runCurrent()
    }

    @Test
    fun aReadReceipt_isCheckedSplitAndSavedForTheGroup() = runTest(main) {
        val source = FakeSource(listOf(kitchen, padel))
        val vm = scan(source)

        vm.onAction(ScanAction.PhotoTaken("file:///cache/shot1"))
        assertEquals(ScanStep.Reading, vm.uiState.value.step)
        runCurrent()
        assertEquals("The checklist shows what was found", 5, vm.uiState.value.foundLines)
        finishReading()

        val state = vm.uiState.value
        assertEquals(ScanStep.Review, state.step)
        assertEquals("Netto", state.title)
        assertEquals("file:///receipts/shot1.jpg", state.photo)
        assertTrue("The misread price makes the total not match", state.receiptTotalCents != state.totalCents)
        vm.onAction(ScanAction.ItemPriceChanged(state.items.first { it.lowConfidence }.id, "79,00"))
        assertEquals(21_195L, vm.uiState.value.totalCents)

        vm.onAction(ScanAction.GoToSplit)
        assertNotNull("Mads's no-alcohol rule is explained", vm.uiState.value.ruleNotice)
        vm.onAction(ScanAction.Save)

        val saved = source.saved.single()
        assertEquals(ScanStep.Saved, vm.uiState.value.step)
        assertEquals("kol", saved.groupId)
        assertEquals("DKK", saved.currency)
        assertEquals("me", saved.createdBy)
        assertEquals("me", saved.payerId)
        assertEquals("Receipt", saved.category)
        assertEquals("file:///receipts/shot1.jpg", saved.receiptLocalUri)
        assertEquals(21_195L, saved.amountCents)
        // What the balance calculation requires: shares add up, none negative, one per person.
        assertEquals(saved.amountCents, saved.splits.sumOf { it.amountOwedCents })
        assertTrue(saved.splits.all { it.amountOwedCents > 0 && it.expenseId == saved.id })
        assertEquals(saved.splits.size, saved.splits.map { it.userId }.toSet().size)
        val share = saved.splits.associate { it.userId to it.amountOwedCents }
        assertTrue("Mads pays nothing for the beer and wine", share.getValue("mads") < share.getValue("ida"))
    }

    @Test
    fun withoutAGroupChosen_theMostRecentlyActiveGroupIsUsed() = runTest(main) {
        val vm = scan(groupId = null)
        assertEquals("kol", vm.uiState.value.group?.id)
        assertEquals(listOf("kol", "pad"), vm.uiState.value.groups.map { it.id })
    }

    @Test
    fun anUnreadablePhoto_letsTheUserTypeTheItemsAndKeepsThePhoto() = runTest(main) {
        val vm = scan(scanner = { error("no text") })

        vm.onAction(ScanAction.PhotoPicked("content://media/picker/7"))
        finishReading()

        val state = vm.uiState.value
        assertEquals(ScanStep.Review, state.step)
        assertTrue(state.readFailed)
        assertEquals(listOf(""), state.items.map { it.name })
        assertNull("No total to compare with", state.receiptTotalCents)
        assertEquals("file:///receipts/7.jpg", state.photo)
    }

    @Test
    fun aPhotoThatCantBeOpened_staysOnTheCameraWithAMessage() = runTest(main) {
        val vm = scan(photos = FakePhotos().apply { unreadable = true })

        vm.onAction(ScanAction.PhotoPicked("content://media/picker/broken"))
        runCurrent()

        assertEquals(ScanStep.Camera, vm.uiState.value.step)
        assertNotNull(vm.uiState.value.cameraMessage)
    }

    @Test
    fun retakingAPhoto_deletesTheOneThatWasNeverSaved() = runTest(main) {
        val photos = FakePhotos()
        val vm = scan(photos = photos)
        vm.onAction(ScanAction.PhotoTaken("file:///cache/first"))
        finishReading()

        vm.onAction(ScanAction.OpenCamera)

        assertEquals(listOf("file:///receipts/first.jpg"), photos.deleted)
        assertNull(vm.uiState.value.photo)
    }

    @Test
    fun aSavedExpensesPhoto_isKeptWhenScanningAnother() = runTest(main) {
        val photos = FakePhotos()
        val vm = scan(photos = photos)
        vm.onAction(ScanAction.PhotoTaken("file:///cache/kept"))
        finishReading()
        vm.onAction(ScanAction.GoToSplit)
        vm.onAction(ScanAction.Save)

        vm.onAction(ScanAction.ScanAnother)

        assertTrue(photos.deleted.isEmpty())
        assertEquals(ScanStep.Camera, vm.uiState.value.step)
    }

    @Test
    fun typingItIn_savesAnExpenseWithoutAPhoto() = runTest(main) {
        val source = FakeSource(listOf(kitchen))
        val vm = scan(source)
        vm.onAction(ScanAction.TypeManually)
        vm.onAction(ScanAction.TitleChanged("Pizza"))
        vm.onAction(ScanAction.ItemPriceChanged(1, "120,00"))
        vm.onAction(ScanAction.GoToSplit)
        vm.onAction(ScanAction.Save)

        val saved = source.saved.single()
        assertEquals("Pizza", saved.title)
        assertEquals("General", saved.category)
        assertNull(saved.receiptLocalUri)
        assertEquals(listOf(3_000L, 3_000L, 3_000L, 3_000L), saved.splits.map { it.amountOwedCents })
    }

    @Test
    fun theConfirmation_followsTheExpensesSyncStatus() = runTest(main) {
        // Reliability QA: offline the expense is saved on the phone and says when it will sync.
        val source = FakeSource(listOf(kitchen))
        val online = MutableStateFlow(false)
        val vm = scan(source, online = online)
        vm.onAction(ScanAction.TypeManually)
        vm.onAction(ScanAction.ItemPriceChanged(1, "120,00"))
        vm.onAction(ScanAction.GoToSplit)
        vm.onAction(ScanAction.Save)
        assertEquals("Saved on your phone · syncs when you're online", vm.uiState.value.saved?.statusText)

        online.value = true
        assertEquals("Saved on your phone · syncing…", vm.uiState.value.saved?.statusText)

        val id = source.saved.single().id
        source.statuses.update { it + (id to SyncStatus.SYNCED) }
        val saved = checkNotNull(vm.uiState.value.saved)
        assertTrue(saved.synced)
        assertEquals("Synced · Ida, Mads and Jonas notified", saved.statusText)
    }

    @Test
    fun anExpenseOnlyForYou_saysSyncedWithoutNotifyingAnyone() = runTest(main) {
        val alone = ScanGroup("solo", "Just me", 0, "DKK", 0L, listOf(ScanPerson("me", "Victor", 3)))
        val source = FakeSource(listOf(alone))
        val vm = scan(source, groupId = "solo")
        vm.onAction(ScanAction.TypeManually)
        vm.onAction(ScanAction.ItemPriceChanged(1, "89,50"))
        vm.onAction(ScanAction.GoToSplit)
        vm.onAction(ScanAction.Save)

        source.statuses.update { it + (source.saved.single().id to SyncStatus.SYNCED) }
        assertEquals("Synced", vm.uiState.value.saved?.statusText)
    }

    @Test
    fun aFailedSave_keepsTheReceiptAndSaysWhy() = runTest(main) {
        val source = FakeSource(listOf(kitchen)).apply { failure = IllegalStateException("Someone on this receipt is no longer in Kollegiet.") }
        val photos = FakePhotos()
        val vm = scan(source, photos)
        vm.onAction(ScanAction.PhotoTaken("file:///cache/later"))
        finishReading()
        vm.onAction(ScanAction.GoToSplit)
        vm.onAction(ScanAction.Save)

        val state = vm.uiState.value
        assertEquals(ScanStep.Split, state.step)
        assertEquals("Someone on this receipt is no longer in Kollegiet.", state.saveError)
        assertEquals(5, state.items.size)
        assertTrue("The photo is kept for another try", photos.deleted.isEmpty())

        source.failure = null
        vm.onAction(ScanAction.Save)
        assertEquals(ScanStep.Saved, vm.uiState.value.step)
        assertNull(vm.uiState.value.saveError)
        assertEquals(1, source.saved.size)
    }

    @Test
    fun pressingSaveTwice_savesOnce() = runTest(main) {
        val source = FakeSource(listOf(kitchen)).apply { gate = CompletableDeferred() }
        val vm = scan(source)
        vm.onAction(ScanAction.TypeManually)
        vm.onAction(ScanAction.ItemPriceChanged(1, "50,00"))
        vm.onAction(ScanAction.GoToSplit)

        vm.onAction(ScanAction.Save)
        assertTrue(vm.uiState.value.saving)
        assertFalse(vm.uiState.value.canSave)
        vm.onAction(ScanAction.Save)
        checkNotNull(source.gate).complete(Unit)
        runCurrent()

        assertEquals(1, source.saved.size)
        assertEquals(ScanStep.Saved, vm.uiState.value.step)
    }
}
