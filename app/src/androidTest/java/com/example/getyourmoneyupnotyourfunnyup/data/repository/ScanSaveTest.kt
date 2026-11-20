package com.example.getyourmoneyupnotyourfunnyup.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.GroupEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.GroupMemberEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.UserEntity
import com.example.getyourmoneyupnotyourfunnyup.model.BalanceCalculator
import com.example.getyourmoneyupnotyourfunnyup.model.Expense
import com.example.getyourmoneyupnotyourfunnyup.model.ExpenseSplit
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SyncAction
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SyncEntityType
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.User
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Saving a scanned receipt on the phone, with a real (in-memory) database and no network: the expense,
 * its shares and the upload queue entry are stored together, and the balance calculation accepts it.
 */
@RunWith(AndroidJUnit4::class)
class ScanSaveTest {
    private val me = User("me", "victor@example.dk", "Victor")
    private lateinit var database: AppDatabase
    private lateinit var source: LocalScanSource

    @Before
    fun setUp() {
        database = AppDatabase.createInMemory(InstrumentationRegistry.getInstrumentation().targetContext)
        database.userDao.upsertUser(UserEntity(me.id, me.email, me.displayName))
        database.userDao.upsertUser(UserEntity("mads", "mads@example.dk", "Mads", excludeAlcohol = true))
        database.userDao.upsertUser(UserEntity("ida", "ida@example.dk", "Ida", excludeMeat = true))
        database.groupDao.upsertGroupWithMembers(
            GroupEntity("g", "Kollegiet", currency = "DKK", createdBy = me.id, colourIndex = 3),
            listOf(GroupMemberEntity("g", me.id), GroupMemberEntity("g", "mads"), GroupMemberEntity("g", "ida")),
        )
        // No remote directory: everything here is local.
        val groups = GroupRepository(database, me, remoteUsers = null)
        source = LocalScanSource(database, groups, ExpenseRepository(database))
    }

    @After
    fun tearDown() = database.close()

    private fun receipt(payer: String = me.id, currency: String = "DKK", shares: Map<String, Long> = mapOf(me.id to 6_000L, "mads" to 2_000L, "ida" to 4_000L)): Expense {
        val id = "e1"
        return Expense(
            id = id, groupId = "g", title = "Netto", amountCents = shares.values.sum(), currency = currency, payerId = payer,
            category = "Receipt", receiptLocalUri = "file:///receipts/netto.jpg",
            splits = shares.map { (user, owed) -> ExpenseSplit("$id-$user", id, user, owed) }, createdBy = me.id,
        )
    }

    @Test
    fun groups_comeWithEachMembersDietRules() = runBlocking {
        val group = source.groups.first().single()
        assertEquals("Kollegiet", group.name)
        assertEquals(3, group.colourIndex)
        assertEquals("DKK", group.currency)
        val mads = group.members.single { it.id == "mads" }
        val ida = group.members.single { it.id == "ida" }
        assertTrue(mads.excludeAlcohol)
        assertFalse(mads.excludeMeat)
        assertTrue(ida.excludeMeat)
        assertEquals(GroupRepository.memberColourIndex("mads"), mads.colourIndex)
    }

    @Test
    fun aSavedReceipt_isStoredWithItsUploadQueuedAndCountsInTheBalances() = runBlocking {
        // Reliability QA: saved on the phone straight away, waiting to sync, with no network involved.
        source.save(receipt())

        val stored = checkNotNull(database.expenseDao.getExpenseById("e1"))
        assertEquals(SyncStatus.PENDING_INSERT, stored.syncStatus)
        assertEquals("file:///receipts/netto.jpg", stored.receiptLocalUri)
        assertEquals(3, database.expenseDao.getSplitsForExpense("e1").size)
        val queued = database.syncQueueDao.getAllPending().single()
        assertEquals(SyncEntityType.EXPENSE, queued.entityType)
        assertEquals(SyncAction.CREATE, queued.action)
        assertEquals("e1", queued.entityId)
        assertEquals(SyncStatus.PENDING_INSERT, source.syncStatus("e1", "g").first())

        // The scanner → balances contract: the balance calculation accepts what the scanner saved.
        val splits = database.expenseDao.getSplitsForGroup("g").map { it.toDomain() }
        val expenses = database.expenseDao.getActiveExpensesForGroup("g").map { it.toDomain(splits.filter { s -> s.expenseId == it.id }) }
        val balances = BalanceCalculator.calculate("g", "DKK", listOf(me.id, "mads", "ida"), expenses)
            .associate { it.userId to it.netBalanceCents }
        assertEquals(mapOf("ida" to -4_000L, "mads" to -2_000L, me.id to 6_000L), balances)
    }

    @Test
    fun someoneWhoLeftTheGroup_cantBeOnTheReceipt() = runBlocking {
        try {
            source.save(receipt(payer = "jonas"))
            fail("A payer who isn't a member must be refused")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("no longer in Kollegiet"))
        }
        assertNull(database.expenseDao.getExpenseById("e1"))
        assertTrue(database.syncQueueDao.getAllPending().isEmpty())
    }

    @Test
    fun aReceiptInAnotherCurrency_isRefused() = runBlocking {
        try {
            source.save(receipt(currency = "EUR"))
            fail("The expense must use the group's currency")
        } catch (expected: IllegalArgumentException) {
            assertEquals("This expense must be in DKK.", expected.message)
        }
        assertNull(database.expenseDao.getExpenseById("e1"))
    }
}
