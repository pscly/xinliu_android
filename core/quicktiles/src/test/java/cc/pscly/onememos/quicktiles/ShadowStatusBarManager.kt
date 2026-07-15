package cc.pscly.onememos.quicktiles

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import java.util.concurrent.Executor
import java.util.function.Consumer
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * Robolectric 默认 StatusBarManager 不会触发 requestAddTileService 回调。
 * Shadow 同步回调，避免依赖 mainExecutor/Looper idle。
 */
@Implements(StatusBarManager::class)
class ShadowStatusBarManager {
    @Implementation
    fun requestAddTileService(
        componentName: ComponentName?,
        label: CharSequence?,
        icon: Icon?,
        resultExecutor: Executor?,
        resultCallback: Consumer<Int>?,
    ) {
        // 同步回调：真实系统会异步，但 Robolectric 主线程 executor 在 runBlocking 中不会自动推进。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            resultCallback?.accept(StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED)
        } else {
            resultCallback?.accept(0)
        }
    }
}
