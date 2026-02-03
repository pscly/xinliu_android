@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package cc.pscly.onememos.ui.feature.sharecard

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import cc.pscly.onememos.ui.component.TagChip
import cc.pscly.onememos.ui.util.DateTimeFormatter

@Composable
fun ShareCardCanvas(
    state: ShareCardUiState,
    modifier: Modifier = Modifier,
    heightPx: Int? = null,
    wrapContentHeight: Boolean = false,
) {
    val theme = state.theme
    val bg = remember(theme) { themeBackground(theme) }
    val textColor = remember(theme) { themeTextColor(theme) }
    val accent = remember(theme) { themeAccentColor(theme) }

    val baseModifier =
        modifier
            .then(
                if (wrapContentHeight) {
                    // 长图导出时走 wrap-content（由离屏渲染测量真实高度）。
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.fillMaxSize()
                },
            )
            .background(bg)
            .then(
                if (wrapContentHeight) {
                    // 关键点：长图导出时，背景层必须“不参与测量”，否则会被 AT_MOST(最大高度) 撑爆成超长空白。
                    // 因此这里把背景（纸纹/图片蒙版）改成 drawWithCache 绘制。
                    val bgBitmap = state.backgroundBitmap
                    val accentLine = accent.copy(alpha = 0.28f)
                    Modifier.drawWithCache {
                        val img = bgBitmap?.asImageBitmap()
                        val lineColor = Color(0xFF000000).copy(alpha = 0.05f)
                        val stroke = 1.dp.toPx()
                        val marginX = 22.dp.toPx()
                        val lineGap = 26.dp.toPx()

                        onDrawBehind {
                            // 光影：有图就铺底（长图模式不做 blur，避免导出变慢/变重）
                            if (theme == ShareCardTheme.GUANG_YING && img != null) {
                                drawImage(
                                    image = img,
                                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                                )
                                drawRect(
                                    brush =
                                        Brush.verticalGradient(
                                            listOf(
                                                Color(0xFF000000).copy(alpha = 0.55f),
                                                Color(0xFF000000).copy(alpha = 0.35f),
                                            ),
                                        ),
                                )
                            }

                            // 纸纹：几条淡横线 + 左侧朱砂竖线
                            if (theme == ShareCardTheme.SU_LV || theme == ShareCardTheme.XUAN_ZHI) {
                                var y = 24.dp.toPx()
                                while (y < size.height) {
                                    drawLine(
                                        color = lineColor,
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = stroke,
                                    )
                                    y += lineGap
                                }
                                drawLine(
                                    color = accentLine,
                                    start = Offset(marginX, 0f),
                                    end = Offset(marginX, size.height),
                                    strokeWidth = 3.dp.toPx(),
                                )
                            }
                        }
                    }
                } else {
                    Modifier
                },
            )

    Box(modifier = baseModifier) {
        if (!wrapContentHeight && theme == ShareCardTheme.GUANG_YING) {
            ShareCardImageBackground(bitmap = state.backgroundBitmap, overlay = true)
        }

        // “宣纸/素履”做一点轻纹理：几条淡淡的横线 + 左侧朱砂竖线
        if (!wrapContentHeight && (theme == ShareCardTheme.SU_LV || theme == ShareCardTheme.XUAN_ZHI)) {
            PaperLines(accent = accent.copy(alpha = 0.28f))
        }

        val contentPadding = PaddingValues(horizontal = 54.dp, vertical = 56.dp)
        Column(
            modifier =
                Modifier
                    .then(if (wrapContentHeight) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                    .padding(contentPadding),
            verticalArrangement = Arrangement.Top,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.tags.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.tags.take(6), key = { it }) { tag ->
                            TagChip(
                                tag = tag,
                                label = tag,
                                selected = false,
                                onClick = null,
                            )
                        }
                    }
                }

                if (state.photoBitmaps.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.photoBitmaps.take(3), key = { it.hashCode() }) { bmp ->
                            PhotoPolaroid(bitmap = bmp, accent = accent)
                        }
                    }
                }

                Text(
                    text = "心流 · 墨迹",
                    style = MaterialTheme.typography.labelLarge,
                    color = textColor.copy(alpha = 0.70f),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val align = state.align
            val textAlign = if (align == ShareCardAlign.CENTER) TextAlign.Center else TextAlign.Start
            val fontSize =
                when (state.fontSize) {
                    ShareCardFontSize.SMALL -> 18.sp
                    ShareCardFontSize.MEDIUM -> 20.sp
                    ShareCardFontSize.LARGE -> 23.sp
                }

            val maxChars = 140
            val shouldTruncate = !state.longMode && state.content.length > maxChars
            val displayText =
                if (shouldTruncate) {
                    state.content.take(maxChars).trimEnd() + "…"
                } else {
                    state.content
                }

            val hasQuote = remember(state.content) { state.content.lineSequence().any { it.trimStart().startsWith(">") } }
            if (hasQuote && heightPx != null) {
                QuoteWatermark(textColor = textColor.copy(alpha = 0.10f), align = align)
            }

            Text(
                text = displayText.ifBlank { "(空内容)" },
                modifier = Modifier.fillMaxWidth(),
                color = textColor,
                fontSize = fontSize,
                lineHeight = (fontSize.value * 1.55f).sp,
                textAlign = textAlign,
                maxLines = if (state.longMode) Int.MAX_VALUE else maxLinesByHeight(heightPx),
                overflow = TextOverflow.Ellipsis,
            )

            if (!wrapContentHeight) {
                Spacer(modifier = Modifier.weight(1f, fill = true))
            } else {
                Spacer(modifier = Modifier.height(20.dp))
            }

            ShareCardFooter(
                createdAt = state.createdAt,
                theme = theme,
                textColor = textColor.copy(alpha = 0.72f),
                accent = accent,
                align = align,
                authorName = state.authorName,
                showQr = state.qrEnabled,
                qrBitmap = state.qrBitmap,
                truncated = shouldTruncate,
            )
        }
    }
}

@Composable
private fun ShareCardFooter(
    createdAt: Long,
    theme: ShareCardTheme,
    textColor: Color,
    accent: Color,
    align: ShareCardAlign,
    authorName: String,
    showQr: Boolean,
    qrBitmap: Bitmap?,
    truncated: Boolean,
) {
    val date = DateTimeFormatter.formatYmd(createdAt).replace('-', '.')
    val footerAlign = if (align == ShareCardAlign.CENTER) Alignment.CenterHorizontally else Alignment.Start
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = footerAlign,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (align == ShareCardAlign.CENTER) Arrangement.Center else Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val author = authorName.trim()
            if (author.isNotBlank()) {
                Text(
                    text = author,
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (align == ShareCardAlign.LEFT) {
                Text(
                    text = "来自 1memos",
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                )
            }

            Text(
                text = date,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
            )

            val sealText = pickSealText(author)
            SealMark(
                text = sealText,
                accent = if (theme == ShareCardTheme.MO_RAN) accent.copy(alpha = 0.78f) else accent,
            )
        }

        if (truncated) {
            Text(
                text = "长文已截断 · 可开启长文模式导出长图",
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.75f),
                textAlign = if (align == ShareCardAlign.CENTER) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (showQr && qrBitmap != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (align == ShareCardAlign.CENTER) Arrangement.Center else Arrangement.End,
            ) {
                QrBlock(bitmap = qrBitmap, theme = theme)
            }
        }
    }
}

@Composable
private fun SealMark(
    text: String,
    accent: Color,
) {
    Surface(
        color = accent.copy(alpha = 0.92f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            text = text,
            color = Color(0xFFFDFBF7),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun PaperLines(
    accent: Color,
) {
    // 轻量纹理：避免引入大图，且离屏渲染更稳定。
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .alpha(0.22f)
                .padding(horizontal = 0.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        repeat(40) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFF000000).copy(alpha = 0.05f)),
            )
        }
    }

    // 左侧朱砂竖线：与编辑器“信纸”保持品牌一致性
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(start = 22.dp)
                .background(Color.Transparent),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .size(width = 3.dp, height = 520.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent),
        )
    }
}

@Composable
private fun ShareCardImageBackground(
    bitmap: Bitmap?,
    overlay: Boolean,
) {
    if (bitmap == null) return
    val img = bitmap.asImageBitmap()
    Image(
        bitmap = img,
        contentDescription = "背景图",
        modifier =
            Modifier
                .fillMaxSize()
                .blur(24.dp),
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
    )
    if (overlay) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF000000).copy(alpha = 0.55f),
                                Color(0xFF000000).copy(alpha = 0.35f),
                            ),
                        ),
                    ),
        )
    }
}

private fun themeBackground(theme: ShareCardTheme): Color =
    when (theme) {
        ShareCardTheme.SU_LV -> Color(0xFFFDFBF7)
        ShareCardTheme.MO_RAN -> Color(0xFF1A1A1A)
        ShareCardTheme.XUAN_ZHI -> Color(0xFFF7F2E9)
        ShareCardTheme.GUANG_YING -> Color(0xFF101010)
    }

private fun themeTextColor(theme: ShareCardTheme): Color =
    when (theme) {
        ShareCardTheme.MO_RAN, ShareCardTheme.GUANG_YING -> Color(0xFFECECEC)
        else -> Color(0xFF1E1E1E)
    }

private fun themeAccentColor(theme: ShareCardTheme): Color =
    when (theme) {
        ShareCardTheme.MO_RAN -> Color(0xFFB6A37A)
        else -> Color(0xFFCA2A2A) // 朱砂红
    }

private fun maxLinesByHeight(heightPx: Int?): Int {
    // 离屏导出时根据高度给一个粗略上限；预览时走默认。
    return when {
        heightPx == null -> 20
        heightPx >= 2100 -> 36
        heightPx >= 1750 -> 28
        else -> 22
    }
}

private fun pickSealText(author: String): String {
    val a = author.trim()
    if (a.isBlank()) return "录"
    return if (a.length >= 2) a.takeLast(2) else a.takeLast(1)
}

@Composable
private fun PhotoPolaroid(
    bitmap: Bitmap,
    accent: Color,
) {
    val img = bitmap.asImageBitmap()
    Surface(
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        color = Color(0xFFFCFBF7),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                bitmap = img,
                contentDescription = "随笔图片",
                modifier = Modifier.size(108.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(accent.copy(alpha = 0.35f)),
            )
        }
    }
}

@Composable
private fun QrBlock(
    bitmap: Bitmap,
    theme: ShareCardTheme,
) {
    val img = bitmap.asImageBitmap()
    val bg =
        if (theme == ShareCardTheme.MO_RAN || theme == ShareCardTheme.GUANG_YING) {
            Color(0xFFECECEC)
        } else {
            Color(0xFFFFFFFF)
        }
    Surface(
        color = bg.copy(alpha = 0.95f),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 4.dp,
    ) {
        Image(
            bitmap = img,
            contentDescription = "二维码",
            modifier = Modifier.size(92.dp).padding(10.dp),
        )
    }
}

@Composable
private fun QuoteWatermark(
    textColor: Color,
    align: ShareCardAlign,
) {
    val a = if (align == ShareCardAlign.CENTER) Alignment.Center else Alignment.TopStart
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = a) {
        Text(
            text = "“",
            fontSize = 150.sp,
            color = textColor,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = 80.dp, start = 12.dp),
        )
    }
}
