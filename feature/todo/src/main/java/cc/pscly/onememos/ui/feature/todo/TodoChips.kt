package cc.pscly.onememos.ui.feature.todo

import androidx.compose.runtime.Composable
import cc.pscly.onememos.ui.component.InkChip

/**
 * 兼容旧实现：TodoDialogs 里有大量对 TodoFilterChip 的引用。
 *
 * 说明：
 * - 旧版使用 Card 模拟 Chip；现在统一收敛到 designsystem 的 InkChip，保证风格一致。
 * - 后续若要进一步细分（例如更小的密度/更轻的描边），再在 designsystem 做专用实现即可。
 */
@Composable
internal fun TodoFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    InkChip(
        label = label,
        selected = selected,
        onClick = onClick,
    )
}

