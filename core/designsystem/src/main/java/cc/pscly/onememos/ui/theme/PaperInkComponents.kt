package cc.pscly.onememos.ui.theme

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Shapes
import androidx.compose.material3.SheetState
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.window.DialogProperties

/**
 * M2.10：M3 系统组件纸墨化。
 *
 * 两条覆盖路径：
 * 1. 全局：[OneMemosTheme] 把 [PaperInkShapes] 与全令牌 [androidx.compose.material3.ColorScheme]
 *    注入 MaterialTheme 组合局部量，M3 组件默认值（AlertDialog 形状/容器色、BottomSheet 容器色、
 *    Snackbar 反色、TopAppBar 滚动态容器色）随之取纸墨令牌，业务屏零改动。
 * 2. 显式：[PaperInkComponentDefaults] + `PaperInk*` 包装组件，用于 M3 硬编码、不读色板的
 *    默认值（例如 BottomSheet DragHandle 默认 onSurfaceVariant），以及新屏直接引用。
 */
val PaperInkShapes: Shapes =
    Shapes(
        extraSmall = InkShape.Stamp, // 10dp：标签/盖章反馈
        small = InkShape.SealCompact, // 12dp：紧凑印章/筛选片
        medium = InkShape.Card, // 14dp：卡片/纸面（M3 Card 默认读 medium）
        large = InkShape.Paper, // 14dp
        extraLarge = InkShape.Card, // 14dp：AlertDialog / BottomSheet 顶角
    )

/** M3 组件的纸墨默认皮肤：全部颜色取自 MaterialTheme.colorScheme 令牌。 */
object PaperInkComponentDefaults {

    // ---------- TopAppBar ----------
    // 容器/滚动态容器统一 surface：m3 1.4 顶栏无阴影，滚动态只剩 surfaceContainer 变色，一并抹平
    @Composable
    fun topAppBarColors(): TopAppBarColors =
        TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
        )

    // ---------- Snackbar ----------
    @Composable
    fun snackbarContainerColor(): Color = MaterialTheme.colorScheme.inverseSurface

    @Composable
    fun snackbarContentColor(): Color = MaterialTheme.colorScheme.inverseOnSurface

    @Composable
    fun snackbarActionColor(): Color = MaterialTheme.colorScheme.inversePrimary

    // ---------- Dialog ----------
    val dialogShape: Shape
        get() = InkShape.Card

    @Composable
    fun dialogContainerColor(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

    // ---------- BottomSheet ----------
    @Composable
    fun bottomSheetContainerColor(): Color = MaterialTheme.colorScheme.surfaceContainerLow

    @Composable
    fun bottomSheetDragHandleColor(): Color = MaterialTheme.colorScheme.outlineVariant
}

/** 纸墨皮肤顶栏：surface 底 + onSurface 内容，滚动态不变色。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperInkTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        windowInsets = windowInsets,
        colors = PaperInkComponentDefaults.topAppBarColors(),
        scrollBehavior = scrollBehavior,
    )
}

/** 纸墨皮肤 Snackbar：inverseSurface 墨底 + inverseOnSurface 纸字。 */
@Composable
fun PaperInkSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier,
) {
    Snackbar(
        snackbarData = snackbarData,
        modifier = modifier,
        containerColor = PaperInkComponentDefaults.snackbarContainerColor(),
        contentColor = PaperInkComponentDefaults.snackbarContentColor(),
        actionColor = PaperInkComponentDefaults.snackbarActionColor(),
        dismissActionContentColor = PaperInkComponentDefaults.snackbarContentColor(),
    )
}

/** 挂载 [PaperInkSnackbar] 的宿主，替换默认 SnackbarHost 用法。 */
@Composable
fun PaperInkSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        PaperInkSnackbar(snackbarData = data)
    }
}

/** 纸墨皮肤对话框：surfaceContainerHigh 底 + [InkShape.Card] 圆角。 */
@Composable
fun PaperInkAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = PaperInkComponentDefaults.dialogShape,
        containerColor = PaperInkComponentDefaults.dialogContainerColor(),
        properties = properties,
    )
}

/** 纸墨皮肤模态底单：surfaceContainerLow 底 + outlineVariant 拖拽柄。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperInkModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = androidx.compose.material3.rememberModalBottomSheetState(),
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = PaperInkComponentDefaults.bottomSheetContainerColor(),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = PaperInkComponentDefaults.bottomSheetDragHandleColor(),
            )
        },
    ) {
        content()
    }
}
