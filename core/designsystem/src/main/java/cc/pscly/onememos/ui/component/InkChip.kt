package cc.pscly.onememos.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import cc.pscly.onememos.ui.accessibility.PaperInkFocusIndicator
import cc.pscly.onememos.ui.theme.InkBorder
import cc.pscly.onememos.ui.theme.InkShape
import cc.pscly.onememos.ui.theme.InkSpacing
import cc.pscly.onememos.ui.theme.LocalInkDisabledColors

/**
 * 国漫风“墨迹筛选 Chip”：
 * - 用于清单/状态等轻量筛选（比 TagChip 更克制、更接近纸上标注）
 * - 视觉语言：描边 + 轻量选中底色 + 无系统 ripple
 * - 触控：外层 ≥48dp 语义/触控区，内层 Surface 保持紧凑视觉
 * - 焦点：keyboard/D-pad 时叠加 [PaperInkFocusIndicator.paperInkFocusBorder]
 * - 禁用：文字/描边改用 [LocalInkDisabledColors] 内容色（M3 onSurface×0.38）
 * - 选中：暴露 Selected 与 stateDescription（非仅靠颜色）
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
    val focused by interactionSource.collectIsFocusedAsState()
    val disabledColors = LocalInkDisabledColors.current
    val clickable = enabled

    val borderAlpha = if (selected) InkBorder.OutlineSelected else InkBorder.OutlineIdle
    val containerAlpha = if (selected) InkBorder.ChipFillSelected else 0.00f
    val borderColor =
        if (enabled) {
            MaterialTheme.colorScheme.outline.copy(alpha = borderAlpha)
        } else {
            disabledColors.content
        }
    val containerColor =
        if (enabled) {
            MaterialTheme.colorScheme.primary.copy(alpha = containerAlpha)
        } else {
            // 禁用态容器保持透明/不强调选中填充，仅以描边+文字表达禁用
            MaterialTheme.colorScheme.primary.copy(alpha = 0f)
        }
    val textColor =
        if (!enabled) {
            disabledColors.content
        } else if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    // 外层 ≥48dp 触控/语义区；内层 Surface 保持紧凑视觉。
    Box(
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .defaultMinSize(
                    minWidth = InkSpacing.TouchTargetMin,
                    minHeight = InkSpacing.TouchTargetMin,
                )
                .semantics {
                    role = Role.Button
                    this.selected = selected
                    stateDescription = if (selected) "已选中" else "未选中"
                    if (!enabled) disabled()
                }
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier =
                Modifier.then(
                    with(PaperInkFocusIndicator) {
                        Modifier.paperInkFocusBorder(
                            focused = focused && clickable,
                            shape = shape,
                        )
                    },
                ),
            shape = shape,
            color = containerColor,
            border = BorderStroke(InkBorder.Hairline, borderColor),
        ) {
            Text(
                modifier =
                    Modifier.padding(
                        PaddingValues(
                            horizontal = InkSpacing.ChipPaddingH,
                            vertical = InkSpacing.ChipPaddingV,
                        ),
                    ),
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                maxLines = 1,
            )
        }
    }
}
