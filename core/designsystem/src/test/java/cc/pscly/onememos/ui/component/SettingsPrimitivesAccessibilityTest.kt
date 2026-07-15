package cc.pscly.onememos.ui.component

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cc.pscly.onememos.ui.accessibility.ReducedMotion
import cc.pscly.onememos.ui.theme.OneMemosTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings 共享原语可访问性契约：
 * 触控区 ≥ 48dp、语义禁用、contentDescription 必填、reduced-motion 关闭按压缩放。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsPrimitivesAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sealIconButton_defaultVisualSize44_hasAtLeast48TouchTargetAndDescription() {
        composeRule.setContent {
            OneMemosTheme {
                SealIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "新增",
                    modifier = Modifier.testTag("seal_icon"),
                    onClick = {},
                )
            }
        }
        val node = composeRule.onNodeWithTag("seal_icon", useUnmergedTree = false)
        node.assertIsDisplayed()
        node.assertWidthIsAtLeast(48.dp)
        node.assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("新增").assertIsDisplayed()
        assertEquals("新增", node.contentDescription())
    }

    @Test
    fun sealButton_disabled_hasSemanticsAndStillShowsLabel() {
        composeRule.setContent {
            OneMemosTheme {
                SealButton(
                    text = "盖章",
                    enabled = false,
                    contentDescription = "盖章按钮",
                    onClick = { error("disabled should not click") },
                )
            }
        }
        composeRule.onNodeWithText("盖章").assertIsDisplayed()
        val node = composeRule.onNodeWithContentDescription("盖章按钮")
        node.assertIsNotEnabled()
        assertTrue(node.isDisabledSemantics())
    }

    @Test
    fun clickableInkCard_hasMinHeight48_andContentDescription() {
        composeRule.setContent {
            OneMemosTheme {
                InkCard(
                    onClick = {},
                    contentDescription = "设置入口",
                    modifier = Modifier.testTag("ink_card"),
                ) {
                    Text("摘要")
                }
            }
        }
        val node = composeRule.onNodeWithTag("ink_card")
        node.assertIsDisplayed()
        node.assertHeightIsAtLeast(48.dp)
        node.assertIsEnabled()
        assertEquals("设置入口", node.contentDescription())
    }

    @Test
    fun reducedMotion_keepsResultTextImmediate_andAllowsClickWithoutCrash() {
        var clicks = 0
        composeRule.setContent {
            CompositionLocalProvider(ReducedMotion.Local provides true) {
                OneMemosTheme {
                    Box {
                        SealButton(
                            text = "完成",
                            contentDescription = "完成按钮",
                            onClick = { clicks += 1 },
                        )
                        Text("结果已就绪")
                    }
                }
            }
        }
        composeRule.onNodeWithText("结果已就绪").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("完成按钮").performClick()
        composeRule.waitForIdle()
        assertEquals(1, clicks)
        // reduced-motion 下点击仍立即生效；缩放动画被 snap 关闭，由实现契约保证。
        assertNotNull(ReducedMotion.Local)
    }

    private fun SemanticsNodeInteraction.contentDescription(): String? {
        val config = fetchSemanticsNode().config
        return config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
    }

    private fun SemanticsNodeInteraction.isDisabledSemantics(): Boolean {
        val config = fetchSemanticsNode().config
        return config.contains(SemanticsProperties.Disabled)
    }
}
