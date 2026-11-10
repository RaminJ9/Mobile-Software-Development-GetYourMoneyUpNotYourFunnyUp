package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.getyourmoneyupnotyourfunnyup.data.repository.LedgerRepository
import com.example.getyourmoneyupnotyourfunnyup.model.LedgerSnapshot
import com.example.getyourmoneyupnotyourfunnyup.model.myGroups
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@Immutable
data class MemberChipUi(val id: String, val name: String, val avatar: AvatarUi, val kind: Kind) {
    enum class Kind { You, Added, Invited }
}

@Immutable
data class CreateGroupUiState(
    val name: String,
    val description: String,
    val colorIndex: Int,
    val members: List<MemberChipUi>,
    val query: String,
    val notFoundContact: String?,
    val suggestions: List<PersonSuggestionUi>,
    /** Set once the group exists so the screen can navigate into it. */
    val createdGroupId: String?,
) {
    val canCreate: Boolean get() = name.isNotBlank()
    val previewInitial: String get() = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
}

sealed interface CreateGroupAction {
    data class NameChanged(val value: String) : CreateGroupAction
    data class DescriptionChanged(val value: String) : CreateGroupAction
    data class PickColor(val index: Int) : CreateGroupAction
    data class QueryChanged(val value: String) : CreateGroupAction
    data object SubmitQuery : CreateGroupAction
    data class AddSuggested(val personId: String) : CreateGroupAction
    data object Invite : CreateGroupAction
    data class Remove(val chipId: String) : CreateGroupAction
    data object Create : CreateGroupAction
}

data class CreateGroupLocalState(
    val name: String = "",
    val description: String = "",
    val colorIndex: Int = 4,
    val addedIds: List<String> = emptyList(),
    val invited: List<String> = emptyList(),
    val query: String = "",
    val notFound: String? = null,
    val createdGroupId: String? = null,
)

/** The six colours offered when creating a group (the guide keeps 7–9 for later groups). */
const val CREATE_GROUP_COLOR_COUNT = 6

fun buildCreateGroupUiState(snapshot: LedgerSnapshot, local: CreateGroupLocalState): CreateGroupUiState {
    val me = snapshot.currentUserId
    val chips = listOf(MemberChipUi(me, "You", AvatarUi(snapshot.me.initial, -1), MemberChipUi.Kind.You)) +
        local.addedIds.map { id ->
            val person = snapshot.person(id)
            MemberChipUi(id, person.name, AvatarUi(person.initial, person.paletteIndex), MemberChipUi.Kind.Added)
        } +
        local.invited.map { contact ->
            MemberChipUi("invite:$contact", contact.substringBefore('@'), AvatarUi(contact.take(1).uppercase(), 0), MemberChipUi.Kind.Invited)
        }
    val suggestions = snapshot.myGroups().flatMap { group -> group.members.map { it.userId } }
        .filter { it != me && it !in local.addedIds }
        .groupingBy { it }.eachCount()
        .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { snapshot.person(it.key).name })
        .take(4)
        .map { snapshot.person(it.key) }
        .map { PersonSuggestionUi(it.id, it.name, it.contact, AvatarUi(it.initial, it.paletteIndex)) }
    return CreateGroupUiState(
        name = local.name,
        description = local.description,
        colorIndex = local.colorIndex,
        members = chips,
        query = local.query,
        notFoundContact = local.notFound,
        suggestions = suggestions,
        createdGroupId = local.createdGroupId,
    )
}

/** Create group → navigates into the new group. */
class CreateGroupViewModel(private val ledger: LedgerRepository) : ViewModel() {
    private val local = MutableStateFlow(CreateGroupLocalState())

    val uiState: StateFlow<CreateGroupUiState> = combine(ledger.snapshot, local, ::buildCreateGroupUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, buildCreateGroupUiState(ledger.snapshot.value, local.value))

    fun onAction(action: CreateGroupAction) {
        when (action) {
            is CreateGroupAction.NameChanged -> local.update { it.copy(name = action.value) }
            is CreateGroupAction.DescriptionChanged -> local.update { it.copy(description = action.value) }
            is CreateGroupAction.PickColor -> local.update { it.copy(colorIndex = action.index) }
            is CreateGroupAction.QueryChanged -> local.update { it.copy(query = action.value, notFound = null) }
            CreateGroupAction.SubmitQuery -> {
                val query = local.value.query.trim()
                if (query.isEmpty()) return
                val person = ledger.findPerson(query)
                if (person == null) {
                    local.update { it.copy(notFound = query) }
                } else {
                    local.update { state ->
                        state.copy(addedIds = (state.addedIds + person.id).distinct(), query = "", notFound = null)
                    }
                }
            }
            is CreateGroupAction.AddSuggested -> local.update {
                it.copy(addedIds = (it.addedIds + action.personId).distinct(), query = "", notFound = null)
            }
            CreateGroupAction.Invite -> local.update { state ->
                val contact = state.notFound ?: return@update state
                state.copy(invited = (state.invited + contact).distinct(), notFound = null, query = "")
            }
            is CreateGroupAction.Remove -> local.update { state ->
                if (action.chipId.startsWith("invite:")) {
                    state.copy(invited = state.invited - action.chipId.removePrefix("invite:"))
                } else {
                    state.copy(addedIds = state.addedIds - action.chipId)
                }
            }
            CreateGroupAction.Create -> {
                val state = local.value
                if (state.name.isBlank() || state.createdGroupId != null) return
                val id = ledger.createGroup(
                    name = state.name,
                    description = state.description,
                    colorIndex = state.colorIndex,
                    memberIds = state.addedIds,
                    invitedContacts = state.invited,
                )
                local.update { it.copy(createdGroupId = id) }
            }
        }
    }
}
