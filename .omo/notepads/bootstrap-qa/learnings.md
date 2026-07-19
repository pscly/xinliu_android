## 2026-02-01 Task: bootstrap-qa
- 工作流状态文件统一放在 `.sisyphus/` 下；计划文件为 `.sisyphus/plans/*.md`；状态为 `.sisyphus/boulder.json`。
- 在仓库根目录 `.gitignore` 的 “AI / tooling caches” 分组下新增忽略规则：`**/.sisyphus/`，避免误提交工作流状态文件。
- 2026-02-01T03:11:37Z ebbbf7d10629430f5c5452e625150dd64b453624 chore: 忽略本地 .sisyphus 工作流状态文件
