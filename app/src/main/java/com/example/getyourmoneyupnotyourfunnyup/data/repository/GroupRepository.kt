package com.example.getyourmoneyupnotyourfunnyup.data.repository

import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.ExpensesTable
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.GroupMembersTable
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.GroupsTable
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.SyncQueueTable
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.UsersTable
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.GroupEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.GroupMemberEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SyncAction
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SyncEntityType
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SyncQueueEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.UserEntity
import com.example.getyourmoneyupnotyourfunnyup.data.remote.GroupDocumentMapper
import com.example.getyourmoneyupnotyourfunnyup.data.remote.SyncPayloadJson
import com.example.getyourmoneyupnotyourfunnyup.data.remote.UserDocumentMapper
import com.example.getyourmoneyupnotyourfunnyup.model.GroupRole
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.DashboardGroup
import com.example.getyourmoneyupnotyourfunnyup.ui.groups.*
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Abstraction for looking up registered users in the remote database (Cloud Firestore `/users`)
 * when a contact is not yet cached in the local SQLite `users` table.
 */
interface RemoteUserDirectory {
    suspend fun findByContact(query: String, kind: ContactKind): User?
    suspend fun fetchAllUsers(): List<User> = emptyList()
}

/**
 * Queries Cloud Firestore `/users` by email or phone number, falling back to a case-insensitive /
 * phone-normalised scan of `/users` documents so formatting or casing differences never hide an
 * existing user. Safely returns `null` / empty when offline or when Firebase is not initialized.
 */
class FirestoreRemoteUserDirectory(
    private val firestoreProvider: () -> FirebaseFirestore? = {
        runCatching { Firebase.firestore }.getOrNull()
    }
) : RemoteUserDirectory {

    override suspend fun findByContact(query: String, kind: ContactKind): User? {
        val firestore = firestoreProvider() ?: return null
        val trimmed = query.trim()
        if (trimmed.isEmpty() || kind == ContactKind.Invalid) return null
        return try {
            val usersCol = firestore.collection(AuthRepository.USERS_COLLECTION)
            when (kind) {
                ContactKind.Email -> {
                    val exact = usersCol.whereEqualTo("email", trimmed).limit(1).get().awaitOrNull()
                        ?.documents?.firstOrNull { !it.id.startsWith(GroupRepository.INVITE_PREFIX) }
                    val lower = if (exact == null && trimmed.lowercase() != trimmed) {
                        usersCol.whereEqualTo("email", trimmed.lowercase()).limit(1).get().awaitOrNull()
                            ?.documents?.firstOrNull { !it.id.startsWith(GroupRepository.INVITE_PREFIX) }
                    } else null
                    val matched = exact ?: lower ?: usersCol.get().awaitOrNull()?.documents?.firstOrNull { doc ->
                        !doc.id.startsWith(GroupRepository.INVITE_PREFIX) &&
                            doc.getString("email")?.trim()?.equals(trimmed, ignoreCase = true) == true
                    }
                    matched?.toRemoteUser()
                }
                ContactKind.Phone -> {
                    val normalised = normaliseContact(trimmed)
                    val byRawPhoneNumber = usersCol.whereEqualTo("phoneNumber", trimmed).limit(1).get().awaitOrNull()
                        ?.documents?.firstOrNull { !it.id.startsWith(GroupRepository.INVITE_PREFIX) }
                    val byNormalisedPhoneNumber = if (byRawPhoneNumber == null && normalised != trimmed) {
                        usersCol.whereEqualTo("phoneNumber", normalised).limit(1).get().awaitOrNull()
                            ?.documents?.firstOrNull { !it.id.startsWith(GroupRepository.INVITE_PREFIX) }
                    } else null
                    val byPhoneField = if (byRawPhoneNumber == null && byNormalisedPhoneNumber == null) {
                        usersCol.whereEqualTo("phone", normalised).limit(1).get().awaitOrNull()
                            ?.documents?.firstOrNull { !it.id.startsWith(GroupRepository.INVITE_PREFIX) }
                    } else null
                    val matched = byRawPhoneNumber ?: byNormalisedPhoneNumber ?: byPhoneField
                        ?: usersCol.get().awaitOrNull()?.documents?.firstOrNull { doc ->
                            !doc.id.startsWith(GroupRepository.INVITE_PREFIX) && (
                                phonesMatch(doc.getString("phoneNumber"), normalised) ||
                                    phonesMatch(doc.getString("phone"), normalised)
                                )
                        }
                    matched?.toRemoteUser()
                }
                ContactKind.Invalid -> null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun fetchAllUsers(): List<User> {
        val firestore = firestoreProvider() ?: return emptyList()
        return try {
            firestore.collection(AuthRepository.USERS_COLLECTION).get().awaitOrNull()
                ?.documents
                .orEmpty()
                .filterNot { it.id.startsWith(GroupRepository.INVITE_PREFIX) }
                .mapNotNull { doc -> runCatching { doc.toRemoteUser() }.getOrNull() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun DocumentSnapshot.toRemoteUser(): User {
        val raw = data.orEmpty()
        val email = (raw["email"] as? String)?.trim().orEmpty()
        val fallbackName = email.substringBefore("@").ifBlank { id }
        return UserDocumentMapper.fromMap(
            data = raw,
            uid = id,
            fallbackEmail = email,
            fallbackName = fallbackName
        )
    }

    private suspend fun <T> Task<T>.awaitOrNull(): T? = try {
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result ->
                if (continuation.isActive) continuation.resume(result)
            }.addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}

/**
 * Offline-first repository for groups & members (Person 2: S4, S5, S8c, S-Warn).
 *
 * Every write goes to SQLite first, inside one transaction together with its `sync_queue` entry,
 * and returns the local sync state (`PENDING_*`). The UI never waits for the network; the queued
 * entry is pushed to Firestore later by the WorkManager processor owned by the database/sync owner.
 *
 * Construct one instance per signed-in account ([account]) and drop it on logout.
 */
class GroupRepository(
    private val database: AppDatabase,
    private val account: User,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val onEnqueued: (() -> Unit)? = null,
    private val remoteUsers: RemoteUserDirectory? = FirestoreRemoteUserDirectory()
) : GroupFavouriteActions, ContactDirectory, GroupCreationActions, MemberActions, MemberRemovalActions {

    private val groupDao get() = database.groupDao
    private val userDao get() = database.userDao
    private val expenseDao get() = database.expenseDao
    private val syncQueueDao get() = database.syncQueueDao

    /* ------------------------------------------------------------------ reads (SSOT) */

    /** Groups the account belongs to, with colour, favourite, activity and pending-sync metadata. */
    fun observeGroups(): Flow<List<DashboardGroup>> =
        database.observeTables(
            GroupsTable.TABLE_NAME, GroupMembersTable.TABLE_NAME, UsersTable.TABLE_NAME, ExpensesTable.TABLE_NAME
        ).map { withContext(Dispatchers.IO) { loadGroups() } }

    /** Groups tab snapshot. [balances] (per-group net for the account) come from the ledger owner. */
    fun observeGroupsList(balances: Flow<Map<String, Long>?> = flowOf(null)): Flow<GroupsListData> =
        combine(observeGroups(), balances) { groups, groupBalances ->
            GroupsListData(account.id, groups, groupBalances, clock())
        }

    /**
     * Members tab snapshot. [balances] are each member's net in øre from the ledger owner, and
     * [dietaryRules] the members' "Never charge me for" settings from the profile owner.
     */
    fun observeMembers(
        groupId: String,
        balances: Flow<Map<String, Long>?> = flowOf(null),
        dietaryRules: Flow<Map<String, Set<DietaryRule>>> = flowOf(emptyMap())
    ): Flow<GroupMembersData> = combine(
        database.observeTables(GroupsTable.TABLE_NAME, GroupMembersTable.TABLE_NAME, UsersTable.TABLE_NAME)
            .map { withContext(Dispatchers.IO) { loadMembers(groupId) } },
        balances,
        dietaryRules
    ) { loaded, memberBalances, rules ->
        loaded.copy(
            members = loaded.members.map { it.copy(dietaryRules = rules[it.userId].orEmpty()) },
            balances = memberBalances
        )
    }

    fun observeKnownContacts(): Flow<List<Contact>> =
        database.observeTables(GroupMembersTable.TABLE_NAME, UsersTable.TABLE_NAME)
            .map { withContext(Dispatchers.IO) { loadKnownContacts() } }

    private fun loadGroups(): List<DashboardGroup> = groupDao.getGroupsForUser(account.id).map { entity ->
        val members = groupDao.getMembersForGroup(entity.id).map { member ->
            member.toDomain(userDao.getUserById(member.userId)?.displayName.orEmpty())
        }
        val expenses = expenseDao.getActiveExpensesForGroup(entity.id)
        DashboardGroup(
            group = entity.toDomain(members),
            colourIndex = entity.colourIndex,
            isFavourite = entity.isFavourite,
            lastActivityAt = maxOf(entity.updatedAt, expenses.maxOfOrNull { it.createdAt } ?: 0L),
            pendingExpenseCount = expenses.count { it.syncStatus != SyncStatus.SYNCED }
        )
    }

    private fun loadMembers(groupId: String): GroupMembersData {
        val group = groupDao.getGroupById(groupId)
        val members = groupDao.getMembersForGroup(groupId).map { member ->
            val user = userDao.getUserById(member.userId)
            GroupMemberItem(
                userId = member.userId,
                displayName = when {
                    member.userId == account.id -> "You"
                    user != null -> user.displayName
                    else -> "Unknown member"
                },
                role = member.role,
                colourIndex = memberColourIndex(member.userId),
                invited = member.invited,
                syncStatus = group?.syncStatus ?: SyncStatus.SYNCED
            )
        }
        return GroupMembersData(
            groupId = groupId,
            groupName = group?.name.orEmpty(),
            currentUserId = account.id,
            members = members,
            suggestions = loadKnownContacts(),
            canManageMembers = group != null && members.any { it.userId == account.id }
        )
    }

    private fun loadKnownContacts(): List<Contact> = groupDao.getCoMemberIds(account.id)
        .mapNotNull { userDao.getUserById(it)?.toContact() }

    /* ------------------------------------------------------------------ lookup */

    override suspend fun find(query: String): Contact? = withContext(Dispatchers.IO) {
        val value = query.trim()
        val kind = contactKind(value)
        val localUser = when (kind) {
            ContactKind.Email -> userDao.getUserByEmailIgnoringCase(value)
            ContactKind.Phone -> userDao.getUserByPhone(normaliseContact(value))
            ContactKind.Invalid -> null
        }?.takeUnless { it.id.startsWith(INVITE_PREFIX) }

        if (localUser != null) {
            return@withContext localUser.toContact()
        }

        if (kind == ContactKind.Invalid) return@withContext null

        val remoteUser = remoteUsers?.findByContact(value, kind)
            ?.takeUnless { it.id.startsWith(INVITE_PREFIX) }
            ?: return@withContext null

        val cached = userDao.cacheRemoteUser(UserEntity.fromDomain(remoteUser))
        cached.toContact()
    }

    /**
     * Pulls registered users from the remote `/users` collection into the local SQLite `users`
     * table so member lookups and display names work offline too.
     */
    suspend fun syncRemoteUsers() = withContext(Dispatchers.IO) {
        val fetched = remoteUsers?.fetchAllUsers().orEmpty()
        for (remoteUser in fetched) {
            if (!remoteUser.id.startsWith(INVITE_PREFIX)) {
                userDao.cacheRemoteUser(UserEntity.fromDomain(remoteUser))
            }
        }
    }

    /* ------------------------------------------------------------------ writes */

    override suspend fun setFavourite(groupId: String, favourite: Boolean): SyncStatus = withContext(Dispatchers.IO) {
        requireMembership(groupId)
        // The star is the account's personal preference, kept on this phone; nothing to upload.
        groupDao.setFavourite(groupId, favourite)
        SyncStatus.SYNCED
    }

    override suspend fun createGroup(draft: CreateGroupDraft): CreatedGroup = withContext(Dispatchers.IO) {
        val name = draft.name.trim()
        require(name.isNotEmpty()) { "Give your group a name" }
        val now = clock()
        val groupId = newId()
        val group = GroupEntity(
            id = groupId,
            name = name,
            description = draft.description?.trim()?.ifEmpty { null },
            currency = "DKK",
            createdBy = account.id,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING_INSERT,
            colourIndex = draft.colourIndex.mod(GROUP_COLOUR_COUNT)
        )
        val members = buildList {
            add(GroupMemberEntity(groupId, account.id, GroupRole.ADMIN, now))
            draft.members.distinctBy { it.key }.forEachIndexed { index, member ->
                add(GroupMemberEntity(groupId, member.key, GroupRole.MEMBER, now + index + 1, member is MemberDraft.Invited))
            }
        }.distinctBy { it.userId }
        inTransaction {
            ensureAccountRow()
            draft.members.forEach { ensureUserRow(it) }
            groupDao.upsertGroupWithMembers(group, members)
            enqueue(SyncAction.CREATE, groupId, groupPayload(group, members))
        }
        onEnqueued?.invoke()
        CreatedGroup(groupId, SyncStatus.PENDING_INSERT)
    }

    override suspend fun addMember(groupId: String, member: MemberDraft): SyncStatus = withContext(Dispatchers.IO) {
        requireMembership(groupId)
        val existing = groupDao.getMembersForGroup(groupId)
        require(existing.none { it.userId == member.key }) { "${member.displayName} is already in the group" }
        val entity = GroupMemberEntity(groupId, member.key, GroupRole.MEMBER, clock(), member is MemberDraft.Invited)
        val status = nextStatus(groupId)
        inTransaction {
            ensureUserRow(member)
            groupDao.addMember(entity)
            groupDao.touchGroup(groupId, status, clock())
            val payloadMap = buildMap<String, Any?> {
                put("op", if (entity.invited) "inviteMember" else "addMember")
                put("groupId", groupId)
                put("member", memberMap(entity))
                if (member is MemberDraft.Invited) put("contact", normaliseContact(member.contactValue))
            }
            enqueue(SyncAction.UPDATE, groupId, SyncPayloadJson.encode(payloadMap))
        }
        onEnqueued?.invoke()
        status
    }

    override suspend fun removeMember(groupId: String, userId: String): SyncStatus = withContext(Dispatchers.IO) {
        requireMembership(groupId)
        require(userId != account.id) { "You can't remove yourself here" }
        require(groupDao.getMembersForGroup(groupId).any { it.userId == userId }) { "This member has already left" }
        val status = nextStatus(groupId)
        inTransaction {
            groupDao.removeMember(groupId, userId)
            groupDao.touchGroup(groupId, status, clock())
            enqueue(
                SyncAction.UPDATE,
                groupId,
                SyncPayloadJson.encode(mapOf("op" to "removeMember", "groupId" to groupId, "userId" to userId))
            )
        }
        onEnqueued?.invoke()
        status
    }

    suspend fun cacheRemoteGroup(data: Map<String, Any?>, groupId: String) = withContext(Dispatchers.IO) {
        val local = groupDao.getGroupById(groupId)
        if (local != null && local.syncStatus != SyncStatus.SYNCED) return@withContext
        val domainGroup = GroupDocumentMapper.fromMap(data = data, groupId = groupId, nowMillis = clock())
        val (remoteGroup, remoteMembers) = GroupDocumentMapper.entitiesFromMap(
            data = data,
            groupId = groupId,
            isFavourite = local?.isFavourite ?: false,
            nowMillis = clock()
        )
        if (local != null && local.updatedAt > remoteGroup.updatedAt) return@withContext
        val namesById = domainGroup.members.associate { it.userId to it.displayName }
        remoteMembers.forEach { member ->
            if (userDao.getUserById(member.userId) == null) {
                userDao.upsertUser(
                    UserEntity(
                        id = member.userId,
                        email = "",
                        displayName = namesById[member.userId]?.takeIf { it.isNotBlank() } ?: member.userId,
                        updatedAt = clock()
                    )
                )
            }
        }
        groupDao.upsertGroupWithMembers(remoteGroup, remoteMembers)
    }

    /* ------------------------------------------------------------------ helpers */

    private fun requireMembership(groupId: String) {
        checkNotNull(groupDao.getGroupById(groupId)) { "This group isn't on your phone anymore" }
        check(groupDao.getMembersForGroup(groupId).any { it.userId == account.id }) { "You're not a member of this group" }
    }

    /** A group that was never uploaded stays PENDING_INSERT so the processor creates it once. */
    private fun nextStatus(groupId: String): SyncStatus =
        if (groupDao.getGroupById(groupId)?.syncStatus == SyncStatus.PENDING_INSERT) SyncStatus.PENDING_INSERT
        else SyncStatus.PENDING_UPDATE

    private fun ensureAccountRow() {
        if (userDao.getUserById(account.id) == null) {
            userDao.upsertUser(UserEntity.fromDomain(account))
        }
    }

    /** group_members has a foreign key to users, so invites get a placeholder user row. */
    private fun ensureUserRow(member: MemberDraft) {
        if (userDao.getUserById(member.key) != null) return
        val entity = when (member) {
            is MemberDraft.Existing -> UserEntity(
                id = member.contact.userId,
                email = member.contact.email,
                displayName = member.contact.displayName,
                phone = member.contact.phone?.let(::normaliseContact),
                updatedAt = clock()
            )
            is MemberDraft.Invited -> {
                val contact = normaliseContact(member.contactValue)
                val isEmail = contactKind(member.contactValue) == ContactKind.Email
                UserEntity(
                    id = member.key,
                    email = if (isEmail) contact else "",
                    displayName = member.displayName,
                    phone = if (isEmail) null else contact,
                    syncStatus = SyncStatus.PENDING_INSERT,
                    updatedAt = clock()
                )
            }
        }
        userDao.upsertUser(entity)
    }

    private fun enqueue(action: SyncAction, groupId: String, payload: String) {
        val id = syncQueueDao.enqueue(
            SyncQueueEntity(entityType = SyncEntityType.GROUP, entityId = groupId, action = action, payloadJson = payload)
        )
        check(id != -1L) { "Couldn't queue the change for syncing" }
    }

    private inline fun inTransaction(block: () -> Unit) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            block()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        // Re-notify after commit so observers never re-query a not-yet-committed state.
        database.notifyTableChanged(
            GroupsTable.TABLE_NAME, GroupMembersTable.TABLE_NAME, UsersTable.TABLE_NAME, SyncQueueTable.TABLE_NAME
        )
    }

    private fun groupPayload(group: GroupEntity, members: List<GroupMemberEntity>): String {
        val displayNames = members.mapNotNull { member ->
            userDao.getUserById(member.userId)?.displayName?.let { member.userId to it }
        }.toMap()
        return SyncPayloadJson.encode(GroupDocumentMapper.toMap(group, members, displayNames))
    }

    private fun memberMap(member: GroupMemberEntity): Map<String, Any?> =
        GroupDocumentMapper.memberToMap(member, userDao.getUserById(member.userId)?.displayName)

    private fun UserEntity.toContact() = Contact(
        userId = id,
        displayName = displayName,
        email = email,
        phone = phone?.takeIf { it.isNotBlank() } ?: phoneNumber?.takeIf { it.isNotBlank() }?.let(::normaliseContact),
        colourIndex = memberColourIndex(id)
    )

    companion object {
        const val INVITE_PREFIX = "invite:"
        private const val GROUP_COLOUR_COUNT = 9

        /** Stable avatar colour for a person (the account itself is always drawn in Green300). */
        fun memberColourIndex(userId: String): Int = userId.hashCode().mod(GROUP_COLOUR_COUNT)
    }
}
