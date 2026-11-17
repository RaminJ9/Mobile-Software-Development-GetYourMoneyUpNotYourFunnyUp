package com.example.getyourmoneyupnotyourfunnyup.ui.groups

import com.example.getyourmoneyupnotyourfunnyup.model.Group
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.DashboardGroup
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupsViewModelsTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val me = User("you", "victor@mail.dk", "Victor")
    private val mads = Contact("mads", "Mads", "mads.h@mail.dk")

    /* ---------------- Groups tab ---------------- */

    private fun groupsData(fav: Boolean = false) = GroupsListData(
        "you",
        listOf(DashboardGroup(Group("ski", "Ski trip Åre", createdBy = "you"), 1, fav)),
        mapOf("ski" to -28600L)
    )

    private fun GroupsViewModel.current() = state.value as GroupsUiState.Success

    @Test fun favouriteIsOptimisticAndSettlesWhenStoredSnapshotAgrees() = runTest(dispatcher) {
        val source = MutableStateFlow(groupsData())
        val write = CompletableDeferred<SyncStatus>()
        val vm = GroupsViewModel(source, GroupFavouriteActions { _, _ -> write.await() })
        vm.toggleFavourite("ski")
        assertTrue(vm.current().groups.single().isFavourite)
        vm.toggleFavourite("ski") // double tap while writing is ignored
        assertTrue(vm.current().groups.single().isFavourite)
        write.complete(SyncStatus.SYNCED)
        source.value = groupsData(fav = true)
        assertTrue(vm.current().favouriteOverrides.isEmpty())
        assertEquals(1, vm.current().counts[GroupListFilter.Favourites])
    }

    @Test fun failedFavouriteRevertsAndExplainsWithoutBlockingTheList() = runTest(dispatcher) {
        val vm = GroupsViewModel(MutableStateFlow(groupsData()), GroupFavouriteActions { _, _ -> error("disk full") })
        vm.toggleFavourite("ski")
        assertFalse(vm.current().groups.single().isFavourite)
        assertEquals("Couldn't update Ski trip Åre. Try again.", vm.current().message)
        vm.setQuery("ski")
        assertEquals(1, vm.current().shown.size)
    }

    @Test fun reliability_cachedGroupsStayReadableWhenRefreshFails() = runTest(dispatcher) {
        val source = flow {
            emit(groupsData())
            throw IllegalStateException("offline")
        }
        val vm = GroupsViewModel(source)
        assertEquals(1, vm.current().shown.size)
        assertEquals("Couldn't refresh. Showing your saved groups.", vm.current().sourceError)
    }

    /* ---------------- Create group ---------------- */

    private fun createVm(
        directory: ContactDirectory = ContactDirectory { if (mads.matchesContact(it)) mads else null },
        creation: GroupCreationActions = GroupCreationActions { CreatedGroup("g1", SyncStatus.PENDING_INSERT) }
    ) = CreateGroupViewModel(me, directory, creation, MutableStateFlow(listOf(mads)))

    @Test fun cannotCreateWithoutName() = runTest(dispatcher) {
        var calls = 0
        val vm = createVm(creation = GroupCreationActions { calls++; CreatedGroup("g", SyncStatus.SYNCED) })
        vm.setName("   ")
        vm.create()
        assertFalse(vm.state.value.canCreate)
        assertEquals(0, calls)
    }

    @Test fun foundUserIsAddedAndRemovedFromSuggestions() = runTest(dispatcher) {
        val vm = createVm()
        assertEquals(listOf(mads), vm.state.value.suggestions)
        vm.setQuery("MADS.H@mail.dk")
        vm.lookUp()
        assertEquals(listOf<MemberDraft>(MemberDraft.Existing(mads)), vm.state.value.members)
        assertEquals("", vm.state.value.query)
        assertTrue(vm.state.value.suggestions.isEmpty())
        assertEquals(2, vm.state.value.memberCount)
        vm.setQuery("mads.h@mail.dk"); vm.lookUp()
        assertEquals(MemberLookupState.AlreadyAdded("Mads"), vm.state.value.lookup)
    }

    @Test fun unknownUserShowsErrorAndCanBeInvited() = runTest(dispatcher) {
        val vm = createVm()
        vm.setQuery("anna.b@mail.dk"); vm.lookUp()
        assertEquals(MemberLookupState.NotFound("anna.b@mail.dk"), vm.state.value.lookup)
        vm.sendInvite()
        val invited = vm.state.value.members.single() as MemberDraft.Invited
        assertEquals("anna.b", invited.displayName)
        assertEquals(MemberLookupState.Idle, vm.state.value.lookup)
    }

    @Test fun invalidOrFailedLookupNeverOffersAnInvite() = runTest(dispatcher) {
        val vm = createVm(directory = ContactDirectory { error("db closed") })
        vm.setQuery("anna"); vm.lookUp()
        assertEquals(MemberLookupState.Invalid, vm.state.value.lookup)
        vm.setQuery("anna@mail.dk"); vm.lookUp()
        assertEquals(MemberLookupState.Failed, vm.state.value.lookup)
        vm.sendInvite()
        assertTrue(vm.state.value.members.isEmpty())
    }

    @Test fun reliability_groupCreatedOfflineIsSavedAndMarkedWaitingToSync() = runTest(dispatcher) {
        var draft: CreateGroupDraft? = null
        val vm = createVm(creation = GroupCreationActions { draft = it; CreatedGroup("g1", SyncStatus.PENDING_INSERT) })
        vm.setName("  Roskilde 2027 "); vm.setDescription(" "); vm.selectColour(GroupColourOption.Peach)
        vm.addSuggestion(mads)
        vm.create()
        assertEquals(CreateGroupDraft("Roskilde 2027", null, 2, listOf(MemberDraft.Existing(mads))), draft)
        val saved = vm.state.value.submission as CreateGroupSubmission.Saved
        assertEquals(SyncStatus.PENDING_INSERT, saved.group.syncStatus)
        vm.setName("changed after save")
        assertEquals("  Roskilde 2027 ", vm.state.value.name)
    }

    @Test fun failedLocalSaveKeepsTheFormSoTheUserCanRetry() = runTest(dispatcher) {
        var calls = 0
        val vm = createVm(creation = GroupCreationActions {
            calls++
            if (calls == 1) error("Couldn't save") else CreatedGroup("g1", SyncStatus.PENDING_INSERT)
        })
        vm.setName("Trip"); vm.create()
        assertEquals(CreateGroupSubmission.Failed("Couldn't save"), vm.state.value.submission)
        assertEquals("Trip", vm.state.value.name)
        vm.create()
        assertTrue(vm.state.value.submission is CreateGroupSubmission.Saved)
    }

    /* ---------------- Members tab & S-Warn ---------------- */

    private fun membersData(balances: Map<String, Long>? = mapOf("you" to -100L, "ida" to 0L, "jonas" to -14000L)) =
        GroupMembersData(
            "ski", "Ski trip Åre", "you",
            listOf(GroupMemberItem("you", "You"), GroupMemberItem("ida", "Ida"), GroupMemberItem("jonas", "Jonas")),
            balances,
            suggestions = listOf(mads)
        )

    private fun GroupMembersViewModel.current() = state.value as GroupMembersUiState.Success

    private fun membersVm(
        source: MutableStateFlow<GroupMembersData> = MutableStateFlow(membersData()),
        removed: MutableList<String> = mutableListOf(),
        added: MutableList<MemberDraft> = mutableListOf(),
        removeResult: () -> SyncStatus = { SyncStatus.PENDING_UPDATE }
    ) = GroupMembersViewModel(
        source,
        ContactDirectory { if (mads.matchesContact(it)) mads else null },
        MemberActions { _, member -> added += member; SyncStatus.PENDING_UPDATE },
        MemberRemovalActions { _, id -> removed += id; removeResult() }
    )

    @Test fun settledMemberIsRemovedWithoutWarning() = runTest(dispatcher) {
        val removed = mutableListOf<String>()
        val vm = membersVm(removed = removed)
        vm.askRemove("ida")
        assertEquals(listOf("ida"), removed)
        assertNull(vm.current().warningFor)
        assertEquals(SyncStatus.PENDING_UPDATE, vm.current().lastWrite)
    }

    @Test fun openBalanceWarnsAndKeepCancels() = runTest(dispatcher) {
        val removed = mutableListOf<String>()
        val vm = membersVm(removed = removed)
        vm.askRemove("jonas")
        assertEquals("jonas", vm.current().warningFor)
        assertTrue(removed.isEmpty())
        vm.keepMember()
        assertNull(vm.current().warningFor)
        vm.confirmRemove("jonas") // without an open warning nothing happens
        assertTrue(removed.isEmpty())
        vm.askRemove("jonas"); vm.confirmRemove("jonas")
        assertEquals(listOf("jonas"), removed)
    }

    @Test fun unknownBalanceAlsoWarnsAndYouCannotRemoveYourself() = runTest(dispatcher) {
        val removed = mutableListOf<String>()
        val vm = membersVm(MutableStateFlow(membersData(balances = null)), removed)
        vm.askRemove("ida")
        assertEquals("ida", vm.current().warningFor)
        vm.askRemove("you")
        assertEquals("ida", vm.current().warningFor)
        assertTrue(removed.isEmpty())
    }

    @Test fun changedBalanceClosesTheStaleWarning() = runTest(dispatcher) {
        val source = MutableStateFlow(membersData())
        val vm = membersVm(source)
        vm.askRemove("jonas")
        source.value = membersData(mapOf("you" to 0L, "ida" to 0L, "jonas" to -5000L))
        assertNull(vm.current().warningFor)
    }

    @Test fun failedRemovalKeepsMemberAndShowsMessage() = runTest(dispatcher) {
        val vm = membersVm(removeResult = { error("Couldn't remove Ida. Try again.") })
        vm.askRemove("ida")
        assertTrue(vm.current().removing.isEmpty())
        assertEquals("Couldn't remove Ida. Try again.", vm.current().message)
        assertEquals(3, vm.current().members.size)
    }

    @Test fun addMemberFindsUserOrInvitesUnknownPerson() = runTest(dispatcher) {
        val added = mutableListOf<MemberDraft>()
        val vm = membersVm(added = added)
        vm.openAddMember()
        vm.setAddQuery("mads.h@mail.dk"); vm.lookUpMember()
        assertEquals(listOf<MemberDraft>(MemberDraft.Existing(mads)), added)
        vm.setAddQuery("karl@mail.dk"); vm.lookUpMember()
        assertEquals(MemberLookupState.NotFound("karl@mail.dk"), vm.current().addPanel?.lookup)
        vm.sendInvite()
        assertEquals(MemberDraft.Invited("karl@mail.dk"), added.last())
        assertEquals(AddMemberPanel(), vm.current().addPanel)
    }

    @Test fun withoutWriteServicesTheTabIsReadOnly() = runTest(dispatcher) {
        val vm = GroupMembersViewModel(MutableStateFlow(membersData()))
        vm.openAddMember(); vm.askRemove("ida")
        assertFalse(vm.current().canManage)
        assertNull(vm.current().addPanel)
        assertNull(vm.current().warningFor)
    }
}
