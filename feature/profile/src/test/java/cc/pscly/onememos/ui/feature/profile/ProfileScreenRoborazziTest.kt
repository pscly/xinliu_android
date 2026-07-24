package cc.pscly.onememos.ui.feature.profile

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.ui.theme.OneMemosTheme
import cc.pscly.onememos.ui.theme.OneMemosThemeConfig
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class ProfileScreenRoborazziTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val roborazziRule =
        RoborazziRule(
            options = RoborazziRule.Options(outputDirectoryPath = "src/test/screenshots"),
        )

    private val month = YearMonth.of(2026, 7)
    private val today = LocalDate.of(2026, 7, 22)
    private val gridStart =
        month.atDay(1).minusDays((month.atDay(1).dayOfWeek.value - 1).toLong())

    private val heatmap =
        HeatmapUiModel(
            month = month,
            activeStart = month.atDay(1),
            activeEnd = month.atEndOfMonth(),
            gridStart = gridStart,
            rows = 5,
            counts = mapOf(today to 3, today.minusDays(1) to 1),
            maxCount = 3,
        )

    private val fixture =
        ProfileUiState(
            heatmap = heatmap,
            month = month,
            selection = DateRangeSelection(anchor = today, current = today),
            sections = emptyList(),
            selectedMemoCount = 0,
        )

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun profile_compact_light() = capture(dark = false, fontScale = 1f)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun profile_compact_dark() = capture(dark = true, fontScale = 1f)

    @Test
    @Config(qualifiers = "w600dp-h840dp-xxhdpi")
    fun profile_expanded_light() = capture(dark = false, fontScale = 1f)

    @Test
    @Config(qualifiers = "w600dp-h840dp-xxhdpi")
    fun profile_expanded_dark() = capture(dark = true, fontScale = 1f)

    @Test
    @Config(qualifiers = "w360dp-h250dp-xxhdpi")
    fun profile_compactHeight_light() = capture(dark = false, fontScale = 1f)

    @Test
    @Config(qualifiers = "w360dp-h250dp-xxhdpi")
    fun profile_compactHeight_dark() = capture(dark = true, fontScale = 1f)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun profile_largeFont_light() = capture(dark = false, fontScale = 2f)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun profile_largeFont_dark() = capture(dark = true, fontScale = 2f)

    private fun capture(dark: Boolean, fontScale: Float) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale)) {
                OneMemosTheme(
                    config =
                        OneMemosThemeConfig(
                            palette = ThemePalette.PAPER_INK,
                            themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
                        ),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .testTag(HOST_TAG),
                    ) {
                        ProfileScreenContent(
                            uiState = fixture,
                            onOpenDrawer = {},
                            onOpenMemo = {},
                            onSetMonth = {},
                            onPrevMonth = {},
                            onNextMonth = {},
                            onGoToToday = {},
                            onSelectSingle = {},
                            onStartRange = {},
                            onUpdateRange = {},
                            today = today,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(HOST_TAG).captureRoboImage()
    }

    companion object {
        private const val HOST_TAG = "profile_roborazzi_host"
    }
}
