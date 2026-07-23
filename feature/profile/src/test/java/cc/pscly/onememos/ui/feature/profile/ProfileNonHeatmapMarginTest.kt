package cc.pscly.onememos.ui.feature.profile

import android.app.Application
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import cc.pscly.onememos.ui.theme.InkSpacing
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
 * 非热力图卡片水平外边距 ≥ X16（相对屏宽 360）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp-xxhdpi")
class ProfileNonHeatmapMarginTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectionAndEmptyCards_haveAtLeastX16HorizontalMargin() {
        val zone = ZoneId.of("UTC")
        val month = YearMonth.of(2026, 2)
        val uiState =
            ProfileUiState(
                heatmap = buildHeatmapUiModel(memos = emptyList(), zoneId = zone, month = month),
                month = month,
                selection =
                    DateRangeSelection(
                        anchor = LocalDate.of(2026, 2, 1),
                        current = LocalDate.of(2026, 2, 1),
                    ),
                sections = emptyList(),
                selectedMemoCount = 0,
            )

        var densityValue = 1f
        composeRule.setContent {
            densityValue = LocalDensity.current.density
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

        val minMarginPx = InkSpacing.X16.value * densityValue
        val screenWidthPx = 360f * densityValue

        listOf("profile_selection_card", "profile_empty_card").forEach { tag ->
            val bounds =
                composeRule
                    .onNodeWithTag(tag)
                    .fetchSemanticsNode()
                    .boundsInRoot
            assertTrue(
                "$tag left margin ${bounds.left} must be >= X16 ($minMarginPx px)",
                bounds.left + 0.5f >= minMarginPx,
            )
            val rightMargin = screenWidthPx - bounds.right
            assertTrue(
                "$tag right margin $rightMargin must be >= X16 ($minMarginPx px)",
                rightMargin + 0.5f >= minMarginPx,
            )
        }

        // 热力图卡外边距为 X12（更窄），对照：left 应 < selection 的 left
        val heatmapLeft =
            composeRule
                .onNodeWithTag("profile_heatmap_card")
                .fetchSemanticsNode()
                .boundsInRoot
                .left
        val selectionLeft =
            composeRule
                .onNodeWithTag("profile_selection_card")
                .fetchSemanticsNode()
                .boundsInRoot
                .left
        assertTrue(
            "heatmap X12 margin ($heatmapLeft) should be less than selection X16 ($selectionLeft)",
            heatmapLeft < selectionLeft - 1f,
        )
    }
}
