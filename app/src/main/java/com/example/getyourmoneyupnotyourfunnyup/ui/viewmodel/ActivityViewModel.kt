package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.ui.dashboard.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

sealed interface ActivityUiState {
    data object Loading : ActivityUiState
    data class Error(val message: String) : ActivityUiState
    data class Success(
        val data: ActivityData,
        val filter: ActivityFilter = ActivityFilter.All,
        val readIds: Set<String> = emptySet(),
        val actions: Map<String, InlineActionState> = emptyMap(),
        val canSettle: Boolean = false,
        val canRetry: Boolean = false,
        val sourceError: String? = null
    ) : ActivityUiState {
        val events: List<ActivityEvent> = data.events.orEmpty().distinctBy { it.id }
        val needsCount: Int = events.count { it.needsAction(data.userId, actionFor(it)) }
        val hasUnread: Boolean = events.any { it.unread && it.id !in readIds }
        fun actionFor(event: ActivityEvent): InlineActionState {
            val action = actions[event.id] ?: InlineActionState.Idle
            if (event.type != ActivityType.SyncFailure) return action
            return when (event.syncStatus) {
                SyncStatus.SYNCED -> InlineActionState.Done(SyncStatus.SYNCED)
                SyncStatus.PENDING_INSERT, SyncStatus.PENDING_UPDATE, SyncStatus.PENDING_DELETE ->
                    if (action == InlineActionState.Idle) InlineActionState.Done(event.syncStatus) else action
                else -> action
            }
        }
    }
}

class ActivityViewModel(
    private val source: Flow<ActivityData>,
    private val settlements: SettlementActions? = null,
    private val retries: SyncRetryActions? = null
) : ViewModel() {
    private val mutableState = MutableStateFlow<ActivityUiState>(ActivityUiState.Loading)
    val state = mutableState.asStateFlow()
    private val activeIds = mutableSetOf<String>()
    private var observation: Job? = null

    init { observe() }

    fun retryObservation() {
        val current = mutableState.value
        if (observation?.isActive != true &&
            (current is ActivityUiState.Error || current is ActivityUiState.Success && current.sourceError != null)) observe()
    }

    private fun observe() {
        observation = viewModelScope.launch {
            try {
                source.collect { data ->
                    val current = mutableState.value as? ActivityUiState.Success
                    mutableState.value = if (current == null || current.data.userId != data.userId) {
                        ActivityUiState.Success(data, canSettle = settlements != null, canRetry = retries != null)
                    } else {
                        current.copy(data = data, sourceError = null, actions = reconcileActions(current, data))
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val current = mutableState.value as? ActivityUiState.Success
                mutableState.value = current?.copy(sourceError = "Couldn't refresh. Showing the last activity.")
                ?: ActivityUiState.Error(error.message ?: "Couldn't load activity")
            }
        }
    }

    private fun reconcileActions(current: ActivityUiState.Success, data: ActivityData): Map<String, InlineActionState> =
        current.actions.mapNotNull { (id, action) ->
            val previous = current.events.firstOrNull { it.id == id }
            val next = data.events.orEmpty().firstOrNull { it.id == id }
            if (next == null || previous == null || previous.payment != next.payment || previous.type != next.type) null
            else id to when {
                next.type == ActivityType.SyncFailure && next.syncStatus == SyncStatus.SYNCED ->
                    InlineActionState.Done(SyncStatus.SYNCED)
                next.type == ActivityType.SyncFailure && next.syncStatus == SyncStatus.FAILED &&
                    (previous.syncStatus != SyncStatus.FAILED || action is InlineActionState.Done && action.syncStatus != SyncStatus.SYNCED) ->
                    InlineActionState.Failed("Couldn't sync yet. Try again.")
                next.type == ActivityType.SyncFailure && action != InlineActionState.Working &&
                    next.syncStatus in listOf(SyncStatus.PENDING_INSERT, SyncStatus.PENDING_UPDATE, SyncStatus.PENDING_DELETE) ->
                    InlineActionState.Done(next.syncStatus)
                else -> action
            }
        }.toMap()

    private fun update(change: (ActivityUiState.Success) -> ActivityUiState.Success) {
        mutableState.update { if (it is ActivityUiState.Success) change(it) else it }
    }

    fun selectFilter(filter: ActivityFilter) = update { it.copy(filter = filter) }
    fun markAllRead() = update { it.copy(readIds = it.readIds + it.events.map { event -> event.id }) }

    fun askPayment(id: String) = update {
        val event = it.events.firstOrNull { event -> event.id == id }
        if (event == null || !it.canSettle || !event.canPay(it.data.userId) || id in activeIds || it.actionFor(event) is InlineActionState.Done) it
        else it.copy(
            actions = it.actions.filterValues { value -> value != InlineActionState.Confirming } + (id to InlineActionState.Confirming),
            readIds = it.readIds + id
        )
    }

    fun cancelPayment() = update { it.copy(actions = it.actions.filterValues { value -> value != InlineActionState.Confirming }) }

    fun confirmPayment(id: String) {
        val current = mutableState.value as? ActivityUiState.Success ?: return
        val event = current.events.firstOrNull { it.id == id } ?: return
        val service = settlements ?: return
        if (current.actions[id] != InlineActionState.Confirming || !event.canPay(current.data.userId)) return
        runAction(event) { service.recordSettlement(event.payment!!) }
    }

    fun retry(id: String) {
        val current = mutableState.value as? ActivityUiState.Success ?: return
        val event = current.events.firstOrNull { it.id == id } ?: return
        val service = retries ?: return
        if (event.type != ActivityType.SyncFailure || !event.needsAction(current.data.userId, current.actionFor(event))) return
        runAction(event) {
            val status = service.retry(event)
            check(status != SyncStatus.FAILED) { "Couldn't sync yet. Try again." }
            status
        }
    }

    private fun runAction(event: ActivityEvent, action: suspend () -> SyncStatus) {
        if (!activeIds.add(event.id)) return
        val userId = (mutableState.value as? ActivityUiState.Success)?.data?.userId ?: return
        update { it.copy(actions = it.actions + (event.id to InlineActionState.Working), readIds = it.readIds + event.id) }
        viewModelScope.launch {
            try {
                val result = try {
                    InlineActionState.Done(action())
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    InlineActionState.Failed(error.message ?: "Couldn't complete this action. Try again.")
                }
                update {
                    if (it.data.userId == userId && it.actions[event.id] == InlineActionState.Working &&
                        it.events.any { current -> current.id == event.id && current.payment == event.payment && current.type == event.type }) {
                        it.copy(actions = it.actions + (event.id to result))
                    } else it
                }
            } finally { activeIds.remove(event.id) }
        }
    }
}
