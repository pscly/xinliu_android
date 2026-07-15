package cc.pscly.onememos.di

import cc.pscly.onememos.diagnostics.AppIdentityPort
import cc.pscly.onememos.diagnostics.DiagnosticsExporter
import cc.pscly.onememos.diagnostics.DiagnosticsExporterImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DiagnosticsModule {
    @Binds
    @Singleton
    abstract fun bindDiagnosticsIdentity(adapter: DiagnosticsIdentityAdapter): AppIdentityPort

    @Binds
    @Singleton
    abstract fun bindDiagnosticsExporter(impl: DiagnosticsExporterImpl): DiagnosticsExporter
}
