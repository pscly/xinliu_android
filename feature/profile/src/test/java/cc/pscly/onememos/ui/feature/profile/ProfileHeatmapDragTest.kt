package cc.pscly.onememos.ui.feature.profile

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
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
 * 热力图拖动多选：pointerInput 路径在超过 touchSlop 后更新范围。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp-xxhdpi")
class ProfileHeatmapDragTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun swipeAcrossCells_preservesMultiDateDragSelection() {
        val zone = ZoneId.of("UTC")
        val month = YearMonth.of(2026, 1)
        val heatmap = buildHeatmapUiModel(memos = emptyList(), zoneId = zone, month = month)
        // 同一行相邻两日：1 月 5 日(周一)→1 月 7 日(周三)
        val startDate = LocalDate.of(2026, 1, 5)
        val endDate = LocalDate.of(2026, 1, 7)

        val dragStarts = mutableListOf<LocalDate>()
        val dragUpdates = mutableListOf<LocalDate>()

        composeRule.setContent {
            OneMemosTheme {
                var selection by remember {
                    mutableStateOf(DateRangeSelection(anchor = startDate, current = startDate))
                }
                HeatmapGrid(
                    model = heatmap,
                    selection = selection,
                    onTapDate = { selection = DateRangeSelection(anchor = it, current = it) },
                    onDragStart = { date ->
                        dragStarts += date
                        selection = DateRangeSelection(anchor = date, current = date)
                    },
                    onDragUpdate = { date ->
                        dragUpdates += date
                        selection = selection.copy(current = date)
                    },
                )
            }
        }
        composeRule.waitForIdle()

        val startBounds =
            composeRule
                .onNodeWithTag("heatmap_cell_$startDate", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val endBounds =
            composeRule
                .onNodeWithTag("heatmap_cell_$endDate", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot

        val startX = (startBounds.left + startBounds.right) / 2f
        val startY = (startBounds.top + startBounds.bottom) / 2f
        val endX = (endBounds.left + endBounds.right) / 2f
        val endY = (endBounds.top + endBounds.bottom) / 2f

        composeRule
            .onNodeWithTag("profile_heatmap_grid")
            .performTouchInput {
                // 从格子中心扫到同行另一格，超过 touchSlop 进入拖动
                swipe(
                    start = androidx.compose.ui.geometry.Offset(startX, startY),
                    end = androidx.compose.ui.geometry.Offset(endX, endY),
                    durationMillis = 400,
                )
            }
        composeRule.waitForIdle()

        assertTrue(
            "drag start should fire (got $dragStarts)",
            dragStarts.isNotEmpty(),
        )
        assertTrue(
            "drag update should reach end date $endDate (updates=$dragUpdates)",
            dragUpdates.contains(endDate) ||
                (dragUpdates.isNotEmpty() && dragUpdates.last() >= endDate.minusDays(1)),
        )
        // 多日：至少有 start 与另一日
        val allTouched = (dragStarts + dragUpdates).toSet()
        assertTrue(
            "multi-date drag should touch >1 day, touched=$allTouched",
            allTouched.size >= 2 ||
                (dragStarts.isNotEmpty() && dragUpdates.isNotEmpty() &&
                    dragUpdates.any { it != dragStarts.first() }),
        )
    }
}
