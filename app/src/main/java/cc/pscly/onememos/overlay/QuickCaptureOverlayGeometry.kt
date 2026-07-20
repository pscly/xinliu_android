package cc.pscly.onememos.overlay

/**
 * 快速记录悬浮窗的纯几何计算契约（仅 px，不依赖 Android View / Compose 类型）。
 *
 * 语义约定（双信号自由带）：
 * - `fullViewportHeightPx` 为全屏视口高度（屏幕尺寸，不随键盘变化）；
 * - `windowHeightPx` 为当前窗口实际高度（`SOFT_INPUT_ADJUST_RESIZE` 生效时会被键盘压缩）；
 * - `imeBottomPx` 为 `WindowInsets.ime.bottom`（部分 OEM 不向 overlay 窗口派发，恒为 0）；
 * - 自由带 = min(窗口高度, 全屏视口 − 钳制后的 IME 高度)，两个信号任一生效都能
 *   把卡片限制在键盘上方，双信号同时到达时也不会重复扣减；
 * - 底部避让（`bottomPaddingPx`）= 窗口高度 − 自由带，仅在窗口未被缩放时补偿 IME；
 * - 垂直边距应用在自由带内部，上下各一份；
 * - 可见卡片最大高度 = min(全屏视口 × 卡高比例, 自由带 − 2×垂直边距)；
 * - 所有输出值必须非负。
 */
internal data class QuickCaptureOverlayGeometryInput(
    /** 全屏视口高度（px），来自屏幕配置，不随键盘变化 */
    val fullViewportHeightPx: Int,
    /** 当前窗口实际高度（px），ADJUST_RESIZE 生效时随键盘压缩 */
    val windowHeightPx: Int,
    /** IME（软键盘）从底部占位的高度（px），可能大于视口，需要钳制 */
    val imeBottomPx: Int,
    /** 自由带内部的垂直边距（px），上下各应用一次 */
    val verticalMarginPx: Int,
    /** 卡片最大高度占全屏视口的比例（例如 0.5f 表示半屏） */
    val cardMaxHeightFraction: Float,
)

/**
 * 几何计算输出：包含布局与断言所需的全部显式几何量。
 */
internal data class QuickCaptureOverlayLayout(
    /** 钳制后的 IME 底部占位高度（不超过全屏视口，非负） */
    val clampedImeBottomPx: Int,
    /** 需要在窗口底部额外避让的高度（px）；窗口已被键盘缩放时为 0 */
    val bottomPaddingPx: Int,
    /** 自由带高度（px），即可摆放卡片的键盘上方区域 */
    val freeBandHeightPx: Int,
    /** 卡片可用区域高度（px），即自由带减去上下垂直边距 */
    val cardAreaHeightPx: Int,
    /** 可见卡片最大高度（px） */
    val maxCardHeightPx: Int,
)

/**
 * 悬浮窗几何计算器：对输入做防御性钳制后，按双信号契约计算自由带与卡片布局，
 * 全程使用确定性整数运算，保证所有输出非负。
 */
internal object QuickCaptureOverlayGeometry {

    fun compute(input: QuickCaptureOverlayGeometryInput): QuickCaptureOverlayLayout {
        // 输入钳制：视口/窗口非负；IME 限制在 0..视口；边距非负；比例限制在 0f..1f
        val fullViewport = input.fullViewportHeightPx.coerceAtLeast(0)
        val window = input.windowHeightPx.coerceAtLeast(0)
        val clampedImeBottom = input.imeBottomPx.coerceIn(0, fullViewport)
        val margin = input.verticalMarginPx.coerceAtLeast(0)
        val fraction = input.cardMaxHeightFraction.coerceIn(0f, 1f)

        // 双信号自由带：窗口缩放（ADJUST_RESIZE）与 IME insets 取较小者，互不重复扣减
        val freeBandByIme = fullViewport - clampedImeBottom
        val freeBandHeight = minOf(window, freeBandByIme).coerceAtLeast(0)

        // 窗口未被缩放时才需要底部避让；窗口已缩放时避让为 0
        val bottomPadding = (window - freeBandHeight).coerceAtLeast(0)

        // 卡片可用区域 = 自由带减去上下边距（不为负）
        val cardAreaHeight = (freeBandHeight - 2 * margin).coerceAtLeast(0)

        // 视口比例上限（向下取整）；可见卡高取可用区域与比例上限的较小值
        val viewportCap = (fullViewport * fraction).toInt()
        val maxCardHeight = minOf(cardAreaHeight, viewportCap)

        return QuickCaptureOverlayLayout(
            clampedImeBottomPx = clampedImeBottom,
            bottomPaddingPx = bottomPadding,
            freeBandHeightPx = freeBandHeight,
            cardAreaHeightPx = cardAreaHeight,
            maxCardHeightPx = maxCardHeight,
        )
    }
}
