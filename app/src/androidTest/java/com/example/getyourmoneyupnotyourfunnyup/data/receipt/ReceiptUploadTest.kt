package com.example.getyourmoneyupnotyourfunnyup.data.receipt

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.GroupEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.GroupMemberEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.UserEntity
import com.example.getyourmoneyupnotyourfunnyup.data.receipt.ReceiptUploader.Outcome
import com.example.getyourmoneyupnotyourfunnyup.data.repository.ExpenseRepository
import com.example.getyourmoneyupnotyourfunnyup.data.repository.GroupRepository
import com.example.getyourmoneyupnotyourfunnyup.data.repository.LocalScanSource
import com.example.getyourmoneyupnotyourfunnyup.model.Expense
import com.example.getyourmoneyupnotyourfunnyup.model.ExpenseSplit
import com.example.getyourmoneyupnotyourfunnyup.model.User
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Uploading receipt photos, with a real (in-memory) database and a stand-in for cloud storage, so
 * nothing leaves the phone.
 */
@RunWith(AndroidJUnit4::class)
class ReceiptUploadTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val me = User("me", "victor@example.dk", "Victor")
    private lateinit var database: AppDatabase
    private lateinit var photo: File

    private class FakeStorage(var failure: Exception? = null, val permanent: Boolean = false) : ReceiptStorage {
        val uploaded = mutableListOf<Pair<String, File>>()
        override suspend fun put(path: String, photo: File) {
            failure?.let { throw it }
            uploaded += path to photo
        }
        override fun isPermanent(error: Exception) = permanent
    }

    @Before
    fun setUp() {
        database = AppDatabase.createInMemory(context)
        database.userDao.upsertUser(UserEntity(me.id, me.email, me.displayName))
        database.userDao.upsertUser(UserEntity("ida", "ida@example.dk", "Ida"))
        database.groupDao.upsertGroupWithMembers(
            GroupEntity("g", "Kollegiet", currency = "DKK", createdBy = me.id),
            listOf(GroupMemberEntity("g", me.id), GroupMemberEntity("g", "ida")),
        )
        photo = File(context.cacheDir, "upload-test.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    }

    @After
    fun tearDown() {
        database.close()
        photo.delete()
    }

    private fun expense(createdBy: String = me.id, photoUri: String? = Uri.fromFile(photo).toString()) = Expense(
        id = "e1", groupId = "g", title = "Netto", amountCents = 10_000, currency = "DKK", payerId = createdBy,
        receiptLocalUri = photoUri, createdBy = createdBy,
        splits = listOf(ExpenseSplit("e1-me", "e1", me.id, 5_000), ExpenseSplit("e1-ida", "e1", "ida", 5_000)),
    )

    private fun uploader(storage: ReceiptStorage, account: String? = me.id) = ReceiptUploader(database, { account }, storage)

    @Test
    fun yourReceiptPhoto_isUploaded_andItsPathQueuedForTheOtherMembers() = runBlocking {
        ExpenseRepository(database).saveExpense(expense())
        val storage = FakeStorage()

        assertEquals(Outcome.Uploaded, uploader(storage).upload("e1"))

        assertEquals(listOf("receipts/g/e1.jpg" to photo), storage.uploaded)
        val stored = checkNotNull(database.expenseDao.getExpenseById("e1"))
        assertEquals("receipts/g/e1.jpg", stored.receiptRemotePath)
        assertEquals("The photo on this phone is kept", Uri.fromFile(photo).toString(), stored.receiptLocalUri)
        val queued = database.syncQueueDao.getAllPending().filter { it.entityId == "e1" }
        assertTrue("The path is queued for Firestore", queued.last().payloadJson.contains("receipts/g/e1.jpg"))

        // Already uploaded: nothing happens the second time.
        assertEquals(Outcome.NothingToDo, uploader(storage).upload("e1"))
        assertEquals(1, storage.uploaded.size)
    }

    @Test
    fun noAccessToStorage_givesUp_butNetworkTroubleTriesAgain_andTheExpenseIsUnchanged() = runBlocking {
        ExpenseRepository(database).saveExpense(expense())

        assertEquals(Outcome.GiveUp, uploader(FakeStorage(IllegalStateException("not authorized"), permanent = true)).upload("e1"))
        assertEquals(Outcome.TryLater, uploader(FakeStorage(IllegalStateException("offline"))).upload("e1"))
        assertNull(database.expenseDao.getExpenseById("e1")?.receiptRemotePath)
        assertEquals("Only the expense's own sync entry", 1, database.syncQueueDao.getAllPending().count { it.entityId == "e1" })
    }

    @Test
    fun nothingIsUploaded_forSomeoneElsesExpense_aTypedInExpense_orAPhotoThatIsGone() = runBlocking {
        val storage = FakeStorage()
        ExpenseRepository(database).saveExpense(expense(createdBy = "ida"))
        assertEquals(Outcome.NothingToDo, uploader(storage).upload("e1"))

        ExpenseRepository(database).saveExpense(expense(photoUri = null))
        assertEquals(Outcome.NothingToDo, uploader(storage).upload("e1"))

        ExpenseRepository(database).saveExpense(expense(photoUri = Uri.fromFile(File(context.cacheDir, "missing.jpg")).toString()))
        assertEquals(Outcome.NothingToDo, uploader(storage).upload("e1"))

        assertEquals(Outcome.NothingToDo, uploader(storage).upload("not-an-expense"))
        assertTrue(storage.uploaded.isEmpty())
    }

    @Test
    fun whileSignedOut_itTriesAgainLater() = runBlocking {
        ExpenseRepository(database).saveExpense(expense())
        assertEquals(Outcome.TryLater, uploader(FakeStorage(), account = null).upload("e1"))
    }

    @Test
    fun atStartUp_onlyYourPhotosThatNeverReachedStorage_areUploadedAgain() = runBlocking {
        val repository = ExpenseRepository(database)
        fun splitsFor(id: String) = listOf(ExpenseSplit("$id-me", id, me.id, 5_000), ExpenseSplit("$id-ida", id, "ida", 5_000))
        repository.saveExpense(expense().copy(id = "waiting", splits = splitsFor("waiting")))
        repository.saveExpense(expense().copy(id = "done", splits = splitsFor("done"), receiptRemotePath = "receipts/g/done.jpg"))
        repository.saveExpense(expense(createdBy = "ida").copy(id = "idas", splits = splitsFor("idas")))
        repository.saveExpense(expense(photoUri = null).copy(id = "typed", splits = splitsFor("typed")))

        val scheduled = mutableListOf<String>()
        ReceiptUploads { scheduled += it }.scheduleMissing(database, me.id)

        assertEquals(listOf("waiting"), scheduled)
    }

    @Test
    fun savingAScannedReceipt_startsTheUpload_butATypedInExpenseDoesNot() = runBlocking {
        val scheduled = mutableListOf<String>()
        val source = LocalScanSource(database, GroupRepository(database, me, remoteUsers = null), ExpenseRepository(database)) { scheduled += it }

        source.save(expense())
        source.save(expense(photoUri = null).copy(id = "e2", splits = expense().splits.map { it.copy(id = it.id.replace("e1", "e2"), expenseId = "e2") }))

        assertEquals(listOf("e1"), scheduled)
    }
}
