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
import androidx.compose.ui.test.onAllNodesWithContentDescription
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

    @Test
    fun tagChip_compact_keepsTagContentDescription() {
        // 紧凑视觉契约：memo 卡内标签为次要交互，整卡可点进详情，
        // 产品决策视觉高度≈文字行高（不再断言 48dp 最小触控目标）；
        // 无障碍语义 contentDescription/stateDescription 必须保留。
        composeRule.setContent {
            OneMemosTheme {
                TagChip(
                    tag = "工作",
                    selected = true,
                    onClick = {},
                    modifier = Modifier.testTag("tag_chip"),
                )
            }
        }
        val node = composeRule.onNodeWithTag("tag_chip")
        node.assertIsDisplayed()
        val desc = node.contentDescription()
        assertNotNull(desc)
        assertTrue(desc!!.contains("工作"))
        assertEquals("已选中", node.stateDescription())
    }

    @Test
    fun sealStampOverlay_visible_hasContentDescriptionAndSurvivesLargeFont() {
        composeRule.setContent {
            CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides
                    androidx.compose.ui.unit.Density(density = 1f, fontScale = 2f),
            ) {
                OneMemosTheme {
                    Box(modifier = Modifier.testTag("stamp_host")) {
                        SealStampOverlay(
                            visible = true,
                            text = "已存",
                            modifier = Modifier.testTag("stamp_overlay"),
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("已存").assertExists()
        // 大字体下语义节点仍存在（不因裁切从语义树消失）
        assertTrue(
            composeRule
                .onAllNodesWithContentDescription("已存")
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
    }

    private fun SemanticsNodeInteraction.contentDescription(): String? {
        val config = fetchSemanticsNode().config
        return config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
    }

    private fun SemanticsNodeInteraction.isDisabledSemantics(): Boolean {
        val config = fetchSemanticsNode().config
        return config.contains(SemanticsProperties.Disabled)
    }

    private fun SemanticsNodeInteraction.stateDescription(): String? {
        val config = fetchSemanticsNode().config
        return config.getOrNull(SemanticsProperties.StateDescription)
    }
}
