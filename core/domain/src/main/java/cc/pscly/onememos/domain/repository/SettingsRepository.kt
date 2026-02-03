package cc.pscly.onememos.domain.repository

import cc.pscly.onememos.domain.model.AppSettings
import cc.pscly.onememos.domain.model.FullSyncStage
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setWelcomeCompleted(completed: Boolean)

    suspend fun setServerUrl(url: String)
    suspend fun setToken(token: String)
    suspend fun setLoginMode(mode: LoginMode)
    suspend fun setCurrentUserCreator(creator: String)
    suspend fun setDev2Unlocked(unlocked: Boolean)
    suspend fun setDev2ShowPublicWorkspaceMemos(enabled: Boolean)

    suspend fun setThemePalette(palette: ThemePalette)
    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDefaultVisibility(visibility: MemoVisibility)

    suspend fun setRegexSearchEnabled(enabled: Boolean)

    suspend fun setShowTagCountsInFilter(enabled: Boolean)

    suspend fun setQuickCaptureOverlayEnabled(enabled: Boolean)

    suspend fun setSealStampDurationMs(durationMs: Int)

    suspend fun setOfflineImagePrefetchEnabled(enabled: Boolean)

    suspend fun setOfflineImagePrefetchMaxMemos(count: Int)

    suspend fun setOfflineImagePrefetchMaxImages(count: Int)

    suspend fun setAttachmentCacheMaxMb(mb: Int)

    // 开发者选项：自动标签元数据行（如 __Atags）
    suspend fun setDevAutoTagLineKeywords(raw: String)

    suspend fun setDevShowAutoTagLineInHome(show: Boolean)

    suspend fun setDevShowAutoTagLineInView(show: Boolean)

    suspend fun setDevShowAutoTagLineInEdit(show: Boolean)

    // 开发者选项：主页富预览“粘住”上限（0=关闭；默认 500；仅 benchmark/release 生效）
    suspend fun setDevHomeRichPreviewStickyLimit(limit: Int)

    // ----------------------------
    // 全量同步（Full Sync）状态写入
    // ----------------------------
    suspend fun setFullSyncRunning(runId: String)

    suspend fun setFullSyncProgress(
        runId: String,
        stage: FullSyncStage,
        pagesFetched: Int,
        itemsFetched: Int,
    )

    suspend fun setFullSyncSuccess(
        runId: String,
        stage: FullSyncStage,
        pagesFetched: Int,
        itemsFetched: Int,
    )

    suspend fun setFullSyncFailed(
        runId: String,
        stage: FullSyncStage,
        pagesFetched: Int,
        itemsFetched: Int,
        error: String,
    )

    /**
     * 将全量同步标记为“已取消”。
     *
     * 注意：为兼容既有实现，这里提供默认实现，默认回退为 Failed（error = "已取消"），
     * 并保留 stage/pagesFetched/itemsFetched。
     */
    suspend fun setFullSyncCancelled(
        runId: String,
        stage: FullSyncStage,
        pagesFetched: Int,
        itemsFetched: Int,
    ) {
        setFullSyncFailed(
            runId = runId,
            stage = stage,
            pagesFetched = pagesFetched,
            itemsFetched = itemsFetched,
            error = "已取消",
        )
    }
}
