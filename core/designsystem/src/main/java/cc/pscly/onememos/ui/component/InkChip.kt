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
import cc.pscly.onememos.ui.theme.InkBorder
import cc.pscly.onememos.ui.theme.InkShape
import cc.pscly.onememos.ui.theme.InkSpacing

/**
 * 国漫风“墨迹筛选 Chip”：
 * - 用于清单/状态等轻量筛选（比 TagChip 更克制、更接近纸上标注）
 * - 视觉语言：描边 + 轻量选中底色 + 无系统 ripple
 */
@Composable
fun InkChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = InkShape.Chip
    val interactionSource = remember { MutableInteractionSource() }

    val borderAlpha = if (selected) InkBorder.OutlineSelected else InkBorder.OutlineIdle
    val containerAlpha = if (selected) InkBorder.ChipFillSelected else 0.00f
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = borderAlpha)
    val containerColor = MaterialTheme.colorScheme.primary.copy(alpha = containerAlpha)
    val textColor =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Surface(
        modifier =
            modifier.clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = shape,
        color = containerColor,
        border = BorderStroke(InkBorder.Hairline, borderColor),
    ) {
        Text(
            modifier = Modifier.padding(PaddingValues(horizontal = InkSpacing.ChipPaddingH, vertical = InkSpacing.ChipPaddingV)),
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            maxLines = 1,
        )
    }
}

