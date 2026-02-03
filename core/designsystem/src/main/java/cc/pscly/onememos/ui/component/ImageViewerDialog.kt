@file:OptIn(ExperimentalFoundationApi::class)

package cc.pscly.onememos.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Circle
import androidx.compose.runtime.Immutable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

@Composable
fun ImageViewerDialog(
    models: List<Any>,
    startIndex: Int,
    onDismiss: () -> Unit,
) {
    if (models.isEmpty()) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, models.lastIndex)) { models.size }
            var chromeVisible by remember { mutableStateOf(true) }

            // iOS 相册的“点一下隐藏 UI”，这里也做一个轻量模拟：默认显示，稍后自动隐藏；点一下再显示。
            LaunchedEffect(pagerState.currentPage, chromeVisible) {
                if (!chromeVisible) return@LaunchedEffect
                delay(2200)
                chromeVisible = false
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ZoomableImage(
                    key = page,
                    model = models[page],
                    modifier = Modifier.fillMaxSize(),
                    onToggleChrome = { chromeVisible = !chromeVisible },
                )
            }

            // 顶部控制条：偏 iOS 相册风格（渐变遮罩 + 轻薄按钮），并加一点“朱砂”点缀。
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .alpha(if (chromeVisible) 1f else 0f)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.72f),
                            1f to Color.Transparent,
                        ),
                    )
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = Color.White.copy(alpha = 0.10f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                        ),
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "关闭",
                                tint = Color.White,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // “朱砂”小点：国风很轻，不抢照片注意力
                        Icon(
                            imageVector = Icons.Filled.Circle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            text = "${pagerState.currentPage + 1} / ${models.size}",
                            color = Color.White.copy(alpha = 0.92f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                )
            }

            // 底部轻提示（类似 iOS 的“交互引导”）
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .alpha(if (chromeVisible) 1f else 0f)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.72f),
                        ),
                    )
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                Text(
                    text = "双击放大 · 双指缩放 · 拖动查看",
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun ZoomableImage(
    key: Any,
    model: Any,
    modifier: Modifier = Modifier,
    onToggleChrome: () -> Unit,
) {
    var scale by remember(key) { mutableFloatStateOf(1f) }
    var offsetX by remember(key) { mutableFloatStateOf(0f) }
    var offsetY by remember(key) { mutableFloatStateOf(0f) }
    var size by remember(key) { mutableStateOf(IntSize.Zero) }
    val viewConfig = LocalViewConfiguration.current

    // 轻宣纸边缘：不靠资源图，使用“暖色渐变 + 很淡细边线”模拟纸张边缘质感。
    val paperEdge =
        remember {
            PaperEdgeStyle(
                edgeColor = Color(0xFFF3E7C9),
                lineColor = Color(0xFFB44A3A), // 朱砂偏棕红
            )
        }

    Box(
        modifier =
            modifier
                .background(Color(0xFF050403))
                .graphicsLayer { /* 仅用于承接 drawWithCache 的缓存路径 */ }
                .pointerInput(key) {
                    // 目标：
                    // - 单指：左右滑动交给 HorizontalPager；轻点切换 UI
                    // - 双指：缩放 + 拖动（两指）查看；此时消费事件，避免翻页
                    var lastTapTime = 0L
                    var lastTapX = 0f
                    var lastTapY = 0f

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        val startY = down.position.y
                        val startTime = down.uptimeMillis
                        var tapCandidate = true
                        var transformHappened = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break

                            if (pressed.size >= 2) {
                                // 双指：缩放/拖动（消费事件，禁止翻页）
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                if (zoom != 1f || pan.x != 0f || pan.y != 0f) {
                                    transformHappened = true
                                    tapCandidate = false

                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    offsetX += pan.x
                                    offsetY += pan.y

                                    // 简单边界：避免拖出太远
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                    val maxX = (w * (scale - 1f)) / 2f
                                    val maxY = (h * (scale - 1f)) / 2f
                                    offsetX = offsetX.coerceIn(-maxX, maxX)
                                    offsetY = offsetY.coerceIn(-maxY, maxY)

                                    pressed.forEach { it.consume() }
                                }
                            } else {
                                // 单指：不消费，让 pager 负责滑动；这里只判断是否还能算 tap
                                val dx = pressed[0].position.x - startX
                                val dy = pressed[0].position.y - startY
                                if (hypot(dx.toDouble(), dy.toDouble()) > viewConfig.touchSlop) {
                                    tapCandidate = false
                                }
                            }
                        }

                        // 双击：只有在“没有拖动/缩放”且“没有明显移动”时成立
                        if (tapCandidate && !transformHappened) {
                            val dt = startTime - lastTapTime
                            val dist = hypot((startX - lastTapX).toDouble(), (startY - lastTapY).toDouble()).toFloat()
                            if (dt in 1..320 && dist <= viewConfig.touchSlop * 2f) {
                                // 双击缩放：更像相册的“就地放大/还原”
                                if (scale > 1f) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    scale = 2.5f
                                }
                                lastTapTime = 0L
                            } else {
                                onToggleChrome()
                                lastTapTime = startTime
                                lastTapX = startX
                                lastTapY = startY
                            }
                        }
                    }
                }
                .onSizeChanged { size = it }
                .drawWithCache {
                    val edge = paperEdge.edgeColor
                    val line = paperEdge.lineColor
                    val radius = min(size.width, size.height) * 0.72f
                    val vignette =
                        Brush.radialGradient(
                            colors = listOf(Color.Transparent, edge.copy(alpha = 0.18f)),
                            radius = max(1f, radius),
                        )

                    // 顶部/底部更像宣纸“毛边”发白的感觉：极淡暖色渐变
                    val topFade =
                        Brush.verticalGradient(
                            0f to edge.copy(alpha = 0.12f),
                            1f to Color.Transparent,
                            endY = size.height * 0.18f,
                        )
                    val bottomFade =
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to edge.copy(alpha = 0.12f),
                            startY = size.height * 0.82f,
                        )

                    onDrawWithContent {
                        drawContent()

                        // 纸边光晕
                        drawRect(vignette)
                        drawRect(topFade)
                        drawRect(bottomFade)

                        // 极淡“朱砂细线”框（靠近边缘 6dp），增强国风边界感
                        val inset = 6.dp.toPx()
                        drawRect(
                            color = line.copy(alpha = 0.10f),
                            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                            size = androidx.compose.ui.geometry.Size(
                                width = size.width - inset * 2f,
                                height = size.height - inset * 2f,
                            ),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = model,
            contentDescription = "图片",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
            contentScale = ContentScale.Fit,
        )
    }
}

@Immutable
private data class PaperEdgeStyle(
    val edgeColor: Color,
    val lineColor: Color,
)
