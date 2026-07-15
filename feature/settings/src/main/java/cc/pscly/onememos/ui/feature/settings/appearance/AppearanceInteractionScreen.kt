package cc.pscly.onememos.ui.feature.settings.appearance

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.domain.settings.AppearanceInteractionSettingsCommand
import cc.pscly.onememos.domain.settings.AppearanceInteractionSettingsSnapshot
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.feature.settings.R
import cc.pscly.onememos.ui.accessibility.ReducedMotion
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.component.ScrollPaperSurface

@Composable
fun AppearanceInteractionScreen(viewModel: AppearanceInteractionViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AppearanceInteractionContent(uiState, viewModel::onIntent)
}

@Composable
fun AppearanceInteractionContent(
    uiState: AppearanceInteractionUiState,
    onIntent: (AppearanceInteractionUserIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = ReducedMotion.current
    val scroll = rememberScrollState()
    val motionDescription = stringResource(
        if (reduceMotion) R.string.settings_appearance_reduced_motion_on
        else R.string.settings_appearance_reduced_motion_off,
    )
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().semantics { stateDescription = motionDescription }
            .testTag("settings_appearance_root"),
    ) {
        val contentMax = maxWidth.coerceAtMost(720.dp)
        ScrollPaperSurface(
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = contentMax).fillMaxSize(),
            scrollOffsetPx = scroll.value.toFloat(),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(vertical = 8.dp)
                    .testTag("settings_appearance_list"),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.settings_appearance_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.settings_appearance_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                AppearanceBody(uiState, onIntent)
            }
        }
    }
}

@Composable
private fun AppearanceBody(
    uiState: AppearanceInteractionUiState,
    onIntent: (AppearanceInteractionUserIntent) -> Unit,
) {
    uiState.persistentError?.let { error ->
        Text(
            text = errorText(error),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    val snapshot = uiState.snapshot
    if (snapshot == null) {
        Text(stringResource(R.string.settings_appearance_loading), style = MaterialTheme.typography.bodyLarge)
        return
    }
    SelectionSection(SelectionGroup.PALETTE, snapshot, onIntent)
    SelectionSection(SelectionGroup.MODE, snapshot, onIntent)
    OverlaySection(snapshot, onIntent)
    SelectionSection(SelectionGroup.DURATION, snapshot, onIntent)
}

@Composable
private fun SelectionSection(
    group: SelectionGroup,
    snapshot: AppearanceInteractionSettingsSnapshot,
    onIntent: (AppearanceInteractionUserIntent) -> Unit,
) {
    SettingsSection(stringResource(group.titleRes)) {
        SelectionOption.entries.filter { it.group == group }.forEach { option ->
            SelectionCard(option, snapshot) { onIntent(option.command.toUserIntent()) }
        }
    }
}

@Composable
private fun OverlaySection(
    snapshot: AppearanceInteractionSettingsSnapshot,
    onIntent: (AppearanceInteractionUserIntent) -> Unit,
) {
    val nextEnabled = !snapshot.quickCaptureOverlayEnabled
    val command = AppearanceInteractionSettingsCommand.SetQuickCaptureOverlayEnabled(nextEnabled)
    val status = stringResource(
        if (snapshot.quickCaptureOverlayEnabled) R.string.settings_appearance_enabled
        else R.string.settings_appearance_disabled,
    )
    SettingsSection(title = stringResource(R.string.settings_appearance_overlay_title)) {
        InkCard(
            onClick = { onIntent(AppearanceInteractionUserIntent.SetQuickCaptureOverlayEnabled(nextEnabled)) },
            enabled = snapshot.commandInFlight != command,
            contentDescription = stringResource(R.string.settings_appearance_overlay_semantics, status),
            modifier = Modifier.heightIn(min = 48.dp).semantics { stateDescription = status }
                .testTag("settings_appearance_overlay"),
        ) {
            Text(text = status, fontWeight = FontWeight.SemiBold)
            Text(
                text = stringResource(R.string.settings_appearance_overlay_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
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
private fun SelectionCard(
    option: SelectionOption,
    snapshot: AppearanceInteractionSettingsSnapshot,
    onClick: () -> Unit,
) {
    val label = stringResource(option.labelRes)
    val selected = snapshot.isSelected(option.command)
    val selectionText = stringResource(
        if (selected) R.string.settings_appearance_selected
        else R.string.settings_appearance_not_selected,
    )
    InkCard(
        onClick = onClick,
        enabled = snapshot.commandInFlight != option.command,
        contentDescription = stringResource(
            R.string.settings_appearance_choice_semantics,
            label,
            selectionText,
        ),
        modifier = Modifier.heightIn(min = 48.dp).semantics {
                    this.selected = selected
                    stateDescription = selectionText
                }
                .testTag(option.testTag),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, modifier = Modifier.weight(1f))
            Text(
                text = selectionText,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
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

private fun AppearanceInteractionSettingsSnapshot.isSelected(command: AppearanceInteractionSettingsCommand): Boolean =
    when (command) {
        is AppearanceInteractionSettingsCommand.SetThemePalette -> themePalette == command.palette
        is AppearanceInteractionSettingsCommand.SetThemeMode -> themeMode == command.mode
        is AppearanceInteractionSettingsCommand.SetQuickCaptureOverlayEnabled ->
            quickCaptureOverlayEnabled == command.enabled
        is AppearanceInteractionSettingsCommand.SetSealStampDurationMs ->
            sealStampDurationMs == command.value
    }

private fun AppearanceInteractionSettingsCommand.toUserIntent(): AppearanceInteractionUserIntent =
    when (this) {
        is AppearanceInteractionSettingsCommand.SetThemePalette ->
            AppearanceInteractionUserIntent.SetThemePalette(palette)
        is AppearanceInteractionSettingsCommand.SetThemeMode ->
            AppearanceInteractionUserIntent.SetThemeMode(mode)
        is AppearanceInteractionSettingsCommand.SetQuickCaptureOverlayEnabled ->
            AppearanceInteractionUserIntent.SetQuickCaptureOverlayEnabled(enabled)
        is AppearanceInteractionSettingsCommand.SetSealStampDurationMs ->
            AppearanceInteractionUserIntent.SetSealStampDurationMs(value)
    }

private enum class SelectionGroup(val titleRes: Int) {
    PALETTE(R.string.settings_appearance_palette_title),
    MODE(R.string.settings_appearance_mode_title),
    DURATION(R.string.settings_appearance_duration_title),
}

private enum class SelectionOption(
    val group: SelectionGroup,
    val labelRes: Int,
    val command: AppearanceInteractionSettingsCommand,
) {
    PALETTE_PAPER_INK(SelectionGroup.PALETTE, R.string.settings_appearance_palette_paper_ink, AppearanceInteractionSettingsCommand.SetThemePalette(ThemePalette.PAPER_INK)),
    PALETTE_INDIGO(SelectionGroup.PALETTE, R.string.settings_appearance_palette_indigo, AppearanceInteractionSettingsCommand.SetThemePalette(ThemePalette.INDIGO)),
    PALETTE_CYBER(SelectionGroup.PALETTE, R.string.settings_appearance_palette_cyber, AppearanceInteractionSettingsCommand.SetThemePalette(ThemePalette.CYBER)),
    MODE_FOLLOW_SYSTEM(SelectionGroup.MODE, R.string.settings_appearance_mode_follow_system, AppearanceInteractionSettingsCommand.SetThemeMode(ThemeMode.FOLLOW_SYSTEM)),
    MODE_LIGHT(SelectionGroup.MODE, R.string.settings_appearance_mode_light, AppearanceInteractionSettingsCommand.SetThemeMode(ThemeMode.LIGHT)),
    MODE_DARK(SelectionGroup.MODE, R.string.settings_appearance_mode_dark, AppearanceInteractionSettingsCommand.SetThemeMode(ThemeMode.DARK)),
    DURATION_200(SelectionGroup.DURATION, R.string.settings_appearance_duration_200, AppearanceInteractionSettingsCommand.SetSealStampDurationMs(200)),
    DURATION_600(SelectionGroup.DURATION, R.string.settings_appearance_duration_600, AppearanceInteractionSettingsCommand.SetSealStampDurationMs(600)),
    DURATION_2000(SelectionGroup.DURATION, R.string.settings_appearance_duration_2000, AppearanceInteractionSettingsCommand.SetSealStampDurationMs(2_000)),
    ;

    val testTag: String = "settings_appearance_${name.lowercase()}"
}
