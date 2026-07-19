# issues.md

（累计记录：遇到的问题、失败原因、回滚/替代方案。只追加，不覆盖。）

## 2026-02-21 - selectionMode 下子元素点击吞事件
- 现象：MemoItem 内部的 `TagChip` 自带 clickable，会优先消费点击，导致 selectionMode 下“点标签不切换选中”（看起来像没点上）。
- 处理：在 `selectionMode=true` 时对 `TagChip(onClick = null)`，让点击落到父级 `InkCard` 的 onClick（从而触发 toggle）。

## 2026-02-21 - Compose 的 weight import 会导致编译失败
- 现象：在 `HomeScreen.kt` 显式 `import androidx.compose.foundation.layout.weight` 时，Kotlin 编译报错：`Cannot access 'weight': it is internal in 'androidx.compose.foundation.layout'`。
- 处理：不要显式 import；在 `Row/Column` 的 scope 内直接使用 `Spacer(modifier = Modifier.weight(1f))`（依赖 scope 提供的 member extension）。

## 2026-02-21 - 本机缺少 rg / Kotlin LSP 初始化超时
- 现象：终端环境 `rg` 不存在（`command not found: rg`），无法按计划使用 ripgrep。
- 现象：`lsp_diagnostics` 初始化超时/端口绑定失败，无法拿到增量诊断结果。
- 处理：改用工程内 `grep` 工具做字符串检索；用 `./gradlew :app:assembleDebug --stacktrace` 作为最终编译验证。

## 2026-02-21 - 多选“分享合并文本”可能触发 TransactionTooLargeException
- 风险：合并全文后字符串可能非常大，部分机型/ROM 在 `Intent.EXTRA_TEXT` 走 Binder 传输时可能抛 `TransactionTooLargeException`。
- 记录：已在 `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeScreen.kt` 的 `putExtra(Intent.EXTRA_TEXT, ...)` 附近添加中文注释提示；后续可改为“导出到文件再分享”。

## 2026-02-21 - feature:collections 单测 paging* 空实现的类型推断失败
- 现象：在 FakeMemoRepository 里用 `override fun pagingMemos(...) = emptyFlow()` 会触发 Kotlin 编译错误：`Not enough information to infer type variable T`
- 原因：返回类型是 `Flow<PagingData<Memo>>`，表达式体 `emptyFlow()` 无法推断泛型
- 处理：显式写出返回类型：`override fun pagingMemos(...): Flow<PagingData<Memo>> = emptyFlow()`（`pagingArchivedMemos` 同理）

## 2026-02-21 - feature:collections 单测误把 import 写到文件末尾
- 现象：Kotlin 编译报错：`imports are only allowed in the beginning of file`
- 处理：把 import 移到文件顶部（package 下方），避免 apply_patch 插入点匹配失误导致的“尾部 import”

## 2026-02-21 - lsp_diagnostics 仍可能 initialize timeout
- 现象：对 `CollectionsScreen.kt` 执行 `lsp_diagnostics` 时，LSP initialize 请求超时
- 处理：以 Gradle 构建作为主验证（`:app:assembleDebug` + `:app:assembleBenchmark`）
