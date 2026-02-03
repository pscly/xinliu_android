package cc.pscly.onememos.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import cc.pscly.onememos.ui.util.rememberOneMemosHaptics

@Composable
fun SealButton(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        label = "seal_scale",
    )
    val haptics = rememberOneMemosHaptics()
    val corner = if (size <= 44.dp) 12.dp else 14.dp
    val textStyle: TextStyle = if (size <= 44.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(corner))
            .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
            ) {
                // 盖章感：轻量震动 + 缩放顿挫（成功反馈由各业务页在事件结束时触发）
                haptics.tick()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onPrimary,
            style = textStyle,
            fontWeight = FontWeight.Bold,
        )
    }
}
