package cc.pscly.onememos.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
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
    durationMs: Int = 600,
) {
    val base = durationMs.coerceIn(200, 2000)
    val enterMs = (base * 0.45f).roundToInt().coerceIn(120, 900)
    val exitMs = (base * 0.35f).roundToInt().coerceIn(120, 900)

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec =
            if (visible) {
                keyframes {
                    durationMillis = enterMs
                    0f at 0
                    1f at (enterMs * 0.35f).roundToInt().coerceIn(80, enterMs)
                }
            } else {
                tween(durationMillis = exitMs)
            },
        label = "stamp_alpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 1.25f,
        // “盖章压下”：先冲击(偏大) -> 轻微回弹(偏小) -> 稳定到 1f
        animationSpec =
            if (visible) {
                keyframes {
                    durationMillis = enterMs
                    1.42f at 0
                    0.94f at (enterMs * 0.58f).roundToInt().coerceIn(120, enterMs)
                    1.00f at enterMs
                }
            } else {
                tween(durationMillis = exitMs)
            },
        label = "stamp_scale",
    )
    val rotation by animateFloatAsState(
        targetValue = if (visible) -12f else -24f,
        animationSpec =
            if (visible) {
                keyframes {
                    durationMillis = enterMs
                    -26f at 0
                    -8f at (enterMs * 0.62f).roundToInt().coerceIn(120, enterMs)
                    -12f at enterMs
                }
            } else {
                tween(durationMillis = exitMs)
            },
        label = "stamp_rotation",
    )

    if (alpha <= 0.01f) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.10f * alpha)),
        contentAlignment = Alignment.Center,
    ) {
        val sealColor = MaterialTheme.colorScheme.primary
        val shape = RoundedCornerShape(10.dp)

        Surface(
            modifier = Modifier
                .size(150.dp)
                .graphicsLayer {
                    this.alpha = alpha
                    this.scaleX = scale
                    this.scaleY = scale
                    this.rotationZ = rotation
                },
            color = sealColor.copy(alpha = 0.10f),
            border = BorderStroke(2.dp, sealColor.copy(alpha = 0.85f)),
            shape = shape,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = text,
                    color = sealColor.copy(alpha = 0.90f),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}
