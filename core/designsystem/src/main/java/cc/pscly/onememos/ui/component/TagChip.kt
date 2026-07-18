package cc.pscly.onememos.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import cc.pscly.onememos.domain.model.ThemeTexture
import cc.pscly.onememos.ui.accessibility.PaperInkFocusIndicator
import cc.pscly.onememos.ui.theme.InkBorder
import cc.pscly.onememos.ui.theme.InkShape
import cc.pscly.onememos.ui.theme.InkSpacing
import cc.pscly.onememos.ui.theme.InkTone
import cc.pscly.onememos.ui.theme.LocalThemeTexture
import kotlin.math.absoluteValue

@Composable
fun TagChip(
    tag: String,
    modifier: Modifier = Modifier,
    label: String = tag,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val texture = LocalThemeTexture.current
    // 清简质感：标签退后，使用 surface/次要文字色 + outline 描边，不再按 hash 生成彩虹底色
    val minimal = texture == ThemeTexture.MINIMAL
    val bg =
        if (minimal) {
            MaterialTheme.colorScheme.surface
        } else {
            remember(tag, selected) { tagBackgroundColor(tag = tag, selected = selected) }
        }
    val fg =
        if (minimal) {
            if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            remember(bg) { if (bg.luminance() > 0.55f) InkTone.TagTextOnLight else InkTone.TagTextOnDark }
        }
    val borderColor =
        MaterialTheme.colorScheme.outline.copy(
            alpha = if (selected) InkBorder.OutlineSelected else InkBorder.TagIdle,
        )
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val displayLabel = "#$label"
    val a11yDescription = if (selected) "标签 $displayLabel，已选中" else "标签 $displayLabel"
    val clickable = onClick != null
    val shape = InkShape.Tag

    Surface(
        modifier =
            modifier
                .then(
                    if (clickable) {
                        Modifier
                            .minimumInteractiveComponentSize()
                            .defaultMinSize(
                                minWidth = InkSpacing.TouchTargetMin,
                                minHeight = InkSpacing.TouchTargetMin,
                            )
                    } else {
                        Modifier
                    },
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = a11yDescription
                    if (selected) {
                        stateDescription = "已选中"
                    }
                    if (clickable) {
                        role = Role.Button
                    }
                }
                .then(
                    if (clickable) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Button,
                            onClick = onClick!!,
                        )
                    } else {
                        Modifier
                    },
                )
                .then(
                    with(PaperInkFocusIndicator) {
                        Modifier.paperInkFocusBorder(
                            focused = focused && clickable,
                            shape = shape,
                        )
                    },
                ),
        color = bg,
        contentColor = fg,
        // 标签视觉：更接近“色块”，避免太像胶囊。
        shape = shape,
        border = BorderStroke(InkBorder.Hairline, borderColor),
    ) {
        Text(
            modifier = Modifier.padding(PaddingValues(horizontal = InkSpacing.TagPaddingH, vertical = InkSpacing.TagPaddingV)),
            text = displayLabel,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

private fun tagBackgroundColor(tag: String, selected: Boolean): Color {
    val h = tag.hashCode().absoluteValue % 360
    // 文墨质感：哈希彩色降饱和收敛，安静退后，仅保留轻微色彩倾向
    val saturation = if (selected) 0.26f else 0.16f
    val value = if (selected) 0.92f else 0.86f
    return Color.hsv(h.toFloat(), saturation, value)
}
