package cc.pscly.onememos.ui.component

/*
 * M3.4 状态原语：全 App 统一的「加载 / 空态 / 错误 / 重试横幅」。
 *
 * 设计语义：
 * - 安静退后：图标与辅助文案只用 outline / onSurfaceVariant 弱色阶，不抢正文；
 * - 无投影：重试横幅用纸面细描边（InkBorder.Hairline），不用 elevation；
 * - 令牌纪律：颜色全部来自 MaterialTheme.colorScheme，间距/圆角全部来自
 *   InkSpacing / InkShape，组件内不出现裸 dp。
 *
 * 迁移清单（grep 全库裸状态实现核对结果，后续逐屏替换；M3.4 本任务不改 feature 屏）：
 * - feature/home/.../HomeScreen.kt
 *   - HomeListLoadingItem（≈L894）：Box + 裸 CircularProgressIndicator → InkLoading
 *   - HomeListErrorItem（≈L907）：InkCard + 错误文案 + 重试/同步 → InkError
 *   - HomeListEmptyItem（≈L928）：Box + 空态文案 → InkEmpty
 *   - HomeListAppendLoadingItem（≈L945）：分页追加转圈 → InkLoading
 *   - SyncStatusBanner（≈L1295）：InkCard + 重试/去登录 → InkRetryBanner
 * - feature/sharecard/.../ShareCardScreen.kt：≈L165 全屏 loading Box；≈L172 裸错误 Text；
 *   ≈L334 导出行内进度（转圈+文案）
 * - feature/auth/.../AuthScreen.kt：≈L190/L291 按钮内 18dp 转圈（属按钮加载态，由按钮原语承载，
 *   不在本组迁移范围）
 * - feature/home/.../AddToCollectionsDialog.kt（≈L156 busy 转圈）、AddToCollectionsBatchDialog.kt：
 *   弹层内小转圈
 * - feature/settings/.../record/RecordEditingScreen.kt（≈L154 转圈+文案 Row）、
 *   reminder/ReminderCalendarScreen.kt（≈L83 同款 Row）
 * - feature/todo/.../TodoScreen.kt（≈L337 InkCard 空态）、TodoDialogs.kt
 *   （≈L173/L232/L658/L886 “暂无…”裸 Text 多处）
 * - feature/home/.../MemoItem.kt（≈L424 图片加载占位已用 surfaceVariant，与本组令牌口径一致，
 *   无需迁移，仅作对照记录）
 */

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import cc.pscly.onememos.ui.theme.InkBorder
import cc.pscly.onememos.ui.theme.InkShape
import cc.pscly.onememos.ui.theme.InkSpacing

/**
 * 全幅加载态：整行居中的 [CircularProgressIndicator]。
 *
 * 指示色默认取 `colorScheme.surfaceVariant`——与 MemoItem 图片加载占位同一口径，
 * 在纸面（surface）上呈现安静的灰纸调，不用默认 primary 强色抢视线。
 *
 * 用法：
 * ```kotlin
 * if (uiState.isLoading) {
 *     InkLoading(message = "加载中…")
 *     return@Column
 * }
 * ```
 *
 * @param message 可选辅助文案；为 null 时只渲染转圈
 * @param color 指示色，默认 surfaceVariant，可按场景覆盖
 */
@Composable
fun InkLoading(
    modifier: Modifier = Modifier,
    message: String? = null,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = InkSpacing.StatePaddingV),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = color)
        if (message != null) {
            Spacer(modifier = Modifier.height(InkSpacing.StateGapM))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 空态：引导图标 + 安静文案 + 可选动作按钮。
 *
 * 图标与文案只用 outline / onSurfaceVariant 弱色阶（标签安静退后原则）；
 * 无数据时给用户的唯一点缀是可选动作（如「去记录」）。
 *
 * 用法：
 * ```kotlin
 * if (uiState.items.isEmpty()) {
 *     InkEmpty(
 *         message = "还没有任何记录，点右下角“记”开始吧。",
 *         actionLabel = "去记录",
 *         onAction = onNewMemo,
 *     )
 * }
 * ```
 *
 * @param icon 引导图标，默认 [Icons.Outlined.Inbox]，可按场景换 EditNote 等
 * @param actionLabel 动作按钮文案；与 [onAction] 同时非空才渲染按钮
 */
@Composable
fun InkEmpty(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = InkSpacing.StatePaddingV),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(InkSpacing.StateIconSize),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(modifier = Modifier.height(InkSpacing.StateGapM))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(InkSpacing.StateGapS))
            TextButton(onClick = onAction) {
                Text(text = actionLabel)
            }
        }
    }
}

/**
 * 错误态：错误图标 + 错误文案 + 重试按钮。
 *
 * 图标与文案取 `colorScheme.error`；容器声明 assertive live region，
 * 错误出现时由 TalkBack 主动播报（对齐设置页既有错误语义）。
 *
 * 用法：
 * ```kotlin
 * uiState.error?.let { error ->
 *     InkError(message = "加载失败：$error", onRetry = viewModel::refresh)
 * }
 * ```
 *
 * @param retryLabel 重试按钮文案，默认「重试」，可传「重新登录」等场景化文案
 */
@Composable
fun InkError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.ErrorOutline,
    retryLabel: String = "重试",
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Assertive }
                .padding(vertical = InkSpacing.StatePaddingV),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(InkSpacing.StateIconSize),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(InkSpacing.StateGapM))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(InkSpacing.StateGapS))
        TextButton(onClick = onRetry) {
            Text(text = retryLabel)
        }
    }
}

/**
 * 内联重试横幅：列表/页面局部失败时挂在内容旁的轻量条（非全幅替换）。
 *
 * 视觉为纸面子表面：surfaceVariant 底 + 发丝细描边（[InkBorder.Hairline]），
 * 形状复用 [InkShape.Card]；polite live region 避免打断用户当前操作。
 *
 * 用法（列表顶部内联提示，不打断已有内容）：
 * ```kotlin
 * AnimatedVisibility(visible = uiState.syncFailed) {
 *     InkRetryBanner(message = "同步失败：网络不可用", onRetry = viewModel::retrySync)
 * }
 * ```
 */
@Composable
fun InkRetryBanner(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryLabel: String = "重试",
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        shape = InkShape.Card,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border =
            BorderStroke(
                InkBorder.Hairline,
                MaterialTheme.colorScheme.outline.copy(alpha = InkBorder.OutlineSoft),
            ),
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = InkSpacing.BannerPaddingH,
                    vertical = InkSpacing.BannerPaddingV,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onRetry) {
                Text(text = retryLabel)
            }
        }
    }
}
