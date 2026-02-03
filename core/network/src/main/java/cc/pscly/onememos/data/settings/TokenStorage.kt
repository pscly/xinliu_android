package cc.pscly.onememos.data.settings

/**
 * token 存储抽象：用于让 SettingsRepositoryImpl 在 JVM 单元测试中可替换为内存实现。
 */
interface TokenStorage {
    fun getToken(): String

    fun setToken(token: String)
}
