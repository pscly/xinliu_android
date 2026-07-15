package cc.pscly.onememos.di

import cc.pscly.onememos.calendar.SystemCalendarGateway
import cc.pscly.onememos.calendar.SystemCalendarGatewayImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarCapabilityModule {
    @Binds
    @Singleton
    abstract fun bindSystemCalendarGateway(impl: SystemCalendarGatewayImpl): SystemCalendarGateway
}
