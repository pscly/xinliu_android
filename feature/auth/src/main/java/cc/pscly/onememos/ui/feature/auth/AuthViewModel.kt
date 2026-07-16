package cc.pscly.onememos.ui.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.pscly.onememos.core.network.FlowBackendApi
import cc.pscly.onememos.core.network.FlowAuthRequest
import cc.pscly.onememos.core.network.FlowAuthResponse
import cc.pscly.onememos.core.network.MemosCurrentUserResolver
import cc.pscly.onememos.core.network.MemosUrls
import cc.pscly.onememos.data.auth.FlowBackendCredentialStorage
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.sync.SyncScheduler
import cc.pscly.onememos.navigation.AuthKey
import cc.pscly.onememos.navigation.AuthMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthTab {
    BACKEND,
    CUSTOM,
}

enum class BackendForm {
    LOGIN,
    REGISTER,
}

data class AuthUiState(
    val tab: AuthTab = AuthTab.BACKEND,
    val backendForm: BackendForm = BackendForm.LOGIN,
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val dev2Unlocked: Boolean = false,
    val customServerUrl: String = "",
    val customToken: String = "",
    val passwordVisible: Boolean = false,
    val tokenVisible: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

sealed interface AuthEvent {
    data object Authed : AuthEvent
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val flowBackendApi: FlowBackendApi,
    private val currentUserResolver: MemosCurrentUserResolver,
    private val settingsRepository: SettingsRepository,
    private val syncScheduler: SyncScheduler,
    private val flowBackendCredentialStorage: FlowBackendCredentialStorage,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    private var initialized = false
    private var boundKey: AuthKey? = null
    private var pendingKey: AuthKey? = null

    fun bind(key: AuthKey) {
        if (boundKey == key) return
        boundKey = key
        val wantCustom = key.mode == AuthMode.CUSTOM_TOKEN
        if (initialized) {
            _uiState.update {
                it.copy(tab = if (wantCustom) AuthTab.CUSTOM else AuthTab.BACKEND, error = null)
            }
        } else {
            pendingKey = key
        }
    }

    init {
        viewModelScope.launch {
            val s = settingsRepository.settings.first()
            if (initialized) return@launch
            initialized = true
            val tab =
                if (pendingKey?.mode == AuthMode.CUSTOM_TOKEN) {
                    AuthTab.CUSTOM
                } else {
                    AuthTab.BACKEND
                }
            pendingKey = null
            _uiState.update {
                it.copy(
                    tab = tab,
                    dev2Unlocked = s.dev2Unlocked,
                    customServerUrl = s.serverUrl,
                    customToken = s.token,
                )
            }
        }
    }

    fun switchTab(tab: AuthTab) {
        _uiState.update { it.copy(tab = tab, error = null) }
    }

    fun switchBackendForm(form: BackendForm) {
        _uiState.update { it.copy(backendForm = form, error = null) }
    }

    fun updateUsername(v: String) {
        _uiState.update { it.copy(username = v.trim(), error = null) }
    }

    fun updatePassword(v: String) {
        _uiState.update { it.copy(password = v, error = null) }
    }

    fun updateConfirmPassword(v: String) {
        _uiState.update { it.copy(confirmPassword = v, error = null) }
    }

    fun updateCustomServerUrl(v: String) {
        _uiState.update { it.copy(customServerUrl = v, error = null) }
    }

    fun updateCustomToken(v: String) {
        _uiState.update { it.copy(customToken = v, error = null) }
    }

    fun togglePasswordVisible() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun toggleTokenVisible() {
        _uiState.update { it.copy(tokenVisible = !it.tokenVisible) }
    }

    fun submitBackend() {
        val state = _uiState.value
        if (state.loading) return

        val username = state.username.trim()
        val password = state.password
        val confirm = state.confirmPassword

        val usernameOk = username.matches(Regex("^[A-Za-z0-9]+$"))
        if (!usernameOk) {
            _uiState.update { it.copy(error = "用户名仅支持字母与数字（不支持下划线/短横线/中文）。") }
            return
        }
        val passwordBytes = password.toByteArray(Charsets.UTF_8).size
        if (password.length < 6) {
            _uiState.update { it.copy(error = "密码至少 6 位。") }
            return
        }
        if (passwordBytes > 72) {
            _uiState.update { it.copy(error = "密码过长（建议不超过 72 字节），请尽量使用英文/数字/常见符号。") }
            return
        }
        if (state.backendForm == BackendForm.REGISTER && password != confirm) {
            _uiState.update { it.copy(error = "两次输入的密码不一致。") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result =
                runCatching {
                    val body = FlowAuthRequest(username = username, password = password)
                    val resp =
                        if (state.backendForm == BackendForm.REGISTER) {
                            flowBackendApi.register(body)
                        } else {
                            flowBackendApi.login(body)
                        }
                    resp
                }.getOrNull()

            if (result == null) {
                _uiState.update { it.copy(loading = false, error = "网络异常，请稍后重试。") }
                return@launch
            }

            if (!result.isSuccessful) {
                _uiState.update { it.copy(loading = false, error = mapHttpError(result.code(), state.backendForm)) }
                return@launch
            }

            val payload = result.body()
            val (token, backendServerUrl) = extractAuthTokenAndServerUrl(payload)
            if (token.isBlank()) {
                _uiState.update { it.copy(loading = false, error = "服务返回异常：未返回 token。") }
                return@launch
            }

            val dev2Unlocked = settingsRepository.settings.first().dev2Unlocked
            val serverUrl =
                if (dev2Unlocked) {
                    backendServerUrl
                } else {
                    MemosUrls.DEFAULT_MEMOS_SERVER_URL
                }
            if (dev2Unlocked && serverUrl.isBlank()) {
                _uiState.update { it.copy(loading = false, error = "服务返回异常：未返回 server_url。") }
                return@launch
            }

            val currentUserCreator = resolveCurrentUserCreator(serverUrl, token)
            if (currentUserCreator.isNullOrBlank()) {
                _uiState.update { it.copy(loading = false, error = "无法验证当前账号，请检查服务器地址或 Token 后重试。") }
                return@launch
            }

            settingsRepository.setServerUrl(serverUrl)
            settingsRepository.setToken(token)
            settingsRepository.setLoginMode(LoginMode.BACKEND)
            settingsRepository.setWelcomeCompleted(true)
            settingsRepository.setCurrentUserCreator(currentUserCreator)
            syncScheduler.requestSync()

            // 用于“每次启动向 Flow Backend 换取 token”：安全保存账号密码。
            flowBackendCredentialStorage.set(username = username, password = password)

            _uiState.update { it.copy(loading = false, password = "", confirmPassword = "") }
            _events.tryEmit(AuthEvent.Authed)
        }
    }

    fun saveCustom() {
        val state = _uiState.value
        if (state.loading) return

        val token = state.customToken.trim()
        if (token.isBlank()) {
            _uiState.update { it.copy(error = "请填写 Token。") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val dev2Unlocked = settingsRepository.settings.first().dev2Unlocked
            val server =
                if (dev2Unlocked) {
                    state.customServerUrl.trim()
                } else {
                    MemosUrls.DEFAULT_MEMOS_SERVER_URL
                }
            if (dev2Unlocked && server.isBlank()) {
                _uiState.update { it.copy(loading = false, error = "请填写服务器地址。") }
                return@launch
            }

            val currentUserCreator = resolveCurrentUserCreator(server, token)
            if (currentUserCreator.isNullOrBlank()) {
                _uiState.update { it.copy(loading = false, error = "无法验证当前 Token，请检查服务器地址或 Token 后重试。") }
                return@launch
            }

            settingsRepository.setServerUrl(server)
            settingsRepository.setToken(token)
            settingsRepository.setLoginMode(LoginMode.CUSTOM)
            settingsRepository.setWelcomeCompleted(true)
            settingsRepository.setCurrentUserCreator(currentUserCreator)
            syncScheduler.requestSync()
            _uiState.update { it.copy(loading = false) }
            _events.tryEmit(AuthEvent.Authed)
        }
    }

    private fun extractAuthTokenAndServerUrl(payload: FlowAuthResponse?): Pair<String, String> {
        // 兼容两种形态：
        // 1) Envelope：{code,data:{token,server_url}}
        // 2) 扁平：{token,server_url}
        val token = payload?.data?.token?.trim().orEmpty().ifBlank { payload?.token?.trim().orEmpty() }
        val serverUrl = payload?.data?.serverUrl?.trim().orEmpty().ifBlank { payload?.serverUrl?.trim().orEmpty() }
        return token to serverUrl
    }

    private suspend fun resolveCurrentUserCreator(
        serverUrl: String,
        bearerToken: String,
    ): String? {
        val serverBase = MemosUrls.normalizeServerBase(serverUrl) ?: return null
        return currentUserResolver.resolve(serverBase, bearerToken = bearerToken)
    }

    private fun mapHttpError(status: Int, form: BackendForm): String {
        return when (status) {
            400 -> "请检查用户名/密码格式。"
            401 -> "用户名或密码错误。"
            403 -> "账号已被禁用，请联系管理员。"
            409 -> if (form == BackendForm.REGISTER) "用户名已存在，请直接登录。" else "请求冲突，请稍后重试。"
            502 -> "服务暂不可用，请稍后重试。"
            else -> "请求失败（HTTP $status），请稍后重试。"
        }
    }
}
