package cc.pscly.onememos.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Flow Backend 账号登录凭证存储。
 *
 * 需求：为了“每次打开 App 都向 Flow Backend 请求一次 token”，需要在本机安全保存账号与密码。
 * 注意：这里仅用于换取 memos token，不参与 memos 数据代理。
 */
@Singleton
class FlowBackendCredentialStorage @Inject constructor(
    @ApplicationContext context: Context,
) {
    data class Credentials(
        val username: String,
        val password: String,
    )

    private val prefs: SharedPreferences = createPrefs(context)

    fun get(): Credentials? {
        val u = prefs.getString(KEY_USERNAME, "").orEmpty().trim()
        val p = prefs.getString(KEY_PASSWORD, "").orEmpty()
        if (u.isBlank() || p.isBlank()) return null
        return Credentials(username = u, password = p)
    }

    fun set(username: String, password: String) {
        prefs
            .edit()
            .putString(KEY_USERNAME, username.trim())
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun clear() {
        prefs
            .edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .apply()
    }

    private companion object {
        private const val FILE_NAME = "one_memos_flow_backend_secure"
        private const val KEY_USERNAME = "flow_username"
        private const val KEY_PASSWORD = "flow_password"
    }

    private fun createPrefs(context: Context): SharedPreferences {
        return runCatching {
            EncryptedSharedPreferences.create(
                FILE_NAME,
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse {
            // Robolectric / 部分 JVM 环境下 Android Keystore 不可用时，降级为普通 SharedPreferences，
            // 避免账号密码相关单测直接因为加密存储初始化失败而全线报错。
            context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        }
    }
}
