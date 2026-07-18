package cc.pscly.onememos.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import cc.pscly.onememos.domain.model.ThemeTexture
import cc.pscly.onememos.ui.theme.InkBorder
import cc.pscly.onememos.ui.theme.InkShape
import cc.pscly.onememos.ui.theme.InkSpacing
import cc.pscly.onememos.ui.theme.LocalThemeTexture
/**
 * 国漫风“信纸/奏折”背景（无滚动版）：
 * - 仅负责绘制宣纸底色、横线、左侧朱砂竖线与外边框
 * - 不包含 scroll 行为，便于与 LazyColumn 等虚拟列表组合使用
 *
 * 质感分支：
 * - [ThemeTexture.SCROLL]：保持上述卷轴表现；
 * - [ThemeTexture.MINIMAL]：清简风，不绘横线与朱砂竖线，仅保留发丝边框，
 *   默认内边距改用 InkSpacing 的 Minimal 语义别名（更大留白）。
 *
 * @param scrollOffsetPx 用于驱动横线偏移的“累计滚动像素”。可通过 nestedScroll 记录滚动 delta 得到连续值。
 */
@Composable
fun ScrollPaperSurface(
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = InkSpacing.LinePitch,
    scrollOffsetPx: Float = 0f,
    contentPadding: PaddingValues =
        if (LocalThemeTexture.current == ThemeTexture.MINIMAL) {
            PaddingValues(
                start = InkSpacing.PaperPaddingStartMinimal,
                end = InkSpacing.PaperPaddingEndMinimal,
                top = InkSpacing.PaperPaddingVMinimal,
                bottom = InkSpacing.PaperPaddingVMinimal,
            )
        } else {
            PaddingValues(
                start = InkSpacing.PaperPaddingStart,
                end = InkSpacing.PaperPaddingEnd,
                top = InkSpacing.PaperPaddingV,
                bottom = InkSpacing.PaperPaddingV,
            )
        },
    content: @Composable () -> Unit,
) {
    val texture = LocalThemeTexture.current
    val shape = InkShape.Paper
    val density = LocalDensity.current

    val lineHeightPx = remember(density, lineHeight) { with(density) { lineHeight.toPx() } }
    val marginLineX = remember(density) { with(density) { InkSpacing.MarginLineX.toPx() } }

    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = InkBorder.OutlineStrong)
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = InkBorder.OutlineSoft)
    val marginColor = MaterialTheme.colorScheme.primary.copy(alpha = InkBorder.MarginLine)

    // 清简质感：无横线、无朱砂竖线，跳过重绘缓存以省去每帧绘制
    val textureModifier =
        if (texture == ThemeTexture.SCROLL) {
            Modifier.drawWithCache {
                val strokeWidth = InkBorder.Hairline.toPx()
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
                    // scrollOffsetPx 可能为负：取模后做一次归一化，保证 offset 落在 [0, lineHeightPx)。
                    val mod = ((scrollOffsetPx % lineHeightPx) + lineHeightPx) % lineHeightPx
                    val offsetY = -mod

                    withTransform({ translate(top = offsetY) }) {
                        drawPath(
                            path = linesPath,
                            color = lineColor,
                            style = Stroke(width = strokeWidth),
                        )
                    }

                    drawLine(
                        color = marginColor,
                        start = Offset(marginLineX, 0f),
                        end = Offset(marginLineX, size.height),
                        strokeWidth = strokeWidth,
                    )
                }
            }
        } else {
            Modifier
        }

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(width = InkBorder.Hairline, color = borderColor, shape = shape)
            .then(textureModifier)
            .padding(contentPadding),
    ) {
        content()
    }
}
