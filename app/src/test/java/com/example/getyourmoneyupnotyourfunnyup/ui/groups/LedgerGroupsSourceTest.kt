package com.example.getyourmoneyupnotyourfunnyup.ui.groups

import com.example.getyourmoneyupnotyourfunnyup.data.demo.DemoLedger
import com.example.getyourmoneyupnotyourfunnyup.data.repository.InMemoryLedgerRepository
import com.example.getyourmoneyupnotyourfunnyup.model.GroupRole
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.testing.MainDispatcherRule
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.homeData
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.CreateGroupSubmission
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.CreateGroupViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.GroupMembersUiState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.GroupMembersViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.GroupsUiState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.MemberLookupState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

/**
 * The adapter between our ledger and Bashir's group screens: the mapping must give his screens real
 * balances, his write rules must hold (members only, no duplicates, never remove yourself), and a
 * group made offline must be saved on the phone and sync later (Reliability QA).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerGroupsSourceTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val now = 1_762_800_000_000L
    private val me = DemoLedger.ME
    private val demo = DemoLedger.create(now)

    private fun TestScope.ledger(online: MutableStateFlow<Boolean> = MutableStateFlow(true)) = InMemoryLedgerRepository(
        scope = backgroundScope, isOnline = online, clock = { now }, initial = DemoLedger.create(now),
        syncDelayMs = 1_000, paymentDelayMs = 500,
    )

    private fun GroupMembersData.member(userId: String) = members.single { it.userId == userId }

    // --- Mapping ---------------------------------------------------------------------------------

    @Test
    fun groupsTab_getsRealBalances_soTheMoneyFiltersWork() {
        val state = GroupsUiState.Success(groupsListData(demo, now), filter = GroupListFilter.YouOwe)
        assertEquals(9, state.counts[GroupListFilter.All])
        assertEquals(3, state.counts[GroupListFilter.YouOwe])
        assertEquals(2, state.counts[GroupListFilter.OwedToYou])
        assertEquals(4, state.counts[GroupListFilter.Settled])
        assertEquals("You owe 412,50 kr across 3 groups", state.summary?.text)
    }

    @Test
    fun membersTab_showsRolesDietAndEachPersonsBalance() {
        val kol = groupMembersData(demo, "kol")
        assertEquals("You", kol.member(me).displayName)
        assertEquals(GroupRole.ADMIN, kol.member(me).role)
        assertEquals(setOf(DietaryRule.Alcohol), kol.member("mads").dietaryRules)
        val balances = checkNotNull(kol.balances)
        assertEquals(-8_500L, balances["ida"])
        assertTrue("Ida still owes, so removing her warns", requiresRemovalWarning(balances["ida"]))
        assertFalse("Emil is settled, so he can go straight away", requiresRemovalWarning(balances["emil"]))
    }

    @Test
    fun contacts_putPhoneNumbersInThePhoneField_andSuggestionsStayShort() = runTest {
        val jonas = checkNotNull(LedgerGroupsSource(ledger()).find("+45 22 81 40 17"))
        assertEquals("", jonas.email)
        assertEquals("+45 22 81 40 17", jonas.phone)

        val kol = groupMembersData(demo, "kol")
        assertTrue(kol.suggestions.isNotEmpty())
        assertTrue(kol.suggestions.none { s -> kol.members.any { it.userId == s.userId } })
        assertTrue(kol.suggestions.size <= LedgerGroupsSource.MAX_SUGGESTIONS)
    }

    // --- Writes ----------------------------------------------------------------------------------

    @Test
    fun createdGroup_showsUpOnHomeWithItsInvites() = runTest {
        val ledger = ledger()
        val source = LedgerGroupsSource(ledger)
        val ida = checkNotNull(source.find("ida.lund@mail.dk"))

        val created = source.createGroup(
            CreateGroupDraft("Roskilde 2027", null, 3, listOf(MemberDraft.Existing(ida), MemberDraft.Invited("Anna.B@mail.dk"))),
        )

        assertEquals(SyncStatus.PENDING_INSERT, created.syncStatus)
        assertTrue(homeData(ledger.snapshot.value, now).groups.any { it.group.id == created.groupId && it.colourIndex == 3 })
        val members = groupMembersData(ledger.snapshot.value, created.groupId)
        assertEquals(setOf(me, "ida", "invite:anna.b@mail.dk"), members.members.map { it.userId }.toSet())
        assertTrue(members.member("invite:anna.b@mail.dk").invited)
    }

    @Test
    fun find_onlyMatchesAppUsersByEmailOrPhone() = runTest {
        val source = LedgerGroupsSource(ledger())
        assertEquals("mads", source.find("MADS.H@mail.dk")?.userId)
        assertEquals("jonas", source.find("+45 22 81 40 17")?.userId)
        assertNull("A name is not an email or phone number", source.find("Mads"))
        assertNull(source.find("nobody@mail.dk"))
    }

    @Test
    fun addingAndInviting_markTheGroupForSync_andRefuseDuplicates() = runTest {
        val ledger = ledger()
        val source = LedgerGroupsSource(ledger)
        val astrid = checkNotNull(source.find("astrid.m@mail.dk"))

        assertEquals(SyncStatus.PENDING_UPDATE, source.addMember("kol", MemberDraft.Existing(astrid)))
        assertTrue(ledger.snapshot.value.group("kol")!!.members.any { it.userId == "astrid" })
        assertFailsWith("already in the group") { source.addMember("kol", MemberDraft.Existing(astrid)) }

        source.addMember("kol", MemberDraft.Invited("anna@mail.dk"))
        assertFailsWith("already invited") { source.addMember("kol", MemberDraft.Invited("ANNA@mail.dk")) }
    }

    @Test
    fun removing_neverRemovesYourself_andWithdrawsInvites() = runTest {
        val ledger = ledger()
        val source = LedgerGroupsSource(ledger)
        assertFailsWith("remove yourself") { source.removeMember("kol", me) }

        source.addMember("kol", MemberDraft.Invited("anna@mail.dk"))
        source.removeMember("kol", "invite:anna@mail.dk")
        assertTrue(ledger.snapshot.value.invites.none { it.groupId == "kol" })

        source.removeMember("kol", "emil")
        assertFailsWith("already left") { source.removeMember("kol", "emil") }
        assertFailsWith("isn't on your phone") { source.removeMember("no-such-group", "ida") }
    }

    @Test
    fun favourite_isSetNotToggled_soRepeatingItIsSafe() = runTest {
        val ledger = ledger()
        val source = LedgerGroupsSource(ledger)
        assertFalse("pad" in ledger.snapshot.value.favouriteGroupIds)

        source.setFavourite("pad", true)
        source.setFavourite("pad", true)
        assertTrue("pad" in ledger.snapshot.value.favouriteGroupIds)
    }

    @Test
    fun offlineGroup_isSavedOnThePhone_andSyncsOnReconnect() = runTest {
        val online = MutableStateFlow(false)
        val ledger = ledger(online)
        val source = LedgerGroupsSource(ledger)
        runCurrent()

        val created = source.createGroup(CreateGroupDraft("Cabin weekend", "Offline test", 0, emptyList()))
        assertEquals(SyncStatus.PENDING_INSERT, created.syncStatus)
        advanceTimeBy(60_000)
        assertEquals(SyncStatus.PENDING_INSERT, ledger.snapshot.value.group(created.groupId)!!.syncStatus)

        online.value = true
        runCurrent()
        advanceTimeBy(1_001)
        assertEquals(SyncStatus.SYNCED, ledger.snapshot.value.group(created.groupId)!!.syncStatus)
    }

    // --- End to end through Bashir's ViewModels ---------------------------------------------------

    @Test
    fun membersViewModel_warnsBeforeRemovingSomeoneWhoOwes() = runTest(mainDispatcher.dispatcher) {
        val ledger = ledger()
        val source = LedgerGroupsSource(ledger)
        val vm = GroupMembersViewModel(source.members("kol"), source, source, source)

        vm.askRemove("ida")
        assertEquals("ida", (vm.state.value as GroupMembersUiState.Success).warningFor)
        assertTrue("Nothing removed yet", ledger.snapshot.value.group("kol")!!.members.any { it.userId == "ida" })

        vm.confirmRemove("ida")
        runCurrent()
        assertTrue(ledger.snapshot.value.group("kol")!!.members.none { it.userId == "ida" })

        vm.askRemove("emil") // settled: no warning
        runCurrent()
        assertTrue(ledger.snapshot.value.group("kol")!!.members.none { it.userId == "emil" })
    }

    @Test
    fun createGroupViewModel_invitesAnUnknownContactAndSaves() = runTest(mainDispatcher.dispatcher) {
        val ledger = ledger()
        val source = LedgerGroupsSource(ledger)
        val vm = CreateGroupViewModel(source.currentUser(), source, source, source.knownContacts)

        vm.setQuery("anna.b@mail.dk")
        vm.lookUp()
        runCurrent()
        assertEquals(MemberLookupState.NotFound("anna.b@mail.dk"), vm.state.value.lookup)
        vm.sendInvite()
        vm.setName("Roskilde 2027")
        vm.create()
        runCurrent()

        val saved = vm.state.value.submission as CreateGroupSubmission.Saved
        assertTrue(ledger.snapshot.value.invites.any { it.groupId == saved.group.groupId && it.contact == "anna.b@mail.dk" })
    }

    private suspend fun assertFailsWith(messagePart: String, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected a failure containing \"$messagePart\"")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(messagePart))
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(messagePart))
        }
    }
}
