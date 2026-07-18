package cc.pscly.onememos.ui.accessibility

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cc.pscly.onememos.ui.theme.InkBorder
import cc.pscly.onememos.ui.theme.OneMemosTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 纸墨焦点环令牌与 Modifier 契约：宽度对齐 InkBorder，颜色取 primary，
 * focused=false 时不崩溃且节点仍可布局。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PaperInkFocusIndicatorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun strokeTokens_matchInkBorderHairlineAndStamp() {
        assertEquals(InkBorder.Hairline, PaperInkFocusIndicator.StrokeWidth)
        assertEquals(InkBorder.Stamp, PaperInkFocusIndicator.EmphasizedStrokeWidth)
    }

    @Test
    fun color_matchesThemePrimary() {
        var primary = androidx.compose.ui.graphics.Color.Unspecified
        var focusColor = androidx.compose.ui.graphics.Color.Unspecified
        composeRule.setContent {
            OneMemosTheme {
                primary = MaterialTheme.colorScheme.primary
                focusColor = PaperInkFocusIndicator.color()
            }
        }
        composeRule.waitForIdle()
        assertEquals(primary, focusColor)
    }

    @Test
    fun paperInkFocusBorder_focusedAndUnfocused_composableDoesNotCrash() {
        composeRule.setContent {
            OneMemosTheme {
                FocusSample(focused = true, tag = "focus_on")
                FocusSample(focused = false, tag = "focus_off")
            }
        }
        composeRule.onNodeWithTag("focus_on").assertIsDisplayed()
        composeRule.onNodeWithTag("focus_off").assertIsDisplayed()
        assertTrue(PaperInkFocusIndicator.StrokeWidth > 0.dp)
    }

    @Composable
    private fun FocusSample(
        focused: Boolean,
        tag: String,
    ) {
        with(PaperInkFocusIndicator) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .paperInkFocusBorder(
                            focused = focused,
                            shape = RoundedCornerShape(8.dp),
                            emphasized = focused,
                        )
                        .testTag(tag),
            )
        }
    }
}
