package cc.pscly.onememos.worker

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import cc.pscly.onememos.domain.sync.FullResyncScheduleResult
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkManagerSyncSchedulerTest {
    @Test
    fun concurrentFullResyncDuringEnqueueHandoff_createsOneKeepRequest() =
        runBlocking {
            val handoff = CompletableDeferred<Unit>()
            val workManager = FakeSyncWorkManager(handoff = handoff)
            val scheduler = WorkManagerSyncScheduler(workManager)

            val first = async { scheduler.requestFullResync() }
            workManager.awaitEnqueueStarted()
            val second = async { scheduler.requestFullResync() }

            assertEquals(1, workManager.oneTimeEnqueues.size)
            handoff.complete(Unit)

            assertTrue(first.await() is FullResyncScheduleResult.Accepted)
            assertEquals(FullResyncScheduleResult.Duplicate, second.await())
            assertEquals(1, workManager.oneTimeEnqueues.size)
            assertEquals(ExistingWorkPolicy.KEEP, workManager.oneTimeEnqueues.single().policy)
            assertFalse(workManager.oneTimeEnqueues.any { it.policy == ExistingWorkPolicy.REPLACE })
        }

    @Test
    fun unfinishedFullAndNormalWork_returnDuplicateAndBusyWithoutEnqueue() =
        runBlocking {
            val fullWorkManager = FakeSyncWorkManager()
            fullWorkManager.unfinished +=
                SyncWorkInfo(
                    id = UUID.randomUUID(),
                    tags = setOf(MemosSyncWorker.TAG, MemosSyncWorker.FULL_RESYNC_TAG),
                )
            val fullScheduler = WorkManagerSyncScheduler(fullWorkManager)
            assertEquals(FullResyncScheduleResult.Duplicate, fullScheduler.requestFullResync())
            assertTrue(fullWorkManager.oneTimeEnqueues.isEmpty())

            val normalWorkManager = FakeSyncWorkManager()
            normalWorkManager.unfinished +=
                SyncWorkInfo(
                    id = UUID.randomUUID(),
                    tags = setOf(MemosSyncWorker.TAG),
                )
            val normalScheduler = WorkManagerSyncScheduler(normalWorkManager)
            assertEquals(FullResyncScheduleResult.Busy, normalScheduler.requestFullResync())
            assertTrue(normalWorkManager.oneTimeEnqueues.isEmpty())
        }

    @Test
    fun enqueueFailure_isPropagated() =
        runBlocking {
            val failure = IllegalStateException("enqueue failed")
            val workManager = FakeSyncWorkManager(enqueueFailure = failure)
            val scheduler = WorkManagerSyncScheduler(workManager)

            var caught: Throwable? = null
            try {
                scheduler.requestFullResync()
            } catch (throwable: Throwable) {
                caught = throwable
            }
            assertEquals(failure, caught)
        }

    private class FakeSyncWorkManager(
        private val handoff: CompletableDeferred<Unit> = CompletableDeferred(Unit),
        private val enqueueFailure: Throwable? = null,
    ) : SyncWorkManager {
        val unfinished = mutableListOf<SyncWorkInfo>()
        val oneTimeEnqueues = mutableListOf<OneTimeEnqueue>()
        private val enqueueStarted = CompletableDeferred<Unit>()

        override suspend fun unfinishedWork(uniqueWorkName: String): List<SyncWorkInfo> = unfinished.toList()

        override fun enqueueUniqueWork(
            uniqueWorkName: String,
            policy: ExistingWorkPolicy,
            request: OneTimeWorkRequest,
        ): SyncEnqueueOperation {
            oneTimeEnqueues += OneTimeEnqueue(policy = policy, request = request)
            enqueueStarted.complete(Unit)
            return SyncEnqueueOperation {
                handoff.await()
                enqueueFailure?.let { throw it }
                unfinished += SyncWorkInfo(id = request.id, tags = request.tags)
            }
        }

        override suspend fun containsWork(id: UUID): Boolean = unfinished.any { it.id == id }

        override fun enqueueUniquePeriodicWork(
            uniqueWorkName: String,
            policy: ExistingPeriodicWorkPolicy,
            request: PeriodicWorkRequest,
        ) = Unit

        suspend fun awaitEnqueueStarted() = enqueueStarted.await()
    }

    private data class OneTimeEnqueue(
        val policy: ExistingWorkPolicy,
        val request: OneTimeWorkRequest,
    )
}
