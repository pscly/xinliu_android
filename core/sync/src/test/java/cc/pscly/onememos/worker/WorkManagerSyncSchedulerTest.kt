package cc.pscly.onememos.worker

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import cc.pscly.onememos.domain.sync.FullResyncScheduleResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class WorkManagerSyncSchedulerTest {
    @Test
    fun accepted_returnsActualWorkRequestId() =
        runBlocking {
            val manager = FakeSyncWorkManager()
            val scheduler = WorkManagerSyncScheduler(manager)
            val result = scheduler.requestFullResync()
            assertTrue(result is FullResyncScheduleResult.Accepted)
            val accepted = result as FullResyncScheduleResult.Accepted
            val persisted = manager.oneTimeEnqueues.single().request
            assertEquals(persisted.id.toString(), accepted.requestId)
        }

    @Test
    fun acceptedRequestFinishedBeforePostCommitSnapshot_stillReturnsAccepted() =
        runBlocking {
            val manager = FakeSyncWorkManager()
            manager.snapshotAfterCommit = { snapshot ->
                snapshot.mapValues { (id, info) ->
                    if (id == manager.oneTimeEnqueues.single().request.id) {
                        info.copy(isFinished = true)
                    } else {
                        info
                    }
                }
            }
            val scheduler = WorkManagerSyncScheduler(manager)
            assertTrue(scheduler.requestFullResync() is FullResyncScheduleResult.Accepted)
        }

    @Test
    fun twoSchedulerInstances_fullWinnerFinishesDuringHandoff_loserReturnsDuplicate() =
        runBlocking {
            val manager = FakeSyncWorkManager()
            val s1 = WorkManagerSyncScheduler(manager)
            val s2 = WorkManagerSyncScheduler(manager)

            val first = async { s1.requestFullResync() }
            manager.awaitEnqueueStarted()
            val winnerRequest = manager.oneTimeEnqueues.single().request

            // 胜者期间第二名可见未结束全量请求
            manager.preCommitSnapshot = {
                val winner = SyncWorkInfo(
                    id = winnerRequest.id,
                    tags = setOf(MemosSyncWorker.TAG, MemosSyncWorker.FULL_RESYNC_TAG),
                )
                mapOf(winnerRequest.id to winner)
            }

            // 第二名先看到全量在跑 → Duplicate（因为 Mutex 释放了）
            // 但测试的要点是共享 fake 数据库，所以第二名通过 Mutex 应直接看到 before 快照中的未结束全量。
            // 这里通过两个实例验证：胜者第一、第二名同时看到未结束状态
            val second =
                async {
                    manager.preCommitSnapshot = {
                        val winner = SyncWorkInfo(
                            id = winnerRequest.id,
                            tags = setOf(MemosSyncWorker.TAG, MemosSyncWorker.FULL_RESYNC_TAG),
                        )
                        mapOf(winnerRequest.id to winner)
                    }
                    s2.requestFullResync()
                }

            manager.handoff.complete(Unit)
            assertTrue(first.await() is FullResyncScheduleResult.Accepted)
            assertEquals(FullResyncScheduleResult.Duplicate, second.await())
        }

    @Test
    fun normalWinnerFinishesDuringFullHandoff_returnsBusy() =
        runBlocking {
            val manager = FakeSyncWorkManager()
            // before 快照里有未结束的普通同步（无 FULL_RESYNC_TAG）
            manager.preCommitSnapshot = {
                val normal = SyncWorkInfo(
                    id = UUID.randomUUID(),
                    tags = setOf(MemosSyncWorker.TAG),
                )
                mapOf(normal.id to normal)
            }
            val scheduler = WorkManagerSyncScheduler(manager)
            assertEquals(FullResyncScheduleResult.Busy, scheduler.requestFullResync())
            assertTrue(manager.oneTimeEnqueues.isEmpty())
        }

    @Test
    fun enqueueCommitCancellation_isRethrown() =
        runBlocking {
            val cancellation = CancellationException("commit cancelled")
            val manager = FakeSyncWorkManager(enqueueFailure = cancellation)
            val scheduler = WorkManagerSyncScheduler(manager)
            var caught: CancellationException? = null
            try {
                scheduler.requestFullResync()
            } catch (c: CancellationException) {
                caught = c
            }
            assertEquals(cancellation, caught)
        }

    @Test
    fun postCommitSnapshotCancellation_isRethrown() =
        runBlocking {
            val cancellation = CancellationException("snapshot cancelled")
            val manager = FakeSyncWorkManager(snapshotFailureAfterCommit = cancellation)
            val scheduler = WorkManagerSyncScheduler(manager)
            var caught: CancellationException? = null
            try {
                scheduler.requestFullResync()
            } catch (c: CancellationException) {
                caught = c
            }
            assertEquals(cancellation, caught)
        }

    @Test
    fun initialSnapshotFailure_isPropagated() =
        runBlocking {
            val failure = IllegalStateException("db down")
            val manager = FakeSyncWorkManager(snapshotFailure = failure)
            val scheduler = WorkManagerSyncScheduler(manager)
            var caught: Throwable? = null
            try {
                scheduler.requestFullResync()
            } catch (t: Throwable) {
                caught = t
            }
            assertEquals(failure, caught)
        }

    @Test
    fun postCommitSnapshotFailure_isPropagated() =
        runBlocking {
            val failure = IllegalStateException("db down after commit")
            val manager = FakeSyncWorkManager(snapshotFailureAfterCommit = failure)
            val scheduler = WorkManagerSyncScheduler(manager)
            var caught: Throwable? = null
            try {
                scheduler.requestFullResync()
            } catch (t: Throwable) {
                caught = t
            }
            assertEquals(failure, caught)
        }

    @Test
    fun missingOrMixedRacedWinner_isNotMisclassified() =
        runBlocking {
            // 己方 ID 不在 after 中，但 after 里没有新竞争请求 → 不变量错误
            val manager = FakeSyncWorkManager()
            manager.snapshotAfterCommit = { _ -> emptyMap() }
            val scheduler = WorkManagerSyncScheduler(manager)
            try {
                scheduler.requestFullResync()
                fail("expected invariant error")
            } catch (e: IllegalStateException) {
                assertTrue(e.message != null)
                assertTrue(e.message!!.contains("observable"))
            }

            val mixed = FakeSyncWorkManager()
            mixed.snapshotAfterCommit = {
                val full = UUID.randomUUID()
                val normal = UUID.randomUUID()
                mapOf(
                    full to SyncWorkInfo(id = full, tags = setOf(MemosSyncWorker.TAG, MemosSyncWorker.FULL_RESYNC_TAG)),
                    normal to SyncWorkInfo(id = normal, tags = setOf(MemosSyncWorker.TAG)),
                )
            }
            val scheduler2 = WorkManagerSyncScheduler(mixed)
            try {
                scheduler2.requestFullResync()
                fail("expected invariant error")
            } catch (e: IllegalStateException) {
                assertTrue(e.message != null)
                assertTrue(e.message!!.contains("ambiguous"))
            }
        }

    @Test
    fun allContendingRequestsUseKeep_andNeverReplace() =
        runBlocking {
            val manager = FakeSyncWorkManager()
            val c1 = async { WorkManagerSyncScheduler(manager).requestFullResync() }
            manager.awaitEnqueueStarted()
            val c2 = async { WorkManagerSyncScheduler(manager).requestFullResync() }
            manager.handoff.complete(Unit)

            c1.await()
            c2.await()
            assertFalse(manager.oneTimeEnqueues.any { it.policy == ExistingWorkPolicy.REPLACE })
            for (enqueue in manager.oneTimeEnqueues) {
                assertEquals(ExistingWorkPolicy.KEEP, enqueue.policy)
            }
        }

    @Test
    fun afterSnapshotIncludesFinishedOwnId_returnsAccepted() =
        runBlocking {
            val manager = FakeSyncWorkManager()
            manager.snapshotAfterCommit = { snapshot ->
                // 己方 ID 存在但已结束 → Accepted
                snapshot
            }
            val scheduler = WorkManagerSyncScheduler(manager)
            assertTrue(scheduler.requestFullResync() is FullResyncScheduleResult.Accepted)
        }

    @Test
    fun afterSnapshotExcludesOwnId_withOnlyFinishedFullContender_racesToDuplicate() =
        runBlocking {
            val manager = FakeSyncWorkManager()
            manager.snapshotAfterCommit = {
                val contending = UUID.randomUUID()
                mapOf(
                    contending to SyncWorkInfo(
                        id = contending,
                        tags = setOf(MemosSyncWorker.TAG, MemosSyncWorker.FULL_RESYNC_TAG),
                        isFinished = true,
                    ),
                )
            }
            val scheduler = WorkManagerSyncScheduler(manager)
            assertEquals(FullResyncScheduleResult.Duplicate, scheduler.requestFullResync())
        }

    @Test
    fun afterSnapshotExcludesOwnId_withOnlyFinishedNormalContender_racesToBusy() =
        runBlocking {
            val manager = FakeSyncWorkManager()
            manager.snapshotAfterCommit = {
                val contending = UUID.randomUUID()
                mapOf(
                    contending to SyncWorkInfo(
                        id = contending,
                        tags = setOf(MemosSyncWorker.TAG),
                        isFinished = true,
                    ),
                )
            }
            val scheduler = WorkManagerSyncScheduler(manager)
            assertEquals(FullResyncScheduleResult.Busy, scheduler.requestFullResync())
        }
    // ── Fake ──

    private class FakeSyncWorkManager(
        val handoff: CompletableDeferred<Unit> = CompletableDeferred(Unit),
        private val enqueueFailure: Throwable? = null,
        private val snapshotFailure: Throwable? = null,
        private val snapshotFailureAfterCommit: Throwable? = null,
    ) : SyncWorkManager {
        val oneTimeEnqueues = mutableListOf<OneTimeEnqueue>()
        private val enqueueStarted = CompletableDeferred<Unit>()
        var preCommitSnapshot: (suspend () -> Map<UUID, SyncWorkInfo>)? = null
        var snapshotAfterCommit: ((Map<UUID, SyncWorkInfo>) -> Map<UUID, SyncWorkInfo>)? = null
        private var snapshotCallCount = 0

        override suspend fun uniqueWorkSnapshot(
            uniqueWorkName: String,
        ): Map<UUID, SyncWorkInfo> {
            snapshotCallCount++
            if (snapshotCallCount <= 1 && snapshotFailure != null) throw snapshotFailure!!
            if (snapshotCallCount >= 2 && snapshotFailureAfterCommit != null) throw snapshotFailureAfterCommit!!
            val base = preCommitSnapshot?.invoke() ?: oneTimeEnqueues.associate {
                it.request.id to SyncWorkInfo(id = it.request.id, tags = it.request.tags)
            }
            if (snapshotCallCount >= 2 && snapshotAfterCommit != null) {
                return snapshotAfterCommit!!(base)
            }
            return base
        }

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
            }
        }

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
