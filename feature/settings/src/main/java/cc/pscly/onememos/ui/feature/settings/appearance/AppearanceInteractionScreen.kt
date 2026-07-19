package cc.pscly.onememos.ui.feature.settings.appearance

import android.os.Build
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
import cc.pscly.onememos.domain.model.ReadingFontScale
import cc.pscly.onememos.domain.model.ReadingLineHeight
import cc.pscly.onememos.domain.model.ThemeDensity
import cc.pscly.onememos.domain.model.ThemeDescriptor
import cc.pscly.onememos.domain.model.ThemeFontFamily
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.domain.model.ThemeTexture
import cc.pscly.onememos.domain.settings.AppearanceInteractionSettingsSnapshot
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.feature.settings.R
import cc.pscly.onememos.ui.accessibility.ReducedMotion
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.component.ScrollPaperSurface
import cc.pscly.onememos.ui.theme.InkSpacing
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
        val contentMax = maxWidth.coerceAtMost(InkSpacing.ContentMaxWidth)
        ScrollPaperSurface(
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = contentMax).fillMaxSize(),
            scrollOffsetPx = scroll.value.toFloat(),
        ) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(vertical = InkSpacing.X8)
                .testTag("settings_appearance_list"), verticalArrangement = Arrangement.spacedBy(InkSpacing.X16)) {
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
    PresetSection(snapshot, uiState.controlsEnabled, onIntent)
    ModeSection(snapshot, uiState.controlsEnabled, onIntent)
    AdvancedSection(snapshot, uiState.controlsEnabled, onIntent)
    TagColorSection(snapshot, uiState.controlsEnabled, onIntent)
    ReadingSection(snapshot, uiState.controlsEnabled, onIntent)
    OverlaySection(snapshot, uiState.controlsEnabled, onIntent)
    DurationSection(snapshot.sealStampDurationMs, uiState.controlsEnabled, onIntent)
}

@Composable
private fun PresetSection(
    snapshot: AppearanceInteractionSettingsSnapshot,
    enabled: Boolean,
    onIntent: (AppearanceInteractionUserIntent) -> Unit,
) {
    SettingsSection(stringResource(R.string.settings_appearance_preset_title)) {
        FactoryPresetOption.entries.forEach { option ->
            val selected = snapshot.themeDescriptor == option.descriptor
            ChoiceCard(
                title = stringResource(option.titleRes),
                subtitle = stringResource(option.subtitleRes),
                selected = selected,
                enabled = enabled,
                testTag = option.testTag,
                onClick = {
                    onIntent(AppearanceInteractionUserIntent.SetThemeDescriptor(option.descriptor))
                },
            )
        }
    }
}

@Composable
private fun ModeSection(
    snapshot: AppearanceInteractionSettingsSnapshot,
    enabled: Boolean,
    onIntent: (AppearanceInteractionUserIntent) -> Unit,
) {
    SettingsSection(stringResource(R.string.settings_appearance_mode_title)) {
        ModeOption.entries.forEach { option ->
            ChoiceCard(
                title = stringResource(option.labelRes),
                subtitle = null,
                selected = snapshot.themeMode == option.mode,
                enabled = enabled,
                testTag = option.testTag,
                onClick = { onIntent(AppearanceInteractionUserIntent.SetThemeMode(option.mode)) },
            )
        }
    }
}

@Composable
private fun AdvancedSection(
    snapshot: AppearanceInteractionSettingsSnapshot,
    enabled: Boolean,
    onIntent: (AppearanceInteractionUserIntent) -> Unit,
) {
    val descriptor = snapshot.themeDescriptor
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.X12)) {
        Text(
            text = stringResource(R.string.settings_appearance_advanced_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = appearanceInk(MaterialTheme.colorScheme),
        )
        SettingsSection(stringResource(R.string.settings_appearance_palette_title)) {
            AdvancedPaletteOption.entries
                .filter { it.palette != ThemePalette.DYNAMIC || dynamicSupported }
                .forEach { option ->
                    ChoiceCard(
                        title = stringResource(option.labelRes),
                        subtitle = null,
                        selected = descriptor.palette == option.palette,
                        enabled = enabled,
                        testTag = option.testTag,
                        onClick = {
                            onIntent(
                                AppearanceInteractionUserIntent.SetThemeDescriptor(
                                    descriptor.copy(palette = option.palette),
                                ),
                            )
                        },
                    )
                }
        }
        SettingsSection(stringResource(R.string.settings_appearance_texture_title)) {
            TextureOption.entries.forEach { option ->
                ChoiceCard(
                    title = stringResource(option.labelRes),
                    subtitle = null,
                    selected = descriptor.texture == option.texture,
                    enabled = enabled,
                    testTag = option.testTag,
                    onClick = {
                        onIntent(
                            AppearanceInteractionUserIntent.SetThemeDescriptor(
                                descriptor.copy(texture = option.texture),
                            ),
                        )
                    },
                )
            }
        }
        SettingsSection(stringResource(R.string.settings_appearance_density_title)) {
            DensityOption.entries.forEach { option ->
                ChoiceCard(
                    title = stringResource(option.labelRes),
                    subtitle = null,
                    selected = descriptor.density == option.density,
                    enabled = enabled,
                    testTag = option.testTag,
                    onClick = {
                        onIntent(
                            AppearanceInteractionUserIntent.SetThemeDescriptor(
                                descriptor.copy(density = option.density),
                            ),
                        )
                    },
                )
            }
        }
        SettingsSection(stringResource(R.string.settings_appearance_font_title)) {
            FontOption.entries.forEach { option ->
                ChoiceCard(
                    title = stringResource(option.labelRes),
                    subtitle = null,
                    selected = descriptor.fontFamily == option.fontFamily,
                    enabled = enabled,
                    testTag = option.testTag,
                    onClick = {
                        onIntent(
                            AppearanceInteractionUserIntent.SetThemeDescriptor(
                                descriptor.copy(fontFamily = option.fontFamily),
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ReadingSection(
    snapshot: AppearanceInteractionSettingsSnapshot,
    enabled: Boolean,
    onIntent: (AppearanceInteractionUserIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.X12)) {
        Text(
            text = stringResource(R.string.settings_appearance_reading_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = appearanceInk(MaterialTheme.colorScheme),
        )
        SettingsSection(stringResource(R.string.settings_appearance_reading_font_title)) {
            ReadingFontOption.entries.forEach { option ->
                ChoiceCard(
                    title = stringResource(option.labelRes),
                    subtitle = null,
                    selected = snapshot.readingFontScale == option.scale,
                    enabled = enabled,
                    testTag = option.testTag,
                    onClick = {
                        onIntent(AppearanceInteractionUserIntent.SetReadingFontScale(option.scale))
                    },
                )
            }
        }
        SettingsSection(stringResource(R.string.settings_appearance_reading_line_title)) {
            ReadingLineOption.entries.forEach { option ->
                ChoiceCard(
                    title = stringResource(option.labelRes),
                    subtitle = null,
                    selected = snapshot.lineHeight == option.lineHeight,
                    enabled = enabled,
                    testTag = option.testTag,
                    onClick = {
                        onIntent(
                            AppearanceInteractionUserIntent.SetReadingLineHeight(option.lineHeight),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun TagColorSection(snapshot: AppearanceInteractionSettingsSnapshot, enabled: Boolean,
    onIntent: (AppearanceInteractionUserIntent) -> Unit) {
    val nextEnabled = !snapshot.tagChipColorful
    val statusRes = if (snapshot.tagChipColorful) R.string.settings_appearance_enabled
        else R.string.settings_appearance_disabled
    val status = stringResource(statusRes)
    SettingsSection(title = stringResource(R.string.settings_appearance_tag_color_title)) {
        OnSurfaceFocus {
            InkCard(
                onClick = { onIntent(AppearanceInteractionUserIntent.SetTagChipColorful(nextEnabled)) },
                enabled = enabled,
                contentDescription = stringResource(R.string.settings_appearance_tag_color_semantics, status),
                modifier = Modifier.heightIn(min = InkSpacing.TouchTargetMin).semantics { stateDescription = status }
                    .testTag("settings_appearance_tag_color"),
            ) {
                Text(status, color = appearanceInk(MaterialTheme.colorScheme), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_appearance_tag_color_description),
                    style = MaterialTheme.typography.bodySmall, color = appearanceInk(MaterialTheme.colorScheme))
            }
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
                modifier = Modifier.heightIn(min = InkSpacing.TouchTargetMin).semantics { stateDescription = status }
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
            modifier = Modifier.fillMaxWidth().heightIn(min = InkSpacing.TouchTargetMin)
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
    Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.X8)) {
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
private fun ChoiceCard(
    title: String,
    subtitle: String?,
    selected: Boolean,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val selectionRes = if (selected) R.string.settings_appearance_selected
        else R.string.settings_appearance_not_selected
    val selectionText = stringResource(selectionRes)
    val semanticsLabel = if (subtitle.isNullOrBlank()) title else "$title，$subtitle"
    OnSurfaceFocus {
        InkCard(
            onClick = onClick,
            enabled = enabled,
            contentDescription = stringResource(
                R.string.settings_appearance_choice_semantics,
                semanticsLabel,
                selectionText,
            ),
            modifier = Modifier.heightIn(min = InkSpacing.TouchTargetMin).semantics {
                this.selected = selected
                stateDescription = selectionText
            }.testTag(testTag),
        ) {
            Row(
                Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, color = appearanceInk(MaterialTheme.colorScheme))
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = appearanceInk(MaterialTheme.colorScheme),
                        )
                    }
                }
                Text(
                    selectionText,
                    style = MaterialTheme.typography.labelLarge,
                    color = appearanceInk(MaterialTheme.colorScheme),
                )
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

private enum class FactoryPresetOption(
    val descriptor: ThemeDescriptor,
    val titleRes: Int,
    val subtitleRes: Int,
    val testTag: String,
) {
    WENMO_ZHUSHA(
        ThemeDescriptor.WENMO_ZHUSHA,
        R.string.settings_appearance_preset_wenmo_zhusha,
        R.string.settings_appearance_preset_wenmo_zhusha_sub,
        "settings_appearance_preset_wenmo_zhusha",
    ),
    QINGJIAN_YUEBAI(
        ThemeDescriptor.QINGJIAN_YUEBAI,
        R.string.settings_appearance_preset_qingjian_yuebai,
        R.string.settings_appearance_preset_qingjian_yuebai_sub,
        "settings_appearance_preset_qingjian_yuebai",
    ),
    YEHANG_DAILAN(
        ThemeDescriptor.YEHANG_DAILAN,
        R.string.settings_appearance_preset_yehang_dailan,
        R.string.settings_appearance_preset_yehang_dailan_sub,
        "settings_appearance_preset_yehang_dailan",
    ),
    SAIBO_FLUOR(
        ThemeDescriptor.SAIBO_FLUOR,
        R.string.settings_appearance_preset_saibo_fluor,
        R.string.settings_appearance_preset_saibo_fluor_sub,
        "settings_appearance_preset_saibo_fluor",
    ),
}

private enum class ModeOption(val mode: ThemeMode, val labelRes: Int) {
    FOLLOW_SYSTEM(ThemeMode.FOLLOW_SYSTEM, R.string.settings_appearance_mode_follow_system),
    LIGHT(ThemeMode.LIGHT, R.string.settings_appearance_mode_light),
    DARK(ThemeMode.DARK, R.string.settings_appearance_mode_dark),
    ;

    val testTag: String = "settings_appearance_mode_${name.lowercase()}"
}

private enum class AdvancedPaletteOption(val palette: ThemePalette, val labelRes: Int) {
    PAPER_INK(ThemePalette.PAPER_INK, R.string.settings_appearance_palette_paper_ink),
    INDIGO(ThemePalette.INDIGO, R.string.settings_appearance_palette_indigo),
    CYBER(ThemePalette.CYBER, R.string.settings_appearance_palette_cyber),
    MOON_WHITE(ThemePalette.MOON_WHITE, R.string.settings_appearance_palette_moon_white),
    DYNAMIC(ThemePalette.DYNAMIC, R.string.settings_appearance_palette_dynamic),
    ;

    val testTag: String = "settings_appearance_palette_${name.lowercase()}"
}

private enum class TextureOption(val texture: ThemeTexture, val labelRes: Int) {
    SCROLL(ThemeTexture.SCROLL, R.string.settings_appearance_texture_scroll),
    MINIMAL(ThemeTexture.MINIMAL, R.string.settings_appearance_texture_minimal),
    ;

    val testTag: String = "settings_appearance_texture_${name.lowercase()}"
}

private enum class DensityOption(val density: ThemeDensity, val labelRes: Int) {
    STANDARD(ThemeDensity.STANDARD, R.string.settings_appearance_density_standard),
    RELAXED(ThemeDensity.RELAXED, R.string.settings_appearance_density_relaxed),
    COMPACT(ThemeDensity.COMPACT, R.string.settings_appearance_density_compact),
    ;

    val testTag: String = "settings_appearance_density_${name.lowercase()}"
}

private enum class FontOption(val fontFamily: ThemeFontFamily, val labelRes: Int) {
    WENKAI(ThemeFontFamily.WENKAI, R.string.settings_appearance_font_wenkai),
    SYSTEM(ThemeFontFamily.SYSTEM, R.string.settings_appearance_font_system),
    ;

    val testTag: String = "settings_appearance_font_${name.lowercase()}"
}

private enum class ReadingFontOption(val scale: ReadingFontScale, val labelRes: Int) {
    SMALL(ReadingFontScale.SMALL, R.string.settings_appearance_reading_font_small),
    STANDARD(ReadingFontScale.STANDARD, R.string.settings_appearance_reading_font_standard),
    LARGE(ReadingFontScale.LARGE, R.string.settings_appearance_reading_font_large),
    EXTRA_LARGE(ReadingFontScale.EXTRA_LARGE, R.string.settings_appearance_reading_font_extra_large),
    ;

    val testTag: String = "settings_appearance_reading_font_${name.lowercase()}"
}

private enum class ReadingLineOption(val lineHeight: ReadingLineHeight, val labelRes: Int) {
    COMPACT(ReadingLineHeight.COMPACT, R.string.settings_appearance_reading_line_compact),
    STANDARD(ReadingLineHeight.STANDARD, R.string.settings_appearance_reading_line_standard),
    RELAXED(ReadingLineHeight.RELAXED, R.string.settings_appearance_reading_line_relaxed),
    ;

    val testTag: String = "settings_appearance_reading_line_${name.lowercase()}"
}

private const val DURATION_MIN_MS = 200
private const val DURATION_MAX_MS = 2_000
