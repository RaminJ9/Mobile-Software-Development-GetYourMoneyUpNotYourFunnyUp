package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.example.getyourmoneyupnotyourfunnyup.ui.groups.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Result of typing an email/phone and tapping `Add` (shared by S5 and S8c-add). */
sealed interface MemberLookupState {
    data object Idle : MemberLookupState
    data object Searching : MemberLookupState
    /** Not a valid email or phone number. */
    data object Invalid : MemberLookupState
    /** S-Error: "No one uses the app with <query> yet" + `Send invite`. */
    data class NotFound(val query: String) : MemberLookupState
    data class AlreadyAdded(val name: String) : MemberLookupState
    /** The directory couldn't be reached; never offer an invite on a guess. */
    data object Failed : MemberLookupState
}

sealed interface CreateGroupSubmission {
    data object Editing : CreateGroupSubmission
    data object Saving : CreateGroupSubmission
    data class Saved(val group: CreatedGroup) : CreateGroupSubmission
    data class Failed(val message: String) : CreateGroupSubmission
}

data class CreateGroupUiState(
    val currentUser: User,
    val name: String = "",
    val description: String = "",
    val colour: GroupColourOption = GroupColourOption.Teal,
    val query: String = "",
    val members: List<MemberDraft> = emptyList(),
    val lookup: MemberLookupState = MemberLookupState.Idle,
    val knownContacts: List<Contact> = emptyList(),
    val submission: CreateGroupSubmission = CreateGroupSubmission.Editing
) {
    /** "You" is always in the group and cannot be removed. */
    val memberCount: Int = members.size + 1
    val canCreate: Boolean = name.isNotBlank() && submission !is CreateGroupSubmission.Saving &&
        submission !is CreateGroupSubmission.Saved
    val suggestions: List<Contact> = knownContacts
        .filter { contact -> contact.userId != currentUser.id && members.none { it.key == contact.userId } }
        .distinctBy { it.userId }
    val previewInitial: String = name.trim().take(1).uppercase().ifEmpty { "?" }
}

class CreateGroupViewModel(
    currentUser: User,
    private val directory: ContactDirectory,
    private val creation: GroupCreationActions,
    knownContacts: Flow<List<Contact>>? = null
) : ViewModel() {
    private val mutableState = MutableStateFlow(CreateGroupUiState(currentUser))
    val state = mutableState.asStateFlow()
    private var lookupToken = 0

    init {
        knownContacts?.let { flow ->
            viewModelScope.launch {
                try {
                    flow.collect { contacts -> mutableState.update { it.copy(knownContacts = contacts) } }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Suggestions are optional; the email/phone field still works without them.
                }
            }
        }
    }

    private fun edit(change: (CreateGroupUiState) -> CreateGroupUiState) = mutableState.update {
        if (it.submission is CreateGroupSubmission.Saving || it.submission is CreateGroupSubmission.Saved) it
        else change(it).let { next ->
            if (next.submission is CreateGroupSubmission.Failed) next.copy(submission = CreateGroupSubmission.Editing) else next
        }
    }

    fun setName(value: String) = edit { it.copy(name = value.take(60)) }
    fun setDescription(value: String) = edit { it.copy(description = value.take(200)) }
    fun selectColour(colour: GroupColourOption) = edit { it.copy(colour = colour) }
    fun setQuery(value: String) {
        lookupToken++
        edit { it.copy(query = value.take(80), lookup = MemberLookupState.Idle) }
    }

    fun addSuggestion(contact: Contact) = edit { state ->
        if (state.members.any { it.key == contact.userId } || contact.userId == state.currentUser.id) state
        else state.copy(members = state.members + MemberDraft.Existing(contact), lookup = MemberLookupState.Idle)
    }

    fun removeMember(key: String) = edit { state -> state.copy(members = state.members.filterNot { it.key == key }) }

    fun lookUp() {
        val current = mutableState.value
        val query = current.query.trim()
        if (query.isEmpty() || current.lookup == MemberLookupState.Searching) return
        if (contactKind(query) == ContactKind.Invalid) {
            edit { it.copy(lookup = MemberLookupState.Invalid) }
            return
        }
        if (current.currentUser.email.isNotBlank() && normaliseContact(current.currentUser.email) == normaliseContact(query)) {
            edit { it.copy(lookup = MemberLookupState.AlreadyAdded("You")) }
            return
        }
        current.members.firstOrNull { member ->
            when (member) {
                is MemberDraft.Existing -> member.contact.matchesContact(query)
                is MemberDraft.Invited -> normaliseContact(member.contactValue) == normaliseContact(query)
            }
        }?.let { existing ->
            edit { it.copy(lookup = MemberLookupState.AlreadyAdded(existing.displayName)) }
            return
        }
        val token = ++lookupToken
        edit { it.copy(lookup = MemberLookupState.Searching) }
        viewModelScope.launch {
            val result = try {
                directory.find(query)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (token == lookupToken) edit { it.copy(lookup = MemberLookupState.Failed) }
                return@launch
            }
            if (token != lookupToken) return@launch
            edit { state ->
                when {
                    result == null -> state.copy(lookup = MemberLookupState.NotFound(query))
                    result.userId == state.currentUser.id -> state.copy(lookup = MemberLookupState.AlreadyAdded("You"))
                    state.members.any { it.key == result.userId } ->
                        state.copy(lookup = MemberLookupState.AlreadyAdded(result.displayName))
                    else -> state.copy(
                        members = state.members + MemberDraft.Existing(result),
                        query = "",
                        lookup = MemberLookupState.Idle
                    )
                }
            }
        }
    }

    fun sendInvite() = edit { state ->
        val notFound = state.lookup as? MemberLookupState.NotFound ?: return@edit state
        val invite = MemberDraft.Invited(notFound.query)
        if (state.members.any { it.key == invite.key }) state.copy(lookup = MemberLookupState.Idle, query = "")
        else state.copy(members = state.members + invite, lookup = MemberLookupState.Idle, query = "")
    }

    fun create() {
        val current = mutableState.value
        if (!current.canCreate) return
        val draft = CreateGroupDraft(
            name = current.name.trim(),
            description = current.description.trim().ifEmpty { null },
            colourIndex = current.colour.colourIndex,
            members = current.members
        )
        mutableState.update { it.copy(submission = CreateGroupSubmission.Saving) }
        viewModelScope.launch {
            val result = try {
                CreateGroupSubmission.Saved(creation.createGroup(draft))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                CreateGroupSubmission.Failed(error.message ?: "Couldn't save the group on your phone. Try again.")
            }
            mutableState.update { it.copy(submission = result) }
        }
    }
}
