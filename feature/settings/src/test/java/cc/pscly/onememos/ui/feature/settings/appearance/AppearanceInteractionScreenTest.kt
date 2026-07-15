package cc.pscly.onememos.ui.feature.settings.appearance

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import cc.pscly.onememos.domain.model.*
import cc.pscly.onememos.domain.settings.*
import cc.pscly.onememos.ui.accessibility.ReducedMotion
import cc.pscly.onememos.ui.theme.*
import org.junit.Assert.*
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

        listOf("主题色板", "宣纸 · 朱砂", "宣纸 · 黛蓝", "玄青 · 荧光青", "明暗模式", "跟随系统",
            "浅色", "深色", "悬浮记录", "已关闭", "授权", "盖章反馈时长", "600 毫秒").forEach { text ->
            composeRule.onNodeWithText(text, substring = true, useUnmergedTree = true).assertExists()
        }
        optionTags.forEach { tag(it).assertHeightIsAtLeast(48.dp) }
        tag("settings_appearance_palette_paper_ink").assertIsSelected()
        tag("settings_appearance_palette_indigo").assertIsNotSelected()
        tag("settings_appearance_mode_follow_system").assertIsSelected()
        tag("settings_appearance_duration_slider").assertHeightIsAtLeast(48.dp)
        assertEquals("已关闭", tag("settings_appearance_overlay").stateDescription())
    }

    @Test
    fun capabilityRows_emitTypedUserIntents() {
        val intents = mutableListOf<AppearanceInteractionUserIntent>()
        setAppearance(onIntent = intents::add)

        listOf("settings_appearance_palette_indigo", "settings_appearance_mode_dark",
            "settings_appearance_overlay").forEach { tag(it).performScrollTo().performClick() }
        assertEquals(
            listOf(
                AppearanceInteractionUserIntent.SetThemePalette(ThemePalette.INDIGO),
                AppearanceInteractionUserIntent.SetThemeMode(ThemeMode.DARK),
                AppearanceInteractionUserIntent.SetQuickCaptureOverlayEnabled(true),
            ),
            intents,
        )
    }

    @Test
    fun durationSlider_representsPersistedIntermediateValue() {
        setAppearance(uiState = readyUiState(durationMs = 800))

        val slider = tag("settings_appearance_duration_slider", unmerged = false).performScrollTo()
        composeRule.onNodeWithText("800 毫秒", useUnmergedTree = true).assertIsDisplayed()
        slider.assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo(800f, 200f..2_000f, 0)))
    }

    @Test
    fun durationSlider_submitsOnlyAfterGestureFinishes() {
        val intents = mutableListOf<AppearanceInteractionUserIntent>()
        setAppearance(onIntent = intents::add)
        val slider = tag("settings_appearance_duration_slider").performScrollTo()
        slider.performTouchInput { down(centerLeft) }
        slider.performTouchInput { moveTo(centerRight) }
        composeRule.waitForIdle()
        assertTrue(intents.isEmpty())

        slider.performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals(1, intents.size)
        val submitted = intents.single() as AppearanceInteractionUserIntent.SetSealStampDurationMs
        assertTrue(submitted.value in 601..2_000)
    }

    @Test
    fun submittingCommand_disablesAllAppearanceControls() {
        val uiState = readyUiState().copy(
            submittingCommand = AppearanceInteractionSettingsCommand.SetThemeMode(ThemeMode.DARK))
        setAppearance(uiState = uiState)
        optionTags.forEach { tag(it).assertIsNotEnabled() }
        tag("settings_appearance_duration_slider", unmerged = false).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun allThemeCombinations_useReadableTextAndFocusRoles() {
        val schemes = ThemePalette.entries.flatMap {
            listOf(oneMemosLightColorScheme(it), oneMemosDarkColorScheme(it))
        }
        schemes.forEach { scheme ->
            val ink = appearanceInk(scheme)
            assertEquals(scheme.onSurface, ink)
            assertTrue(contrastRatio(ink, scheme.surface) >= 4.5f)
            assertTrue(contrastRatio(ink, scheme.surface) >= 3f)
        }
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

        listOf(DpSize(360.dp, 800.dp), DpSize(600.dp, 960.dp), DpSize(840.dp, 900.dp)).forEach { next ->
            size = next
            composeRule.waitForIdle()
            val listNode = composeRule.onNodeWithTag("settings_appearance_list")
            listNode.assertExists()
            assertTrue(listNode.fetchSemanticsNode().boundsInRoot.width <= 720f)
            optionTags.forEach { tag(it).assertExists() }
            // Robolectric 宿主尺寸固定，requiredSize 超出宿主时会被外层裁切；
            // 这里验证三种宽度下末项仍在同一滚动列中可达且不会压缩到 48dp 以下。
            tag("settings_appearance_duration_slider").performScrollTo()
                .assertExists().assertHeightIsAtLeast(48.dp)
            composeRule.onNodeWithText("600 毫秒", useUnmergedTree = true).assertExists()
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

        val choice = tag("settings_appearance_palette_cyber")
        choice.performSemanticsAction(SemanticsActions.RequestFocus) { request -> request() }
        choice.assertIsFocused()
        tag("settings_appearance_root", unmerged = false).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "减少动态效果已开启"))

        choice.performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(AppearanceInteractionUserIntent.SetThemePalette(ThemePalette.CYBER)), intents)
        composeRule.onNodeWithText("宣纸 · 朱砂", useUnmergedTree = true).assertExists()
    }

    private fun setAppearance(
        uiState: AppearanceInteractionUiState = readyUiState(),
        onIntent: (AppearanceInteractionUserIntent) -> Unit = {},
    ) {
        composeRule.setContent {
            OneMemosTheme {
                AppearanceInteractionContent(uiState, onIntent, Modifier.fillMaxSize())
            }
        }
        composeRule.waitForIdle()
    }

    private fun readyUiState(durationMs: Int = 600) = AppearanceInteractionUiState(
        loading = false, snapshot = AppearanceInteractionSettingsSnapshot(
            ThemePalette.PAPER_INK, ThemeMode.FOLLOW_SYSTEM, false, durationMs, null))

    private fun tag(value: String, unmerged: Boolean = true) =
        composeRule.onNodeWithTag(value, useUnmergedTree = unmerged)

    private fun SemanticsNodeInteraction.stateDescription(): String? =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    private companion object {
        val optionTags = listOf("settings_appearance_palette_paper_ink",
            "settings_appearance_palette_indigo", "settings_appearance_palette_cyber",
            "settings_appearance_mode_follow_system", "settings_appearance_mode_light",
            "settings_appearance_mode_dark", "settings_appearance_overlay")

        fun contrastRatio(first: Color, second: Color): Float {
            val lighter = maxOf(first.luminance(), second.luminance())
            val darker = minOf(first.luminance(), second.luminance())
            return (lighter + 0.05f) / (darker + 0.05f)
        }
    }
}
