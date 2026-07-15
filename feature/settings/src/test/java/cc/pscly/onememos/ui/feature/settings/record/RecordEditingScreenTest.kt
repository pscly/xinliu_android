package cc.pscly.onememos.ui.feature.settings.record

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.QuickInsertTimeFormat
import cc.pscly.onememos.domain.settings.RecordEditingSettingsCommand
import cc.pscly.onememos.domain.settings.RecordEditingSettingsSnapshot
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.ui.theme.OneMemosTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp")
class RecordEditingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun currentValuesAreVisible_andAllFiveControlsSubmitDomainCommands() {
        val submitted = mutableListOf<RecordEditingSettingsCommand>()
        setRecordContent(onSubmit = submitted::add)

        composeRule.onNodeWithTag("settings_record_visibility_private").assertIsSelected()
        composeRule.onNodeWithTag("settings_record_regex").assertIsOff()
        composeRule.onNodeWithTag("settings_record_tag_counts").assertIsOn()
        composeRule.onNodeWithTag("settings_record_quick_insert").assertIsOff()
        composeRule.onNodeWithTag("settings_record_format_full").assertIsSelected()

        composeRule.onNodeWithTag("settings_record_visibility_public").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_record_regex").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_record_tag_counts").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_record_quick_insert").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_record_format_time").performScrollTo().performClick()

        assertEquals(
            listOf(
                RecordEditingSettingsCommand.SetDefaultVisibility(MemoVisibility.PUBLIC),
                RecordEditingSettingsCommand.SetRegexSearchEnabled(true),
                RecordEditingSettingsCommand.SetShowTagCounts(false),
                RecordEditingSettingsCommand.SetQuickInsertTimeEnabled(true),
                RecordEditingSettingsCommand.SetQuickInsertTimeFormat(QuickInsertTimeFormat.TIME_ONLY),
            ),
            submitted,
        )
    }

    @Test
    fun matchingCommandInFlight_disablesOnlyCorrespondingControls() {
        val cases =
            listOf(
                RecordEditingSettingsCommand.SetDefaultVisibility(MemoVisibility.PUBLIC) to
                    listOf(
                        "settings_record_visibility_private",
                        "settings_record_visibility_protected",
                        "settings_record_visibility_public",
                    ),
                RecordEditingSettingsCommand.SetRegexSearchEnabled(true) to
                    listOf("settings_record_regex"),
                RecordEditingSettingsCommand.SetShowTagCounts(false) to
                    listOf("settings_record_tag_counts"),
                RecordEditingSettingsCommand.SetQuickInsertTimeEnabled(true) to
                    listOf("settings_record_quick_insert"),
                RecordEditingSettingsCommand.SetQuickInsertTimeFormat(QuickInsertTimeFormat.TIME_ONLY) to
                    listOf("settings_record_format_full", "settings_record_format_time"),
            )
        val allControls = cases.flatMap { it.second }.distinct()
        var uiState by androidx.compose.runtime.mutableStateOf(recordState())
        composeRule.setContent {
            OneMemosTheme {
                RecordEditingContent(
                    uiState = uiState,
                    onBack = {},
                    onSubmit = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        cases.forEach { (command, disabledControls) ->
            uiState = recordState(commandInFlight = command)
            composeRule.waitForIdle()
            allControls.forEach { tag ->
                val node = composeRule.onNodeWithTag(tag)
                if (tag in disabledControls) {
                    node.assertIsNotEnabled()
                } else {
                    node.assertIsEnabled()
                }
            }
        }
    }

    @Test
    fun persistentError_hasVisibleTextAssertiveSemantics_andBackTargetIs48Dp() {
        var backClicks = 0
        setRecordContent(
            uiState = recordState(error = SettingsCapabilityError.StorageFailure),
            onBack = { backClicks += 1 },
        )

        composeRule.onNodeWithText("设置保存失败，请稍后重试").assertIsDisplayed()
        val errorConfig = composeRule.onNodeWithTag("settings_record_error").fetchSemanticsNode().config
        assertEquals(
            "设置保存失败，请稍后重试",
            errorConfig.getOrNull(SemanticsProperties.StateDescription),
        )
        assertEquals(
            LiveRegionMode.Assertive,
            errorConfig.getOrNull(SemanticsProperties.LiveRegion),
        )
        composeRule
            .onNodeWithTag("settings_record_back")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, backClicks)
    }

    @Test
    fun compactWindowAtLargeFont_remainsSingleColumnWithoutClippingKeyContent() {
        composeRule.setContent {
            OneMemosTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    Box(modifier = Modifier.requiredSize(360.dp, 800.dp)) {
                        RecordEditingContent(
                            uiState = recordState(),
                            onBack = {},
                            onSubmit = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("记录与编辑").assertIsDisplayed()
        listOf(
            "settings_record_visibility_private",
            "settings_record_regex",
            "settings_record_tag_counts",
            "settings_record_quick_insert",
            "settings_record_format_time",
        ).forEach { tag ->
            composeRule
                .onNodeWithTag(tag)
                .performScrollTo()
                .assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp)
        }
    }

    private fun setRecordContent(
        uiState: RecordEditingUiState = recordState(),
        onBack: () -> Unit = {},
        onSubmit: (RecordEditingSettingsCommand) -> Unit = {},
    ) {
        composeRule.setContent {
            OneMemosTheme {
                RecordEditingContent(
                    uiState = uiState,
                    onBack = onBack,
                    onSubmit = onSubmit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun recordState(
        commandInFlight: RecordEditingSettingsCommand? = null,
        error: SettingsCapabilityError? = null,
    ): RecordEditingUiState =
        RecordEditingUiState(
            loading = false,
            snapshot =
                RecordEditingSettingsSnapshot(
                    defaultVisibility = MemoVisibility.PRIVATE,
                    regexSearchEnabled = false,
                    showTagCounts = true,
                    quickInsertTimeEnabled = false,
                    quickInsertTimeFormat = QuickInsertTimeFormat.FULL_DATETIME,
                    commandInFlight = commandInFlight,
                ),
            persistentError = error,
        )
}
