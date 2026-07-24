package cc.pscly.onememos.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cc.pscly.onememos.core.network.MemosUrls
import cc.pscly.onememos.domain.model.AppSettings
import cc.pscly.onememos.domain.model.FullSyncStage
import cc.pscly.onememos.domain.model.FullSyncState
import cc.pscly.onememos.domain.model.FullSyncStatus
import cc.pscly.onememos.domain.model.LastSyncState
import cc.pscly.onememos.domain.model.ListLayout
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.QuickInsertTimeFormat
import cc.pscly.onememos.domain.model.ReadingFontScale
import cc.pscly.onememos.domain.model.ReadingLineHeight
import cc.pscly.onememos.domain.model.SwipeAction
import cc.pscly.onememos.domain.model.ThemeDescriptor
import cc.pscly.onememos.domain.model.ThemeDensity
import cc.pscly.onememos.domain.model.ThemeFontFamily
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.domain.model.ThemeTexture
import cc.pscly.onememos.domain.model.ThemeTypeScale
import cc.pscly.onememos.domain.model.TodoReminderMode
import cc.pscly.onememos.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import org.json.JSONObject
import java.security.MessageDigest
import javax.inject.Inject

/**
 * 应用设置 DataStore（单文件）。internal 以便同模块单测可 seed 旧 key 验证迁移。
 */
internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "one_memos_settings",
)

    class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptedTokenStorage: TokenStorage,
) : SettingsRepository {
    private object Keys {
        val WELCOME_COMPLETED = booleanPreferencesKey("welcome_completed")
        val SERVER_URL = stringPreferencesKey("server_url")
        val LOGIN_MODE = stringPreferencesKey("login_mode")
        val CURRENT_USER_CREATOR = stringPreferencesKey("current_user_creator")
        val DEV2_UNLOCKED = booleanPreferencesKey("dev2_unlocked")
        val DEV2_SHOW_PUBLIC_WORKSPACE_MEMOS = booleanPreferencesKey("dev2_show_public_workspace_memos")
        // 兼容迁移：历史版本可能把 token 明文写入 DataStore
        val LEGACY_TOKEN = stringPreferencesKey("token")
        // 用于触发 settings flow 更新：token 实际存放于 EncryptedSharedPreferences
        val TOKEN_UPDATED_AT = longPreferencesKey("token_updated_at")
        // 兼容迁移：历史版本只存 theme_palette 字符串枚举
        val LEGACY_THEME_PALETTE = stringPreferencesKey("theme_palette")
        /** 主题描述符 JSON：{palette, texture, density, typeScale, fontFamily} */
        val THEME_DESCRIPTOR = stringPreferencesKey("theme_descriptor")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_VISIBILITY = stringPreferencesKey("default_visibility")
        val REGEX_SEARCH_ENABLED = booleanPreferencesKey("regex_search_enabled")
        val SHOW_TAG_COUNTS_IN_FILTER = booleanPreferencesKey("show_tag_counts_in_filter")
        val TAG_CHIP_COLORFUL = booleanPreferencesKey("tag_chip_colorful")
        val QUICK_CAPTURE_OVERLAY_ENABLED = booleanPreferencesKey("quick_capture_overlay_enabled")
        val QUICK_INSERT_TIME_ENABLED = booleanPreferencesKey("quick_insert_time_enabled")
        val QUICK_INSERT_TIME_FORMAT = stringPreferencesKey("quick_insert_time_format")
        val SEAL_STAMP_DURATION_MS = intPreferencesKey("seal_stamp_duration_ms")
        val OFFLINE_IMAGE_PREFETCH_ENABLED = booleanPreferencesKey("offline_image_prefetch_enabled")
        val OFFLINE_IMAGE_PREFETCH_MAX_MEMOS = intPreferencesKey("offline_image_prefetch_max_memos")
        val OFFLINE_IMAGE_PREFETCH_MAX_IMAGES = intPreferencesKey("offline_image_prefetch_max_images")
        val ATTACHMENT_CACHE_MAX_MB = intPreferencesKey("attachment_cache_max_mb")
        val ATTACHMENT_UPLOAD_MAX_MB = intPreferencesKey("attachment_upload_max_mb")

        // Todo 提醒模式（SMART / EXACT）
        val TODO_REMINDER_MODE = stringPreferencesKey("todo_reminder_mode")

        // 日历联动：Todo -> 系统日历
        val CALENDAR_INTEGRATION_ENABLED = booleanPreferencesKey("calendar_integration_enabled")
        val CALENDAR_INTEGRATION_CALENDAR_ID = longPreferencesKey("calendar_integration_calendar_id")
        val CALENDAR_INTEGRATION_SYNC_REMINDERS = booleanPreferencesKey("calendar_integration_sync_reminders")

        // 外观交互（M2/M3 schema，M1 只写字段与默认）
        val LIST_LAYOUT = stringPreferencesKey("list_layout")
        val SWIPE_ENABLED = booleanPreferencesKey("swipe_enabled")
        val SWIPE_RIGHT_ACTION = stringPreferencesKey("swipe_right_action")
        val SWIPE_LEFT_ACTION = stringPreferencesKey("swipe_left_action")
        val PAGE_TRANSITIONS_ENABLED = booleanPreferencesKey("page_transitions_enabled")
        val READING_FONT_SCALE = stringPreferencesKey("reading_font_scale")
        val LINE_HEIGHT = stringPreferencesKey("line_height")
        val LIST_MARKDOWN_IMMEDIATE_LOAD = booleanPreferencesKey("list_markdown_immediate_load")

        // 最近一次同步结果（轻量状态）
        val LAST_SYNC_SUCCESS_AT = longPreferencesKey("last_sync_success_at")
        val LAST_SYNC_ERROR = stringPreferencesKey("last_sync_error")
        val LAST_SYNC_ERROR_AT = longPreferencesKey("last_sync_error_at")
        val LAST_SYNC_ERROR_HTTP_CODE = intPreferencesKey("last_sync_error_http_code")

        // 全量同步（Full Sync）状态
        // 兼容迁移：历史版本的 fullSync 是单槽位存储。
        val LEGACY_FULL_SYNC_STATUS = stringPreferencesKey("full_sync_status")
        val LEGACY_FULL_SYNC_RUN_ID = stringPreferencesKey("full_sync_run_id")
        val LEGACY_FULL_SYNC_LAST_SUCCESS_AT = longPreferencesKey("full_sync_last_success_at")
        val LEGACY_FULL_SYNC_LAST_ERROR = stringPreferencesKey("full_sync_last_error")
        val LEGACY_FULL_SYNC_STAGE = stringPreferencesKey("full_sync_stage")
        val LEGACY_FULL_SYNC_PAGES_FETCHED = intPreferencesKey("full_sync_pages_fetched")
        val LEGACY_FULL_SYNC_ITEMS_FETCHED = intPreferencesKey("full_sync_items_fetched")
        val LEGACY_FULL_SYNC_KEY = stringPreferencesKey("full_sync_key")

        // 开发者选项：自动标签元数据行（如 __Atags）
        val DEV_AUTO_TAG_LINE_KEYWORDS = stringPreferencesKey("dev_auto_tag_line_keywords")
        val DEV_SHOW_AUTO_TAG_LINE_IN_HOME = booleanPreferencesKey("dev_show_auto_tag_line_in_home")
        val DEV_SHOW_AUTO_TAG_LINE_IN_VIEW = booleanPreferencesKey("dev_show_auto_tag_line_in_view")
        val DEV_SHOW_AUTO_TAG_LINE_IN_EDIT = booleanPreferencesKey("dev_show_auto_tag_line_in_edit")

        // 开发者选项：主页富预览“粘住”上限（仅 benchmark/release 生效）
        val DEV_HOME_RICH_PREVIEW_STICKY_LIMIT = intPreferencesKey("dev_home_rich_preview_sticky_limit")
    }

    private val migrationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val tokenCacheLock = Any()
    @Volatile private var cachedTokenUpdatedAt: Long = Long.MIN_VALUE
    @Volatile private var cachedToken: String = ""

    private fun cachedTokenFor(prefs: Preferences): String {
        // TOKEN_UPDATED_AT 用作“token 变化”的触发器：只有它变化时才重新读取加密存储。
        val updatedAt = prefs[Keys.TOKEN_UPDATED_AT] ?: 0L
        if (cachedTokenUpdatedAt == updatedAt) return cachedToken

        synchronized(tokenCacheLock) {
            if (cachedTokenUpdatedAt == updatedAt) return cachedToken
            val token = encryptedTokenStorage.getToken()
            cachedTokenUpdatedAt = updatedAt
            cachedToken = token
            return token
        }
    }

    init {
        migrateLegacyTokenIfNeeded()
        migrateLegacyFullSyncIfNeeded()
        migrateLegacyThemePaletteIfNeeded()
    }

    private data class FullSyncPreferenceKeys(
        val status: Preferences.Key<String>,
        val runId: Preferences.Key<String>,
        val acknowledgedSuccessRunId: Preferences.Key<String>,
        val lastSuccessAt: Preferences.Key<Long>,
        val lastError: Preferences.Key<String>,
        val stage: Preferences.Key<String>,
        val pagesFetched: Preferences.Key<Int>,
        val itemsFetched: Preferences.Key<Int>,
    ) {
        fun hasAny(prefs: Preferences): Boolean {
            return prefs.contains(status) ||
                prefs.contains(runId) ||
                prefs.contains(acknowledgedSuccessRunId) ||
                prefs.contains(lastSuccessAt) ||
                prefs.contains(lastError) ||
                prefs.contains(stage) ||
                prefs.contains(pagesFetched) ||
                prefs.contains(itemsFetched)
        }
    }

    /**
     * fullSync 状态按 syncKey 分槽位存储。
     *
     * 说明：
     * - Preferences 的 key 不适合直接拼接 syncKey（可能含非法字符且过长），这里用稳定 hash 作为后缀。
     * - hash 只用于“key 命名”，不会影响业务语义；真实 syncKey 仍由 settings 映射计算得出。
     */
    private fun fullSyncPreferenceKeys(syncKey: String): FullSyncPreferenceKeys {
        val h = shortStableHash(syncKey)
        return FullSyncPreferenceKeys(
            status = stringPreferencesKey("full_sync_status_$h"),
            runId = stringPreferencesKey("full_sync_run_id_$h"),
            acknowledgedSuccessRunId = stringPreferencesKey("full_sync_acknowledged_success_run_id_$h"),
            lastSuccessAt = longPreferencesKey("full_sync_last_success_at_$h"),
            lastError = stringPreferencesKey("full_sync_last_error_$h"),
            stage = stringPreferencesKey("full_sync_stage_$h"),
            pagesFetched = intPreferencesKey("full_sync_pages_fetched_$h"),
            itemsFetched = intPreferencesKey("full_sync_items_fetched_$h"),
        )
    }

    private fun shortStableHash(input: String): String {
        // 取 sha256 的前 12 个 hex（48bit），足够用作 Preferences key 后缀。
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val out = CharArray(12)
        val hex = "0123456789abcdef"
        var o = 0
        var i = 0
        while (o < out.size) {
            val b = digest[i].toInt() and 0xff
            out[o++] = hex[b ushr 4]
            if (o >= out.size) break
            out[o++] = hex[b and 0x0f]
            i++
        }
        return String(out)
    }

    override val settings: Flow<AppSettings> =
        context.settingsDataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs ->
                val serverUrl = prefs[Keys.SERVER_URL].orEmpty()
                val token = cachedTokenFor(prefs)
                val loginMode =
                    parseLoginMode(prefs[Keys.LOGIN_MODE])
                        ?: if (serverUrl.isNotBlank() || token.isNotBlank()) LoginMode.CUSTOM else LoginMode.UNKNOWN
                val dev2Unlocked = prefs[Keys.DEV2_UNLOCKED] ?: false
                val dev2ShowPublicWorkspaceMemos =
                    (prefs[Keys.DEV2_SHOW_PUBLIC_WORKSPACE_MEMOS]
                        ?: false) && dev2Unlocked && token.isNotBlank()
                val currentUserCreator = prefs[Keys.CURRENT_USER_CREATOR].orEmpty()
                val welcomeCompleted =
                    prefs[Keys.WELCOME_COMPLETED]
                        // 升级用户：若已经配置过 serverUrl/token，则默认视为“已完成引导”，避免更新后首次打开被打断。
                        ?: (serverUrl.isNotBlank() || token.isNotBlank())

                // 普通用户无感：只要已登录/已配置 token，就强制使用默认服务器；避免 UI 与配置暴露“服务器概念”。
                // 仅当已解锁开发者模式2时才允许使用自定义 serverUrl。
                val effectiveServerUrl =
                    if (token.isBlank()) {
                        serverUrl
                    } else if (dev2Unlocked) {
                        serverUrl
                    } else {
                        MemosUrls.DEFAULT_MEMOS_SERVER_URL
                    }

                val currentFullSyncKey = buildFullSyncKey(
                    serverUrl = effectiveServerUrl,
                    currentUserCreator = currentUserCreator,
                )

                val lastSync =
                    LastSyncState(
                        lastSuccessAt = prefs[Keys.LAST_SYNC_SUCCESS_AT] ?: 0L,
                        lastError = prefs[Keys.LAST_SYNC_ERROR].orEmpty(),
                        lastErrorAt = prefs[Keys.LAST_SYNC_ERROR_AT] ?: 0L,
                        lastErrorHttpCode = prefs[Keys.LAST_SYNC_ERROR_HTTP_CODE] ?: 0,
                    )

                val slotKeys = fullSyncPreferenceKeys(currentFullSyncKey)
                val slotFullSync =
                    FullSyncState(
                        status = parseFullSyncStatus(prefs[slotKeys.status]),
                        runId = prefs[slotKeys.runId].orEmpty(),
                        acknowledgedSuccessRunId = prefs[slotKeys.acknowledgedSuccessRunId].orEmpty(),
                        lastSuccessAt = prefs[slotKeys.lastSuccessAt] ?: 0L,
                        lastError = prefs[slotKeys.lastError].orEmpty(),
                        stage = parseFullSyncStage(prefs[slotKeys.stage]),
                        pagesFetched = (prefs[slotKeys.pagesFetched] ?: 0).coerceAtLeast(0),
                        itemsFetched = (prefs[slotKeys.itemsFetched] ?: 0).coerceAtLeast(0),
                        syncKey = currentFullSyncKey,
                    )

                // 兼容：迁移尚未完成前，允许“只读回退”到旧单槽位状态（依然按 syncKey 严格匹配，不串台）。
                val legacySyncKey = prefs[Keys.LEGACY_FULL_SYNC_KEY].orEmpty().trim()
                val hasSlotState = slotKeys.hasAny(prefs)
                val effectiveFullSync =
                    if (!hasSlotState && legacySyncKey.isNotBlank() && legacySyncKey == currentFullSyncKey) {
                        FullSyncState(
                            status = parseFullSyncStatus(prefs[Keys.LEGACY_FULL_SYNC_STATUS]),
                            runId = prefs[Keys.LEGACY_FULL_SYNC_RUN_ID].orEmpty(),
                            lastSuccessAt = prefs[Keys.LEGACY_FULL_SYNC_LAST_SUCCESS_AT] ?: 0L,
                            lastError = prefs[Keys.LEGACY_FULL_SYNC_LAST_ERROR].orEmpty(),
                            stage = parseFullSyncStage(prefs[Keys.LEGACY_FULL_SYNC_STAGE]),
                            pagesFetched = (prefs[Keys.LEGACY_FULL_SYNC_PAGES_FETCHED] ?: 0).coerceAtLeast(0),
                            itemsFetched = (prefs[Keys.LEGACY_FULL_SYNC_ITEMS_FETCHED] ?: 0).coerceAtLeast(0),
                            syncKey = currentFullSyncKey,
                        )
                    } else {
                        slotFullSync
                    }

                AppSettings(
                    serverUrl = effectiveServerUrl,
                    token = token,
                    loginMode = loginMode,
                    currentUserCreator = currentUserCreator,
                    dev2ShowPublicWorkspaceMemos = dev2ShowPublicWorkspaceMemos,
                    dev2Unlocked = dev2Unlocked,
                    welcomeCompleted = welcomeCompleted,
                    themeDescriptor = resolveThemeDescriptor(prefs),
                    themeMode = parseThemeMode(prefs[Keys.THEME_MODE]),
                    defaultVisibility = parseMemoVisibility(prefs[Keys.DEFAULT_VISIBILITY]),
                    regexSearchEnabled = prefs[Keys.REGEX_SEARCH_ENABLED] ?: false,
                    showTagCountsInFilter = prefs[Keys.SHOW_TAG_COUNTS_IN_FILTER] ?: true,
                    tagChipColorful = prefs[Keys.TAG_CHIP_COLORFUL] ?: true,
                    quickCaptureOverlayEnabled = prefs[Keys.QUICK_CAPTURE_OVERLAY_ENABLED] ?: false,
                    quickInsertTimeEnabled = prefs[Keys.QUICK_INSERT_TIME_ENABLED] ?: false,
                    quickInsertTimeFormat = QuickInsertTimeFormat.fromStorage(prefs[Keys.QUICK_INSERT_TIME_FORMAT]),
                    sealStampDurationMs = (prefs[Keys.SEAL_STAMP_DURATION_MS] ?: 600).coerceIn(200, 2000),
                    offlineImagePrefetchEnabled = prefs[Keys.OFFLINE_IMAGE_PREFETCH_ENABLED] ?: true,
                    offlineImagePrefetchMaxMemos = (prefs[Keys.OFFLINE_IMAGE_PREFETCH_MAX_MEMOS] ?: 30).coerceIn(0, 5000),
                    offlineImagePrefetchMaxImages = (prefs[Keys.OFFLINE_IMAGE_PREFETCH_MAX_IMAGES] ?: 60).coerceIn(0, 5000),
                    attachmentCacheMaxMb = (prefs[Keys.ATTACHMENT_CACHE_MAX_MB] ?: 1024).coerceIn(0, 10 * 1024),
                    attachmentUploadMaxMb =
                        if (dev2Unlocked) {
                            (prefs[Keys.ATTACHMENT_UPLOAD_MAX_MB] ?: 50).coerceIn(1, 1024)
                        } else {
                            50
                        },
                    todoReminderMode = parseTodoReminderMode(prefs[Keys.TODO_REMINDER_MODE]),
                    calendarIntegrationEnabled = prefs[Keys.CALENDAR_INTEGRATION_ENABLED] ?: false,
                    calendarIntegrationCalendarId = prefs[Keys.CALENDAR_INTEGRATION_CALENDAR_ID],
                    calendarIntegrationSyncReminders = prefs[Keys.CALENDAR_INTEGRATION_SYNC_REMINDERS] ?: true,
                    listLayout = parseListLayout(prefs[Keys.LIST_LAYOUT]),
                    swipeEnabled = prefs[Keys.SWIPE_ENABLED] ?: true,
                    swipeRightAction = parseSwipeAction(prefs[Keys.SWIPE_RIGHT_ACTION], SwipeAction.ADD_TO_TODO),
                    swipeLeftAction = parseSwipeAction(prefs[Keys.SWIPE_LEFT_ACTION], SwipeAction.FAVORITE),
                    pageTransitionsEnabled = prefs[Keys.PAGE_TRANSITIONS_ENABLED] ?: true,
                    readingFontScale = parseReadingFontScale(prefs[Keys.READING_FONT_SCALE]),
                    lineHeight = parseReadingLineHeight(prefs[Keys.LINE_HEIGHT]),
                    listMarkdownImmediateLoad = prefs[Keys.LIST_MARKDOWN_IMMEDIATE_LOAD] ?: false,
                    lastSync = lastSync,
                    fullSync = effectiveFullSync,
                    devAutoTagLineKeywords = prefs[Keys.DEV_AUTO_TAG_LINE_KEYWORDS] ?: "__Atags",
                    devShowAutoTagLineInHome = prefs[Keys.DEV_SHOW_AUTO_TAG_LINE_IN_HOME] ?: false,
                    devShowAutoTagLineInView = prefs[Keys.DEV_SHOW_AUTO_TAG_LINE_IN_VIEW] ?: false,
                    devShowAutoTagLineInEdit = prefs[Keys.DEV_SHOW_AUTO_TAG_LINE_IN_EDIT] ?: false,
                    devHomeRichPreviewStickyLimit =
                        (prefs[Keys.DEV_HOME_RICH_PREVIEW_STICKY_LIMIT] ?: 500).coerceIn(0, 5000),
                )
            }
            // settings 会在冷启动早期被收集；把 token 的加密存储读取放到 IO，避免主线程触发 KeyStore 工作。
            .flowOn(Dispatchers.IO)

    override suspend fun setWelcomeCompleted(completed: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.WELCOME_COMPLETED] = completed
        }
    }

    override suspend fun setServerUrl(url: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = url.trim()
            clearLastSyncStateLocked(prefs)
        }
    }

    override suspend fun setToken(token: String) {
        encryptedTokenStorage.setToken(token.trim())
        context.settingsDataStore.edit { prefs ->
            prefs.remove(Keys.LEGACY_TOKEN)
            prefs[Keys.TOKEN_UPDATED_AT] = System.currentTimeMillis()
            clearLastSyncStateLocked(prefs)
        }
    }

    override suspend fun setLoginMode(mode: LoginMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.LOGIN_MODE] = mode.name
        }
    }

    override suspend fun setCurrentUserCreator(creator: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.CURRENT_USER_CREATOR] = creator.trim()
        }
    }

    override suspend fun setDev2Unlocked(unlocked: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DEV2_UNLOCKED] = unlocked
        }
    }

    override suspend fun setDev2ShowPublicWorkspaceMemos(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DEV2_SHOW_PUBLIC_WORKSPACE_MEMOS] = enabled
        }
    }

    override suspend fun setThemePalette(palette: ThemePalette) {
        // 兼容旧 API：只改色板轴，其余轴沿用当前描述符（缺失时按旧枚举完整映射）。
        context.settingsDataStore.edit { prefs ->
            val current = resolveThemeDescriptor(prefs)
            val next =
                if (prefs.contains(Keys.THEME_DESCRIPTOR)) {
                    current.copy(palette = palette)
                } else {
                    ThemeDescriptor.fromLegacyPalette(palette)
                }
            prefs[Keys.THEME_DESCRIPTOR] = encodeThemeDescriptor(next)
            prefs.remove(Keys.LEGACY_THEME_PALETTE)
        }
    }

    override suspend fun setThemeDescriptor(descriptor: ThemeDescriptor) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.THEME_DESCRIPTOR] = encodeThemeDescriptor(descriptor)
            prefs.remove(Keys.LEGACY_THEME_PALETTE)
        }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    override suspend fun setDefaultVisibility(visibility: MemoVisibility) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_VISIBILITY] = visibility.name
        }
    }

    override suspend fun setRegexSearchEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.REGEX_SEARCH_ENABLED] = enabled
        }
    }

    override suspend fun setShowTagCountsInFilter(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SHOW_TAG_COUNTS_IN_FILTER] = enabled
        }
    }

    override suspend fun setTagChipColorful(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.TAG_CHIP_COLORFUL] = enabled
        }
    }

    override suspend fun setListMarkdownImmediateLoad(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.LIST_MARKDOWN_IMMEDIATE_LOAD] = enabled
        }
    }

    override suspend fun setQuickCaptureOverlayEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.QUICK_CAPTURE_OVERLAY_ENABLED] = enabled
        }
    }

    override suspend fun setQuickInsertTimeEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.QUICK_INSERT_TIME_ENABLED] = enabled
        }
    }

    override suspend fun setQuickInsertTimeFormat(format: QuickInsertTimeFormat) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.QUICK_INSERT_TIME_FORMAT] = format.name
        }
    }

    override suspend fun setSealStampDurationMs(durationMs: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SEAL_STAMP_DURATION_MS] = durationMs.coerceIn(200, 2000)
        }
    }

    override suspend fun setReadingFontScale(scale: ReadingFontScale) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.READING_FONT_SCALE] = scale.name
        }
    }

    override suspend fun setReadingLineHeight(lineHeight: ReadingLineHeight) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.LINE_HEIGHT] = lineHeight.name
        }
    }

    override suspend fun setOfflineImagePrefetchEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.OFFLINE_IMAGE_PREFETCH_ENABLED] = enabled
        }
    }

    override suspend fun setOfflineImagePrefetchMaxMemos(count: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.OFFLINE_IMAGE_PREFETCH_MAX_MEMOS] = count.coerceIn(0, 5000)
        }
    }

    override suspend fun setOfflineImagePrefetchMaxImages(count: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.OFFLINE_IMAGE_PREFETCH_MAX_IMAGES] = count.coerceIn(0, 5000)
        }
    }

    override suspend fun setAttachmentCacheMaxMb(mb: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.ATTACHMENT_CACHE_MAX_MB] = mb.coerceIn(0, 10 * 1024)
        }
    }

    override suspend fun setAttachmentUploadMaxMb(mb: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.ATTACHMENT_UPLOAD_MAX_MB] = mb.coerceIn(1, 1024)
        }
    }

    override suspend fun setTodoReminderMode(mode: TodoReminderMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.TODO_REMINDER_MODE] = mode.name
        }
    }

    override suspend fun setCalendarIntegrationEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.CALENDAR_INTEGRATION_ENABLED] = enabled
        }
    }

    override suspend fun setCalendarIntegrationCalendarId(calendarId: Long?) {
        context.settingsDataStore.edit { prefs ->
            if (calendarId == null) {
                prefs.remove(Keys.CALENDAR_INTEGRATION_CALENDAR_ID)
            } else {
                prefs[Keys.CALENDAR_INTEGRATION_CALENDAR_ID] = calendarId.coerceAtLeast(0L)
            }
        }
    }

    override suspend fun setCalendarIntegrationSyncReminders(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.CALENDAR_INTEGRATION_SYNC_REMINDERS] = enabled
        }
    }

    override suspend fun setLastSyncSuccess() {
        val now = System.currentTimeMillis()
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.LAST_SYNC_SUCCESS_AT] = now
            prefs[Keys.LAST_SYNC_ERROR] = ""
            prefs[Keys.LAST_SYNC_ERROR_AT] = 0L
            prefs[Keys.LAST_SYNC_ERROR_HTTP_CODE] = 0
        }
    }

    override suspend fun setLastSyncError(error: String, httpCode: Int) {
        val err = error.trim().take(2000)
        val now = System.currentTimeMillis()
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.LAST_SYNC_ERROR] = err
            prefs[Keys.LAST_SYNC_ERROR_AT] = now
            prefs[Keys.LAST_SYNC_ERROR_HTTP_CODE] = httpCode.coerceAtLeast(0)
        }
    }

    override suspend fun setDevAutoTagLineKeywords(raw: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DEV_AUTO_TAG_LINE_KEYWORDS] = raw.trim()
        }
    }

    override suspend fun setDevShowAutoTagLineInHome(show: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DEV_SHOW_AUTO_TAG_LINE_IN_HOME] = show
        }
    }

    override suspend fun setDevShowAutoTagLineInView(show: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DEV_SHOW_AUTO_TAG_LINE_IN_VIEW] = show
        }
    }

    override suspend fun setDevShowAutoTagLineInEdit(show: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DEV_SHOW_AUTO_TAG_LINE_IN_EDIT] = show
        }
    }

    override suspend fun setDevHomeRichPreviewStickyLimit(limit: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DEV_HOME_RICH_PREVIEW_STICKY_LIMIT] = limit.coerceIn(0, 5000)
        }
    }

    override suspend fun setFullSyncRunning(runId: String) {
        val runIdNorm = runId.trim()
        val syncKey = computeFullSyncKey()
        val keys = fullSyncPreferenceKeys(syncKey)

        context.settingsDataStore.edit { prefs ->
            prefs[keys.status] = FullSyncStatus.RUNNING.name
            prefs[keys.runId] = runIdNorm
            prefs[keys.stage] = FullSyncStage.NORMAL.name
            prefs[keys.pagesFetched] = 0
            prefs[keys.itemsFetched] = 0
            prefs[keys.lastError] = ""
        }
    }

    override suspend fun setFullSyncProgress(
        runId: String,
        stage: FullSyncStage,
        pagesFetched: Int,
        itemsFetched: Int,
    ) {
        val runIdNorm = runId.trim()
        val syncKey = computeFullSyncKey()
        val keys = fullSyncPreferenceKeys(syncKey)

        context.settingsDataStore.edit { prefs ->
            val storedRunId = prefs[keys.runId].orEmpty()
            if (storedRunId.isNotBlank() && storedRunId != runIdNorm) return@edit

            prefs[keys.status] = FullSyncStatus.RUNNING.name
            prefs[keys.runId] = runIdNorm
            prefs[keys.stage] = stage.name
            prefs[keys.pagesFetched] = pagesFetched.coerceAtLeast(0)
            prefs[keys.itemsFetched] = itemsFetched.coerceAtLeast(0)
        }
    }

    override suspend fun setFullSyncSuccess(
        runId: String,
        stage: FullSyncStage,
        pagesFetched: Int,
        itemsFetched: Int,
    ) {
        val runIdNorm = runId.trim()
        val syncKey = computeFullSyncKey()
        val keys = fullSyncPreferenceKeys(syncKey)
        val now = System.currentTimeMillis()

        context.settingsDataStore.edit { prefs ->
            val storedRunId = prefs[keys.runId].orEmpty()
            if (storedRunId.isNotBlank() && storedRunId != runIdNorm) return@edit

            prefs[keys.status] = FullSyncStatus.SUCCESS.name
            prefs[keys.runId] = runIdNorm
            prefs[keys.stage] = stage.name
            prefs[keys.pagesFetched] = pagesFetched.coerceAtLeast(0)
            prefs[keys.itemsFetched] = itemsFetched.coerceAtLeast(0)
            prefs[keys.lastSuccessAt] = now
            prefs[keys.lastError] = ""
        }
    }

    override suspend fun acknowledgeFullSyncCompletion(runId: String) {
        val runIdNorm = runId.trim()
        if (runIdNorm.isBlank()) return
        val syncKey = computeFullSyncKey()
        val keys = fullSyncPreferenceKeys(syncKey)

        context.settingsDataStore.edit { prefs ->
            if (prefs[keys.status] != FullSyncStatus.SUCCESS.name) return@edit
            if (prefs[keys.runId].orEmpty() != runIdNorm) return@edit
            prefs[keys.acknowledgedSuccessRunId] = runIdNorm
        }
    }

    override suspend fun setFullSyncFailed(
        runId: String,
        stage: FullSyncStage,
        pagesFetched: Int,
        itemsFetched: Int,
        error: String,
    ) {
        val runIdNorm = runId.trim()
        val syncKey = computeFullSyncKey()
        val keys = fullSyncPreferenceKeys(syncKey)
        val errorNorm = error.trim().take(2000)

        context.settingsDataStore.edit { prefs ->
            val storedRunId = prefs[keys.runId].orEmpty()
            if (storedRunId.isNotBlank() && storedRunId != runIdNorm) return@edit

            prefs[keys.status] = FullSyncStatus.FAILED.name
            prefs[keys.runId] = runIdNorm
            prefs[keys.stage] = stage.name
            prefs[keys.pagesFetched] = pagesFetched.coerceAtLeast(0)
            prefs[keys.itemsFetched] = itemsFetched.coerceAtLeast(0)
            prefs[keys.lastError] = errorNorm
        }
    }

    override suspend fun setFullSyncCancelled(
        runId: String,
        stage: FullSyncStage,
        pagesFetched: Int,
        itemsFetched: Int,
    ) {
        val runIdNorm = runId.trim()
        val syncKey = computeFullSyncKey()
        val keys = fullSyncPreferenceKeys(syncKey)

        context.settingsDataStore.edit { prefs ->
            // 与 success/failed 保持一致：仅允许当前槽位的 runId 更新自己的状态，避免串台覆盖。
            val storedRunId = prefs[keys.runId].orEmpty()
            if (storedRunId.isNotBlank() && storedRunId != runIdNorm) return@edit

            prefs[keys.status] = FullSyncStatus.CANCELLED.name
            prefs[keys.runId] = runIdNorm
            prefs[keys.stage] = stage.name
            prefs[keys.pagesFetched] = pagesFetched.coerceAtLeast(0)
            prefs[keys.itemsFetched] = itemsFetched.coerceAtLeast(0)
            // 注意：不要清空 lastSuccessAt，以保留历史成功记录。
            prefs[keys.lastError] = "已取消"
        }
    }

    /**
     * 解析主题描述符：优先 `theme_descriptor` JSON；否则从旧 `theme_palette` 映射；
     * 缺失/未知一律回退文墨·朱砂。
     *
     * 注意：此方法只读不写；异步迁移见 [migrateLegacyThemePaletteIfNeeded]。
     */
    private fun resolveThemeDescriptor(prefs: Preferences): ThemeDescriptor {
        val json = prefs[Keys.THEME_DESCRIPTOR]
        if (!json.isNullOrBlank()) {
            decodeThemeDescriptor(json)?.let { return it }
            return ThemeDescriptor.WENMO_ZHUSHA
        }
        val legacy = prefs[Keys.LEGACY_THEME_PALETTE]
        if (!legacy.isNullOrBlank()) {
            val palette =
                runCatching { ThemePalette.valueOf(legacy) }.getOrNull()
                    ?: return ThemeDescriptor.WENMO_ZHUSHA
            return ThemeDescriptor.fromLegacyPalette(palette)
        }
        return ThemeDescriptor.WENMO_ZHUSHA
    }

    private fun encodeThemeDescriptor(descriptor: ThemeDescriptor): String =
        JSONObject()
            .put("palette", descriptor.palette.name)
            .put("texture", descriptor.texture.name)
            .put("density", descriptor.density.name)
            .put("typeScale", descriptor.typeScale.name)
            .put("fontFamily", descriptor.fontFamily.name)
            .toString()

    private fun decodeThemeDescriptor(raw: String): ThemeDescriptor? {
        return runCatching {
            val obj = JSONObject(raw)
            val palette =
                runCatching { ThemePalette.valueOf(obj.getString("palette")) }.getOrNull()
                    ?: return null
            val texture =
                runCatching { ThemeTexture.valueOf(obj.getString("texture")) }.getOrNull()
                    ?: return null
            val density =
                runCatching { ThemeDensity.valueOf(obj.getString("density")) }.getOrNull()
                    ?: return null
            val typeScale =
                runCatching { ThemeTypeScale.valueOf(obj.getString("typeScale")) }.getOrNull()
                    ?: return null
            val fontFamily =
                runCatching { ThemeFontFamily.valueOf(obj.getString("fontFamily")) }.getOrNull()
                    ?: return null
            ThemeDescriptor(
                palette = palette,
                texture = texture,
                density = density,
                typeScale = typeScale,
                fontFamily = fontFamily,
            )
        }.getOrNull()
    }

    private fun parseThemeMode(raw: String?): ThemeMode =
        runCatching { if (raw.isNullOrBlank()) null else ThemeMode.valueOf(raw) }
            .getOrNull()
            ?: ThemeMode.FOLLOW_SYSTEM

    private fun parseListLayout(raw: String?): ListLayout =
        runCatching { if (raw.isNullOrBlank()) null else ListLayout.valueOf(raw) }
            .getOrNull()
            ?: ListLayout.AUTO

    private fun parseSwipeAction(raw: String?, default: SwipeAction): SwipeAction =
        runCatching { if (raw.isNullOrBlank()) null else SwipeAction.valueOf(raw) }
            .getOrNull()
            ?: default

    private fun parseReadingFontScale(raw: String?): ReadingFontScale =
        runCatching { if (raw.isNullOrBlank()) null else ReadingFontScale.valueOf(raw) }
            .getOrNull()
            ?: ReadingFontScale.STANDARD

    private fun parseReadingLineHeight(raw: String?): ReadingLineHeight =
        runCatching { if (raw.isNullOrBlank()) null else ReadingLineHeight.valueOf(raw) }
            .getOrNull()
            ?: ReadingLineHeight.STANDARD

    private fun parseTodoReminderMode(raw: String?): TodoReminderMode =
        runCatching { if (raw.isNullOrBlank()) null else TodoReminderMode.valueOf(raw) }
            .getOrNull()
            ?: TodoReminderMode.SMART

    private fun parseFullSyncStatus(raw: String?): FullSyncStatus =
        runCatching { if (raw.isNullOrBlank()) null else FullSyncStatus.valueOf(raw) }
            .getOrNull()
            ?: FullSyncStatus.IDLE

    private fun parseFullSyncStage(raw: String?): FullSyncStage =
        runCatching { if (raw.isNullOrBlank()) null else FullSyncStage.valueOf(raw) }
            .getOrNull()
            ?: FullSyncStage.NORMAL

    private fun clearLastSyncStateLocked(prefs: MutablePreferences) {
        prefs[Keys.LAST_SYNC_SUCCESS_AT] = 0L
        prefs[Keys.LAST_SYNC_ERROR] = ""
        prefs[Keys.LAST_SYNC_ERROR_AT] = 0L
        prefs[Keys.LAST_SYNC_ERROR_HTTP_CODE] = 0
    }

    private suspend fun computeFullSyncKey(): String {
        val s = settings.first()
        return buildFullSyncKey(serverUrl = s.serverUrl, currentUserCreator = s.currentUserCreator)
    }

    private fun buildFullSyncKey(serverUrl: String, currentUserCreator: String): String {
        val base = MemosUrls.normalizeServerBase(serverUrl) ?: ""
        val creator = currentUserCreator.trim().ifBlank { "(unknown)" }
        return "$base|$creator"
    }

    private fun parseMemoVisibility(raw: String?): MemoVisibility =
        runCatching { if (raw.isNullOrBlank()) null else MemoVisibility.valueOf(raw) }
            .getOrNull()
            ?: MemoVisibility.PRIVATE

    private fun parseLoginMode(raw: String?): LoginMode? {
        val v = raw.orEmpty().trim()
        if (v.isBlank()) return null
        return runCatching { LoginMode.valueOf(v) }.getOrNull()
    }

    private fun migrateLegacyTokenIfNeeded() {
        migrationScope.launch {
            val prefs = context.settingsDataStore.data.first()
            val legacy = prefs[Keys.LEGACY_TOKEN].orEmpty().trim()
            val current = encryptedTokenStorage.getToken().trim()

            if (legacy.isNotBlank() && current.isBlank()) {
                encryptedTokenStorage.setToken(legacy)
            }

            if (legacy.isNotBlank()) {
                context.settingsDataStore.edit { p ->
                    p.remove(Keys.LEGACY_TOKEN)
                    p[Keys.TOKEN_UPDATED_AT] = System.currentTimeMillis()
                }
            }
        }
    }

    /**
     * 将旧 key `theme_palette` 迁移为 `theme_descriptor` JSON，成功后清除旧 key。
     * 未知枚举 → 文墨·朱砂；若已有有效 descriptor 则仅清旧 key。
     */
    private fun migrateLegacyThemePaletteIfNeeded() {
        migrationScope.launch {
            val prefs = context.settingsDataStore.data.first()
            val hasLegacy = prefs.contains(Keys.LEGACY_THEME_PALETTE)
            val hasDescriptor = prefs.contains(Keys.THEME_DESCRIPTOR)
            if (!hasLegacy && hasDescriptor) return@launch
            if (!hasLegacy && !hasDescriptor) return@launch

            val descriptor =
                if (hasDescriptor) {
                    decodeThemeDescriptor(prefs[Keys.THEME_DESCRIPTOR].orEmpty())
                        ?: ThemeDescriptor.WENMO_ZHUSHA
                } else {
                    val legacyRaw = prefs[Keys.LEGACY_THEME_PALETTE]
                    val palette =
                        runCatching {
                            if (legacyRaw.isNullOrBlank()) null else ThemePalette.valueOf(legacyRaw)
                        }.getOrNull()
                    if (palette != null) {
                        ThemeDescriptor.fromLegacyPalette(palette)
                    } else {
                        ThemeDescriptor.WENMO_ZHUSHA
                    }
                }

            context.settingsDataStore.edit { p ->
                if (!p.contains(Keys.THEME_DESCRIPTOR) ||
                    decodeThemeDescriptor(p[Keys.THEME_DESCRIPTOR].orEmpty()) == null
                ) {
                    p[Keys.THEME_DESCRIPTOR] = encodeThemeDescriptor(descriptor)
                }
                if (p.contains(Keys.LEGACY_THEME_PALETTE)) {
                    p.remove(Keys.LEGACY_THEME_PALETTE)
                }
            }
        }
    }

    private fun migrateLegacyFullSyncIfNeeded() {
        migrationScope.launch {
            val prefs = context.settingsDataStore.data.first()

            val hasLegacyState =
                prefs.contains(Keys.LEGACY_FULL_SYNC_STATUS) ||
                    prefs.contains(Keys.LEGACY_FULL_SYNC_RUN_ID) ||
                    prefs.contains(Keys.LEGACY_FULL_SYNC_LAST_SUCCESS_AT) ||
                    prefs.contains(Keys.LEGACY_FULL_SYNC_LAST_ERROR) ||
                    prefs.contains(Keys.LEGACY_FULL_SYNC_STAGE) ||
                    prefs.contains(Keys.LEGACY_FULL_SYNC_PAGES_FETCHED) ||
                    prefs.contains(Keys.LEGACY_FULL_SYNC_ITEMS_FETCHED) ||
                    prefs.contains(Keys.LEGACY_FULL_SYNC_KEY)

            if (!hasLegacyState) return@launch

            val legacySyncKey = prefs[Keys.LEGACY_FULL_SYNC_KEY].orEmpty().trim()
            val targetSyncKey =
                if (legacySyncKey.isNotBlank()) {
                    legacySyncKey
                } else {
                    // best-effort：若历史版本没写 full_sync_key，则退化为“按当前账号/服务器”迁移一次。
                    computeFullSyncKeyFromPrefs(prefs)
                }

            if (targetSyncKey.isBlank()) return@launch
            val targetKeys = fullSyncPreferenceKeys(targetSyncKey)

            context.settingsDataStore.edit { p ->
                val alreadyMigrated = targetKeys.hasAny(p)

                if (!alreadyMigrated) {
                    p[targetKeys.status] = p[Keys.LEGACY_FULL_SYNC_STATUS] ?: FullSyncStatus.IDLE.name
                    p[targetKeys.runId] = p[Keys.LEGACY_FULL_SYNC_RUN_ID].orEmpty()
                    p[targetKeys.lastSuccessAt] = p[Keys.LEGACY_FULL_SYNC_LAST_SUCCESS_AT] ?: 0L
                    p[targetKeys.lastError] = p[Keys.LEGACY_FULL_SYNC_LAST_ERROR].orEmpty()
                    p[targetKeys.stage] = p[Keys.LEGACY_FULL_SYNC_STAGE] ?: FullSyncStage.NORMAL.name
                    p[targetKeys.pagesFetched] = (p[Keys.LEGACY_FULL_SYNC_PAGES_FETCHED] ?: 0).coerceAtLeast(0)
                    p[targetKeys.itemsFetched] = (p[Keys.LEGACY_FULL_SYNC_ITEMS_FETCHED] ?: 0).coerceAtLeast(0)
                }

                // 清理旧 key：
                // - legacySyncKey 明确时，可安全清理；
                // - legacySyncKey 缺失时，只在“完成一次 best-effort 复制”后清理，避免误删其它账号的历史。
                val canCleanLegacy = legacySyncKey.isNotBlank() || !alreadyMigrated
                if (canCleanLegacy) {
                    p.remove(Keys.LEGACY_FULL_SYNC_STATUS)
                    p.remove(Keys.LEGACY_FULL_SYNC_RUN_ID)
                    p.remove(Keys.LEGACY_FULL_SYNC_LAST_SUCCESS_AT)
                    p.remove(Keys.LEGACY_FULL_SYNC_LAST_ERROR)
                    p.remove(Keys.LEGACY_FULL_SYNC_STAGE)
                    p.remove(Keys.LEGACY_FULL_SYNC_PAGES_FETCHED)
                    p.remove(Keys.LEGACY_FULL_SYNC_ITEMS_FETCHED)
                    p.remove(Keys.LEGACY_FULL_SYNC_KEY)
                }
            }
        }
    }

    private fun computeFullSyncKeyFromPrefs(prefs: Preferences): String {
        val serverUrl = prefs[Keys.SERVER_URL].orEmpty()
        val currentUserCreator = prefs[Keys.CURRENT_USER_CREATOR].orEmpty()
        val dev2Unlocked = prefs[Keys.DEV2_UNLOCKED] ?: false
        val token = encryptedTokenStorage.getToken().trim()

        // 与 settings 映射保持一致：只要已有 token，且未解锁 dev2，就强制使用默认服务器。
        val effectiveServerUrl =
            if (token.isBlank()) {
                serverUrl
            } else if (dev2Unlocked) {
                serverUrl
            } else {
                MemosUrls.DEFAULT_MEMOS_SERVER_URL
            }

        return buildFullSyncKey(serverUrl = effectiveServerUrl, currentUserCreator = currentUserCreator)
    }
}
