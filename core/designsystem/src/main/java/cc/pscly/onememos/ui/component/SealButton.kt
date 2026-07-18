package cc.pscly.onememos.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import cc.pscly.onememos.ui.theme.InkBorder
import cc.pscly.onememos.ui.theme.InkMotion
import cc.pscly.onememos.ui.theme.InkShape
import cc.pscly.onememos.ui.theme.InkSpacing
import cc.pscly.onememos.ui.accessibility.ReducedMotion
import cc.pscly.onememos.ui.util.rememberOneMemosHaptics

@Composable
fun SealButton(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = InkSpacing.SealButtonSize,
    enabled: Boolean = true,
    contentDescription: String = text,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val reduceMotion = ReducedMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !reduceMotion) InkMotion.PressScale else 1f,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = InkMotion.PressDurationMs),
        label = "seal_scale",
    )
    val haptics = rememberOneMemosHaptics()
    val textStyle: TextStyle =
        if (size <= InkSpacing.SealCompactThreshold) {
            MaterialTheme.typography.titleMedium
        } else {
            MaterialTheme.typography.titleLarge
        }
    val shape = InkShape.sealFor(size)

    // 外层保证 ≥48dp 触控区；内层保留视觉 size（可小于 48dp）。
    Box(
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .defaultMinSize(minWidth = InkSpacing.TouchTargetMin, minHeight = InkSpacing.TouchTargetMin)
                .semantics {
                    this.contentDescription = contentDescription
                    role = Role.Button
                    if (!enabled) disabled()
                }
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                ) {
                    if (!reduceMotion) {
                        haptics.tick()
                    }
                    onClick()
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(shape)
                    .background(
                        if (enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    )
                    .then(
                        if (focused) {
                            Modifier.border(InkBorder.Hairline, MaterialTheme.colorScheme.primary, shape)
                        } else {
                            Modifier
                        },
                    ),
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
}
