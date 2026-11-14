package com.example.getyourmoneyupnotyourfunnyup.data.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SyncRunnerTest {
    @Test fun missingProcessorIsUnavailable() = runTest {
        assertEquals(SyncRunResult.Unavailable, SyncRunner(null, { "u" }).run("u"))
    }

    @Test fun wrongOrMissingAccountDoesNotInvokeProcessor() = runTest {
        var calls = 0
        val runner = SyncRunner(SyncQueueProcessor { calls++; SyncStep.Empty }, { "u" })
        assertEquals(SyncRunResult.AccountChanged, runner.run("other"))
        assertEquals(SyncRunResult.AccountChanged, runner.run(""))
        assertEquals(0, calls)
    }

    @Test fun acknowledgesThenDrains() = runTest {
        var calls = 0
        val runner = SyncRunner(SyncQueueProcessor {
            if (calls++ == 0) SyncStep.Acknowledged(it) else SyncStep.Empty
        }, { "u" })
        assertEquals(SyncRunResult.Complete, runner.run("u"))
        assertEquals(2, calls)
    }

    @Test fun temporaryFailureRequiresRetry() = runTest {
        val runner = SyncRunner(SyncQueueProcessor { SyncStep.RetryLater }, { "u" })
        assertEquals(SyncRunResult.Retry, runner.run("u"))
    }

    @Test fun blockedOrUnexpectedFailureNeedsAttention() = runTest {
        assertEquals(SyncRunResult.NeedsAttention,
            SyncRunner(SyncQueueProcessor { SyncStep.NeedsAttention }, { "u" }).run("u"))
        assertEquals(SyncRunResult.NeedsAttention,
            SyncRunner(SyncQueueProcessor { error("Unsupported payload") }, { "u" }).run("u"))
    }

    @Test fun timeoutLeavesWorkRetryable() = runTest {
        val runner = SyncRunner(SyncQueueProcessor { awaitCancellation() }, { "u" }, timeoutMillis = 100)
        assertEquals(SyncRunResult.Retry, runner.run("u"))
    }

    @Test fun operationLimitEndsTheRun() = runTest {
        var calls = 0
        val runner = SyncRunner(SyncQueueProcessor { calls++; SyncStep.Acknowledged(it) }, { "u" }, maxOperations = 2)
        assertEquals(SyncRunResult.Retry, runner.run("u"))
        assertEquals(2, calls)
    }

    @Test fun accountChangeStopsBeforeAnotherOperation() = runTest {
        var account: String? = "u"
        var calls = 0
        val runner = SyncRunner(SyncQueueProcessor {
            calls++
            account = null
            SyncStep.Acknowledged(it)
        }, { account })
        assertEquals(SyncRunResult.AccountChanged, runner.run("u"))
        assertEquals(1, calls)
    }

    @Test fun wrongOwnerAcknowledgementIsRejected() = runTest {
        val runner = SyncRunner(SyncQueueProcessor { SyncStep.Acknowledged("other") }, { "u" })
        assertEquals(SyncRunResult.NeedsAttention, runner.run("u"))
    }

    @Test fun automaticAndManualRunnersCannotProcessConcurrently() = runTest {
        var active = 0
        var maximum = 0
        val processor = SyncQueueProcessor {
            active++
            maximum = maxOf(maximum, active)
            delay(100)
            active--
            SyncStep.Empty
        }
        val first = async { SyncRunner(processor, { "u" }).run("u") }
        val second = async { SyncRunner(processor, { "u" }).run("u") }
        assertEquals(SyncRunResult.Complete, first.await())
        assertEquals(SyncRunResult.Complete, second.await())
        assertEquals(1, maximum)
    }

    @Test fun accountIsRecheckedAfterWaitingForLock() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = launch {
            SyncRunner(SyncQueueProcessor { entered.complete(Unit); release.await(); SyncStep.Empty }, { "u" }).run("u")
        }
        entered.await()
        var account: String? = "u"
        var calls = 0
        val second = async { SyncRunner(SyncQueueProcessor { calls++; SyncStep.Empty }, { account }).run("u") }
        runCurrent()
        account = "other"
        release.complete(Unit)
        first.join()
        assertEquals(SyncRunResult.AccountChanged, second.await())
        assertEquals(0, calls)
    }

    @Test fun cancellationReleasesLockWithoutRetrying() = runTest {
        val entered = CompletableDeferred<Unit>()
        val job = launch {
            SyncRunner(SyncQueueProcessor { entered.complete(Unit); awaitCancellation() }, { "u" }).run("u")
        }
        entered.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(SyncRunResult.Complete, SyncRunner(SyncQueueProcessor { SyncStep.Empty }, { "u" }).run("u"))
    }

    @Test fun callersTimeoutIsNotConvertedIntoRetry() = runTest {
        var timedOut = false
        try {
            withTimeout(50) {
                SyncRunner(SyncQueueProcessor { awaitCancellation() }, { "u" }).run("u")
            }
        } catch (error: TimeoutCancellationException) {
            timedOut = true
        }
        assertTrue(timedOut)
    }
}
