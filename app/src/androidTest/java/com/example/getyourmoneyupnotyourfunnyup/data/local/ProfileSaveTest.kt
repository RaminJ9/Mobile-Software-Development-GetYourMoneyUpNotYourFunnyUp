package com.example.getyourmoneyupnotyourfunnyup.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.UserEntity
import com.example.getyourmoneyupnotyourfunnyup.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileSaveTest {
    private lateinit var database: AppDatabase
    private val initial = UserEntity("u", "u@test", "Ramin", count = 7, updatedAt = 1L)
    @Before fun setUp() {
        database = AppDatabase.createInMemory(InstrumentationRegistry.getInstrumentation().targetContext)
        database.userDao.upsertUser(initial)
    }
    @After fun tearDown() = database.close()
    private fun changes(name: String = "Changed") = ProfileChanges.fromUser(initial.toDomain()).copy(displayName = name)

    @Test fun profileAndOutboxCommitTogetherWithCorrectPatch() {
        val (saved, _) = database.userDao.saveProfileAndEnqueue("u", changes().copy(phoneNumber = null, excludeAlcohol = true))
        assertEquals(saved.updatedAt, database.userDao.getUserById("u")!!.updatedAt)
        assertEquals(SyncStatus.PENDING_UPDATE, saved.syncStatus)
        val queue = database.syncQueueDao.getAllPending().single()
        val payload = JSONObject(queue.payloadJson)
        assertEquals("Changed", payload.getString("displayName"))
        assertTrue(payload.getBoolean("excludeAlcohol"))
        assertTrue(payload.isNull("phoneNumber"))
        assertFalse(payload.has("email"))
        assertFalse(payload.has("count"))
        assertEquals(initial.email, saved.email)
        assertEquals(initial.count, saved.count)
    }

    @Test fun outboxFailureRollsBackTheProfileWrite() {
        database.writableDatabase.execSQL("CREATE TRIGGER reject_test_outbox BEFORE INSERT ON sync_queue BEGIN SELECT RAISE(ABORT, 'outbox unavailable'); END")
        assertTrue(runCatching { database.userDao.saveProfileAndEnqueue("u", changes()) }.isFailure)
        assertEquals(initial, database.userDao.getUserById("u"))
        assertTrue(database.syncQueueDao.getAllPending().isEmpty())
    }

    @Test fun remoteReadsCannotOverwritePendingOrFailedEdits() = runBlocking {
        val (saved, queueId) = database.userDao.saveProfileAndEnqueue("u", changes())
        assertEquals(saved.displayName, database.userDao.cacheRemoteUser(initial.copy(updatedAt = saved.updatedAt + 10)).displayName)
        database.userDao.finishProfileUpload("u", queueId, saved.updatedAt, "Offline")
        database.userDao.cacheRemoteUser(initial.copy(updatedAt = saved.updatedAt + 20))
        val local = database.userDao.observeUserById("u").first()!!
        assertEquals("Changed", local.displayName)
        assertEquals(SyncStatus.FAILED, local.syncStatus)
        assertEquals("Offline", database.syncQueueDao.getAllPending().single().errorMessage)
    }

    @Test fun olderAcknowledgementCannotClearANewerEdit() {
        val (first, firstId) = database.userDao.saveProfileAndEnqueue("u", changes("First"))
        val (second, secondId) = database.userDao.saveProfileAndEnqueue("u", changes("Second"))
        database.userDao.finishProfileUpload("u", firstId, first.updatedAt)
        assertEquals("Second", database.userDao.getUserById("u")!!.displayName)
        assertEquals(SyncStatus.PENDING_UPDATE, database.userDao.getUserById("u")!!.syncStatus)
        assertEquals(secondId, database.syncQueueDao.getAllPending().single().queueId)
        database.userDao.finishProfileUpload("u", secondId, second.updatedAt)
        assertEquals(SyncStatus.SYNCED, database.userDao.getUserById("u")!!.syncStatus)
        assertTrue(database.syncQueueDao.getAllPending().isEmpty())
        database.userDao.cacheRemoteUser(initial)
        assertEquals("Second", database.userDao.getUserById("u")!!.displayName)
    }

    @Test fun failedOlderUploadAndWrongAccountResultDoNotChangeNewerSave() {
        val (first, firstId) = database.userDao.saveProfileAndEnqueue("u", changes("First"))
        val (second, secondId) = database.userDao.saveProfileAndEnqueue("u", changes("Second"))
        database.userDao.finishProfileUpload("u", firstId, first.updatedAt, "Old failure")
        database.userDao.finishProfileUpload("other", secondId, second.updatedAt)
        assertEquals("Second", database.userDao.getUserById("u")!!.displayName)
        assertEquals(SyncStatus.PENDING_UPDATE, database.userDao.getUserById("u")!!.syncStatus)
        assertEquals(2, database.syncQueueDao.getAllPending().size)
    }
}
