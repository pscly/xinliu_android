package cc.pscly.onememos.di

import cc.pscly.onememos.domain.settings.AboutAdvancedSettingsCapability
import cc.pscly.onememos.domain.settings.AccountSyncSettingsCapability
import cc.pscly.onememos.domain.settings.AppearanceInteractionSettingsCapability
import cc.pscly.onememos.domain.settings.RecordEditingSettingsCapability
import cc.pscly.onememos.domain.settings.ReminderCalendarSettingsCapability
import cc.pscly.onememos.domain.settings.SettingsHubCapability
import cc.pscly.onememos.domain.settings.StorageOfflineSettingsCapability
import cc.pscly.onememos.settings.SettingsHubCapabilityImpl
import cc.pscly.onememos.settings.about.AboutAdvancedSettingsCapabilityImpl
import cc.pscly.onememos.settings.account.AccountSyncSettingsCapabilityImpl
import cc.pscly.onememos.settings.appearance.AppearanceInteractionSettingsCapabilityImpl
import cc.pscly.onememos.settings.record.RecordEditingSettingsCapabilityImpl
import cc.pscly.onememos.settings.reminder.ReminderCalendarSettingsCapabilityImpl
import cc.pscly.onememos.settings.storage.StorageOfflineSettingsCapabilityImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * app 组合根唯一装配七个设置能力接口。
 * 不在此模块重复提供更新/身份/日历等依赖，复用既有 AppUpdateModule 等绑定。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsCapabilityModule {
    @Binds
    @Singleton
    abstract fun bindSettingsHubCapability(impl: SettingsHubCapabilityImpl): SettingsHubCapability

    @Binds
    @Singleton
    abstract fun bindAccountSyncSettingsCapability(
        impl: AccountSyncSettingsCapabilityImpl,
    ): AccountSyncSettingsCapability

    @Binds
    @Singleton
    abstract fun bindRecordEditingSettingsCapability(
        impl: RecordEditingSettingsCapabilityImpl,
    ): RecordEditingSettingsCapability

    @Binds
    @Singleton
    abstract fun bindReminderCalendarSettingsCapability(
        impl: ReminderCalendarSettingsCapabilityImpl,
    ): ReminderCalendarSettingsCapability

    @Binds
    @Singleton
    abstract fun bindStorageOfflineSettingsCapability(
        impl: StorageOfflineSettingsCapabilityImpl,
    ): StorageOfflineSettingsCapability

    @Binds
    @Singleton
    abstract fun bindAppearanceInteractionSettingsCapability(
        impl: AppearanceInteractionSettingsCapabilityImpl,
    ): AppearanceInteractionSettingsCapability

    @Binds
    @Singleton
    abstract fun bindAboutAdvancedSettingsCapability(
        impl: AboutAdvancedSettingsCapabilityImpl,
    ): AboutAdvancedSettingsCapability
}
