package cc.pscly.onememos.ui.feature.settings.appearance

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.domain.settings.AppearanceInteractionSettingsCommand
import cc.pscly.onememos.domain.settings.AppearanceInteractionSettingsSnapshot
import cc.pscly.onememos.ui.accessibility.ReducedMotion
import cc.pscly.onememos.ui.theme.OneMemosTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppearanceInteractionScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun content_showsEveryCapabilityWithTextSelectionAndMinimumTargets() {
        setAppearance()

        listOf(
            "主题色板",
            "宣纸 · 朱砂",
            "宣纸 · 黛蓝",
            "玄青 · 荧光青",
            "明暗模式",
            "跟随系统",
            "浅色",
            "深色",
            "悬浮记录",
            "已关闭",
            "授权",
            "盖章反馈时长",
            "200 毫秒",
            "600 毫秒",
            "2000 毫秒",
        ).forEach { text ->
            composeRule.onNodeWithText(text, substring = true, useUnmergedTree = true).assertExists()
        }

        optionTags.forEach { tag ->
            composeRule.onNodeWithTag(tag, useUnmergedTree = true).assertHeightIsAtLeast(48.dp)
        }
        composeRule
            .onNodeWithTag("settings_appearance_palette_paper_ink", useUnmergedTree = true)
            .assertIsSelected()
        composeRule
            .onNodeWithTag("settings_appearance_palette_indigo", useUnmergedTree = true)
            .assertIsNotSelected()
        composeRule
            .onNodeWithTag("settings_appearance_mode_follow_system", useUnmergedTree = true)
            .assertIsSelected()
        composeRule
            .onNodeWithTag("settings_appearance_duration_600", useUnmergedTree = true)
            .assertIsSelected()
        assertEquals(
            "已关闭",
            composeRule
                .onNodeWithTag("settings_appearance_overlay", useUnmergedTree = true)
                .stateDescription(),
        )
    }

    @Test
    fun capabilityRows_emitTypedUserIntents() {
        val intents = mutableListOf<AppearanceInteractionUserIntent>()
        setAppearance(onIntent = intents::add)

        listOf(
            "settings_appearance_palette_indigo",
            "settings_appearance_mode_dark",
            "settings_appearance_overlay",
            "settings_appearance_duration_2000",
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo().performClick()
        }

        assertEquals(
            listOf(
                AppearanceInteractionUserIntent.SetThemePalette(ThemePalette.INDIGO),
                AppearanceInteractionUserIntent.SetThemeMode(ThemeMode.DARK),
                AppearanceInteractionUserIntent.SetQuickCaptureOverlayEnabled(true),
                AppearanceInteractionUserIntent.SetSealStampDurationMs(2_000),
            ),
            intents,
        )
    }

    @Test
    fun threeWindowSizesAndLargeFont_keepSingleColumnAndReachLastChoice() {
        var size by mutableStateOf(DpSize(360.dp, 800.dp))
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                OneMemosTheme {
                    Box(modifier = Modifier.requiredSize(size).testTag("window_host")) {
                        AppearanceInteractionContent(
                            uiState = readyUiState(),
                            onIntent = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        listOf(
            DpSize(360.dp, 800.dp),
            DpSize(600.dp, 960.dp),
            DpSize(840.dp, 900.dp),
        ).forEach { next ->
            size = next
            composeRule.waitForIdle()
            val listNode = composeRule.onNodeWithTag("settings_appearance_list")
            listNode.assertExists()
            assertTrue(listNode.fetchSemanticsNode().boundsInRoot.width <= 720f)
            optionTags.forEach { tag ->
                composeRule.onNodeWithTag(tag, useUnmergedTree = true).assertExists()
            }
            // Robolectric 宿主尺寸固定，requiredSize 超出宿主时会被外层裁切；
            // 这里验证三种宽度下末项仍在同一滚动列中可达且不会压缩到 48dp 以下。
            composeRule
                .onNodeWithTag("settings_appearance_duration_2000", useUnmergedTree = true)
                .performScrollTo()
                .assertExists()
                .assertHeightIsAtLeast(48.dp)
            composeRule.onNodeWithText("2000 毫秒", useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun focusedChoiceHasFocusSemantics_andReducedMotionKeepsImmediateTextState() {
        val intents = mutableListOf<AppearanceInteractionUserIntent>()
        composeRule.setContent {
            CompositionLocalProvider(ReducedMotion.Local provides true) {
                OneMemosTheme {
                    AppearanceInteractionContent(
                        uiState = readyUiState(),
                        onIntent = { intents.add(it) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        val choice =
            composeRule.onNodeWithTag(
                "settings_appearance_palette_cyber",
                useUnmergedTree = true,
            )
        choice.performSemanticsAction(SemanticsActions.RequestFocus) { request -> request() }
        choice.assertIsFocused()
        composeRule
            .onNodeWithTag("settings_appearance_root")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "减少动态效果已开启"))

        choice.performClick()
        composeRule.waitForIdle()
        assertEquals(
            listOf(AppearanceInteractionUserIntent.SetThemePalette(ThemePalette.CYBER)),
            intents,
        )
        composeRule.onNodeWithText("宣纸 · 朱砂", useUnmergedTree = true).assertExists()
    }

    private fun setAppearance(
        onIntent: (AppearanceInteractionUserIntent) -> Unit = {},
    ) {
        composeRule.setContent {
            OneMemosTheme {
                AppearanceInteractionContent(
                    uiState = readyUiState(),
                    onIntent = onIntent,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun readyUiState() =
        AppearanceInteractionUiState(
            loading = false,
            snapshot =
                AppearanceInteractionSettingsSnapshot(
                    themePalette = ThemePalette.PAPER_INK,
                    themeMode = ThemeMode.FOLLOW_SYSTEM,
                    quickCaptureOverlayEnabled = false,
                    sealStampDurationMs = 600,
                    commandInFlight = null,
                ),
        )

    private fun SemanticsNodeInteraction.stateDescription(): String? =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    private companion object {
        val optionTags =
            listOf(
                "settings_appearance_palette_paper_ink",
                "settings_appearance_palette_indigo",
                "settings_appearance_palette_cyber",
                "settings_appearance_mode_follow_system",
                "settings_appearance_mode_light",
                "settings_appearance_mode_dark",
                "settings_appearance_overlay",
                "settings_appearance_duration_200",
                "settings_appearance_duration_600",
                "settings_appearance_duration_2000",
            )
    }
}
