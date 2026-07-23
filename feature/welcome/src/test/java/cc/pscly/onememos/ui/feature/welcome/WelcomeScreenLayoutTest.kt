package cc.pscly.onememos.ui.feature.welcome

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * WelcomeScreen 布局回归：
 * 小视口 (360dp × 250dp) + 大字体 (fontScale=2f) 时，
 * 「立即体验（离线）」按钮可通过滚动到达且可见，
 * 且垂直滚动偏移 value > 0（证明确实发生了滚动）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WelcomeScreenLayoutTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = "w360dp-h250dp-xxhdpi")
    fun compactHeightAndLargeFont_enterLocalButtonReachableByScroll() {
        composeTestRule.setContent {
            val ambient = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = ambient.density, fontScale = 2f),
            ) {
                WelcomeScreenContent(
                    onEnterLocal = {},
                    onGoBindServer = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("welcome_enter_local")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.waitForIdle()

        val scrollRange = composeTestRule
            .onNodeWithTag("welcome_scroll")
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.VerticalScrollAxisRange)
        assertNotNull("welcome_scroll 应具备 VerticalScrollAxisRange", scrollRange)
        assertTrue(
            "performScrollTo 后垂直滚动偏移应 > 0，实际 value=${scrollRange!!.value()}",
            scrollRange.value() > 0f,
        )
    }
}
