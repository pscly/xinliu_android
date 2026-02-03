package cc.pscly.onememos.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object MemoDerivedFieldsRebuildScheduler {
    fun enqueue(
        context: Context,
        initialDelaySeconds: Long = 60,
        existingWorkPolicy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
    ) {
        val constraints =
            Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

        val request =
            OneTimeWorkRequestBuilder<MemoDerivedFieldsRebuildWorker>()
                .setConstraints(constraints)
                .setInitialDelay(initialDelaySeconds.coerceAtLeast(0), TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.SECONDS)
                .addTag(MemoDerivedFieldsRebuildWorker.TAG)
                .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            MemoDerivedFieldsRebuildWorker.UNIQUE_WORK_NAME,
            existingWorkPolicy,
            request,
        )
    }
}
