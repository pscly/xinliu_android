package cc.pscly.onememos.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import cc.pscly.onememos.domain.model.ThemeTexture
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

    Surface(
        modifier =
            modifier.then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        color = bg,
        contentColor = fg,
        // 标签视觉：更接近“色块”，避免太像胶囊。
        shape = InkShape.Tag,
        border = BorderStroke(InkBorder.Hairline, borderColor),
    ) {
        Text(
            modifier = Modifier.padding(PaddingValues(horizontal = InkSpacing.TagPaddingH, vertical = InkSpacing.TagPaddingV)),
            text = "#$label",
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

private fun tagBackgroundColor(tag: String, selected: Boolean): Color {
    val h = tag.hashCode().absoluteValue % 360
    val saturation = if (selected) 0.52f else 0.40f
    val value = if (selected) 0.92f else 0.86f
    return Color.hsv(h.toFloat(), saturation, value)
}
