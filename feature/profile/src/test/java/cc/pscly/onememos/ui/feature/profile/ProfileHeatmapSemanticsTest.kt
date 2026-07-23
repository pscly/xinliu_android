package cc.pscly.onememos.ui.feature.profile

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import cc.pscly.onememos.ui.theme.InkSpacing
import cc.pscly.onememos.ui.theme.OneMemosTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 热力单元格语义：OnClick 触发 onTapDate、contentDescription 格式、≥ TouchTargetMin。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp-xxhdpi")
class ProfileHeatmapSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun inMonthCell_semanticsOnClick_firesOnTapDate_andMeetsTouchTarget() {
        val zone = ZoneId.of("UTC")
        val month = YearMonth.of(2026, 3)
        val target = LocalDate.of(2026, 3, 15)
        val counts = mapOf(target to 3)
        val heatmap =
            buildHeatmapUiModel(memos = emptyList(), zoneId = zone, month = month).let {
                it.copy(counts = counts, maxCount = 3)
            }
        val uiState =
            ProfileUiState(
                heatmap = heatmap,
                month = month,
                selection = DateRangeSelection(anchor = target, current = target),
                sections = emptyList(),
                selectedMemoCount = 0,
            )

        val tapped = mutableListOf<LocalDate>()
        composeRule.setContent {
            OneMemosTheme {
                ProfileScreenContent(
                    uiState = uiState,
                    onOpenDrawer = {},
                    onOpenMemo = {},
                    onSetMonth = {},
                    onPrevMonth = {},
                    onNextMonth = {},
                    onGoToToday = {},
                    onSelectSingle = { tapped += it },
                    onStartRange = {},
                    onUpdateRange = {},
                )
            }
        }
        composeRule.waitForIdle()

        val tag = "heatmap_cell_$target"
        val node = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
        node.assertWidthIsAtLeast(InkSpacing.TouchTargetMin)
        node.assertHeightIsAtLeast(InkSpacing.TouchTargetMin)

        val config = node.fetchSemanticsNode().config
        val description = config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
        assertEquals("3月15日，3 篇，已选中", description)

        node.performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(listOf(target), tapped)
    }

    @Test
    fun unselectedCell_contentDescription_omitsSelectedSuffix() {
        val zone = ZoneId.of("UTC")
        val month = YearMonth.of(2026, 3)
        val selected = LocalDate.of(2026, 3, 1)
        val other = LocalDate.of(2026, 3, 10)
        val uiState =
            ProfileUiState(
                heatmap = buildHeatmapUiModel(memos = emptyList(), zoneId = zone, month = month),
                month = month,
                selection = DateRangeSelection(anchor = selected, current = selected),
                sections = emptyList(),
                selectedMemoCount = 0,
            )

        composeRule.setContent {
            OneMemosTheme {
                ProfileScreenContent(
                    uiState = uiState,
                    onOpenDrawer = {},
                    onOpenMemo = {},
                    onSetMonth = {},
                    onPrevMonth = {},
                    onNextMonth = {},
                    onGoToToday = {},
                    onSelectSingle = {},
                    onStartRange = {},
                    onUpdateRange = {},
                )
            }
        }
        composeRule.waitForIdle()

        val config =
            composeRule
                .onNodeWithTag("heatmap_cell_$other", useUnmergedTree = true)
                .fetchSemanticsNode()
                .config
        val description = config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
        assertEquals("3月10日，0 篇", description)
        assertTrue(description != null && !description.contains("已选中"))
    }
}
