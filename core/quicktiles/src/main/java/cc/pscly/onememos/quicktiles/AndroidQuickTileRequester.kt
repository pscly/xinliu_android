package cc.pscly.onememos.quicktiles

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import cc.pscly.onememos.qs.QuickCaptureTileService
import cc.pscly.onememos.qs.QuickScreenshotTileService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/**
 * 使用 application Context 请求系统添加 TileService。
 * 系统服务缺失、资源不可用或回调超时返回 PlatformUnavailable；callback 状态码包装为 Completed。
 */
@Singleton
class AndroidQuickTileRequester @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : QuickTileRequester {
    override suspend fun request(kind: QuickTileKind): QuickTileRequestResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return QuickTileRequestResult.PlatformUnavailable
        }
        val sbm =
            context.getSystemService(StatusBarManager::class.java)
                ?: return QuickTileRequestResult.PlatformUnavailable

        val component =
            when (kind) {
                QuickTileKind.QUICK_CAPTURE ->
                    ComponentName(context, QuickCaptureTileService::class.java)
                QuickTileKind.SCREENSHOT_CAPTURE ->
                    ComponentName(context, QuickScreenshotTileService::class.java)
            }

        val labelAndIcon =
            runCatching {
                val label =
                    when (kind) {
                        QuickTileKind.QUICK_CAPTURE ->
                            context.getString(cc.pscly.onememos.core.quicktiles.R.string.qs_quick_capture)
                        QuickTileKind.SCREENSHOT_CAPTURE ->
                            context.getString(cc.pscly.onememos.core.quicktiles.R.string.qs_quick_screenshot)
                    }
                val iconRes =
                    when (kind) {
                        QuickTileKind.QUICK_CAPTURE ->
                            cc.pscly.onememos.core.quicktiles.R.drawable.ic_qs_quick_capture
                        QuickTileKind.SCREENSHOT_CAPTURE ->
                            cc.pscly.onememos.core.quicktiles.R.drawable.ic_qs_quick_screenshot
                    }
                label to Icon.createWithResource(context, iconRes)
            }.getOrElse {
                return QuickTileRequestResult.PlatformUnavailable
            }
        val (label, icon) = labelAndIcon

        return try {
            withTimeout(REQUEST_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val resumed = AtomicBoolean(false)
                    try {
                        sbm.requestAddTileService(
                            component,
                            label,
                            icon,
                            context.mainExecutor,
                        ) { result ->
                            if (resumed.compareAndSet(false, true) && cont.isActive) {
                                cont.resume(QuickTileRequestResult.Completed(result))
                            }
                        }
                    } catch (_: Throwable) {
                        if (resumed.compareAndSet(false, true) && cont.isActive) {
                            cont.resume(QuickTileRequestResult.PlatformUnavailable)
                        }
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            QuickTileRequestResult.PlatformUnavailable
        }
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 3_000L
    }
}
