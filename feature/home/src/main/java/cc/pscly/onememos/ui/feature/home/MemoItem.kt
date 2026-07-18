@file:OptIn(ExperimentalLayoutApi::class)

package cc.pscly.onememos.ui.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import cc.pscly.onememos.core.network.MemosUrls
import cc.pscly.onememos.domain.derived.MarkdownDeriver
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.model.SyncStatus
import cc.pscly.onememos.domain.tag.TagExtractor
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.component.MarkdownPreview
import cc.pscly.onememos.ui.component.TagChip
import cc.pscly.onememos.ui.theme.InkBorder
import cc.pscly.onememos.ui.theme.InkShape
import cc.pscly.onememos.ui.theme.InkSpacing
import cc.pscly.onememos.ui.theme.LocalReadingConfig
import cc.pscly.onememos.ui.util.AutoTagLineHider
import cc.pscly.onememos.ui.util.DateTimeFormatter
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

@Composable
internal fun MemoItem(
    memo: Memo,
    serverBase: String?,
    devKeywordsRaw: String,
    showAutoTagLineInHome: Boolean,
    enableRichPreview: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onOpenMemo: () -> Unit,
    onLongShare: () -> Unit,
    onToggleTag: (String) -> Unit,
    onMoreActions: (() -> Unit)? = null,
) {
    // 选中态视觉统一：primary 8% 底色 + 同色系描边环，所有选中卡片观感一致。
    // 通过局部覆盖 colorScheme.surface 实现底色，避免给 InkCard 增加参数、也避免遮罩盖住正文。
    val baseScheme = MaterialTheme.colorScheme
    val cardScheme =
        remember(baseScheme, selected) {
            if (selected) {
                baseScheme.copy(
                    surface =
                        baseScheme.primary
                            .copy(alpha = 0.08f)
                            .compositeOver(baseScheme.surface),
                )
            } else {
                baseScheme
            }
        }
    val selectedBorder =
        BorderStroke(
            width = InkBorder.Stamp,
            color = baseScheme.primary.copy(alpha = InkBorder.OutlineSelected),
        )
    // 卡片圆角统一走 InkShape 令牌（14dp），不再书写裸值。
    val cardShape = InkShape.Card
    MaterialTheme(colorScheme = cardScheme) {
    val cardContentDescription =
        remember(memo, selectionMode, selected) {
            MemoItemTalkBack.contentDescription(
                memo = memo,
                selectionMode = selectionMode,
                selected = selected,
            )
        }
    val timeLabel = DateTimeFormatter.formatYmdHm(memo.createdAt)
    val statusText =
        MemoItemTalkBack.statusLabel(memo.serverState, memo.syncStatus)

    InkCard(
        modifier =
            (if (selected) Modifier.border(selectedBorder, cardShape) else Modifier)
                .testTag("home_memo_item_${memo.uuid}"),
        onClick = onOpenMemo,
        onLongClick = onLongShare,
        contentDescription = cardContentDescription,
    ) {
        val tags =
            remember(memo.tags, memo.uuid, memo.updatedAt) {
                if (memo.tags.isNotEmpty()) memo.tags else TagExtractor.extractAll(memo.content)
            }
        val visibleTags = remember(tags) { tags.take(5) }
        val moreTags = (tags.size - visibleTags.size).coerceAtLeast(0)

        // 顶部元信息行：时间戳退居左上（次级、安静），右上为更多操作/选择指示。
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (selectionMode) {
                Icon(
                    imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint =
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            } else if (onMoreActions != null) {
                // 更多操作常显但保持弱化；独立可聚焦，不并入卡片合并语义
                IconButton(
                    onClick = onMoreActions,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "更多操作",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(InkSpacing.X8))

        val autoTagKeywords = remember(devKeywordsRaw) { AutoTagLineHider.parseKeywords(devKeywordsRaw) }

        val allImageThumbModels =
            remember(memo.attachments, serverBase) {
                memo.attachments
                    .mapNotNull { a ->
                        // a.* 跨 Gradle 模块后不再允许对属性做 smart cast；用局部变量保持语义不变。
                        val remoteName = a.remoteName
                        val filename = a.filename
                        val cacheModel = usableFileUri(a.cacheUri)
                        when {
                            cacheModel != null -> cacheModel
                            !a.localUri.isNullOrBlank() -> a.localUri
                            !serverBase.isNullOrBlank() &&
                                !remoteName.isNullOrBlank() &&
                                !filename.isNullOrBlank() &&
                                a.mimeType?.startsWith("image/") == true -> {
                                MemosUrls.attachmentFileUrl(
                                    base = serverBase,
                                    attachmentName = remoteName,
                                    filename = filename,
                                    thumbnail = true,
                                )
                            }

                            else -> null
                        }
                    }
            }
        val contentPlaceholder =
            remember(allImageThumbModels, memo.attachments) {
                val hasAttachments = memo.attachments.isNotEmpty()
                val hasImages =
                    allImageThumbModels.isNotEmpty() ||
                        memo.attachments.any { it.mimeType?.startsWith("image/") == true }
                when {
                    hasImages -> "(无文字，含图片)"
                    hasAttachments -> "(无文字，含附件)"
                    else -> "(无文字内容)"
                }
            }
        val basePlainPreview =
            // 同上：用稳定的 uuid/updatedAt 作为触发条件即可。
            remember(memo.plainPreview, memo.uuid, memo.updatedAt) {
                memo.plainPreview.ifBlank { MarkdownDeriver.plainPreview(memo.content, maxChars = 320) }
            }
        val plainPreview =
            remember(basePlainPreview, memo.uuid, memo.updatedAt, showAutoTagLineInHome, autoTagKeywords, contentPlaceholder) {
                val p =
                    if (showAutoTagLineInHome) {
                        basePlainPreview
                    } else {
                        val keys = autoTagKeywords
                        when {
                            keys.isEmpty() || basePlainPreview.isBlank() -> basePlainPreview
                            keys.any { basePlainPreview.contains(it) } ->
                                MarkdownDeriver.plainPreviewSkippingLinesEndingWithKeywords(
                                    markdown = memo.content,
                                    keywords = keys,
                                    maxChars = 320,
                                )

                            else -> basePlainPreview
                        }
                    }
                p.ifBlank { contentPlaceholder }
            }

        // 文本预览统一整行展示；图片区域独立于文本下方，按图片数量自适应排布。
        // 正文字号/行高跟随阅读模式（LocalReadingConfig），由外观设置页切换。
        val readingConfig = LocalReadingConfig.current
        if (enableRichPreview) {
            val displayMarkdown =
                remember(memo.uuid, memo.updatedAt, showAutoTagLineInHome, autoTagKeywords) {
                    if (showAutoTagLineInHome) memo.content else AutoTagLineHider.hideFast(memo.content, autoTagKeywords)
                }
            MarkdownPreview(
                markdown = displayMarkdown,
                placeholder = contentPlaceholder,
                maxBlocks = 4,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = plainPreview,
                style = readingConfig.applyTo(MaterialTheme.typography.bodyMedium),
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (allImageThumbModels.isNotEmpty()) {
            Spacer(modifier = Modifier.height(InkSpacing.X12))
            MemoImageGrid(models = allImageThumbModels)
        }

        if (memo.attachments.isNotEmpty()) {
            Text(
                modifier = Modifier.padding(top = InkSpacing.X8),
                text = "附件：${memo.attachments.size} 个",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 标签行退到内容之后：属于次级元信息，不与正文抢视觉焦点。
        if (visibleTags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(InkSpacing.X12))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.X10),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.X10),
            ) {
                visibleTags.forEach { t ->
                    TagChip(tag = t, onClick = if (selectionMode) null else ({ onToggleTag(t) }))
                }
                if (moreTags > 0) {
                    TagChip(tag = "+$moreTags", label = "+$moreTags")
                }
            }
        }

        // 底部状态行：仅承载同步状态，弱化字号与颜色，退到卡片最底层。
        Row(
            modifier = Modifier.padding(top = InkSpacing.X8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color =
                    when (memo.syncStatus) {
                        SyncStatus.LOCAL_ONLY -> MaterialTheme.colorScheme.onSurfaceVariant
                        SyncStatus.DIRTY -> MaterialTheme.colorScheme.secondary
                        SyncStatus.SYNCING -> MaterialTheme.colorScheme.secondary
                        SyncStatus.SYNCED -> MaterialTheme.colorScheme.onSurfaceVariant
                        SyncStatus.FAILED -> MaterialTheme.colorScheme.error
                    },
            )
            if (memo.syncStatus == SyncStatus.FAILED && !memo.lastSyncError.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(InkSpacing.X8))
                Text(
                    text = "原因：${memo.lastSyncError}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    }
}

/**
 * 图片宫格区域：按图片数量切换布局。
 * - 1 图：通栏大图，默认 3:2 宽高比；
 * - 2 图：等宽并排；
 * - 3~4 图：2×2 宫格；
 * - 5 图及以上：3 列宫格，最多展示 9 张，超出部分在最后一张叠 “+N” 角标。
 */
@Composable
private fun MemoImageGrid(models: List<Any>) {
    when (models.size) {
        1 ->
            MemoImageTile(
                model = models.first(),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 2f),
            )

        2 ->
            Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.X6)) {
                models.forEach { model ->
                    MemoImageTile(
                        model = model,
                        modifier =
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                    )
                }
            }

        in 3..4 ->
            Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.X6)) {
                models.chunked(2).forEach { rowModels ->
                    Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.X6)) {
                        rowModels.forEach { model ->
                            MemoImageTile(
                                model = model,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f),
                            )
                        }
                        // 3 图时第二行缺一张，用空白占位保持 2×2 对齐。
                        repeat(2 - rowModels.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

        else -> {
            val maxVisible = 9
            val visible = models.take(maxVisible)
            val overflow = models.size - visible.size
            Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.X6)) {
                visible.chunked(3).forEachIndexed { rowIndex, rowModels ->
                    Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.X6)) {
                        rowModels.forEachIndexed { columnIndex, model ->
                            val tileIndex = rowIndex * 3 + columnIndex
                            val isLastVisible = tileIndex == visible.lastIndex
                            MemoImageTile(
                                model = model,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f),
                                badge = if (isLastVisible && overflow > 0) "+$overflow" else null,
                            )
                        }
                        repeat(3 - rowModels.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单张图片瓦片：统一 InkShape.Card（14dp）圆角。
 * 加载中/失败均回退到 surfaceVariant 主题色占位，避免白屏。
 */
@Composable
private fun MemoImageTile(
    model: Any,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    val context = LocalContext.current
    val request =
        remember(model, context) {
            // 不显式指定解码尺寸，交由 Coil 按瓦片实际布局约束解析；
            // 首页滚动中大量缩略图出现时，crossfade 动画会带来额外合成成本，这里关闭。
            ImageRequest.Builder(context)
                .data(model)
                .crossfade(false)
                .build()
        }
    Box(modifier = modifier.clip(InkShape.Card)) {
        // 加载中占位：主题化 surfaceVariant，避免“白屏”。
        Surface(
            modifier = Modifier.matchParentSize(),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {}
        SubcomposeAsyncImage(
            model = request,
            contentDescription = "图片预览",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            error = {
                // 加载失败：保留占位底色并叠加弱化图标，避免突兀空白。
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.BrokenImage,
                            contentDescription = "图片加载失败",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            },
        )
        if (badge != null) {
            Surface(
                modifier = Modifier.matchParentSize(),
                color = Color.Black.copy(alpha = 0.35f),
                contentColor = Color.White,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = badge, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

private fun usableFileUri(uri: String?): String? {
    if (uri.isNullOrBlank()) return null
    return runCatching {
        val parsed = android.net.Uri.parse(uri)
        if (!parsed.scheme.equals("file", ignoreCase = true)) return@runCatching null
        // 体验优化：避免在 Compose 组合/滚动路径做磁盘 IO（File.exists）。
        // 约定：cacheUri 写入时保证文件存在；缓存裁剪/清理时会同步清空 DB 字段。
        val path = parsed.path ?: return@runCatching null
        if (path.isBlank()) null else uri
    }.getOrNull()
}
