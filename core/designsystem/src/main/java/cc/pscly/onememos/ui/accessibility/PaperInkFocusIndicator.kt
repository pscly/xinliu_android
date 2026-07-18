package cc.pscly.onememos.ui.accessibility

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import cc.pscly.onememos.ui.theme.InkBorder
import cc.pscly.onememos.ui.theme.InkShape

/**
 * 纸墨主题焦点环：键盘 / D-pad / 焦点导航时可见的描边指示。
 *
 * 设计约束（对齐 DESIGN.md 深度策略）：
 * - 仅用 [colorScheme.primary] 细描边，无阴影、无光晕、无渐变；
 * - 宽度取自 [InkBorder] 令牌，与卡片/印章描边体系统一；
 * - 供 InkCard / SealButton / TagChip 等原语复用，避免各组件硬编码边框。
 */
object PaperInkFocusIndicator {
    /** 常规交互控件焦点环宽度（发丝线）。 */
    val StrokeWidth: Dp = InkBorder.Hairline

    /** 印章类强调焦点环宽度（与盖章印记描边一致）。 */
    val EmphasizedStrokeWidth: Dp = InkBorder.Stamp

    /** 焦点环颜色：始终跟随主题 primary（朱砂/黛蓝等）。 */
    @Composable
    fun color(): Color = MaterialTheme.colorScheme.primary

    /**
     * 聚焦时叠加纸墨焦点环；未聚焦返回原 [Modifier]（零分配分支）。
     *
     * @param focused 是否处于焦点态
     * @param shape 描边形状，默认卡片圆角
     * @param emphasized 印章按钮等需要更醒目描边时为 true
     */
    @Composable
    fun Modifier.paperInkFocusBorder(
        focused: Boolean,
        shape: Shape = InkShape.Card,
        emphasized: Boolean = false,
    ): Modifier {
        if (!focused) return this
        return border(
            width = if (emphasized) EmphasizedStrokeWidth else StrokeWidth,
            color = color(),
            shape = shape,
        )
    }
}
