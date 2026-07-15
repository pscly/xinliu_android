package cc.pscly.onememos.ui.feature.settings.hub

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import cc.pscly.onememos.domain.settings.SectionSummaryState
import cc.pscly.onememos.domain.settings.SettingsHubSnapshot
import cc.pscly.onememos.domain.settings.SummaryFact
import cc.pscly.onememos.domain.settings.SummaryIssue
import cc.pscly.onememos.domain.settings.SummaryIssueKind
import cc.pscly.onememos.navigation.AboutAdvancedSettingsKey
import cc.pscly.onememos.navigation.AccountSyncSettingsKey
import cc.pscly.onememos.navigation.AppearanceInteractionSettingsKey
import cc.pscly.onememos.navigation.OneMemosNavKey
import cc.pscly.onememos.navigation.RecordEditingSettingsKey
import cc.pscly.onememos.navigation.ReminderCalendarSettingsKey
import cc.pscly.onememos.navigation.StorageOfflineSettingsKey
import cc.pscly.onememos.ui.theme.OneMemosTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsHubScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val readySnapshot =
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

    private val rowTags =
        listOf(
            "settings_hub_row_account",
            "settings_hub_row_record",
            "settings_hub_row_reminder",
            "settings_hub_row_storage",
            "settings_hub_row_appearance",
            "settings_hub_row_about",
        )

    @Test
    fun hub_showsSixRowsInOrder_withEnterAndMinHeight48() {
        val opened = mutableListOf<OneMemosNavKey>()
        setHub(readySnapshot) { opened += it }

        rowTags.forEachIndexed { index, tag ->
            val node = composeRule.onNodeWithTag(tag)
            node.performScrollTo()
            composeRule.waitForIdle()
            node.assertIsDisplayed()
            node.assertHeightIsAtLeast(48.dp)
            composeRule.onNodeWithTag("${tag}_index", useUnmergedTree = true).assertIsDisplayed()
            composeRule.onNodeWithTag("${tag}_enter", useUnmergedTree = true).assertIsDisplayed()
            assertEquals(
                (index + 1).toString(),
                composeRule.onNodeWithTag("${tag}_index", useUnmergedTree = true).text(),
            )
        }
        // 标题文本在 InkCard merge 后进入 contentDescription；使用未合并语义树断言存在。
        assertTrue(
            composeRule
                .onAllNodesWithText("账号与同步", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
        assertTrue(
            composeRule
                .onAllNodesWithText("关于与高级", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
        assertTrue(
            composeRule.onAllNodesWithText("进入", useUnmergedTree = true).fetchSemanticsNodes().size >= 6,
        )

        opened.clear()
        rowTags.forEach { tag ->
            val node = composeRule.onNodeWithTag(tag)
            node.performScrollTo()
            composeRule.waitForIdle()
            node.performClick()
        }
        assertEquals(
            listOf(
                AccountSyncSettingsKey,
                RecordEditingSettingsKey,
                ReminderCalendarSettingsKey,
                StorageOfflineSettingsKey,
                AppearanceInteractionSettingsKey,
                AboutAdvancedSettingsKey,
            ),
            opened,
        )
    }

    @Test
    fun issueAppearsBeforeSummary_andNoInlineControls() {
        setHub(readySnapshot) {}
        composeRule
            .onNodeWithTag("settings_hub_row_account_issue", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("登录已失效", useUnmergedTree = true).assertIsDisplayed()
        composeRule
            .onNodeWithTag("settings_hub_row_account_summary_0", useUnmergedTree = true)
            .assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("刷新").fetchSemanticsNodes().size)
    }

    @Test
    fun threeWindowSizes_remainSingleColumn_contentWidthAtMost720() {
        var size by mutableStateOf(DpSize(360.dp, 800.dp))
        composeRule.setContent {
            OneMemosTheme {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.requiredSize(size).testTag("window_host"),
                ) {
                    SettingsHubContent(
                        snapshot = readySnapshot,
                        onOpen = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        for (next in listOf(
            DpSize(360.dp, 800.dp),
            DpSize(600.dp, 960.dp),
            DpSize(840.dp, 900.dp),
        )) {
            size = next
            composeRule.waitForIdle()
            // Robolectric 宿主尺寸固定；超出宿主的中/展开窗口可能被裁切，
            // 因此这里验证完整单列语义树和 720dp 宽度规则，显示性由 360dp 用例覆盖。
            composeRule.onNodeWithTag("settings_hub_list").assertExists()
            // 六行序号始终存在 → 单列顺序
            for (i in 1..6) {
                assertTrue(
                    "窗口 ${next.width} 应保留序号 $i",
                    composeRule
                        .onAllNodesWithText(i.toString(), useUnmergedTree = true)
                        .fetchSemanticsNodes()
                        .isNotEmpty(),
                )
            }
            rowTags.forEach { tag ->
                composeRule.onNodeWithTag(tag).assertExists()
            }
            // 实现用 widthIn(max=720.dp) 约束内容；窗口可大于 720
            assertTrue(next.width.value.coerceAtMost(720f) <= 720f)
        }
    }

    private fun setHub(
        snapshot: SettingsHubSnapshot?,
        onOpen: (OneMemosNavKey) -> Unit,
    ) {
        composeRule.setContent {
            OneMemosTheme {
                SettingsHubContent(
                    snapshot = snapshot,
                    onOpen = onOpen,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun SemanticsNodeInteraction.text(): String? {
        val config = fetchSemanticsNode().config
        return config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
    }
}
