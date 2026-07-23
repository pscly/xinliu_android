package cc.pscly.onememos.ui.feature.home

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FilterStatusBannerSemanticsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun bothTagsAndQuery_exposesMergedPoliteStatus() {
        composeTestRule.setContent {
            FilterStatusBanner(
                visible = true,
                query = "test",
                tags = listOf("work", "life"),
                regexEnabled = false,
                onClearAll = {},
                onClearQuery = {},
                onEditQuery = {},
                onToggleTag = {},
            )
        }

        composeTestRule
            .onNode(filterStatusSemantics("筛选：2 标签, 搜索中"))
            .assertExists()
    }

    @Test
    fun tagsOnly_exposesMergedPoliteStatus() {
        composeTestRule.setContent {
            FilterStatusBanner(
                visible = true,
                query = "",
                tags = listOf("work"),
                regexEnabled = false,
                onClearAll = {},
                onClearQuery = {},
                onEditQuery = {},
                onToggleTag = {},
            )
        }

        composeTestRule
            .onNode(filterStatusSemantics("筛选：1 标签"))
            .assertExists()
    }

    @Test
    fun queryOnly_exposesMergedPoliteStatus() {
        composeTestRule.setContent {
            FilterStatusBanner(
                visible = true,
                query = "test",
                tags = emptyList(),
                regexEnabled = true,
                onClearAll = {},
                onClearQuery = {},
                onEditQuery = {},
                onToggleTag = {},
            )
        }

        composeTestRule
            .onNode(filterStatusSemantics("搜索中"))
            .assertExists()
    }

    @Test
    fun neither_exposesEmptyPoliteStatusAndInvisibleBannerIsAbsent() {
        val visible = mutableStateOf(true)

        composeTestRule.setContent {
            FilterStatusBanner(
                visible = visible.value,
                query = "",
                tags = emptyList(),
                regexEnabled = false,
                onClearAll = {},
                onClearQuery = {},
                onEditQuery = {},
                onToggleTag = {},
            )
        }

        composeTestRule
            .onNode(filterStatusSemantics(""))
            .assertExists()

        composeTestRule.runOnIdle {
            visible.value = false
        }

        composeTestRule
            .onNode(filterStatusSemantics(""))
            .assertDoesNotExist()
    }

    private fun filterStatusSemantics(contentDescription: String): SemanticsMatcher =
        hasContentDescription(contentDescription) and
            SemanticsMatcher.expectValue(
                SemanticsProperties.LiveRegion,
                LiveRegionMode.Polite,
            )
}
