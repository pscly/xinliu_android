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

        listOf(
            "风格预设", "文墨·朱砂", "清简·月白", "夜航·黛蓝", "赛博·荧光青",
            "明暗模式", "跟随系统", "浅色", "深色",
            "高级调节", "色板", "宣纸 · 朱砂", "宣纸 · 黛蓝", "玄青 · 荧光青", "月白 · 中性",
            "跟随系统动态色",
            "质感", "文墨卷轴", "清简",
            "密度", "标准", "宽松", "紧凑",
            "字体", "霞鹜文楷", "系统字体",
            "阅读模式", "正文字号", "字号·小", "字号·标准", "字号·大", "字号·特大",
            "行距", "行距·紧凑", "行距·标准", "行距·宽松",
            "标签彩色", "已开启",
            "悬浮记录", "已关闭", "盖章反馈时长", "600 毫秒",
        ).forEach { text ->
            composeRule.onNodeWithText(text, substring = false, useUnmergedTree = true).assertExists()
        }
        optionTags.forEach { tag(it).assertHeightIsAtLeast(48.dp) }
        tag("settings_appearance_preset_wenmo_zhusha").assertIsSelected()
        tag("settings_appearance_preset_qingjian_yuebai").assertIsNotSelected()
        tag("settings_appearance_mode_follow_system").assertIsSelected()
        tag("settings_appearance_palette_paper_ink").assertIsSelected()
        tag("settings_appearance_texture_scroll").assertIsSelected()
        tag("settings_appearance_density_standard").assertIsSelected()
        tag("settings_appearance_font_wenkai").assertIsSelected()
        tag("settings_appearance_reading_font_standard").assertIsSelected()
        tag("settings_appearance_reading_line_standard").assertIsSelected()
        tag("settings_appearance_duration_slider").assertHeightIsAtLeast(48.dp)
        assertEquals("已关闭", tag("settings_appearance_overlay").stateDescription())
        assertEquals("已开启", tag("settings_appearance_tag_color").stateDescription())
    }

    @Test
    fun capabilityRows_emitTypedUserIntents() {
        val intents = mutableListOf<AppearanceInteractionUserIntent>()
        setAppearance(onIntent = intents::add)

        listOf(
            "settings_appearance_preset_yehang_dailan",
            "settings_appearance_mode_dark",
            "settings_appearance_palette_indigo",
            "settings_appearance_texture_minimal",
            "settings_appearance_density_compact",
            "settings_appearance_font_system",
            "settings_appearance_reading_font_large",
            "settings_appearance_reading_line_relaxed",
            "settings_appearance_tag_color",
            "settings_appearance_overlay",
        ).forEach { tag(it).performScrollTo().performClick() }
        assertEquals(
            listOf(
                AppearanceInteractionUserIntent.SetThemeDescriptor(ThemeDescriptor.YEHANG_DAILAN),
                AppearanceInteractionUserIntent.SetThemeMode(ThemeMode.DARK),
                AppearanceInteractionUserIntent.SetThemeDescriptor(
                    ThemeDescriptor.WENMO_ZHUSHA.copy(palette = ThemePalette.INDIGO),
                ),
                AppearanceInteractionUserIntent.SetThemeDescriptor(
                    ThemeDescriptor.WENMO_ZHUSHA.copy(texture = ThemeTexture.MINIMAL),
                ),
                AppearanceInteractionUserIntent.SetThemeDescriptor(
                    ThemeDescriptor.WENMO_ZHUSHA.copy(density = ThemeDensity.COMPACT),
                ),
                AppearanceInteractionUserIntent.SetThemeDescriptor(
                    ThemeDescriptor.WENMO_ZHUSHA.copy(fontFamily = ThemeFontFamily.SYSTEM),
                ),
                AppearanceInteractionUserIntent.SetReadingFontScale(ReadingFontScale.LARGE),
                AppearanceInteractionUserIntent.SetReadingLineHeight(ReadingLineHeight.RELAXED),
                AppearanceInteractionUserIntent.SetTagChipColorful(false),
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

        val choice = tag("settings_appearance_preset_saibo_fluor")
        choice.performSemanticsAction(SemanticsActions.RequestFocus) { request -> request() }
        choice.assertIsFocused()
        tag("settings_appearance_root", unmerged = false).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "减少动态效果已开启"))

        choice.performClick()
        composeRule.waitForIdle()
        assertEquals(
            listOf(AppearanceInteractionUserIntent.SetThemeDescriptor(ThemeDescriptor.SAIBO_FLUOR)),
            intents,
        )
        composeRule.onNodeWithText("文墨·朱砂", useUnmergedTree = true).assertExists()
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
        loading = false,
        snapshot = AppearanceInteractionSettingsSnapshot(
            themeDescriptor = ThemeDescriptor.WENMO_ZHUSHA,
            themeMode = ThemeMode.FOLLOW_SYSTEM,
            quickCaptureOverlayEnabled = false,
            sealStampDurationMs = durationMs,
            commandInFlight = null,
        ),
    )

    private fun tag(value: String, unmerged: Boolean = true) =
        composeRule.onNodeWithTag(value, useUnmergedTree = unmerged)

    private fun SemanticsNodeInteraction.stateDescription(): String? =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    private companion object {
        val optionTags = listOf(
            "settings_appearance_preset_wenmo_zhusha",
            "settings_appearance_preset_qingjian_yuebai",
            "settings_appearance_preset_yehang_dailan",
            "settings_appearance_preset_saibo_fluor",
            "settings_appearance_mode_follow_system",
            "settings_appearance_mode_light",
            "settings_appearance_mode_dark",
            "settings_appearance_palette_paper_ink",
            "settings_appearance_palette_indigo",
            "settings_appearance_palette_cyber",
            "settings_appearance_palette_moon_white",
            "settings_appearance_palette_dynamic",
            "settings_appearance_texture_scroll",
            "settings_appearance_texture_minimal",
            "settings_appearance_density_standard",
            "settings_appearance_density_relaxed",
            "settings_appearance_density_compact",
            "settings_appearance_font_wenkai",
            "settings_appearance_font_system",
            "settings_appearance_reading_font_small",
            "settings_appearance_reading_font_standard",
            "settings_appearance_reading_font_large",
            "settings_appearance_reading_font_extra_large",
            "settings_appearance_reading_line_compact",
            "settings_appearance_reading_line_standard",
            "settings_appearance_reading_line_relaxed",
            "settings_appearance_tag_color",
            "settings_appearance_overlay",
        )

        fun contrastRatio(first: Color, second: Color): Float {
            val lighter = maxOf(first.luminance(), second.luminance())
            val darker = minOf(first.luminance(), second.luminance())
            return (lighter + 0.05f) / (darker + 0.05f)
        }
    }
}
