package cc.pscly.onememos.ui.feature.settings.appearance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.domain.settings.AppearanceInteractionSettingsSnapshot
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.feature.settings.R
import cc.pscly.onememos.ui.accessibility.ReducedMotion
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.component.ScrollPaperSurface
import kotlin.math.roundToInt

internal fun appearanceInk(colors: ColorScheme) = colors.onSurface

@Composable fun AppearanceInteractionScreen(viewModel: AppearanceInteractionViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AppearanceInteractionContent(uiState, viewModel::onIntent)
}

@Composable
fun AppearanceInteractionContent(
    uiState: AppearanceInteractionUiState,
    onIntent: (AppearanceInteractionUserIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val motionDescription = stringResource(if (ReducedMotion.current) {
        R.string.settings_appearance_reduced_motion_on
    } else R.string.settings_appearance_reduced_motion_off)
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().semantics { stateDescription = motionDescription }
            .testTag("settings_appearance_root"),
    ) {
        val contentMax = maxWidth.coerceAtMost(720.dp)
        ScrollPaperSurface(
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = contentMax).fillMaxSize(),
            scrollOffsetPx = scroll.value.toFloat(),
        ) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(vertical = 8.dp)
                .testTag("settings_appearance_list"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.settings_appearance_title),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.settings_appearance_description),
                    style = MaterialTheme.typography.bodyMedium, color = appearanceInk(MaterialTheme.colorScheme),
                )
                AppearanceBody(uiState, onIntent)
            }
        }
    }
}

@Composable private fun AppearanceBody(
    uiState: AppearanceInteractionUiState,
    onIntent: (AppearanceInteractionUserIntent) -> Unit,
) {
    uiState.persistentError?.let { error ->
        Text(errorText(error), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    val snapshot = uiState.snapshot ?: run {
        Text(stringResource(R.string.settings_appearance_loading), style = MaterialTheme.typography.bodyLarge)
        return
    }
    SelectionSection(SelectionGroup.PALETTE, snapshot, uiState.controlsEnabled, onIntent)
    SelectionSection(SelectionGroup.MODE, snapshot, uiState.controlsEnabled, onIntent)
    OverlaySection(snapshot, uiState.controlsEnabled, onIntent)
    DurationSection(snapshot.sealStampDurationMs, uiState.controlsEnabled, onIntent)
}

@Composable
private fun SelectionSection(group: SelectionGroup, snapshot: AppearanceInteractionSettingsSnapshot,
    enabled: Boolean, onIntent: (AppearanceInteractionUserIntent) -> Unit) {
    SettingsSection(stringResource(group.titleRes)) {
        SelectionOption.entries.filter { it.group == group }.forEach { option ->
            SelectionCard(option, snapshot, enabled) { onIntent(option.intent) }
        }
    }
}

@Composable
private fun OverlaySection(snapshot: AppearanceInteractionSettingsSnapshot, enabled: Boolean,
    onIntent: (AppearanceInteractionUserIntent) -> Unit) {
    val nextEnabled = !snapshot.quickCaptureOverlayEnabled
    val statusRes = if (snapshot.quickCaptureOverlayEnabled) R.string.settings_appearance_enabled
        else R.string.settings_appearance_disabled
    val status = stringResource(statusRes)
    SettingsSection(title = stringResource(R.string.settings_appearance_overlay_title)) {
        OnSurfaceFocus {
            InkCard(
                onClick = { onIntent(AppearanceInteractionUserIntent.SetQuickCaptureOverlayEnabled(nextEnabled)) },
                enabled = enabled,
                contentDescription = stringResource(R.string.settings_appearance_overlay_semantics, status),
                modifier = Modifier.heightIn(min = 48.dp).semantics { stateDescription = status }
                    .testTag("settings_appearance_overlay"),
            ) {
                Text(status, color = appearanceInk(MaterialTheme.colorScheme), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_appearance_overlay_description),
                    style = MaterialTheme.typography.bodySmall, color = appearanceInk(MaterialTheme.colorScheme))
            }
        }
    }
}

@Composable
private fun DurationSection(persistedValue: Int, enabled: Boolean,
    onIntent: (AppearanceInteractionUserIntent) -> Unit) {
    var sliderValue by remember(persistedValue) { mutableFloatStateOf(
        persistedValue.coerceIn(DURATION_MIN_MS, DURATION_MAX_MS).toFloat()) }
    val value = sliderValue.roundToInt()
    val valueText = stringResource(R.string.settings_appearance_duration_value, value)
    val semantics = stringResource(R.string.settings_appearance_duration_semantics, value)
    val ink = appearanceInk(MaterialTheme.colorScheme)
    SettingsSection(title = stringResource(R.string.settings_appearance_duration_title)) {
        Text(valueText, color = ink, style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold)
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = semantics
                    stateDescription = valueText
                    progressBarRangeInfo = ProgressBarRangeInfo(sliderValue,
                        DURATION_MIN_MS.toFloat()..DURATION_MAX_MS.toFloat())
                    if (!enabled) disabled()
                }.testTag("settings_appearance_duration_slider"),
            contentAlignment = Alignment.Center,
        ) {
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it.roundToInt().toFloat() },
                onValueChangeFinished = { if (value != persistedValue) {
                    onIntent(AppearanceInteractionUserIntent.SetSealStampDurationMs(value))
                } },
                enabled = enabled,
                valueRange = DURATION_MIN_MS.toFloat()..DURATION_MAX_MS.toFloat(),
                colors = SliderDefaults.colors(thumbColor = ink, activeTrackColor = ink),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun OnSurfaceFocus(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    MaterialTheme(colorScheme = colors.copy(primary = appearanceInk(colors)), content = content)
}

@Composable
private fun SelectionCard(option: SelectionOption, snapshot: AppearanceInteractionSettingsSnapshot,
    enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(option.labelRes)
    val selected = option.isSelected(snapshot)
    val selectionRes = if (selected) R.string.settings_appearance_selected
        else R.string.settings_appearance_not_selected
    val selectionText = stringResource(selectionRes)
    OnSurfaceFocus {
        InkCard(
            onClick = onClick,
            enabled = enabled,
            contentDescription = stringResource(R.string.settings_appearance_choice_semantics,
                label, selectionText),
            modifier = Modifier.heightIn(min = 48.dp).semantics {
                this.selected = selected
                stateDescription = selectionText
            }.testTag(option.testTag),
        ) {
            Row(
                Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically,
            ) {
                Text(text = label, color = appearanceInk(MaterialTheme.colorScheme), modifier = Modifier.weight(1f))
                Text(selectionText, style = MaterialTheme.typography.labelLarge,
                    color = appearanceInk(MaterialTheme.colorScheme))
            }
        }
    }
}

@Composable
private fun errorText(error: SettingsCapabilityError): String =
    stringResource(
        when (error) {
            SettingsCapabilityError.PermissionDenied -> R.string.settings_appearance_error_permission
            SettingsCapabilityError.PlatformUnavailable -> R.string.settings_appearance_error_platform
            SettingsCapabilityError.InvalidInput -> R.string.settings_appearance_error_invalid
            SettingsCapabilityError.StorageFailure -> R.string.settings_appearance_error_storage
            SettingsCapabilityError.NetworkUnavailable -> R.string.settings_appearance_error_network
            SettingsCapabilityError.AuthenticationExpired -> R.string.settings_appearance_error_auth
            SettingsCapabilityError.AlreadyRunning -> R.string.settings_appearance_error_running
            is SettingsCapabilityError.Unknown -> R.string.settings_appearance_error_unknown
        },
    )

private enum class SelectionGroup(val titleRes: Int) {
    PALETTE(R.string.settings_appearance_palette_title), MODE(R.string.settings_appearance_mode_title) }

private enum class SelectionOption(
    val group: SelectionGroup,
    val labelRes: Int,
    val intent: AppearanceInteractionUserIntent,
) {
    PALETTE_PAPER_INK(SelectionGroup.PALETTE, R.string.settings_appearance_palette_paper_ink, AppearanceInteractionUserIntent.SetThemePalette(ThemePalette.PAPER_INK)), PALETTE_INDIGO(SelectionGroup.PALETTE, R.string.settings_appearance_palette_indigo, AppearanceInteractionUserIntent.SetThemePalette(ThemePalette.INDIGO)),
    PALETTE_CYBER(SelectionGroup.PALETTE, R.string.settings_appearance_palette_cyber, AppearanceInteractionUserIntent.SetThemePalette(ThemePalette.CYBER)), MODE_FOLLOW_SYSTEM(SelectionGroup.MODE, R.string.settings_appearance_mode_follow_system, AppearanceInteractionUserIntent.SetThemeMode(ThemeMode.FOLLOW_SYSTEM)),
    MODE_LIGHT(SelectionGroup.MODE, R.string.settings_appearance_mode_light, AppearanceInteractionUserIntent.SetThemeMode(ThemeMode.LIGHT)), MODE_DARK(SelectionGroup.MODE, R.string.settings_appearance_mode_dark, AppearanceInteractionUserIntent.SetThemeMode(ThemeMode.DARK)),
    ;

    val testTag: String = "settings_appearance_${name.lowercase()}"

    fun isSelected(snapshot: AppearanceInteractionSettingsSnapshot): Boolean =
        when (val value = intent) {
            is AppearanceInteractionUserIntent.SetThemePalette -> snapshot.themePalette == value.palette
            is AppearanceInteractionUserIntent.SetThemeMode -> snapshot.themeMode == value.mode
            else -> false
        }
}

private const val DURATION_MIN_MS = 200
private const val DURATION_MAX_MS = 2_000
