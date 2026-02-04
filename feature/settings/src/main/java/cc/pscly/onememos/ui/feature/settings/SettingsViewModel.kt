package cc.pscly.onememos.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.pscly.onememos.domain.model.CacheStats
import cc.pscly.onememos.domain.model.FullSyncStage
import cc.pscly.onememos.domain.model.FullSyncStatus
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.domain.repository.CacheRepository
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.sync.SyncScheduler
import cc.pscly.onememos.core.network.ChangePasswordRequest
import cc.pscly.onememos.core.network.FlowBackendApi
import cc.pscly.onememos.data.auth.FlowBackendCredentialStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val token: String = "",
    val loginMode: LoginMode = LoginMode.UNKNOWN,
    val dev2Unlocked: Boolean = false,
    val dev2ShowPublicWorkspaceMemos: Boolean = false,
    val themePalette: ThemePalette = ThemePalette.PAPER_INK,
    val themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    val defaultVisibility: MemoVisibility = MemoVisibility.PRIVATE,
    val regexSearchEnabled: Boolean = false,
    val showTagCountsInFilter: Boolean = true,
    val quickCaptureOverlayEnabled: Boolean = false,
    val sealStampDurationMs: Int = 600,
    val offlineImagePrefetchEnabled: Boolean = true,
    val offlineImagePrefetchMaxMemos: Int = 30,
    val offlineImagePrefetchMaxImages: Int = 60,
    val attachmentCacheMaxMb: Int = 1024,

    // 全量同步（Full Sync）状态
    val fullSyncStatus: FullSyncStatus = FullSyncStatus.IDLE,
    val fullSyncRunId: String = "",
    val fullSyncLastSuccessAt: Long = 0L,
    val fullSyncLastError: String = "",
    val fullSyncStage: FullSyncStage = FullSyncStage.NORMAL,
    val fullSyncPagesFetched: Int = 0,
    val fullSyncItemsFetched: Int = 0,
    val fullSyncKey: String = "",

    val devAutoTagLineKeywords: String = "__Atags",
    val devShowAutoTagLineInHome: Boolean = false,
    val devShowAutoTagLineInView: Boolean = false,
    val devShowAutoTagLineInEdit: Boolean = false,
    val devHomeRichPreviewStickyLimit: Int = 500,
    val cacheStats: CacheStats? = null,
    val cacheLoading: Boolean = false,
    val cacheClearing: Boolean = false,
    val cacheError: String? = null,
)

data class ChangePasswordUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val successAt: Long = 0L,
)

private data class CacheSectionState(
    val stats: CacheStats? = null,
    val loading: Boolean = false,
    val clearing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val cacheRepository: CacheRepository,
    private val syncScheduler: SyncScheduler,
    private val flowBackendApi: FlowBackendApi,
    private val flowBackendCredentialStorage: FlowBackendCredentialStorage,
) : ViewModel() {
    private val cacheState = MutableStateFlow(CacheSectionState())
    private val changePasswordState = MutableStateFlow(ChangePasswordUiState())

    val uiState: StateFlow<SettingsUiState> =
        combine(settingsRepository.settings, cacheState) { s, c ->
            SettingsUiState(
                serverUrl = s.serverUrl,
                token = s.token,
                loginMode = s.loginMode,
                dev2Unlocked = s.dev2Unlocked,
                dev2ShowPublicWorkspaceMemos = s.dev2ShowPublicWorkspaceMemos,
                themePalette = s.themePalette,
                themeMode = s.themeMode,
                defaultVisibility = s.defaultVisibility,
                regexSearchEnabled = s.regexSearchEnabled,
                showTagCountsInFilter = s.showTagCountsInFilter,
                quickCaptureOverlayEnabled = s.quickCaptureOverlayEnabled,
                sealStampDurationMs = s.sealStampDurationMs,
                offlineImagePrefetchEnabled = s.offlineImagePrefetchEnabled,
                offlineImagePrefetchMaxMemos = s.offlineImagePrefetchMaxMemos,
                offlineImagePrefetchMaxImages = s.offlineImagePrefetchMaxImages,
                attachmentCacheMaxMb = s.attachmentCacheMaxMb,

                fullSyncStatus = s.fullSync.status,
                fullSyncRunId = s.fullSync.runId,
                fullSyncLastSuccessAt = s.fullSync.lastSuccessAt,
                fullSyncLastError = s.fullSync.lastError,
                fullSyncStage = s.fullSync.stage,
                fullSyncPagesFetched = s.fullSync.pagesFetched,
                fullSyncItemsFetched = s.fullSync.itemsFetched,
                fullSyncKey = s.fullSync.syncKey,

                devAutoTagLineKeywords = s.devAutoTagLineKeywords,
                devShowAutoTagLineInHome = s.devShowAutoTagLineInHome,
                devShowAutoTagLineInView = s.devShowAutoTagLineInView,
                devShowAutoTagLineInEdit = s.devShowAutoTagLineInEdit,
                devHomeRichPreviewStickyLimit = s.devHomeRichPreviewStickyLimit,
                cacheStats = c.stats,
                cacheLoading = c.loading,
                cacheClearing = c.clearing,
                cacheError = c.error,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsUiState(),
            )

    val changePasswordUiState: StateFlow<ChangePasswordUiState> = changePasswordState

    init {
        refreshCacheStats()

        // 当用户“完成绑定”（serverUrl + token 都有效）时，自动触发一次同步：
        // - 上传离线期间创建的本地记录
        // - 拉取服务端历史记录并合并
        viewModelScope.launch {
            settingsRepository.settings
                .map { s -> s.serverUrl.isNotBlank() && s.token.isNotBlank() }
                .distinctUntilChanged()
                .drop(1)
                .collect { canSync ->
                    if (canSync) {
                        syncScheduler.requestSync()
                    }
                }
        }
    }

    fun updateServerUrl(url: String) {
        viewModelScope.launch { settingsRepository.setServerUrl(url) }
    }

    fun updateToken(token: String) {
        viewModelScope.launch { settingsRepository.setToken(token) }
    }

    fun logout(clearServerUrl: Boolean = false) {
        viewModelScope.launch {
            settingsRepository.setToken("")
            settingsRepository.setLoginMode(LoginMode.UNKNOWN)
            settingsRepository.setCurrentUserCreator("")
            // 账号登录模式下也清理本机保存的账号密码，避免下次启动仍自动换取 token。
            flowBackendCredentialStorage.clear()
            if (clearServerUrl) {
                settingsRepository.setServerUrl("")
            }
        }
    }

    fun resetChangePasswordState() {
        changePasswordState.value = ChangePasswordUiState()
    }

    fun changePassword(current: String, new1: String, new2: String) {
        if (changePasswordState.value.loading) return

        if (current.isBlank()) {
            changePasswordState.value = ChangePasswordUiState(error = "请输入当前密码。")
            return
        }
        if (new1.length < 6) {
            changePasswordState.value = ChangePasswordUiState(error = "新密码至少 6 位。")
            return
        }
        val newBytes = new1.toByteArray(Charsets.UTF_8).size
        if (newBytes > 71) {
            changePasswordState.value = ChangePasswordUiState(error = "新密码过长（UTF-8 不超过 71 字节）。")
            return
        }
        if (new1 != new2) {
            changePasswordState.value = ChangePasswordUiState(error = "两次输入的新密码不一致。")
            return
        }

        viewModelScope.launch {
            changePasswordState.value = ChangePasswordUiState(loading = true)

            val settings = settingsRepository.settings.first()
            val token = settings.token.trim()
            if (token.isBlank()) {
                changePasswordState.value = ChangePasswordUiState(error = "登录已失效，请重新登录。")
                return@launch
            }

            val resp =
                runCatching {
                    flowBackendApi.changePassword(
                        token = "Bearer $token",
                        body = ChangePasswordRequest(
                            currentPassword = current,
                            newPassword = new1,
                            newPassword2 = new2,
                        ),
                    )
                }.getOrNull()

            if (resp == null) {
                changePasswordState.value = ChangePasswordUiState(error = "网络异常，请稍后重试。")
                return@launch
            }
            if (!resp.isSuccessful) {
                changePasswordState.value = ChangePasswordUiState(error = mapChangePasswordHttpError(resp.code()))
                return@launch
            }

            val payload = resp.body()
            val ok = payload?.code == 200 && payload.data?.ok == true
            if (!ok) {
                changePasswordState.value = ChangePasswordUiState(error = "请求失败，请稍后重试。")
                return@launch
            }

            if (settings.loginMode == LoginMode.BACKEND) {
                val cred = flowBackendCredentialStorage.get()
                if (cred != null) {
                    flowBackendCredentialStorage.set(username = cred.username, password = new1)
                }
            }
            changePasswordState.value = ChangePasswordUiState(successAt = System.currentTimeMillis())
        }
    }

    private fun mapChangePasswordHttpError(status: Int): String {
        return when (status) {
            400 -> "新密码不一致或格式不正确。"
            401 -> "当前密码不正确。"
            403 -> "鉴权失败，请重新登录。"
            409 -> "账号未配置 Memos 信息，请联系管理员。"
            502 -> "同步 Memos 失败，请稍后再试。"
            else -> "请求失败（HTTP $status），请稍后重试。"
        }
    }

    fun updateDev2Unlocked(unlocked: Boolean) {
        viewModelScope.launch { settingsRepository.setDev2Unlocked(unlocked) }
    }

    fun updateDev2ShowPublicWorkspaceMemos(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDev2ShowPublicWorkspaceMemos(enabled) }
    }

    fun requestSyncNow() {
        syncScheduler.requestSync()
    }

    /**
     * 请求一次“重新同步所有笔记”（全量重同步）。
     *
     * 说明：该请求会以 REPLACE 覆盖当前正在执行/排队的同名同步任务，并强制触发 full sync。
     */
    fun requestFullResync() {
        syncScheduler.requestFullResync()
    }

    fun updateThemePalette(palette: ThemePalette) {
        viewModelScope.launch { settingsRepository.setThemePalette(palette) }
    }

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun updateDefaultVisibility(visibility: MemoVisibility) {
        viewModelScope.launch { settingsRepository.setDefaultVisibility(visibility) }
    }

    fun updateRegexSearchEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setRegexSearchEnabled(enabled) }
    }

    fun updateShowTagCountsInFilter(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowTagCountsInFilter(enabled) }
    }

    fun updateQuickCaptureOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setQuickCaptureOverlayEnabled(enabled) }
    }

    fun updateSealStampDurationMs(durationMs: Int) {
        viewModelScope.launch { settingsRepository.setSealStampDurationMs(durationMs) }
    }

    fun updateOfflineImagePrefetchEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setOfflineImagePrefetchEnabled(enabled) }
    }

    fun updateOfflineImagePrefetchMaxMemos(count: Int) {
        viewModelScope.launch { settingsRepository.setOfflineImagePrefetchMaxMemos(count) }
    }

    fun updateOfflineImagePrefetchMaxImages(count: Int) {
        viewModelScope.launch { settingsRepository.setOfflineImagePrefetchMaxImages(count) }
    }

    fun updateAttachmentCacheMaxMb(mb: Int) {
        viewModelScope.launch { settingsRepository.setAttachmentCacheMaxMb(mb) }
    }

    fun updateDevAutoTagLineKeywords(raw: String) {
        viewModelScope.launch { settingsRepository.setDevAutoTagLineKeywords(raw) }
    }

    fun updateDevShowAutoTagLineInHome(show: Boolean) {
        viewModelScope.launch { settingsRepository.setDevShowAutoTagLineInHome(show) }
    }

    fun updateDevShowAutoTagLineInView(show: Boolean) {
        viewModelScope.launch { settingsRepository.setDevShowAutoTagLineInView(show) }
    }

    fun updateDevShowAutoTagLineInEdit(show: Boolean) {
        viewModelScope.launch { settingsRepository.setDevShowAutoTagLineInEdit(show) }
    }

    fun updateDevHomeRichPreviewStickyLimit(limit: Int) {
        viewModelScope.launch { settingsRepository.setDevHomeRichPreviewStickyLimit(limit) }
    }

    fun refreshCacheStats() {
        viewModelScope.launch {
            cacheState.value = cacheState.value.copy(loading = true, error = null)
            val stats = runCatching { cacheRepository.getCacheStats() }.getOrNull()
            if (stats == null) {
                cacheState.value = cacheState.value.copy(loading = false, error = "缓存统计失败")
            } else {
                cacheState.value = cacheState.value.copy(loading = false, stats = stats, error = null)
            }
        }
    }

    fun clearImageCache() {
        viewModelScope.launch {
            cacheState.value = cacheState.value.copy(clearing = true, error = null)
            val ok = runCatching { cacheRepository.clearImageCache() }.isSuccess
            cacheState.value = cacheState.value.copy(clearing = false, error = if (ok) null else "清理图片缓存失败")
            refreshCacheStats()
        }
    }

    fun clearAttachmentCache() {
        viewModelScope.launch {
            cacheState.value = cacheState.value.copy(clearing = true, error = null)
            val ok = runCatching { cacheRepository.clearAttachmentCache() }.isSuccess
            cacheState.value = cacheState.value.copy(clearing = false, error = if (ok) null else "清理附件缓存失败")
            refreshCacheStats()
        }
    }

    fun clearAllCache() {
        viewModelScope.launch {
            cacheState.value = cacheState.value.copy(clearing = true, error = null)
            val ok = runCatching { cacheRepository.clearAllCache() }.isSuccess
            cacheState.value = cacheState.value.copy(clearing = false, error = if (ok) null else "清理缓存失败")
            refreshCacheStats()
        }
    }
}
