package cc.pscly.onememos.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 国漫风“信纸/奏折”容器：
 * - 自绘背景横线与左侧朱砂竖线
 * - 内容区域可滚动，且横线会随滚动做偏移，保持纸张质感
 *
 * 说明：抽出该容器后，编辑态/只读态/Markdown 预览都能复用同一套底层风格，便于扩展。
 */
@Composable
fun ScrollPaper(
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = 30.sp,
    contentPadding: PaddingValues = PaddingValues(start = 34.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    content: @Composable (contentModifier: Modifier) -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    val lineHeightPx = remember(density, lineHeight) { with(density) { lineHeight.toPx() } }
    val marginLineX = remember(density) { with(density) { 24.dp.toPx() } }

    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val marginColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .drawWithCache {
                // 性能优化：
                // - 旧实现每一帧都用 while 循环画横线；在输入/IME 动画期间会反复触发 draw，容易出现“卡卡的”感受。
                // - 这里把横线“路径”缓存起来（尺寸不变时复用），每帧仅做一次 Y 方向平移即可。
                val strokeWidth = 1.dp.toPx()
                val linesPath = Path().apply {
                    var y = lineHeightPx
                    val endY = size.height + lineHeightPx
                    while (y <= endY) {
                        moveTo(0f, y)
                        lineTo(size.width, y)
                        y += lineHeightPx
                    }
                }

                onDrawBehind {
                    val scrollOffset = scrollState.value.toFloat()
                    val offsetY = -((scrollOffset % lineHeightPx))

                    // 横线：随滚动偏移，像“纸张内容在移动”
                    withTransform({ translate(top = offsetY) }) {
                        drawPath(
                            path = linesPath,
                            color = lineColor,
                            style = Stroke(width = strokeWidth),
                        )
                    }

                    // 左侧朱砂竖线：带一点“奏折”味道
                    drawLine(
                        color = marginColor,
                        start = Offset(marginLineX, 0f),
                        end = Offset(marginLineX, size.height),
                        strokeWidth = strokeWidth,
                    )
                }
            }
            .padding(contentPadding),
    ) {
        content(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        )
    }
}
