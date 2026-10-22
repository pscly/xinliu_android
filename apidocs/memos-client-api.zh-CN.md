# Memos 客户端 API 对齐说明（v0.31.0）

最后更新：2026-10-22

本文件记录 **1memos Android 客户端** 与上游 [usememos/memos](https://github.com/usememos/memos) 的协议对齐情况。
与 `apidocs/api.zh-CN.md`（Flow Backend 自有接口）相互独立：本 App 的笔记数据走的是**用户自建 Memos 实例**（默认 `https://me.pscly.cc/`）。

## 1. 对齐目标版本

| 项 | 值 |
|---|---|
| 上游稳定版 | **v0.31.0**（2026-09-19） |
| 上游预览线 | `26.10-rc.1`（2026-10-09，**不跟进**） |
| REST 前缀 | `/api/v1/...`（与线上实例探测结果一致） |
| 权威来源 | `proto/gen/openapi.yaml` + `proto/api/v1/memo_service.proto`（tag `v0.31.0`） |

> 已探测线上实例 `me.pscly.cc`：`GET /api/v1/memos` 返回 401（存在）、`GET /api/memos` 返回 404，
> 说明服务端仍是 `/api/v1` 体系，因此**不能**跟进 26.10 的路径迁移。

## 2. 26.10 的破坏性变更（暂不跟进，留档备查）

26.10 起上游做了一次大范围重命名，客户端必须同步改造，否则请求会拿到 `410 Gone`：

- REST 路径：`/api/v1/...` → `/api/...`（SSE 从 `/api/v1/sse` → `/api/sse`）
- proto 包名：`memos.api.v1` → `memos.api`
- 资源类型：`memos.api.v1/<Type>` → `memos.api/<Type>`
- 字段改名示例：`ListMemoCommentsResponse.memos` → `comments`、`ListAllUserStats` → `ListUserStats`、
  `UserStats.tag_count` → `tag_counts`、`SignInResponse.access_token_expires_at` → `access_token_expire_time`

迁移成本控制：本仓库把所有路径集中放在 `core/network/.../MemosUrls.kt` 的 `API_PREFIX` 常量，
将来只需改一处并复核 `MemosDtoContractTest`。

## 3. 资源模型对齐（Memo）

`MemoDto` 已按 v0.31.0 补齐：

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` / `state` / `content` / `visibility` | String | 原有字段 |
| `creator` / `createTime` / `updateTime` | String | 原有字段 |
| `displayTime` | String | **0.31 已 reserved**，仅为兼容旧服务端保留，读到就用、没有就回落 `createTime` |
| `tags` | List\<String\> | 新增：服务端从正文提取的标签（只读） |
| `snippet` | String | 新增：纯文本摘要（只读） |
| `pinned` | Boolean | 原有字段 |
| `parent` | String | 新增：评论类 memo 的父资源名（只读） |
| `property` | MemoPropertyDto | 新增：`hasLink/hasTaskList/hasCode/hasIncompleteTasks/title` |
| `location` | MemoLocationDto | 新增：`placeholder/latitude/longitude` |
| `attachments` | List\<AttachmentDto\> | 原有字段 |
| `relations` | List\<MemoRelationDto\> | 新增：`memo/relatedMemo/type(REFERENCE|COMMENT)` |
| `reactions` | List\<ReactionDto\> | 新增：0.31 起移除 `contentId`，反应只作用于所属 memo |

创建/更新请求：

- `CreateMemoRequestDto`：在 `state/content/visibility` 之外新增可选 `pinned`、`parent`、`location`；
- `UpdateMemoRequestDto`：新增可选 `location`；
- Gson 默认不序列化 null，因此**未开启位置时请求体与旧版完全一致**。

## 4. 端点覆盖情况

### 4.1 已接线（App 实际调用）

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/v1/memos` | 增量/全量同步拉取 |
| POST | `/api/v1/memos` | 新建笔记（本次新增 `location` 支持） |
| GET | `/api/v1/memos/{memo}` | 单条详情 / 冲突处理 |
| PATCH | `/api/v1/memos/{memo}` | 更新正文/可见性/置顶/状态 |
| PATCH | `/api/v1/memos/{memo}/attachments` | 覆盖式设置附件 |
| POST | `/api/v1/attachments` | 附件上传（含流式） |
| GET | `/api/v1/auth/me` | 登录态校验 |
| GET | `/api/v1/users/me` | 当前用户解析 |

### 4.2 协议层已对齐、UI 尚未接入

`MemosMemoExtrasApi`、`MemosSpaceApi`、`MemosViewApi` 已定义下列端点，供后续功能直接复用：

- Memo 子资源：`comments`（GET/POST）、`reactions`（GET/POST/DELETE）、`relations`（GET/PATCH）、
  `shares`（GET/POST/DELETE）、`/shares/{token}/memo`、`/-/linkMetadata`（单个/批量）、`DELETE /memos/{memo}`
- 附件：`GET /attachments`、`GET /attachments/{attachment}`、`DELETE /attachments/{attachment}`
- Space：`/spaces`（GET/POST）、`/spaces/{space}`（GET/PATCH/DELETE）、
  `members`（GET/PATCH/DELETE）、`invitations`（GET/POST/DELETE + `:accept` / `:decline`）、
  `/users/{user}/spaceInvitations`
- MemoView：`/users/{user}/views`（GET/POST）、`/users/{user}/views/{view}`（GET/PATCH/DELETE）

### 4.3 明确未覆盖（与本 App 定位无关）

`IdentityProviderService`、`InstanceService` 的管理类接口、`UserService` 的用户/Webhook/PAT/通知/导入导出管理接口，
以及 `26.10` 的 AI 转录、Chunked upload 等。需要时再补，避免现在引入大量未验证代码。

## 5. 本次新增的兼容策略

上游 0.31 的 REST 层是 connect + protojson，**未知字段可能导致请求被拒**。因此：

- 新建笔记时若带 `location` 失败，会**去掉 `location` 重试一次**（`MemosSyncWorker.createMemoOnServer`）；
- 这样即使实例还没升级到 0.31，也不会因为多带一个字段把同步卡死；
- 拉取侧：`MemoDto.location` 为空（老服务端）时本地位置列保持 null，不做回填。

## 6. 验证方式

- 契约测试：`app/src/test/java/cc/pscly/onememos/core/network/MemosDtoContractTest.kt`
  （字段解析、`location` 的可选序列化、路径常量）
- 本地门禁：`./gradlew :app:testDebugUnitTest`
- CI 门禁：`.github/workflows/android-benchmark.yml` 跑 `scripts/verify.sh`
