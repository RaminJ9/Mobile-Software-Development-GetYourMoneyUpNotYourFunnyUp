package com.example.getyourmoneyupnotyourfunnyup.data.remote

import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.ExpenseEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.ExpenseSplitEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.GroupEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.GroupMemberEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SettlementEntity
import com.example.getyourmoneyupnotyourfunnyup.model.Expense
import com.example.getyourmoneyupnotyourfunnyup.model.ExpenseSplit
import com.example.getyourmoneyupnotyourfunnyup.model.Group
import com.example.getyourmoneyupnotyourfunnyup.model.GroupMember
import com.example.getyourmoneyupnotyourfunnyup.model.GroupRole
import com.example.getyourmoneyupnotyourfunnyup.model.Settlement
import com.example.getyourmoneyupnotyourfunnyup.model.SettlementStatus
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import org.junit.Assert.*
import org.junit.Test

class EntityDocumentMappersTest {

    @Test
    fun groupDocumentMapper_roundTripsEntitiesAndExcludesInvitedFromMemberIds() {
        val group = GroupEntity(
            id = "grp_1",
            name = "Ski trip Åre",
            description = "Cabin & lift passes",
            currency = "DKK",
            createdBy = "u_victor",
            createdAt = 1000L,
            updatedAt = 2000L,
            syncStatus = SyncStatus.PENDING_INSERT,
            colourIndex = 3,
            isFavourite = true
        )
        val members = listOf(
            GroupMemberEntity("grp_1", "u_victor", GroupRole.ADMIN, 1000L, invited = false),
            GroupMemberEntity("grp_1", "u_mads", GroupRole.MEMBER, 1001L, invited = false),
            GroupMemberEntity("grp_1", "invite:anna@mail.dk", GroupRole.MEMBER, 1002L, invited = true)
        )
        val names = mapOf(
            "u_victor" to "Victor",
            "u_mads" to "Mads",
            "invite:anna@mail.dk" to "anna"
        )

        val remoteMap = GroupDocumentMapper.toMap(group, members, names)
        assertEquals(listOf("u_victor", "u_mads"), remoteMap["memberIds"])
        assertEquals(3, remoteMap["colourIndex"])

        val json = SyncPayloadJson.encode(remoteMap)
        val decodedMap = SyncPayloadJson.decodeObject(json)

        val (restoredGroup, restoredMembers) = GroupDocumentMapper.entitiesFromMap(
            data = decodedMap,
            groupId = "grp_1",
            isFavourite = true
        )
        assertEquals(group.copy(syncStatus = SyncStatus.SYNCED), restoredGroup)
        assertEquals(members, restoredMembers)

        val restoredDomain = GroupDocumentMapper.fromMap(decodedMap, "grp_1")
        assertEquals("Ski trip Åre", restoredDomain.name)
        assertEquals(3, restoredDomain.members.size)
        assertEquals("Victor", restoredDomain.members.first().displayName)
        assertEquals(GroupRole.ADMIN, restoredDomain.members.first().role)
    }

    @Test
    fun groupDocumentMapper_fallsBackToMemberIdsWhenMembersArrayMissing() {
        val raw = mapOf<String, Any?>(
            "name" to "Friday Bar",
            "createdBy" to "u1",
            "memberIds" to listOf("u1", "u2")
        )
        val domain = GroupDocumentMapper.fromMap(raw, "g_bar", nowMillis = 500L)
        assertEquals("g_bar", domain.id)
        assertEquals("DKK", domain.currency)
        assertEquals(2, domain.members.size)
        assertEquals(GroupRole.ADMIN, domain.members[0].role)
        assertEquals(GroupRole.MEMBER, domain.members[1].role)
    }

    @Test
    fun expenseDocumentMapper_roundTripsDomainAndEntitiesThroughJson() {
        val splits = listOf(
            ExpenseSplit("sp_1", "exp_1", "u_victor", 12500L, 1.0),
            ExpenseSplit("sp_2", "exp_1", "u_mads", 12500L, 1.0)
        )
        val expense = Expense(
            id = "exp_1",
            groupId = "grp_1",
            title = "Netto groceries",
            amountCents = 25000L,
            currency = "DKK",
            payerId = "u_victor",
            category = "Groceries",
            notes = "Shared dinner",
            receiptLocalUri = "file:///local/receipt.jpg",
            receiptRemotePath = "receipts/grp_1/exp_1.jpg",
            splits = splits,
            createdAt = 3000L,
            createdBy = "u_victor",
            isDeleted = false,
            syncStatus = SyncStatus.PENDING_INSERT
        )

        val map = ExpenseDocumentMapper.toMap(expense)
        assertEquals("receipts/grp_1/exp_1.jpg", map["receiptStoragePath"])

        val decoded = SyncPayloadJson.decodeObject(SyncPayloadJson.encode(map))
        val (entity, splitEntities) = ExpenseDocumentMapper.entitiesFromMap(
            data = decoded,
            expenseId = "exp_1",
            receiptLocalUri = expense.receiptLocalUri
        )
        assertEquals(
            ExpenseEntity.fromDomain(expense.copy(syncStatus = SyncStatus.SYNCED)),
            entity
        )
        assertEquals(splits.map(ExpenseSplitEntity::fromDomain), splitEntities)

        val domain = ExpenseDocumentMapper.fromMap(decoded, "exp_1")
        assertEquals(expense.copy(receiptLocalUri = null, syncStatus = SyncStatus.SYNCED), domain)
    }

    @Test
    fun settlementDocumentMapper_roundTripsSettlementAndPreservesRecordedBy() {
        val settlement = Settlement(
            id = "stl_1",
            groupId = "grp_1",
            debtorId = "u_mads",
            creditorId = "u_victor",
            amountCents = 12500L,
            currency = "DKK",
            paymentMethod = "MobilePay",
            status = SettlementStatus.COMPLETED,
            createdAt = 4000L,
            syncStatus = SyncStatus.PENDING_INSERT
        )

        val map = SettlementDocumentMapper.toMap(settlement, recordedBy = "u_mads")
        assertEquals("u_mads", map["recordedBy"])
        assertEquals("COMPLETED", map["status"])

        val decoded = SyncPayloadJson.decodeObject(SyncPayloadJson.encode(map))
        val restoredEntity = SettlementDocumentMapper.entityFromMap(decoded, "stl_1")
        assertEquals(
            SettlementEntity.fromDomain(settlement.copy(syncStatus = SyncStatus.SYNCED)),
            restoredEntity
        )
        val restoredDomain = SettlementDocumentMapper.fromMap(decoded, "stl_1")
        assertEquals(settlement.copy(syncStatus = SyncStatus.SYNCED), restoredDomain)
    }

    @Test
    fun domainGroupToMap_filtersInvitedMembersFromMemberIds() {
        val group = Group(
            id = "g1",
            name = "Summer House",
            description = null,
            currency = "DKK",
            createdBy = "u1",
            createdAt = 10L,
            updatedAt = 20L,
            members = listOf(
                GroupMember("g1", "u1", "Victor", GroupRole.ADMIN, 10L),
                GroupMember("g1", "invite:guest@mail.dk", "guest", GroupRole.MEMBER, 11L)
            )
        )
        val map = GroupDocumentMapper.toMap(group, colourIndex = 4)
        assertEquals(listOf("u1"), map["memberIds"])
        val membersList = map["members"] as List<*>
        assertEquals(2, membersList.size)
    }
}
