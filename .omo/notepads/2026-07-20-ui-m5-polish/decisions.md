## decisions

## 2026-07-20 M5 scope
推荐最小方案 A：
1) Home 列表/网格 contentPadding.bottom += FAB 清除高度（单 FAB / 双 FAB 动态）
2) ShareCardCanvas 标签由 LazyRow 改为 FlowRow（或保证完整可见），避免导出/预览半裁切
3) ThemesPanel 可选改为 FlowRow 防小屏裁切（防御性）
不重做设计系统，不碰版本发布直到代码+门禁通过。
