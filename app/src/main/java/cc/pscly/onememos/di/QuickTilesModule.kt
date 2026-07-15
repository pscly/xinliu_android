package cc.pscly.onememos.di

import cc.pscly.onememos.quicktiles.AndroidOverlayPermissionGateway
import cc.pscly.onememos.quicktiles.OverlayPermissionGateway
import cc.pscly.onememos.quicktiles.QuickCaptureTargetPort
import cc.pscly.onememos.quicktiles.ScreenshotEntryPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class QuickTilesModule {
    @Binds
    @Singleton
    abstract fun bindQuickCaptureTargetPort(adapter: QuickCaptureTargetAdapter): QuickCaptureTargetPort

    @Binds
    @Singleton
    abstract fun bindScreenshotEntryPort(adapter: ScreenshotEntryAdapter): ScreenshotEntryPort

    @Binds
    @Singleton
    abstract fun bindOverlayPermissionGateway(impl: AndroidOverlayPermissionGateway): OverlayPermissionGateway
}
