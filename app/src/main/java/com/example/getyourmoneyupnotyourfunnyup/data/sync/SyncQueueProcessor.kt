package com.example.getyourmoneyupnotyourfunnyup.data.sync

fun interface SyncQueueProcessor {
    suspend fun processNext(accountId: String): SyncStep
}

sealed interface SyncStep {
    data object Empty : SyncStep
    data class Acknowledged(val accountId: String) : SyncStep
    data object RetryLater : SyncStep
    data object NeedsAttention : SyncStep
}

enum class SyncRunResult { Complete, Retry, Unavailable, AccountChanged, NeedsAttention }
