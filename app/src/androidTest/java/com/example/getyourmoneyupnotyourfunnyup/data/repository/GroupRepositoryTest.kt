package com.example.getyourmoneyupnotyourfunnyup.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SyncAction
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SyncEntityType
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.UserEntity
import com.example.getyourmoneyupnotyourfunnyup.model.GroupRole
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.ui.groups.CreateGroupDraft
import com.example.getyourmoneyupnotyourfunnyup.ui.groups.MemberDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Reliability QA: group writes are saved on the phone and queued, with no network involved. */
@RunWith(AndroidJUnit4::class)
class GroupRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: GroupRepository
    private val me = User("you", "victor@mail.dk", "Victor")

    @Before fun setUp() {
        database = AppDatabase.createInMemory(InstrumentationRegistry.getInstrumentation().targetContext)
        database.userDao.upsertUser(UserEntity("mads", "mads.h@mail.dk", "Mads", phone = "+4522814017"))
        var id = 0
        repository = GroupRepository(database, me, clock = { 1_000L }, newId = { "g${++id}" })
    }

    @After fun tearDown() = database.close()

    @Test fun createGroupOfflineSavesLocallyAndQueuesSync() = runBlocking {
        val mads = repository.find("+45 22 81 40 17")!!
        val created = repository.createGroup(
            CreateGroupDraft("Roskilde 2027", null, 2, listOf(MemberDraft.Existing(mads), MemberDraft.Invited("anna@mail.dk")))
        )
        assertEquals(SyncStatus.PENDING_INSERT, created.syncStatus)
        val group = repository.observeGroups().first().single()
        assertEquals("Roskilde 2027", group.group.name)
        assertEquals(2, group.colourIndex)
        assertEquals(3, group.group.members.size)
        val members = database.groupDao.getMembersForGroup("g1")
        assertEquals(GroupRole.ADMIN, members.first { it.userId == "you" }.role)
        assertTrue(members.first { it.userId == "invite:anna@mail.dk" }.invited)
        val queued = database.syncQueueDao.getAllPending().single()
        assertEquals(SyncEntityType.GROUP, queued.entityType)
        assertEquals(SyncAction.CREATE, queued.action)
        assertTrue(queued.payloadJson.contains("Roskilde 2027"))
    }

    @Test fun favouriteAndMemberChangesKeepMembersAndQueueUpdates() = runBlocking {
        repository.createGroup(CreateGroupDraft("Trip", null, 0, emptyList()))
        repository.setFavourite("g1", true)
        assertTrue(repository.observeGroups().first().single().isFavourite)
        assertEquals(1, database.groupDao.getMembersForGroup("g1").size)

        assertEquals(SyncStatus.PENDING_INSERT, repository.addMember("g1", MemberDraft.Existing(repository.find("MADS.H@mail.dk")!!)))
        assertEquals(SyncStatus.PENDING_INSERT, repository.removeMember("g1", "mads"))
        assertEquals(1, database.groupDao.getMembersForGroup("g1").size)
        assertEquals(3, database.syncQueueDao.getAllPending().size)
        assertNull(repository.find("nobody@mail.dk"))
    }
}
