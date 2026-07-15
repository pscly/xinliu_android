package cc.pscly.onememos.worker

fun interface SyncEnqueueOperation {
    suspend fun commit()
}
