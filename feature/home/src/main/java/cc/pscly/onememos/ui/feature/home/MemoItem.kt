@file:OptIn(ExperimentalLayoutApi::class)

package cc.pscly.onememos.ui.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.pscly.onememos.core.network.MemosUrls
import cc.pscly.onememos.domain.derived.MarkdownDeriver
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.model.MemoServerState
import cc.pscly.onememos.domain.model.SyncStatus
import cc.pscly.onememos.domain.tag.TagExtractor
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.component.MarkdownPreview
import cc.pscly.onememos.ui.component.TagChip
import cc.pscly.onememos.ui.util.AutoTagLineHider
import cc.pscly.onememos.ui.util.DateTimeFormatter
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
internal fun MemoItem(
    memo: Memo,
    serverBase: String?,
    devKeywordsRaw: String,
    showAutoTagLineInHome: Boolean,
    enableRichPreview: Boolean,
    onOpenMemo: () -> Unit,
    onLongShare: () -> Unit,
    onToggleTag: (String) -> Unit,
) {
    InkCard(onClick = onOpenMemo, onLongClick = onLongShare) {
        val tags =
            // 避免把大字符串（content）作为 remember key，降低 equals 比较的潜在开销。
            remember(memo.tags, memo.uuid, memo.updatedAt) {
                if (memo.tags.isNotEmpty()) memo.tags else TagExtractor.extractAll(memo.content)
            }
        val visibleTags = remember(tags) { tags.take(5) }
        val moreTags = (tags.size - visibleTags.size).coerceAtLeast(0)
        if (visibleTags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                visibleTags.forEach { t ->
                    TagChip(tag = t, onClick = { onToggleTag(t) })
                }
                if (moreTags > 0) {
                    TagChip(tag = "+$moreTags", label = "+$moreTags")
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

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
        val maxThumbs = if (enableRichPreview) 2 else 1
        val imageThumbModels =
            remember(allImageThumbModels, maxThumbs) {
                allImageThumbModels.take(maxThumbs.coerceAtLeast(0))
            }
        val moreImages = (allImageThumbModels.size - imageThumbModels.size).coerceAtLeast(0)
        val context = LocalContext.current
        val thumbSizePx =
            with(LocalDensity.current) {
                // 与布局里固定的缩略图尺寸对齐（76dp / 88dp），让 Coil 直接按目标尺寸解码，减少滚动路径开销。
                if (imageThumbModels.size == 1) 76.dp.roundToPx() else 88.dp.roundToPx()
            }
        val imageThumbRequests =
            remember(imageThumbModels, context, thumbSizePx) {
                imageThumbModels.map { model ->
                    // 首页滚动中大量缩略图出现时，crossfade 动画会带来额外合成成本；这里对缩略图关闭。
                    ImageRequest.Builder(context)
                        .data(model)
                        .size(thumbSizePx)
                        .crossfade(false)
                        .build()
                }
            }
        val hasOneImage = imageThumbRequests.size == 1

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

        if (hasOneImage) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(14.dp)),
                ) {
                    // 缩略图加载/缓存回收时避免“空白块”，给一个静态背景占位。
                    Surface(
                        modifier = Modifier.matchParentSize(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {}
                    AsyncImage(
                        model = imageThumbRequests.first(),
                        contentDescription = "图片预览",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    if (moreImages > 0) {
                        Surface(
                            modifier = Modifier.matchParentSize(),
                            color = Color.Black.copy(alpha = 0.35f),
                            contentColor = Color.White,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = "+$moreImages", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (enableRichPreview) {
                        val displayMarkdown =
                            remember(memo.uuid, memo.updatedAt, showAutoTagLineInHome, autoTagKeywords) {
                                if (showAutoTagLineInHome) memo.content else AutoTagLineHider.hideFast(memo.content, autoTagKeywords)
                            }
                        MarkdownPreview(
                            markdown = displayMarkdown,
                            placeholder = contentPlaceholder,
                            maxBlocks = 3,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = plainPreview,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        } else {
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
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (imageThumbRequests.size >= 2) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    imageThumbRequests.forEach { request ->
                        Box(
                            modifier =
                                Modifier
                                    .size(88.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                        ) {
                            Surface(
                                modifier = Modifier.matchParentSize(),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {}
                            AsyncImage(
                                model = request,
                                contentDescription = "图片预览",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    if (moreImages > 0) {
                        Surface(
                            modifier = Modifier.size(88.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = "+$moreImages", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }

        if (memo.attachments.isNotEmpty()) {
            Text(
                modifier = Modifier.padding(top = 10.dp),
                text = "附件：${memo.attachments.size} 个",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        val statusText =
            if (memo.serverState == MemoServerState.ARCHIVED) {
                when (memo.syncStatus) {
                    SyncStatus.LOCAL_ONLY -> "已归档（本地）"
                    SyncStatus.DIRTY -> "归档中"
                    SyncStatus.SYNCING -> "归档同步中"
                    SyncStatus.SYNCED -> "已归档"
                    SyncStatus.FAILED -> "归档失败"
                }
            } else {
                when (memo.syncStatus) {
                    SyncStatus.LOCAL_ONLY -> "仅本地"
                    SyncStatus.DIRTY -> "待同步"
                    SyncStatus.SYNCING -> "同步中"
                    SyncStatus.SYNCED -> "已同步"
                    SyncStatus.FAILED -> "失败"
                }
            }

        Row(
            modifier = Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    when (memo.syncStatus) {
                        SyncStatus.LOCAL_ONLY -> MaterialTheme.colorScheme.outline
                        SyncStatus.DIRTY -> MaterialTheme.colorScheme.secondary
                        SyncStatus.SYNCING -> MaterialTheme.colorScheme.secondary
                        SyncStatus.SYNCED -> MaterialTheme.colorScheme.outline
                        SyncStatus.FAILED -> MaterialTheme.colorScheme.error
                    },
            )
            if (memo.syncStatus == SyncStatus.FAILED && !memo.lastSyncError.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "原因：${memo.lastSyncError}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(10.dp))
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Text(
                text = DateTimeFormatter.formatYmdHm(memo.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
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
