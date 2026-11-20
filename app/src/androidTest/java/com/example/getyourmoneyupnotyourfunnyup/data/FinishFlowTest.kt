package com.example.getyourmoneyupnotyourfunnyup.data

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.*
import com.example.getyourmoneyupnotyourfunnyup.data.remote.*
import com.example.getyourmoneyupnotyourfunnyup.data.repository.*
import com.example.getyourmoneyupnotyourfunnyup.data.sync.RemoteLedgerCache
import com.example.getyourmoneyupnotyourfunnyup.model.*
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.canPay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Real SQLite tests for the financial integration, authorization and offline outbox. */
class FinishFlowTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private class Fixture(val db: AppDatabase) {
        var account: String? = "creditor"
        val group = GroupEntity("g", "Trip", currency = "DKK", createdBy = "creditor", updatedAt = 1, colourIndex = 4, isFavourite = true)
        val members = listOf("bystander", "debtor", "creditor").map { GroupMemberEntity("g", it) }
        val row = ExpenseEntity("e", "g", "Dinner", 10000, currency = "DKK", payerId = "creditor", createdBy = "creditor", createdAt = 1)
        val split = ExpenseSplitEntity("s", "e", "debtor", 10000)
        val expense get() = db.expenseDao.getExpenseById("e")!!.toDomain(db.expenseDao.getSplitsForExpense("e").map { it.toDomain() })
        val transfer = DebtTransfer("debtor", "creditor", 10000, "DKK")
        val reference get() = SettlementReference.id("g", transfer, listOf(expense), emptyList())
        val dashboard = DashboardDataSource(db) { account }
        init {
            listOf("bystander", "debtor", "creditor").forEach { db.userDao.upsertUser(UserEntity(it, "$it@test", it)) }
            db.groupDao.upsertGroupWithMembers(group, members)
            db.expenseDao.upsertExpenseWithSplits(row, listOf(split))
        }
        fun user(id: String) = db.userDao.getUserById(id)!!.toDomain()
    }
    private suspend fun fixture(test: suspend (Fixture) -> Unit) {
        val db = AppDatabase.createInMemory(context)
        try { test(Fixture(db)) } finally { db.close() }
    }
    private suspend fun rejects(action: suspend () -> Unit) {
        try { action(); fail("Expected access or stale-ledger rejection") }
        catch (_: SecurityException) { } catch (_: IllegalArgumentException) { }
    }

    @Test fun ownerRemovalIsAtomicIdempotentAndUpdatesHomeBalancesOffline() = runBlocking { fixture { f ->
        val remove = LocalExpenseRemoval(f.db) { f.account }
        val expected = f.expense
        assertEquals(SyncStatus.PENDING_DELETE, remove.remove("creditor", "g", expected))
        assertEquals(SyncStatus.PENDING_DELETE, remove.remove("creditor", "g", expected))
        assertTrue(f.expense.isDeleted)
        assertEquals(1, f.db.syncQueueDao.getAllPending().size)
        assertEquals("DELETE", f.db.syncQueueDao.getAllPending().single().action.name)
        assertEquals(1, f.db.expenseDao.getSplitsForExpense("e").size)
        f.account = "debtor"
        assertTrue(f.dashboard.observeHomeData(f.user("debtor")).first().finances!!.outstandingPayments.isEmpty())
    } }

    @Test fun nonOwnerStaleDetailsAndChangedAccountCannotRemove() = runBlocking { fixture { f ->
        val remove = LocalExpenseRemoval(f.db) { f.account }
        f.account = "debtor"; rejects { remove.remove("debtor", "g", f.expense) }
        f.account = "creditor"; rejects { remove.remove("creditor", "g", f.expense.copy(title = "Old details")) }
        f.account = "other"; rejects { remove.remove("creditor", "g", f.expense) }
        assertFalse(f.expense.isDeleted); assertTrue(f.db.syncQueueDao.getAllPending().isEmpty())
    } }

    @Test fun failedOutboxWriteRollsBackExpenseRemoval() = runBlocking { fixture { f ->
        f.db.writableDatabase.execSQL("CREATE TRIGGER reject_queue BEFORE INSERT ON sync_queue BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try { LocalExpenseRemoval(f.db) { f.account }.remove("creditor", "g", f.expense); fail("Expected rollback") }
        catch (_: android.database.sqlite.SQLiteException) { }
        assertFalse(f.expense.isDeleted); assertTrue(f.db.syncQueueDao.getAllPending().isEmpty())
    } }

    @Test fun reminderHasCorrectRecipientAmountAndSurvivesReadAndPaymentChanges() = runBlocking { fixture { f ->
        val reminders = LocalReminderRepository(f.db) { f.account }
        val first = reminders.remind("creditor", "g", f.transfer, f.reference)
        assertEquals(first, reminders.remind("creditor", "g", f.transfer, f.reference))
        assertEquals(1, f.db.syncQueueDao.getAllPending().size)
        f.account = "debtor"
        val home = f.dashboard.observeHomeData(f.user("debtor")).first()
        assertEquals(0, home.sync.pendingCount)
        assertNull(com.example.getyourmoneyupnotyourfunnyup.data.sync.DatabaseSyncQueueStore(f.db).nextPendingItem("debtor"))
        val payment = home.finances!!.outstandingPayments.single()
        assertEquals("creditor", payment.creditorName)
        assertEquals(first.obligationId, payment.id)
        assertEquals(4, home.groups.single().colourIndex); assertTrue(home.groups.single().isFavourite)
        val event = f.dashboard.observeActivityData(f.user("debtor")).first().events.orEmpty().first { it.id == first.id }
        assertTrue(event.unread); assertTrue(event.canPay("debtor")); assertEquals(10000L, event.amountCents)
        f.dashboard.markRead("debtor", setOf(first.id))
        assertFalse(f.dashboard.observeActivityData(f.user("debtor")).first().events.orEmpty().first { it.id == first.id }.unread)
        f.dashboard.settlementActions.recordSettlement(payment)
        f.dashboard.settlementActions.recordSettlement(payment)
        assertEquals(1, f.db.settlementDao.getSettlementsForGroup("g").size)
        val paid = f.dashboard.observeActivityData(f.user("debtor")).first()
        assertFalse(paid.events.orEmpty().first { it.id == first.id }.canPay("debtor"))
        assertEquals(SyncStatus.PENDING_INSERT, paid.paymentStatuses[payment.id])
        assertTrue(f.dashboard.observeHomeData(f.user("debtor")).first().finances!!.outstandingPayments.isEmpty())
    } }

    @Test fun remindersRespectPreferencesActorAndStaleBalance() = runBlocking { fixture { f ->
        val reminders = LocalReminderRepository(f.db) { f.account }
        f.account = "debtor"; rejects { reminders.remind("debtor", "g", f.transfer, f.reference) }
        f.account = "creditor"; rejects { reminders.remind("creditor", "g", f.transfer, "stale") }
        f.db.writableDatabase.execSQL("UPDATE users SET notify_payment_reminders = 0 WHERE id = 'debtor'")
        rejects { reminders.remind("creditor", "g", f.transfer, f.reference) }
        assertTrue(f.db.reminderDao.forGroup("g").isEmpty())
        assertTrue(f.db.syncQueueDao.getAllPending().isEmpty())
    } }

    @Test fun remoteGroupRefreshPreservesLedgerAndProtectsPendingExpense() = runBlocking { fixture { f ->
        val cache = RemoteLedgerCache(f.db, "creditor") { f.account }
        cache.group("g", GroupDocumentMapper.toMap(f.group.copy(name = "New name", updatedAt = 2), f.members))
        assertEquals("New name", f.db.groupDao.getGroupById("g")!!.name)
        assertEquals(10000L, f.expense.amountCents)
        assertEquals(1, f.expense.splits.size)
        assertTrue(f.db.groupDao.getGroupById("g")!!.isFavourite)
        f.db.writableDatabase.execSQL("UPDATE expenses SET title = 'Local edit', sync_status = 'PENDING_UPDATE' WHERE id = 'e'")
        cache.expense("g", "e", ExpenseDocumentMapper.toMap(f.row.copy(title = "Remote edit"), listOf(f.split)))
        assertEquals("Local edit", f.expense.title)
        cache.revokeAbsentGroups(emptySet())
        try { LocalBalanceRepository(f.db) { f.account }.observeGroup("creditor", "g").first(); fail("Expected revocation") }
        catch (_: SecurityException) { }
    } }

    @Test fun cloudReceiptCachesImageWithoutChangingFinancialStateOrOutbox() = runBlocking { fixture { f ->
        f.db.expenseDao.upsertExpenseWithSplits(f.row.copy(receiptRemotePath = "receipts/test-${System.nanoTime()}.png"), listOf(f.split))
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        var downloads = 0
        val repo = ReceiptRepository(context, f.db, { f.account }, ReceiptDownload { downloads++; bytes })
        val uri = repo.load("creditor", "g", f.expense)!!
        try {
            assertEquals(uri, f.expense.receiptLocalUri)
            assertEquals(SyncStatus.SYNCED, f.expense.syncStatus)
            assertTrue(f.db.syncQueueDao.getAllPending().isEmpty())
            assertEquals(uri, repo.load("creditor", "g", f.expense)); assertEquals(1, downloads)
            f.account = "other"; rejects { repo.load("creditor", "g", f.expense) }
        } finally { java.io.File(android.net.Uri.parse(uri).path!!).delete() }
    } }
    @Test fun verifiedAuthEmailUpdatesLookupAndOutboxWithoutDiscardingProfileOrMembers() = runBlocking { fixture { f ->
        val current = f.db.userDao.getUserById("creditor")!!
        f.db.userDao.upsertUser(current.copy(displayName = "Local name", syncStatus = SyncStatus.PENDING_UPDATE))
        val saved = f.db.userDao.reconcileAuthenticatedEmail("creditor", "verified@test.dk")
        assertEquals("verified@test.dk", saved.email); assertEquals("Local name", saved.displayName)
        assertEquals(3, f.db.groupDao.getMembersForGroup("g").size)
        assertEquals(1, f.db.syncQueueDao.getAllPending().size)
        f.db.userDao.reconcileAuthenticatedEmail("creditor", "verified@test.dk")
        assertEquals(1, f.db.syncQueueDao.getAllPending().size)
    } }

    @Test fun versionFourMigrationKeepsLedgerAndReminderReadStateSurvivesRestart() = runBlocking {
        val name = "finish-migration-${java.util.UUID.randomUUID()}.db"
        var db = AppDatabase(context, name)
        try {
            Fixture(db)
            db.writableDatabase.execSQL("DROP TABLE reminders")
            db.writableDatabase.execSQL("DROP TABLE activity_reads")
            db.writableDatabase.version = 4
            db.close(); db = AppDatabase(context, name)
            assertEquals(5, db.readableDatabase.version)
            assertEquals(10000L, db.expenseDao.getExpenseById("e")!!.amountCents)
            val f = Fixture(db)
            val reminder = LocalReminderRepository(db) { "creditor" }.remind("creditor", "g", f.transfer, f.reference)
            DashboardDataSource(db) { "debtor" }.markRead("debtor", setOf(reminder.id))
            db.close(); db = AppDatabase(context, name)
            val user = db.userDao.getUserById("debtor")!!.toDomain()
            val data = DashboardDataSource(db) { "debtor" }.observeActivityData(user).first()
            val event = data.events.orEmpty().first { it.id == reminder.id }
            assertFalse(event.unread); assertEquals(10000L, event.amountCents); assertTrue(event.canPay("debtor"))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun payAllCanRecordTwoCreditorsWithoutInvalidatingUnchangedSecondObligation() = runBlocking { fixture { f ->
        f.db.expenseDao.upsertExpenseWithSplits(f.row.copy(id = "e2", amountCents = 5000, payerId = "bystander", createdBy = "bystander"),
            listOf(f.split.copy(id = "s2", expenseId = "e2", amountOwedCents = 5000)))
        f.account = "debtor"
        val payments = f.dashboard.observeHomeData(f.user("debtor")).first().finances!!.outstandingPayments
        assertEquals(2, payments.size)
        payments.forEach { f.dashboard.settlementActions.recordSettlement(it) }
        assertEquals(2, f.db.settlementDao.getSettlementsForGroup("g").size)
        assertTrue(f.dashboard.observeHomeData(f.user("debtor")).first().finances!!.outstandingPayments.isEmpty())
    } }

    @Test fun writeDuringInitialSnapshotIsNotLostBeforeObserverStartsListening() = runBlocking { fixture { f ->
        val initialRead = CompletableDeferred<Unit>(); val releaseRead = CompletableDeferred<Unit>()
        val observer = async(Dispatchers.Default) {
            var first = true
            f.db.observeTables("expenses").take(2).onEach {
                if (first) { first = false; initialRead.complete(Unit); releaseRead.await() }
            }.toList()
        }
        withTimeout(5000) { initialRead.await() }
        f.db.notifyTableChanged("expenses")
        releaseRead.complete(Unit)
        assertEquals(2, withTimeout(5000) { observer.await() }.size)
    } }

}
