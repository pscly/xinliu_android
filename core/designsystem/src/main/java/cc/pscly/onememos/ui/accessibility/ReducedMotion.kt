package cc.pscly.onememos.ui.accessibility

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * 系统“减少动态效果”偏好。
 * Settings 原语与后续动效统一读取此值，避免页面各自复制分支。
 *
 * 门控范围（ADR 0012）：页面转场、印章按压缩放、盖章浮层动效。
 * 任一来源为真即全部停用：
 * - 系统“移除动画”（animator/transition/window scale 归零）
 * - [Local] 由上层提供（例如设置项 `pageTransitionsEnabled=false`，见 [providesFromPageTransitions]）
 */
object ReducedMotion {
    val Local = staticCompositionLocalOf { false }

    /**
     * 由「页面转场」设置派生 [Local] 的提供值。
     * 用户关闭页面转场（pageTransitionsEnabled=false）等效于偏好减少动效：
     * 印章按压/盖章反馈与页面转场一并停用。
     */
    fun providesFromPageTransitions(pageTransitionsEnabled: Boolean): ProvidedValue<Boolean> =
        Local provides !pageTransitionsEnabled

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
