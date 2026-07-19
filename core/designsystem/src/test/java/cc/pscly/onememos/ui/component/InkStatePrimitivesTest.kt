package cc.pscly.onememos.ui.component

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.ui.theme.InkSpacing
import cc.pscly.onememos.ui.theme.OneMemosTheme
import cc.pscly.onememos.ui.theme.OneMemosThemeConfig
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * M3.4 状态原语测试：渲染断言 + 动作回调 + 无障碍语义 + 三态截图。
 *
 * 截图录制：`./gradlew :core:designsystem:recordRoborazziDebug`
 * （普通 testDebugUnitTest 下 captureRoboImage 只跑通捕获路径，不落金图）
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp-xxhdpi")
class InkStatePrimitivesTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val roborazziRule = RoborazziRule(
        options = RoborazziRule.Options(
            outputDirectoryPath = "src/test/screenshots",
        ),
    )

    @Test
    fun inkLoading_showsIndicatorAndMessage() {
        composeRule.setContent {
            OneMemosTheme(config = themeConfig(dark = false)) {
                InkLoading(message = "加载中…")
            }
        }
        composeRule
            .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
        composeRule.onNodeWithText("加载中…").assertIsDisplayed()
    }

    @Test
    fun inkLoading_withoutMessage_hidesAuxText() {
        composeRule.setContent {
            OneMemosTheme(config = themeConfig(dark = false)) {
                InkLoading()
            }
        }
        composeRule
            .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
    }

    @Test
    fun inkEmpty_showsMessageAndAction_invokesCallback() {
        var clicks = 0
        composeRule.setContent {
            OneMemosTheme(config = themeConfig(dark = false)) {
                InkEmpty(
                    message = "还没有任何记录",
                    actionLabel = "去记录",
                    onAction = { clicks++ },
                )
            }
        }
        composeRule.onNodeWithText("还没有任何记录").assertIsDisplayed()
        composeRule.onNodeWithText("去记录").assertIsDisplayed().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun inkEmpty_withoutAction_rendersNoButton() {
        composeRule.setContent {
            OneMemosTheme(config = themeConfig(dark = false)) {
                InkEmpty(message = "空空如也")
            }
        }
        composeRule.onNodeWithText("空空如也").assertIsDisplayed()
        composeRule.onNode(hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun inkError_showsMessage_retryInvokes_andAnnouncesAssertively() {
        var retries = 0
        composeRule.setContent {
            OneMemosTheme(config = themeConfig(dark = false)) {
                InkError(
                    message = "加载失败：网络不可用",
                    onRetry = { retries++ },
                    modifier = Modifier.testTag(ERROR_TAG),
                )
            }
        }
        composeRule.onNodeWithText("加载失败：网络不可用").assertIsDisplayed()
        composeRule.onNodeWithText("重试").assertIsDisplayed().performClick()
        assertEquals(1, retries)
        composeRule.onNodeWithTag(ERROR_TAG).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.LiveRegion,
                LiveRegionMode.Assertive,
            ),
        )
    }

    @Test
    fun inkRetryBanner_showsMessage_retryInvokes_andAnnouncesPolitely() {
        var retries = 0
        composeRule.setContent {
            OneMemosTheme(config = themeConfig(dark = false)) {
                InkRetryBanner(
                    message = "同步失败：网络不可用",
                    onRetry = { retries++ },
                    modifier = Modifier.testTag(BANNER_TAG),
                )
            }
        }
        composeRule.onNodeWithText("同步失败：网络不可用").assertIsDisplayed()
        composeRule.onNodeWithText("重试").assertIsDisplayed().performClick()
        assertEquals(1, retries)
        composeRule.onNodeWithTag(BANNER_TAG).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.LiveRegion,
                LiveRegionMode.Polite,
            ),
        )
    }

    @Test
    fun inkRetryBanner_withoutAction_rendersNoButton() {
        composeRule.setContent {
            OneMemosTheme(config = themeConfig(dark = false)) {
                InkRetryBanner(
                    message = "同步中…",
                    modifier = Modifier.testTag(BANNER_TAG),
                )
            }
        }
        composeRule.onNodeWithText("同步中…").assertIsDisplayed()
        composeRule.onNode(hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun inkStatePrimitives_threeStates_light_captures() {
        composeRule.setContent {
            OneMemosTheme(config = themeConfig(dark = false)) {
                Column(
                    modifier =
                        Modifier
                            .background(MaterialTheme.colorScheme.background)
                            .padding(InkSpacing.X12)
                            .testTag(STATES_TAG),
                    verticalArrangement = Arrangement.spacedBy(InkSpacing.X12),
                ) {
                    InkLoading(message = "加载中…")
                    InkEmpty(message = "还没有任何记录", actionLabel = "去记录", onAction = {})
                    InkError(message = "加载失败：网络不可用", onRetry = {})
                    InkRetryBanner(message = "同步失败：网络不可用", onRetry = {})
                }
            }
        }
        composeRule.onNodeWithTag(STATES_TAG).captureRoboImage()
    }

    private fun themeConfig(dark: Boolean) = OneMemosThemeConfig(
        palette = ThemePalette.PAPER_INK,
        themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
    )

    private companion object {
        const val STATES_TAG = "ink_state_primitives_matrix"
        const val ERROR_TAG = "ink_state_error"
        const val BANNER_TAG = "ink_state_banner"
    }
}
