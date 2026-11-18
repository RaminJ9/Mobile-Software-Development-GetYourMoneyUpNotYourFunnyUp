package com.example.getyourmoneyupnotyourfunnyup.ui.groups

import com.example.getyourmoneyupnotyourfunnyup.data.repository.LedgerRepository
import com.example.getyourmoneyupnotyourfunnyup.model.DietRule
import com.example.getyourmoneyupnotyourfunnyup.model.LedgerSnapshot
import com.example.getyourmoneyupnotyourfunnyup.model.Person
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.model.myGroups
import com.example.getyourmoneyupnotyourfunnyup.model.myNet
import com.example.getyourmoneyupnotyourfunnyup.model.netBalances
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.LedgerDashboardSource
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.SyncPresentation
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.dashboardGroup
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.toUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Supplies Bashir's Groups, Create group and Members screens from the local-first [LedgerRepository],
 * so a group created or changed there shows up everywhere else (Home, Activity, the group page, Scan).
 *
 * Writes follow the same rules as `GroupRepository`: only members of a group can change it, you can't
 * remove yourself, nobody is added twice, and each write returns the group's sync state.
 * The mapping lives in the pure functions [groupsListData] and [groupMembersData] for unit tests.
 */
class LedgerGroupsSource(private val ledger: LedgerRepository) :
    GroupFavouriteActions, ContactDirectory, GroupCreationActions, MemberActions, MemberRemovalActions {

    val groupsList: Flow<GroupsListData> = ledger.snapshot.map { groupsListData(it, ledger.now()) }

    val knownContacts: Flow<List<Contact>> =
        ledger.snapshot.map { knownContacts(it).take(MAX_SUGGESTIONS) }.distinctUntilChanged()

    fun members(groupId: String): Flow<GroupMembersData> = ledger.snapshot.map { groupMembersData(it, groupId) }

    /** The signed-in user, for the create-group screen ("You" is always in a new group). */
    fun currentUser(): User = ledger.snapshot.value.me.toUser()

    /** The star is a personal preference kept on this phone, so there is nothing to upload. */
    override suspend fun setFavourite(groupId: String, favourite: Boolean): SyncStatus {
        val snapshot = ledger.snapshot.value
        requireMembership(snapshot, groupId)
        if ((groupId in snapshot.favouriteGroupIds) != favourite) ledger.toggleFavourite(groupId)
        return SyncStatus.SYNCED
    }

    /** App users only, by email or phone number (never by name, and never yourself). */
    override suspend fun find(query: String): Contact? {
        if (contactKind(query) == ContactKind.Invalid) return null
        return ledger.findPerson(query)?.toContact()
    }

    override suspend fun createGroup(draft: CreateGroupDraft): CreatedGroup {
        val name = draft.name.trim()
        require(name.isNotEmpty()) { "Give your group a name" }
        val snapshot = ledger.snapshot.value
        val memberIds = draft.members.filterIsInstance<MemberDraft.Existing>().map { it.contact.userId }.distinct()
        check(memberIds.all { it in snapshot.people }) { "Someone you added isn't on your phone anymore. Look them up again." }
        val invites = draft.members.filterIsInstance<MemberDraft.Invited>().map { normaliseContact(it.contactValue) }.distinct()
        val groupId = ledger.createGroup(name, draft.description, draft.colourIndex, memberIds, invites)
        return CreatedGroup(groupId, groupStatus(groupId))
    }

    override suspend fun addMember(groupId: String, member: MemberDraft): SyncStatus {
        val snapshot = ledger.snapshot.value
        val group = requireMembership(snapshot, groupId)
        when (member) {
            is MemberDraft.Existing -> {
                val userId = member.contact.userId
                check(userId in snapshot.people) { "${member.displayName} isn't on your phone anymore. Look them up again." }
                require(group.members.none { it.userId == userId }) { "${member.displayName} is already in the group" }
                ledger.addMember(groupId, userId)
            }
            is MemberDraft.Invited -> {
                val contact = normaliseContact(member.contactValue)
                require(snapshot.invites.none { it.groupId == groupId && normaliseContact(it.contact) == contact }) {
                    "${member.displayName} is already invited"
                }
                ledger.invite(groupId, contact)
            }
        }
        return groupStatus(groupId)
    }

    override suspend fun removeMember(groupId: String, userId: String): SyncStatus {
        val snapshot = ledger.snapshot.value
        val group = requireMembership(snapshot, groupId)
        require(userId != snapshot.currentUserId) { "You can't remove yourself here" }
        if (userId.startsWith(INVITE_PREFIX)) {
            val invite = snapshot.invites.firstOrNull { it.groupId == groupId && inviteId(it.contact) == userId }
            require(invite != null) { "This invite was already withdrawn" }
            ledger.removeInvite(groupId, invite.contact)
        } else {
            require(group.members.any { it.userId == userId }) { "This member has already left" }
            ledger.removeMember(groupId, userId)
        }
        return groupStatus(groupId)
    }

    private fun requireMembership(snapshot: LedgerSnapshot, groupId: String) =
        checkNotNull(snapshot.group(groupId)) { "This group isn't on your phone anymore" }.also { group ->
            check(group.members.any { it.userId == snapshot.currentUserId }) { "You're not a member of this group" }
        }

    private fun groupStatus(groupId: String): SyncStatus =
        ledger.snapshot.value.group(groupId)?.syncStatus ?: error("This group isn't on your phone anymore")

    companion object {
        /** Same id scheme as `GroupRepository` for people who were invited but haven't joined. */
        const val INVITE_PREFIX = "invite:"

        /** How many "People you've split with" to suggest (the HiFi shows a short list). */
        const val MAX_SUGGESTIONS = 4
    }
}

private fun inviteId(contact: String) = LedgerGroupsSource.INVITE_PREFIX + normaliseContact(contact)

/** A person's lookup contact is an email or a phone number; put it in the matching field. */
private fun Person.toContact(): Contact {
    val isEmail = contactKind(contact) == ContactKind.Email
    return Contact(
        userId = id, displayName = name, email = if (isEmail) contact else "",
        phone = phone ?: contact.takeUnless { isEmail }, colourIndex = paletteIndex,
    )
}

/** What the Groups tab shows. Balances are the user's net per group, computed from the ledger. */
fun groupsListData(snapshot: LedgerSnapshot, now: Long): GroupsListData {
    val groups = snapshot.myGroups()
    return GroupsListData(
        userId = snapshot.currentUserId,
        groups = groups.map { snapshot.dashboardGroup(it) },
        balancesByGroup = groups.associate { it.id to snapshot.myNet(it.id) },
        nowMillis = now,
        sync = SyncPresentation(savedAt = snapshot.lastSyncedAt, offlineNote = LedgerDashboardSource.OFFLINE_NOTE),
    )
}

/** What a group's Members tab shows: members, then people invited to it, with each person's net balance. */
fun groupMembersData(snapshot: LedgerSnapshot, groupId: String): GroupMembersData {
    val group = snapshot.group(groupId)
    val me = snapshot.currentUserId
    val net = snapshot.netBalances(groupId)
    val members = group?.members.orEmpty().map { member ->
        val person = snapshot.person(member.userId)
        GroupMemberItem(
            userId = member.userId,
            displayName = if (member.userId == me) "You" else person.name,
            role = member.role,
            colourIndex = person.paletteIndex,
            dietaryRules = person.dietRules.map { if (it == DietRule.NO_ALCOHOL) DietaryRule.Alcohol else DietaryRule.Meat }.toSet(),
            syncStatus = group?.syncStatus ?: SyncStatus.SYNCED,
        )
    }
    val invited = snapshot.invites.filter { it.groupId == groupId }.map { invite ->
        GroupMemberItem(
            userId = inviteId(invite.contact),
            displayName = inviteDisplayName(invite.contact),
            invited = true,
            syncStatus = group?.syncStatus ?: SyncStatus.SYNCED,
        )
    }
    return GroupMembersData(
        groupId = groupId,
        groupName = group?.name.orEmpty(),
        currentUserId = me,
        members = members + invited,
        // Invited people haven't joined, so they can't owe anything yet.
        balances = members.associate { it.userId to (net[it.userId] ?: 0L) } + invited.associate { it.userId to 0L },
        suggestions = knownContacts(snapshot).filter { contact -> group?.members?.none { it.userId == contact.userId } ?: true }
            .take(LedgerGroupsSource.MAX_SUGGESTIONS),
        canManageMembers = group?.members?.any { it.userId == me } == true,
    )
}

/** People the user has split with, most shared groups first. */
private fun knownContacts(snapshot: LedgerSnapshot): List<Contact> = snapshot.myGroups()
    .flatMap { group -> group.members.map { it.userId } }
    .filter { it != snapshot.currentUserId }
    .groupingBy { it }.eachCount()
    .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { snapshot.person(it.key).name })
    .map { snapshot.person(it.key).toContact() }
