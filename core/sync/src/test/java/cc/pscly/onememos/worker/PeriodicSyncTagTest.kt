package cc.pscly.onememos.worker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归测试（1.17.5）：周期同步不能被算作“正在同步”。
 *
 * 真实缺陷：
 * - `WorkManagerSyncStatusMonitor` 之前用 `getWorkInfosByTagLiveData(MemosSyncWorker.TAG)`
 *   查询同步状态；
 * - WorkManager 会为每个 Worker 自动追加**类名 tag**
 *   （`cc.pscly.onememos.worker.MemosSyncWorker`），而周期同步复用同一个 Worker 类；
 * - 周期任务长期处于 ENQUEUED，于是 `computeWorkState` 永远返回 RUNNING/ENQUEUED，
 *   首页横幅一直显示“同步中…”，即使同步早已成功。
 */
class PeriodicSyncTagTest {
    @Test
    fun periodicMarkerTags_areDetected() {
        assertTrue(isPeriodicSyncTags(setOf(WorkManagerSyncScheduler.PERIODIC_TAG)))
        assertTrue(isPeriodicSyncTags(setOf(WorkManagerSyncScheduler.UNIQUE_PERIODIC_WORK_NAME)))
        assertTrue(isPeriodicSyncTags(setOf(MemosSyncWorker.PERIODIC_MARKER_TAG)))
    }

    @Test
    fun periodicWork_withClassTagAndPeriodicTags_isDetected() {
        // 真机上的周期任务 tag 组合：类名 tag + unique name tag + 周期标记
        val realTags =
            setOf(
                "cc.pscly.onememos.worker.MemosSyncWorker",
                WorkManagerSyncScheduler.UNIQUE_PERIODIC_WORK_NAME,
                MemosSyncWorker.PERIODIC_MARKER_TAG,
            )
        assertTrue(isPeriodicSyncTags(realTags))
    }

    @Test
    fun oneTimeSyncWork_isNotMistakenAsPeriodic() {
        // 一次性同步：类名 tag + one_memos_sync
        val oneTimeTags =
            setOf(
                "cc.pscly.onememos.worker.MemosSyncWorker",
                MemosSyncWorker.TAG,
            )
        assertFalse(
            "一次性同步不能被当成周期任务，否则“同步中”会提前消失",
            isPeriodicSyncTags(oneTimeTags),
        )
    }

    @Test
    fun fullResyncWork_isNotMistakenAsPeriodic() {
        val fullResyncTags =
            setOf(
                "cc.pscly.onememos.worker.MemosSyncWorker",
                MemosSyncWorker.TAG,
                MemosSyncWorker.FULL_RESYNC_TAG,
            )
        assertFalse(isPeriodicSyncTags(fullResyncTags))
    }
}
