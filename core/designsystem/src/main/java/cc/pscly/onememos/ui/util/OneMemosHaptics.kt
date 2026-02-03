package cc.pscly.onememos.ui.util

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 统一的触感反馈封装：
 * - 本工程 minSdk=33，可直接使用 VibratorManager + 预设震动效果
 * - 目标：让关键操作更“有实体感”，同时避免到处散落调用细节
 */
class OneMemosHaptics internal constructor(
    private val vibrator: Vibrator?,
) {
    fun confirm() {
        vibratePredefined(VibrationEffect.EFFECT_CLICK)
    }

    fun heavyClick() {
        vibratePredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
    }

    fun tick() {
        vibratePredefined(VibrationEffect.EFFECT_TICK)
    }

    private fun vibratePredefined(effectId: Int) {
        runCatching {
            val v = vibrator ?: return
            if (!v.hasVibrator()) return
            v.vibrate(VibrationEffect.createPredefined(effectId))
        }
    }
}

fun Context.createOneMemosHaptics(): OneMemosHaptics {
    // 某些设备/ROM 可能返回 null，或在未声明 VIBRATE 权限时抛异常；这里统一降级为 no-op。
    val vibrator =
        runCatching {
            val manager = getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        }.getOrNull()
            ?: runCatching { getSystemService(Vibrator::class.java) }.getOrNull()

    return OneMemosHaptics(vibrator)
}

@Composable
fun rememberOneMemosHaptics(): OneMemosHaptics {
    val context = LocalContext.current
    return remember(context) { context.createOneMemosHaptics() }
}
