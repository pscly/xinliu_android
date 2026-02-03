package cc.pscly.onememos.core.performance

import android.app.Activity
import android.view.Display

/**
 * 在同分辨率下请求“最高刷新率”（例如 120Hz），让系统更倾向于给高刷。
 *
 * 说明：
 * - 这只是 hint，并不保证一定吃满 120Hz；真正“丝滑”取决于每帧是否能在更短时间内完成渲染（120Hz 约 8.3ms/帧）。
 * - Android 是否最终切到高刷由系统策略决定；这里通过 WindowManager.LayoutParams 的 hint 增加“倾向”。
 */
fun Activity.requestMaxRefreshRate() {
    val display = display ?: return
    val bestMode = display.bestModeSameResolution() ?: return

    // 对部分厂商 ROM：preferredDisplayModeId/preferredRefreshRate 仍然更“好使”
    val lp = window.attributes
    lp.preferredRefreshRate = bestMode.refreshRate
    if (bestMode.modeId != display.mode.modeId) {
        lp.preferredDisplayModeId = bestMode.modeId
    }
    window.attributes = lp
}

private fun Display.bestModeSameResolution(): Display.Mode? {
    val current = mode
    return supportedModes
        .asSequence()
        .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        .maxByOrNull { it.refreshRate }
}
