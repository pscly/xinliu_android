package cc.pscly.onememos.di

import cc.pscly.onememos.data.paging.MemoPagingDataSource
import cc.pscly.onememos.data.paging.RoomMemoPagingDataSource
import cc.pscly.onememos.ui.feature.home.HomeMemoPagingSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface HomePagingModule {
    @Binds
    @Singleton
    fun bindMemoPagingDataSource(impl: RoomMemoPagingDataSource): MemoPagingDataSource

    @Binds
    @Singleton
    fun bindHomeMemoPagingSource(adapter: HomeMemoPagingSourceAdapter): HomeMemoPagingSource
}
