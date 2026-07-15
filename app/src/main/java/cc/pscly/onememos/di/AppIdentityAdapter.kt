package cc.pscly.onememos.di

import cc.pscly.onememos.BuildConfig
import cc.pscly.onememos.update.AppIdentityPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppIdentityAdapter @Inject constructor() : AppIdentityPort {
    override val applicationId: String = BuildConfig.APPLICATION_ID
    override val versionName: String = BuildConfig.VERSION_NAME
    override val versionCode: Long = BuildConfig.VERSION_CODE.toLong()
    override val fileProviderAuthority: String = "${BuildConfig.APPLICATION_ID}.fileprovider"
}
