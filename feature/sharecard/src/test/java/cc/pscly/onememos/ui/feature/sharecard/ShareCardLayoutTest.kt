package cc.pscly.onememos.ui.feature.sharecard

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import cc.pscly.onememos.ui.theme.OneMemosTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ShareCard 布局回归：
 * 小视口 (360dp × 250dp) + 大字体时，Styles 比例 FlowRow 必须换行，
 * More 面板末尾可通过滚动到达且可见。
 *
 * 注意：h250 下预览区会把 ratio chips 挤出 fold，必须先 scroll 再读 bounds，
 * 否则 boundsInRoot 会是无效的 [0,0,0]。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ShareCardLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val fixture =
        ShareCardUiState(
            content = "测试内容",
            createdAt = 1_784_678_400_000L,
            theme = ShareCardTheme.SU_LV,
            ratio = ShareCardRatio.AUTO,
            fontSize = ShareCardFontSize.MEDIUM,
            align = ShareCardAlign.LEFT,
            longMode = true,
            longExportMode = ShareCardLongExportMode.SINGLE,
        )

    @Test
    @Config(qualifiers = "w360dp-h250dp-xxhdpi")
    fun flowRow_ratios_wrap_on_narrow_screen() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                OneMemosTheme {
                    ShareCardScreenContent(
                        uiState = fixture,
                        selectedTabIndex = 1,
                        onTabSelected = {},
                        onBack = {},
                        onSaveToGallery = {},
                        onShare = {},
                        onThemeSelected = {},
                        onRatioSelected = {},
                        onFontSizeSelected = {},
                        onAlignSelected = {},
                        onLongModeChanged = {},
                        onLongExportModeChanged = {},
                        onAuthorNameChanged = {},
                        onQrEnabledChanged = {},
                        onQrTextChanged = {},
                    )
                }
            }
        }

        // 预览区高度 > 视口：先把 ratio FlowRow 滚入可见区，再读有效 bounds
        composeTestRule
            .onNodeWithTag("flow_row_ratios", useUnmergedTree = true)
            .performScrollTo()
        composeTestRule.waitForIdle()

        val firstTop =
            composeTestRule
                .onNodeWithTag("chip_ratio_AUTO", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
                .top
        val thirdTop =
            composeTestRule
                .onNodeWithTag("chip_ratio_STORY_9_16", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
                .top

        assertTrue(
            "ratio FlowRow must wrap: firstTop=$firstTop thirdTop=$thirdTop",
            firstTop > 0f && thirdTop > firstTop,
        )
    }

    @Test
    @Config(qualifiers = "w360dp-h250dp-xxhdpi")
    fun morePanel_scrolls_into_view() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                OneMemosTheme {
                    ShareCardScreenContent(
                        uiState = fixture,
                        selectedTabIndex = 2,
                        onTabSelected = {},
                        onBack = {},
                        onSaveToGallery = {},
                        onShare = {},
                        onThemeSelected = {},
                        onRatioSelected = {},
                        onFontSizeSelected = {},
                        onAlignSelected = {},
                        onLongModeChanged = {},
                        onLongExportModeChanged = {},
                        onAuthorNameChanged = {},
                        onQrEnabledChanged = {},
                        onQrTextChanged = {},
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithTag("more_panel")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
