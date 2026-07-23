package cc.pscly.onememos.ui.feature.todo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import cc.pscly.onememos.ui.component.InkCard
import kotlinx.coroutines.delay

/**
 * 撤销提示条：删除后显示 6 秒，超时回调 [onExpired]；6 秒内「撤销」回调 [onUndo]。
 *
 * - [deletedItemId] 为 null：本轮结束边界，不渲染条（但保留布局根）
 * - 非 null：显示并启动 6s timer
 * - 过期/撤销：本轮 [dismissed] 立即隐藏，并回调父级（生产侧在 ID 匹配时清 null）
 * - 父级清 null 后再赋同 ID：[key] 重建新一轮，重新显示并重新计时
 *
 * 始终保留 [Box] 布局根，避免过期后空树导致 same-id 重进在
 * mainClock.autoAdvance=false 下无法 recompose。
 */
@Composable
internal fun TodoUndoHost(
    deletedItemId: String?,
    onUndo: (String) -> Unit,
    onExpired: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnUndo by rememberUpdatedState(onUndo)
    val currentOnExpired by rememberUpdatedState(onExpired)

    // 布局根始终保留，保证父级 State 变化能驱动重组
    Box(modifier = modifier.fillMaxWidth()) {
        val id = deletedItemId
        if (id == null) return@Box

        // id 变化时销毁旧会话（取消旧 delay）；同 ID 经 null 重进时同样新建会话
        key(id) {
            var dismissed by remember { mutableStateOf(false) }
            // 协程与点击共享，避免 delay 后与手动撤销竞态
            val finished = remember { BooleanFlag() }

            LaunchedEffect(Unit) {
                delay(6_000)
                if (!finished.value) {
                    finished.value = true
                    dismissed = true
                    currentOnExpired(id)
                }
            }

            if (!dismissed) {
                InkCard(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = "已删除 1 项，可撤销"
                            },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "已删除 1 项",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                if (!finished.value) {
                                    finished.value = true
                                    dismissed = true
                                    currentOnUndo(id)
                                }
                            },
                        ) {
                            Text("撤销")
                        }
                    }
                }
            }
        }
    }
}

/** 协程与点击共享的布尔标志（非 Compose State）。 */
private class BooleanFlag {
    var value: Boolean = false
}
