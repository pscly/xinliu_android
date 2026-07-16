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
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * 验证 WorkManagerSyncScheduler 的 KEEP 全量重同步仲裁。
 *
 * 跨实例正确性依赖：
 * 1. Fake 在 commit 时原子应用 KEEP（仅一个请求进入 unique 快照）
 * 2. 败者 requestId 永不出现在 after 快照
 * 3. 双调度器在 empty unfinished before 下各自 enqueue+commit，
 *    败者经 after.keys - before.keys 重分类（非仅 pre-commit 守卫）
 */
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
            assertTrue(persisted.id in manager.committedUniqueWork())
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
            val result = scheduler.requestFullResync()
            assertTrue(result is FullResyncScheduleResult.Accepted)
            val accepted = result as FullResyncScheduleResult.Accepted
            assertEquals(manager.oneTimeEnqueues.single().request.id.toString(), accepted.requestId)
        }

    @Test
    fun unfinishedFullBeforeGuard_returnsDuplicateWithoutEnqueue() =
        runBlocking {
            val manager = FakeSyncWorkManager()
            manager.seedCommitted(
                SyncWorkInfo(
                    id = UUID.randomUUID(),
                    tags = setOf(MemosSyncWorker.TAG, MemosSyncWorker.FULL_RESYNC_TAG),
                    isFinished = false,
                ),
            )
            val scheduler = WorkManagerSyncScheduler(manager)
            assertEquals(FullResyncScheduleResult.Duplicate, scheduler.requestFullResync())
            assertTrue(manager.oneTimeEnqueues.isEmpty())
            assertEquals(0, manager.commitCount.get())
        }

    @Test
    fun unfinishedNormalBeforeGuard_returnsBusyWithoutEnqueue() =
        runBlocking {
            val manager = FakeSyncWorkManager()
            manager.seedCommitted(
                SyncWorkInfo(
                    id = UUID.randomUUID(),
                    tags = setOf(MemosSyncWorker.TAG),
                    isFinished = false,
                ),
            )
            val scheduler = WorkManagerSyncScheduler(manager)
            assertEquals(FullResyncScheduleResult.Busy, scheduler.requestFullResync())
            assertTrue(manager.oneTimeEnqueues.isEmpty())
            assertEquals(0, manager.commitCount.get())
        }

    /**
     * 真跨实例 KEEP 竞态：两调度器实例各自 Mutex，before 均为空，双方都 enqueue+commit；
     * Fake 原子 KEEP 只接纳胜者；败者 ID 不进 unique 快照，经 after.keys-before.keys → Duplicate。
     */
    @Test
    fun dualSchedulerKeepRace_emptyBefore_winnerAccepted_loserFullDuplicateViaKeysDiff() =
        runBlocking {
            val manager = FakeSyncWorkManager(handoff = CompletableDeferred())
            val s1 = WorkManagerSyncScheduler(manager)
            val s2 = WorkManagerSyncScheduler(manager)

            val first = async { s1.requestFullResync() }
            manager.awaitEnqueueCount(1)
            // 第一名已越过空 before 并入队，尚未 commit
            assertTrue(manager.committedUniqueWork().isEmpty())

            val second = async { s2.requestFullResync() }
            manager.awaitEnqueueCount(2)
            assertTrue(manager.committedUniqueWork().isEmpty())
            assertEquals(2, manager.oneTimeEnqueues.size)
            assertTrue(manager.oneTimeEnqueues.all { it.policy == ExistingWorkPolicy.KEEP })

            manager.handoff.complete(Unit)

            val results = listOf(first.await(), second.await())
            val accepted = results.filterIsInstance<FullResyncScheduleResult.Accepted>()
            val duplicates = results.filter { it == FullResyncScheduleResult.Duplicate }
            assertEquals(1, accepted.size)
            assertEquals(1, duplicates.size)

            val winnerId = UUID.fromString(accepted.single().requestId)
            val committed = manager.committedUniqueWork()
            assertEquals(setOf(winnerId), committed.keys)
            assertTrue(MemosSyncWorker.FULL_RESYNC_TAG in committed.getValue(winnerId).tags)

            val loserId =
                manager.oneTimeEnqueues
                    .map { it.request.id }
                    .single { it != winnerId }
            assertFalse(loserId in committed)
            // 双方都完成了 KEEP commit，败者靠 keys-diff 而非 pre-commit 守卫
            assertEquals(2, manager.commitCount.get())
        }

    /**
     * 全量请求 before 为空并已 enqueue；commit 前普通同步抢占 unique 链；
     * 全量 KEEP 被丢弃后经 after.keys-before.keys → Busy（非 pre-commit Busy）。
     */
    @Test
    fun dualSchedulerKeepRace_emptyBefore_normalContender_fullBusyViaKeysDiff() =
        runBlocking {
            val manager = FakeSyncWorkManager(handoff = CompletableDeferred())
            val scheduler = WorkManagerSyncScheduler(manager)

            val full = async { scheduler.requestFullResync() }
            manager.awaitEnqueueCount(1)
            assertTrue(manager.committedUniqueWork().isEmpty())
            val fullRequestId = manager.oneTimeEnqueues.single().request.id

            val normalId = UUID.randomUUID()
            manager.seedCommitted(
                SyncWorkInfo(
                    id = normalId,
                    tags = setOf(MemosSyncWorker.TAG),
                    isFinished = false,
                ),
            )

            manager.handoff.complete(Unit)
            assertEquals(FullResyncScheduleResult.Busy, full.await())

            val committed = manager.committedUniqueWork()
            assertEquals(setOf(normalId), committed.keys)
            assertFalse(fullRequestId in committed)
            assertEquals(1, manager.commitCount.get())
        }

    /**
     * 双全量 KEEP 竞态后，将 after 中胜者标签降为普通同步，
     * 迫使败者经 keys-diff 重分类为 Busy（证明非仅靠 FULL 守卫）。
     */
    @Test
    fun dualSchedulerKeepRace_emptyBefore_winnerAccepted_loserNormalBusyViaKeysDiff() =
        runBlocking {
            val manager = FakeSyncWorkManager(handoff = CompletableDeferred())
            manager.snapshotAfterCommit = { snapshot ->
                snapshot.mapValues { (_, info) ->
                    info.copy(tags = setOf(MemosSyncWorker.TAG))
                }
            }
            val s1 = WorkManagerSyncScheduler(manager)
            val s2 = WorkManagerSyncScheduler(manager)

            val first = async { s1.requestFullResync() }
            manager.awaitEnqueueCount(1)
            val second = async { s2.requestFullResync() }
            manager.awaitEnqueueCount(2)
            assertTrue(manager.committedUniqueWork().isEmpty())

            manager.handoff.complete(Unit)

            val results = listOf(first.await(), second.await())
            val accepted = results.filterIsInstance<FullResyncScheduleResult.Accepted>()
            val busy = results.filter { it == FullResyncScheduleResult.Busy }
            assertEquals(1, accepted.size)
            assertEquals(1, busy.size)

            val winnerId = UUID.fromString(accepted.single().requestId)
            val committed = manager.committedUniqueWork()
            assertEquals(setOf(winnerId), committed.keys)

            val loserId =
                manager.oneTimeEnqueues
                    .map { it.request.id }
                    .single { it != winnerId }
            assertFalse(loserId in committed)
            assertEquals(2, manager.commitCount.get())
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
                    full to
                        SyncWorkInfo(
                            id = full,
                            tags = setOf(MemosSyncWorker.TAG, MemosSyncWorker.FULL_RESYNC_TAG),
                        ),
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
            val manager = FakeSyncWorkManager(handoff = CompletableDeferred())
            val c1 = async { WorkManagerSyncScheduler(manager).requestFullResync() }
            manager.awaitEnqueueCount(1)
            val c2 = async { WorkManagerSyncScheduler(manager).requestFullResync() }
            manager.awaitEnqueueCount(2)
            manager.handoff.complete(Unit)

            c1.await()
            c2.await()
            assertFalse(manager.oneTimeEnqueues.any { it.policy == ExistingWorkPolicy.REPLACE })
            for (enqueue in manager.oneTimeEnqueues) {
                assertEquals(ExistingWorkPolicy.KEEP, enqueue.policy)
            }
            assertEquals(1, manager.committedUniqueWork().size)
        }

    @Test
    fun afterSnapshotIncludesFinishedOwnId_returnsAccepted() =
        runBlocking {
            val manager = FakeSyncWorkManager()
            manager.snapshotAfterCommit = { snapshot ->
                // 名称语义：己方 ID 在 after 中且 isFinished=true → 仍 Accepted
                snapshot.mapValues { (_, info) -> info.copy(isFinished = true) }
            }
            val scheduler = WorkManagerSyncScheduler(manager)
            val result = scheduler.requestFullResync()
            assertTrue(result is FullResyncScheduleResult.Accepted)
            val accepted = result as FullResyncScheduleResult.Accepted
            val ownId = UUID.fromString(accepted.requestId)
            val afterView = manager.snapshotAfterCommit!!(manager.committedUniqueWork())
            assertTrue(ownId in afterView)
            assertTrue(afterView.getValue(ownId).isFinished)
        }

    @Test
    fun afterSnapshotExcludesOwnId_withOnlyFinishedFullContender_racesToDuplicate() =
        runBlocking {
            val manager = FakeSyncWorkManager()
            manager.snapshotAfterCommit = {
                val contending = UUID.randomUUID()
                mapOf(
                    contending to
                        SyncWorkInfo(
                            id = contending,
                            tags = setOf(MemosSyncWorker.TAG, MemosSyncWorker.FULL_RESYNC_TAG),
                            isFinished = true,
                        ),
                )
            }
            val scheduler = WorkManagerSyncScheduler(manager)
            assertEquals(FullResyncScheduleResult.Duplicate, scheduler.requestFullResync())
            val after = manager.snapshotAfterCommit!!(manager.committedUniqueWork())
            assertFalse(manager.oneTimeEnqueues.single().request.id in after)
        }

    @Test
    fun afterSnapshotExcludesOwnId_withOnlyFinishedNormalContender_racesToBusy() =
        runBlocking {
            val manager = FakeSyncWorkManager()
            manager.snapshotAfterCommit = {
                val contending = UUID.randomUUID()
                mapOf(
                    contending to
                        SyncWorkInfo(
                            id = contending,
                            tags = setOf(MemosSyncWorker.TAG),
                            isFinished = true,
                        ),
                )
            }
            val scheduler = WorkManagerSyncScheduler(manager)
            assertEquals(FullResyncScheduleResult.Busy, scheduler.requestFullResync())
            val after = manager.snapshotAfterCommit!!(manager.committedUniqueWork())
            assertFalse(manager.oneTimeEnqueues.single().request.id in after)
        }

    @Test
    fun keepCommit_isAtomic_loserNeverAppearsInUniqueSnapshot() =
        runBlocking {
            val manager = FakeSyncWorkManager(handoff = CompletableDeferred())
            val s1 = WorkManagerSyncScheduler(manager)
            val s2 = WorkManagerSyncScheduler(manager)

            val a = async { s1.requestFullResync() }
            manager.awaitEnqueueCount(1)
            val b = async { s2.requestFullResync() }
            manager.awaitEnqueueCount(2)

            manager.handoff.complete(Unit)
            a.await()
            b.await()

            val committedIds = manager.committedUniqueWork().keys
            assertEquals(1, committedIds.size)
            val loserIds =
                manager.oneTimeEnqueues.map { it.request.id }.filterNot { it in committedIds }
            assertEquals(1, loserIds.size)
            assertFalse(loserIds.single() in manager.committedUniqueWork())
        }

    // ── KEEP 感知 Fake ──

    /**
     * 模拟 WorkManager unique-work KEEP：
     * - enqueue 只记录意图；commit 时原子裁决
     * - 若已有未结束工作则丢弃本次 request（败者 ID 永不进入快照）
     * - 否则写入唯一快照
     */
    private class FakeSyncWorkManager(
        val handoff: CompletableDeferred<Unit> =
            CompletableDeferred<Unit>().also { it.complete(Unit) },
        private val enqueueFailure: Throwable? = null,
        private val snapshotFailure: Throwable? = null,
        private val snapshotFailureAfterCommit: Throwable? = null,
    ) : SyncWorkManager {
        val oneTimeEnqueues = CopyOnWriteArrayList<OneTimeEnqueue>()
        val commitCount = AtomicInteger(0)
        private val enqueueCount = AtomicInteger(0)
        private val commitLock = Any()
        private val committedWork = mutableMapOf<String, MutableMap<UUID, SyncWorkInfo>>()

        var preCommitSnapshot: (suspend () -> Map<UUID, SyncWorkInfo>)? = null
        var snapshotAfterCommit: ((Map<UUID, SyncWorkInfo>) -> Map<UUID, SyncWorkInfo>)? = null

        // 仅用于失败注入路径的调用序（单调度器 before/after）
        private val snapshotCallCount = AtomicInteger(0)

        fun committedUniqueWork(
            uniqueWorkName: String = MemosSyncWorker.UNIQUE_WORK_NAME,
        ): Map<UUID, SyncWorkInfo> =
            synchronized(commitLock) {
                committedWork[uniqueWorkName]?.toMap() ?: emptyMap()
            }

        fun seedCommitted(
            info: SyncWorkInfo,
            uniqueWorkName: String = MemosSyncWorker.UNIQUE_WORK_NAME,
        ) {
            synchronized(commitLock) {
                val db = committedWork.getOrPut(uniqueWorkName) { mutableMapOf() }
                db[info.id] = info
            }
        }

        override suspend fun uniqueWorkSnapshot(
            uniqueWorkName: String,
        ): Map<UUID, SyncWorkInfo> {
            val call = snapshotCallCount.incrementAndGet()
            val commits = commitCount.get()

            // 失败注入：首个 before / 任意 post-commit 快照
            if (commits == 0 && call == 1 && snapshotFailure != null) {
                throw snapshotFailure
            }
            if (commits > 0 && snapshotFailureAfterCommit != null) {
                throw snapshotFailureAfterCommit
            }

            val base =
                preCommitSnapshot?.invoke()
                    ?: committedUniqueWork(uniqueWorkName)

            // after 钩子：任意 commit 之后的快照均可改写（终态/竞争注入）
            if (commits > 0 && snapshotAfterCommit != null) {
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
            enqueueCount.incrementAndGet()
            return SyncEnqueueOperation {
                handoff.await()
                enqueueFailure?.let { throw it }
                applyKeepCommit(uniqueWorkName, policy, request)
                commitCount.incrementAndGet()
            }
        }

        override fun enqueueUniquePeriodicWork(
            uniqueWorkName: String,
            policy: ExistingPeriodicWorkPolicy,
            request: PeriodicWorkRequest,
        ) = Unit

        suspend fun awaitEnqueueCount(target: Int) {
            while (enqueueCount.get() < target) {
                yield()
            }
        }

        private fun applyKeepCommit(
            uniqueWorkName: String,
            policy: ExistingWorkPolicy,
            request: OneTimeWorkRequest,
        ) {
            synchronized(commitLock) {
                val db = committedWork.getOrPut(uniqueWorkName) { mutableMapOf() }
                when (policy) {
                    ExistingWorkPolicy.KEEP -> {
                        val hasUnfinished = db.values.any { !it.isFinished }
                        if (!hasUnfinished) {
                            // 无未结束工作则可插入；清理已结束条目以贴近唯一链语义
                            db.entries.removeAll { it.value.isFinished }
                            db[request.id] =
                                SyncWorkInfo(
                                    id = request.id,
                                    tags = request.tags,
                                    isFinished = false,
                                )
                        }
                        // 有未结束：KEEP 丢弃，request.id 不进入 db
                    }
                    ExistingWorkPolicy.REPLACE -> {
                        db.clear()
                        db[request.id] =
                            SyncWorkInfo(
                                id = request.id,
                                tags = request.tags,
                                isFinished = false,
                            )
                    }
                    else -> {
                        db[request.id] =
                            SyncWorkInfo(
                                id = request.id,
                                tags = request.tags,
                                isFinished = false,
                            )
                    }
                }
            }
        }
    }

    private data class OneTimeEnqueue(
        val policy: ExistingWorkPolicy,
        val request: OneTimeWorkRequest,
    )
}
