package cc.pscly.onememos.ui.feature.settings

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cc.pscly.onememos.domain.model.CacheStats
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.QuickInsertTimeFormat
import cc.pscly.onememos.domain.model.ThemeDescriptor
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.domain.model.TodoReminderMode
import cc.pscly.onememos.domain.settings.AboutAdvancedSettingsSnapshot
import cc.pscly.onememos.domain.settings.AccountSyncHealth
import cc.pscly.onememos.domain.settings.AccountSyncSettingsSnapshot
import cc.pscly.onememos.domain.settings.AppearanceInteractionSettingsSnapshot
import cc.pscly.onememos.domain.settings.CalendarPermissionState
import cc.pscly.onememos.domain.settings.CalendarSummary
import cc.pscly.onememos.domain.settings.DeveloperOptions
import cc.pscly.onememos.domain.settings.RecordEditingSettingsSnapshot
import cc.pscly.onememos.domain.settings.ReminderCalendarSettingsSnapshot
import cc.pscly.onememos.domain.settings.SectionSummaryState
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.settings.SettingsHubSnapshot
import cc.pscly.onememos.domain.settings.StorageOfflineSettingsSnapshot
import cc.pscly.onememos.domain.settings.SummaryFact
import cc.pscly.onememos.domain.settings.SummaryIssue
import cc.pscly.onememos.domain.settings.SummaryIssueKind
import cc.pscly.onememos.domain.settings.UpdateSettingsPhase
import cc.pscly.onememos.domain.settings.UpdateSettingsSnapshot
import cc.pscly.onememos.ui.accessibility.ReducedMotion
import cc.pscly.onememos.ui.feature.settings.about.AboutAdvancedContent
import cc.pscly.onememos.ui.feature.settings.about.AboutAdvancedContentCallbacks
import cc.pscly.onememos.ui.feature.settings.about.AboutAdvancedUiState
import cc.pscly.onememos.ui.feature.settings.about.AboutAnnouncementKind
import cc.pscly.onememos.ui.feature.settings.account.AccountSyncContent
import cc.pscly.onememos.ui.feature.settings.appearance.AppearanceInteractionContent
import cc.pscly.onememos.ui.feature.settings.appearance.AppearanceInteractionUiState
import cc.pscly.onememos.ui.feature.settings.hub.SettingsHubContent
import cc.pscly.onememos.ui.feature.settings.record.RecordEditingContent
import cc.pscly.onememos.ui.feature.settings.record.RecordEditingUiState
import cc.pscly.onememos.ui.feature.settings.reminder.ReminderCalendarContent
import cc.pscly.onememos.ui.feature.settings.reminder.ReminderCalendarUiState
import cc.pscly.onememos.ui.feature.settings.storage.StorageOfflineContent
import cc.pscly.onememos.ui.feature.settings.storage.StorageOfflineUiState
import cc.pscly.onememos.ui.theme.OneMemosTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings 可访问性矩阵：Hub 与六个能力页的触控目标、语义顺序、状态描述、
 * live region、大字体与 reduced-motion 契约。
 *
 * Robolectric 宿主固定尺寸时，部分节点可能不在可见裁切区；矩阵以语义树 + 触控高度为准，
 * 显示性与设备矩阵见 androidTest 与 QA 文档。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsAccessibilityMatrixTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val hubRowTags =
        listOf(
            "settings_hub_row_account",
            "settings_hub_row_record",
            "settings_hub_row_reminder",
            "settings_hub_row_storage",
            "settings_hub_row_appearance",
            "settings_hub_row_about",
        )

    private val accountOrderTags =
        listOf(
            "settings_account_header",
            "settings_account_health",
            "settings_account_primary",
            "settings_account_last_success",
            "settings_account_summary",
            "settings_account_management",
            "settings_account_advanced",
        )

    @Test
    fun hub_sixRows_order1to6_minTouch48_andStateDescription() {
        setHub(readyHubSnapshot())

        hubRowTags.forEachIndexed { index, tag ->
            val node = composeRule.onNodeWithTag(tag)
            node.performScrollTo()
            composeRule.waitForIdle()
            node.assertIsDisplayed()
            node.assertHeightIsAtLeast(48.dp)
            node.assertWidthIsAtLeast(48.dp)
            assertEquals(
                (index + 1).toString(),
                composeRule.onNodeWithTag("${tag}_index", useUnmergedTree = true).textOrNull(),
            )
            assertNotNull("Hub 行 $tag 必须有 stateDescription", node.stateDescription())
            assertNotNull("Hub 行 $tag 必须有 contentDescription", node.contentDescription())
        }
        assertTrue(
            composeRule.onAllNodesWithText("进入", useUnmergedTree = true).fetchSemanticsNodes().size >= 6,
        )
        // issue 在 InkCard 合并语义树中；用未合并树断言存在（与 SettingsHubScreenTest 一致）
        composeRule
            .onNodeWithTag("settings_hub_row_account_issue", useUnmergedTree = true)
            .assertExists()
        composeRule.onNodeWithText("登录已失效", useUnmergedTree = true).assertExists()
    }

    @Test
    fun accountPage_fixedOrder_primaryAndNavTargetsMin48_healthIsLiveRegion() {
        setAccount(accountHealthySnapshot())

        val ordered =
            composeRule
                .onAllNodes(
                    SemanticsMatcher("账号页固定语义顺序") { node ->
                        node.config.getOrNull(SemanticsProperties.TestTag) in accountOrderTags
                    },
                ).fetchSemanticsNodes()
                .map { it.config.getOrNull(SemanticsProperties.TestTag) }
        assertEquals(accountOrderTags, ordered)

        listOf(
            "settings_account_back",
            "settings_account_primary",
            "settings_account_management",
            "settings_account_advanced",
        ).forEach { tag ->
            val node = composeRule.onNodeWithTag(tag)
            node.performScrollTo()
            composeRule.waitForIdle()
            node.assertHeightIsAtLeast(48.dp)
        }

        val health = composeRule.onNodeWithTag("settings_account_health")
        assertNotNull(health.stateDescription())
        assertTrue("健康卡必须是 live region", health.isLiveRegion())
        assertNotNull(composeRule.onNodeWithTag("settings_account_back").contentDescription())
    }

    @Test
    fun recordPage_interactiveTargetsMin48_andErrorIsLiveRegion() {
        setRecord(
            RecordEditingUiState(
                loading = false,
                snapshot =
                    RecordEditingSettingsSnapshot(
                        defaultVisibility = MemoVisibility.PRIVATE,
                        regexSearchEnabled = true,
                        showTagCounts = true,
                        quickInsertTimeEnabled = true,
                        quickInsertTimeFormat = QuickInsertTimeFormat.FULL_DATETIME,
                    ),
                persistentError = SettingsCapabilityError.StorageFailure,
            ),
        )

        listOf(
            "settings_record_back",
            "settings_record_visibility_private",
            "settings_record_visibility_protected",
            "settings_record_visibility_public",
            "settings_record_regex",
            "settings_record_tag_counts",
            "settings_record_quick_insert",
            "settings_record_format_full",
            "settings_record_format_time",
        ).forEach { tag ->
            val node = composeRule.onNodeWithTag(tag)
            node.performScrollTo()
            composeRule.waitForIdle()
            node.assertHeightIsAtLeast(48.dp)
        }

        val error = composeRule.onNodeWithTag("settings_record_error")
        error.performScrollTo()
        composeRule.waitForIdle()
        error.assertExists()
        assertTrue(error.isLiveRegion())
        assertNotNull(error.stateDescription())
    }

    @Test
    fun reminderPage_writeTargetsMin48_permissionAndResultLive() {
        setReminder(
            ReminderCalendarUiState(
                loading = false,
                snapshot =
                    ReminderCalendarSettingsSnapshot(
                        reminderMode = TodoReminderMode.SMART,
                        calendarEnabled = true,
                        selectedCalendar = CalendarSummary(7L, "工作"),
                        syncCalendarReminders = true,
                        permission = CalendarPermissionState.GRANTED,
                        writableCalendars =
                            listOf(
                                CalendarSummary(7L, "工作"),
                                CalendarSummary(9L, "生活"),
                            ),
                    ),
                persistentError = SettingsCapabilityError.PermissionDenied,
                notice = null,
            ),
        )

        listOf(
            "settings_reminder_mode_smart",
            "settings_reminder_mode_exact",
            "settings_reminder_calendar_enabled",
            "settings_reminder_calendar_7",
            "settings_reminder_calendar_9",
            "settings_reminder_clear_calendar",
            "settings_reminder_sync_reminders",
            "settings_reminder_reschedule",
        ).forEach { tag ->
            val node = composeRule.onNodeWithTag(tag)
            node.performScrollTo()
            composeRule.waitForIdle()
            node.assertHeightIsAtLeast(48.dp)
        }

        val error = composeRule.onNodeWithTag("settings_reminder_error")
        error.performScrollTo()
        composeRule.waitForIdle()
        error.assertExists()
        assertTrue(error.isLiveRegion())

        val permission = composeRule.onNodeWithTag("settings_reminder_permission_status")
        permission.performScrollTo()
        composeRule.waitForIdle()
        assertNotNull(permission.stateDescription())
        assertTrue(permission.isLiveRegion())
    }

    @Test
    fun storagePage_cleanupAndPrefetchTargetsMin48_rootHasStateDescription() {
        setStorage(readyStorageState())

        listOf(
            "settings_storage_refresh",
            "settings_storage_prefetch_switch",
            "settings_storage_clear_images",
            "settings_storage_clear_attachments",
            "settings_storage_clear_all",
        ).forEach { tag ->
            val node = composeRule.onNodeWithTag(tag)
            node.performScrollTo()
            composeRule.waitForIdle()
            node.assertHeightIsAtLeast(48.dp)
        }

        assertNotNull(composeRule.onNodeWithTag("settings_storage_root").stateDescription())
    }

    @Test
    fun appearancePage_optionsMin48_overlayState_andReducedMotionKeepsText() {
        var reduceMotion by mutableStateOf(false)
        composeRule.setContent {
            CompositionLocalProvider(ReducedMotion.Local provides reduceMotion) {
                OneMemosTheme {
                    AppearanceInteractionContent(
                        uiState = readyAppearanceState(),
                        onIntent = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        listOf(
            "settings_appearance_preset_wenmo_zhusha",
            "settings_appearance_preset_qingjian_yuebai",
            "settings_appearance_preset_yehang_dailan",
            "settings_appearance_preset_saibo_fluor",
            "settings_appearance_mode_follow_system",
            "settings_appearance_mode_light",
            "settings_appearance_mode_dark",
            "settings_appearance_palette_paper_ink",
            "settings_appearance_palette_indigo",
            "settings_appearance_palette_cyber",
            "settings_appearance_palette_moon_white",
            "settings_appearance_texture_scroll",
            "settings_appearance_density_standard",
            "settings_appearance_font_wenkai",
            "settings_appearance_overlay",
            "settings_appearance_duration_slider",
        ).forEach { tag ->
            val node = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
            node.performScrollTo()
            composeRule.waitForIdle()
            node.assertHeightIsAtLeast(48.dp)
        }
        assertEquals(
            "已关闭",
            composeRule
                .onNodeWithTag("settings_appearance_overlay", useUnmergedTree = true)
                .stateDescription(),
        )

        reduceMotion = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText("外观与交互", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("settings_appearance_root").assertExists()
        assertNotNull(composeRule.onNodeWithTag("settings_appearance_root").stateDescription())
    }

    @Test
    fun aboutPage_actionsMin48_resultAnnouncerIsLiveRegion() {
        setAbout(
            AboutAdvancedUiState(
                loading = false,
                snapshot = aboutSnapshot(),
                announcementKind = AboutAnnouncementKind.SUCCESS,
                announcementToken = 1L,
            ),
        )

        listOf(
            "settings_about_check_update",
            "settings_about_export",
            "settings_about_quick_tile",
            "settings_about_quick_open",
            "settings_about_screenshot_tile",
            "settings_about_screenshot_open",
            "settings_about_rebuild",
        ).forEach { tag ->
            val node = composeRule.onNodeWithTag(tag)
            node.performScrollTo()
            composeRule.waitForIdle()
            node.assertHeightIsAtLeast(48.dp)
        }

        val announcer =
            composeRule.onNodeWithTag("settings_about_result_announcer", useUnmergedTree = true)
        announcer.assertExists()
        assertTrue(announcer.isLiveRegion())
    }

    @Test
    fun largeFont_hubAndAccount_keepKeySemantics() {
        var surface by mutableStateOf(LargeFontSurface.HUB)
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                OneMemosTheme {
                    when (surface) {
                        LargeFontSurface.HUB ->
                            SettingsHubContent(
                                snapshot = readyHubSnapshot(),
                                onOpen = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                        LargeFontSurface.ACCOUNT ->
                            AccountSyncContent(
                                snapshot = accountHealthySnapshot(),
                                onBack = {},
                                onOpenLogin = {},
                                onSyncNow = {},
                                onOpenAccountManagement = {},
                                onOpenAdvancedSync = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        hubRowTags.forEach { tag ->
            assertTrue(
                "大字体下 Hub 行 $tag 语义树仍存在",
                composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty(),
            )
        }

        surface = LargeFontSurface.ACCOUNT
        composeRule.waitForIdle()
        accountOrderTags.forEach { tag ->
            assertTrue(
                "大字体下账号页 $tag 语义树仍存在",
                composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }

    @Test
    fun pureIconButtons_haveActionContentDescription() {
        var surface by mutableStateOf(IconSurface.ACCOUNT)
        composeRule.setContent {
            OneMemosTheme {
                when (surface) {
                    IconSurface.ACCOUNT ->
                        AccountSyncContent(
                            snapshot = accountHealthySnapshot(),
                            onBack = {},
                            onOpenLogin = {},
                            onSyncNow = {},
                            onOpenAccountManagement = {},
                            onOpenAdvancedSync = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                    IconSurface.RECORD ->
                        RecordEditingContent(
                            uiState =
                                RecordEditingUiState(
                                    loading = false,
                                    snapshot =
                                        RecordEditingSettingsSnapshot(
                                            defaultVisibility = MemoVisibility.PRIVATE,
                                            regexSearchEnabled = false,
                                            showTagCounts = true,
                                            quickInsertTimeEnabled = false,
                                            quickInsertTimeFormat = QuickInsertTimeFormat.FULL_DATETIME,
                                        ),
                                ),
                            onBack = {},
                            onSubmit = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                }
            }
        }
        composeRule.waitForIdle()

        val accountBack = composeRule.onNodeWithTag("settings_account_back")
        val accountDesc = accountBack.contentDescription()
        assertNotNull(accountDesc)
        assertFalse(accountDesc.isNullOrBlank())

        surface = IconSurface.RECORD
        composeRule.waitForIdle()
        val recordBack = composeRule.onNodeWithTag("settings_record_back")
        assertFalse(recordBack.contentDescription().isNullOrBlank())
    }

    @Test
    fun largeFont_allCapabilityPages_keepRootOrKeySemantics() {
        var surface by mutableStateOf(LargeFontAllSurface.STORAGE)
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                OneMemosTheme {
                    when (surface) {
                        LargeFontAllSurface.STORAGE ->
                            StorageOfflineContent(
                                uiState = readyStorageState(),
                                confirmation = null,
                                onAction = {},
                                onDismissConfirmation = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                        LargeFontAllSurface.APPEARANCE ->
                            AppearanceInteractionContent(
                                uiState = readyAppearanceState(),
                                onIntent = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                        LargeFontAllSurface.REMINDER ->
                            ReminderCalendarContent(
                                uiState =
                                    ReminderCalendarUiState(
                                        loading = false,
                                        snapshot =
                                            ReminderCalendarSettingsSnapshot(
                                                reminderMode = TodoReminderMode.SMART,
                                                calendarEnabled = false,
                                                selectedCalendar = null,
                                                syncCalendarReminders = false,
                                                permission = CalendarPermissionState.UNKNOWN,
                                                writableCalendars = emptyList(),
                                            ),
                                    ),
                                onIntent = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                        LargeFontAllSurface.ABOUT ->
                            AboutAdvancedContent(
                                state =
                                    AboutAdvancedUiState(
                                        loading = false,
                                        snapshot = aboutSnapshot(),
                                    ),
                                callbacks = noopAboutCallbacks(),
                                showRebuildConfirm = false,
                                modifier = Modifier.fillMaxSize(),
                            )
                        LargeFontAllSurface.RECORD ->
                            RecordEditingContent(
                                uiState =
                                    RecordEditingUiState(
                                        loading = false,
                                        snapshot =
                                            RecordEditingSettingsSnapshot(
                                                defaultVisibility = MemoVisibility.PRIVATE,
                                                regexSearchEnabled = false,
                                                showTagCounts = true,
                                                quickInsertTimeEnabled = false,
                                                quickInsertTimeFormat = QuickInsertTimeFormat.FULL_DATETIME,
                                            ),
                                    ),
                                onBack = {},
                                onSubmit = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        fun assertTagExists(tag: String) {
            assertTrue(
                "大字体下 $tag 语义树仍存在",
                composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty(),
            )
        }

        assertTagExists("settings_storage_root")
        surface = LargeFontAllSurface.APPEARANCE
        composeRule.waitForIdle()
        assertTagExists("settings_appearance_root")
        surface = LargeFontAllSurface.REMINDER
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithTag("settings_reminder_mode_smart").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("提醒", substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
        surface = LargeFontAllSurface.ABOUT
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithTag("settings_about_check_update").fetchSemanticsNodes().isNotEmpty(),
        )
        surface = LargeFontAllSurface.RECORD
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithTag("settings_record_back").fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun paperInkFocusTokens_andHubTouchTargetContract_remainGreen() {
        // 矩阵门禁：纸墨焦点环令牌与 Hub 48dp 一并守护，防止 M3.2 回归
        assertEquals(
            cc.pscly.onememos.ui.theme.InkBorder.Hairline,
            cc.pscly.onememos.ui.accessibility.PaperInkFocusIndicator.StrokeWidth,
        )
        assertEquals(
            cc.pscly.onememos.ui.theme.InkBorder.Stamp,
            cc.pscly.onememos.ui.accessibility.PaperInkFocusIndicator.EmphasizedStrokeWidth,
        )
        setHub(readyHubSnapshot())
        hubRowTags.forEach { tag ->
            val node = composeRule.onNodeWithTag(tag)
            node.performScrollTo()
            composeRule.waitForIdle()
            node.assertHeightIsAtLeast(48.dp)
            node.assertWidthIsAtLeast(48.dp)
        }
    }

    private enum class LargeFontSurface {
        HUB,
        ACCOUNT,
    }

    private enum class LargeFontAllSurface {
        STORAGE,
        APPEARANCE,
        REMINDER,
        ABOUT,
        RECORD,
    }

    private enum class IconSurface {
        ACCOUNT,
        RECORD,
    }

    private fun setHub(snapshot: SettingsHubSnapshot) {
        composeRule.setContent {
            OneMemosTheme {
                SettingsHubContent(
                    snapshot = snapshot,
                    onOpen = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun setAccount(snapshot: AccountSyncSettingsSnapshot) {
        composeRule.setContent {
            OneMemosTheme {
                AccountSyncContent(
                    snapshot = snapshot,
                    onBack = {},
                    onOpenLogin = {},
                    onSyncNow = {},
                    onOpenAccountManagement = {},
                    onOpenAdvancedSync = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun setRecord(uiState: RecordEditingUiState) {
        composeRule.setContent {
            OneMemosTheme {
                RecordEditingContent(
                    uiState = uiState,
                    onBack = {},
                    onSubmit = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun setReminder(uiState: ReminderCalendarUiState) {
        composeRule.setContent {
            OneMemosTheme {
                ReminderCalendarContent(
                    uiState = uiState,
                    onIntent = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun setStorage(uiState: StorageOfflineUiState) {
        composeRule.setContent {
            OneMemosTheme {
                StorageOfflineContent(
                    uiState = uiState,
                    confirmation = null,
                    onAction = {},
                    onDismissConfirmation = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun setAbout(state: AboutAdvancedUiState) {
        composeRule.setContent {
            OneMemosTheme {
                AboutAdvancedContent(
                    state = state,
                    callbacks = noopAboutCallbacks(),
                    showRebuildConfirm = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun readyHubSnapshot() =
        SettingsHubSnapshot(
            accountSync =
                SectionSummaryState.Ready(
                    primary = SummaryFact("ACCOUNT_HEALTHY"),
                    secondary = SummaryFact("LAST_SUCCESS_AT_1"),
                    issue = SummaryIssue(SummaryIssueKind.AUTHENTICATION_EXPIRED),
                ),
            recordEditing =
                SectionSummaryState.Ready(
                    primary = SummaryFact("VISIBILITY_PRIVATE"),
                    secondary = SummaryFact("REGEX_ON"),
                ),
            reminderCalendar =
                SectionSummaryState.Ready(
                    primary = SummaryFact("REMINDER_SMART"),
                    secondary = SummaryFact("CALENDAR_OFF"),
                ),
            storageOffline =
                SectionSummaryState.Ready(
                    primary = SummaryFact("PREFETCH_OFF"),
                    secondary = SummaryFact("CACHE_LIMIT_MB_256"),
                ),
            appearanceInteraction =
                SectionSummaryState.Ready(
                    primary = SummaryFact("THEME_PAPER_INK"),
                    secondary = SummaryFact("MODE_FOLLOW_SYSTEM"),
                ),
            aboutAdvanced =
                SectionSummaryState.Ready(
                    primary = SummaryFact("VERSION_1.2.3"),
                    secondary = SummaryFact("UPDATE_IDLE"),
                ),
        )

    private fun accountHealthySnapshot() =
        AccountSyncSettingsSnapshot(
            health = AccountSyncHealth.Healthy(lastSuccessAtEpochMs = 100L),
            accountLabel = "已连接账号",
            lastSuccessAtEpochMs = 100L,
            commandInFlight = null,
        )

    private fun readyStorageState() =
        StorageOfflineUiState(
            loading = false,
            snapshot =
                StorageOfflineSettingsSnapshot(
                    imagePrefetchEnabled = true,
                    prefetchMemoLimit = 12,
                    prefetchImageLimit = 36,
                    attachmentCacheLimitMb = 256,
                    cacheStats =
                        CacheStats(
                            databaseBytes = 1_024,
                            imageCacheBytes = 2_048,
                            attachmentCacheBytes = 3_072,
                            otherCacheBytes = 4_096,
                        ),
                ),
        )

    private fun readyAppearanceState() =
        AppearanceInteractionUiState(
            loading = false,
            snapshot =
                AppearanceInteractionSettingsSnapshot(
                    themeDescriptor = ThemeDescriptor.WENMO_ZHUSHA,
                    themeMode = ThemeMode.FOLLOW_SYSTEM,
                    quickCaptureOverlayEnabled = false,
                    sealStampDurationMs = 600,
                    commandInFlight = null,
                ),
        )

    private fun aboutSnapshot() =
        AboutAdvancedSettingsSnapshot(
            versionName = "2.1.0",
            versionCode = 210L,
            buildType = "debug",
            update = UpdateSettingsSnapshot(phase = UpdateSettingsPhase.IDLE),
            diagnosticsAvailable = true,
            attachmentUploadLimitMb = 50,
            developerOptions =
                DeveloperOptions(
                    unlocked = false,
                    showPublicWorkspaceMemos = false,
                    autoTagLineKeywords = "",
                    showAutoTagLineInHome = false,
                    showAutoTagLineInView = false,
                    showAutoTagLineInEdit = false,
                    homeRichPreviewStickyLimit = 0,
                ),
            commandInFlight = null,
        )

    private fun noopAboutCallbacks() =
        AboutAdvancedContentCallbacks(
            onCheckForUpdates = {},
            onDownloadUpdate = {},
            onInstallUpdate = {},
            onClearIgnoredUpdate = {},
            onExportDiagnostics = {},
            onRequestQuickCaptureTile = {},
            onRequestScreenshotTile = {},
            onOpenQuickCapture = {},
            onOpenScreenshotCapture = {},
            onRequestRebuildDerivedFields = {},
            onConfirmRebuildDerivedFields = {},
            onDismissRebuildConfirm = {},
            onSetAttachmentUploadLimitMb = {},
            onSetDeveloperOptions = {},
        )

    private fun SemanticsNodeInteraction.contentDescription(): String? {
        val config = fetchSemanticsNode().config
        return config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
    }

    private fun SemanticsNodeInteraction.stateDescription(): String? {
        val config = fetchSemanticsNode().config
        return config.getOrNull(SemanticsProperties.StateDescription)
    }

    private fun SemanticsNodeInteraction.textOrNull(): String? {
        val config = fetchSemanticsNode().config
        return config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
    }

    private fun SemanticsNodeInteraction.isLiveRegion(): Boolean {
        val config = fetchSemanticsNode().config
        return config.contains(SemanticsProperties.LiveRegion)
    }
}
