package cc.pscly.onememos.ui.feature.todo

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.ui.theme.InkSpacing
import cc.pscly.onememos.ui.theme.OneMemosTheme
import cc.pscly.onememos.ui.theme.OneMemosThemeConfig
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 验证 TodoDoneMark 的无障碍语义：外层 Box 唯一交互节点 + 内层 clearAndSetSemantics。
 */

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TodoCompletionSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `one-off done true - Role Checkbox ToggleableState On stateDescription 已完成 height ge TouchTargetMin exactly 1 OnClick`() {
        composeRule.setContent {
            OneMemosTheme(config = themeConfig(dark = false)) {
                TodoDoneMark(isDone = true, isRecurring = false, onClick = {})
            }
        }

        // onNode 要求精确匹配 1 个节点 = 自动验证"唯一交互节点"
        val node =
            composeRule.onNode(
                SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On),
            )
        node.assertIsDisplayed()
        node.assertHasClickAction()
        node.assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已完成"),
        )
        node.assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox),
        )
        node.assertHeightIsAtLeast(InkSpacing.TouchTargetMin)
    }

    @Test
    fun `recurring done false - Role Button contentDescription 完成下次循环任务`() {
        composeRule.setContent {
            OneMemosTheme(config = themeConfig(dark = false)) {
                TodoDoneMark(isDone = false, isRecurring = true, onClick = {})
            }
        }

        // onNode 要求精确匹配 1 个节点 = 自动验证"唯一交互节点"
        val node = composeRule.onNode(hasContentDescription("完成下次循环任务"))
        node.assertIsDisplayed()
        node.assertHasClickAction()
        node.assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
        )
    }

    private fun themeConfig(dark: Boolean) =
        OneMemosThemeConfig(
            palette = ThemePalette.PAPER_INK,
            themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
        )
}
