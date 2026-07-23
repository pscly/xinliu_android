package cc.pscly.onememos.ui.component

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cc.pscly.onememos.ui.theme.InkSpacing
import cc.pscly.onememos.ui.theme.OneMemosTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * InkCard contentPadding 契约：默认仍为 CardPadding；自定义水平 0 时内容更宽。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp-xxhdpi")
class InkCardContentPaddingTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun defaultContentPadding_appliesCardPaddingOnBothSides() {
        var contentWidthDp = 0f
        composeRule.setContent {
            val density = LocalDensity.current
            OneMemosTheme {
                Box(modifier = Modifier.width(360.dp)) {
                    InkCard(
                        modifier = Modifier.testTag("ink_card_default"),
                        onClick = null,
                    ) {
                        Text(
                            text = "default",
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag("ink_card_default_content")
                                    .onGloballyPositioned { coords ->
                                        contentWidthDp = with(density) { coords.size.width.toDp().value }
                                    },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val expected = 360f - InkSpacing.CardPadding.value * 2f
        assertTrue(
            "default content width should be ~$expected dp (got $contentWidthDp)",
            kotlin.math.abs(contentWidthDp - expected) <= 2f,
        )
    }

    @Test
    fun customZeroHorizontalPadding_yieldsWiderContentThanDefault() {
        var defaultWidth = 0f
        var customWidth = 0f
        composeRule.setContent {
            val density = LocalDensity.current
            OneMemosTheme {
                Box(modifier = Modifier.width(360.dp)) {
                    InkCard(
                        modifier = Modifier.testTag("ink_card_default_cmp"),
                        onClick = null,
                    ) {
                        Text(
                            text = "d",
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { coords ->
                                        defaultWidth = with(density) { coords.size.width.toDp().value }
                                    },
                        )
                    }
                }
                Box(modifier = Modifier.width(360.dp)) {
                    InkCard(
                        modifier = Modifier.testTag("ink_card_zero_h"),
                        onClick = null,
                        contentPadding = PaddingValues(vertical = InkSpacing.CardPadding),
                    ) {
                        Text(
                            text = "z",
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag("ink_card_zero_h_content")
                                    .onGloballyPositioned { coords ->
                                        customWidth = with(density) { coords.size.width.toDp().value }
                                    },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        assertTrue(
            "zero-horizontal content ($customWidth) must be wider than default ($defaultWidth)",
            customWidth > defaultWidth + InkSpacing.CardPadding.value,
        )
        assertEquals(360f, customWidth, 2f)
        composeRule.onNodeWithTag("ink_card_zero_h").assertExists()
    }
}
