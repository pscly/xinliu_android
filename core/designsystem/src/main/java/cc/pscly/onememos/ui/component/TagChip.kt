package cc.pscly.onememos.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Color
import cc.pscly.onememos.ui.accessibility.PaperInkFocusIndicator
import cc.pscly.onememos.ui.theme.InkBorder
import cc.pscly.onememos.ui.theme.InkShape
import cc.pscly.onememos.ui.theme.InkSpacing
import cc.pscly.onememos.ui.theme.InkTone
import cc.pscly.onememos.ui.theme.LocalTagChipColorful
import kotlin.math.abs

@Composable
fun TagChip(
    tag: String,
    modifier: Modifier = Modifier,
    label: String = tag,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    // 标签彩色开关（设置-外观交互）：开启时按标签名 HSV 哈希生成柔和粉彩色；
    // 关闭时统一退后灰，标签作为卡片内的次要信息安静呈现。
    val colorful = LocalTagChipColorful.current
    val bg: Color
    val fg: Color
    if (colorful) {
        // 彩色模式：HSV 哈希粉彩，未选中更浅、选中略饱和；底色亮度恒高，文字恒用深墨。
        val hash = abs(tag.hashCode()) % 360
        bg =
            if (selected) {
                Color.hsv(hash.toFloat(), 0.26f, 0.92f)
            } else {
                Color.hsv(hash.toFloat(), 0.16f, 0.86f)
            }
        fg = InkTone.TagTextOnLight
    } else {
        // 退后模式：未选中 surfaceVariant 低透明度底 + 次要文字色；
        // 选中 primary 低填充 + primary 文字（与 InkChip 选中口径一致）。
        bg =
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = InkBorder.ChipFillSelected)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            }
        fg =
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
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
        // 视觉高度由内容决定（文字行高 + TagPaddingV×2 ≈ 24dp）：memo 卡内标签为次要交互，
        // 整卡可点进详情，产品决策不套用 48dp 最小触控目标；无障碍语义由下方 semantics 保留。
        modifier =
            modifier
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
