# 锦囊/Collections（离线优先 + Flow Sync）工作计划

## TL;DR

> **目标**：在现有 1memos Android（Compose 多模块）里新增“锦囊/Collections（结构层）”能力：可嵌套文件夹 + 笔记引用（note_ref），离线优先（Room + outbox + `/api/v1/sync/push|pull`），支持批量管理、拖拽移动/排序、细腻触感与国风动效。
>
> **核心策略**：
> - **结构与引用**走 Flow Backend（Collections），**笔记正文**仍走 Memos。
> - Flow Sync 的 cursor **疑似全局游标**：必须保证“推进 cursor 前已 apply 所有资源”，因此把 `collection_item` 并入现有 `FlowTodoSyncWorker` 的同一条 push/pull 管道（单 worker、单 cursor、单 outbox）。
>
> **交付物**：
> - 新增锦囊 UI（抽屉入口 + 列表/多宝阁 + 面包屑 + 选择器 + 批量/拖拽）
> - 本地表 `collection_items` + migration（DB v10 -> v11）
> - Flow sync 支持 `collection_item`（push/pull/apply + conflict 策略）
> - TDD：DAO/Repository/Worker 关键逻辑单测（JUnit4 + Robolectric）
> - Benchmark APK：`./gradlew :app:assembleBenchmark`，产物重命名带时间戳

**预计工作量**：Large（数据层 + 同步层 + UI 复杂交互）

---

## Context

### 原始需求摘录（用户确认版）
- 功能来自 `plan.md` 的“## plan7 锦囊模式”。
- 离线优先：断网可整理；联网自动同步。
- 入口：不改全局导航框架；保持抽屉（Drawer）并新增“锦囊”菜单项。
- ref_type：`memos_memo + flow_note`，但 `flow_note` 首版仅“占位引用”（可组织/移动/改色/删除，不做预览/详情）。
- 非 BACKEND 登录模式（自定义服务器）：锦囊禁用并提示原因。
- 多账号：同一 `serverUrl` 下多账号；通过“退出/登录”切换；历史缓存一直保留（不做账号列表 UI）。
- 冲突策略（collection_item）：**客户端优先**；把服务端快照保存为“_冲突_yyyy-MM-ddTHH-mm-ss”副本，用户可查看/删除。
- 测试策略：TDD。

### 关键参考资料/现状
- 接口文档（权威）：`apidocs/collections.zh-CN.md`
- 导航：`core/navigation/src/main/java/cc/pscly/onememos/ui/Routes.kt` + `app/src/main/java/cc/pscly/onememos/ui/OneMemosApp.kt`
- Flow Sync 现状：`core/network/src/main/java/cc/pscly/onememos/core/network/FlowSyncApi.kt` + `core/sync/src/main/java/cc/pscly/onememos/worker/FlowTodoSyncWorker.kt`
- Room DB：`core/database/src/main/java/cc/pscly/onememos/core/database/OneMemosDatabase.kt`（version=10，手写 SQL migrations）
- Memo uuid 重键：`core/database/src/main/java/cc/pscly/onememos/core/database/dao/MemoDao.kt`（`replaceMemoUuid(...)` 存在）
- 测试样板：`core/sync/src/test/java/cc/pscly/onememos/worker/FullSyncHelpersTest.kt`

### Metis 评审要点（已吸收）
- 必须显式定义：删除级联、排序语义、循环父子禁止、冲突副本是否参与同步。
- tombstone 与 LWW 合并规则必须写死并配测试（“旧 server 不覆盖新 local，但更‘新’的 tombstone 必须能压过”）。
- multi-account 必须按 `ownerKey` 隔离 collections/outbox/state；并确保 UI 不串台。

---

## Work Objectives

### Core Objective
实现一个可用、可维护、可同步（离线优先）的锦囊系统，并将其与现有 memo 列表与同步机制安全集成。

### 范围（IN / OUT）

IN：
- collections 结构：folder/note_ref 混排、无限层级、sortOrder、color、tombstone、批量操作、拖拽排序/移动。
- 同步：Flow Sync push/pull（resource=`collection_item`）。
- 冲突：客户端优先 + 保存服务端副本（本地可见）。
- UI：抽屉入口 + “多宝阁”列表/网格 + 面包屑/返回导航 + 选择器弹窗 + 空状态 + 国风动效/触感。

OUT：
- `flow_note` 的内容预览/详情页（首版仅占位引用）。
- 自定义服务器模式下启用 collections（保持禁用并提示）。
- 账号列表/一键切换 UI（仅退出/登录切换）。

### Definition of Done
- `./gradlew :app:testDebugUnitTest` 通过。
- `./gradlew :app:lint` 通过。
- `./gradlew :app:assembleBenchmark` 通过，且产出 benchmark APK 并按时间戳重命名。
- 核心行为由单测覆盖：创建/移动/删除/重排、sync push/pull apply、冲突副本策略、循环父子校验。

---

## Verification Strategy（强制：无人工介入）

### Test Decision
- **基础设施存在**：YES（JUnit4 + Robolectric 已在 `core:sync`/`app` 使用）
- **自动化测试**：YES（TDD）

### 统一验收命令（Agent 可直接执行）
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lint
./gradlew :app:assembleBenchmark
```

### 证据产物（Agent 侧）
- 单测报告：各 module 的 `build/reports/tests/`（Gradle 默认）
- Lint 报告：`app/build/reports/lint-results-*.html`（Gradle 默认）
- APK：`app/build/outputs/apk/benchmark/` 下的 apk（最终重命名一份到可定位路径，见 TODO 最后一项）

---

## Execution Strategy

Wave 1（先定模型与 DB/Sync 基建）：
- DB schema + migration + DAO + domain model
- Flow sync model/worker 扩展（先只做落库/冲突策略，不碰 UI）

Wave 2（Repository + 业务规则 + 单测闭环）：
- outbox 写入、LWW 合并、tombstone、循环父子校验、拖拽排序算法

Wave 3（UI + 交互 + 动效/触感）：
- 新 feature screen、抽屉入口、选择器、批量模式、拖拽

Wave 4（收尾）：
- 端到端回归（以单测为主）+ Lint + benchmark APK
- 更新 `.ai_session.md`（执行代理完成）

---

## TODOs（TDD：每条都要求先写测试）

### 1) 定义本地数据模型（Collections）并新增 Room 表 + Migration

**What to do**:
- 新增 `CollectionItemEntity`（单表混排）：字段对齐 `apidocs/collections.zh-CN.md`，并增加两个本地字段：
  - `localOnly`：冲突副本用，且不参与同步。
  - `refLocalUuid`：仅用于 `ref_type=memos_memo` 且目标 memo 尚未拿到 `serverId` 时的“本地临时引用”（用于离线先整理、联网后补齐 refId 再同步）。
- 新增 `CollectionDao`：
  - 按 parentId 列子节点（root 支持 null）
  - 全量列出用于建树
  - upsert / batch upsert
  - 本地级联 tombstone（删除 folder 递归 tombstone 子树）
- 修改 `OneMemosDatabase`：
  - version 10 -> 11
  - entities 增加 collection_items
  - 增加 MIGRATION_10_11（只新增表/索引，保持低风险）
- 修改 `AppModule`：注册新 migration。

**References**:
- `apidocs/collections.zh-CN.md`（字段语义、tombstone、LWW、move 防环）
- `core/database/src/main/java/cc/pscly/onememos/core/database/OneMemosDatabase.kt`（迁移风格）
- `core/database/src/main/java/cc/pscly/onememos/core/database/entity/TodoListEntity.kt`（ownerKey + deletedAt + clientUpdatedAtMs 模式）

**Acceptance Criteria**:
- 新增 migration 后：`./gradlew :app:testDebugUnitTest` 仍通过。
- 新增 DAO 单测（Robolectric + in-memory Room）覆盖：
  - root/child 列表排序规则（sortOrder + clientUpdatedAtMs tie-break）
  - folder 级联删除 tombstone（含多层）
  - localOnly 条目不会被“同步写入”逻辑选中（后续 Task 会验证）

---

### 2) 扩展 Flow Sync 模型：支持 `collection_item` 的 pull changes

**What to do**:
- 在 `FlowSyncApi` 的 `FlowSyncChanges` 增加 `collection_items`。
- 定义 `CollectionItemOut` DTO（字段与 `apidocs/collections.zh-CN.md` 一致：id/item_type/parent_id/name/color/ref_type/ref_id/sort_order/client_updated_at_ms/updated_at/deleted_at）。

**References**:
- `core/network/src/main/java/cc/pscly/onememos/core/network/FlowSyncApi.kt`
- `apidocs/collections.zh-CN.md` 第 4 章（Sync）

**Acceptance Criteria**:
- DTO 解析单测（最少 2 个 JSON 样例：folder、note_ref + tombstone）

---

### 3) 扩展 `FlowTodoSyncWorker`：把 `collection_item` 并入同一条 push/pull 管道

**Why**:
- Flow Sync cursor 很可能是“按用户的全局游标”；如果拆 worker 或拆 cursor，会造成永久漏同步。

**What to do**:
- 在 `FlowTodoSyncWorker` 的 `applyChanges()` 增加 collections apply：
  - 对每条 server item：按 LWW 合并写入 `collection_items`（规则见 Task 4）。
  - 对 tombstone：deletedAt != null 时，必须能压过旧的本地实体。
- 扩展 `applyServerSnapshot()` 或 `handleRejected()`：对 resource=`collection_item` 走“客户端优先”冲突策略：
  - 把 `server_snapshot` 写成 `localOnly=true` 的冲突副本（新 id），name 后缀 `_冲突_yyyy-MM-ddTHH-mm-ss`。
  - 保持本地主版本不变，并 enqueue 一个“更大 clientUpdatedAtMs”的 upsert 以覆盖服务端。
  - 冲突副本 **默认不参与同步**（不写 outbox）。

**References**:
- `core/sync/src/main/java/cc/pscly/onememos/worker/FlowTodoSyncWorker.kt`
- `core/database/src/main/java/cc/pscly/onememos/core/database/dao/TodoSyncDao.kt`（outbox/state 模式）
- `core/database/src/main/java/cc/pscly/onememos/core/database/entity/TodoSyncOutboxEntity.kt`（resource 字段可复用）

**Acceptance Criteria**:
- 单测覆盖（Robolectric + in-memory Room）：
  - pull 的 collection_items 能正确落库（upsert + tombstone）。
  - push conflict（rejected + server_snapshot）会：
    - 插入 1 条 localOnly 冲突副本（name 带 `_冲突_`）
    - 原条目仍保持本地内容
    - outbox 中出现“重推 upsert”（clientUpdatedAtMs 变大）
  - 后续 pull 给出旧 server_snapshot 时，不会覆盖本地更“新”的主版本。

---

### 4) Collections 合并规则（LWW + tombstone + 不覆盖）与排序/防环纯函数

**What to do**:
- 实现并单测以下纯函数（建议放 domain 或 core:data 中，便于 UI/Repo/Worker 共用）：
  - `canApplyRemote(local, remote)`：remote.clientUpdatedAtMs >= local.clientUpdatedAtMs 时才覆盖；但 remote.deletedAt != null 且时间更大必须 tombstone。
  - `bumpClientUpdatedAtMs(nowMs, local)`：保证单调递增（防时间回退）
  - `isMoveValid(tree, movingFolderId, targetParentId)`：禁止移动到自己或后代（cycle）
  - `reorder(items, from, to)`：输出新的 sortOrder（0..n-1）

**References**:
- `apidocs/collections.zh-CN.md` 的 LWW 与 move 防环说明
- `core/data/src/main/java/cc/pscly/onememos/data/repository/TodoRepositoryImpl.kt`（clientUpdatedAtMs 的写入风格）

**Acceptance Criteria**:
- 纯函数单测覆盖：
  - local newer + remote older -> 不覆盖
  - remote tombstone newer -> 必须 tombstone
  - 防环：moving folder under descendant -> false
  - reorder 后 sortOrder 连续且稳定

---

### 5) Repository：锦囊本地操作（CRUD/批量/拖拽）-> 写 Room + 写 outbox + 触发 Flow sync

**What to do**:
- 新增 domain 层接口（建议）：`CollectionsRepository`（observe children, create folder, add note_ref, move, reorder, delete, batch ops）。
- 在 data 层实现：
  - ownerKey 计算复用 FlowTodo 的规则（sha256(username.lowercase())），与 todo 保持一致。
  - 每次本地写入：
    - 先写 Room（乐观更新）
    - 再写 outbox（resource=`collection_item`，op=upsert/delete，dataJson 按 apidocs 形状）
    - 调用现有 `TodoSyncScheduler.requestSync()` 触发 `FlowTodoSyncWorker`。
- 处理 memo uuid 重键：当 memo uuid 被替换（`replaceMemoUuid`）时，若存在引用 `refType=memos_memo && refId=oldUuid`，同步更新为 newUuid。
  - 最小实现：在 memo 的替换事务后补一次“引用修复”调用（需要新的 DAO 方法）。

- 处理“memo 尚未上传但已放入锦囊”的离线场景：
  - 创建 note_ref 时：若 `memo.serverId` 为空，则写 `refId=null` 且写 `refLocalUuid=memo.uuid`。
  - 写 outbox 时：仅对 `refId` 非空的 note_ref 写入 `collection_item` mutation；否则先不 push，避免后端出现无效引用。
  - 当 memo 后续拿到 `serverId`（`MemoEntity.serverId` 被回写为 `memos/123`）时：执行一次“引用补齐/修复”
    - 找出 `refLocalUuid == memo.uuid AND refId IS NULL` 的 collection items
    - 写入 `refId = memo.serverId` 并清空 `refLocalUuid`
    - 为这些 items 写 outbox upsert，并触发 Flow sync
  - 触发时机建议：在 `MemosSyncWorker` 成功创建 memo 并写回 `serverId` 后立即调用（避免用户长期离线导致永远不 push）。

**References**:
- `core/data/src/main/java/cc/pscly/onememos/data/repository/TodoRepositoryImpl.kt`（outbox 写入范式）
- `core/data/src/main/java/cc/pscly/onememos/data/auth/FlowBackendCredentialStorage.kt`（当前账号来源）
- `core/database/src/main/java/cc/pscly/onememos/core/database/entity/MemoEntity.kt`（memo uuid/serverId 的关系）

**Acceptance Criteria**:
- Repository 单测覆盖：
  - create folder/note_ref 会写入 outbox 且触发 requestSync（可用 fake scheduler 断言）
  - batch move/delete/recolor 输出正确的 outbox mutations（数量/字段正确）
  - localOnly 冲突副本不会写 outbox
  - 引用修复：oldUuid -> newUuid 更新后，collections 仍能关联到 memo

---

### 6) UI：新增 `feature:collections` Screen（多宝阁 + 面包屑 + 选择器 + 批量/拖拽 + 动效/触感）

**What to do**:
- 新增 `:feature:collections` module（或按现有命名 `:feature:collection`，以仓库惯例为准）。
- 设计语言：延续“新中式/国漫风” designsystem（丝带配色、印章面包屑、开箱动效、细腻 haptics）。
- 页面结构：
  - 顶部：面包屑（印章链/竹节导航）+ 右侧操作（新建文件夹、批量模式开关）。
  - 内容：LazyVerticalGrid / staggered grid 混排 folder 与 note_ref。
  - 空态：打开的空柜子/水墨猫（占位图）；提供“新建文件夹/放入笔记”引导。
- 交互：
  - 点击 folder：进入下一层（动画：开箱/镜头推进）
  - 点击 note_ref(memos_memo)：打开 editor/view（沿用 `Routes.editor(uuid)`）
  - 点击 note_ref(flow_note)：占位 toast/提示（后续再支持）
  - 长按：进入多选；批量操作（移动/删除/改色）
  - 拖拽：
    - 同层排序：更新 sortOrder
    - 拖入 folder：移动 parentId
    - 防环：拖 folder 到自身/后代时禁用
  - 触感：hover/进入 folder/批量确认等按你现有震动风格（参考其它 feature 的 haptic 用法）

**References**:
- `core/designsystem/src/main/java/cc/pscly/onememos/ui/`（主题/组件）
- `app/src/main/java/cc/pscly/onememos/ui/OneMemosApp.kt`（screen 接入方式）
- `core/navigation/src/main/java/cc/pscly/onememos/ui/Routes.kt`（新增 route 常量）
- `plan.md` plan7（多宝阁、印章面包屑、开箱动效、ghost “残卷”）

**Acceptance Criteria**:
- Compose UI 的关键状态用单测/截图替代“人工拖拽验收”：
  - state reducer / viewmodel 单测：进入/返回层级、批量选择、移动/删除后列表更新。
  - drag 行为至少用“排序/移动算法纯函数单测 + DAO 顺序断言”覆盖。
- `./gradlew :app:testDebugUnitTest` 通过（含 collections 的 viewmodel/state 测试）。

---

### 7) 导航接入：抽屉入口 + Routes + NavHost

**What to do**:
- 在 `Routes.kt` 增加 `COLLECTIONS` route 常量（以及可选参数如 folderId，建议用 query arg 形式）。
- 在 `OneMemosApp.kt`：
  - Drawer 增加“锦囊”菜单项。
  - NavHost 增加 `composable(Routes.COLLECTIONS)` -> `CollectionsScreen(...)`。
- 非 BACKEND 模式：Drawer 项置灰/提示（或点击进入一个解释页/对话框）。

**References**:
- `core/navigation/src/main/java/cc/pscly/onememos/ui/Routes.kt`
- `app/src/main/java/cc/pscly/onememos/ui/OneMemosApp.kt`

**Acceptance Criteria**:
- 单测：Routes 编码/解码（如带 folderId 参数）
- 编译通过：`./gradlew :app:assembleDebug`（可作为附加验证）

---

### 8) Home 集成：从 memo 列表“放入锦囊”

**What to do**:
- 在 Home 列表项长按菜单新增“放入锦囊”。
- 弹出选择器：
  - 可选择目标 folder（含 root）
  - 支持快速新建 folder
- 确认后：创建 note_ref（ref_type=memos_memo、ref_id=优先使用 `memo.serverId`（例如 `memos/123`）；若为空则走 `refLocalUuid=memo.uuid` 暂存，待上传后补齐）写入 collections。

**References**:
- `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeScreen.kt`
- `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeViewModel.kt`（已有 creator 过滤逻辑）

**Acceptance Criteria**:
- 单测：调用 repository 的 add-to-collections 会写 outbox 并触发 requestSync。

---

### 9) 收尾：更新会话记录、提交策略、Benchmark APK 产出

**What to do**:
- 更新项目根 `.ai_session.md`：记录本次“锦囊/Collections”实现的关键决策、文件与验证命令。
- 按仓库要求提交 git commit（conventional commits，中文优先）。
- 构建 benchmark APK：
  - `./gradlew :app:assembleBenchmark`
  - 将产物复制/重命名为 `benchmark-YYYY-MM-DDTHH-mm-ss.apk`（不要 debug）。

**Acceptance Criteria**:
- `git status` 干净（仅在提交完成后）。
- benchmark APK 路径可定位并反馈给用户。

---

## Defaults Applied（无进一步询问，已在计划中锁定）
- 冲突副本：**仅本地可见**（`localOnly=true`），不写 outbox、不参与 sync，避免重复/无限冲突。
- 删除 folder：级联 tombstone 整棵子树（与后端 delete 语义一致）。
- sortOrder：采用 int 连续重排（0..n-1），拖拽后批量更新。

---

## Commit Strategy（建议）
- `feat(collections): add local schema and dao`
- `feat(collections): sync collection_item via flow sync worker`
- `feat(collections): collections repository and conflict policy`
- `feat(ui): add collections screen and drawer entry`
- `feat(home): add add-to-collections action`
- `test(collections): cover dao/repo/sync merge rules`

---

## Success Criteria
- 锦囊可离线创建/移动/删除/改色，并在联网后通过 sync 与后端一致。
- 冲突时：本地版本最终覆盖服务端，且服务端旧版本以“冲突副本”形式保留在本地可见。
- 非 BACKEND 模式：锦囊入口明确禁用并给出解释。
- 所有关键行为可由自动化测试与 Gradle 命令验证。
