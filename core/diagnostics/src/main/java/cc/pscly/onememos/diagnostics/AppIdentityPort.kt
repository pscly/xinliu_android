package cc.pscly.onememos.diagnostics

interface AppIdentityPort {
    val applicationId: String
    val versionName: String
    val versionCode: Long
    val buildType: String
    val flowBackendBaseUrl: String
    val fileProviderAuthority: String
}
