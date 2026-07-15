package cc.pscly.onememos.di

import cc.pscly.onememos.externalactions.InAppFallbackPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExternalActionsModule {
    @Binds
    @Singleton
    abstract fun bindInAppFallbackPort(adapter: InAppFallbackAdapter): InAppFallbackPort
}
