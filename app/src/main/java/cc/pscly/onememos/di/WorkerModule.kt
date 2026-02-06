package cc.pscly.onememos.di

import cc.pscly.onememos.domain.sync.SyncScheduler
import cc.pscly.onememos.domain.sync.SyncStatusMonitor
import cc.pscly.onememos.domain.sync.TodoReminderScheduler
import cc.pscly.onememos.domain.sync.TodoReminderTestScheduler
import cc.pscly.onememos.domain.sync.TodoSyncScheduler
import cc.pscly.onememos.worker.WorkManagerTodoReminderScheduler
import cc.pscly.onememos.worker.WorkManagerTodoReminderTestScheduler
import cc.pscly.onememos.worker.WorkManagerTodoSyncScheduler
import cc.pscly.onememos.worker.WorkManagerSyncScheduler
import cc.pscly.onememos.worker.WorkManagerSyncStatusMonitor
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
    abstract fun bindSyncStatusMonitor(impl: WorkManagerSyncStatusMonitor): SyncStatusMonitor

    @Binds
    abstract fun bindTodoSyncScheduler(impl: WorkManagerTodoSyncScheduler): TodoSyncScheduler

    @Binds
    abstract fun bindTodoReminderScheduler(impl: WorkManagerTodoReminderScheduler): TodoReminderScheduler

    @Binds
    abstract fun bindTodoReminderTestScheduler(impl: WorkManagerTodoReminderTestScheduler): TodoReminderTestScheduler
}
