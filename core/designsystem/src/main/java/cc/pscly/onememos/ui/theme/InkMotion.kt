package cc.pscly.onememos.ui.theme

/**
 * 动效与交互时令牌（参数来源：DESIGN.md §6.1 现有行为表）。
 *
 * 印章按压、盖章入场/退场的时长、比例、旋转统一收口于此；
 * 业务页面不得复制另一套印章物理感。
 */
object InkMotion {
    // ---------- 印章控件按压 ----------
    const val PressScale = 0.92f // 按下缩放
    const val PressDurationMs = 120 // 按压缩放 tween 时长

    // ---------- 盖章反馈时长 ----------
    const val StampDurationDefaultMs = 600 // 总时长基准
    const val StampDurationMinMs = 200
    const val StampDurationMaxMs = 2000
    const val StampEnterRatio = 0.45f // 入场占总时长比例
    const val StampExitRatio = 0.35f // 退场占总时长比例
    const val StampSegmentMinMs = 120 // 入场/退场片段最小钳制
    const val StampSegmentMaxMs = 900 // 入场/退场片段最大钳制

    // ---------- 盖章关键帧 ----------
    const val StampAlphaKeyRatio = 0.35f // 透明度关键帧位置（占入场）
    const val StampAlphaKeyMinMs = 80
    const val StampScaleKeyRatio = 0.58f // 回弹关键帧位置（占入场）
    const val StampRotationKeyRatio = 0.62f // 旋转关键帧位置（占入场）
    const val StampScaleInStart = 1.42f // 落印冲击比例
    const val StampScaleInMid = 0.94f // 轻微回弹比例
    const val StampScaleInEnd = 1.00f // 定格比例
    const val StampScaleOut = 1.25f // 退场趋向比例
    const val StampRotationStart = -26f
    const val StampRotationMid = -8f
    const val StampRotationEnd = -12f // 定格角度
    const val StampRotationOut = -24f // 退场趋向角度
    const val StampHideAlphaThreshold = 0.01f // 低于该透明度直接不绘制

    // ---------- 图片查看器 ----------
    const val ViewerChromeAutoHideMs = 2200L // 控制条自动隐藏延时
    const val ViewerDoubleTapWindowMs = 320L // 双击判定时间窗
    const val ViewerDoubleTapZoom = 2.5f // 双击放大倍率
    const val ViewerMaxZoom = 5f // 双指缩放上限
}
