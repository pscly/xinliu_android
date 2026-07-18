package cc.pscly.onememos.domain.repository

import cc.pscly.onememos.domain.model.AppSettings
import cc.pscly.onememos.domain.model.FullSyncStage
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.QuickInsertTimeFormat
import cc.pscly.onememos.domain.model.ReadingFontScale
import cc.pscly.onememos.domain.model.ReadingLineHeight
import cc.pscly.onememos.domain.model.ThemeDescriptor
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.domain.model.TodoReminderMode
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

    /**
     * 写入完整主题描述符（出厂预设一键切换、高级轴调节）。
     * 会清除旧 `theme_palette` 键。
     */
    suspend fun setThemeDescriptor(descriptor: ThemeDescriptor)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDefaultVisibility(visibility: MemoVisibility)

    suspend fun setRegexSearchEnabled(enabled: Boolean)

    suspend fun setShowTagCountsInFilter(enabled: Boolean)

    suspend fun setQuickCaptureOverlayEnabled(enabled: Boolean)

    suspend fun setQuickInsertTimeEnabled(enabled: Boolean)

    suspend fun setQuickInsertTimeFormat(format: QuickInsertTimeFormat)

    suspend fun setSealStampDurationMs(durationMs: Int)

    /**
     * 阅读字号档（M3.3）。
     * 默认空实现：既有测试 Fake 无需逐一覆写；正式实现见 SettingsRepositoryImpl。
     */
    suspend fun setReadingFontScale(scale: ReadingFontScale) = Unit

    /**
     * 阅读行距档（M3.3）。
     * 默认空实现：既有测试 Fake 无需逐一覆写；正式实现见 SettingsRepositoryImpl。
     */
    suspend fun setReadingLineHeight(lineHeight: ReadingLineHeight) = Unit

    suspend fun setOfflineImagePrefetchEnabled(enabled: Boolean)

    suspend fun setOfflineImagePrefetchMaxMemos(count: Int)

    suspend fun setOfflineImagePrefetchMaxImages(count: Int)

    suspend fun setAttachmentCacheMaxMb(mb: Int)

    suspend fun setAttachmentUploadMaxMb(mb: Int)

    // ----------------------------
    // Todo（待办）提醒
    // ----------------------------
    suspend fun setTodoReminderMode(mode: TodoReminderMode)

    // ----------------------------
    // 日历联动：Todo -> 系统日历
    // ----------------------------
    suspend fun setCalendarIntegrationEnabled(enabled: Boolean)

    /**
     * 设置要写入的目标日历 id（CalendarContract.Calendars._ID）。
     *
     * @param calendarId null 表示清空选择。
     */
    suspend fun setCalendarIntegrationCalendarId(calendarId: Long?)

    /**
     * 是否把 remindersJson（提前 X 分钟）同步到日历提醒（CalendarContract.Reminders）。
     */
    suspend fun setCalendarIntegrationSyncReminders(enabled: Boolean)

    // ----------------------------
    // 同步（轻量状态）
    // ----------------------------
    /**
     * 写入“最近一次同步成功”的时间，并清空上次错误。
     */
    suspend fun setLastSyncSuccess()

    /**
     * 写入“最近一次同步失败”的错误信息（会保留 lastSuccessAt）。
     *
     * @param httpCode 可选：HTTP 状态码（401/403 可用于判断鉴权失效）。
     */
    suspend fun setLastSyncError(
        error: String,
        httpCode: Int = 0,
    )

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

    suspend fun acknowledgeFullSyncCompletion(runId: String)

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
