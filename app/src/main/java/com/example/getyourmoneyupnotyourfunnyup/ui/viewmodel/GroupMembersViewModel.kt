package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.ui.groups.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Inline `+ Add member` panel (S8c-add): same lookup / invite rules as Create group. */
data class AddMemberPanel(
    val query: String = "",
    val lookup: MemberLookupState = MemberLookupState.Idle,
    val adding: Boolean = false
)

sealed interface GroupMembersUiState {
    data object Loading : GroupMembersUiState
    data class Error(val message: String) : GroupMembersUiState
    data class Success(
        val data: GroupMembersData,
        /** Member whose S-Warn card is expanded (non-zero or unknown balance). */
        val warningFor: String? = null,
        val removing: Set<String> = emptySet(),
        val addPanel: AddMemberPanel? = null,
        /** Latest local write result, shown as a sync strip ("Saved on your phone · waiting to sync"). */
        val lastWrite: SyncStatus? = null,
        val message: String? = null,
        val canEdit: Boolean = false,
        val sourceError: String? = null
    ) : GroupMembersUiState {
        val members: List<GroupMemberItem> = data.members.distinctBy { it.userId }
            .sortedWith(compareBy<GroupMemberItem> { it.userId != data.currentUserId }.thenBy { it.invited })
        val suggestions: List<Contact> = data.suggestions
            .filter { contact -> members.none { it.userId == contact.userId } && contact.userId != data.currentUserId }
            .distinctBy { it.userId }
        val canManage: Boolean = canEdit && data.canManageMembers
        fun balanceFor(userId: String): Long? = data.balances?.get(userId)
        fun canRemove(member: GroupMemberItem): Boolean =
            canManage && member.userId != data.currentUserId && member.userId !in removing
    }
}

class GroupMembersViewModel(
    private val source: Flow<GroupMembersData>,
    private val directory: ContactDirectory? = null,
    private val adding: MemberActions? = null,
    private val removal: MemberRemovalActions? = null
) : ViewModel() {
    private val mutableState = MutableStateFlow<GroupMembersUiState>(GroupMembersUiState.Loading)
    val state = mutableState.asStateFlow()
    private var observation: Job? = null
    private var lookupToken = 0

    init { observe() }

    fun retryObservation() {
        val current = mutableState.value
        if (observation?.isActive != true &&
            (current is GroupMembersUiState.Error || current is GroupMembersUiState.Success && current.sourceError != null)) observe()
    }

    private fun observe() {
        observation = viewModelScope.launch {
            try {
                source.collect { data ->
                    val current = mutableState.value as? GroupMembersUiState.Success
                    mutableState.value = if (current == null || current.data.groupId != data.groupId ||
                        current.data.currentUserId != data.currentUserId) {
                        GroupMembersUiState.Success(data, canEdit = adding != null && removal != null)
                    } else {
                        val stillHere = data.members.map { it.userId }.toSet()
                        val oldBalance = current.warningFor?.let { current.data.balances?.get(it) }
                        val newBalance = current.warningFor?.let { data.balances?.get(it) }
                        current.copy(
                            data = data,
                            // A changed balance invalidates the warning the user was reading.
                            warningFor = current.warningFor?.takeIf { it in stillHere && oldBalance == newBalance },
                            removing = current.removing.filter { it in stillHere }.toSet(),
                            sourceError = null
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val current = mutableState.value as? GroupMembersUiState.Success
                mutableState.value = current?.copy(sourceError = "Couldn't refresh. Showing saved members.")
                    ?: GroupMembersUiState.Error(error.message ?: "Couldn't load members")
            }
        }
    }

    private fun update(change: (GroupMembersUiState.Success) -> GroupMembersUiState.Success) {
        mutableState.update { if (it is GroupMembersUiState.Success) change(it) else it }
    }

    /* ----- S-Warn: removal ----- */

    /** Tap on `×`: settled members go straight away, anyone with an open balance gets a warning. */
    fun askRemove(userId: String) {
        val current = mutableState.value as? GroupMembersUiState.Success ?: return
        val member = current.members.firstOrNull { it.userId == userId } ?: return
        if (!current.canRemove(member)) return
        if (requiresRemovalWarning(current.balanceFor(userId))) {
            update { it.copy(warningFor = userId, message = null) }
        } else remove(userId)
    }

    /** `Keep <Name>` */
    fun keepMember() = update { it.copy(warningFor = null) }

    /** `Remove anyway` */
    fun confirmRemove(userId: String) {
        val current = mutableState.value as? GroupMembersUiState.Success ?: return
        if (current.warningFor != userId) return
        remove(userId)
    }

    private fun remove(userId: String) {
        val actions = removal ?: return
        val current = mutableState.value as? GroupMembersUiState.Success ?: return
        val groupId = current.data.groupId
        val name = current.members.firstOrNull { it.userId == userId }?.displayName ?: return
        update { it.copy(removing = it.removing + userId, warningFor = null, message = null) }
        viewModelScope.launch {
            try {
                val status = actions.removeMember(groupId, userId)
                update { it.copy(lastWrite = status) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                update {
                    it.copy(
                        removing = it.removing - userId,
                        message = error.message ?: "Couldn't remove $name. Try again."
                    )
                }
            }
        }
    }

    /* ----- S8c-add: add member ----- */

    fun openAddMember() = update { if (it.canManage) it.copy(addPanel = it.addPanel ?: AddMemberPanel(), warningFor = null) else it }
    fun closeAddMember() {
        lookupToken++
        update { it.copy(addPanel = null) }
    }

    fun setAddQuery(value: String) {
        lookupToken++
        update { state -> state.addPanel?.let { state.copy(addPanel = it.copy(query = value.take(80), lookup = MemberLookupState.Idle)) } ?: state }
    }

    fun lookUpMember() {
        val current = mutableState.value as? GroupMembersUiState.Success ?: return
        val panel = current.addPanel ?: return
        val query = panel.query.trim()
        val finder = directory ?: return
        if (query.isEmpty() || panel.adding || panel.lookup == MemberLookupState.Searching) return
        if (contactKind(query) == ContactKind.Invalid) {
            setPanel { it.copy(lookup = MemberLookupState.Invalid) }
            return
        }
        val token = ++lookupToken
        setPanel { it.copy(lookup = MemberLookupState.Searching) }
        viewModelScope.launch {
            val contact = try {
                finder.find(query)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (token == lookupToken) setPanel { it.copy(lookup = MemberLookupState.Failed) }
                return@launch
            }
            if (token != lookupToken) return@launch
            val latest = mutableState.value as? GroupMembersUiState.Success ?: return@launch
            val existing = contact?.let { found -> latest.members.firstOrNull { it.userId == found.userId } }
            when {
                contact == null -> setPanel { it.copy(lookup = MemberLookupState.NotFound(query)) }
                existing != null -> setPanel {
                    it.copy(lookup = MemberLookupState.AlreadyAdded(if (existing.userId == latest.data.currentUserId) "You" else existing.displayName))
                }
                else -> add(MemberDraft.Existing(contact))
            }
        }
    }

    fun addSuggestion(contact: Contact) = add(MemberDraft.Existing(contact))

    fun sendInvite() {
        val panel = (mutableState.value as? GroupMembersUiState.Success)?.addPanel ?: return
        val notFound = panel.lookup as? MemberLookupState.NotFound ?: return
        add(MemberDraft.Invited(notFound.query))
    }

    private fun add(member: MemberDraft) {
        val actions = adding ?: return
        val current = mutableState.value as? GroupMembersUiState.Success ?: return
        if (!current.canManage || current.addPanel?.adding == true) return
        if (current.members.any { it.userId == member.key }) return
        val groupId = current.data.groupId
        update { it.copy(addPanel = (it.addPanel ?: AddMemberPanel()).copy(adding = true), message = null) }
        viewModelScope.launch {
            try {
                val status = actions.addMember(groupId, member)
                update { it.copy(addPanel = AddMemberPanel(), lastWrite = status) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                update {
                    it.copy(
                        addPanel = it.addPanel?.copy(adding = false),
                        message = error.message ?: "Couldn't add ${member.displayName}. Try again."
                    )
                }
            }
        }
    }

    private fun setPanel(change: (AddMemberPanel) -> AddMemberPanel) =
        update { state -> state.addPanel?.let { state.copy(addPanel = change(it)) } ?: state }
}
