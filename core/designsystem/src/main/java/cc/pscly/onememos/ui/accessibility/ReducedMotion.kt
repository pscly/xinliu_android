package cc.pscly.onememos.ui.accessibility

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * 系统“减少动态效果”偏好。
 * Settings 原语与后续动效统一读取此值，避免页面各自复制分支。
 */
object ReducedMotion {
    val Local = staticCompositionLocalOf { false }

    val current: Boolean
        @Composable
        @ReadOnlyComposable
        get() = Local.current || isSystemReducedMotionEnabled()
}

@Composable
@ReadOnlyComposable
fun isSystemReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    return runCatching {
        val resolver = context.contentResolver
        val animator = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val transition = Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
        val window = Settings.Global.getFloat(resolver, Settings.Global.WINDOW_ANIMATION_SCALE, 1f)
        animator == 0f || transition == 0f || window == 0f
    }.getOrDefault(false)
}
