package cc.pscly.onememos.ui.feature.profile

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cc.pscly.onememos.ui.theme.OneMemosTheme
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
 * W360 热力图：卡内 7 个本月首行单元格 bounds 落在卡内且 x 轴互不重叠。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp-xxhdpi")
class ProfileHeatmapBoundsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun firstWeekCells_lieInsideCard_andAreXAxisDisjoint() {
        val zone = ZoneId.of("UTC")
        val month = YearMonth.of(2026, 1)
        val uiState =
            ProfileUiState(
                heatmap = buildHeatmapUiModel(memos = emptyList(), zoneId = zone, month = month),
                month = month,
                selection =
                    DateRangeSelection(
                        anchor = LocalDate.of(2026, 1, 1),
                        current = LocalDate.of(2026, 1, 1),
                    ),
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

        val cardBounds =
            composeRule
                .onNodeWithTag("profile_heatmap_card")
                .fetchSemanticsNode()
                .boundsInRoot

        val expectedCardWidthPx = with(composeRule.density) { 336.dp.toPx() }
        assertTrue(
            "W360 heatmap card width should be 336dp: bounds=$cardBounds",
            kotlin.math.abs(cardBounds.width - expectedCardWidthPx) <= 1f,
        )

        // 2026-01-01 是周四：首行本月格子为 1~4 日；再取 5~7 共 7 个连续本月日
        val dates =
            (1..7).map { day -> LocalDate.of(2026, 1, day) }

        val cellBounds =
            dates.map { date ->
                composeRule
                    .onNodeWithTag("heatmap_cell_$date", useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
            }

        cellBounds.forEachIndexed { index, bounds ->
            assertTrue(
                "cell[$index] left=${bounds.left} must be >= card left=${cardBounds.left}",
                bounds.left >= cardBounds.left - 1f,
            )
            assertTrue(
                "cell[$index] right=${bounds.right} must be <= card right=${cardBounds.right}",
                bounds.right <= cardBounds.right + 1f,
            )
        }

        val sorted = cellBounds.sortedBy { it.left }
        for (i in 0 until sorted.lastIndex) {
            assertTrue(
                "cells must be x-axis disjoint: " +
                    "cell$i.right=${sorted[i].right} > cell${i + 1}.left=${sorted[i + 1].left}",
                sorted[i].right <= sorted[i + 1].left + 0.5f,
            )
        }

        // 336/7 = 48dp：在 xxhdpi 下 48dp ≈ 144px；允许密度换算误差
        val expectedPx = with(composeRule.density) { 48.dp.toPx() }
        cellBounds.forEach { bounds ->
            val w = bounds.right - bounds.left
            assertTrue(
                "cell width $w should be ~48dp ($expectedPx px)",
                kotlin.math.abs(w - expectedPx) <= expectedPx * 0.08f,
            )
        }
    }
}
