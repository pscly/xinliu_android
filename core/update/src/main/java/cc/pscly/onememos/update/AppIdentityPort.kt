package cc.pscly.onememos.update

interface AppIdentityPort {
    val applicationId: String
    val versionName: String
    val versionCode: Long
    val fileProviderAuthority: String
}
