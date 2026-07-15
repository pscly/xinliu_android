package cc.pscly.onememos.ui.feature.sharecard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.pscly.onememos.domain.repository.MemoRepository
import cc.pscly.onememos.navigation.ShareCardKey
import cc.pscly.onememos.domain.repository.SettingsRepository
import cc.pscly.onememos.domain.tag.TagExtractor
import cc.pscly.onememos.ui.theme.OneMemosTheme
import cc.pscly.onememos.ui.theme.OneMemosThemeConfig
import cc.pscly.onememos.ui.util.AutoTagLineHider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ShareCardViewModel @Inject constructor(
    @ApplicationContext appContext: Context,
    private val memoRepository: MemoRepository,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    // ViewModel 里持有的是 ApplicationContext，不会泄漏 Activity；这里抑制 lint 的误报。
    @SuppressLint("StaticFieldLeak")
    private val context: Context = appContext

    private val uuidArg: String? =
        savedStateHandle.get<String>("uuid")?.let { raw ->
            runCatching { Uri.decode(raw) }.getOrDefault(raw)
        }

    private val _uiState = MutableStateFlow(ShareCardUiState(uuid = uuidArg, loading = true))
    val uiState: StateFlow<ShareCardUiState> = _uiState.asStateFlow()

    private var boundKey: ShareCardKey? = null
    private var loadStartedFor: String? = null

    fun bind(key: ShareCardKey) {
        if (boundKey == key) return
        boundKey = key
        val uuid = key.uuid.trim().takeIf { it.isNotBlank() } ?: return
        if (loadStartedFor == uuid) return
        loadStartedFor = uuid
        _uiState.update { it.copy(uuid = uuid, loading = true, error = null) }
        viewModelScope.launch {
            val memo = memoRepository.getMemo(uuid)
            if (memo == null) {
                _uiState.update { it.copy(loading = false, error = "记录不存在或已被删除") }
                return@launch
            }
            val settings = settingsRepository.settings.first()
            val keywords = AutoTagLineHider.parseKeywords(settings.devAutoTagLineKeywords)
            val content =
                if (settings.devShowAutoTagLineInView) {
                    memo.content
                } else {
                    AutoTagLineHider.hideFast(memo.content, keywords)
                }
            val tags = TagExtractor.extractAll(content)
            val photos = withContext(Dispatchers.IO) { loadUsableImages(memo.attachments, maxCount = 3, maxSizePx = 720) }
            val background = photos.firstOrNull()
            val theme = if (background != null) ShareCardTheme.GUANG_YING else ShareCardTheme.SU_LV
            _uiState.update {
                it.copy(
                    loading = false,
                    error = null,
                    content = content,
                    createdAt = memo.createdAt,
                    tags = tags,
                    backgroundBitmap = background,
                    photoBitmaps = photos,
                    qrEnabled = true,
                    qrText = "https://github.com/pscly/onememos",
                    theme = theme,
                )
            }
            refreshQr()
        }
    }

    // 体验优化：二维码生成可能被频繁触发（开关/文本变化）。用递增 token 丢弃过期结果，避免“旧二维码闪回”。
    private var qrGenerationId: Int = 0

    init {
        val uuid = uuidArg?.trim().takeIf { !it.isNullOrBlank() }
        if (uuid != null) loadStartedFor = uuid
        if (uuid == null) {
            _uiState.update { it.copy(loading = false, error = "参数错误：uuid 为空") }
        } else {
            viewModelScope.launch {
                val memo = memoRepository.getMemo(uuid)
                if (memo == null) {
                    _uiState.update { it.copy(loading = false, error = "记录不存在或已被删除") }
                    return@launch
                }

                val settings = settingsRepository.settings.first()
                val keywords = AutoTagLineHider.parseKeywords(settings.devAutoTagLineKeywords)
                val content =
                    if (settings.devShowAutoTagLineInView) {
                        memo.content
                    } else {
                        AutoTagLineHider.hideFast(memo.content, keywords)
                    }

                val tags = TagExtractor.extractAll(content)
                val photos = withContext(Dispatchers.IO) { loadUsableImages(memo.attachments, maxCount = 3, maxSizePx = 720) }
                val background = photos.firstOrNull()
                val theme = if (background != null) ShareCardTheme.GUANG_YING else ShareCardTheme.SU_LV

                _uiState.update {
                    it.copy(
                        loading = false,
                        error = null,
                        exportError = null,
                        exportProgressText = null,
                        content = content,
                        createdAt = memo.createdAt,
                        tags = tags,
                        backgroundBitmap = background,
                        photoBitmaps = photos,
                        // 默认开启二维码：作为“品牌印记”。不喜欢可在“更多”里关掉。
                        qrEnabled = true,
                        qrText = "https://github.com/pscly/onememos",
                        theme = theme,
                    )
                }

                refreshQr()
            }
        }
    }

    fun consumeExportError() {
        _uiState.update { it.copy(exportError = null) }
    }

    fun setTheme(theme: ShareCardTheme) {
        _uiState.update { it.copy(theme = theme) }
    }

    fun setRatio(ratio: ShareCardRatio) {
        _uiState.update { it.copy(ratio = ratio) }
    }

    fun setFontSize(size: ShareCardFontSize) {
        _uiState.update { it.copy(fontSize = size) }
    }

    fun setAlign(align: ShareCardAlign) {
        _uiState.update { it.copy(align = align) }
    }

    fun setLongMode(enabled: Boolean) {
        _uiState.update {
            if (!enabled) {
                it.copy(longMode = false, longExportMode = ShareCardLongExportMode.PAGED)
            } else {
                it.copy(longMode = true)
            }
        }
    }

    fun setLongExportMode(mode: ShareCardLongExportMode) {
        _uiState.update { it.copy(longExportMode = mode) }
    }

    fun setAuthorName(name: String) {
        _uiState.update { it.copy(authorName = name.take(24)) }
    }

    fun setQrEnabled(enabled: Boolean) {
        _uiState.update { it.copy(qrEnabled = enabled) }
        refreshQr()
    }

    fun setQrText(text: String) {
        _uiState.update { it.copy(qrText = text.take(240)) }
        refreshQr()
    }

    fun exportAndShare(onLaunch: (IntentPayload) -> Unit) {
        val state = _uiState.value
        if (state.loading || state.error != null || state.saving) return

        viewModelScope.launch {
            var pages: List<Bitmap> = emptyList()
            _uiState.update { it.copy(saving = true, exportError = null, exportProgressText = "准备生成…") }
            yield()

            try {
                val (w, h) = ShareCardSizes.pick(state.ratio, state.content.length)

                pages = renderExportBitmaps(state = state, widthPx = w, heightPx = h)

                _uiState.update { it.copy(exportProgressText = "正在写入文件…") }
                val outFiles = withContext(Dispatchers.IO) { ShareCardFileStore.writeToCacheJpegs(context, pages) }
                if (outFiles.isEmpty()) throw IllegalStateException("导出失败")

                val uris = outFiles.map { fileToContentUri(it) }
                onLaunch(
                    if (uris.size <= 1) {
                        IntentPayload.ShareImage(uri = uris.first(), mimeType = "image/jpeg")
                    } else {
                        IntentPayload.ShareImages(uris = uris, mimeType = "image/jpeg")
                    },
                )
                _uiState.update { it.copy(lastSavedPath = outFiles.firstOrNull()?.absolutePath, exportProgressText = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(exportError = (e.message ?: "导出失败").take(140), exportProgressText = null) }
            } finally {
                pages.forEach { bmp -> runCatching { bmp.recycle() } }
                _uiState.update { it.copy(saving = false) }
            }
        }
    }

    fun exportAndSaveToGallery(onSaved: (Int) -> Unit) {
        val state = _uiState.value
        if (state.loading || state.error != null || state.saving) return

        viewModelScope.launch {
            var pages: List<Bitmap> = emptyList()
            _uiState.update { it.copy(saving = true, exportError = null, exportProgressText = "准备生成…") }
            yield()

            try {
                val (w, h) = ShareCardSizes.pick(state.ratio, state.content.length)

                pages = renderExportBitmaps(state = state, widthPx = w, heightPx = h)

                _uiState.update { it.copy(exportProgressText = "正在保存到相册…") }
                val uris = withContext(Dispatchers.IO) { ShareCardFileStore.saveToGalleryJpegs(context, pages) }
                if (uris.isEmpty()) throw IllegalStateException("保存失败")

                onSaved(uris.size)
                _uiState.update { it.copy(lastSavedPath = uris.firstOrNull()?.toString(), exportProgressText = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(exportError = (e.message ?: "保存失败").take(140), exportProgressText = null) }
                onSaved(0)
            } finally {
                pages.forEach { bmp -> runCatching { bmp.recycle() } }
                _uiState.update { it.copy(saving = false) }
            }
        }
    }

    private suspend fun renderExportBitmaps(
        state: ShareCardUiState,
        widthPx: Int,
        heightPx: Int,
    ): List<Bitmap> {
        if (state.longMode && state.ratio == ShareCardRatio.AUTO) {
            val pages =
                ShareCardBitmapRenderer.renderPaged(
                    context = context,
                    widthPx = widthPx,
                    maxHeightPx = ShareCardSizes.LONG_MAX_HEIGHT_PX,
                    pageHeightPx = ShareCardSizes.LONG_PAGE_HEIGHT_PX,
                    onProgress = { cur, total ->
                        _uiState.update { it.copy(exportProgressText = "正在渲染第 $cur/$total 张…") }
                    },
                ) {
                    OneMemosTheme(config = OneMemosThemeConfig()) {
                        ShareCardCanvas(
                            state = state,
                            heightPx = null,
                            wrapContentHeight = true,
                        )
                    }
                }

            if (state.longExportMode == ShareCardLongExportMode.SINGLE) {
                val merged = mergePagesToSingleIfSafe(pages)
                if (merged != null) {
                    // 合成长图后，原分页 bitmap 不再需要，立即回收释放内存。
                    pages.forEach { bmp -> runCatching { bmp.recycle() } }
                    _uiState.update { it.copy(exportProgressText = "已合成为单张长图") }
                    return listOf(merged)
                }
            }

            return pages
        }

        _uiState.update { it.copy(exportProgressText = "正在渲染…") }
        val bmp =
            ShareCardBitmapRenderer.render(
                context = context,
                widthPx = widthPx,
                heightPx = heightPx,
            ) {
                OneMemosTheme(config = OneMemosThemeConfig()) {
                    ShareCardCanvas(
                        state = state,
                        heightPx = heightPx,
                    )
                }
            }
        return listOf(bmp)
    }

    private suspend fun mergePagesToSingleIfSafe(pages: List<Bitmap>): Bitmap? {
        if (pages.isEmpty()) return null
        if (pages.size > ShareCardSizes.LONG_SINGLE_MAX_PAGES) return null

        val width = pages.first().width
        val totalHeight = pages.sumOf { it.height }
        if (totalHeight <= 0 || totalHeight > ShareCardSizes.LONG_SINGLE_MAX_HEIGHT_PX) return null

        _uiState.update { it.copy(exportProgressText = "正在合成长图…") }
        return withContext(Dispatchers.Default) {
            val out = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            var y = 0f
            pages.forEach { p ->
                canvas.drawBitmap(p, 0f, y, null)
                y += p.height.toFloat()
            }
            out
        }
    }

    private fun fileToContentUri(file: File): Uri =
        FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file,
        )

    private fun refreshQr() {
        val state = _uiState.value
        qrGenerationId += 1
        val gen = qrGenerationId

        if (!state.qrEnabled) {
            _uiState.update { it.copy(qrBitmap = null) }
            return
        }

        val text = state.qrText
        viewModelScope.launch(Dispatchers.Default) {
            val bmp = ShareCardQrGenerator.generate(text, sizePx = 360)
            if (gen != qrGenerationId) {
                // 过期结果：不要写回 UI，减少闪烁；生成出来的 bmp 也无需保留。
                runCatching { bmp?.recycle() }
                return@launch
            }
            _uiState.update { it.copy(qrBitmap = bmp) }
        }
    }

    private suspend fun loadUsableImages(
        attachments: List<cc.pscly.onememos.domain.model.MemoAttachment>,
        maxCount: Int,
        maxSizePx: Int,
    ): List<Bitmap> {
        val uris =
            attachments.asSequence()
                .filter { a ->
                    val mime = a.mimeType.orEmpty()
                    mime.startsWith("image/") && (!a.cacheUri.isNullOrBlank() || !a.localUri.isNullOrBlank())
                }
                .mapNotNull { it.cacheUri ?: it.localUri }
                .mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
                .take(maxCount)
                .toList()

        return uris.mapNotNull { decodeBitmap(it, maxSizePx = maxSizePx) }
    }

    private fun decodeBitmap(
        uri: Uri,
        maxSizePx: Int,
    ): Bitmap? {
        return runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.isMutableRequired = false
                val maxDim = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
                val sample = (maxDim / maxSizePx).coerceAtLeast(1)
                decoder.setTargetSampleSize(sample)
            }
        }.getOrNull()
    }
}

sealed interface IntentPayload {
    data class ShareImage(
        val uri: Uri,
        val mimeType: String,
    ) : IntentPayload

    data class ShareImages(
        val uris: List<Uri>,
        val mimeType: String,
    ) : IntentPayload
}

private object ShareCardSizes {
    const val LONG_MAX_HEIGHT_PX = 120_000
    const val LONG_PAGE_HEIGHT_PX = 2400
    const val LONG_SINGLE_MAX_HEIGHT_PX = 18_000
    const val LONG_SINGLE_MAX_PAGES = 8

    fun pick(ratio: ShareCardRatio, contentLength: Int): Pair<Int, Int> {
        val width = 1080
        return when (ratio) {
            ShareCardRatio.SQUARE_1_1 -> width to 1080
            ShareCardRatio.STORY_9_16 -> width to 1920
            ShareCardRatio.AUTO -> {
                val height =
                    when {
                        contentLength > 900 -> 2160
                        contentLength > 360 -> 1800
                        else -> 1350
                    }
                width to height
            }
        }
    }
}
