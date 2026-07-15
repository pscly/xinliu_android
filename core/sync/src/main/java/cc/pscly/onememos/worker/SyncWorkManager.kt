package cc.pscly.onememos.worker

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import java.util.UUID

interface SyncWorkManager {
    suspend fun unfinishedWork(uniqueWorkName: String): List<SyncWorkInfo>

    fun enqueueUniqueWork(
        uniqueWorkName: String,
        policy: ExistingWorkPolicy,
        request: OneTimeWorkRequest,
    ): SyncEnqueueOperation

    suspend fun containsWork(id: UUID): Boolean

    fun enqueueUniquePeriodicWork(
        uniqueWorkName: String,
        policy: ExistingPeriodicWorkPolicy,
        request: PeriodicWorkRequest,
    )
}
