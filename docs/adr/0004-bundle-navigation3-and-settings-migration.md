# 同一版本完成 Navigation 3 与设置重构

尽管 Navigation 2 仍受维护，且 Navigation 3 会把 Settings 单一切片扩大为工具链、全局路由、返回栈、状态恢复与 Hilt 作用域迁移，仍选择在同一个稳定版本中完成 Navigation 3 和 Settings 能力重构。实施过程必须保留可独立验证的内部阶段与提交，但只在两部分共同通过完整门禁后发布，避免交付长期过渡架构。所有迁移依赖采用实施当日相互兼容的最新稳定版本并精确锁定，禁止动态版本、Alpha、Beta、RC、Canary、EAP 与 Snapshot。
