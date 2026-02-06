package cc.pscly.onememos.di

import cc.pscly.onememos.domain.sync.SyncScheduler
import cc.pscly.onememos.domain.sync.TodoSyncScheduler
import cc.pscly.onememos.worker.WorkManagerTodoSyncScheduler
import cc.pscly.onememos.worker.WorkManagerSyncScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkerModule {
    @Binds
    abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler

    @Binds
    abstract fun bindTodoSyncScheduler(impl: WorkManagerTodoSyncScheduler): TodoSyncScheduler
}
