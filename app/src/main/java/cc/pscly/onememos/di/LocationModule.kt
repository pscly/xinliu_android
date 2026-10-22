package cc.pscly.onememos.di

import cc.pscly.onememos.domain.location.MemoLocationProvider
import cc.pscly.onememos.location.AndroidMemoLocationProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 定位能力绑定：domain 只依赖 [MemoLocationProvider] 接口，实现留在 app 模块。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {
    @Binds
    @Singleton
    abstract fun bindMemoLocationProvider(impl: AndroidMemoLocationProvider): MemoLocationProvider
}
