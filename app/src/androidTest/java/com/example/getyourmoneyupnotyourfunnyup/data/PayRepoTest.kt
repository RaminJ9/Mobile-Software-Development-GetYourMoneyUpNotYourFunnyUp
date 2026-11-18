package com.example.getyourmoneyupnotyourfunnyup.data

import android.database.sqlite.SQLiteException
import androidx.test.platform.app.InstrumentationRegistry
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.*
import com.example.getyourmoneyupnotyourfunnyup.data.repository.*
import com.example.getyourmoneyupnotyourfunnyup.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class PayRepoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var name: String
    private var account: String? = "you"
    private val transfer = DebtTransfer("you", "ida", 5000, "DKK")
    private fun repository() = LocalPaymentRepository(db) { account }

    @Before fun setup() {
        name = "payment_test_${UUID.randomUUID()}.db"
        db = AppDatabase(context, name)
        db.userDao.upsertUser(UserEntity("you", "you@test", "Ramin"))
        db.userDao.upsertUser(UserEntity("ida", "ida@test", "Ida"))
        db.userDao.upsertUser(UserEntity("other", "other@test", "Other"))
        seedGroup("g")
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }
    private fun seedGroup(id: String, currency: String = "DKK") {
        db.groupDao.upsertGroupWithMembers(GroupEntity(id, "Trip", currency = currency, createdBy = "ida"),
            listOf(GroupMemberEntity(id, "you"), GroupMemberEntity(id, "ida"), GroupMemberEntity(id, "other")))
        db.expenseDao.upsertExpenseWithSplits(ExpenseEntity("e-$id", id, "Food", 10000,
            currency = currency, payerId = "ida", createdBy = "ida"), listOf(
            ExpenseSplitEntity("s1-$id", "e-$id", "you", 5000), ExpenseSplitEntity("s2-$id", "e-$id", "ida", 5000)))
    }
    private suspend fun rejected(type: Class<out Throwable>, action: suspend () -> Unit) {
        val error = try { action(); null } catch (failure: Throwable) { failure }
        assertNotNull("Expected a rejected payment", error)
        assertTrue("Unexpected error: $error", type.isInstance(error))
    }
    private fun assertNoPayments() {
        assertTrue(db.settlementDao.getSettlementsForGroup("g").isEmpty())
        assertTrue(db.syncQueueDao.getAllPending().isEmpty())
    }

    @Test fun savesSimulatedPaymentAndCompleteOutboxPayloadAtomically() = runBlocking {
        val saved = repository().pay("you", "g", transfer, "request")
        assertEquals("Simulated", saved.paymentMethod)
        assertEquals(SettlementStatus.COMPLETED, saved.status)
        assertEquals(SyncStatus.PENDING_INSERT, saved.syncStatus)
        assertEquals(saved, db.settlementDao.getSettlementsForGroup("g").single().toDomain())
        val queue = db.syncQueueDao.getAllPending().single()
        assertEquals(SyncEntityType.SETTLEMENT, queue.entityType)
        assertEquals(SyncAction.CREATE, queue.action)
        assertEquals(saved.id, queue.entityId)
        val payload = JSONObject(queue.payloadJson)
        assertEquals("you", payload.getString("debtorId")); assertEquals("ida", payload.getString("creditorId"))
        assertEquals("g", payload.getString("groupId")); assertEquals(5000L, payload.getLong("amountCents"))
        assertEquals(saved.createdAt, payload.getLong("createdAt"))
        assertEquals("DKK", payload.getString("currency")); assertEquals("COMPLETED", payload.getString("status"))
    }
    @Test fun offlinePaymentSurvivesReopenAndClearsBalancesBeforeSync() = runBlocking {
        repository().pay("you", "g", transfer, "request")
        db.close(); db = AppDatabase(context, name)
        val snapshot = withTimeout(5.seconds) { LocalBalanceRepository(db) { account }.observeGroup("you", "g").first() }
        val balances = BalanceCalculator.calculate("g", "DKK", snapshot.group.members.map { it.userId }, snapshot.expenses, snapshot.settlements)
        assertTrue(balances.all { it.netBalanceCents == 0L })
        assertTrue(SettlementCalculator.suggest(balances).isEmpty())
        assertEquals(SyncStatus.PENDING_INSERT, snapshot.settlements.single().syncStatus)
        assertEquals(1, db.syncQueueDao.getAllPending().size)
    }
    @Test fun retryingSameRequestDoesNotDoublePay() = runBlocking {
        val first = repository().pay("you", "g", transfer, "request")
        val second = repository().pay("you", "g", transfer, "request")
        assertEquals(first, second)
        assertEquals(1, db.settlementDao.getSettlementsForGroup("g").size)
        assertEquals(1, db.syncQueueDao.getAllPending().size)
    }
    @Test fun reusedRequestCannotChangeItsPayment() = runBlocking {
        repository().pay("you", "g", transfer, "request")
        rejected(IllegalArgumentException::class.java) { repository().pay("you", "g", transfer.copy(amountCents = 1), "request") }
        assertEquals(5000L, db.settlementDao.getSettlementsForGroup("g").single().amountCents)
        assertEquals(1, db.syncQueueDao.getAllPending().size)
    }
    @Test fun staleOrWrongRecipientAmountAndCurrencyAreRejected() = runBlocking {
        for (invalid in listOf(transfer.copy(creditorId = "other"), transfer.copy(amountCents = 6000),
            transfer.copy(amountCents = 0), transfer.copy(amountCents = -1), transfer.copy(currency = "EUR"),
            transfer.copy(creditorId = "you"))) {
            rejected(IllegalArgumentException::class.java) { repository().pay("you", "g", invalid, UUID.randomUUID().toString()) }
        }
        db.expenseDao.markExpenseDeleted("e-g")
        rejected(IllegalArgumentException::class.java) { repository().pay("you", "g", transfer, "stale") }
        assertNoPayments()
    }
    @Test fun sessionAndMembershipAreCheckedIncludingRetries() = runBlocking {
        account = "other"
        rejected(SecurityException::class.java) { repository().pay("you", "g", transfer, "request") }
        account = "you"
        rejected(SecurityException::class.java) { repository().pay("you", "g", transfer.copy(debtorId = "other"), "request") }
        assertNoPayments()
        repository().pay("you", "g", transfer, "request")
        db.groupDao.removeMember("g", "you")
        rejected(SecurityException::class.java) { repository().pay("you", "g", transfer, "request") }
        assertEquals(1, db.settlementDao.getSettlementsForGroup("g").size)
    }
    @Test fun queueInsertFailureRollsBackSettlementToo() = runBlocking {
        db.writableDatabase.execSQL("""CREATE TEMP TRIGGER reject_payment_queue BEFORE INSERT ON "sync_queue"
            BEGIN SELECT RAISE(ABORT, 'forced test failure'); END""")
        rejected(SQLiteException::class.java) { repository().pay("you", "g", transfer, "request") }
        assertNoPayments()
        db.writableDatabase.execSQL("DROP TRIGGER reject_payment_queue")
        repository().pay("you", "g", transfer, "request")
        assertEquals(1, db.settlementDao.getSettlementsForGroup("g").size)
    }
    @Test fun simultaneousDuplicateConfirmationsCreateOnlyOneRecord() = runBlocking {
        val results = coroutineScope {
            List(2) { async(Dispatchers.IO) { repository().pay("you", "g", transfer, "same-request") } }.awaitAll()
        }
        assertEquals(results[0], results[1])
        assertEquals(1, db.settlementDao.getSettlementsForGroup("g").size)
        assertEquals(1, db.syncQueueDao.getAllPending().size)
    }
    @Test fun separateRequestsCannotPayTheSameDebtTwice() = runBlocking {
        val results = coroutineScope {
            List(2) { index -> async(Dispatchers.IO) { runCatching { repository().pay("you", "g", transfer, "request-$index") } } }.awaitAll()
        }
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, db.settlementDao.getSettlementsForGroup("g").size)
        assertEquals(1, db.syncQueueDao.getAllPending().size)
    }
    @Test fun requestCollisionInOtherGroupDoesNotOverwritePayment() = runBlocking {
        repository().pay("you", "g", transfer, "request")
        seedGroup("g2")
        rejected(SQLiteException::class.java) { repository().pay("you", "g2", transfer, "request") }
        assertEquals(1, db.settlementDao.getSettlementsForGroup("g").size)
        assertTrue(db.settlementDao.getSettlementsForGroup("g2").isEmpty())
        assertEquals(1, db.syncQueueDao.getAllPending().size)
    }
    @Test fun committedPaymentNotifiesBalanceObservers() = runBlocking {
        val snapshots = Channel<BalanceLedger>(Channel.UNLIMITED)
        val observation = launch(Dispatchers.IO) {
            LocalBalanceRepository(db) { account }.observeGroup("you", "g").collect { snapshots.send(it) }
        }
        try {
            withTimeout(5.seconds) { snapshots.receive() }
            repository().pay("you", "g", transfer, "request")
            val after = withTimeout(5.seconds) { snapshots.receive() }
            assertEquals("request", after.settlements.single().id)
            assertEquals(1, db.syncQueueDao.getAllPending().size)
        } finally { observation.cancelAndJoin(); snapshots.close() }
    }
}
