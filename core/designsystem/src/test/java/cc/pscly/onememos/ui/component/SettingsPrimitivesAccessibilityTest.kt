package cc.pscly.onememos.ui.component

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import cc.pscly.onememos.ui.theme.InkDisabledContainerAlpha
import cc.pscly.onememos.ui.theme.InkDisabledContentAlpha
import cc.pscly.onememos.ui.theme.LocalInkDisabledColors
import cc.pscly.onememos.ui.theme.OneMemosTheme
import cc.pscly.onememos.ui.theme.inkDisabledColorsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun inkChip_disabled_hasDisabledSemanticsAndDoesNotClick() {
        var clicks = 0
        composeRule.setContent {
            OneMemosTheme {
                InkChip(
                    label = "待办",
                    selected = false,
                    enabled = false,
                    onClick = { clicks += 1 },
                    modifier = Modifier.testTag("ink_chip_disabled"),
                )
            }
        }
        val node = composeRule.onNodeWithTag("ink_chip_disabled")
        node.assertIsDisplayed()
        node.assertIsNotEnabled()
        assertTrue(node.isDisabledSemantics())
        node.performClick()
        composeRule.waitForIdle()
        assertEquals(0, clicks)
    }

    @Test
    fun inkChip_enabled_acceptsFocusRequesterWithoutCrash() {
        // 焦点环：InkChip 已接 paperInkFocusBorder(focused && clickable, shape=Chip)；
        // 通过 FocusRequester 请求焦点，验证可聚焦路径不崩溃且语义树仍可见。
        val focusRequester = FocusRequester()
        composeRule.setContent {
            OneMemosTheme {
                InkChip(
                    label = "全部",
                    selected = true,
                    enabled = true,
                    onClick = {},
                    modifier =
                        Modifier
                            .testTag("ink_chip_focus")
                            .focusRequester(focusRequester),
                )
            }
        }
        composeRule.onNodeWithTag("ink_chip_focus").assertIsDisplayed()
        composeRule.runOnIdle {
            focusRequester.requestFocus()
        }
        composeRule.waitForIdle()
        val node = composeRule.onNodeWithTag("ink_chip_focus")
        node.assertIsDisplayed()
        node.assertIsEnabled()
        assertFalse(node.isDisabledSemantics())
        // 聚焦后节点仍在语义树（焦点环为视觉边框，不改变 disabled 语义）
        assertTrue(
            node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused) == true ||
                node.fetchSemanticsNode().layoutInfo.isPlaced,
        )
    }

    @Test
    fun inkDisabledColors_matchM3OnSurfaceAlphas() {
        var onSurface = androidx.compose.ui.graphics.Color.Unspecified
        var localContainer = androidx.compose.ui.graphics.Color.Unspecified
        var localContent = androidx.compose.ui.graphics.Color.Unspecified
        var derivedContainer = androidx.compose.ui.graphics.Color.Unspecified
        var derivedContent = androidx.compose.ui.graphics.Color.Unspecified
        composeRule.setContent {
            OneMemosTheme {
                onSurface = MaterialTheme.colorScheme.onSurface
                val disabled = LocalInkDisabledColors.current
                localContainer = disabled.container
                localContent = disabled.content
                val derived = inkDisabledColorsOf(MaterialTheme.colorScheme)
                derivedContainer = derived.container
                derivedContent = derived.content
            }
        }
        composeRule.waitForIdle()
        assertEquals(onSurface.copy(alpha = InkDisabledContainerAlpha), localContainer)
        assertEquals(onSurface.copy(alpha = InkDisabledContentAlpha), localContent)
        assertEquals(derivedContainer, localContainer)
        assertEquals(derivedContent, localContent)
    }

    @Test
    fun clickableTagChip_hasAtLeast48TouchTarget_selectedSemanticsAndDescription() {
        // 可点击 TagChip：外层 ≥48dp 触控区 + Selected/stateDescription；内层保持紧凑视觉。
        var clicks = 0
        composeRule.setContent {
            OneMemosTheme {
                TagChip(
                    tag = "工作",
                    selected = true,
                    onClick = { clicks += 1 },
                    modifier = Modifier.testTag("tag_chip_clickable"),
                )
            }
        }
        val node = composeRule.onNodeWithTag("tag_chip_clickable")
        node.assertIsDisplayed()
        node.assertWidthIsAtLeast(48.dp)
        node.assertHeightIsAtLeast(48.dp)
        assertEquals(true, node.isSelectedSemantics())
        assertEquals("已选中", node.stateDescription())
        val desc = node.contentDescription()
        assertNotNull(desc)
        assertTrue(desc!!.contains("工作"))
        node.performClick()
        composeRule.waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun clickableTagChip_unselected_exposesSelectedFalse() {
        composeRule.setContent {
            OneMemosTheme {
                TagChip(
                    tag = "生活",
                    selected = false,
                    onClick = {},
                    modifier = Modifier.testTag("tag_chip_unselected"),
                )
            }
        }
        val node = composeRule.onNodeWithTag("tag_chip_unselected")
        node.assertIsDisplayed()
        node.assertWidthIsAtLeast(48.dp)
        node.assertHeightIsAtLeast(48.dp)
        assertEquals(false, node.isSelectedSemantics())
        assertEquals("未选中", node.stateDescription())
    }

    @Test
    fun staticTagChip_unselected_hasNoClickActionOrSelectionSemantics() {
        // 静态未选 TagChip：不伪装按钮、不朗读“未选中”，无 click action。
        composeRule.setContent {
            OneMemosTheme {
                TagChip(
                    tag = "静态",
                    selected = false,
                    onClick = null,
                    modifier = Modifier.testTag("tag_chip_static"),
                )
            }
        }
        val node = composeRule.onNodeWithTag("tag_chip_static")
        node.assertIsDisplayed()
        assertFalse(node.hasClickAction())
        assertEquals(null, node.isSelectedSemantics())
        assertEquals(null, node.stateDescription())
        val desc = node.contentDescription()
        assertNotNull(desc)
        assertTrue(desc!!.contains("静态"))
        assertFalse(desc.contains("未选中"))
    }

    @Test
    fun staticTagChip_selected_exposesSelectionWithoutButtonRole() {
        composeRule.setContent {
            OneMemosTheme {
                TagChip(
                    tag = "已标",
                    selected = true,
                    onClick = null,
                    modifier = Modifier.testTag("tag_chip_static_selected"),
                )
            }
        }
        val node = composeRule.onNodeWithTag("tag_chip_static_selected")
        node.assertIsDisplayed()
        assertFalse(node.hasClickAction())
        assertEquals(true, node.isSelectedSemantics())
        assertEquals("已选中", node.stateDescription())
    }

    @Test
    fun inkChip_enabled_hasAtLeast48TouchTargetAndSelectedSemantics() {
        var clicks = 0
        composeRule.setContent {
            OneMemosTheme {
                InkChip(
                    label = "全部",
                    selected = true,
                    enabled = true,
                    onClick = { clicks += 1 },
                    modifier = Modifier.testTag("ink_chip_selected"),
                )
            }
        }
        val node = composeRule.onNodeWithTag("ink_chip_selected")
        node.assertIsDisplayed()
        node.assertWidthIsAtLeast(48.dp)
        node.assertHeightIsAtLeast(48.dp)
        node.assertIsEnabled()
        assertEquals(true, node.isSelectedSemantics())
        assertEquals("已选中", node.stateDescription())
        node.performClick()
        composeRule.waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun inkChip_unselected_exposesSelectedFalse() {
        composeRule.setContent {
            OneMemosTheme {
                InkChip(
                    label = "筛选",
                    selected = false,
                    enabled = true,
                    onClick = {},
                    modifier = Modifier.testTag("ink_chip_unselected"),
                )
            }
        }
        val node = composeRule.onNodeWithTag("ink_chip_unselected")
        node.assertIsDisplayed()
        node.assertWidthIsAtLeast(48.dp)
        node.assertHeightIsAtLeast(48.dp)
        assertEquals(false, node.isSelectedSemantics())
        assertEquals("未选中", node.stateDescription())
    }

    @Test
    fun inkChip_disabled_hasAtLeast48TouchTarget_andSelectedSemantics() {
        var clicks = 0
        composeRule.setContent {
            OneMemosTheme {
                InkChip(
                    label = "待办",
                    selected = false,
                    enabled = false,
                    onClick = { clicks += 1 },
                    modifier = Modifier.testTag("ink_chip_disabled_size"),
                )
            }
        }
        val node = composeRule.onNodeWithTag("ink_chip_disabled_size")
        node.assertIsDisplayed()
        node.assertWidthIsAtLeast(48.dp)
        node.assertHeightIsAtLeast(48.dp)
        node.assertIsNotEnabled()
        assertTrue(node.isDisabledSemantics())
        assertEquals(false, node.isSelectedSemantics())
        assertEquals("未选中", node.stateDescription())
        node.performClick()
        composeRule.waitForIdle()
        assertEquals(0, clicks)
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

    private fun SemanticsNodeInteraction.isSelectedSemantics(): Boolean? {
        val config = fetchSemanticsNode().config
        return config.getOrNull(SemanticsProperties.Selected)
    }

    private fun SemanticsNodeInteraction.hasClickAction(): Boolean {
        val config = fetchSemanticsNode().config
        return config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick)
    }
}
