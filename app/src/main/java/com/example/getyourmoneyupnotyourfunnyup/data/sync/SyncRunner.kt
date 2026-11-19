package com.example.getyourmoneyupnotyourfunnyup.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.time.Duration.Companion.milliseconds

class SyncRunner(
    private val processor: SyncQueueProcessor?,
    private val currentAccount: () -> String?,
    private val timeoutMillis: Long = 30_000,
    private val maxOperations: Int = 20
) {
    init {
        require(timeoutMillis > 0 && maxOperations > 0)
    }

    fun readiness(accountId: String): SyncRunResult? = when {
        processor == null -> SyncRunResult.Unavailable
        accountId.isBlank() || currentAccount() != accountId -> SyncRunResult.AccountChanged
        else -> null
    }

    suspend fun run(accountId: String): SyncRunResult {
        readiness(accountId)?.let { return it }
        return try {
            withTimeout(timeoutMillis.milliseconds) {
                processingLock.withLock {
                    repeat(maxOperations) {
                        if (currentAccount() != accountId) return@withLock SyncRunResult.AccountChanged
                        val step = processor!!.processNext(accountId)
                        if (currentAccount() != accountId) return@withLock SyncRunResult.AccountChanged
                        when (step) {
                            SyncStep.Empty -> return@withLock SyncRunResult.Complete
                            SyncStep.RetryLater -> return@withLock SyncRunResult.Retry
                            SyncStep.NeedsAttention -> return@withLock SyncRunResult.NeedsAttention
                            is SyncStep.Acknowledged -> if (step.accountId != accountId) {
                                return@withLock SyncRunResult.NeedsAttention
                            }
                        }
                    }
                    SyncRunResult.Retry
                }
            }
        } catch (error: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            SyncRunResult.Retry
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            SyncRunResult.NeedsAttention
        }
    }

    companion object {
        private val processingLock = Mutex()
    }
}
