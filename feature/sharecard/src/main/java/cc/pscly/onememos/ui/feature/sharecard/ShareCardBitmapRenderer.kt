package cc.pscly.onememos.ui.feature.sharecard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * 离屏渲染 Compose 到 Bitmap：
 * - 不依赖复杂 Canvas 绘图，样式迭代成本更低
 * - 适合“模板化卡片”这种以排版为核心的导出功能
 *
 * 关键点：
 * - 渲染/测量/绘制本质属于 View 系统操作，必须在主线程进行
 * - 但导出过程可能很重（长文分页、多张图），因此在分页循环中 `yield()`，让 UI 有机会刷新进度/避免“像卡死”
 */
object ShareCardBitmapRenderer {
    suspend fun render(
        context: Context,
        widthPx: Int,
        heightPx: Int,
        content: @Composable () -> Unit,
    ): Bitmap =
        withContext(Dispatchers.Main.immediate) {
            val view =
                // Feature 模块下 non-transitive R 开启后，不能引用 :app 的 R.style；
                // 这里直接使用传入的 Context，并依赖上层 Compose Theme（OneMemosTheme）提供样式。
                ComposeView(context).apply {
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                    setContent(content)
                }

            val wSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
            val hSpec = View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
            view.measure(wSpec, hSpec)
            view.layout(0, 0, widthPx, heightPx)

            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)

            runCatching { view.disposeComposition() }
            bitmap
        }

    /**
     * 长图/分页长图渲染：
     * - 先以 wrap-content 计算真实高度
     * - 若高度过大，则按页高切片导出（避免一次性创建超大 Bitmap OOM）
     */
    suspend fun renderPaged(
        context: Context,
        widthPx: Int,
        maxHeightPx: Int,
        pageHeightPx: Int,
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
        content: @Composable () -> Unit,
    ): List<Bitmap> =
        withContext(Dispatchers.Main.immediate) {
            val view =
                ComposeView(context).apply {
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                    setContent(content)
                }

            val wSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
            val hSpec = View.MeasureSpec.makeMeasureSpec(maxHeightPx, View.MeasureSpec.AT_MOST)
            view.measure(wSpec, hSpec)
            val measuredHeight = view.measuredHeight.coerceAtLeast(1)
            view.layout(0, 0, widthPx, measuredHeight)

            val safePageHeight = pageHeightPx.coerceIn(800, 5000)
            val total = ((measuredHeight + safePageHeight - 1) / safePageHeight).coerceAtLeast(1)

            val pages = mutableListOf<Bitmap>()
            var y = 0
            var idx = 0
            while (y < measuredHeight) {
                yield()
                idx += 1
                onProgress?.invoke(idx, total)

                val h = minOf(safePageHeight, measuredHeight - y)
                val bitmap = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.translate(0f, -y.toFloat())
                view.draw(canvas)
                pages.add(bitmap)
                y += h
            }

            runCatching { view.disposeComposition() }
            pages
        }
}
