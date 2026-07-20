@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package cc.pscly.onememos.ui.feature.sharecard

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.component.InkError
import cc.pscly.onememos.ui.component.InkLoading
import cc.pscly.onememos.ui.component.TagChip
import cc.pscly.onememos.ui.theme.InkShape
import cc.pscly.onememos.ui.theme.InkSpacing
import kotlin.math.max
import kotlin.math.min

@Composable
fun ShareCardScreen(
    onBack: () -> Unit,
    viewModel: ShareCardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tabIndex by remember { mutableIntStateOf(0) }

    val h = remember(context) { Toast.makeText(context, "", Toast.LENGTH_SHORT) }

    fun toast(msg: String) {
        h.setText(msg)
        h.show()
    }

    LaunchedEffect(uiState.exportError) {
        val msg = uiState.exportError
        if (!msg.isNullOrBlank()) {
            toast(msg)
            viewModel.consumeExportError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("墨迹卡片") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        enabled = !uiState.loading && uiState.error == null && !uiState.saving,
                        onClick = {
                            viewModel.exportAndSaveToGallery { count ->
                                toast(if (count > 0) "已保存到相册（$count 张）" else "保存失败")
                            }
                        },
                    ) {
                        Icon(imageVector = Icons.Filled.Download, contentDescription = "保存到相册")
                    }
                    IconButton(
                        enabled = !uiState.loading && uiState.error == null && !uiState.saving,
                        onClick = {
                            viewModel.exportAndShare { payload ->
                                when (payload) {
                                    is IntentPayload.ShareImage -> {
                                        val i =
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = payload.mimeType
                                                putExtra(Intent.EXTRA_STREAM, payload.uri)
                                                clipData = ClipData.newUri(context.contentResolver, "share-card", payload.uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                        runCatching {
                                            context.startActivity(Intent.createChooser(i, "分享墨迹卡片"))
                                        }.onFailure {
                                            toast("没有可用的分享方式")
                                        }
                                    }

                                    is IntentPayload.ShareImages -> {
                                        val list = payload.uris
                                        if (list.isEmpty()) {
                                            toast("导出失败")
                                            return@exportAndShare
                                        }
                                        val clip = ClipData.newUri(context.contentResolver, "share-card", list.first())
                                        list.drop(1).forEach { u -> clip.addItem(ClipData.Item(u)) }

                                        val i =
                                            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                                type = payload.mimeType
                                                putParcelableArrayListExtra(
                                                    Intent.EXTRA_STREAM,
                                                    ArrayList(list),
                                                )
                                                clipData = clip
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                        runCatching {
                                            context.startActivity(Intent.createChooser(i, "分享墨迹卡片（多张）"))
                                        }.onFailure {
                                            toast("没有可用的分享方式")
                                        }
                                    }
                                }
                            }
                        },
                    ) {
                        Icon(imageVector = Icons.Filled.Share, contentDescription = "分享")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = InkSpacing.X16, vertical = InkSpacing.X12),
            verticalArrangement = Arrangement.spacedBy(InkSpacing.X12),
        ) {
            if (uiState.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    InkLoading()
                }
                return@Column
            }

            if (!uiState.error.isNullOrBlank()) {
                // 错误在本屏为终态（记录不存在/参数错误），唯一可行动作是返回上一页。
                InkError(
                    message = uiState.error.orEmpty(),
                    onRetry = onBack,
                    retryLabel = "返回",
                )
                return@Column
            }

            // 预览区（支持缩放），不引入过多复杂控件：保持“克制的美感”
            InkCard {
                var scale by remember { mutableStateOf(1f) }
                val transformState =
                    rememberTransformableState { zoomChange, _, _ ->
                        scale = min(2.5f, max(1.0f, scale * zoomChange))
                    }
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            // 一次性布局常量：预览区固定高度（屏幕预览专用，与导出位图尺寸无关）。
                            .height(InkSpacing.ShareCardPreviewHeight)
                            .clip(InkShape.Card)
                            .transformable(transformState),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer(scaleX = scale, scaleY = scale),
                    ) {
                        ShareCardCanvas(state = uiState)
                    }
                }
                Spacer(modifier = Modifier.height(InkSpacing.X10))
                Text(
                    text = "双指缩放预览 · 点右上角可保存/分享",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("模板") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("样式") })
                Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = { Text("更多") })
            }

            when (tabIndex) {
                0 -> ThemesPanel(state = uiState, onSelect = viewModel::setTheme)
                1 -> StylesPanel(state = uiState, onRatio = viewModel::setRatio, onSize = viewModel::setFontSize, onAlign = viewModel::setAlign)
                else -> MorePanel(
                    state = uiState,
                    onLongMode = viewModel::setLongMode,
                    onLongExportMode = viewModel::setLongExportMode,
                    onAuthorName = viewModel::setAuthorName,
                    onQrEnabled = viewModel::setQrEnabled,
                    onQrText = viewModel::setQrText,
                )
            }
        }
    }
}

@Composable
private fun ThemesPanel(
    state: ShareCardUiState,
    onSelect: (ShareCardTheme) -> Unit,
) {
    InkCard {
        Text(text = "主题", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(InkSpacing.X10))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.X10),
        ) {
            ShareCardTheme.entries.forEach { t ->
                TagChip(
                    tag = t.name,
                    label = t.displayName,
                    selected = state.theme == t,
                    onClick = { onSelect(t) },
                )
            }
        }
        Spacer(modifier = Modifier.height(InkSpacing.X8))
        Text(
            text = "提示：有图时推荐用「光影」。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun StylesPanel(
    state: ShareCardUiState,
    onRatio: (ShareCardRatio) -> Unit,
    onSize: (ShareCardFontSize) -> Unit,
    onAlign: (ShareCardAlign) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.X12)) {
        InkCard {
            Text(text = "画布比例", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(InkSpacing.X10))
            Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.X10)) {
                ShareCardRatio.entries.forEach { r ->
                    TagChip(
                        tag = r.name,
                        label = r.displayName,
                        selected = state.ratio == r,
                        onClick = { onRatio(r) },
                    )
                }
            }
        }

        InkCard {
            Text(text = "字体大小", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(InkSpacing.X10))
            Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.X10)) {
                ShareCardFontSize.entries.forEach { s ->
                    TagChip(
                        tag = s.name,
                        label = s.displayName,
                        selected = state.fontSize == s,
                        onClick = { onSize(s) },
                    )
                }
            }
        }

        InkCard {
            Text(text = "对齐方式", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(InkSpacing.X10))
            Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.X10)) {
                ShareCardAlign.entries.forEach { a ->
                    TagChip(
                        tag = a.name,
                        label = a.displayName,
                        selected = state.align == a,
                        onClick = { onAlign(a) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MorePanel(
    state: ShareCardUiState,
    onLongMode: (Boolean) -> Unit,
    onLongExportMode: (ShareCardLongExportMode) -> Unit,
    onAuthorName: (String) -> Unit,
    onQrEnabled: (Boolean) -> Unit,
    onQrText: (String) -> Unit,
) {
    InkCard {
        Text(text = "更多", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(InkSpacing.X10))

        if (state.saving) {
            InkLoading(message = state.exportProgressText ?: "正在生成图片…")
        } else {
            Text(
                text = "导出说明：默认截断长文；开启“长文模式”会导出长图（过长会自动分页）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )

            Spacer(modifier = Modifier.height(InkSpacing.X12))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "长文模式", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = state.longMode,
                    onCheckedChange = { onLongMode(it) },
                )
            }
            Text(
                text = if (state.longMode) "提示：仅“自适应”比例会按内容导出长图。" else "提示：开启后更适合分享长文/清单。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )

            if (state.longMode && state.ratio == ShareCardRatio.AUTO) {
                Spacer(modifier = Modifier.height(InkSpacing.X10))
                Text(
                    text = "长图导出方式",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(InkSpacing.X8))
                Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.X10)) {
                    ShareCardLongExportMode.entries.forEach { m ->
                        TagChip(
                            tag = m.name,
                            label = m.displayName,
                            selected = state.longExportMode == m,
                            onClick = { onLongExportMode(m) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(InkSpacing.X8))
                Text(
                    text = if (state.longExportMode == ShareCardLongExportMode.SINGLE) "提示：内容过长时会自动回退为分页导出。" else "提示：分页最稳，适合超长清单。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            Spacer(modifier = Modifier.height(InkSpacing.X12))
            OutlinedTextField(
                value = state.authorName,
                onValueChange = onAuthorName,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("作者署名（可选）") },
                placeholder = { Text("例如：小陈 / pscly") },
            )

            Spacer(modifier = Modifier.height(InkSpacing.X12))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "显示二维码", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = state.qrEnabled,
                    onCheckedChange = { onQrEnabled(it) },
                )
            }
            if (state.qrEnabled) {
                OutlinedTextField(
                    value = state.qrText,
                    onValueChange = onQrText,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("二维码内容（链接/文本）") },
                    placeholder = { Text("https://...") },
                )
                if (state.qrText.isNotBlank() && state.qrBitmap == null) {
                    Spacer(modifier = Modifier.height(InkSpacing.X6))
                    Text(
                        text = "二维码生成中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            if (!state.lastSavedPath.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(InkSpacing.X8))
                Text(
                    text = "最近导出：${state.lastSavedPath}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}
