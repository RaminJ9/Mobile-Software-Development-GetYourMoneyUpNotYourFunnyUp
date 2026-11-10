package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.getyourmoneyupnotyourfunnyup.data.repository.LedgerRepository
import com.example.getyourmoneyupnotyourfunnyup.model.LedgerSnapshot
import com.example.getyourmoneyupnotyourfunnyup.model.MoneyFormat
import com.example.getyourmoneyupnotyourfunnyup.model.myGroups
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class GroupFilter(val label: String) {
    All("All"),
    Favourites("Favourites"),
    Owe("You owe"),
    Owed("Owed to you"),
    Settled("Settled");

    fun matches(row: GroupRowUi): Boolean = when (this) {
        All -> true
        Favourites -> row.isFavourite
        Owe -> row.netCents < 0
        Owed -> row.netCents > 0
        Settled -> row.netCents == 0L
    }
}

enum class SummaryTone { Owe, Owed }

@Immutable
data class GroupsUiState(
    val query: String,
    val filter: GroupFilter,
    val counts: Map<GroupFilter, Int>,
    val rows: List<GroupRowUi>,
    /** "You owe 412,50 kr across 3 groups" — only for the You owe / Owed to you filters. */
    val summary: String?,
    val summaryTone: SummaryTone,
    val emptyTitle: String?,
    val emptyText: String,
)

sealed interface GroupsAction {
    data class QueryChanged(val query: String) : GroupsAction
    data class SelectFilter(val filter: GroupFilter) : GroupsAction
    data class ToggleFavourite(val groupId: String) : GroupsAction
}

data class GroupsLocalState(val query: String = "", val filter: GroupFilter = GroupFilter.All)

fun buildGroupsUiState(snapshot: LedgerSnapshot, local: GroupsLocalState, now: Long): GroupsUiState {
    val all = snapshot.myGroups().map { snapshot.groupRow(it, now) }.sortedByDescending { it.lastActiveAt }
    val q = local.query.trim().lowercase()
    val list = all.filter(local.filter::matches).filter { q.isEmpty() || it.name.lowercase().contains(q) }

    val summary = when {
        q.isNotEmpty() || list.isEmpty() -> null
        local.filter == GroupFilter.Owe ->
            "You owe ${MoneyFormat.kr(list.sumOf { it.netCents })} kr across ${groupsLabel(list.size)}"
        local.filter == GroupFilter.Owed ->
            "You’re owed ${MoneyFormat.kr(list.sumOf { it.netCents })} kr across ${groupsLabel(list.size)}"
        else -> null
    }
    val (emptyTitle, emptyText) = when {
        list.isNotEmpty() -> null to ""
        q.isNotEmpty() -> "No groups match “${local.query.trim()}”" to "Check the spelling or try another filter."
        local.filter == GroupFilter.Favourites ->
            "No favourites yet" to "Tap the star on a group to pin it here and on your home screen."
        local.filter == GroupFilter.Owe -> "You don’t owe anyone" to "All your groups are square."
        all.isEmpty() -> "No groups yet" to "Create a group for your flat, a trip or Friday bar."
        else -> "Nothing here" to "All your groups are square."
    }
    return GroupsUiState(
        query = local.query,
        filter = local.filter,
        counts = GroupFilter.entries.associateWith { filter -> all.count(filter::matches) },
        rows = list,
        summary = summary,
        summaryTone = if (local.filter == GroupFilter.Owe) SummaryTone.Owe else SummaryTone.Owed,
        emptyTitle = emptyTitle,
        emptyText = emptyText,
    )
}

private fun groupsLabel(count: Int) = if (count == 1) "1 group" else "$count groups"

/** S3 Groups tab with S-filter (search + filters) and S-empty states. */
class GroupsViewModel(private val ledger: LedgerRepository) : ViewModel() {
    private val local = MutableStateFlow(GroupsLocalState())

    val uiState: StateFlow<GroupsUiState> = combine(ledger.snapshot, local) { snapshot, localState ->
        buildGroupsUiState(snapshot, localState, ledger.now())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, buildGroupsUiState(ledger.snapshot.value, local.value, ledger.now()))

    fun onAction(action: GroupsAction) {
        when (action) {
            is GroupsAction.QueryChanged -> local.update { it.copy(query = action.query) }
            is GroupsAction.SelectFilter -> local.update { it.copy(filter = action.filter) }
            is GroupsAction.ToggleFavourite -> ledger.toggleFavourite(action.groupId)
        }
    }
}
