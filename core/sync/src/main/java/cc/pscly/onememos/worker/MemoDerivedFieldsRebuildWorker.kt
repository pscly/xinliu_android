package cc.pscly.onememos.worker

import android.content.Context
import android.os.SystemClock
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cc.pscly.onememos.core.database.dao.MemoDao
import cc.pscly.onememos.domain.derived.MemoDerivedFieldsDeriver
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class MemoDerivedFieldsRebuildWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val memoDao: MemoDao,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val targetVersion = MemoDerivedFieldsDeriver.CURRENT_VERSION
        val start = SystemClock.elapsedRealtime()

        var processed = 0
        while (SystemClock.elapsedRealtime() - start < MAX_RUN_MS && processed < MAX_ITEMS_PER_RUN) {
            val batch = memoDao.listMemosWithOutdatedDerivedFields(targetVersion = targetVersion, limit = BATCH_SIZE)
            if (batch.isEmpty()) break

            for (memo in batch) {
                val derived = MemoDerivedFieldsDeriver.derive(content = memo.content)
                memoDao.updateDerivedFields(
                    localId = memo.localId,
                    plainPreview = derived.plainPreview,
                    tagsText = derived.tagsText,
                    derivedVersion = derived.derivedVersion,
                    derivedAt = derived.derivedAt,
                )
                processed++
                if (SystemClock.elapsedRealtime() - start >= MAX_RUN_MS || processed >= MAX_ITEMS_PER_RUN) {
                    break
                }
            }
        }

        val remaining = memoDao.countMemosWithOutdatedDerivedFields(targetVersion = targetVersion)
        return if (remaining > 0) Result.retry() else Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME: String = "one_memos_rebuild_memo_derived_fields"
        const val TAG: String = "one_memos_rebuild_memo_derived_fields"

        private const val BATCH_SIZE: Int = 50
        private const val MAX_ITEMS_PER_RUN: Int = 300
        private const val MAX_RUN_MS: Long = 6_000L
    }
}
