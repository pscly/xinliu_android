# Flow Backend v2 对接文档（快照）

本目录用于 Android 端对接 **Flow Backend（后端）** 的接口文档与 OpenAPI 快照。

> 说明：
> - 这是从后端仓库同步过来的“快照”，用于 App 开发联调与后续维护。
> - 文档来源与具体版本请看：`SOURCE_COMMIT.txt`。

## 1. 文件说明（你应该用哪些）

- `to_app_plan.md`：客户端对接总指南（推荐先读）
  - 重点：鉴权方式、通用请求头、错误处理、同步/冲突策略、对接路线建议。
- `api.zh-CN.md`：详细接口文档（v1 + v2 汇总）
  - 重点：每个接口的字段、示例、错误码、对接注意点。
- `openapi-v2.json`：v2 OpenAPI 快照（机器可读）
  - 重点：可用于生成客户端 SDK、校验请求/响应结构、对照接口是否变更。
- `openapi-v1.json`：v1 OpenAPI 快照（兼容/历史接口）
  - 说明：仅在你需要兼容旧接口或排查历史行为时使用。
- `SOURCE_COMMIT.txt`：来源与时间戳（可追溯）

## 2. 推荐阅读顺序

1. `to_app_plan.md`
2. `api.zh-CN.md`
3. `openapi-v2.json`

## 3. 运行中后端的 OpenAPI 地址（用于实时对照）

不同环境域名/端口以实际部署为准，路径固定为：

- v1：`GET /openapi.json`
- v2：`GET /api/v2/openapi.json`

## 4. 如何更新本目录（后端接口有变更时）

1. 在后端仓库更新并（可选）重新导出 OpenAPI 快照：
   - 在后端仓库根目录执行：`uv run python scripts/export_openapi.py`
2. 重新把最新的文档与 OpenAPI 文件复制到本目录（覆盖旧文件）
3. 更新 `SOURCE_COMMIT.txt`（记录最新 commit 与同步时间）
4. 在 Android 仓库提交 git（便于团队协作与回溯）

