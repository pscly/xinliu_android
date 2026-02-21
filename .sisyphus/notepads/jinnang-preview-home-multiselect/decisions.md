# decisions.md

（累计记录：关键决策与原因。只追加，不覆盖。）

## 2026-02-21 - HomeSelectionState API 行为取舍
- `enter/start`：进入多选时将选择重置为仅包含该 id，避免调用方在“已处于多选态”时误用 `enter` 导致保留旧选择、行为不确定。
- 空白 id：对传入 id 做 `trim()`，若结果为空/全空白则 no-op（不崩溃、不污染 `selectedIds`），相关行为已写入单测。

## 2026-02-21 - Home 多选分享文本拼接规则
- 保持输入顺序：builder 内不排序，确保 UI 只要按“主页列表顺序”传入即可得到一致的分享结果。
- 空列表占位：返回 `(分享内容为空)`，与 `app/src/main/java/cc/pscly/onememos/share/ShareIntentParser.kt` 的占位文案保持一致。
- 固定分隔符：使用 `\n\n---\n\n`，避免后续 UI/测试因为分隔策略变化出现不必要的行为差异。

## 2026-02-21 - Home 放入锦囊 displayName 兜底与截断长度
- 兜底文案：使用 `"随笔"`，避免 Collections 列表出现 `（无标题）`，同时保持语义中性。
- maxChars=80：NOTE_REF 名称需要短且可读，列表展示更稳定；同时按字符上限扫描，生成开销更低。
