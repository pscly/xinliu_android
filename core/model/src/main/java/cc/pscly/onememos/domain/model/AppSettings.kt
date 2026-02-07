package cc.pscly.onememos.domain.model

data class AppSettings(
    val serverUrl: String = "",
    val token: String = "",
    val loginMode: LoginMode = LoginMode.UNKNOWN,
    // 当前登录用户在 Memos 侧的资源名（例如 users/1）。用于“默认只看自己的历史”过滤。
    val currentUserCreator: String = "",
    // 开发者模式2（隐藏）：是否允许在 UI 中展示公开/工作区等“非私密”内容。
    // 默认策略：账号登录（BACKEND）下默认关闭（更“只看自己的”）；自定义登录默认保持兼容。
    val dev2ShowPublicWorkspaceMemos: Boolean = false,
    // 开发者模式2（隐藏）：是否已解锁（通过 10s 内点按版本号 6 次 + 密码）。
    val dev2Unlocked: Boolean = false,
    // 是否已完成首次启动引导。升级用户若已配置过 serverUrl/token，会在读取设置时自动视为已完成。
    val welcomeCompleted: Boolean = false,
    val themePalette: ThemePalette = ThemePalette.PAPER_INK,
    val themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    val defaultVisibility: MemoVisibility = MemoVisibility.PRIVATE,
    // 仅影响“搜索文本”的匹配方式；#标签筛选逻辑不受影响。
    val regexSearchEnabled: Boolean = false,
    // 标签筛选面板里是否展示每个标签的数量（例如：#读书 (12)）。
    val showTagCountsInFilter: Boolean = true,
    // 极速记录是否使用“悬浮窗”模式（不跳转当前应用界面）。需要系统授予“在其他应用上层显示”权限。
    val quickCaptureOverlayEnabled: Boolean = false,
    // 编辑页/悬浮极速记录：是否显示“一键插入当前时间”按钮（默认关闭）。
    val quickInsertTimeEnabled: Boolean = false,
    // “盖章”浮层停留时长（毫秒）；越大越慢，建议 500~800ms。
    val sealStampDurationMs: Int = 600,
    // 是否自动预取最近随笔的图片附件（用于离线浏览时不“空白”）。
    val offlineImagePrefetchEnabled: Boolean = true,
    // 预取范围：最近 N 条“随笔”（越大越离线，越小越省流量）。
    val offlineImagePrefetchMaxMemos: Int = 30,
    // 预取上限：最多缓存多少张图片（防止极端情况下下载过多）。
    val offlineImagePrefetchMaxImages: Int = 60,
    // 附件持久缓存上限（MB）。0 表示不限制（不推荐）。
    val attachmentCacheMaxMb: Int = 1024,

    // ----------------------------
    // Todo（待办）提醒：双模式
    // ----------------------------
    val todoReminderMode: TodoReminderMode = TodoReminderMode.SMART,

    // ----------------------------
    // 日历联动：Todo -> 系统日历
    // ----------------------------
    // 是否启用“自动写入系统日历”（需要用户授予 READ/WRITE_CALENDAR 权限，并选择一个可写日历）。
    val calendarIntegrationEnabled: Boolean = false,
    // 目标日历 id（CalendarContract.Calendars._ID）；null 表示尚未选择。
    val calendarIntegrationCalendarId: Long? = null,
    // 是否把 remindersJson（提前 X 分钟）同步到日历提醒（避免只写事件但不提醒）。
    val calendarIntegrationSyncReminders: Boolean = true,

    // ----------------------------
    // 同步（轻量状态）
    // ----------------------------
    val lastSync: LastSyncState = LastSyncState(),

    // ----------------------------
    // 全量同步（手动重同步）状态
    // ----------------------------
    val fullSync: FullSyncState = FullSyncState(),

    // ----------------------------
    // 开发者选项（内容渲染/调试开关）
    // ----------------------------
    // 用于隐藏“自动标签元数据行”（例如 n8n/LLM 写入的 __Atags 行）。支持多个关键字（逗号/空格/换行分隔）。
    val devAutoTagLineKeywords: String = "__Atags",
    // 是否在主页列表中显示该行（默认隐藏）。
    val devShowAutoTagLineInHome: Boolean = false,
    // 是否在查看页（只读）中显示该行（默认隐藏）。
    val devShowAutoTagLineInView: Boolean = false,
    // 是否在编辑页中显示该行（默认隐藏；保存时会自动保留该行内容）。
    val devShowAutoTagLineInEdit: Boolean = false,

    // 主页富预览“粘住”上限：0=关闭；默认 500；仅 benchmark/release 生效。
    val devHomeRichPreviewStickyLimit: Int = 500,
)
