package cc.pscly.onememos.core.network

import cc.pscly.onememos.data.settings.TokenStorage
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthorizationInterceptor @Inject constructor(
    private val appSettingsState: AppSettingsState,
    private val encryptedTokenStorage: TokenStorage,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var token = appSettingsState.snapshot().token.trim()
        if (token.isBlank()) {
            // 避免“刚保存 token 立即同步时，内存快照还没来得及刷新”导致的一次性 401。
            // 这里直接读加密存储，避免阻塞 OkHttp 线程（runBlocking 会拖慢网络/图片请求）。
            token = encryptedTokenStorage.getToken().trim()
        }
        if (token.isBlank()) {
            return chain.proceed(chain.request())
        }

        val request =
            chain.request()
                .newBuilder()
                .header("Authorization", "Bearer $token")
                .build()

        return chain.proceed(request)
    }
}
