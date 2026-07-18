package cc.pscly.onememos.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import cc.pscly.onememos.ui.theme.InkBorder
import cc.pscly.onememos.ui.theme.InkMotion
import cc.pscly.onememos.ui.theme.InkShape
import cc.pscly.onememos.ui.theme.InkSpacing
import kotlin.math.roundToInt

/**
 * “盖章”浮层动效：短暂出现即可（保存/归档成功后作为仪式感反馈）。
 *
 * 使用方式：
 * - visible=true 时弹出盖章；visible=false 时淡出
 * - 上层负责控制显示时长（例如 200~400ms）
 */
@Composable
fun SealStampOverlay(
    visible: Boolean,
    text: String,
    modifier: Modifier = Modifier,
    durationMs: Int = InkMotion.StampDurationDefaultMs,
) {
    val base = durationMs.coerceIn(InkMotion.StampDurationMinMs, InkMotion.StampDurationMaxMs)
    val enterMs =
        (base * InkMotion.StampEnterRatio).roundToInt()
            .coerceIn(InkMotion.StampSegmentMinMs, InkMotion.StampSegmentMaxMs)
    val exitMs =
        (base * InkMotion.StampExitRatio).roundToInt()
            .coerceIn(InkMotion.StampSegmentMinMs, InkMotion.StampSegmentMaxMs)

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec =
            if (visible) {
                keyframes {
                    durationMillis = enterMs
                    0f at 0
                    1f at (enterMs * InkMotion.StampAlphaKeyRatio).roundToInt().coerceIn(InkMotion.StampAlphaKeyMinMs, enterMs)
                }
            } else {
                tween(durationMillis = exitMs)
            },
        label = "stamp_alpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else InkMotion.StampScaleOut,
        // “盖章压下”：先冲击(偏大) -> 轻微回弹(偏小) -> 稳定到 1f
        animationSpec =
            if (visible) {
                keyframes {
                    durationMillis = enterMs
                    InkMotion.StampScaleInStart at 0
                    InkMotion.StampScaleInMid at
                        (enterMs * InkMotion.StampScaleKeyRatio).roundToInt()
                            .coerceIn(InkMotion.StampSegmentMinMs, enterMs)
                    InkMotion.StampScaleInEnd at enterMs
                }
            } else {
                tween(durationMillis = exitMs)
            },
        label = "stamp_scale",
    )
    val rotation by animateFloatAsState(
        targetValue = if (visible) InkMotion.StampRotationEnd else InkMotion.StampRotationOut,
        animationSpec =
            if (visible) {
                keyframes {
                    durationMillis = enterMs
                    InkMotion.StampRotationStart at 0
                    InkMotion.StampRotationMid at
                        (enterMs * InkMotion.StampRotationKeyRatio).roundToInt()
                            .coerceIn(InkMotion.StampSegmentMinMs, enterMs)
                    InkMotion.StampRotationEnd at enterMs
                }
            } else {
                tween(durationMillis = exitMs)
            },
        label = "stamp_rotation",
    )

    if (alpha <= InkMotion.StampHideAlphaThreshold) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = InkBorder.StampScrim * alpha)),
        contentAlignment = Alignment.Center,
    ) {
        val sealColor = MaterialTheme.colorScheme.primary
        val shape = InkShape.Stamp

        Surface(
            modifier = Modifier
                .size(InkSpacing.StampSize)
                .graphicsLayer {
                    this.alpha = alpha
                    this.scaleX = scale
                    this.scaleY = scale
                    this.rotationZ = rotation
                },
            color = sealColor.copy(alpha = InkBorder.StampFill),
            border = BorderStroke(InkBorder.Stamp, sealColor.copy(alpha = InkBorder.StampOutline)),
            shape = shape,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = text,
                    color = sealColor.copy(alpha = InkBorder.StampText),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}
