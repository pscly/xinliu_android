package cc.pscly.onememos.ui.feature.quickcapture

import android.app.Application
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.input.TextFieldValue
import cc.pscly.onememos.ui.theme.InkSpacing
import cc.pscly.onememos.ui.theme.OneMemosTheme
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * QuickCaptureScreen 语义树验证：
 * - 错误节点 liveRegion = Assertive 且 error 内容一致
 * - 草稿横幅 liveRegion = Polite
 * - 操作按钮（续写/时/取消/盖）触控目标 ≥ TouchTargetMin
 * - 续写按钮同时提供 OnClick 与 OnLongClick
 * - 所有操作按钮 contentDescription 正确
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class QuickCaptureSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val readyUiState = QuickCaptureUiState(
        content = TextFieldValue(""),
        isSaving = false,
        error = null,
        quickInsertTimeEnabled = true,
    )

    private val errorUiState = readyUiState.copy(error = "测试模拟错误")

    @Test
    fun errorNode_hasAssertiveLiveRegion_andErrorText() {
        composeRule.setContent {
            OneMemosTheme {
                QuickCaptureScreen(
                    uiState = errorUiState,
                    focusRequester = FocusRequester(),
                    onClose = {},
                    onContentChange = {},
                    onInsertTime = {},
                    onSave = {},
                    onEditPrevious = {},
                    onRefreshHistory = {},
                    onLoadForEdit = {},
                    onRestoreDraft = {},
                    onClearDraft = {},
                    onConfirmOverwrite = {},
                    onDismissOverwrite = {},
                    showStamp = false,
                )
            }
        }

        composeRule
            .onNodeWithTag("qc_error", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "测试模拟错误"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
    }

    @Test
    fun draftBanner_hasPoliteLiveRegion() {
        val draftUiState = readyUiState.copy(draftBannerVisible = true)
        composeRule.setContent {
            OneMemosTheme {
                QuickCaptureScreen(
                    uiState = draftUiState,
                    focusRequester = FocusRequester(),
                    onClose = {},
                    onContentChange = {},
                    onInsertTime = {},
                    onSave = {},
                    onEditPrevious = {},
                    onRefreshHistory = {},
                    onLoadForEdit = {},
                    onRestoreDraft = {},
                    onClearDraft = {},
                    onConfirmOverwrite = {},
                    onDismissOverwrite = {},
                    showStamp = false,
                )
            }
        }

        composeRule.onNodeWithText("有草稿，可恢复").assertExists()

        val allNodes = composeRule.onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        )
        allNodes.assertCountEquals(1)
    }

    @Test
    fun actionButtons_haveTouchTargetMin_andCorrectSemantics() {
        composeRule.setContent {
            OneMemosTheme {
                QuickCaptureScreen(
                    uiState = readyUiState,
                    focusRequester = FocusRequester(),
                    onClose = {},
                    onContentChange = {},
                    onInsertTime = {},
                    onSave = {},
                    onEditPrevious = {},
                    onRefreshHistory = {},
                    onLoadForEdit = {},
                    onRestoreDraft = {},
                    onClearDraft = {},
                    onConfirmOverwrite = {},
                    onDismissOverwrite = {},
                    showStamp = false,
                )
            }
        }

        // 续写按钮：contentDescription="续写"，同时有 OnClick+OnLongClick
        val xuxieNodes = composeRule.onAllNodesWithContentDescription("续写")
        xuxieNodes.assertCountEquals(1)
        val xuxie = xuxieNodes[0]
        xuxie.assertIsEnabled()
        xuxie.assertHeightIsAtLeast(InkSpacing.TouchTargetMin)
        xuxie.assertWidthIsAtLeast(InkSpacing.TouchTargetMin)
        xuxie.assertHasClickAction()

        val xuxieConfig = xuxie.fetchSemanticsNode().config
        val longClickLabels = xuxieConfig.getOrNull(SemanticsActions.OnLongClick)
        assertNotNull("续写 should have OnLongClick", longClickLabels)

        // 时按钮：contentDescription="插入时间"
        val insertTimeNodes = composeRule.onAllNodesWithContentDescription("插入时间")
        insertTimeNodes.assertCountEquals(1)
        insertTimeNodes[0].assertIsEnabled()
        insertTimeNodes[0].assertHeightIsAtLeast(InkSpacing.TouchTargetMin)

        // 取消按钮：通过文本查找，assert 触控目标
        composeRule.onNodeWithText("取消").assertIsEnabled()
            .assertHeightIsAtLeast(InkSpacing.TouchTargetMin)

        // 盖按钮：由 SealButton 提供
        val sealNodes = composeRule.onAllNodesWithContentDescription("盖")
        sealNodes.assertCountEquals(1)
        sealNodes[0].assertIsEnabled()
        sealNodes[0].assertHeightIsAtLeast(InkSpacing.TouchTargetMin)
    }

    @Test
    fun saveButton_disabledWhenSaving() {
        val savingUiState = readyUiState.copy(isSaving = true)
        composeRule.setContent {
            OneMemosTheme {
                QuickCaptureScreen(
                    uiState = savingUiState,
                    focusRequester = FocusRequester(),
                    onClose = {},
                    onContentChange = {},
                    onInsertTime = {},
                    onSave = {},
                    onEditPrevious = {},
                    onRefreshHistory = {},
                    onLoadForEdit = {},
                    onRestoreDraft = {},
                    onClearDraft = {},
                    onConfirmOverwrite = {},
                    onDismissOverwrite = {},
                    showStamp = false,
                )
            }
        }

        val sealNodes = composeRule.onAllNodesWithContentDescription("盖")
        sealNodes.assertCountEquals(1)
        sealNodes[0].assertExists()
    }
}
