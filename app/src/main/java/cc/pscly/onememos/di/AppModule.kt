package cc.pscly.onememos.di

import android.content.Context
import androidx.room.Room
import cc.pscly.onememos.core.database.OneMemosDatabase
import cc.pscly.onememos.core.database.dao.MemoDao
import cc.pscly.onememos.core.database.dao.TodoDao
import cc.pscly.onememos.core.database.dao.TodoSyncDao
import cc.pscly.onememos.data.cache.CacheRepositoryImpl
import cc.pscly.onememos.data.auth.FlowBackendCredentialStorage
import cc.pscly.onememos.data.repository.MemoRepositoryImpl
import cc.pscly.onememos.data.repository.TodoRepositoryImpl
import cc.pscly.onememos.data.settings.EncryptedTokenStorage
import cc.pscly.onememos.data.settings.SettingsRepositoryImpl
import cc.pscly.onememos.data.settings.TokenStorage
import cc.pscly.onememos.domain.repository.CacheRepository
import cc.pscly.onememos.domain.repository.MemoRepository
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.repository.TodoRepository
import cc.pscly.onememos.domain.sync.SyncScheduler
import cc.pscly.onememos.domain.sync.TodoSyncScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): OneMemosDatabase =
        Room.databaseBuilder(context, OneMemosDatabase::class.java, "one_memos.db")
            .addMigrations(
                OneMemosDatabase.MIGRATION_3_4,
                OneMemosDatabase.MIGRATION_4_5,
                OneMemosDatabase.MIGRATION_5_6,
                OneMemosDatabase.MIGRATION_6_7,
                OneMemosDatabase.MIGRATION_7_8,
                OneMemosDatabase.MIGRATION_8_9,
                OneMemosDatabase.MIGRATION_9_10,
            )
            .build()

    @Provides
    fun provideMemoDao(db: OneMemosDatabase): MemoDao = db.memoDao()

    @Provides
    fun provideTodoDao(db: OneMemosDatabase): TodoDao = db.todoDao()

    @Provides
    fun provideTodoSyncDao(db: OneMemosDatabase): TodoSyncDao = db.todoSyncDao()

    @Provides
    @Singleton
    fun provideMemoRepository(
        memoDao: MemoDao,
        settingsRepository: SettingsRepository,
        syncScheduler: SyncScheduler,
    ): MemoRepository =
        MemoRepositoryImpl(memoDao, settingsRepository, syncScheduler)

    @Provides
    @Singleton
    fun provideTodoRepository(
        todoDao: TodoDao,
        todoSyncDao: TodoSyncDao,
        settingsRepository: SettingsRepository,
        todoSyncScheduler: TodoSyncScheduler,
        flowBackendCredentialStorage: FlowBackendCredentialStorage,
    ): TodoRepository =
        TodoRepositoryImpl(
            todoDao = todoDao,
            todoSyncDao = todoSyncDao,
            settingsRepository = settingsRepository,
            syncScheduler = todoSyncScheduler,
            flowBackendCredentialStorage = flowBackendCredentialStorage,
        )

    @Provides
    @Singleton
    fun provideTokenStorage(impl: EncryptedTokenStorage): TokenStorage = impl

    @Provides
    @Singleton
    fun provideSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository = impl

    @Provides
    @Singleton
    fun provideCacheRepository(impl: CacheRepositoryImpl): CacheRepository = impl
}
