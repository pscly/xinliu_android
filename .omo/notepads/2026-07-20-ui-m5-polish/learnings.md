## learnings

## 2026-07-20 Investigation
- Home FAB: Scaffold floatingActionButton 不自动给列表加底边 inset；contentPadding 仅 verticalPad。
- 证据 home.xml：末条标签 y≈2943-3111，FAB「记」y≈2948-3144，密度≈3.5，FAB 196px≈56dp。
- ShareCard 第四枚裁切是画布 LazyRow 标签（#同事 仅 33px 宽），非 ThemesPanel（四主题均完整 205px）。
- 远端 main=9993ad2；本地 HEAD e6ecd5b 含 M4+editor fix；工作树仅 .omo/run-continuation 与 .tmp_vqa 脏项，勿提交。
- 下一版本周期 1.14.0；v1.13.0 不可变。
- M5-2 修复：ShareCardCanvas 标签行 LazyRow→FlowRow（X10 水平/X8 垂直间距，仍 take(6)），导出与预览共用同一画布故换行对两者同时生效；文件级 OptIn 追加 ExperimentalLayoutApi。
- :feature:sharecard 无 compileBenchmarkKotlin 任务（benchmark 变体在 app 模块），库模块验证用 compileReleaseKotlin，已通过。
- M5-1 修复：HomeScreen 列表/网格 contentPadding 底部追加 FAB 净空。新建纯函数 `HomeFabClearance.fabBottomClearance(showScrollToTop)`——显示回到顶部时 = TouchTargetMin(48) + X10 + SealButtonSize(56) + X16，否则 = SealButtonSize + X16；LazyColumn 与 LazyVerticalStaggeredGrid 均改为 start/end/top/bottom 显式 PaddingValues，bottom = verticalPad + 净空。
- SealIconButton 外层因 minimumInteractiveComponentSize + defaultMinSize 实际布局高为 TouchTargetMin=48dp（视觉 SealIconSize=44），避让计算须用 48 而非 44。
- home_fab_group / home_fab_create testTag 未动；版本号未动；`:feature:home:testDebugUnitTest --tests HomeFabClearanceTest` 通过。
- 2026-07-20 文档收口：设计规格写于 `docs/superpowers/specs/2026-07-20-ui-m5-fab-sharecard-polish-design.md`；`.ai_session.md` 前置 M5 节。文档任务不改 Kotlin、不 commit、不 bump 版本。

