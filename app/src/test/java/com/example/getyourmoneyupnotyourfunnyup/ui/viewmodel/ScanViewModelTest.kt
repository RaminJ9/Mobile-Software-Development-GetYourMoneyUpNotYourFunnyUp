package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import com.example.getyourmoneyupnotyourfunnyup.data.demo.DemoLedger
import com.example.getyourmoneyupnotyourfunnyup.data.receipt.ReceiptPhotos
import com.example.getyourmoneyupnotyourfunnyup.data.receipt.ReceiptScanner
import com.example.getyourmoneyupnotyourfunnyup.data.repository.InMemoryLedgerRepository
import com.example.getyourmoneyupnotyourfunnyup.model.ScannedReceipt
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The scan flow with a stand-in scanner and photo store (the real ML Kit reading is tested on a device). */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val now = System.currentTimeMillis()

    private class FakePhotos : ReceiptPhotos {
        var unreadable = false
        val deleted = mutableListOf<String>()
        override suspend fun save(source: String): String =
            if (unreadable) error("not an image") else "file:///receipts/${source.substringAfterLast('/')}.jpg"
        override fun delete(photo: String) {
            deleted += photo
        }
    }

    private val sampleReceipt = ScannedReceipt(DemoLedger.RECEIPT_MERCHANT, DemoLedger.receiptItems, DemoLedger.RECEIPT_TOTAL_CENTS)

    private fun TestScope.ledger(online: Boolean = true) = InMemoryLedgerRepository(
        scope = backgroundScope, isOnline = MutableStateFlow(online), clock = { now }, initial = DemoLedger.create(now),
        syncDelayMs = 1_000, paymentDelayMs = 500,
    )

    private fun TestScope.scan(
        repository: InMemoryLedgerRepository = ledger(),
        photos: FakePhotos = FakePhotos(),
        scanner: ReceiptScanner = ReceiptScanner { sampleReceipt },
    ) = ScanViewModel(repository, "kol", scanner, photos).apply { onAction(ScanAction.PermissionChecked(granted = true)) }

    /** Lets the reading finish, including the "Found N lines" pause. */
    private fun TestScope.finishReading() {
        runCurrent()
        advanceTimeBy(ScanViewModel.RESULT_PAUSE_MS + 1)
        runCurrent()
    }

    @Test
    fun aReadReceipt_becomesItemsATotalAndASavedPhoto() = runTest(mainDispatcher.dispatcher) {
        val ledger = ledger()
        val vm = scan(ledger)

        vm.onAction(ScanAction.PhotoTaken("file:///cache/shot1"))
        assertEquals(ScanStep.Reading, vm.uiState.value.step)
        runCurrent()
        assertEquals("The checklist shows what was found", DemoLedger.receiptItems.size, vm.uiState.value.foundLines)
        finishReading()

        val state = vm.uiState.value
        assertEquals(ScanStep.Review, state.step)
        assertEquals("Netto", state.title)
        assertEquals("file:///receipts/shot1.jpg", state.photo)
        assertFalse(state.readFailed)
        assertTrue("The misread price makes the total not match", state.receiptTotalCents != state.totalCents)

        val low = state.items.first { it.lowConfidence }
        vm.onAction(ScanAction.ItemPriceChanged(low.id, DemoLedger.RECEIPT_CORRECT_LOW_PRICE))
        assertEquals(DemoLedger.RECEIPT_TOTAL_CENTS, vm.uiState.value.totalCents)

        vm.onAction(ScanAction.GoToSplit)
        assertNotNull("Mads's no-alcohol rule is explained", vm.uiState.value.ruleNotice)
        vm.onAction(ScanAction.Save)

        val saved = ledger.snapshot.value.expenses.first()
        assertEquals("file:///receipts/shot1.jpg", saved.receiptLocalUri)
        assertEquals(DemoLedger.RECEIPT_TOTAL_CENTS, saved.amountCents)
        assertEquals(saved.amountCents, saved.splits.sumOf { it.amountOwedCents })
    }

    @Test
    fun anUnreadablePhoto_letsTheUserTypeTheItemsAndKeepsThePhoto() = runTest(mainDispatcher.dispatcher) {
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
    fun aPhotoThatCantBeOpened_staysOnTheCameraWithAMessage() = runTest(mainDispatcher.dispatcher) {
        val vm = scan(photos = FakePhotos().apply { unreadable = true })

        vm.onAction(ScanAction.PhotoPicked("content://media/picker/broken"))
        runCurrent()

        assertEquals(ScanStep.Camera, vm.uiState.value.step)
        assertNotNull(vm.uiState.value.cameraMessage)
    }

    @Test
    fun retakingAPhoto_deletesTheOneThatWasNeverSaved() = runTest(mainDispatcher.dispatcher) {
        val photos = FakePhotos()
        val vm = scan(photos = photos)
        vm.onAction(ScanAction.PhotoTaken("file:///cache/first"))
        finishReading()

        vm.onAction(ScanAction.OpenCamera)

        assertEquals(listOf("file:///receipts/first.jpg"), photos.deleted)
        assertNull(vm.uiState.value.photo)
    }

    @Test
    fun aSavedExpensesPhoto_isKeptWhenScanningAnother() = runTest(mainDispatcher.dispatcher) {
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
    fun typingItIn_savesAnExpenseWithoutAPhoto() = runTest(mainDispatcher.dispatcher) {
        val ledger = ledger()
        val vm = scan(ledger)
        vm.onAction(ScanAction.TypeManually)
        vm.onAction(ScanAction.TitleChanged("Pizza"))
        vm.onAction(ScanAction.ItemPriceChanged(1, "120,00"))
        vm.onAction(ScanAction.GoToSplit)
        vm.onAction(ScanAction.Save)

        val saved = ledger.snapshot.value.expenses.first()
        assertEquals("Pizza", saved.title)
        assertNull(saved.receiptLocalUri)
    }

    @Test
    fun scanningOffline_isReadAndSavedOnThePhone() = runTest(mainDispatcher.dispatcher) {
        // Reliability QA: nothing in the scan path needs the internet.
        val ledger = ledger(online = false)
        val vm = scan(ledger)
        vm.onAction(ScanAction.PhotoTaken("file:///cache/offline"))
        finishReading()
        vm.onAction(ScanAction.GoToSplit)
        vm.onAction(ScanAction.Save)

        assertEquals(ScanStep.Saved, vm.uiState.value.step)
        assertEquals(SyncStatus.PENDING_INSERT, ledger.snapshot.value.expenses.first().syncStatus)
        assertFalse(checkNotNull(vm.uiState.value.saved).synced)
    }
}
