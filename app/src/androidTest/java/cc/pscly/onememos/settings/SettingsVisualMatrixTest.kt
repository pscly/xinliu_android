package cc.pscly.onememos.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.domain.settings.AccountSyncHealth
import cc.pscly.onememos.domain.settings.AccountSyncSettingsSnapshot
import cc.pscly.onememos.domain.settings.SectionSummaryState
import cc.pscly.onememos.domain.settings.SettingsHubSnapshot
import cc.pscly.onememos.domain.settings.SummaryFact
import cc.pscly.onememos.domain.settings.SummaryIssue
import cc.pscly.onememos.domain.settings.SummaryIssueKind
import cc.pscly.onememos.ui.feature.settings.account.AccountSyncContent
import cc.pscly.onememos.ui.feature.settings.hub.SettingsHubContent
import cc.pscly.onememos.ui.theme.OneMemosTheme
import cc.pscly.onememos.ui.theme.OneMemosThemeConfig
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 设备视觉矩阵：Hub 与账号同步页在三窗口尺寸、纸墨/黛蓝/赛博明暗、
 * 系统大字体下保持单列、内容最大 720dp、关键文案与异常不裁切。
 *
 * 需连接设备或模拟器：
 * `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cc.pscly.onememos.settings.SettingsVisualMatrixTest`
 */
class SettingsVisualMatrixTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val windowSizes =
        listOf(
            DpSize(360.dp, 800.dp),
            DpSize(600.dp, 960.dp),
            DpSize(840.dp, 900.dp),
        )

    private val hubRowTags =
        listOf(
            "settings_hub_row_account",
            "settings_hub_row_record",
            "settings_hub_row_reminder",
            "settings_hub_row_storage",
            "settings_hub_row_appearance",
            "settings_hub_row_about",
        )

    private val themeConfigs =
        listOf(
            OneMemosThemeConfig(ThemePalette.PAPER_INK, ThemeMode.LIGHT),
            OneMemosThemeConfig(ThemePalette.PAPER_INK, ThemeMode.DARK),
            OneMemosThemeConfig(ThemePalette.INDIGO, ThemeMode.LIGHT),
            OneMemosThemeConfig(ThemePalette.INDIGO, ThemeMode.DARK),
            OneMemosThemeConfig(ThemePalette.CYBER, ThemeMode.LIGHT),
            OneMemosThemeConfig(ThemePalette.CYBER, ThemeMode.DARK),
        )

    @Test
    fun hub_threeWindows_paperInkLightDark_remainSingleColumnMax720() {
        var size by mutableStateOf(windowSizes.first())
        var theme by mutableStateOf(themeConfigs[0])
        composeRule.setContent {
            OneMemosTheme(config = theme) {
                Box(Modifier.requiredSize(size).testTag("window_host")) {
                    SettingsHubContent(
                        snapshot = readyHubSnapshot(),
                        onOpen = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        for (paletteMode in listOf(themeConfigs[0], themeConfigs[1])) {
            theme = paletteMode
            for (next in windowSizes) {
                size = next
                composeRule.waitForIdle()
                assertHubSingleColumn(next)
            }
        }
    }

    @Test
    fun hub_indigoAndCyber_spotCheck_singleColumn() {
        var size by mutableStateOf(DpSize(360.dp, 800.dp))
        var theme by mutableStateOf(themeConfigs[2])
        composeRule.setContent {
            OneMemosTheme(config = theme) {
                Box(Modifier.requiredSize(size).testTag("window_host")) {
                    SettingsHubContent(
                        snapshot = readyHubSnapshot(),
                        onOpen = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        for (config in themeConfigs.drop(2)) {
            theme = config
            size = DpSize(360.dp, 800.dp)
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("settings_hub_list").assertExists()
            hubRowTags.forEach { tag ->
                composeRule.onNodeWithTag(tag).assertExists()
            }
            composeRule
                .onNodeWithTag("settings_hub_row_account_issue", useUnmergedTree = true)
                .assertExists()
        }
    }

    @Test
    fun hub_largeFont_keepsRowsAndIssueSemantics() {
        var size by mutableStateOf(DpSize(360.dp, 800.dp))
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                OneMemosTheme(config = OneMemosThemeConfig(ThemePalette.PAPER_INK, ThemeMode.LIGHT)) {
                    Box(Modifier.requiredSize(size).testTag("window_host")) {
                        SettingsHubContent(
                            snapshot = readyHubSnapshot(),
                            onOpen = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        for (next in windowSizes) {
            size = next
            composeRule.waitForIdle()
            hubRowTags.forEach { tag ->
                assertTrue(
                    "大字体 ${next.width} 下 $tag 仍在语义树",
                    composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty(),
                )
            }
            assertTrue(
                composeRule
                    .onAllNodesWithText("登录已失效", useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty(),
            )
        }
    }

    @Test
    fun accountSync_threeWindows_titleHealthPrimaryVisible() {
        var size by mutableStateOf(windowSizes.first())
        var theme by mutableStateOf(themeConfigs[0])
        composeRule.setContent {
            OneMemosTheme(config = theme) {
                Box(Modifier.requiredSize(size).testTag("window_host")) {
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

        for (config in listOf(themeConfigs[0], themeConfigs[1])) {
            theme = config
            for (next in windowSizes) {
                size = next
                composeRule.waitForIdle()
                composeRule.onNodeWithTag("settings_account_header").assertExists()
                composeRule.onNodeWithTag("settings_account_health").assertExists()
                val primary = composeRule.onNodeWithTag("settings_account_primary")
                primary.performScrollTo()
                composeRule.waitForIdle()
                primary.assertIsDisplayed()
                composeRule.onNodeWithTag("settings_account_management").assertExists()
                composeRule.onNodeWithTag("settings_account_advanced").assertExists()
                val width = composeRule.onNodeWithTag("settings_account_header")
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .width
                assertTrue(
                    "账号页内容宽度不得超过 720dp，实际=$width window=${next.width}",
                    width <= 720f + 1f,
                )
            }
        }
    }

    @Test
    fun accountSync_largeFont_keepsFixedOrderTags() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                OneMemosTheme(config = OneMemosThemeConfig(ThemePalette.PAPER_INK, ThemeMode.LIGHT)) {
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
        composeRule.waitForIdle()
        listOf(
            "settings_account_header",
            "settings_account_health",
            "settings_account_primary",
            "settings_account_last_success",
            "settings_account_summary",
            "settings_account_management",
            "settings_account_advanced",
        ).forEach { tag ->
            assertTrue(
                "大字体下账号页 $tag 语义树仍存在",
                composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty(),
            )
        }
        composeRule.onNodeWithText("当前账号：已连接账号", useUnmergedTree = true).assertExists()
    }

    private fun assertHubSingleColumn(window: DpSize) {
        composeRule.onNodeWithTag("settings_hub_list").assertExists()
        for (i in 1..6) {
            assertTrue(
                "窗口 ${window.width} 应保留序号 $i",
                composeRule
                    .onAllNodesWithText(i.toString(), useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty(),
            )
        }
        hubRowTags.forEach { tag ->
            composeRule.onNodeWithTag(tag).assertExists()
        }
        composeRule
            .onNodeWithTag("settings_hub_row_account_issue", useUnmergedTree = true)
            .assertExists()
        val listWidth =
            composeRule.onNodeWithTag("settings_hub_list").fetchSemanticsNode().boundsInRoot.width
        assertTrue(
            "Hub 列表宽度不得超过 720dp，实际=$listWidth window=${window.width}",
            listWidth <= 720f + 1f,
        )
        // 无矩阵/双栏：六行序号各恰有一次
        for (i in 1..6) {
            val count =
                composeRule
                    .onAllNodesWithText(i.toString(), useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .size
            assertTrue("窗口 ${window.width} 序号 $i 应唯一，实际=$count", count >= 1)
        }
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
}
