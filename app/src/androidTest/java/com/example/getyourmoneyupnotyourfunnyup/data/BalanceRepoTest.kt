package com.example.getyourmoneyupnotyourfunnyup.data

import androidx.test.platform.app.InstrumentationRegistry
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.*
import com.example.getyourmoneyupnotyourfunnyup.data.repository.LocalBalanceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BalanceRepoTest {
    @Test fun savedGroupRemainsReadableAfterDatabaseReopensWithoutNetwork() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "balance_test_${UUID.randomUUID()}.db"
        var db = AppDatabase(context, name)
        try {
            db.userDao.upsertUser(UserEntity("you", "you@test", "Ramin"))
            db.groupDao.upsertGroupWithMembers(GroupEntity("g", "Trip", currency = "DKK", createdBy = "you"),
                listOf(GroupMemberEntity("g", "you")))
            db.close(); db = AppDatabase(context, name)
            val snapshot = withTimeout(5000) { LocalBalanceRepository(db) { "you" }.observeGroup("you", "g").first() }
            assertEquals("Trip", snapshot.group.name)
            assertEquals("Ramin", snapshot.namesByUserId["you"])
            assertEquals("DKK", snapshot.group.currency)
            assertTrue(snapshot.expenses.isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun differentSessionCannotReadCachedGroup() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.createInMemory(context)
        try {
            try {
                withTimeout(5000) { LocalBalanceRepository(db) { "other" }.observeGroup("you", "g").first() }
                fail("Expected session check")
            } catch (_: SecurityException) { /* expected */ }
        } finally { db.close() }
    }
}
