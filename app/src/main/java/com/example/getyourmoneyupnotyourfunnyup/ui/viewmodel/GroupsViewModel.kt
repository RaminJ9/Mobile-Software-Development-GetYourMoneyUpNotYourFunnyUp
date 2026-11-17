package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.DashboardGroup
import com.example.getyourmoneyupnotyourfunnyup.ui.groups.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface GroupsUiState {
    data object Loading : GroupsUiState
    data class Error(val message: String) : GroupsUiState
    data class Success(
        val data: GroupsListData,
        val filter: GroupListFilter = GroupListFilter.All,
        val query: String = "",
        /** Optimistic favourite toggles waiting for the local write to finish. */
        val favouriteOverrides: Map<String, Boolean> = emptyMap(),
        val canFavourite: Boolean = false,
        val message: String? = null,
        val sourceError: String? = null
    ) : GroupsUiState {
        val groups: List<DashboardGroup> = data.groups.map { group ->
            favouriteOverrides[group.group.id]?.let { group.copy(isFavourite = it) } ?: group
        }
        val counts: Map<GroupListFilter, Int> = filterCounts(groups, data.balancesByGroup)
        val shown: List<DashboardGroup> = visibleGroups(groups, data.balancesByGroup, filter, query)
        val summary: GroupsSummary? = groupsSummary(filter, shown, data.balancesByGroup, query)
        val empty: EmptyGroupsMessage? = if (shown.isEmpty()) emptyGroupsMessage(filter, query) else null
        fun balanceFor(groupId: String): Long? = data.balancesByGroup?.get(groupId)
    }
}

class GroupsViewModel(
    private val source: Flow<GroupsListData>,
    private val favourites: GroupFavouriteActions? = null
) : ViewModel() {
    private val mutableState = MutableStateFlow<GroupsUiState>(GroupsUiState.Loading)
    val state = mutableState.asStateFlow()
    private var observation: Job? = null
    private val writing = mutableSetOf<String>()

    init { observe() }

    fun retryObservation() {
        val current = mutableState.value
        if (observation?.isActive != true &&
            (current is GroupsUiState.Error || current is GroupsUiState.Success && current.sourceError != null)) observe()
    }

    private fun observe() {
        observation = viewModelScope.launch {
            try {
                source.collect { data ->
                    val current = mutableState.value as? GroupsUiState.Success
                    mutableState.value = if (current == null || current.data.userId != data.userId) {
                        GroupsUiState.Success(data, canFavourite = favourites != null)
                    } else {
                        // Drop an optimistic value once the stored snapshot agrees with it.
                        val overrides = current.favouriteOverrides.filter { (id, fav) ->
                            id in writing || data.groups.firstOrNull { it.group.id == id }?.isFavourite != fav
                        }.filterKeys { id -> data.groups.any { it.group.id == id } }
                        current.copy(data = data, favouriteOverrides = overrides, sourceError = null)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val current = mutableState.value as? GroupsUiState.Success
                mutableState.value = current?.copy(sourceError = "Couldn't refresh. Showing your saved groups.")
                    ?: GroupsUiState.Error(error.message ?: "Couldn't load your groups")
            }
        }
    }

    private fun update(change: (GroupsUiState.Success) -> GroupsUiState.Success) {
        mutableState.update { if (it is GroupsUiState.Success) change(it) else it }
    }

    fun setQuery(query: String) = update { it.copy(query = query.take(80)) }
    fun selectFilter(filter: GroupListFilter) = update { it.copy(filter = filter) }
    fun dismissMessage() = update { it.copy(message = null) }

    fun toggleFavourite(groupId: String) {
        val actions = favourites ?: return
        val current = mutableState.value as? GroupsUiState.Success ?: return
        val group = current.groups.firstOrNull { it.group.id == groupId } ?: return
        if (groupId in writing) return
        val next = !group.isFavourite
        writing += groupId
        update { it.copy(favouriteOverrides = it.favouriteOverrides + (groupId to next), message = null) }
        viewModelScope.launch {
            try {
                actions.setFavourite(groupId, next)
                writing -= groupId
                update { state ->
                    val stored = state.data.groups.firstOrNull { it.group.id == groupId }?.isFavourite
                    if (stored == next) state.copy(favouriteOverrides = state.favouriteOverrides - groupId) else state
                }
            } catch (error: CancellationException) {
                writing -= groupId
                throw error
            } catch (error: Exception) {
                writing -= groupId
                update {
                    it.copy(
                        favouriteOverrides = it.favouriteOverrides - groupId,
                        message = "Couldn't update ${group.group.name}. Try again."
                    )
                }
            }
        }
    }
}
