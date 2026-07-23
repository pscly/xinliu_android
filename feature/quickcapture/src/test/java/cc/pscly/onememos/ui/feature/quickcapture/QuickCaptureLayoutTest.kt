package cc.pscly.onememos.ui.feature.quickcapture

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import cc.pscly.onememos.ui.theme.OneMemosTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * QuickCaptureScreen 布局回归：
 * - 小视口 (360dp × 250dp) + 大字体 (fontScale=2f) 时，
 *   保存按钮（"盖"）可通过滚动到达且可见。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class QuickCaptureLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val readyUiState = QuickCaptureUiState(
        content = TextFieldValue("测试"),
        isSaving = false,
        error = null,
        quickInsertTimeEnabled = true,
    )

    @Test
    fun smallViewportAndLargeFont_saveButtonReachableByScroll() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                OneMemosTheme {
                    Box(modifier = Modifier.requiredSize(DpSize(360.dp, 250.dp))) {
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
            }
        }

        val saveButton = composeRule.onNodeWithContentDescription("盖")
        saveButton.performScrollTo()
        saveButton.assertExists()
    }
}
