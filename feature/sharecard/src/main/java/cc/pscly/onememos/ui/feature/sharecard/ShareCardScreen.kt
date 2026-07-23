@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package cc.pscly.onememos.ui.feature.sharecard

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.component.InkError
import cc.pscly.onememos.ui.component.InkLoading
import cc.pscly.onememos.ui.component.TagChip
import cc.pscly.onememos.ui.theme.InkShape
import cc.pscly.onememos.ui.theme.InkSpacing
import cc.pscly.onememos.ui.theme.PaperInkTopAppBar
import kotlin.math.max
import kotlin.math.min

// region Production wrapper (Hilt, Context, Toast, Intents, LaunchedEffect)

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

    ShareCardScreenContent(
        uiState = uiState,
        selectedTabIndex = tabIndex,
        onTabSelected = { tabIndex = it },
        onBack = onBack,
        onSaveToGallery = {
            viewModel.exportAndSaveToGallery { count ->
                toast(if (count > 0) "已保存到相册（$count 张）" else "保存失败")
            }
        },
        onShare = {
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
        onThemeSelected = viewModel::setTheme,
        onRatioSelected = viewModel::setRatio,
        onFontSizeSelected = viewModel::setFontSize,
        onAlignSelected = viewModel::setAlign,
        onLongModeChanged = viewModel::setLongMode,
        onLongExportModeChanged = viewModel::setLongExportMode,
        onAuthorNameChanged = viewModel::setAuthorName,
        onQrEnabledChanged = viewModel::setQrEnabled,
        onQrTextChanged = viewModel::setQrText,
    )
}

// endregion

// region ShareCardScreenContent (testable, no Hilt/Context dependency)

@Composable
internal fun ShareCardScreenContent(
    uiState: ShareCardUiState,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onBack: () -> Unit,
    onSaveToGallery: () -> Unit,
    onShare: () -> Unit,
    onThemeSelected: (ShareCardTheme) -> Unit,
    onRatioSelected: (ShareCardRatio) -> Unit,
    onFontSizeSelected: (ShareCardFontSize) -> Unit,
    onAlignSelected: (ShareCardAlign) -> Unit,
    onLongModeChanged: (Boolean) -> Unit,
    onLongExportModeChanged: (ShareCardLongExportMode) -> Unit,
    onAuthorNameChanged: (String) -> Unit,
    onQrEnabledChanged: (Boolean) -> Unit,
    onQrTextChanged: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            PaperInkTopAppBar(
                title = { Text("墨迹卡片") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        enabled = !uiState.loading && uiState.error == null && !uiState.saving,
                        onClick = onSaveToGallery,
                    ) {
                        Icon(imageVector = Icons.Filled.Download, contentDescription = "保存到相册")
                    }
                    IconButton(
                        enabled = !uiState.loading && uiState.error == null && !uiState.saving,
                        onClick = onShare,
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
                    .padding(horizontal = InkSpacing.X16, vertical = InkSpacing.X12)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(InkSpacing.X12),
        ) {
            if (uiState.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    InkLoading()
                }
                return@Column
            }

            if (!uiState.error.isNullOrBlank()) {
                InkError(
                    message = uiState.error.orEmpty(),
                    onRetry = onBack,
                    retryLabel = "返回",
                )
                return@Column
            }

            // 预览区（支持缩放）
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

            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(selected = selectedTabIndex == 0, onClick = { onTabSelected(0) }, text = { Text("模板") })
                Tab(selected = selectedTabIndex == 1, onClick = { onTabSelected(1) }, text = { Text("样式") })
                Tab(selected = selectedTabIndex == 2, onClick = { onTabSelected(2) }, text = { Text("更多") })
            }

            when (selectedTabIndex) {
                0 -> ThemesPanel(state = uiState, onSelect = onThemeSelected)
                1 -> StylesPanel(
                    state = uiState,
                    onRatio = onRatioSelected,
                    onSize = onFontSizeSelected,
                    onAlign = onAlignSelected,
                )
                else -> MorePanel(
                    state = uiState,
                    onLongMode = onLongModeChanged,
                    onLongExportMode = onLongExportModeChanged,
                    onAuthorName = onAuthorNameChanged,
                    onQrEnabled = onQrEnabledChanged,
                    onQrText = onQrTextChanged,
                )
            }
        }
    }
}

// endregion

// region Panels (callback-based, FlowRow for chip groups)

@Composable
private fun ThemesPanel(
    state: ShareCardUiState,
    onSelect: (ShareCardTheme) -> Unit,
) {
    InkCard {
        Text(text = "主题", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(InkSpacing.X10))
        FlowRow(
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
            FlowRow(
                modifier = Modifier.fillMaxWidth().testTag("flow_row_ratios"),
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.X10),
                maxItemsInEachRow = 2,
            ) {
                ShareCardRatio.entries.forEach { r ->
                    TagChip(
                        modifier = Modifier.testTag("chip_ratio_${r.name}"),
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
            FlowRow(
                modifier = Modifier.fillMaxWidth().testTag("flow_row_font_sizes"),
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.X10),
            ) {
                ShareCardFontSize.entries.forEach { s ->
                    TagChip(
                        modifier = Modifier.testTag("chip_font_size_${s.name}"),
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
            FlowRow(
                modifier = Modifier.fillMaxWidth().testTag("flow_row_aligns"),
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.X10),
            ) {
                ShareCardAlign.entries.forEach { a ->
                    TagChip(
                        modifier = Modifier.testTag("chip_align_${a.name}"),
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
    InkCard(
        modifier = Modifier.testTag("more_panel"),
    ) {
        Text(text = "更多", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(InkSpacing.X10))

        if (state.saving) {
            InkLoading(message = state.exportProgressText ?: "正在生成图片…")
        } else {
            Text(
                text = "导出说明：默认截断长文；开启\u201C长文模式\u201D会导出长图（过长会自动分页）。",
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
                text = if (state.longMode) "提示：仅\u201C自适应\u201D比例会按内容导出长图。" else "提示：开启后更适合分享长文/清单。",
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
                FlowRow(
                    modifier = Modifier.testTag("flow_row_long_export_modes"),
                    horizontalArrangement = Arrangement.spacedBy(InkSpacing.X10),
                ) {
                    ShareCardLongExportMode.entries.forEach { m ->
                        TagChip(
                            modifier = Modifier.testTag("chip_long_export_${m.name}"),
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

// endregion
