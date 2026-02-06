package cc.pscly.onememos.data.auth

import cc.pscly.onememos.core.network.FlowAuthRequest
import cc.pscly.onememos.core.network.FlowBackendApi
import cc.pscly.onememos.core.network.MemosUrls
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 在 App 前台可见时，向 Flow Backend 换取最新 token。
 *
 * 约束：
 * - 仅当登录方式为 BACKEND 且本机已保存账号密码时才会请求。
 * - 失败不抛异常、不阻塞 UI。
 * - 并发保护：同一时刻只允许一个刷新在飞行中。
 */
@Singleton
class FlowBackendTokenRefresher @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val flowBackendApi: FlowBackendApi,
    private val flowBackendCredentialStorage: FlowBackendCredentialStorage,
) {
    private val mutex = Mutex()

    suspend fun refreshIfPossible() {
        mutex.withLock {
            val settings = settingsRepository.settings.first()
            if (settings.loginMode != LoginMode.BACKEND) return
            val cred = flowBackendCredentialStorage.get() ?: return

            val resp =
                runCatching {
                    flowBackendApi.login(
                        FlowAuthRequest(
                            username = cred.username,
                            password = cred.password,
                        ),
                    )
                }.getOrNull() ?: return

            if (!resp.isSuccessful) return
            val payload = resp.body()
            val token = payload?.data?.token?.trim().orEmpty().ifBlank { payload?.token?.trim().orEmpty() }
            val backendServerUrl =
                payload?.data?.serverUrl?.trim().orEmpty().ifBlank { payload?.serverUrl?.trim().orEmpty() }
            if (token.isBlank()) return

            // 普通用户无感：仍强制使用默认 memos 服务器；仅 dev2 解锁才允许使用后端返回的 server_url。
            val serverUrl =
                if (settings.dev2Unlocked) {
                    backendServerUrl
                } else {
                    MemosUrls.DEFAULT_MEMOS_SERVER_URL
                }
            if (settings.dev2Unlocked && serverUrl.isBlank()) return

            settingsRepository.setServerUrl(serverUrl)
            settingsRepository.setToken(token)
        }
    }
}
