package com.example.getyourmoneyupnotyourfunnyup.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.getyourmoneyupnotyourfunnyup.data.sync.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import java.util.concurrent.TimeUnit

class SyncWorkTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var workManager: WorkManager

    @Before fun initialize() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        workManager = WorkManager.getInstance(context)
    }

    @After fun close() {
        workManager.cancelAllWork().result.get(5, TimeUnit.SECONDS)
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test fun automaticWorkIsUniqueAndUsesNetworkConstraintAndBackoff() {
        val first = SyncScheduler.request("u")
        val second = SyncScheduler.request("u")
        val name = SyncScheduler.workName("u")
        workManager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, first).result.get(5, TimeUnit.SECONDS)
        workManager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, second).result.get(5, TimeUnit.SECONDS)
        val work = workManager.getWorkInfosForUniqueWork(name).get(5, TimeUnit.SECONDS)
        assertEquals(1, work.size)
        assertEquals(first.id, work.single().id)
        assertEquals(NetworkType.CONNECTED, first.workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, first.workSpec.backoffPolicy)
        assertEquals(10000L, first.workSpec.backoffDelayDuration)
        assertEquals(WorkInfo.State.ENQUEUED, work.single().state)
    }

    @Test fun accountAndManualWorkNamesAreDistinct() {
        assertNotEquals(SyncScheduler.workName("u"), SyncScheduler.workName("other"))
        assertNotEquals(SyncScheduler.workName("u"), SyncScheduler.workName("u", manual = true))
        assertEquals(SyncScheduler.workName("u"), SyncScheduler.workName("u"))
    }

    @Test fun workRunsOnlyAfterTheNetworkConstraintIsReleased() = runBlocking {
        val request = SyncScheduler.request("u")
        workManager.enqueue(request).result.get(5, TimeUnit.SECONDS)
        assertEquals(WorkInfo.State.ENQUEUED, workManager.getWorkInfoById(request.id).get()!!.state)
        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(request.id)
        val finished = withTimeout(5000) {
            workManager.getWorkInfoByIdFlow(request.id).first { it?.state?.isFinished == true }!!
        }
        assertEquals(WorkInfo.State.FAILED, finished.state)
        assertEquals("Unavailable", finished.outputData.getString("reason"))
    }

    @Test fun unavailableProcessorAndWrongAccountDoNotScheduleWork() = runBlocking {
        assertEquals(ScheduleResult.Unavailable,
            SyncScheduler(workManager, SyncRunner(null, { "u" })).scheduleAutomatic("u"))
        assertEquals(ScheduleResult.AccountChanged, SyncScheduler(workManager,
            SyncRunner(SyncQueueProcessor { SyncStep.Empty }, { "other" })).scheduleManual("u"))
        assertTrue(workManager.getWorkInfosForUniqueWork(SyncScheduler.workName("u")).get().isEmpty())
        assertTrue(workManager.getWorkInfosForUniqueWork(SyncScheduler.workName("u", true)).get().isEmpty())
    }

    @Test fun restartHookSchedulesPendingWorkWithoutClaimingSync() = runBlocking {
        val scheduler = SyncScheduler(workManager, SyncRunner(SyncQueueProcessor { SyncStep.Empty }, { "u" }))
        assertEquals(ScheduleResult.Pending, scheduler.recoverAfterRestart("u"))
        assertEquals(ScheduleResult.Pending, scheduler.recoverAfterRestart("u"))
        assertEquals(1, workManager.getWorkInfosForUniqueWork(SyncScheduler.workName("u")).get().size)
    }

    @Test fun noFactoryFailsClosed() = runBlocking {
        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setInputData(workDataOf(SyncWorker.AccountKey to "u")).build()
        assertEquals(ListenableWorker.Result.failure(workDataOf("reason" to "Unavailable")), worker.doWork())
    }

    @Test fun transientFailureProducesWorkManagerRetry() = runBlocking {
        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setInputData(workDataOf(SyncWorker.AccountKey to "u"))
            .setWorkerFactory(SyncWorkerFactory(SyncQueueProcessor { SyncStep.RetryLater }, { "u" })).build()
        assertEquals(ListenableWorker.Result.retry(), worker.doWork())
    }

    @Test fun unsupportedOperationProducesFailure() = runBlocking {
        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setInputData(workDataOf(SyncWorker.AccountKey to "u"))
            .setWorkerFactory(SyncWorkerFactory(SyncQueueProcessor { SyncStep.NeedsAttention }, { "u" })).build()
        assertEquals(ListenableWorker.Result.failure(workDataOf("reason" to "NeedsAttention")), worker.doWork())
    }

    @Test fun emptyQueueIsWorkerSuccessWithoutASyncedUiMessage() = runBlocking {
        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setInputData(workDataOf(SyncWorker.AccountKey to "u"))
            .setWorkerFactory(SyncWorkerFactory(SyncQueueProcessor { SyncStep.Empty }, { "u" })).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }
}
