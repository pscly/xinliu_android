package cc.pscly.onememos.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

@Composable
fun TagChip(
    tag: String,
    modifier: Modifier = Modifier,
    label: String = tag,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val bg = remember(tag, selected) { tagBackgroundColor(tag = tag, selected = selected) }
    val fg = remember(bg) { if (bg.luminance() > 0.55f) Color(0xFF1C1C1C) else Color(0xFFF3F3F3) }
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = if (selected) 0.80f else 0.35f)
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
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            modifier = Modifier.padding(PaddingValues(horizontal = 10.dp, vertical = 6.dp)),
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
