package cc.pscly.onememos.worker

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import java.util.UUID

interface SyncWorkManager {
    suspend fun uniqueWorkSnapshot(
        uniqueWorkName: String,
    ): Map<UUID, SyncWorkInfo>

    fun enqueueUniqueWork(
        uniqueWorkName: String,
        policy: ExistingWorkPolicy,
        request: OneTimeWorkRequest,
    ): SyncEnqueueOperation

    fun enqueueUniquePeriodicWork(
        uniqueWorkName: String,
        policy: ExistingPeriodicWorkPolicy,
        request: PeriodicWorkRequest,
    )
}
