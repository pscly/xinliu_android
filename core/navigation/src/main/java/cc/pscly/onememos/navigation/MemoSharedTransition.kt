package cc.pscly.onememos.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/**
 * Memo 共享边界（shared bounds）唯一入口。
 *
 * - [LocalMemoSharedTransitionScope]：宿主注入当前 [SharedTransitionScope]；
 *   Reduced Motion 时为 null，业务侧 no-op。
 * - [memoSharedContentKey]：稳定 key，形如 `memo/<uuid>`；null/blank 不产生 key。
 * - [memoSharedBounds]：null scope/key 时 early return，且不得条件调用
 *   [SharedTransitionScope.rememberSharedContentState]（Compose slot 稳定性）。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalMemoSharedTransitionScope =
    staticCompositionLocalOf<SharedTransitionScope?> { null }

fun memoSharedContentKey(uuid: String?): String? =
    uuid?.takeIf { it.isNotBlank() }?.let { "memo/$it" }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.memoSharedBounds(uuid: String?): Modifier {
    val scope = LocalMemoSharedTransitionScope.current
    val key = memoSharedContentKey(uuid)
    if (scope == null || key == null) return this
    return with(scope) {
        this@memoSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
        )
    }
}
