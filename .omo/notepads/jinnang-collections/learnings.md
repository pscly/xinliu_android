# Learnings

## 2026-02-13
- （预留）

- `GET /api/v1/collections/items` 不传 `parent_id` 会返回“全部 items”，不是 root；客户端做树必须自己按 `parent_id == null` 过滤 root。
- `PATCH /api/v1/collections/items/move` 与 `POST /api/v1/collections/items/batch-delete` 的 body 都是 `{ "items": [...] }` 包装，不是裸数组。
- sync push 冲突不会返回 409：HTTP 200 但进入 `rejected[]`；必须看 `applied/rejected`。

- 抽屉入口与 NavHost 都在 `app/src/main/java/cc/pscly/onememos/ui/OneMemosApp.kt`；登录态 gating 目前通过 `AppShellViewModel` 的 `showTodo`（loginMode==BACKEND && token 非空）。
- Home 列表长按入口当前是 `feature/home/.../HomeScreen.kt` 的 `shareTarget` 对话框（"更多操作"），长按回调来自 `MemoItem` 的 `onLongClick`。
- Flow 同步主干在 `core/sync/.../FlowTodoSyncWorker.kt`：outbox 表是 `todo_sync_outbox`（resource/op/entityId/dataJson/clientUpdatedAtMs）；pull 后在 `applyChanges()` 落库，然后才推进/落盘 cursor。
